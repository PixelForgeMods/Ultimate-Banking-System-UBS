package net.austizz.ultimatebankingsystem.job;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CourierDeliveryMatcherTest {
    @Test
    void exactManifestItemInTheClickedHandCanCompleteAnAcceptedRoute() {
        assertTrue(CourierDeliveryMatcher.matches(
                "ACCEPTED", "minecraft:paper", 2, "minecraft:paper", 2));
    }

    @Test
    void wrongItemOrInsufficientQuantityDoesNotInterceptTheNpcMenu() {
        assertFalse(CourierDeliveryMatcher.matches(
                "ACCEPTED", "minecraft:paper", 2, "minecraft:paper", 1));
        assertFalse(CourierDeliveryMatcher.matches(
                "ACCEPTED", "minecraft:paper", 2, "minecraft:bread", 2));
        assertFalse(CourierDeliveryMatcher.matches(
                "DELIVERED", "minecraft:paper", 2, "minecraft:paper", 2));
    }
}
