package net.austizz.ultimatebankingsystem.block.custom;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class ATMBlock extends HorizontalDirectionalBlock implements net.minecraft.world.level.block.EntityBlock {
    public static final MapCodec<ATMBlock> CODEC = simpleCodec(ATMBlock::new);

    private static final VoxelShape SHAPE_NORTH_SOUTH = Block.box(2.85, 0, 0, 13.15, 32, 16);
    private static final VoxelShape SHAPE_EAST_WEST = Block.box(0, 0, 2.85, 16, 32, 13.15);


    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    public ATMBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH));
    }

    @Override
    public net.minecraft.world.level.block.entity.BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new net.austizz.ultimatebankingsystem.block.entity.custom.AtmBlockEntity(pos, state);
    }

    @Override
    public <T extends net.minecraft.world.level.block.entity.BlockEntity> net.minecraft.world.level.block.entity.BlockEntityTicker<T> getTicker(
            Level level, BlockState state, net.minecraft.world.level.block.entity.BlockEntityType<T> type) {
        if (type != net.austizz.ultimatebankingsystem.block.entity.ModBlockEntities.ATM.get()) return null;
        return (world, pos, blockState, entity) -> {
            if (entity instanceof net.austizz.ultimatebankingsystem.block.entity.custom.AtmBlockEntity atm)
                net.austizz.ultimatebankingsystem.block.entity.custom.AtmBlockEntity.tick(world, pos, blockState, atm);
        };
    }
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean piston) {
        if (state.getBlock() != replacement.getBlock() && !level.isClientSide
                && level.getBlockEntity(pos) instanceof net.austizz.ultimatebankingsystem.block.entity.custom.AtmBlockEntity atm) atm.dropPendingCash();
        super.onRemove(state, level, pos, replacement, piston);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack,
                                         BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        // The in-world ATM camera handles interaction; consume held-item use.
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        return InteractionResult.SUCCESS;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(FACING).getAxis() == Direction.Axis.X ? SHAPE_EAST_WEST : SHAPE_NORTH_SOUTH;
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        Level level = context.getLevel();
        if (pos.getY() < level.getMaxBuildHeight() - 1 && level.getBlockState(pos.above()).canBeReplaced(context)) {
            return this.defaultBlockState()
                    .setValue(FACING, context.getHorizontalDirection().getOpposite());
        }
        return null;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }
}
