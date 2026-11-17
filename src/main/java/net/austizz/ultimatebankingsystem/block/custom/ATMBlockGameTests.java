package net.austizz.ultimatebankingsystem.block.custom;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.austizz.ultimatebankingsystem.block.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(UltimateBankingSystem.MODID + "_atm")
@PrefixGameTestTemplate(false)
public final class ATMBlockGameTests {
    private ATMBlockGameTests() {
    }

    @GameTest(template = "empty3x3x3")
    public static void fullHeightAtmUsesOnlyOneBlock(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(1, 1, 1));
        var block = (ATMBlock) ModBlocks.ATM_MACHINE.get();
        if (block.defaultBlockState().hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
            helper.fail("ATM must not have an upper/lower half property");
        }
        for (var facing : Direction.Plane.HORIZONTAL) {
            var state = block.defaultBlockState().setValue(ATMBlock.FACING, facing);
            level.setBlock(pos, state, Block.UPDATE_ALL);
            if (!(level.getBlockEntity(pos) instanceof net.austizz.ultimatebankingsystem.block.entity.custom.AtmBlockEntity atm)) {
                helper.fail("ATM must provide its animated-button block entity");
                return;
            } else {
                long now = 1_000_000_000L;
                if (!atm.press(0, now) || atm.press(0, now + 10_000_000L)
                        || !atm.press(23, now) || atm.press(-1, now) || atm.press(24, now)) {
                    helper.fail("ATM controls must reject invalid/repeated presses and support both button banks");
                }
            }
            if (state.getLightEmission(level, pos) != 5) {
                helper.fail("ATM must provide its low ambient block light");
            }
            block.setPlacedBy(level, pos, state, null, ItemStack.EMPTY);
            if (!level.isEmptyBlock(pos.above())) {
                helper.fail("Placing ATM must leave the block above empty");
            }
            var bounds = state.getShape(level, pos).bounds();
            if (Math.abs(bounds.maxY - 2) > 0.000001) {
                helper.fail("Single ATM collision must cover its full two-meter model");
            }
            double width = facing.getAxis() == Direction.Axis.X ? bounds.getZsize() : bounds.getXsize();
            if (Math.abs(width - 10.3 / 16) > 0.000001) {
                helper.fail("ATM shape must rotate with its narrow cabinet");
            }
            level.setBlock(pos.above(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
            if (!level.getBlockState(pos).is(block)) {
                helper.fail("Changing block above must not destroy a single-block ATM");
            }
            level.removeBlock(pos, false);
            if (!level.getBlockState(pos.above()).is(Blocks.STONE)) {
                helper.fail("Breaking ATM must not remove an independent block above it");
            }
            level.removeBlock(pos.above(), false);
        }
        helper.succeed();
    }
}
