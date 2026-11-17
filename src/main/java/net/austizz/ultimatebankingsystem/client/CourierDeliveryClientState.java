package net.austizz.ultimatebankingsystem.client;

import net.austizz.ultimatebankingsystem.network.CourierDeliveryStatePayload;
import net.austizz.ultimatebankingsystem.network.CourierOrderPayload;

import java.util.List;

/** Client-only accepted Courier route state used by the live HUD and map adapters. */
public final class CourierDeliveryClientState {
    private static List<CourierOrderPayload> deliveries = List.of();
    private static long receivedAtNanos;
    private static boolean collapsed;

    private CourierDeliveryClientState() {
    }

    public static void apply(CourierDeliveryStatePayload payload) {
        if (payload == null || !payload.active()) {
            clear();
            return;
        }
        boolean wasActive = isActive();
        deliveries = List.copyOf(payload.deliveries());
        receivedAtNanos = System.nanoTime();
        if (!wasActive) {
            collapsed = false;
        }
        CourierMapIntegration.tick(net.minecraft.client.Minecraft.getInstance());
    }

    public static void clear() {
        deliveries = List.of();
        receivedAtNanos = 0L;
        collapsed = false;
        CourierMapIntegration.clear();
    }

    public static boolean isActive() {
        return !deliveries.isEmpty();
    }

    public static List<CourierOrderPayload> deliveries() {
        return deliveries;
    }

    public static long secondsRemaining(CourierOrderPayload delivery) {
        if (delivery == null || receivedAtNanos <= 0L) {
            return delivery == null ? 0L : Math.max(0L, delivery.secondsRemaining());
        }
        long elapsed = Math.max(0L, (System.nanoTime() - receivedAtNanos) / 1_000_000_000L);
        return Math.max(0L, delivery.secondsRemaining() - elapsed);
    }

    public static boolean isCollapsed() {
        return collapsed;
    }

    public static void toggleCollapsed() {
        if (isActive()) {
            collapsed = !collapsed;
        }
    }
}
