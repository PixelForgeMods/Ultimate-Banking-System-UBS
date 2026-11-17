package net.austizz.ultimatebankingsystem.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AtmCameraMotionTest {
    @Test void inWallCursorSwayMatchesUpAndDownWithoutChangingYaw() {
        var docked = new AtmCameraMotion.Pose(1, 65.62, 2, 90, 12);
        var up = AtmCameraMotion.cursorSway(docked, -1, -1, true);
        var down = AtmCameraMotion.cursorSway(docked, 1, 1, true);
        assertEquals(docked.pitch() - .85F, up.pitch(), 1e-6);
        assertEquals(docked.pitch() + .85F, down.pitch(), 1e-6);
        assertEquals(docked.yaw() - 1.25F, up.yaw(), 1e-6);
        assertEquals(docked.yaw() + 1.25F, down.yaw(), 1e-6);
    }
    @Test void invalidCursorSamplesCannotPoisonCameraOrAudioListenerRotation() {
        for (double pixels : new double[]{0, 100, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertTrue(Double.isFinite(AtmCameraMotion.cursorAxis(pixels, 0)), "Minimized windows must not divide by zero");
            assertTrue(Double.isFinite(AtmCameraMotion.cursorAxis(pixels, 1920)));
        }
        double poisoned = AtmCameraMotion.followCursor(0, Double.NaN, 1.0 / 60);
        var view = AtmCameraMotion.cursorSway(AtmCameraMotion.screen(0, 64, 0, 0, -1), poisoned, poisoned);
        assertTrue(Float.isFinite(view.yaw()) && Float.isFinite(view.pitch()), "Invalid cursor samples must never reach entity rotation or the audio listener");
        assertTrue(Double.isFinite(AtmCameraMotion.followCursor(Double.NaN, 0, .1)), "Recover an already-invalid smoothing state");
    }
    @Test
    void cursorLookKeepsOriginalPositionAndHalfThePreviousCashRange() {
        var docked = AtmCameraMotion.screen(2, 64, 3, 0, -1);
        for (double y : new double[]{-1, 0, .5, .75, 1}) {
            var cashView = AtmCameraMotion.cursorSway(docked, .3, y);
            assertEquals(docked.x(), cashView.x());
            assertEquals(docked.y(), cashView.y());
            assertEquals(docked.z(), cashView.z());
        }
        assertEquals(30, AtmCameraMotion.cursorSway(docked, 0, 1).pitch());
    }
    @Test
    void docksPerpendicularToTheLcdForEveryFacing() {
        for (int[] facing : new int[][]{{0, -1}, {1, 0}, {0, 1}, {-1, 0}}) {
            var pose = AtmCameraMotion.screen(10, 64, -20, facing[0], facing[1]);
            double yaw = Math.toRadians(pose.yaw());
            assertEquals(-facing[0], -Math.sin(yaw), 1e-7);
            assertEquals(-facing[1], Math.cos(yaw), 1e-7);
            assertEquals(64 + 24.22 / 16, pose.y(), 1e-7);
            assertEquals(.6778125, Math.hypot(pose.x() - 10.5, pose.z() + 19.5), 1e-7);
            assertEquals(0, pose.pitch());
        }
    }

    @Test
    void easesBothEndsAndTakesTheShortestYawPath() {
        var from = new AtmCameraMotion.Pose(0, 2, 4, 179, 30);
        var to = new AtmCameraMotion.Pose(10, 4, 0, -179, 0);
        assertEquals(from, AtmCameraMotion.interpolate(from, to, -1));
        var halfway = AtmCameraMotion.interpolate(from, to, .5);
        assertEquals(5, halfway.x());
        assertEquals(180, halfway.yaw());
        assertEquals(15, halfway.pitch());
        var end = AtmCameraMotion.interpolate(from, to, 2);
        assertEquals(to.x(), end.x());
        assertEquals(181, end.yaw());
        assertTrue(AtmCameraMotion.interpolate(from, to, .01).x() < .001);
        assertTrue(AtmCameraMotion.interpolate(from, to, .99).x() > 9.999);
    }

    @Test
    void canReturnSmoothlyBeforeTheArrivalFinishes() {
        var player = new AtmCameraMotion.Pose(2, 65.62, 5, 30, 10);
        var screen = AtmCameraMotion.screen(2, 64, 7, 0, -1);
        var halfway = AtmCameraMotion.interpolate(player, screen, .4);
        assertEquals(halfway, AtmCameraMotion.interpolate(halfway, player, 0));
        assertEquals(player, AtmCameraMotion.interpolate(halfway, player, 1));
    }

    @Test
    void cursorSwayIsClampedAndNeverMovesTheCameraThroughTheCabinet() {
        for (int[] facing : new int[][]{{0, -1}, {1, 0}, {0, 1}, {-1, 0}}) {
            var docked = AtmCameraMotion.screen(0, 64, 0, facing[0], facing[1]);
            assertEquals(docked, AtmCameraMotion.cursorSway(docked, 0, 0));
            for (double cursor : new double[]{-10, -1, 1, 10}) {
                var swayed = AtmCameraMotion.cursorSway(docked, cursor, cursor);
                assertEquals(docked.x(), swayed.x());
                assertEquals(docked.y(), swayed.y());
                assertEquals(docked.z(), swayed.z());
                assertTrue(Math.abs(swayed.yaw() - docked.yaw()) <= 1.25);
                assertTrue(Math.abs(swayed.pitch() - docked.pitch()) <= (cursor > 0 ? 30 : .850001));
            }
        }
    }

    @Test
    void bottomCursorRevealsTheKeypadWithoutLettingItFillTheView() {
        var docked = AtmCameraMotion.screen(0, 64, 0, 0, -1);
        var bottom = AtmCameraMotion.cursorSway(docked, 0, 1);
        // Angular bounds of the exported, sloping PIN keys from the docked camera.
        double topKeyAngle = 41.907448;
        double bottomKeyAngle = 60.823205;
        double halfFov = 35;
        assertTrue(topKeyAngle >= bottom.pitch() - halfFov);
        assertTrue(bottomKeyAngle <= bottom.pitch() + halfFov);
        double keypadHeight = (Math.tan(Math.toRadians(bottomKeyAngle - bottom.pitch()))
                - Math.tan(Math.toRadians(topKeyAngle - bottom.pitch())))
                / (2 * Math.tan(Math.toRadians(halfFov)));
        assertTrue(keypadHeight > .15 && keypadHeight < .4);
        assertTrue(bottom.pitch() - halfFov < 0, "LCD center must still be visible");
        assertTrue(AtmCameraMotion.cursorSway(docked, 0, .5).pitch() > 12,
                "Looking down should already be useful at three quarters of the screen height");
        assertEquals(30, AtmCameraMotion.cursorSway(docked, 0, .7).pitch(),
                "Reach the downward limit at 85% of screen height, before the bottom edge");
        assertEquals(-.85F, AtmCameraMotion.cursorSway(docked, 0, -1).pitch());
    }

    @Test
    void cursorFollowIsFrameRateIndependentAndDoesNotOvershoot() {
        double at60Fps = 0;
        double at144Fps = 0;
        for (int i = 0; i < 60; i++) at60Fps = AtmCameraMotion.followCursor(at60Fps, 1, 1.0 / 60);
        for (int i = 0; i < 144; i++) at144Fps = AtmCameraMotion.followCursor(at144Fps, 1, 1.0 / 144);
        assertEquals(at60Fps, at144Fps, 1e-12);
        assertTrue(at60Fps > .99 && at60Fps < 1);
        assertEquals(.5, AtmCameraMotion.followCursor(.5, -1, 0));
        assertTrue(AtmCameraMotion.followCursor(.5, -1, .1) > -1);
        assertTrue(AtmCameraMotion.followCursor(0, 10, .25) < 1);
    }

    @Test
    void downwardCameraRespondsGentlyToASuddenCursorMoveAtAnyFrameRate() {
        var docked = AtmCameraMotion.screen(0, 64, 0, 0, -1);
        double early = AtmCameraMotion.followVerticalCursor(0, 1, .2);
        assertTrue(AtmCameraMotion.cursorSway(docked, 0, early).pitch() < 2,
                "Moving the cursor to the bottom must not immediately whip the camera downward");
        double at60Fps = 0;
        double at144Fps = 0;
        for (int i = 0; i < 240; i++) at60Fps = AtmCameraMotion.followVerticalCursor(at60Fps, 1, 1.0 / 60);
        for (int i = 0; i < 576; i++) at144Fps = AtmCameraMotion.followVerticalCursor(at144Fps, 1, 1.0 / 144);
        assertEquals(at60Fps, at144Fps, 1e-12);
        float settledPitch = AtmCameraMotion.cursorSway(docked, 0, at60Fps).pitch();
        assertEquals(30, settledPitch, "Full keypad framing must remain reachable without holding the bottom edge");
    }
}
