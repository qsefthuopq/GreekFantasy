package greekfantasy.entity;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import greekfantasy.GFRegistry;
import greekfantasy.GreekFantasy;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MobEntity;
import net.minecraft.entity.ai.attributes.AttributeModifierMap;
import net.minecraft.entity.ai.attributes.Attributes;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.HurtByTargetGoal;
import net.minecraft.entity.ai.goal.LookAtGoal;
import net.minecraft.entity.ai.goal.LookRandomlyGoal;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.ai.goal.NearestAttackableTargetGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.monster.MonsterEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.potion.EffectInstance;
import net.minecraft.potion.Effects;
import net.minecraft.util.DamageSource;
import net.minecraft.util.Hand;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.World;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

public class GorgonEntity extends MonsterEntity {
  
  private static final byte STARE_ATTACK = 9;
  private static final int PETRIFY_DURATION = 80;

  public GorgonEntity(final EntityType<? extends GorgonEntity> type, final World worldIn) {
    super(type, worldIn);
  }
  
  public static AttributeModifierMap.MutableAttribute getAttributes() {
    return MobEntity.func_233666_p_()
        .createMutableAttribute(Attributes.MAX_HEALTH, 24.0D)
        .createMutableAttribute(Attributes.MOVEMENT_SPEED, 0.25D)
        .createMutableAttribute(Attributes.ATTACK_DAMAGE, 1.0D)
        .createMutableAttribute(Attributes.FOLLOW_RANGE, 16.0D);
  }
  
