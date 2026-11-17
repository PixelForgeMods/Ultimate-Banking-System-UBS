package net.austizz.ultimatebankingsystem.block.custom;

import com.mojang.serialization.MapCodec;
import net.austizz.ultimatebankingsystem.bank.safebox.LoadedSafeStructureIndex;
import net.austizz.ultimatebankingsystem.block.ModBlocks;
import net.austizz.ultimatebankingsystem.block.entity.ModBlockEntities;
import net.austizz.ultimatebankingsystem.block.entity.custom.BankVaultDoorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class BankVaultDoorBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final MapCodec<BankVaultDoorBlock> CODEC = simpleCodec(BankVaultDoorBlock::new);
    public static final IntegerProperty PART_X = IntegerProperty.create("part_x", 0, 4);
    public static final IntegerProperty PART_Y = IntegerProperty.create("part_y", 0, 3);
    public static final IntegerProperty PART_D = IntegerProperty.create("part_d", 0, 3);
    public static final BooleanProperty POWERED = BooleanProperty.create("powered");
    public static final BooleanProperty OPEN = BooleanProperty.create("open");

    private static final int MASTER_PART_X = 2;
    private static final int MASTER_PART_Y = 0;
    private static final int MASTER_PART_D = 0;
    private static final int MAX_PART_D = 3;
    private static final int REMOVE_MAX_PART_D = 3;
    private static final int PART_X_COUNT = 5;
    private static final int PART_Y_COUNT = 4;
    private static final int PART_D_COUNT = 4;
    private static final VoxelShape FULL_SHAPE = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 16.0D, 16.0D);
    private static final VoxelShape BOTTOM_SLAB_SHAPE = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 8.0D, 16.0D);
    private static final VoxelShape EMPTY_SHAPE = Shapes.empty();
    private static boolean internalRemoval;

    public BankVaultDoorBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(PART_X, MASTER_PART_X)
                .setValue(PART_Y, MASTER_PART_Y)
                .setValue(PART_D, MASTER_PART_D)
                .setValue(POWERED, false)
                .setValue(OPEN, false));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection().getOpposite();
        BlockPos masterPos = context.getClickedPos();
        Level level = context.getLevel();
        if (findPlacementIssue(context) != null) {
            return null;
        }
        boolean powered = hasAnyPartNeighborSignal(level, masterPos, facing);
        return defaultBlockState()
                .setValue(FACING, facing)
                .setValue(PART_X, MASTER_PART_X)
                .setValue(PART_Y, MASTER_PART_Y)
                .setValue(PART_D, MASTER_PART_D)
                .setValue(POWERED, powered);
    }

    @Override
    public void setPlacedBy(Level level,
                            BlockPos pos,
                            BlockState state,
                            @Nullable LivingEntity placer,
                            ItemStack stack) {
        if (level.isClientSide()) {
            return;
        }
        Direction facing = state.getValue(FACING);
        boolean powered = hasAnyPartNeighborSignal(level, pos, facing);
        BlockState baseState = defaultBlockState()
                .setValue(FACING, facing)
                .setValue(POWERED, powered)
                .setValue(OPEN, false);

        for (int partD = 0; partD <= MAX_PART_D; partD++) {
            for (int partY = 0; partY <= 3; partY++) {
                for (int partX = 0; partX <= 4; partX++) {
                    BlockState partState = baseState
                            .setValue(PART_X, partX)
                            .setValue(PART_Y, partY)
                            .setValue(PART_D, partD);
                    if (partX == MASTER_PART_X && partY == MASTER_PART_Y && partD == MASTER_PART_D) {
                        level.setBlock(pos, partState, Block.UPDATE_ALL);
                        continue;
                    }
                    level.setBlock(
                            getPartPos(pos, facing, partX, partY, partD),
                            partState,
                            Block.UPDATE_ALL
                    );
                }
            }
        }
        if (level.getBlockEntity(pos) instanceof BankVaultDoorBlockEntity vault) {
            vault.setTargetOpen(powered);
        }
        LoadedSafeStructureIndex.register(level, pos, LoadedSafeStructureIndex.Kind.VAULT_DOOR_MASTER);
    }

    public @Nullable PlacementIssue findPlacementIssue(BlockPlaceContext context) {
        if (context == null) {
            return new PlacementIssue(null, "Vault door placement failed: placement context is unavailable.");
        }
        Direction facing = context.getHorizontalDirection().getOpposite();
        BlockPos masterPos = context.getClickedPos();
        Level level = context.getLevel();
        for (int partD = 0; partD < PART_D_COUNT; partD++) {
            for (int partY = 0; partY < PART_Y_COUNT; partY++) {
                BlockPos heightProbe = masterPos.above(partY);
                if (heightProbe.getY() < level.getMinBuildHeight()
                        || heightProbe.getY() >= level.getMaxBuildHeight()) {
                    return new PlacementIssue(heightProbe, "Vault door placement failed: its 5 x 4 x 4 footprint exceeds the build height.");
                }
                for (int partX = 0; partX < PART_X_COUNT; partX++) {
                    BlockPos partPos = getPartPos(masterPos, facing, partX, partY, partD);
                    BlockState existing = level.getBlockState(partPos);
                    if (!existing.canBeReplaced(context)) {
                        String blockedBy = existing.getBlock().getName().getString();
                        return new PlacementIssue(partPos, "Vault door placement failed: clear the full 5 x 4 x 4 footprint. "
                                + "Blocked at " + partPos.getX() + ", " + partPos.getY() + ", " + partPos.getZ()
                                + " by " + blockedBy + ".");
                    }
                }
            }
        }
        return null;
    }

    public record PlacementIssue(@Nullable BlockPos blockedPos, String message) {
    }

    @Override
    protected void neighborChanged(BlockState state,
                                   Level level,
                                   BlockPos pos,
                                   Block neighborBlock,
                                   BlockPos neighborPos,
                                   boolean movedByPiston) {
        if (!level.isClientSide()) {
            syncPoweredState(level, getMasterPos(state, pos));
        }
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
    }

    @Override
    public BlockState updateShape(BlockState state,
                                  Direction direction,
                                  BlockState neighborState,
                                  LevelAccessor level,
                                  BlockPos pos,
                                  BlockPos neighborPos) {
        if (internalRemoval || !(level instanceof Level readableLevel)) {
            return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
        }
        BlockPos masterPos = getMasterPos(state, pos);
        BlockState masterState = readableLevel.getBlockState(masterPos);
        if (!masterState.is(this)
                || masterState.getValue(PART_X) != MASTER_PART_X
                || masterState.getValue(PART_Y) != MASTER_PART_Y
                || masterState.getValue(PART_D) != MASTER_PART_D) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return getVaultShape(state);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return getVaultShape(state);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide()) {
            removeStructure(level, getMasterPos(state, pos), player);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (state.getBlock() == newState.getBlock()) {
            super.onRemove(state, level, pos, newState, isMoving);
            return;
        }
        if (!level.isClientSide() && !internalRemoval) {
            removeStructure(level, getMasterPos(state, pos), null);
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        if (state.getValue(PART_X) != MASTER_PART_X
                || state.getValue(PART_Y) != MASTER_PART_Y
                || state.getValue(PART_D) != MASTER_PART_D) {
            return null;
        }
        return new BankVaultDoorBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level,
                                                                             BlockState state,
                                                                             BlockEntityType<T> type) {
        if (type != ModBlockEntities.BANK_VAULT_DOOR.get()) {
            return null;
        }
        return (tickLevel, tickPos, tickState, blockEntity) -> {
            if (blockEntity instanceof BankVaultDoorBlockEntity vault) {
                BankVaultDoorBlockEntity.tick(tickLevel, tickPos, tickState, vault);
            }
        };
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART_X, PART_Y, PART_D, POWERED, OPEN);
    }

    public static BlockPos getMasterPos(BlockState state, BlockPos pos) {
        if (state == null || pos == null || !state.hasProperty(FACING)
                || !state.hasProperty(PART_X) || !state.hasProperty(PART_Y) || !state.hasProperty(PART_D)) {
            return pos;
        }
        Direction facing = state.getValue(FACING);
        int partX = state.getValue(PART_X);
        int partY = state.getValue(PART_Y);
        int partD = state.getValue(PART_D);
        return pos.relative(getRightDirection(facing), MASTER_PART_X - partX)
                .below(partY - MASTER_PART_Y)
                .relative(facing, partD - MASTER_PART_D);
    }

    public static boolean isCompleteMultiblock(BlockGetter level, BlockPos pos) {
        if (level == null || pos == null) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        if (!state.is(ModBlocks.BANK_VAULT_DOOR.get())) {
            return false;
        }
        BlockPos masterPos = getMasterPos(state, pos);
        BlockState masterState = level.getBlockState(masterPos);
        if (!masterState.is(ModBlocks.BANK_VAULT_DOOR.get())
                || masterState.getValue(PART_X) != MASTER_PART_X
                || masterState.getValue(PART_Y) != MASTER_PART_Y
                || masterState.getValue(PART_D) != MASTER_PART_D) {
            return false;
        }
        Direction facing = masterState.getValue(FACING);
        for (int partD = 0; partD < PART_D_COUNT; partD++) {
            for (int partY = 0; partY < PART_Y_COUNT; partY++) {
                for (int partX = 0; partX < PART_X_COUNT; partX++) {
                    BlockState partState = level.getBlockState(getPartPos(masterPos, facing, partX, partY, partD));
                    if (!partState.is(ModBlocks.BANK_VAULT_DOOR.get())
                            || partState.getValue(FACING) != facing
                            || partState.getValue(PART_X) != partX
                            || partState.getValue(PART_Y) != partY
                            || partState.getValue(PART_D) != partD) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    public static List<BlockPos> plannedPartPositions(BlockState state, BlockPos pos) {
        if (state == null || pos == null || !state.hasProperty(FACING)
                || !state.hasProperty(PART_X) || !state.hasProperty(PART_Y) || !state.hasProperty(PART_D)) {
            return List.of();
        }
        BlockPos masterPos = getMasterPos(state, pos);
        Direction facing = state.getValue(FACING);
        List<BlockPos> positions = new ArrayList<>(PART_X_COUNT * PART_Y_COUNT * PART_D_COUNT);
        for (int partD = 0; partD < PART_D_COUNT; partD++) {
            for (int partY = 0; partY < PART_Y_COUNT; partY++) {
                for (int partX = 0; partX < PART_X_COUNT; partX++) {
                    positions.add(getPartPos(masterPos, facing, partX, partY, partD));
                }
            }
        }
        return List.copyOf(positions);
    }

    public static List<BlockPos> multiblockPartPositions(BlockGetter level, BlockPos pos) {
        if (level == null || pos == null) {
            return List.of();
        }
        BlockState state = level.getBlockState(pos);
        if (!state.is(ModBlocks.BANK_VAULT_DOOR.get())) {
            return List.of();
        }
        BlockPos masterPos = getMasterPos(state, pos);
        BlockState masterState = level.getBlockState(masterPos);
        if (!masterState.is(ModBlocks.BANK_VAULT_DOOR.get())) {
            return List.of();
        }
        return plannedPartPositions(masterState, masterPos);
    }

    private static VoxelShape getVaultShape(BlockState state) {
        if (state == null || !state.hasProperty(PART_X) || !state.hasProperty(PART_Y)
                || !state.hasProperty(PART_D) || !state.hasProperty(OPEN)) {
            return FULL_SHAPE;
        }
        if (state.getValue(PART_D) == MASTER_PART_D
                && !state.getValue(OPEN)
                && state.getValue(PART_X) >= 1 && state.getValue(PART_X) <= 3
                && state.getValue(PART_Y) <= 2) {
            return FULL_SHAPE;
        }
        return getStaticFrameShape(state);
    }

    private static VoxelShape getStaticFrameShape(BlockState state) {
        int partX = state.getValue(PART_X);
        int partY = state.getValue(PART_Y);
        int partD = state.getValue(PART_D);
        if (partD != MASTER_PART_D) {
            return EMPTY_SHAPE;
        }
        if (partY == 0 && partX >= 1 && partX <= 3) {
            return BOTTOM_SLAB_SHAPE;
        }
        boolean centralWalkThrough = partX >= 1 && partX <= 3 && partY <= 2;
        return centralWalkThrough ? EMPTY_SHAPE : FULL_SHAPE;
    }

    public static void setOpenStateIfNeeded(Level level, BlockPos masterPos, BlockState masterState, boolean open) {
        if (level == null || level.isClientSide() || masterPos == null || masterState == null
                || !masterState.is(ModBlocks.BANK_VAULT_DOOR.get())) {
            return;
        }
        BlockState currentState = level.getBlockState(masterPos);
        if (!currentState.is(ModBlocks.BANK_VAULT_DOOR.get()) || !currentState.hasProperty(OPEN)
                || currentState.getValue(OPEN) == open) {
            return;
        }
        Direction facing = currentState.hasProperty(FACING) ? currentState.getValue(FACING) : Direction.NORTH;
        setAllPartsOpen(level, masterPos, facing, open);
    }

    private static void syncPoweredState(Level level, BlockPos masterPos) {
        if (level == null || masterPos == null || level.isClientSide()) {
            return;
        }
        BlockState masterState = level.getBlockState(masterPos);
        if (!masterState.is(ModBlocks.BANK_VAULT_DOOR.get())) {
            return;
        }
        Direction facing = masterState.getValue(FACING);
        boolean powered = hasAnyPartNeighborSignal(level, masterPos, facing);
        setAllPartsPowered(level, masterPos, facing, powered);
        if (level.getBlockEntity(masterPos) instanceof BankVaultDoorBlockEntity vault) {
            vault.setTargetOpen(powered);
        }
    }

    private static void setAllPartsPowered(Level level, BlockPos masterPos, Direction facing, boolean powered) {
        for (int partD = 0; partD <= MAX_PART_D; partD++) {
            for (int partY = 0; partY <= 3; partY++) {
                for (int partX = 0; partX <= 4; partX++) {
                    BlockPos partPos = getPartPos(masterPos, facing, partX, partY, partD);
                    BlockState partState = level.getBlockState(partPos);
                    if (!partState.is(ModBlocks.BANK_VAULT_DOOR.get())
                            || !partState.hasProperty(POWERED)
                            || partState.getValue(POWERED) == powered) {
                        continue;
                    }
                    level.setBlock(partPos, partState.setValue(POWERED, powered), Block.UPDATE_ALL);
                }
            }
        }
    }

    private static void setAllPartsOpen(Level level, BlockPos masterPos, Direction facing, boolean open) {
        for (int partD = 0; partD <= MAX_PART_D; partD++) {
            for (int partY = 0; partY <= 3; partY++) {
                for (int partX = 0; partX <= 4; partX++) {
                    BlockPos partPos = getPartPos(masterPos, facing, partX, partY, partD);
                    BlockState partState = level.getBlockState(partPos);
                    if (!partState.is(ModBlocks.BANK_VAULT_DOOR.get())
                            || !partState.hasProperty(OPEN)
                            || partState.getValue(OPEN) == open) {
                        continue;
                    }
                    level.setBlock(partPos, partState.setValue(OPEN, open), Block.UPDATE_ALL);
                }
            }
        }
    }

    private static boolean hasAnyPartNeighborSignal(Level level, BlockPos masterPos, Direction facing) {
        if (level == null || masterPos == null || facing == null) {
            return false;
        }
        for (int partD = 0; partD <= MAX_PART_D; partD++) {
            for (int partY = 0; partY <= 3; partY++) {
                for (int partX = 0; partX <= 4; partX++) {
                    if (level.hasNeighborSignal(getPartPos(masterPos, facing, partX, partY, partD))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static BlockPos getPartPos(BlockPos masterPos, Direction facing, int partX, int partY, int partD) {
        return masterPos.relative(getRightDirection(facing), partX - MASTER_PART_X)
                .above(partY - MASTER_PART_Y)
                .relative(facing, MASTER_PART_D - partD);
    }

    private static BlockPos getLegacyFrontPartPos(BlockPos masterPos, Direction facing, int partX, int partY, int partD) {
        return masterPos.relative(getRightDirection(facing), partX - MASTER_PART_X)
                .above(partY - MASTER_PART_Y)
                .relative(facing, partD - MASTER_PART_D);
    }

    private static Direction getRightDirection(Direction facing) {
        return facing.getClockWise();
    }

    private void removeStructure(Level level, BlockPos masterPos, @Nullable Player breaker) {
        if (internalRemoval || level == null || masterPos == null) {
            return;
        }
        BlockState masterState = level.getBlockState(masterPos);
        Direction facing = masterState.hasProperty(FACING) ? masterState.getValue(FACING) : Direction.NORTH;
        internalRemoval = true;
        try {
            for (int partD = 0; partD <= REMOVE_MAX_PART_D; partD++) {
                for (int partY = 0; partY <= 3; partY++) {
                    for (int partX = 0; partX <= 4; partX++) {
                        removePart(level, getPartPos(masterPos, facing, partX, partY, partD));
                        removePart(level, getLegacyFrontPartPos(masterPos, facing, partX, partY, partD));
                    }
                }
            }
            boolean shouldDrop = breaker == null || !breaker.getAbilities().instabuild;
            if (shouldDrop) {
                Containers.dropItemStack(
                        level,
                        masterPos.getX() + 0.5D,
                        masterPos.getY() + 0.5D,
                        masterPos.getZ() + 0.5D,
                        new ItemStack(ModBlocks.BANK_VAULT_DOOR.get().asItem())
                );
            }
        } finally {
            internalRemoval = false;
        }
    }

    private static void removePart(Level level, BlockPos partPos) {
        BlockState partState = level.getBlockState(partPos);
        if (partState.is(ModBlocks.BANK_VAULT_DOOR.get())) {
            level.setBlock(partPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
    }
}
