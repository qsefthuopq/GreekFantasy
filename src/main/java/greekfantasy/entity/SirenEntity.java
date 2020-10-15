package greekfantasy.entity;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;

import greekfantasy.GFRegistry;
import greekfantasy.GreekFantasy;
import greekfantasy.entity.ai.SwimUpGoal;
import greekfantasy.entity.ai.SwimmingMovementController;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MobEntity;
import net.minecraft.entity.MoverType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.AttributeModifierMap;
import net.minecraft.entity.ai.attributes.Attributes;
import net.minecraft.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.HurtByTargetGoal;
import net.minecraft.entity.ai.goal.LookAtGoal;
import net.minecraft.entity.ai.goal.LookRandomlyGoal;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.ai.goal.NearestAttackableTargetGoal;
import net.minecraft.entity.item.BoatEntity;
import net.minecraft.entity.passive.WaterMobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.pathfinding.SwimmerPathNavigator;
import net.minecraft.potion.EffectInstance;
import net.minecraft.potion.Effects;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.EntityPredicates;
import net.minecraft.util.RegistryKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.IWorld;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.Biomes;

public class SirenEntity extends WaterMobEntity implements ISwimmingMob {
  
  private static final DataParameter<Boolean> CHARMING = EntityDataManager.createKey(SirenEntity.class, DataSerializers.BOOLEAN);
  
  private boolean swimmingUp;
  
  private final AttributeModifier attackModifier = new AttributeModifier("Charm attack bonus", 2.0D, AttributeModifier.Operation.MULTIPLY_TOTAL);

  public SirenEntity(final EntityType<? extends SirenEntity> type, final World worldIn) {
    super(type, worldIn);
    this.navigator = new SwimmerPathNavigator(this, worldIn);
    this.moveController = new SwimmingMovementController<>(this);
  }
  
  public static AttributeModifierMap.MutableAttribute getAttributes() {
    return MobEntity.func_233666_p_()
        .createMutableAttribute(Attributes.MAX_HEALTH, 24.0D)
        .createMutableAttribute(Attributes.MOVEMENT_SPEED, 0.25D)
        .createMutableAttribute(Attributes.ATTACK_DAMAGE, 0.5D);
  }
  
  // copied from DolphinEntity
  public static boolean canSirenSpawnOn(final EntityType<? extends WaterMobEntity> entity, final IWorld world, final SpawnReason reason, 
      final BlockPos pos, final Random rand) {
    if (pos.getY() <= 45 || pos.getY() >= world.getSeaLevel()) {
      return false;
    }

    Optional<RegistryKey<Biome>> biome = world.func_242406_i(pos);
    return ((!Objects.equals(biome, Optional.of(Biomes.OCEAN)) || !Objects.equals(biome, Optional.of(Biomes.DEEP_OCEAN)))
        && world.getFluidState(pos).isTagged(FluidTags.WATER));
  }
  
  @Override
  protected void registerGoals() {
    super.registerGoals();
    
    this.goalSelector.addGoal(3, new SwimUpGoal<SirenEntity>(this, 1.0D, this.world.getSeaLevel() + 1));
    this.goalSelector.addGoal(6, new LookAtGoal(this, PlayerEntity.class, 8.0F));
    this.goalSelector.addGoal(7, new LookRandomlyGoal(this));
    this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
    this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, PlayerEntity.class, true));
    if(GreekFantasy.CONFIG.SIREN_ATTACK.get()) {
      this.goalSelector.addGoal(2, new CharmAttackGoal(250, 100, 24));
      this.goalSelector.addGoal(3, new AvoidEntityGoal<>(this, PlayerEntity.class, 10.0F, 1.2D, 1.0D, (entity) -> {
        return EntityPredicates.CAN_AI_TARGET.test(entity) && !this.isCharming();
     }));
    } else {
      this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0D, true));
    }
  }

  @Override
  public void registerData() {
    super.registerData();
    this.getDataManager().register(CHARMING, Boolean.valueOf(false));
  }
  
  @Override
  public void livingTick() {
    super.livingTick();
    
    // singing
    if(this.isCharming() && rand.nextInt(7) == 0) {
      final float color = 0.065F + rand.nextFloat() * 0.025F;
      world.addParticle(ParticleTypes.NOTE, this.getPosX(), this.getPosYEye() + 0.15D, this.getPosZ(), color, 0.0D, 0.0D);
    }
  }