  @Override
  protected void registerGoals() {
    super.registerGoals();
    this.goalSelector.addGoal(0, new SwimGoal(this));
    this.goalSelector.addGoal(4, new MeleeAttackGoal(this, 1.0D, true));
    this.goalSelector.addGoal(5, new LookAtGoal(this, PlayerEntity.class, 10.0F));
    this.goalSelector.addGoal(6, new LookRandomlyGoal(this));
    this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
    this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, PlayerEntity.class, true));
    if(GreekFantasy.CONFIG.GORGON_ATTACK.get()) {
      this.goalSelector.addGoal(3, new StareAttackGoal(this, PETRIFY_DURATION + 10));
    }
  }
  
  @OnlyIn(Dist.CLIENT)
  public void handleStatusUpdate(byte id) {
    switch(id) {
    case STARE_ATTACK:
      spawnStareParticles();
      break;
    default:
      super.handleStatusUpdate(id);
      break;
    }
  }
  
  public void spawnStareParticles() {
    if (world.isRemote()) {
      final double motion = 0.08D;
      final double radius = 1.2D;
      for (int i = 0; i < 5; i++) {
        world.addParticle(ParticleTypes.END_ROD, 
            this.getPosX() + (world.rand.nextDouble() - 0.5D) * radius, 
            this.getPosYEye() + (world.rand.nextDouble() - 0.5D) * radius * 0.75D, 
            this.getPosZ() + (world.rand.nextDouble() - 0.5D) * radius,
            (world.rand.nextDouble() - 0.5D) * motion, 
            (world.rand.nextDouble() - 0.5D) * motion * 0.5D,
            (world.rand.nextDouble() - 0.5D) * motion);
      }
      // get list of all nearby players who have been petrified
      final List<PlayerEntity> list = this.getEntityWorld().getEntitiesWithinAABB(PlayerEntity.class, this.getBoundingBox().grow(16.0D, 16.0D, 16.0D), 
        e -> e.getActivePotionEffect(GFRegistry.PETRIFIED_EFFECT) != null);
      for(final PlayerEntity p : list) {
        world.addParticle(GFRegistry.GORGON_PARTICLE, true, p.getPosX(), p.getPosY(), p.getPosZ(), 0D, 0D, 0D);
      }
    }
  }
  
  @Override
  protected SoundEvent getAmbientSound() { return SoundEvents.ENTITY_CAT_HISS; }

  @Override
  protected SoundEvent getHurtSound(DamageSource damageSourceIn) { return SoundEvents.ENTITY_SPIDER_AMBIENT; }

  @Override
  protected SoundEvent getDeathSound() { return SoundEvents.ENTITY_SPIDER_DEATH; }

  @Override
  protected float getSoundVolume() { return 0.8F; }
  
  public boolean isPlayerStaring(final PlayerEntity player) {
    Vector3d vector3d = player.getLook(1.0F).normalize();
    Vector3d vector3d1 = new Vector3d(this.getPosX() - player.getPosX(), this.getPosYEye() - player.getPosYEye(),
        this.getPosZ() - player.getPosZ());
    double d0 = vector3d1.length();
    vector3d1 = vector3d1.normalize();
    double d1 = vector3d.dotProduct(vector3d1);
    return d1 > 1.0D - 0.025D / d0 ? player.canEntityBeSeen(this) : false;
  }
  
  public boolean isImmuneToStareAttack(final LivingEntity target) {
    // check for mirror potion effect
    if((GreekFantasy.CONFIG.isMirrorPotionEnabled() && target.getActivePotionEffect(GFRegistry.MIRROR_EFFECT) != null) 
        || target.isSpectator() || !target.isNonBoss() || (target instanceof PlayerEntity && ((PlayerEntity)target).isCreative())) {
      return true;
    }
    // check for mirror enchantment
    if(GreekFantasy.CONFIG.isMirrorEnabled() && EnchantmentHelper.getEnchantments(target.getHeldItem(Hand.OFF_HAND)).containsKey(GFRegistry.MIRROR_ENCHANTMENT)) {
      return true;
    }
    return false;
  }
  
  public boolean useStareAttack(final LivingEntity target) {
    // apply potion effect
    if(GreekFantasy.CONFIG.isParalysisNerf()) {
      target.addPotionEffect(new EffectInstance(Effects.SLOWNESS, PETRIFY_DURATION, 1, false, false, true));
      target.addPotionEffect(new EffectInstance(Effects.WEAKNESS, PETRIFY_DURATION, 1, false, false, true));
    } else {
      target.addPotionEffect(new EffectInstance(GFRegistry.PETRIFIED_EFFECT, PETRIFY_DURATION, 0, false, false, true));
    }
    // update client-state
    if(this.isServerWorld()) {
      this.world.setEntityState(this, STARE_ATTACK);
    }
    return false;
  }
  
  public static boolean isMirrorShield(final ItemStack stack) {
    return EnchantmentHelper.getEnchantments(stack).containsKey(GFRegistry.MIRROR_ENCHANTMENT);
  }
  
  public static class StareAttackGoal extends Goal {
    private final GorgonEntity entity;
    private final int maxCooldown;
    private int cooldown;
    private List<PlayerEntity> trackedPlayers = new ArrayList<>();
    
    public StareAttackGoal(final GorgonEntity entityIn, final int cooldown) {
       this.setMutexFlags(EnumSet.of(Goal.Flag.LOOK));
       this.entity = entityIn;
       this.maxCooldown = cooldown;
       this.cooldown = cooldown / 4;
    }

    @Override
    public boolean shouldExecute() {
      if(this.cooldown > 0) {
        cooldown--;
      } else {
        this.trackedPlayers = this.entity.getEntityWorld().getEntitiesWithinAABB(PlayerEntity.class, this.entity.getBoundingBox().grow(16.0D, 16.0D, 16.0D), 
            e -> this.entity.canAttack(e)&& !this.entity.isImmuneToStareAttack(e) && this.entity.isPlayerStaring((PlayerEntity)e));
        return !this.trackedPlayers.isEmpty();
      }
      return false;
    }

    @Override
    public void startExecuting() {
      if(!trackedPlayers.isEmpty() && trackedPlayers.get(0) != null && cooldown <= 0) {
        this.entity.getNavigator().clearPath();
        this.entity.getLookController().setLookPositionWithEntity(trackedPlayers.get(0), 100.0F, 100.0F);
        trackedPlayers.forEach(e -> this.entity.useStareAttack(e));
        trackedPlayers.clear();
        this.cooldown = maxCooldown;
      }
    }
    
    @Override
    public boolean shouldContinueExecuting() {
      return false;
    }
    
    @Override
    public void resetTask() {
      this.cooldown = maxCooldown;
    }
  }
  
}
