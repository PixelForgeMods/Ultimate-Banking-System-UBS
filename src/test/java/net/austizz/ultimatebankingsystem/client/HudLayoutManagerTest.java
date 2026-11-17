package net.austizz.ultimatebankingsystem.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HudLayoutManagerTest {
    @Test
    void bottomRightOverlaysStackWithoutTouchingTheVanillaHotbar() {
        HudLayoutManager.beginFrame(960, 540);

        HudLayoutManager.Bounds first = HudLayoutManager.placeBottomRight(
                "delivery-board", 286, 150, 8);
        HudLayoutManager.Bounds second = HudLayoutManager.placeBottomRight(
                "courier-routes", 226, 198, 8);

        assertFalse(first.intersects(second, 6));
        assertTrue(first.bottom() <= 540 - 8);
        assertTrue(second.bottom() <= first.y() - 6);
    }

    @Test
    void topCenterOverlaysMoveBelowTheVanillaBossBarBand() {
        HudLayoutManager.beginFrame(960, 540);

        HudLayoutManager.Bounds alert = HudLayoutManager.placeTopCenter(
                "action-alert", 300, 54, 8);

        assertTrue(alert.y() >= 48);
    }
}
