package greekfantasy.entity;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nullable;

import greekfantasy.GreekFantasy;
import greekfantasy.entity.ai.SummonMobGoal;
import greekfantasy.util.PanfluteMusicManager;
import net.minecraft.block.BlockState;
import net.minecraft.block.CampfireBlock;
import net.minecraft.entity.CreatureEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.IAngerable;
import net.minecraft.entity.ILivingEntityData;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MobEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.ai.RandomPositionGenerator;
import net.minecraft.entity.ai.attributes.AttributeModifierMap;
import net.minecraft.entity.ai.attributes.Attributes;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.HurtByTargetGoal;
import net.minecraft.entity.ai.goal.LookAtGoal;
import net.minecraft.entity.ai.goal.LookRandomlyGoal;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.ai.goal.MoveToBlockGoal;
import net.minecraft.entity.ai.goal.NearestAttackableTargetGoal;
import net.minecraft.entity.ai.goal.RandomWalkingGoal;
import net.minecraft.entity.ai.goal.ResetAngerGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.passive.WolfEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.pathfinding.PathNodeType;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.DamageSource;
import net.minecraft.util.Direction;
import net.minecraft.util.Hand;
import net.minecraft.util.RangedInteger;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.TickRangeConverter;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.GameRules;
import net.minecraft.world.IServerWorld;
import net.minecraft.world.IWorldReader;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

public class SatyrEntity extends CreatureEntity implements IAngerable {
  
  private static final DataParameter<Byte> DATA_STATE = EntityDataManager.createKey(SatyrEntity.class, DataSerializers.BYTE);
  private static final DataParameter<Boolean> DATA_SHAMAN = EntityDataManager.createKey(SatyrEntity.class, DataSerializers.BOOLEAN);
  private static final String KEY_SHAMAN = "Shaman";
  
  private static final Direction[] HORIZONTALS = { Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST };

  private static final ResourceLocation DANCING_SONG = new ResourceLocation(GreekFantasy.MODID, "greensleeves");
  private static final ResourceLocation SUMMONING_SONG = new ResourceLocation(GreekFantasy.MODID, "sarias_song");

  // NONE, DANCING, and SUMMONING are values for DATA_STATE
  protected static final byte NONE = 0;
  protected static final byte DANCING = 1;
  protected static final byte SUMMONING = 2;
  // sent from server to client to trigger sound
  protected static final byte PLAY_SUMMON_SOUND = 12;
  
  protected static final int MAX_SUMMON_TIME = 160;
  protected static final int MAX_PANFLUTE_TIME = 10;
  public int holdingPanfluteTime;
  public int summonTime;
  public boolean hasShamanTexture;
  
  private static final RangedInteger ANGER_RANGE = TickRangeConverter.convertRange(20, 39);
  private int angerTime;
  private UUID angerTarget;
  
  private Optional<BlockPos> campfirePos = Optional.empty();
    
  private final Goal meleeAttackGoal;
  private final Goal summonAnimalsGoal;
  
  public SatyrEntity(final EntityType<? extends SatyrEntity> type, final World worldIn) {
    super(type, worldIn);
    this.setPathPriority(PathNodeType.DANGER_FIRE, -1.0F);
    this.setPathPriority(PathNodeType.DAMAGE_FIRE, -1.0F);
    meleeAttackGoal = new MeleeAttackGoal(this, 1.0D, false);
    summonAnimalsGoal = new SummonAnimalsGoal(MAX_SUMMON_TIME, 280);
  }
  
  public static AttributeModifierMap.MutableAttribute getAttributes() {
    return MobEntity.func_233666_p_()
        .createMutableAttribute(Attributes.MAX_HEALTH, 24.0D)
        .createMutableAttribute(Attributes.MOVEMENT_SPEED, 0.25D)
        .createMutableAttribute(Attributes.ATTACK_DAMAGE, 1.0D);
  }
  
  @Override
  public void registerData() {
    super.registerData();
    this.getDataManager().register(DATA_STATE, Byte.valueOf(NONE));
    this.getDataManager().register(DATA_SHAMAN, Boolean.valueOf(false));
  }
  
