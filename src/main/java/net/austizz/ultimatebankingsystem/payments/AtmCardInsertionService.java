package net.austizz.ultimatebankingsystem.payments;

import net.austizz.ultimatebankingsystem.bank.centralbank.CentralBank;
import net.austizz.ultimatebankingsystem.bank.handler.BankManager;
import net.austizz.ultimatebankingsystem.block.ModBlocks;
import net.austizz.ultimatebankingsystem.item.ModItems;
import net.austizz.ultimatebankingsystem.network.AtmCardRequestPayload;
import net.austizz.ultimatebankingsystem.network.AtmCardResultPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class AtmCardInsertionService {
    public record Result(boolean accepted, InteractionHand hand, ItemStack card, String message, boolean needsPin) {
        static Result denied(String message) { return new Result(false, InteractionHand.MAIN_HAND, ItemStack.EMPTY, message, false); }
    }
    private AtmCardInsertionService() {}

    public static void handle(AtmCardRequestPayload request, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            var result = insert(player, request.atm(), BankManager.getCentralBank(player.server));
            if (!result.accepted() && player.level().getBlockEntity(request.atm()) instanceof net.austizz.ultimatebankingsystem.block.entity.custom.AtmBlockEntity machine && machine.isViewer(player)) {
                machine.lcd().setStatus(net.austizz.ultimatebankingsystem.atm.AtmLcdState.Status.WELCOME);
                machine.lcd().showNotice(result.message(), System.nanoTime()); machine.sync();
            }
            PacketDistributor.sendToPlayer(player, new AtmCardResultPayload(request.atm(), request.requestId(),
                    result.accepted(), result.hand(), result.card(), result.message(), result.needsPin()));
        });
    }

    public static Result insert(ServerPlayer player, BlockPos pos, CentralBank bank) {
        var result = validate(player, pos, bank);
        if (!result.accepted()) return result;
        if (!(player.level().getBlockEntity(pos) instanceof net.austizz.ultimatebankingsystem.block.entity.custom.AtmBlockEntity machine)
                || !machine.insertCard(player, result.hand(), result.needsPin())) return Result.denied("This ATM is already in use.");
        return result;
    }

    public static Result validate(Player player, BlockPos atm, CentralBank bank) {
        return validate(player, atm, bank, null);
    }

    public static Result validate(Player player, BlockPos atm, CentralBank bank, java.util.UUID requiredCard) {
        var level = player.level();
        if (!player.isAlive() || player.isSpectator() || !level.hasChunkAt(atm)
                || !(level.getBlockState(atm).getBlock() instanceof net.austizz.ultimatebankingsystem.block.custom.ATMBlock)
                || player.distanceToSqr(Vec3.atCenterOf(atm)) > 64) {
            return Result.denied("This ATM is no longer available.");
        }
        var hit = level.clip(new ClipContext(player.getEyePosition(), Vec3.atLowerCornerOf(atm).add(.5, 1.25, .5),
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        if (hit.getType() != HitResult.Type.MISS && !net.austizz.ultimatebankingsystem.block.custom.InWallATMBlock.origin(hit.getBlockPos(),level.getBlockState(hit.getBlockPos())).equals(atm)) return Result.denied("The ATM is obstructed.");
        if (bank == null) return Result.denied("Banking data is not available yet.");
        if (level.getBlockEntity(atm) instanceof net.austizz.ultimatebankingsystem.block.entity.custom.AtmBlockEntity machine && machine.dispensing() && !machine.deposit().active()
                && !(requiredCard != null && machine.isViewer(player) && !machine.heldCard(player).isEmpty() && machine.cardStage().equals("INSERTED"))) return Result.denied("Please collect the cash or receipt waiting at this ATM first.");
        if (level.getBlockEntity(atm) instanceof net.austizz.ultimatebankingsystem.block.entity.custom.AtmBlockEntity machine) {
            if (machine.viewer() != null && !machine.isViewer(player)) return Result.denied("This ATM is already in use.");
            var inserted = machine.heldCard(player);
            if (!inserted.isEmpty()) {
                if (requiredCard != null && !machine.cardStage().equals("INSERTED") && !machine.cardStage().equals("INSERTING"))
                    return Result.denied("Your card is being returned. Please insert it again to sign in.");
                var tag = net.austizz.ultimatebankingsystem.util.ItemStackDataCompat.getCustomData(inserted);
                if (requiredCard != null && (!tag.hasUUID(CreditCardService.TAG_CARD_ID) || !requiredCard.equals(tag.getUUID(CreditCardService.TAG_CARD_ID))))
                    return Result.denied("The inserted card has changed.");
                var checked = CreditCardService.validateCardStack(bank, inserted, null);
                return checked.valid() ? new Result(true, machine.cardHand(), inserted, "Card accepted.", !bank.SearchForAccountByAccountId(checked.accountId()).hasPin()) : Result.denied(checked.message());
            }
        }
        if (requiredCard != null) return Result.denied("Please insert your card into this ATM first.");
        String reason = "Hold a valid credit card in either hand.";
        for (var hand : InteractionHand.values()) {
            var stack = player.getItemInHand(hand);
            if (stack.isEmpty() || !stack.is(ModItems.CREDIT_CARD.get())) continue;
            var tag = net.austizz.ultimatebankingsystem.util.ItemStackDataCompat.getCustomData(stack);
            if (requiredCard != null && (!tag.hasUUID(CreditCardService.TAG_CARD_ID)
                    || !requiredCard.equals(tag.getUUID(CreditCardService.TAG_CARD_ID)))) continue;
            var validation = CreditCardService.validateCardStack(bank, stack, null);
            if (validation.valid() && !validation.cardNumber().isBlank()) {
                return new Result(true, hand, stack.copyWithCount(1), "Card accepted.", !bank.SearchForAccountByAccountId(validation.accountId()).hasPin());
            }
            reason = validation.valid() ? "This card has no issued card number." : validation.message();
        }
        return Result.denied(reason);
    }
}
