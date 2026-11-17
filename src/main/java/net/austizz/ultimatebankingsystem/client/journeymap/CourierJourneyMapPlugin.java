package net.austizz.ultimatebankingsystem.client.journeymap;

import journeymap.api.v2.client.IClientAPI;
import journeymap.api.v2.client.IClientPlugin;
import journeymap.api.v2.common.JourneyMapPlugin;
import journeymap.api.v2.common.waypoint.Waypoint;
import journeymap.api.v2.common.waypoint.WaypointFactory;
import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.austizz.ultimatebankingsystem.client.CourierDeliveryClientState;
import net.austizz.ultimatebankingsystem.network.CourierOrderPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.ArrayList;
import java.util.List;

/** JourneyMap API v2 plugin; the class is only loaded when JourneyMap discovers it. */
@JourneyMapPlugin(apiVersion = "2.0.0")
public final class CourierJourneyMapPlugin implements IClientPlugin {
    private static final String MOD_ID = UltimateBankingSystem.MODID;
    private IClientAPI api;
    private String fingerprint = "";
    private final List<Waypoint> routeWaypoints = new ArrayList<>();

    @Override
    public void initialize(IClientAPI jmAPI) {
        this.api = jmAPI;
        NeoForge.EVENT_BUS.register(this);
        sync();
    }

    @Override
    public String getModId() {
        return MOD_ID;
    }

    @SubscribeEvent
    public void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null && minecraft.level.getGameTime() % 20L == 0L) {
            sync();
        }
    }

    private void sync() {
        if (api == null) {
            return;
        }
        String nextFingerprint = routeFingerprint();
        if (nextFingerprint.equals(fingerprint)) {
            return;
        }
        clearRouteWaypoints();
        for (CourierOrderPayload delivery : CourierDeliveryClientState.deliveries()) {
            Waypoint waypoint = createWaypoint(delivery);
            if (waypoint == null) {
                continue;
            }
            try {
                api.addWaypoint(MOD_ID, waypoint);
                routeWaypoints.add(waypoint);
            } catch (Throwable failure) {
                UltimateBankingSystem.LOGGER.debug("Unable to add Courier waypoint to JourneyMap", failure);
            }
        }
        fingerprint = nextFingerprint;
    }

    private Waypoint createWaypoint(CourierOrderPayload delivery) {
        ResourceLocation dimensionId = ResourceLocation.tryParse(delivery.destinationDimension());
        if (dimensionId == null) {
            return null;
        }
        try {
            ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, dimensionId);
            Waypoint waypoint = WaypointFactory.createWaypoint(MOD_ID,
                    new BlockPos(delivery.destinationX(), delivery.destinationY(), delivery.destinationZ()),
                    "Courier #" + delivery.shortId() + " " + delivery.destinationName(), dimension, false);
            waypoint.setDescription("Deliver " + delivery.itemName() + " x" + delivery.quantity()
                    + " | Reward " + delivery.rewardCents() + " cents");
            waypoint.setColor(0x43D99B);
            waypoint.setIconColor(0x43D99B);
            waypoint.setCustomData("ubs_courier_route", delivery.shortId());
            waypoint.setShowBeacon(false);
            waypoint.setShowLabel(true);
            waypoint.setShowOnMap(true);
            waypoint.setShowInWorld(true);
            return waypoint;
        } catch (Throwable failure) {
            UltimateBankingSystem.LOGGER.debug("Unable to create Courier JourneyMap waypoint", failure);
            return null;
        }
    }

    private void clearRouteWaypoints() {
        for (Waypoint waypoint : routeWaypoints) {
            try {
                api.removeWaypoint(MOD_ID, waypoint);
            } catch (Throwable failure) {
                UltimateBankingSystem.LOGGER.debug("Unable to remove Courier JourneyMap waypoint", failure);
            }
        }
        routeWaypoints.clear();
        if (!CourierDeliveryClientState.isActive()) {
            try {
                for (Waypoint waypoint : new ArrayList<>(api.getWaypoints(MOD_ID))) {
                    if (waypoint.getCustomData("ubs_courier_route") != null) {
                        api.removeWaypoint(MOD_ID, waypoint);
                    }
                }
            } catch (Throwable failure) {
                UltimateBankingSystem.LOGGER.debug("Unable to clear stale Courier JourneyMap waypoints", failure);
            }
        }
    }

    private String routeFingerprint() {
        StringBuilder result = new StringBuilder();
        for (CourierOrderPayload delivery : CourierDeliveryClientState.deliveries()) {
            result.append(delivery.shortId()).append(':').append(delivery.destinationDimension()).append(':')
                    .append(delivery.destinationX()).append(':').append(delivery.destinationY()).append(':')
                    .append(delivery.destinationZ()).append('|');
        }
        return result.toString();
    }
}
