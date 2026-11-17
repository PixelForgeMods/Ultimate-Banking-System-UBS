package net.austizz.ultimatebankingsystem.atm;

import net.austizz.ultimatebankingsystem.block.entity.custom.AtmBlockEntity;
import net.austizz.ultimatebankingsystem.payments.AtmDepositCustody;
import net.austizz.ultimatebankingsystem.account.AccountHolder;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import java.util.Arrays;
import java.util.UUID;
import static net.austizz.ultimatebankingsystem.atm.AtmDispenseMotion.Phase.*;

public final class AtmDeposit {
    private final AtmBlockEntity atm;
    private UUID owner;
    private int[] detected = new int[12], feeding = new int[12];
    public AtmDeposit(AtmBlockEntity atm) { this.atm = atm; }
    public UUID owner() { return owner; }
    public int[] detected() { return detected.clone(); }
    public int[] feeding() { return feeding.clone(); }
    public long cents() { return net.austizz.ultimatebankingsystem.item.DollarBills.totalCashValueCentsLong(detected); }
    public boolean active() { return atm.phase().name().startsWith("DEPOSIT_"); }
    public boolean feedingScreen() { return atm.phase() == DEPOSIT_OPENING || atm.phase() == DEPOSIT_FEEDING || atm.phase() == DEPOSIT_INSERTING; }
    private GlobalPos position() { return GlobalPos.of(atm.getLevel().dimension(), atm.getBlockPos()); }
    private AtmDepositCustody custody() { return AtmDepositCustody.get(atm.getLevel().getServer()); }
    public boolean open(ServerPlayer player, UUID account) {
        if (!atm.isViewer(player) || !atm.canReserve() || !custody().open(player, position(), account)) return false;
        owner = player.getUUID(); detected = new int[12]; feeding = new int[12]; atm.depositPhase(DEPOSIT_OPENING); return true;
    }
    public boolean feed(ServerPlayer player, int[] plan) {
        if (!player.getUUID().equals(owner) || atm.phase() != DEPOSIT_FEEDING || !custody().feed(player, position(), plan)) return false;
        detected = custody().bills(owner, position()); feeding = Arrays.copyOf(plan, 12);
        atm.depositPhase(DEPOSIT_INSERTING); return true;
    }
    public boolean count(ServerPlayer player) {
        if (!player.getUUID().equals(owner) || atm.phase() != DEPOSIT_FEEDING || cents() <= 0) return false;
        atm.depositPhase(DEPOSIT_COUNTING); return true;
    }
    public boolean back(ServerPlayer player) {
        if (!player.getUUID().equals(owner) || atm.phase() != DEPOSIT_REVIEW) return false;
        atm.depositPhase(DEPOSIT_OPENING); return true;
    }
    public boolean confirm(ServerPlayer player, AccountHolder account) {
        if (!player.getUUID().equals(owner) || atm.phase() != DEPOSIT_REVIEW || !custody().confirm(player, position(), account)) return false;
        atm.prepareReceipt(player, true, cents());
        finish("Deposit complete", account.getBalance().toPlainString()); atm.offerDepositReceipt(player); return true;
    }
    public boolean returnCash(ServerPlayer player) {
        if (!player.getUUID().equals(owner) || !(atm.phase() == DEPOSIT_FEEDING || atm.phase() == DEPOSIT_REVIEW)) return false;
        if (cents() == 0) { custody().refund(player); finish("Deposit cancelled", ""); }
        else atm.depositPhase(atm.phase() == DEPOSIT_FEEDING ? DEPOSIT_RETURNING : DEPOSIT_RETURN_OPENING);
        return true;
    }
    public boolean pickUp(ServerPlayer player) {
        if (!player.getUUID().equals(owner) || atm.phase() != DEPOSIT_RETURN_READY) return false;
        atm.depositPhase(DEPOSIT_PICKUP); return true;
    }
    private void finish(String message, String balance) {
        detected = new int[12]; feeding = new int[12];
        atm.lcd().setStatus(AtmLcdState.Status.PROCESSING); atm.lcd().actionResult(true, balance, message);
        if (atm.phase() == DEPOSIT_REVIEW) { owner = null; atm.depositPhase(IDLE); }
        else atm.depositPhase(DEPOSIT_CLOSING);
    }
    public void abort(ServerPlayer player) {
        if (!active()) return;
        if (player != null && player.getUUID().equals(owner)) custody().refund(player);
        owner = null; detected = new int[12]; feeding = new int[12]; atm.depositPhase(IDLE);
    }
    public void tick() {
        if (!active()) return;
        if (atm.viewer() == null) { abort(owner == null ? null : atm.getLevel().getServer().getPlayerList().getPlayer(owner)); return; }
        if (owner == null || !custody().contains(owner, position()) && atm.phase() != DEPOSIT_CLOSING) { owner = null; detected = new int[12]; atm.depositPhase(IDLE); return; }
        double age = atm.phaseAge(0);
        switch (atm.phase()) {
            case DEPOSIT_OPENING -> { if (age >= AtmSoundTiming.SHUTTER_OPEN) atm.depositPhase(DEPOSIT_FEEDING); }
            case DEPOSIT_INSERTING -> { if (age >= AtmDepositMotion.INSERT_TICKS) atm.depositPhase(DEPOSIT_FEEDING); }
            case DEPOSIT_COUNTING -> { if (age >= AtmDepositMotion.COUNT_TICKS) atm.depositPhase(DEPOSIT_REVIEW); }
            case DEPOSIT_RETURN_OPENING -> { if (age >= AtmSoundTiming.SHUTTER_OPEN) atm.depositPhase(DEPOSIT_RETURNING); }
            case DEPOSIT_RETURNING -> { if (age >= AtmDepositMotion.RETURN_TICKS) atm.depositPhase(DEPOSIT_RETURN_READY); }
            case DEPOSIT_PICKUP -> {
                var player = atm.getLevel().getServer().getPlayerList().getPlayer(owner);
                if (player == null || player.level() != atm.getLevel() || !player.isAlive() || player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(atm.getBlockPos())) > 64) { atm.depositPhase(DEPOSIT_RETURN_READY); return; }
                if (age >= 26) { custody().refund(player); finish("Cash returned", ""); }
            }
            case DEPOSIT_CLOSING -> { if (age >= AtmSoundTiming.SHUTTER_OPEN) { owner = null; atm.depositPhase(IDLE); } }
            default -> { }
        }
    }
    public void save(CompoundTag tag) {
        if (owner != null) tag.putUUID("deposit_owner", owner);
        tag.putIntArray("deposit_detected", detected); tag.putIntArray("deposit_feeding", feeding);
    }
    public void load(CompoundTag tag) {
        owner = tag.hasUUID("deposit_owner") ? tag.getUUID("deposit_owner") : null;
        detected = Arrays.copyOf(tag.getIntArray("deposit_detected"), 12); feeding = Arrays.copyOf(tag.getIntArray("deposit_feeding"), 12);
    }
}
