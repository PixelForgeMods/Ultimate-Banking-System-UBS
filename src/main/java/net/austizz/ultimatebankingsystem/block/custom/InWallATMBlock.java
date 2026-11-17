package net.austizz.ultimatebankingsystem.block.custom;

import com.mojang.serialization.MapCodec;
import net.austizz.ultimatebankingsystem.block.entity.custom.AtmBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class InWallATMBlock extends ATMBlock {
    public static final MapCodec<InWallATMBlock> CODEC = simpleCodec(InWallATMBlock::new);
    public static final int WALL_CELLS = 6;
    public static final int TOTAL_PARTS = 12;
    public static final IntegerProperty PART = IntegerProperty.create("part", 0, TOTAL_PARTS - 1);
    private static final VoxelShape[][] REAR_SHAPES = new VoxelShape[4][WALL_CELLS];
    private static final VoxelShape[][] MIDDLE_SHAPES = new VoxelShape[4][2];
    private static boolean removing;

    static {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            for (int column = 0; column < 2; column++) {
                double wallMin = column == 0 ? 0 : 8.5;
                double wallMax = column == 0 ? 7.5 : 16;
                double trimMin = column == 0 ? 7.5 : 7.28;
                double trimMax = column == 0 ? 8.72 : 8.5;
                double openingMin = column == 0 ? 8.72 : 0;
                double openingMax = column == 0 ? 16 : 7.28;
                // Step the raycast surface back with the sloped panel.
                MIDDLE_SHAPES[facing.get2DDataValue()][column] = Shapes.or(
                        orientedBox(facing, wallMin, 0, 0, wallMax, 16, 16),
                        orientedBox(facing, trimMin, .4161, -.65, trimMax, 14.6161, 8.55),
                        orientedBox(facing, 0, 0, 0, 16, .4161, 16),
                        orientedBox(facing, openingMin, .4161, -.65, openingMax, 1.5161, 16),
                        orientedBox(facing, openingMin, 1.5161, -.26, openingMax, 2.48, 16),
                        orientedBox(facing, column == 0 ? 7.5 : 0, 14.6161, -.65,
                                column == 0 ? 16 : 8.5, 16, 16),
                        orientedBox(facing, 0, 2.48, 3.45, 16, 6, 16),
                        orientedBox(facing, 0, 6, 4.4, 16, 9, 16),
                        orientedBox(facing, 0, 9, 5.65, 16, 12, 16),
                        orientedBox(facing, 0, 12, 6.9, 16, 14.6161, 16));
            }
            for (int cell = 0; cell < WALL_CELLS; cell++) {
                double minX = cell % 2 == 0 ? 8.27 : 0;
                double maxX = cell % 2 == 0 ? 16 : 7.73;
                REAR_SHAPES[facing.get2DDataValue()][cell] = switch (cell / 2) {
                    case 0 -> rearBox(facing, minX, 0, maxX, 16, 8);
                    case 1 -> Shapes.or(rearBox(facing, minX, 0, maxX, 1.2161, 8),
                            rearBox(facing, minX, .6161, maxX, 16, 11.46));
                    default -> rearBox(facing, minX, 0, maxX, 6.1161, 11.46);
                };
            }
        }
    }

    private static VoxelShape rearBox(Direction facing, double minX, double minY, double maxX, double maxY, double depth) {
        return orientedBox(facing, minX, minY, 0, maxX, maxY, depth);
    }

    private static VoxelShape orientedBox(Direction facing, double minX, double minY, double minZ,
                                          double maxX, double maxY, double maxZ) {
        return switch (facing) {
            case EAST -> Block.box(16 - maxZ, minY, minX, 16 - minZ, maxY, maxX);
            case SOUTH -> Block.box(16 - maxX, minY, 16 - maxZ, 16 - minX, maxY, 16 - minZ);
            case WEST -> Block.box(minZ, minY, 16 - maxX, maxZ, maxY, 16 - minX);
            default -> Block.box(minX, minY, minZ, maxX, maxY, maxZ);
        };
    }

    public static VoxelShape fasciaOutline(Direction facing) {
        return orientedBox(facing, 7.5, 16.4161, -.65, 24.5, 36.9161, .7);
    }

    public static VoxelShape surroundOutline(Direction facing, int part) {
        if (part != 2 && part != 3) return Shapes.block();
        return Shapes.or(orientedBox(facing, 0, 0, 0, 16, .4161, 16),
                orientedBox(facing, part == 2 ? 0 : 8.5, .4161, 0, part == 2 ? 7.5 : 16, 16, 16));
    }

    public InWallATMBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(PART, 0));
    }
    @Override protected MapCodec<? extends HorizontalDirectionalBlock> codec() { return CODEC; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder); builder.add(PART);
    }
    public static BlockPos partPos(BlockPos origin, Direction facing, int part) {
        return origin.relative(facing.getClockWise(), part % 2).above(part % WALL_CELLS / 2)
                .relative(facing.getOpposite(), part / WALL_CELLS);
    }
    public static BlockPos origin(BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof InWallATMBlock)) return pos;
        Direction facing = state.getValue(FACING);
        int part = state.getValue(PART);
        return pos.relative(facing.getClockWise(), -(part % 2)).below(part % WALL_CELLS / 2)
                .relative(facing, part / WALL_CELLS);
    }
    public static boolean isSurround(Vec3 hit, BlockPos origin, Direction facing) {
        Vec3 p = hit.subtract(origin.getX()+.5, origin.getY(), origin.getZ()+.5)
                .yRot((float)Math.toRadians(switch(facing) { case EAST -> 90; case SOUTH -> 180; case WEST -> 270; default -> 0; }))
                .add(.5,0,.5).scale(16);
        return p.x <= 7.5 || p.x >= 24.5 || p.y <= 16.4161 || p.y >= 36.9161;
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        var facing=context.getHorizontalDirection().getOpposite();
        var level=context.getLevel();var pos=context.getClickedPos();
        for(int part=0;part<TOTAL_PARTS;part++) {
            var cell=partPos(pos,facing,part);
            if(!level.isInWorldBounds(cell) || !level.getWorldBorder().isWithinBounds(cell)
                    || !level.getBlockState(cell).canBeReplaced(context)) return null;
        }
        return defaultBlockState().setValue(FACING,facing).setValue(PART,0);
    }
    @Override public void setPlacedBy(Level level,BlockPos pos,BlockState state,LivingEntity placer,ItemStack stack) {
        if(level.isClientSide) return;
        for(int part=1;part<TOTAL_PARTS;part++) level.setBlock(partPos(pos,state.getValue(FACING),part),state.setValue(PART,part),3);
    }

    public boolean restoreRear(Level level, BlockPos origin, BlockState state) {
        Direction facing = state.getValue(FACING);
        for (int part = 0; part < TOTAL_PARTS; part++) {
            BlockPos pos = partPos(origin, facing, part);
            if (!level.hasChunkAt(pos)) return false;
            if (part < WALL_CELLS && !matchesPart(level.getBlockState(pos), facing, part)) return true;
        }
        for (int part = WALL_CELLS; part < TOTAL_PARTS; part++) {
            BlockPos pos = partPos(origin, facing, part);
            if (level.isEmptyBlock(pos)) level.setBlock(pos, state.setValue(PART, part), UPDATE_ALL);
        }
        return true;
    }
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state) {
        return state.getValue(PART)==0 ? super.newBlockEntity(pos,state) : null;
    }
    @Override protected int getLightBlock(BlockState state,BlockGetter level,BlockPos pos) { return 0; }
    @Override protected boolean propagatesSkylightDown(BlockState state,BlockGetter level,BlockPos pos) { return true; }
    @Override protected float getShadeBrightness(BlockState state,BlockGetter level,BlockPos pos) { return 1; }
    @Override public VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context) {
        int part = state.getValue(PART);
        if (part == 2 || part == 3) {
            return MIDDLE_SHAPES[state.getValue(FACING).get2DDataValue()][part - 2];
        }
        return part < WALL_CELLS ? Shapes.block() : REAR_SHAPES[state.getValue(FACING).get2DDataValue()][part - WALL_CELLS];
    }
    @Override public void onRemove(BlockState state,Level level,BlockPos pos,BlockState replacement,boolean piston) {
        if(state.getBlock()!=replacement.getBlock() && !level.isClientSide && !removing) {
            removing=true;
            try {
                var root=origin(pos,state);
                var facing=state.getValue(FACING);
                if(level.getBlockEntity(root) instanceof AtmBlockEntity atm) atm.dropWallMaterials();
                if(state.getValue(PART)!=0 && matchesPart(level.getBlockState(root),facing,0)) level.destroyBlock(root,true);
                for(int part=0;part<TOTAL_PARTS;part++) {
                    var cell=partPos(root,facing,part);
                    if(!cell.equals(pos) && matchesPart(level.getBlockState(cell),facing,part)) level.removeBlock(cell,false);
                }
            } finally { removing=false; }
        }
        super.onRemove(state,level,pos,replacement,piston);
    }
    private boolean matchesPart(BlockState state, Direction facing, int part) {
        return state.is(this) && state.getValue(FACING) == facing && state.getValue(PART) == part;
    }
    @Override protected ItemInteractionResult useItemOn(ItemStack stack,BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit) {
        if (state.getValue(PART) >= WALL_CELLS) return ItemInteractionResult.FAIL;
        var root=origin(pos,state);
        if(stack.getItem() instanceof BlockItem item && isSurround(hit.getLocation(),root,state.getValue(FACING))) {
            var material=item.getBlock().defaultBlockState();
            if(material.getBlock()==this || material.getBlock() instanceof ATMBlock || material.hasBlockEntity() || !material.isSolidRender(level,pos)) return ItemInteractionResult.FAIL;
            if(!level.isClientSide && level.getBlockEntity(root) instanceof AtmBlockEntity atm)
                atm.setWallMaterial(state.getValue(PART),material,player,stack);
            return ItemInteractionResult.SUCCESS;
        }
        return ItemInteractionResult.SUCCESS;
    }
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit) {
        if (state.getValue(PART) >= WALL_CELLS) return InteractionResult.PASS;
        var root=origin(pos,state);
        if(player.isShiftKeyDown() && isSurround(hit.getLocation(),root,state.getValue(FACING)) && !level.isClientSide
                && level.getBlockEntity(root) instanceof AtmBlockEntity atm)
            atm.setWallMaterial(state.getValue(PART),Blocks.AIR.defaultBlockState(),player,ItemStack.EMPTY);
        return InteractionResult.SUCCESS;
    }
}
