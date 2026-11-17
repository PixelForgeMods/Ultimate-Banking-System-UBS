package net.austizz.ultimatebankingsystem.client;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;



@GameTestHolder("ultimatebankingsystem_atm")
@PrefixGameTestTemplate(false)
public class AtmControlsGameTests {
    @GameTest(template = "empty3x3x3")
    public static void readerUsesTheSameRayPickingAndRotatesWithEveryFacing(GameTestHelper helper) {
        var bounds = new AABB(3.99, 19.37, 1.91, 6.01, 21.39, 2.91);
        helper.assertTrue(AtmControls.pickLocal(new Vec3(5, 20.42, 0), new Vec3(5, 20.42, 4), List.of(), bounds)
                == AtmControls.READER, "Reader must be clickable");
        helper.assertTrue(AtmControls.pickLocal(new Vec3(7, 20.42, 0), new Vec3(7, 20.42, 4), List.of(), bounds)
                == -1, "Nearby fascia must not count as the reader");
        var slot = new Vec3(5, 20.42, 1.99);
        for (var facing : Direction.Plane.HORIZONTAL) {
            var pos = new BlockPos(10, 64, -20);
            helper.assertTrue(AtmControls.local(AtmControls.world(slot, pos, facing), pos, facing).distanceTo(slot) < 1e-5,
                    "Card must use the reader anchor for every facing");
        }
        helper.succeed();
    }
    @GameTest(template = "empty3x3x3")
    public static void picksSlopedKeysAndRejectsGaps(GameTestHelper helper) {
        var origin = new Vec3(8, 18.15, -.1);
        var button = new AtmControls.Button(0, true, new AABB(8.92, 18.46, 2.2, 9.6, 18.58, 2.67), origin, -22.5);
        Vec3 start = AtmControls.rotateX(new Vec3(9.2, 22, 2.4).subtract(origin), -22.5).add(origin);
        Vec3 end = AtmControls.rotateX(new Vec3(9.2, 17, 2.4).subtract(origin), -22.5).add(origin);
        assertEquals(0, AtmControls.pickLocal(start, end, List.of(button)));
        assertEquals(-1, AtmControls.pickLocal(start.add(1, 0, 0), end.add(1, 0, 0), List.of(button)));
        var travel = button.depression(1);
        assertTrue(travel.y < 0 && travel.z > 0);
        assertEquals(.08, travel.length(), 1e-9);
        helper.succeed();
    }

    @GameTest(template = "empty3x3x3")
    public static void lcdButtonPushesIntoCabinetAndNearestButtonWins(GameTestHelper helper) {
        var near = new AtmControls.Button(16, false, new AABB(4, 22, 1.74, 4.4, 22.4, 1.94), Vec3.ZERO, 0);
        var far = new AtmControls.Button(17, false, new AABB(4, 22, 2.5, 4.4, 22.4, 2.7), Vec3.ZERO, 0);
        assertEquals(16, AtmControls.pickLocal(new Vec3(4.2, 22.2, 0), new Vec3(4.2, 22.2, 4), List.of(far, near)));
        assertEquals(new Vec3(0, 0, .08), near.depression(1));
        assertEquals(-1, AtmControls.pickLocal(new Vec3(5, 22.2, 0), new Vec3(5, 22.2, 4), List.of(near)));
        helper.succeed();
    }

    @GameTest(template = "empty3x3x3")
    public static void transformsEveryAtmFacingAroundBlockCenter(GameTestHelper helper) {
        var pos = new BlockPos(10, 64, -20);
        var local = new Vec3(.27, 1.5, .12);
        for (var facing : Direction.Plane.HORIZONTAL) {
            var world = local.subtract(.5, 0, .5)
                    .yRot((float) Math.toRadians(-AtmControls.rotation(facing)))
                    .add(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
            assertEquals(0, AtmControls.local(world, pos, facing).distanceTo(local.scale(16)), 1e-5);
        }
        helper.succeed();
    }

    @GameTest(template = "empty3x3x3")
    public static void pressHasDownstrokeHoldAndSmoothReturnWithoutSticking(GameTestHelper helper) {
        long start = 1_000_000_000L;
        assertEquals(0, AtmControls.pressAmount(0, start));
        assertEquals(0, AtmControls.pressAmount(start, start));
        assertEquals(.5, AtmControls.pressAmount(start, start + 20_000_000L));
        assertEquals(1, AtmControls.pressAmount(start, start + 60_000_000L));
        assertEquals(.5, AtmControls.pressAmount(start, start + 170_000_000L), 1e-9);
        assertEquals(0, AtmControls.pressAmount(start, start + 240_000_000L));
        helper.succeed();
    }
    private static void assertTrue(boolean condition) {
        if (!condition) throw new AssertionError("ATM control check failed");
    }

    private static void assertEquals(Object expected, Object actual) {
        if (!expected.equals(actual)) throw new AssertionError(expected + " != " + actual);
    }

    private static void assertEquals(double expected, double actual) {
        assertEquals(expected, actual, 0);
    }

    private static void assertEquals(double expected, double actual, double tolerance) {
        if (Math.abs(expected - actual) > tolerance) throw new AssertionError(expected + " != " + actual);
    }
}
