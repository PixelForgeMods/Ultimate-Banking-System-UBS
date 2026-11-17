package net.austizz.ultimatebankingsystem.network;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.austizz.ultimatebankingsystem.job.JobService;
import net.austizz.ultimatebankingsystem.npc.skin.NpcSkinService;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Isolated registration for job packets so the legacy payload registry stays stable. */
public final class JobPayloads {
    private JobPayloads() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(JobOpenPayload.TYPE, JobOpenPayload.STREAM_CODEC, JobPayloads::handleOpen);
        registrar.playToClient(CourierDeliveryStatePayload.TYPE, CourierDeliveryStatePayload.STREAM_CODEC,
                JobPayloads::handleCourierDeliveryState);
        registrar.playToServer(JobActionPayload.TYPE, JobActionPayload.STREAM_CODEC, JobPayloads::handleAction);
        registrar.playToClient(NpcSkinEditorPayload.TYPE, NpcSkinEditorPayload.STREAM_CODEC,
                JobPayloads::handleNpcSkinEditor);
        registrar.playToClient(NpcSkinSyncPayload.TYPE, NpcSkinSyncPayload.STREAM_CODEC,
                JobPayloads::handleNpcSkinSync);
        registrar.playToServer(NpcSkinActionPayload.TYPE, NpcSkinActionPayload.STREAM_CODEC,
                JobPayloads::handleNpcSkinAction);
    }

    private static void handleOpen(JobOpenPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientPayloadInvoker.invoke("handleJobOpen", payload));
    }

    private static void handleCourierDeliveryState(CourierDeliveryStatePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientPayloadInvoker.invoke("handleCourierDeliveryState", payload));
    }

    private static void handleAction(JobActionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                try {
                    JobService.handleAction(player, payload);
                } catch (RuntimeException exception) {
                    UltimateBankingSystem.LOGGER.warn("Rejected job request from {}: {}",
                            player.getGameProfile().getName(), exception.getMessage());
                }
            }
        });
    }

    private static void handleNpcSkinEditor(NpcSkinEditorPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientPayloadInvoker.invoke("handleNpcSkinEditor", payload));
    }

    private static void handleNpcSkinSync(NpcSkinSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientPayloadInvoker.invoke("handleNpcSkinSync", payload));
    }

    private static void handleNpcSkinAction(NpcSkinActionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                NpcSkinService.handleAction(player, payload);
            }
        });
    }
}
