package net.austizz.ultimatebankingsystem.client;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.austizz.ultimatebankingsystem.network.CourierOrderPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.fml.ModList;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/** Optional Xaero waypoint bridge. It has no hard class references to Xaero. */
@OnlyIn(Dist.CLIENT)
public final class CourierMapIntegration {
    private static final String XAERO_MOD_ID = "xaerominimap";
    private static final String WAYPOINT_OWNER = "UBS Courier";
    private static final String WAYPOINT_PREFIX = "UBS-";
    private static long lastSyncTick = Long.MIN_VALUE;
    private static String lastFingerprint = "";

    private CourierMapIntegration() {
    }

    public static void tick(Minecraft minecraft) {
        if (minecraft == null || minecraft.level == null || minecraft.player == null) {
            return;
        }
        long gameTime = minecraft.level.getGameTime();
        if (gameTime == lastSyncTick || gameTime % 20L != 0L) {
            return;
        }
        lastSyncTick = gameTime;
        syncXaero(minecraft);
    }

    public static void clear() {
        lastFingerprint = "";
        lastSyncTick = Long.MIN_VALUE;
        if (ModList.get().isLoaded(XAERO_MOD_ID)) {
            syncXaero(Minecraft.getInstance());
        }
    }

    private static void syncXaero(Minecraft minecraft) {
        if (!ModList.get().isLoaded(XAERO_MOD_ID) || minecraft == null || minecraft.level == null) {
            return;
        }
        String fingerprint = fingerprint(minecraft);
        if (fingerprint.equals(lastFingerprint)) {
            return;
        }
        Object manager = findWaypointsManager();
        if (manager == null) {
            return;
        }
        Object customWaypoints = invokeCustomWaypoints(manager);
        if (!(customWaypoints instanceof Map<?, ?>) && !(customWaypoints instanceof Collection<?>)) {
            return;
        }
        try {
            removeUbsWaypoints(customWaypoints);
            if (CourierDeliveryClientState.isActive()) {
                for (CourierOrderPayload delivery : CourierDeliveryClientState.deliveries()) {
                    if (!sameDimension(minecraft, delivery)) {
                        continue;
                    }
                    Object waypoint = createWaypoint(delivery);
                    if (waypoint != null) {
                        addWaypoint(customWaypoints, delivery.shortId(), waypoint);
                    }
                }
            }
        } catch (Throwable failure) {
            // Xaero's backing store is version-specific and may be immutable while its screen is open.
            UltimateBankingSystem.LOGGER.debug("Unable to update optional Xaero Courier waypoints", failure);
            return;
        }
        lastFingerprint = fingerprint;
    }

    private static String fingerprint(Minecraft minecraft) {
        StringBuilder result = new StringBuilder(minecraft.level.dimension().location().toString());
        for (CourierOrderPayload delivery : CourierDeliveryClientState.deliveries()) {
            result.append('|').append(delivery.shortId()).append(':')
                    .append(delivery.destinationX()).append(':')
                    .append(delivery.destinationY()).append(':')
                    .append(delivery.destinationZ()).append(':')
                    .append(delivery.destinationDimension());
        }
        return result.toString();
    }

    private static boolean sameDimension(Minecraft minecraft, CourierOrderPayload delivery) {
        return delivery != null && delivery.destinationDimension() != null
                && delivery.destinationDimension().equals(minecraft.level.dimension().location().toString());
    }

    private static Object findWaypointsManager() {
        String[] candidates = {
                "xaero.minimap.XaeroMinimap",
                "xaero.minimap.XaeroMinimapClient",
                "xaero.hud.minimap.XaeroMinimap"
        };
        for (String name : candidates) {
            try {
                Class<?> owner = Class.forName(name);
                Object singleton = null;
                for (Method method : owner.getDeclaredMethods()) {
                    if (!method.getName().equals("getWaypointsManager") || method.getParameterCount() != 0) {
                        continue;
                    }
                    method.setAccessible(true);
                    if (Modifier.isStatic(method.getModifiers())) {
                        return method.invoke(null);
                    }
                    if (singleton == null) {
                        singleton = findSingleton(owner);
                    }
                    if (singleton != null) {
                        return method.invoke(singleton);
                    }
                }
                for (Field field : owner.getDeclaredFields()) {
                    if (!field.getName().toLowerCase(java.util.Locale.ROOT).contains("waypoint")) {
                        continue;
                    }
                    field.setAccessible(true);
                    Object value = field.get(Modifier.isStatic(field.getModifiers()) ? null : findSingleton(owner));
                    if (value != null && value.getClass().getName().toLowerCase(java.util.Locale.ROOT)
                            .contains("waypoint")) {
                        return value;
                    }
                }
            } catch (Throwable ignored) {
                // Xaero changes its client internals between releases; absence or drift is optional.
            }
        }
        return null;
    }

    private static Object findSingleton(Class<?> owner) {
        for (String methodName : List.of("getInstance", "instance", "get")) {
            try {
                Method method = owner.getDeclaredMethod(methodName);
                if (Modifier.isStatic(method.getModifiers())) {
                    method.setAccessible(true);
                    return method.invoke(null);
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static Object invokeCustomWaypoints(Object manager) {
        try {
            Method method = manager.getClass().getMethod("getCustomWaypoints", String.class);
            return method.invoke(manager, WAYPOINT_OWNER);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void removeUbsWaypoints(Object waypoints) {
        if (waypoints instanceof Map<?, ?> map) {
            Iterator<? extends Map.Entry<?, ?>> iterator = map.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<?, ?> entry = iterator.next();
                if (isUbsWaypoint(entry.getValue())) {
                    iterator.remove();
                }
            }
        } else if (waypoints instanceof Collection<?> collection) {
            collection.removeIf(CourierMapIntegration::isUbsWaypoint);
        }
    }

    private static boolean isUbsWaypoint(Object waypoint) {
        if (waypoint == null) {
            return false;
        }
        try {
            Method method = waypoint.getClass().getMethod("getName");
            Object name = method.invoke(waypoint);
            return name != null && name.toString().startsWith(WAYPOINT_PREFIX);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static Object createWaypoint(CourierOrderPayload delivery) {
        try {
            Class<?> waypointClass = Class.forName("xaero.common.minimap.waypoints.Waypoint");
            Constructor<?> constructor = waypointClass.getConstructor(int.class, int.class, int.class,
                    String.class, String.class, int.class);
            return constructor.newInstance(delivery.destinationX(), delivery.destinationY(), delivery.destinationZ(),
                    WAYPOINT_PREFIX + delivery.shortId() + " " + delivery.destinationName(), "C", 4);
        } catch (Throwable ignored) {
            return null;
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void addWaypoint(Object waypoints, String id, Object waypoint) {
        if (waypoints instanceof Map map) {
            map.put(WAYPOINT_PREFIX + id, waypoint);
        } else if (waypoints instanceof Collection collection) {
            collection.add(waypoint);
        }
    }
}
