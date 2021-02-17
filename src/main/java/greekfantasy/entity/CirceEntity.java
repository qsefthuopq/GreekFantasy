package greekfantasy.entity;

import java.util.EnumSet;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import greekfantasy.GFRegistry;
import greekfantasy.GreekFantasy;
import greekfantasy.deity.favor_effect.ConfiguredFavorRange;
import greekfantasy.entity.ai.IntervalRangedAttackGoal;
import greekfantasy.entity.misc.SwineSpellEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ILivingEntityData;
import net.minecraft.entity.IRangedAttackMob;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MobEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.ai.attributes.AttributeModifierMap;
import net.minecraft.entity.ai.attributes.Attributes;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.HurtByTargetGoal;
import net.minecraft.entity.ai.goal.LookAtGoal;
import net.minecraft.entity.ai.goal.LookRandomlyGoal;
import net.minecraft.entity.ai.goal.NearestAttackableTargetGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.goal.WaterAvoidingRandomWalkingGoal;
import net.minecraft.entity.merchant.villager.AbstractVillagerEntity;
import net.minecraft.entity.monster.MonsterEntity;
import net.minecraft.entity.monster.WitchEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.inventory.EquipmentSlotType;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.Hand;
import net.minecraft.util.SoundEvents;
import net.minecraft.world.BossInfo;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.IServerWorld;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerBossInfo;

public class CirceEntity extends MonsterEntity implements IRangedAttackMob {
  
  private final ServerBossInfo bossInfo = (ServerBossInfo)(new ServerBossInfo(this.getDisplayName(), BossInfo.Color.PINK, BossInfo.Overlay.PROGRESS));
  
  protected static final Predicate<LivingEntity> NOT_SWINE = e -> (e != null && null == e.getActivePotionEffect(GFRegistry.SWINE_EFFECT));
  
  public CirceEntity(final EntityType<? extends CirceEntity> type, final World worldIn) {
    super(type, worldIn);
  }
  
  public static AttributeModifierMap.MutableAttribute getAttributes() {
    return MobEntity.func_233666_p_()
        .createMutableAttribute(Attributes.MAX_HEALTH, 60.0D)
        .createMutableAttribute(Attributes.MOVEMENT_SPEED, 0.25D)
        .createMutableAttribute(Attributes.ATTACK_DAMAGE, 1.0D)
        .createMutableAttribute(Attributes.ARMOR, 1.5D);
  }
  
  @Override
  protected void registerGoals() {
    super.registerGoals();
    this.goalSelector.addGoal(0, new SwimGoal(this));
    this.goalSelector.addGoal(1, new IntervalRangedAttackGoal(this, 90, 1, GreekFantasy.CONFIG.getSwineWandCooldown() * 6));
    this.goalSelector.addGoal(4, new WaterAvoidingRandomWalkingGoal(this, 0.8D));
    this.goalSelector.addGoal(5, new LookAtGoal(this, PlayerEntity.class, 6.0F));
    this.goalSelector.addGoal(6, new LookRandomlyGoal(this));
    this.targetSelector.addGoal(1, new HurtByTargetGoal(this, WitchEntity.class, CirceEntity.class));
    this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, PlayerEntity.class, 10, true, false, NOT_SWINE));
    this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, AbstractVillagerEntity.class, 10, true, false, NOT_SWINE));
    this.targetSelector.addGoal(4, new ResetTargetGoal());
  }
  
  @Override
  public void livingTick() {
    super.livingTick();
    // boss info
    this.bossInfo.setPercent(this.getHealth() / this.getMaxHealth());    
  }
  
  @Override
  public void writeAdditional(CompoundNBT compound) {
    super.writeAdditional(compound);
  }

  @Override
  public void readAdditional(CompoundNBT compound) {
    super.readAdditional(compound);
  }
  
  @Nullable
  public ILivingEntityData onInitialSpawn(IServerWorld worldIn, DifficultyInstance difficultyIn, SpawnReason reason,
      @Nullable ILivingEntityData spawnDataIn, @Nullable CompoundNBT dataTag) {
    ILivingEntityData data = super.onInitialSpawn(worldIn, difficultyIn, reason, spawnDataIn, dataTag);
    this.setHeldItem(Hand.MAIN_HAND, new ItemStack(GFRegistry.SWINE_WAND));
    return data;
  }
  
  // Attack //

  @Override
  public void attackEntityWithRangedAttack(LivingEntity arg0, float arg1) {
    if (!world.isRemote()) {
      SwineSpellEntity spell = SwineSpellEntity.create(world, this);
      world.addEntity(spell);
    }
    this.playSound(SoundEvents.ENTITY_ILLUSIONER_CAST_SPELL, 1.2F, 1.0F);
    // swing arm
    this.swingArm(Hand.MAIN_HAND);
    // remove revenge target
    this.setRevengeTarget(null);
  }

  //Boss //

  @Override
  public boolean isNonBoss() { return false; }

  @Override
  public void addTrackingPlayer(ServerPlayerEntity player) {
    super.addTrackingPlayer(player);
    this.bossInfo.addPlayer(player);
    this.bossInfo.setName(this.hasCustomName() ? this.getCustomName() : this.getDisplayName());
    this.bossInfo.setVisible(GreekFantasy.CONFIG.showCirceBossBar());
  }

  @Override
  public void removeTrackingPlayer(ServerPlayerEntity player) {
    super.removeTrackingPlayer(player);
    this.bossInfo.removePlayer(player);
  }
  
  // Goals //
  
  class ResetTargetGoal extends Goal {
    
    protected int interval;
    
    public ResetTargetGoal() { this(10); }
        
    public ResetTargetGoal(int intervalIn) {
      this.setMutexFlags(EnumSet.of(Goal.Flag.TARGET));
      interval = Math.max(1, intervalIn);
    }

    @Override
    public boolean shouldExecute() {
      return CirceEntity.this.ticksExisted % interval == 0 && CirceEntity.this.isAlive() 
          && CirceEntity.this.getAttackTarget() instanceof LivingEntity
          && CirceEntity.this.getAttackTarget() != CirceEntity.this.getRevengeTarget()
          && ((LivingEntity)CirceEntity.this.getAttackTarget()).getActivePotionEffect(GFRegistry.SWINE_EFFECT) != null;
    }
    
    @Override
    public boolean shouldContinueExecuting() { return false; }
    
    @Override
    public void startExecuting() {
      CirceEntity.this.setAttackTarget(null);
    }
  }

}
