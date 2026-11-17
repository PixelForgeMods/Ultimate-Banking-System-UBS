package net.austizz.ultimatebankingsystem.block.custom;

import net.austizz.ultimatebankingsystem.block.ModBlocks;
import net.austizz.ultimatebankingsystem.block.entity.custom.AtmBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("ultimatebankingsystem_in_wall_atm")
@PrefixGameTestTemplate(false)
public final class InWallAtmGameTests {
    @GameTest(template="empty3x3x3")
    public static void inWallUsesTheSameAuthorizedBankingAndCustodyFlow(GameTestHelper h) {
        net.austizz.ultimatebankingsystem.payments.AtmSessionGameTests.verifyAtmSession(h,ModBlocks.IN_WALL_ATM.get());
    }
    @GameTest(template="empty3x3x3")
    public static void wallAndRearResolveOneTerminalAndBreakAsOneInEveryFacing(GameTestHelper h) {
        var level=h.getLevel();var pos=h.absolutePos(new BlockPos(1,1,1));
        var block=(InWallATMBlock)ModBlocks.IN_WALL_ATM.get();
        for(var facing:Direction.Plane.HORIZONTAL) {
            var state=block.defaultBlockState().setValue(ATMBlock.FACING,facing);
            level.setBlock(pos,state,3);block.setPlacedBy(level,pos,state,null,ItemStack.EMPTY);
            for(int cell=0;cell<InWallATMBlock.TOTAL_PARTS;cell++) {
                var p=InWallATMBlock.partPos(pos,facing,cell);var s=level.getBlockState(p);
                h.assertTrue(s.is(block)&&s.getValue(InWallATMBlock.PART)==cell,"Each cell must be reserved");
                h.assertTrue(InWallATMBlock.origin(p,s).equals(pos),"All interactions resolve to the same terminal");
                h.assertTrue((level.getBlockEntity(p) instanceof AtmBlockEntity)==(cell==0),"Only the master owns cash/card/session state");
                if (cell == 2 || cell == 3) {
                    var front = facing;
                    var center = net.minecraft.world.phys.Vec3.atCenterOf(p).add(0, -.03, 0)
                            .relative(facing.getClockWise(), cell == 2 ? .18 : -.18);
                    var target = center.relative(front, .5);
                    var start = target.relative(front, 1);
                    var hit = s.getShape(level, p).clip(start, center, p);
                    h.assertTrue(hit != null && hit.getLocation().distanceTo(target) > .25,
                            "The center ray reaches inside the recess before hitting either middle cell");
                    var edge = target.relative(facing.getClockWise(), cell == 2 ? -.43 : .43);
                    var edgeHit = s.getShape(level, p).clip(edge.relative(front, 1), edge.relative(front.getOpposite(), .5), p);
                    h.assertTrue(edgeHit != null && edgeHit.getLocation().distanceTo(edge) < 1e-6,
                            "The copycat strip still starts at the wall face");
                }
                if (cell >= InWallATMBlock.WALL_CELLS) {
                    var shape = s.getCollisionShape(level,p);
                    var bounds = shape.bounds();
                    double depth = facing.getAxis()==Direction.Axis.X ? bounds.getXsize() : bounds.getZsize();
                    h.assertTrue(Math.abs(depth - (cell < 8 ? 8 : 11.46) / 16) < 1e-6, "Rear collision follows the cabinet depth");
                    var start = net.minecraft.world.phys.Vec3.atCenterOf(p).add(
                            facing.getOpposite().getStepX() * .6, cell >= 10 ? -.3 : 0, facing.getOpposite().getStepZ() * .6)
                            .relative(facing.getClockWise(), cell % 2 == 0 ? .25 : -.25);
                    var end = start.relative(facing, 1.1);
                    h.assertTrue(shape.clip(start,end,p)!=null, "Approaching from behind hits the cabinet");
                }
            }
            level.destroyBlock(InWallATMBlock.partPos(pos,facing,11),false);
            for(int cell=0;cell<InWallATMBlock.TOTAL_PARTS;cell++)h.assertFalse(level.getBlockState(InWallATMBlock.partPos(pos,facing,cell)).is(block),"Breaking the rear removes the structure");
        }
        h.succeed();
    }