  @Override
  protected void registerGoals() {
    super.registerGoals();
    this.goalSelector.addGoal(0, new SwimGoal(this));
    this.goalSelector.addGoal(2, new SatyrEntity.DancingGoal(0.75D, 880));
    this.goalSelector.addGoal(3, new SatyrEntity.PanicGoal(this, 1.3D));
    this.goalSelector.addGoal(4, new SatyrEntity.StartDancingGoal(0.9D, 22, 12, 420));
    this.goalSelector.addGoal(4, new SatyrEntity.LightCampfireGoal(0.9D, 12, 10, 60, 500));
    this.goalSelector.addGoal(4, new RandomWalkingGoal(this, 0.8D, 160) {
      @Override
      public boolean shouldExecute() { 
        return SatyrEntity.this.isIdleState() && SatyrEntity.this.getAttackTarget() == null && super.shouldExecute(); 
      }
    });
    this.goalSelector.addGoal(5, new LookAtGoal(this, PlayerEntity.class, 6.0F));
    this.goalSelector.addGoal(6, new LookRandomlyGoal(this));
    this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
    this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, PlayerEntity.class, 10, true, false, this::func_233680_b_));
    this.targetSelector.addGoal(3, new ResetAngerGoal<>(this, true));
  }
  
  @Override
  public void livingTick() {
    super.livingTick();
    if (this.world.isRemote()) {
      // play music
      if(isSummoning()) {
        PanfluteMusicManager.playMusic(this, SUMMONING_SONG, summonTime, 0.92F, 0.34F);
      } else if(isDancing()) {
        PanfluteMusicManager.playMusic(this, DANCING_SONG, world.getGameTime(), 0.84F, 0.28F);
      }
    } else {
      // anger timer
      this.func_241359_a_((ServerWorld) this.world, true);
      // campfire checker
      if(this.ticksExisted % 60 == 1 && campfirePos.isPresent() && !isValidCampfire(world, campfirePos.get())) {
        campfirePos = Optional.empty();
        setDancing(false);
      }
    }

    // dancing timer
    if(isDancing() || isSummoning()) {
      this.holdingPanfluteTime = Math.min(this.holdingPanfluteTime + 1, MAX_PANFLUTE_TIME);
    } else {
      this.holdingPanfluteTime = Math.max(this.holdingPanfluteTime - 1, 0);
    }
    
    // summon timer
    if(summonTime > 0 && summonTime++ > MAX_SUMMON_TIME) {
      summonTime = 0;
    }   
  }
  
  @Override
  public boolean attackEntityFrom(final DamageSource source, final float amount) {
    final boolean attackEntityFrom = super.attackEntityFrom(source, amount);
    if(attackEntityFrom && source.getImmediateSource() instanceof LivingEntity) {
      // alert all nearby satyr shamans
      final LivingEntity target = (LivingEntity)source.getImmediateSource();
  	  final List<SatyrEntity> shamans = this.getEntityWorld().getEntitiesWithinAABB(SatyrEntity.class, this.getBoundingBox().grow(10.0D), e -> e.isShaman());
  	  for(final SatyrEntity shaman : shamans) {
  	    if(shaman.getAttackTarget() == null) { // if IAngerable#canTargetEntity
  	  	  shaman.setAngerTarget(target.getUniqueID());
  	  	  shaman.setAngerTime(ANGER_RANGE.getRandomWithinRange(this.rand));
  	    }
  	  }   
    }
    
    return attackEntityFrom;
  }
  
  @Override
  public boolean isInvulnerableTo(final DamageSource source) {
    if(super.isInvulnerableTo(source)) {
      return true;
    }
    // immune to fire when on top of campfires
    final BlockState campfire = world.getBlockState(this.getPositionUnderneath());
    return source == DamageSource.IN_FIRE && campfire.isIn(BlockTags.CAMPFIRES) && campfire.get(CampfireBlock.LIT);
  }
 
  @Override
  public ILivingEntityData onInitialSpawn(IServerWorld worldIn, DifficultyInstance difficultyIn, SpawnReason reason,
      @Nullable ILivingEntityData spawnDataIn, @Nullable CompoundNBT dataTag) {
    spawnDataIn = super.onInitialSpawn(worldIn, difficultyIn, reason, spawnDataIn, dataTag);
    // random chance to be a satyr shaman
    if(worldIn.getRandom().nextInt(100) < GreekFantasy.CONFIG.getSatyrShamanChance()) {
      this.setShaman(true);
    }
    return spawnDataIn;
  }
  
  @Override
  public void notifyDataManagerChange(final DataParameter<?> key) {
    super.notifyDataManagerChange(key);
    if(key == DATA_SHAMAN) {
      // change AI task for shaman / non-shaman
      updateCombatAI();
      // update client-side field
      this.hasShamanTexture = this.isShaman();
    } else if(key == DATA_STATE) {
      // update summon time
      if(this.isSummoning()) {
        this.summonTime = 1;
      } else {
        this.summonTime = 0;
      }
    }
  }
  
  @OnlyIn(Dist.CLIENT)
  public void handleStatusUpdate(byte id) {
    if(id == PLAY_SUMMON_SOUND) {
      this.getEntityWorld().playSound(this.getPosX(), this.getPosY(), this.getPosZ(), SoundEvents.ENTITY_WOLF_HOWL, this.getSoundCategory(), 1.1F, 0.9F + this.getRNG().nextFloat() * 0.2F, false);
      this.summonTime = 0;
    } else {
      super.handleStatusUpdate(id);
    }
  }

  @Override
  public void writeAdditional(CompoundNBT compound) {
    super.writeAdditional(compound);
    compound.putBoolean(KEY_SHAMAN, this.isShaman());
    this.writeAngerNBT(compound);
  }

  @Override
  public void readAdditional(CompoundNBT compound) {
    super.readAdditional(compound);
    this.setShaman(compound.getBoolean(KEY_SHAMAN));
    this.readAngerNBT((ServerWorld)this.world, compound);
  }
  
  //IAngerable methods
  
  @Override
  public void func_230258_H__() { this.setAngerTime(ANGER_RANGE.getRandomWithinRange(this.rand)); }
  @Override
  public void setAngerTime(int time) { this.angerTime = time; }
  @Override
  public int getAngerTime() { return this.angerTime; }
  @Override
  public void setAngerTarget(@Nullable UUID target) { this.angerTarget = target; }
  @Override
  public UUID getAngerTarget() { return this.angerTarget; }
 
  // End IAngerable methods
  
  // Dancing, summoning, and shaman getters/setters
  
  public boolean isIdleState() { return this.getDataManager().get(DATA_STATE).byteValue() == NONE; }
  
  public void setIdleState() { this.getDataManager().set(DATA_STATE, Byte.valueOf(NONE)); }
  
  public boolean isDancing() { return this.getDataManager().get(DATA_STATE).byteValue() == DANCING; }
  
  public void setDancing(final boolean dancing) { this.getDataManager().set(DATA_STATE, Byte.valueOf(dancing ? DANCING : NONE)); }
  
  public boolean isSummoning() { return this.getDataManager().get(DATA_STATE).byteValue() == SUMMONING; }
  
  public void setSummoning(final boolean summoning) { this.getDataManager().set(DATA_STATE, Byte.valueOf(summoning ? SUMMONING : NONE)); }
  
  public boolean isShaman() { return this.getDataManager().get(DATA_SHAMAN); }
  
  public void setShaman(final boolean shaman) { 
    this.hasShamanTexture = shaman;
    this.getDataManager().set(DATA_SHAMAN, Boolean.valueOf(shaman));
    if(this.isServerWorld()) {
      updateCombatAI();
    }
  }
  
  protected void updateCombatAI() {
    if(this.isServerWorld()) {
      if(this.isShaman() && GreekFantasy.CONFIG.SATYR_ATTACK.get()) {
        this.goalSelector.addGoal(1, summonAnimalsGoal);
        this.goalSelector.removeGoal(meleeAttackGoal);
      } else {
        this.goalSelector.addGoal(1, meleeAttackGoal);
        this.goalSelector.removeGoal(summonAnimalsGoal);
      }
    }
  }
  
  public boolean hasShamanTexture() {
    return this.hasShamanTexture;
  }
    
  /**
   * Only used client-side. Calculates the portion of dancing
   * or summoning completed up to 8 ticks so that the model 
   * animates in that time.
   * @return the percent
   **/
  public float getArmMovementPercent(final float ageInTicks) {
    final float time = this.holdingPanfluteTime;
    return Math.min(1.0F, (float)time / (float)MAX_PANFLUTE_TIME);
  }
  
  /**
   * @param world the entity's world
   * @param pos the BlockPos to check around
   * @return if the given pos is a campfire with empty space around it
   **/
  protected static boolean isValidCampfire(final IWorldReader world, final BlockPos pos) {
    // check if the block is actually a campfire
    final BlockState campfire = world.getBlockState(pos);
    if(!campfire.isIn(BlockTags.CAMPFIRES) || !campfire.get(CampfireBlock.LIT)) {
      return false;
    }
    // check surrounding area (only flat or passable terrain is allowed)
    for(int x = -1; x <= 1; x++) {
      for(int z = -1; z <= 1; z++) {
        if(!(x == 0 && z == 0)) {
          // check for impassable blocks
          final BlockPos p = pos.add(x, 0, z);
          if(!world.getBlockState(p.down()).getMaterial().isSolid() || 
              world.getBlockState(p).isSolid() ||
              world.getBlockState(p).getMaterial().blocksMovement()) {
            return false;
          }
        }
      }
    }
    return true;
  }
  
  /**
   * @param world the world
   * @param pos the center pos
   * @param lit whether to check for a lit or unlit campfire
   * @return an Optional containing the campfire pos if found, otherwise empty
   **/
  protected static Optional<BlockPos> getCampfireNear(final IWorldReader world, final BlockPos pos, final boolean lit) {
    // check surrounding area (including diagonals)
    for (int x = -2; x <= 2; x++) {
      for (int z = -2; z <= 2; z++) {
        // check for impassable blocks
        final BlockPos p = pos.add(x, 0, z);
        final BlockState state = world.getBlockState(p);
        if (state.isIn(BlockTags.CAMPFIRES) && (state.get(CampfireBlock.LIT) == lit)) {
          return Optional.of(p);
        }
      }
    }
    return Optional.empty();
  }

  class SummonAnimalsGoal extends SummonMobGoal<WolfEntity> {
    
    public SummonAnimalsGoal(final int summonProgressIn, final int summonCooldownIn) {
      super(SatyrEntity.this, summonProgressIn, summonCooldownIn, EntityType.WOLF, 3);
    }
    
    @Override
    public void startExecuting() {
      super.startExecuting();
      SatyrEntity.this.setSummoning(true);
    }
    
    @Override
    public void tick() {
      super.tick();
      // soft reset when entity is hurt
      if(SatyrEntity.this.hurtTime != 0) {
        this.progress = 0;
        this.cooldown = 30;
        SatyrEntity.this.setSummoning(false);
      }
    }
    
    @Override
    public void resetTask() {
      super.resetTask();
      SatyrEntity.this.setSummoning(false);
    }
    
    @Override
    protected void summonMob(final WolfEntity mobEntity) {
      mobEntity.setAngerTime(800);
      mobEntity.setAngerTarget(SatyrEntity.this.getAttackTarget().getUniqueID());
      super.summonMob(mobEntity);
    }
    
  }
  
  class PanicGoal extends net.minecraft.entity.ai.goal.PanicGoal {
    public PanicGoal(CreatureEntity entity, double speed) {
      super(entity, speed);
    }

    @Override
    public boolean shouldExecute() {
      return SatyrEntity.this.getAttackTarget() == null && super.shouldExecute();
    }
    
    @Override
    public void tick() {
      SatyrEntity.this.setIdleState();
      super.tick();
    }

  }
  
  class DancingGoal extends Goal {
    
    private final int maxTravelTime = 100;
    private final int maxDancingTime;
    
    private Optional<Vector3d> targetPos = Optional.empty();
    
    protected final double moveSpeed;
    private int dancingTime = 0;
    private int travelTime = 0;
    
    public DancingGoal(final double speedIn, final int dancingTimeIn) {
      this.setMutexFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.JUMP));
      this.moveSpeed = speedIn;
      this.maxDancingTime = dancingTimeIn;
   }
    
    @Override
    public boolean shouldExecute() {
      return SatyrEntity.this.isDancing() && this.updateTarget();
    }
    
    @Override
    public void startExecuting() {
      super.startExecuting();
      if(targetPos.isPresent()) {
        SatyrEntity.this.getNavigator().tryMoveToXYZ(targetPos.get().x, targetPos.get().y, targetPos.get().z, moveSpeed);
        dancingTime = 1;
      }
    }
    
    @Override
    public boolean shouldContinueExecuting() {
      boolean isCampfireValid = true;
      if(SatyrEntity.this.ticksExisted % 15 == 1) {
        isCampfireValid = SatyrEntity.this.campfirePos.isPresent() && SatyrEntity.isValidCampfire(SatyrEntity.this.getEntityWorld(), SatyrEntity.this.campfirePos.get());
      }
      return SatyrEntity.this.getAttackTarget() == null && SatyrEntity.this.hurtTime == 0 && this.targetPos.isPresent() && isCampfireValid;
    }
    
    @Override
    public void tick() {
      super.tick();
      if(this.dancingTime++ < maxDancingTime && this.travelTime++ < maxTravelTime) {         
        // if we're close to the targetPos, update targetPos and path
        if(isNearTarget(1.2D)) {
          this.updateTarget();
          SatyrEntity.this.jump();
          SatyrEntity.this.getNavigator().tryMoveToXYZ(targetPos.get().x, targetPos.get().y, targetPos.get().z, moveSpeed);
          this.travelTime = 0;
        }
      } else {
        resetTask();
      }
    }
   
    @Override
    public void resetTask() {
      // move out of the way
      if(SatyrEntity.this.campfirePos.isPresent() && campfirePos.get().withinDistance(SatyrEntity.this.getPosition(), 4.0D)) {
        final Vector3d vec = RandomPositionGenerator.findRandomTargetBlockAwayFrom(SatyrEntity.this, 4, 4, Vector3d.copyCenteredHorizontally(SatyrEntity.this.campfirePos.get()));
        if(vec != null) {
          SatyrEntity.this.getNavigator().tryMoveToXYZ(vec.getX(), vec.getY(), vec.getZ(), this.moveSpeed);
        }
      } else {
        SatyrEntity.this.getNavigator().clearPath();
      }
      // reset values
      SatyrEntity.this.campfirePos = Optional.empty();
      this.targetPos = Optional.empty();
      this.dancingTime = 0;
      this.travelTime = 0;
      SatyrEntity.this.setDancing(false);
    }
 
    /**
     * Checks if a campfire has been found, and if so, updates
     * which block the entity should path toward
     * @return whether there is now a targetPos to move toward
     **/
    private boolean updateTarget() {
      if(SatyrEntity.this.campfirePos.isPresent()) {
        final Direction nextDir = getClosestDirection().rotateY();
        final BlockPos target = campfirePos.get().offset(nextDir);
        this.targetPos = Optional.of(new Vector3d(target.getX() + 0.5D, target.getY(), target.getZ() + 0.5D));
        return true;
      }
      return false;
    }
    
    /**
     * Checks each possible direction offset from a campfire
     * @return the Direction offset that is closest to the entity
     **/
    private Direction getClosestDirection() {
      Direction dClosest = Direction.NORTH;
      final Vector3d curPos = SatyrEntity.this.getPositionVec();
      double dMin = 100.0D;
      if(SatyrEntity.this.campfirePos.isPresent()) {
        for(final Direction dir : HORIZONTALS) {
          final BlockPos dPos = campfirePos.get().offset(dir);
          final Vector3d dVec = new Vector3d(dPos.getX() + 0.5D, dPos.getY(), dPos.getZ() + 0.5D);
          final double dSq = curPos.squareDistanceTo(dVec);
          if(dSq < dMin) {
            dClosest = dir;
            dMin = dSq;
          }
        }
      }
      return dClosest;
    }
    
    private boolean isNearTarget(final double distance) {
      return this.targetPos.isPresent() && this.targetPos.get().isWithinDistanceOf(SatyrEntity.this.getPositionVec(), distance);
    }
  }
  
  class StartDancingGoal extends MoveToBlockGoal {
    protected final int chance;
    
    public StartDancingGoal(final double speed, final int searchLength, final int radius, final int chanceIn) {
      super(SatyrEntity.this, speed, searchLength, radius);
      chance = chanceIn;
    }
    
    @Override
    public boolean shouldExecute() {
      return SatyrEntity.this.getAttackTarget() == null && SatyrEntity.this.isIdleState() && super.shouldExecute();
    }

    @Override
    protected boolean shouldMoveTo(IWorldReader worldIn, BlockPos pos) {
      // checks if the given block is within 1 block of a campfire
      for(final Direction d : SatyrEntity.HORIZONTALS) {
        if(SatyrEntity.isValidCampfire(worldIn, pos.offset(d, 1))) {
          return true;
        }
      }
      return false; 
    }
    
    @Override
    public double getTargetDistanceSq() {
      return 2.0D;
    }
    
    @Override
    public void tick() {
      if (this.getIsAboveDestination()) {
        // check surrounding blocks to find a campfire
        final Optional<BlockPos> campfire = SatyrEntity.getCampfireNear(SatyrEntity.this.world, SatyrEntity.this.getPosition(), true);
        if (campfire.isPresent() && SatyrEntity.isValidCampfire(SatyrEntity.this.world, campfire.get())) {
          SatyrEntity.this.campfirePos = campfire;
          SatyrEntity.this.setDancing(true);
        } else {
          resetTask();
        }
      }
      super.tick();
    }
   
    @Override
    protected int getRunDelay(CreatureEntity entity) { return 200 + entity.getRNG().nextInt(chance); }
  }
  
  class LightCampfireGoal extends MoveToBlockGoal {
    protected final int maxLightCampfireTime;
    protected final int chance;

    protected int lightCampfireTimer;
    

    public LightCampfireGoal(double speed, int searchLength, int radius, int maxLightTime, int chanceIn) {
      super(SatyrEntity.this, speed, searchLength, radius);
      maxLightCampfireTime = maxLightTime;
      chance = chanceIn;
    }

    @Override
    protected boolean shouldMoveTo(IWorldReader worldIn, BlockPos pos) {
      // checks if the given block is within 2 blocks of an unlit campfire
      for(final Direction d : SatyrEntity.HORIZONTALS) {
        final BlockState blockstate = worldIn.getBlockState(pos.offset(d, 1));
        if(blockstate.isIn(BlockTags.CAMPFIRES) && !blockstate.get(CampfireBlock.LIT)) {
          return true;
        }
      }
      return false;
    }
    
    @Override
    public double getTargetDistanceSq() {
      return 2.0D;
    }

    @Override
    public boolean shouldExecute() {
      return SatyrEntity.this.isIdleState()
          && SatyrEntity.this.getAttackTarget() == null 
          && !SatyrEntity.this.getEntityWorld().isRaining()
          && SatyrEntity.this.getEntityWorld().getGameRules().getBoolean(GameRules.MOB_GRIEFING)
          && super.shouldExecute();
    }

    @Override
    public void startExecuting() {
      this.lightCampfireTimer = 0;
      super.startExecuting();
    }

    @Override
    public void tick() {
      final Optional<BlockPos> campfire = SatyrEntity.getCampfireNear(SatyrEntity.this.world, SatyrEntity.this.getPosition(), false);
      if (this.getIsAboveDestination() && campfire.isPresent()) {
        if (this.lightCampfireTimer >= maxLightCampfireTime) {
          // find and light campfire
          this.lightCampfire(campfire.get());
        } else {
          ++this.lightCampfireTimer;
          SatyrEntity.this.playSound(SoundEvents.ITEM_FLINTANDSTEEL_USE, 1.0F, 1.0F);
          SatyrEntity.this.swingArm(Hand.MAIN_HAND);
          SatyrEntity.this.getLookController().setLookPosition(Vector3d.copyCenteredHorizontally(campfire.get()));
        }
      }
      super.tick();
    }
    
    @Override
    protected int getRunDelay(CreatureEntity entity) { return 200 + entity.getRNG().nextInt(chance); }

    protected boolean lightCampfire(final BlockPos pos) {
      if (net.minecraftforge.event.ForgeEventFactory.getMobGriefingEvent(SatyrEntity.this.world, SatyrEntity.this)) {
        final BlockState state = SatyrEntity.this.world.getBlockState(pos);
        if (state.isIn(BlockTags.CAMPFIRES)) {
          SatyrEntity.this.playSound(SoundEvents.ITEM_FLINTANDSTEEL_USE, 1.0F, 1.0F);
          SatyrEntity.this.world.setBlockState(pos, state.with(CampfireBlock.LIT, Boolean.valueOf(true)), 2);
          SatyrEntity.this.swingArm(Hand.MAIN_HAND);
          return true;
        }
      }
      return false;
    }
  }
  
}
