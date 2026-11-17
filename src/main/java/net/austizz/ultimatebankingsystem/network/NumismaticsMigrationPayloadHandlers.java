package net.austizz.ultimatebankingsystem.network;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.austizz.ultimatebankingsystem.migration.numismatics.NumismaticsMigrationService;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Locale;

/** Server-side actions for Numismatics migration payloads. */
public final class NumismaticsMigrationPayloadHandlers {
    private NumismaticsMigrationPayloadHandlers() {
    }

    public static void handleOpen(NumismaticsMigrationOpenPayload payload) {
        try {
            Class<?> client = Class.forName("net.austizz.ultimatebankingsystem.client.NumismaticsMigrationClient");
            Method accept = client.getMethod("accept", String.class);
            accept.invoke(null, payload.snapshotJson());
        } catch (ReflectiveOperationException exception) {
            UltimateBankingSystem.LOGGER.error("Could not open the Numismatics migration client", exception);
        }
    }

    public static void handleAction(ServerPlayer player, NumismaticsMigrationActionPayload payload) {
        String action = payload.action() == null ? "" : payload.action().trim().toUpperCase(Locale.ROOT);
        switch (action) {
            case "REFRESH" -> NumismaticsMigrationService.refresh(player, payload.token());
            case "WORLD_SOURCE" -> NumismaticsMigrationService.selectWorldSource(player, payload.token());
            case "SERVER_PATH" -> NumismaticsMigrationService.selectServerSource(player, payload.token(), Path.of(payload.text()));
            case "SET_OPTIONS" -> NumismaticsMigrationService.setOptions(player, payload.token(),
                    payload.number(), payload.text(), payload.flag(), payload.secondFlag());
            case "PREFLIGHT" -> NumismaticsMigrationService.runPreflight(player, payload.token());
            case "EXECUTE" -> NumismaticsMigrationService.execute(player, payload.token());
            case "RESUME" -> NumismaticsMigrationService.resume(player, payload.token());
            case "RESET" -> NumismaticsMigrationService.reset(player, payload.token());
            case "ROLLBACK" -> NumismaticsMigrationService.requestRollback(player, payload.token());
            case "CLAIM_RECOVERY" -> NumismaticsMigrationService.claimRecovery(player, payload.token());
            case "STOP_SERVER" -> NumismaticsMigrationService.stopServer(player, payload.token());
            default -> throw new IllegalArgumentException("Unknown Numismatics migration action: " + action);
        }
    }

    public static void handleUploadBegin(ServerPlayer player, NumismaticsMigrationUploadBeginPayload payload) {
        NumismaticsMigrationService.beginUpload(player, payload.token(), payload.uploadId(),
                payload.fileName(), payload.size(), payload.chunks(), payload.sha256());
    }

    public static void handleUploadChunk(ServerPlayer player, NumismaticsMigrationUploadChunkPayload payload) {
        NumismaticsMigrationService.acceptUploadChunk(player, payload.token(), payload.uploadId(),
                payload.index(), payload.bytes());
    }

    public static void handleUploadFinish(ServerPlayer player, NumismaticsMigrationUploadFinishPayload payload) {
        NumismaticsMigrationService.finishUpload(player, payload.token(), payload.uploadId());
    }

    public static void reportRejected(ServerPlayer player, RuntimeException exception) {
        UltimateBankingSystem.LOGGER.warn("Rejected Numismatics migration request from {}: {}",
                player.getGameProfile().getName(), exception.getMessage());
        player.sendSystemMessage(Component.literal(exception.getMessage() == null
                ? "The migration request failed." : exception.getMessage()).withStyle(ChatFormatting.RED));
    }
}
