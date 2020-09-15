package greekfantasy.block;

import greekfantasy.GFRegistry;
import greekfantasy.GreekFantasy;
import greekfantasy.gui.StatueContainer;
import greekfantasy.tileentity.StatueTileEntity;
import greekfantasy.util.ModelPart;
import greekfantasy.util.StatuePose;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.material.MaterialColor;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.inventory.InventoryHelper;
import net.minecraft.inventory.container.SimpleNamedContainerProvider;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.state.EnumProperty;
import net.minecraft.state.StateContainer;
import net.minecraft.state.properties.BlockStateProperties;
import net.minecraft.state.properties.DoubleBlockHalf;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.Hand;
import net.minecraft.util.IStringSerializable;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.shapes.IBooleanFunction;
import net.minecraft.util.math.shapes.ISelectionContext;
import net.minecraft.util.math.shapes.VoxelShape;
import net.minecraft.util.math.shapes.VoxelShapes;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.IWorldReader;
import net.minecraft.world.World;
import net.minecraftforge.fml.network.NetworkHooks;

public class StatueBlock extends Block {  
  
  public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
  
  protected static final VoxelShape AABB_SLAB_BOTTOM = Block.makeCuboidShape(0.0D, 0.0D, 0.0D, 16.0D, 8.0D, 16.0D);
  protected static final VoxelShape AABB_STATUE = Block.makeCuboidShape(2.0D, 0.0D, 2.0D, 14.0D, 16.0D, 14.0D);
  protected static final VoxelShape AABB_STATUE_TOP = Block.makeCuboidShape(2.0D, 0.0D, 2.0D, 14.0D, 24.0D, 14.0D);
  
  private final StatueMaterial statueMaterial;
    
  public StatueBlock(final StatueMaterial material) {
    super(Block.Properties.create(Material.ROCK, MaterialColor.LIGHT_GRAY).hardnessAndResistance(1.5F, 6.0F).sound(SoundType.STONE).notSolid());
    this.setDefaultState(this.getStateContainer().getBaseState()
        .with(HALF, DoubleBlockHalf.LOWER));
    this.statueMaterial = material;
  }
  
  @Override
  protected void fillStateContainer(StateContainer.Builder<Block, BlockState> builder) {
    builder.add(HALF);
  }

  @Override
  public void onBlockPlacedBy(World worldIn, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
    // place upper block
    worldIn.setBlockState(pos.up(), state.with(HALF, DoubleBlockHalf.UPPER), 3);
    // update body rotations to face player by default
    final float rotation = placer.getHorizontalFacing().getOpposite().getHorizontalAngle();
    final TileEntity te = worldIn.getTileEntity(pos);
    if(te instanceof StatueTileEntity) {
      ((StatueTileEntity)te).getStatuePose().set(ModelPart.BODY, 0, rotation, 0);
      te.markDirty();
      worldIn.notifyBlockUpdate(pos, state, state, 2);
    }
  }
  
  @Override
  public void onBlockHarvested(World worldIn, BlockPos pos, BlockState state, PlayerEntity player) {
    final DoubleBlockHalf half = state.get(HALF);
    final boolean isUpper = half == DoubleBlockHalf.UPPER;
    final BlockPos tePos = isUpper ? pos.down() : pos;
    // drop items from inventory
    TileEntity tileentity = worldIn.getTileEntity(tePos);
    if (!worldIn.isRemote() && tileentity instanceof StatueTileEntity) {
      InventoryHelper.dropItems(worldIn, pos, ((StatueTileEntity) tileentity).getInventory());
    }
    // replace other block with air
    final BlockPos otherHalf = isUpper ? pos.down() : pos.up();
    worldIn.setBlockState(otherHalf, Blocks.AIR.getDefaultState(), 3);
    worldIn.playEvent(player, 2001, pos, Block.getStateId(state));
    super.onBlockHarvested(worldIn, pos, state, player);
  }
  
