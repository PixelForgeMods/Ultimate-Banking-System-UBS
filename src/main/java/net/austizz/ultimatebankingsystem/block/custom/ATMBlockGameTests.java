package net.austizz.ultimatebankingsystem.block.custom;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.austizz.ultimatebankingsystem.block.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(UltimateBankingSystem.MODID + "_atm")
@PrefixGameTestTemplate(false)
public final class ATMBlockGameTests {
    private ATMBlockGameTests() {
    }

    @GameTest(template = "empty3x3x3")
    public static void legacyAtmDropsOneItemFromEitherHalf(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(1, 1, 1));
        var block = ModBlocks.ATM_MACHINE_OLD.get();
        var lower = block.defaultBlockState().setValue(LegacyATMBlock.FACING, Direction.EAST);
        var upper = lower.setValue(LegacyATMBlock.HALF, DoubleBlockHalf.UPPER);

        for (var brokenPos : new BlockPos[]{pos, pos.above()}) {
            level.setBlock(pos, lower, Block.UPDATE_ALL);
            block.setPlacedBy(level, pos, lower, null, new ItemStack(block));
            helper.assertTrue(level.getBlockState(pos.above()) == upper, "Legacy ATM must place a matching upper half");
            level.destroyBlock(brokenPos, true);
            helper.assertTrue(level.isEmptyBlock(pos) && level.isEmptyBlock(pos.above()),
                    "Breaking either half must remove the entire legacy ATM");
            var drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).expandTowards(0, 1, 0),
                    item -> item.getItem().is(block.asItem()));
            helper.assertTrue(drops.stream().mapToInt(item -> item.getItem().getCount()).sum() == 1,
                    "The legacy ATM must drop exactly one item");
            drops.forEach(ItemEntity::discard);
        }
        helper.succeed();
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
