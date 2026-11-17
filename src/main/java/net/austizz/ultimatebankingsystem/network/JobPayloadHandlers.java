package net.austizz.ultimatebankingsystem.network;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.austizz.ultimatebankingsystem.job.JobService;
import net.austizz.ultimatebankingsystem.npc.skin.NpcSkinService;
import net.minecraft.server.level.ServerPlayer;

/** Server-side handlers for job and NPC-skin payloads. */
public final class JobPayloadHandlers {
    private JobPayloadHandlers() {
    }

    public static void handleOpen(JobOpenPayload payload) {
        ClientPayloadInvoker.invoke("handleJobOpen", payload);
    }

    public static void handleCourierDeliveryState(CourierDeliveryStatePayload payload) {
        ClientPayloadInvoker.invoke("handleCourierDeliveryState", payload);
    }

    public static void handleAction(ServerPlayer player, JobActionPayload payload) {
        try {
            JobService.handleAction(player, payload);
        } catch (RuntimeException exception) {
            UltimateBankingSystem.LOGGER.warn("Rejected job request from {}: {}",
                    player.getGameProfile().getName(), exception.getMessage());
        }
    }

    public static void handleNpcSkinEditor(NpcSkinEditorPayload payload) {
        ClientPayloadInvoker.invoke("handleNpcSkinEditor", payload);
    }

    public static void handleNpcSkinSync(NpcSkinSyncPayload payload) {
        ClientPayloadInvoker.invoke("handleNpcSkinSync", payload);
    }

    public static void handleNpcSkinAction(ServerPlayer player, NpcSkinActionPayload payload) {
        NpcSkinService.handleAction(player, payload);
    }
}