//
//  @Override
//  public ILivingEntityData onInitialSpawn(IServerWorld worldIn, DifficultyInstance difficultyIn, SpawnReason reason,
//      @Nullable ILivingEntityData spawnDataIn, @Nullable CompoundNBT dataTag) {
//    if(this.rand.nextFloat() < 0.05F) {
//      final ItemStack trident = new ItemStack(Items.TRIDENT);
//      this.setHeldItem(Hand.MAIN_HAND, trident);
//    }
//    return super.onInitialSpawn(worldIn, difficultyIn, reason, spawnDataIn, dataTag);
//  }
  
  // Swimming methods

  @Override
  public void setSwimmingUp(boolean swimmingUp) { this.swimmingUp = swimmingUp; }

  @Override
  public boolean isSwimmingUp() { return swimmingUp; }
  
  @Override
  public void travel(final Vector3d vec) {
    if (isServerWorld() && isInWater() && isSwimmingUpCalculated()) {
      moveRelative(0.01F, vec);
      move(MoverType.SELF, getMotion());
      setMotion(getMotion().scale(0.9D));
    } else {
      super.travel(vec);
    }
  }

  @Override
  public boolean isPushedByWater() { return !isSwimming(); }

  @Override
  public boolean isSwimmingUpCalculated() {
    if (this.swimmingUp) {
      return true;
    }
    LivingEntity e = getAttackTarget();
    return e != null && e.isInWater();
  }
  
  // Charming methods
  
  public void setCharming(final boolean isCharming) { this.getDataManager().set(CHARMING, isCharming); }
  
  public boolean isCharming() { return this.getDataManager().get(CHARMING); }
  
  /**
   * Applies a special attack after charming the given entity
   * @param entity the target entity
   **/
  private void useCharmingAttack(final LivingEntity target) {
    // temporarily increase attack damage
    this.getAttribute(Attributes.ATTACK_DAMAGE).applyNonPersistentModifier(attackModifier);
    this.attackEntityAsMob(target);
    this.getAttribute(Attributes.ATTACK_DAMAGE).removeModifier(attackModifier);
    // apply stunned effect
    target.addPotionEffect(new EffectInstance(GFRegistry.STUNNED_EFFECT, 3 * 20, 0, false, false, true));
  }
  
  // Charming goal
  
  class CharmAttackGoal extends Goal {
    
    protected final EffectInstance nausea;
    protected final int maxProgress;
    protected final int maxCooldown;    
    protected final float range;
    
    protected int progress;
    protected int cooldown;
    
    public CharmAttackGoal(final int progressIn, final int cooldownIn, final int rangeIn) {
      this.setMutexFlags(EnumSet.noneOf(Goal.Flag.class));
      maxProgress = progressIn;
      maxCooldown = cooldownIn;
      cooldown = 60;
      range = rangeIn;
      nausea = new EffectInstance(Effects.NAUSEA, maxProgress, 0, false, false);
    }
    
    @Override
    public boolean shouldExecute() {
      if(cooldown > 0) {
        cooldown--;
      } else {
        return SirenEntity.this.getAttackTarget() != null && SirenEntity.this.isEntityInRange(SirenEntity.this.getAttackTarget(), range);
      }
      return false;
    }

    @Override
    public void startExecuting() {
      SirenEntity.this.setCharming(true);
      this.progress = 1;
    }
    
    @Override
    public boolean shouldContinueExecuting() {
      return this.progress > 0 && SirenEntity.this.getAttackTarget() != null 
          && SirenEntity.this.isEntityInRange(SirenEntity.this.getAttackTarget(), range);
    }
    
    @Override
    public void tick() {
      super.tick();
      final LivingEntity target = SirenEntity.this.getAttackTarget();
      final double disSq = SirenEntity.this.getEyePosition(1.0F).distanceTo(target.getPositionVec());
      SirenEntity.this.getNavigator().clearPath();
      SirenEntity.this.getLookController().setLookPositionWithEntity(target, 100.0F, 100.0F);
      // inflict nausea
      target.addPotionEffect(nausea);
      if(disSq > 3.5D) {
        // move the target toward this entity
        // TODO force boats to move toward the entity (boats reset velocity every tick)
        final Entity attract = /* target.getRidingEntity() instanceof BoatEntity ? target.getRidingEntity() : */target;
        attractEntity(attract, disSq);
      } else {
        // attack the target
        SirenEntity.this.useCharmingAttack(target);
        this.resetTask();
      }
    }
    
    @Override
    public void resetTask() {
      this.progress = 0;
      this.cooldown = maxCooldown;
      SirenEntity.this.setCharming(false);
    }
    
    private void attractEntity(final Entity entity, final double disSq) {
      // calculate the motion strength to apply
      //final double motion = 0.12 * Math.pow(1.25, -(MathHelper.sqrt(disSq) * (range / 200.0D)));
      final double motion = 0.06D + 0.009D * (1.0D - (disSq / (range * range)));
      final Vector3d vec = SirenEntity.this.getPositionVec().subtract(entity.getPositionVec())
          .normalize().scale(motion);
      entity.addVelocity(vec.x, vec.y, vec.z);
      entity.velocityChanged = true;
    }
  }
  
//  class SwimUpGoal extends greekfantasy.entity.ai.SwimUpGoal<SirenEntity> {
//
//    public SwimUpGoal(double speedIn, int seaLevel) {
//      super(SirenEntity.this, speedIn, seaLevel);
//    }
//    
//  }

}