    @GameTest(template="empty3x3x3")
    public static void existingWallGainsRearWithoutOverwritingSurroundings(GameTestHelper h) {
        var level = h.getLevel();
        var root = h.absolutePos(new BlockPos(0, 0, 0));
        var block = (InWallATMBlock) ModBlocks.IN_WALL_ATM.get();
        var state = block.defaultBlockState();
        for (int part = 0; part < InWallATMBlock.WALL_CELLS; part++) {
            level.setBlock(InWallATMBlock.partPos(root, Direction.NORTH, part), state.setValue(InWallATMBlock.PART, part), 3);
        }
        var occupied = InWallATMBlock.partPos(root, Direction.NORTH, 6);
        level.setBlock(occupied, Blocks.STONE.defaultBlockState(), 3);
        h.assertTrue(block.restoreRear(level, root, state), "Loaded installation can be upgraded");
        h.assertTrue(level.getBlockState(occupied).is(Blocks.STONE), "Existing surrounding blocks are preserved");
        h.assertTrue(level.getBlockState(InWallATMBlock.partPos(root, Direction.NORTH, 7)).is(block), "Empty rear positions are restored");
        level.removeBlock(root, false);
        h.assertTrue(level.getBlockState(occupied).is(Blocks.STONE), "Removal preserves surrounding blocks");
        h.succeed();
    }
    @GameTest(template="empty3x3x3")
    public static void copiedMaterialsAreIndependentPersistedAndChargedPerCell(GameTestHelper h) {
        var level=h.getLevel();var pos=h.absolutePos(new BlockPos(1,1,1));var state=ModBlocks.IN_WALL_ATM.get().defaultBlockState();
        level.setBlock(pos,state,3);var atm=(AtmBlockEntity)level.getBlockEntity(pos);
        var player=new net.minecraft.server.level.ServerPlayer(level.getServer(),level,new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"wall-material-test"),net.minecraft.server.level.ClientInformation.createDefault());
        var stones=new ItemStack(Blocks.STONE,4);var bricks=new ItemStack(Blocks.BRICKS,2);
        atm.setWallMaterial(0,Blocks.STONE.defaultBlockState(),player,stones);
        atm.setWallMaterial(5,Blocks.BRICKS.defaultBlockState(),player,bricks);
        h.assertTrue(stones.getCount()==3&&bricks.getCount()==1,"Charge one actual block per selected quadrant");
        h.assertTrue(atm.wallMaterial(1)==null&&atm.wallMaterial(2)==null,"Other quadrants stay untouched");
        var observer=new AtmBlockEntity(pos,state);observer.loadWithComponents(atm.getUpdateTag(level.registryAccess()),level.registryAccess());
        h.assertTrue(observer.wallMaterial(0).is(Blocks.STONE)&&observer.wallMaterial(5).is(Blocks.BRICKS),"Observers receive all six material slots");
        var restored=new AtmBlockEntity(pos,state);restored.loadWithComponents(atm.saveWithoutMetadata(level.registryAccess()),level.registryAccess());
        h.assertTrue(restored.wallMaterial(0).is(Blocks.STONE)&&restored.wallMaterial(5).is(Blocks.BRICKS),"Materials survive save/load");
        atm.setWallMaterial(0,Blocks.AIR.defaultBlockState(),player,ItemStack.EMPTY);
        h.assertTrue(atm.wallMaterial(0)==null&&atm.wallMaterial(5).is(Blocks.BRICKS)&&player.getInventory().countItem(Blocks.STONE.asItem())==1,"Reset refunds exactly the selected material");
        var chest=new ItemStack(Blocks.CHEST);atm.setWallMaterial(1,Blocks.CHEST.defaultBlockState(),player,chest);
        h.assertTrue(atm.wallMaterial(1)==null&&chest.getCount()==1,"Reject block entities without consuming items");
        h.succeed();
    }
}
