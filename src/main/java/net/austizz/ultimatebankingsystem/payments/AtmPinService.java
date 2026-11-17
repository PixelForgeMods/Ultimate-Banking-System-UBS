package net.austizz.ultimatebankingsystem.payments;

import net.austizz.ultimatebankingsystem.bank.centralbank.CentralBank;
import net.austizz.ultimatebankingsystem.bank.handler.BankManager;
import net.austizz.ultimatebankingsystem.network.AtmPinRequestPayload;
import net.austizz.ultimatebankingsystem.network.AtmPinResultPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import java.util.UUID;

public final class AtmPinService {
    public record Result(boolean accepted, String message) {}
    private AtmPinService() {}

    public static void handle(AtmPinRequestPayload request, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            long now = System.currentTimeMillis();
            var data = player.getPersistentData();
            AtmPinResultPayload result;
            if (now < data.getLong("ubs_atm_pin_next")) result = new AtmPinResultPayload(request.atm(), request.requestId(), false, "Please wait a moment before trying again.");
            else {
                data.putLong("ubs_atm_pin_next", now + 1000);
                result = authenticate(player, request, BankManager.getCentralBank(player.server));
            }
            if (player.level().getBlockEntity(request.atm()) instanceof net.austizz.ultimatebankingsystem.block.entity.custom.AtmBlockEntity atm && atm.isViewer(player)) {
                atm.lcd().pinResult(result.accepted(), result.message()); atm.sync();
            }
            PacketDistributor.sendToPlayer(player, result);
        });
    }

    public static AtmPinResultPayload authenticate(Player player, AtmPinRequestPayload request, CentralBank bank) {
        var result = verify(player, request.atm(), request.cardId(), request.pin(), request.create(), request.repeatedPin(), bank);
        AtmSessionService.close(player);
        UUID token = new UUID(0, 0);
        if (result.accepted()) {
            var card = AtmCardInsertionService.validate(player, request.atm(), bank, request.cardId());
            var validated = CreditCardService.validateCardStack(bank, card.card(), null);
            token = AtmSessionService.open(player, request.atm(), request.cardId(), validated.accountId());
        }
        return new AtmPinResultPayload(request.atm(), request.requestId(), result.accepted(), result.message(), token);
    }

    public static Result verify(Player player, BlockPos atm, UUID cardId, String pin, CentralBank bank) {
        return verify(player, atm, cardId, pin, false, "", bank);
    }

    public static Result verify(Player player, BlockPos atm, UUID cardId, String pin, boolean create, String repeatedPin, CentralBank bank) {
        if (pin == null || !pin.matches("[0-9]{4}")) return new Result(false, "PIN must be exactly 4 digits.");
        if (cardId == null) return new Result(false, "Please insert your card into this ATM.");
        var card = AtmCardInsertionService.validate(player, atm, bank, cardId);
        if (!card.accepted()) return new Result(false, card.message());
        var validation = CreditCardService.validateCardStack(bank, card.card(), null);
        if (!validation.valid()) return new Result(false, validation.message());
        var account = bank.SearchForAccountByAccountId(validation.accountId());
        long now = System.currentTimeMillis();
        if (account != null && now < account.atmPinLockedUntil()) return new Result(false, "Too many incorrect PINs. Try again in " + ((account.atmPinLockedUntil() - now + 999) / 1000) + " seconds.");
        if (account != null && create) {
            if (account.hasPin() && (!(player instanceof ServerPlayer serverPlayer)
                    || !(player.level().getBlockEntity(atm) instanceof net.austizz.ultimatebankingsystem.block.entity.custom.AtmBlockEntity machine)
                    || !AtmSessionService.authorizes(serverPlayer, validation.accountId(), machine)))
                return new Result(false, "Sign in with your current PIN before changing it.");
            if (!pin.equals(repeatedPin)) return new Result(false, "PINs do not match. Please try again.");
            return new Result(account.setPin(pin), "");
        }
        if (account == null || !account.hasPin()) return new Result(false, "Set a 4-digit PIN for this account before using the ATM.");
        boolean accepted = account.matchesPin(pin);
        account.recordAtmPinAttempt(accepted, now);
        return new Result(accepted, accepted ? "" : now < account.atmPinLockedUntil()
                ? "Too many incorrect PINs. Please wait 60 seconds." : "Incorrect PIN. Please try again.");
    }
}
