package net.austizz.ultimatebankingsystem.pc;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.austizz.ultimatebankingsystem.block.ModBlocks;
import net.austizz.ultimatebankingsystem.network.PcLoginPayload.Action;
import net.austizz.ultimatebankingsystem.network.PcLoginPayload.Request;
import net.austizz.ultimatebankingsystem.network.PcLoginPayload.Response;
import net.austizz.ultimatebankingsystem.network.PcLoginPayload.Result;
import net.austizz.ultimatebankingsystem.pc.PcLoginSavedData.Credential;
import net.austizz.ultimatebankingsystem.pc.PcLoginSavedData.Profile;
import net.minecraft.Util;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;

public final class PcLoginService {
    private static final Semaphore HASH_SLOTS = new Semaphore(2);

    private PcLoginService() {}

    public static boolean validPassword(String secret) {
        return secret.length() >= 8 && secret.length() <= 64 && !secret.isBlank();
    }

    public static boolean validPin(String pin) {
        return pin.length() >= 4 && pin.length() <= 8 && pin.chars().allMatch(c -> c >= '0' && c <= '9');
    }

    public static void handle(ServerPlayer player, Request request) {
        var server = player.server;
        if (!canUse(player, request.pc())) {
            respond(player, request, Result.UNAVAILABLE, null);
            return;
        }

        var store = PcLoginSavedData.get(server);
        var uuid = player.getUUID();
        Profile profile = store.profile(uuid);
        if (request.action() == Action.STATUS || !canAttempt(request.action(), profile)) {
            respond(player, request, Result.READY, profile);
            return;
        }
        boolean valid = switch (request.action()) {
            case PIN -> validPin(request.secret());
            case CREATE_PIN -> validPassword(request.secret()) && validPin(request.pin());
            default -> validPassword(request.secret());
        };
        if (!valid) {
            respond(player, request, Result.INVALID, profile);
            return;
        }
        if (store.pending.contains(uuid) || store.retryDelay.getIfPresent(uuid) != null || !HASH_SLOTS.tryAcquire()) {
            respond(player, request, Result.WAIT, profile);
            return;
        }

        store.pending.add(uuid);
        CompletableFuture.supplyAsync(() -> authenticate(request, profile), Util.backgroundExecutor())
                .whenComplete((attempt, error) -> {
                    HASH_SLOTS.release();
                    server.execute(() -> {
                        store.pending.remove(uuid);
                        if (error == null && attempt.result() == Result.INCORRECT) {
                            store.retryDelay.put(uuid, true);
                        }
                        if (server.getPlayerList().getPlayer(uuid) != player) {
                            return;
                        }
                        if (!canUse(player, request.pc()) || error != null) {
                            if (error != null) {
                                UltimateBankingSystem.LOGGER.error("PC credential check failed", error);
                            }
                            respond(player, request, Result.UNAVAILABLE, null);
                        } else {
                            if (attempt.result() == Result.SIGNED_IN && attempt.profile() != profile) {
                                store.saveProfile(uuid, attempt.profile());
                            }
                            respond(player, request, attempt.result(), attempt.profile());
                        }
                    });
                });
    }

    private static boolean canUse(ServerPlayer player, GlobalPos pc) {
        var level = player.serverLevel();
        return player.isAlive() && !player.isSpectator() && pc.dimension().equals(level.dimension())
                && level.hasChunkAt(pc.pos()) && player.canInteractWithBlock(pc.pos(), 1.0)
                && level.getBlockState(pc.pos()).is(ModBlocks.BANK_OWNER_PC.get());
    }

    static boolean canAttempt(Action action, Profile profile) {
        return switch (action) {
            case STATUS -> false;
            case CREATE_PASSWORD -> profile == null;
            case PASSWORD -> profile != null;
            case PIN -> profile != null && profile.pin() != null;
            case CREATE_PIN -> profile != null && profile.pin() == null;
        };
    }

    static Attempt authenticate(Request request, Profile profile) {
        if (request.action() == Action.CREATE_PASSWORD) {
            return new Attempt(Result.SIGNED_IN, new Profile(Credential.create(request.secret()), null));
        }
        var credential = request.action() == Action.PIN ? profile.pin() : profile.password();
        if (!credential.matches(request.secret())) {
            return new Attempt(Result.INCORRECT, profile);
        }
        if (request.action() == Action.CREATE_PIN) {
            return new Attempt(Result.SIGNED_IN, new Profile(profile.password(), Credential.create(request.pin())));
        }
        return new Attempt(Result.SIGNED_IN, profile);
    }

    private static void respond(ServerPlayer player, Request request, Result result, Profile profile) {
        PacketDistributor.sendToPlayer(player, new Response(request.id(), result,
                profile != null, profile != null && profile.pin() != null));
    }

    record Attempt(Result result, Profile profile) {}
}