  @Override
  public void onReplaced(BlockState state, World worldIn, BlockPos pos, BlockState newState, boolean isMoving) {
    if (!state.isIn(newState.getBlock())) {
      final DoubleBlockHalf half = state.get(HALF);
      final boolean isUpper = half == DoubleBlockHalf.UPPER;
      final BlockPos tePos = isUpper ? pos.down() : pos;
      // drop items from inventory
      TileEntity tileentity = worldIn.getTileEntity(tePos);
      if (!worldIn.isRemote() && tileentity instanceof StatueTileEntity) {
        InventoryHelper.dropItems(worldIn, pos, ((StatueTileEntity) tileentity).getInventory());
      }
      // replace other block with air
      final BlockPos otherHalf = isUpper ? pos.down() : pos.up();
      worldIn.setBlockState(otherHalf, Blocks.AIR.getDefaultState(), 3);

      super.onReplaced(state, worldIn, pos, newState, isMoving);
    }
 }

  @Override
  public boolean isValidPosition(BlockState state, IWorldReader worldIn, BlockPos pos) {
    return worldIn.isAirBlock(pos.up());
  }

  @Override
  public ActionResultType onBlockActivated(final BlockState state, final World worldIn, final BlockPos pos,
      final PlayerEntity playerIn, final Hand handIn, final BlockRayTraceResult hit) {
    final BlockPos tePos = state.get(HALF) == DoubleBlockHalf.UPPER ? pos.down() : pos;
    final TileEntity te = worldIn.getTileEntity(tePos);
    if (playerIn instanceof ServerPlayerEntity && te instanceof StatueTileEntity) {
      final StatueTileEntity teStatue = (StatueTileEntity)te;
      // handle item interaction
      final ItemStack stack = playerIn.getHeldItem(handIn);
      if(!stack.isEmpty() && stack.getItem() == Items.NAME_TAG && stack.hasDisplayName()) {        
        teStatue.setTextureName(stack.getDisplayName().getUnformattedComponentText(), true);
        stack.shrink(1);
        return ActionResultType.SUCCESS;
      }
      // get info to send to the GUI constructor and byte buffer
      final StatuePose currentPose = teStatue.getStatuePose();
      final boolean isFemale = teStatue.isStatueFemale();
      final String name = teStatue.getTextureName();
      // open the container GUI
      NetworkHooks.openGui((ServerPlayerEntity)playerIn, 
        new SimpleNamedContainerProvider((id, inventory, player) -> new StatueContainer(id, inventory, teStatue, currentPose, isFemale, name, tePos), 
            StringTextComponent.EMPTY), 
            buf -> {
              buf.writeBoolean(isFemale);
              buf.writeBlockPos(tePos);
              buf.writeCompoundTag(currentPose.serializeNBT());
              buf.writeString(name);
            }
        );
      return ActionResultType.CONSUME;
    } else {
      return ActionResultType.SUCCESS;
    }
  }
  
  @Override
  public VoxelShape getShape(final BlockState state, final IBlockReader worldIn, final BlockPos pos, final ISelectionContext cxt) {
    VoxelShape shape = VoxelShapes.empty();
    if(state.get(HALF) == DoubleBlockHalf.UPPER) {
      shape = AABB_STATUE_TOP;
    } else {
      shape = VoxelShapes.combine(AABB_SLAB_BOTTOM, AABB_STATUE, IBooleanFunction.OR).simplify();
    }
    
    return shape;
  }
  
  @Override
  public boolean hasTileEntity(final BlockState state) {
    return true;
  }
  
  @Override
  public TileEntity createTileEntity(final BlockState state, final IBlockReader world) {
    final StatueTileEntity te = GFRegistry.STATUE_TE.create();
    te.setUpper(state.get(HALF) == DoubleBlockHalf.UPPER);
    te.setStatueFemale(this.RANDOM.nextBoolean());
    return te;
  }
  
  public StatueMaterial getStatueMaterial() {
    return this.statueMaterial;
  }
  
  public static enum StatueMaterial implements IStringSerializable {
    LIMESTONE("limestone"),
    MARBLE("marble");
    
    private final ResourceLocation stoneTexture;
    private final String name;
    
    private StatueMaterial(final String nameIn) {
      this.name = nameIn;
      this.stoneTexture = new ResourceLocation(GreekFantasy.MODID, "textures/entity/statue/" + nameIn + ".png");
    }
    
    public ResourceLocation getStoneTexture() {
      return this.stoneTexture;
    }
    
    public byte getId() {
      return (byte) this.ordinal();
    }
    
    public static StatueMaterial getById(final byte id) {
      return values()[id];
    }

    @Override
    public String getString() {
      return name;
    }
  }
}