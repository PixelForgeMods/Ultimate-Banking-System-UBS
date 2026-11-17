package net.austizz.ultimatebankingsystem.block.entity.custom;

import net.austizz.ultimatebankingsystem.block.entity.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.austizz.ultimatebankingsystem.atm.AtmSoundTiming;
import net.austizz.ultimatebankingsystem.sound.ModSounds;
import net.minecraft.sounds.SoundSource;
import net.austizz.ultimatebankingsystem.atm.AtmDispenseMotion.Phase;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.austizz.ultimatebankingsystem.block.ModBlocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;

import java.util.Arrays;
import java.util.UUID;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.Containers;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.austizz.ultimatebankingsystem.payments.WalletBankingCashService;
import net.austizz.ultimatebankingsystem.item.DollarBills;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;

@EventBusSubscriber(modid = UltimateBankingSystem.MODID)
public final class AtmBlockEntity extends BlockEntity {
    private final long[] presses = new long[24];
    private final net.austizz.ultimatebankingsystem.atm.AtmLcdState lcd = new net.austizz.ultimatebankingsystem.atm.AtmLcdState();

    public net.austizz.ultimatebankingsystem.atm.AtmLcdState lcd() { return lcd; }

    public AtmBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ATM.get(), pos, state);
    }

    private final BlockState[] wallMaterials = new BlockState[6];
    public BlockState wallMaterial(int cell) { return wallMaterials[cell]; }
    public void setWallMaterial(int cell, BlockState material, net.minecraft.world.entity.player.Player player, ItemStack held) {
        if(level==null || level.isClientSide || cell<0 || cell>=6 || viewer!=null
                || !(getBlockState().getBlock() instanceof net.austizz.ultimatebankingsystem.block.custom.InWallATMBlock)) return;
        BlockState previous=wallMaterials[cell];
        if(previous==material || previous==null && material.isAir()) return;
        if(!material.isAir() && (held.isEmpty() || !(held.getItem() instanceof net.minecraft.world.item.BlockItem block)
                || block.getBlock()!=material.getBlock() || material.hasBlockEntity() || !material.isSolidRender(level,worldPosition))) return;
        wallMaterials[cell]=material.isAir()?null:material;
        if(!material.isAir() && !player.getAbilities().instabuild) held.shrink(1);
        if(previous!=null && !player.getAbilities().instabuild) {
            var refund=new ItemStack(previous.getBlock());
            if(!player.getInventory().add(refund)) player.drop(refund,false);
        }
        sync();
    }
    public void dropWallMaterials() {
        if(level==null || level.isClientSide) return;
        for(int cell=0;cell<6;cell++) {
            var material=wallMaterials[cell];wallMaterials[cell]=null;
            if(material!=null) Containers.dropItemStack(level,worldPosition.getX()+.5,worldPosition.getY()+.5,worldPosition.getZ()+.5,new ItemStack(material.getBlock()));
        }
        setChanged();
    }

    public boolean press(int index, long now) {
        if (index < 0 || index >= presses.length
                || presses[index] != 0 && now - presses[index] < 90_000_000L) return false;
        presses[index] = now;
        return true;
    }

    public long pressedAt(int index) {
        long remote = publicButton == index && level != null ? System.nanoTime() - Math.max(0, level.getGameTime() - buttonTick) * 50_000_000L : 0;
        return Math.max(presses[index], remote);
    }

    private final net.austizz.ultimatebankingsystem.atm.AtmDeposit deposit = new net.austizz.ultimatebankingsystem.atm.AtmDeposit(this);
    public net.austizz.ultimatebankingsystem.atm.AtmDeposit deposit() { return deposit; }
    public void depositPhase(Phase next) { change(next); }
    private Phase phase = Phase.IDLE;
    private long phaseStarted;
    private int[] cash = new int[12];
    private UUID owner, collector;
    private long withdrawalDollars;
    private boolean cardTaken;
    private boolean sessionExpired, depositReceipt;
    private String receiptDetails = "";
    public String receiptDetails() { return receiptDetails; }
    public Phase phase() { return phase; }
    public double phaseAge(float partial) { return level == null ? 0 : Math.max(0, level.getGameTime() - phaseStarted + partial); }
    public int[] cashPlan() { return cash.clone(); }
    public UUID collector() { return collector; }
    public long withdrawalDollars() { return withdrawalDollars; }
    public boolean dispensing() { return phase != Phase.IDLE; }
    public boolean canReserve() { return !dispensing() && Arrays.stream(cash).sum() == 0; }

    private final net.austizz.ultimatebankingsystem.atm.AtmLcdState sharedLcd = new net.austizz.ultimatebankingsystem.atm.AtmLcdState();
    private UUID viewer, cardOwner;
    private long viewPing, cardStarted, buttonTick;
    private int publicButton = -1;
    private String cardStage = "EMPTY";
    private ItemStack visualCard = ItemStack.EMPTY;
    private net.minecraft.world.InteractionHand cardHand = net.minecraft.world.InteractionHand.MAIN_HAND;
    public boolean sessionExpired() { return sessionExpired; }
    public UUID viewer() { return viewer; }
    public UUID cardOwner() { return cardOwner; }
    public String cardStage() { return cardStage; }
    public double cardAge(float partial) { return level == null ? 0 : Math.max(0, level.getGameTime() - cardStarted + partial); }
    public ItemStack visualCard() { return visualCard; }
    public net.minecraft.world.InteractionHand cardHand() { return cardHand; }
    public net.austizz.ultimatebankingsystem.atm.AtmLcdState sharedLcd() { return sharedLcd; }
    public boolean isViewer(net.minecraft.world.entity.player.Player player) { return player.getUUID().equals(viewer); }
    public boolean claimView(ServerPlayer player) {
        if (!player.isAlive() || player.isSpectator() || player.level() != level
                || player.distanceToSqr(Vec3.atCenterOf(worldPosition)) > 64
                || viewer != null && !isViewer(player)) return false;
        viewer = player.getUUID(); viewPing = level.getGameTime(); sync(); return true;
    }
    public void sync() {
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }
    private net.minecraft.core.GlobalPos globalPos() { return net.minecraft.core.GlobalPos.of(level.dimension(), worldPosition); }
    public ItemStack heldCard(net.minecraft.world.entity.player.Player player) {
        if (!isViewer(player) || !player.getUUID().equals(cardOwner) || level == null || level.isClientSide) return ItemStack.EMPTY;
        return net.austizz.ultimatebankingsystem.payments.AtmCardCustody.get(level.getServer()).card(cardOwner, globalPos());
    }
    public boolean insertCard(ServerPlayer player, net.minecraft.world.InteractionHand hand, boolean needsPin) {
        if (!isViewer(player) || !cardStage.equals("EMPTY") || dispensing()) return false;
        int slot = hand == net.minecraft.world.InteractionHand.OFF_HAND ? 40 : player.getInventory().selected;
        if (!net.austizz.ultimatebankingsystem.payments.AtmCardCustody.get(player.server).insert(player, globalPos(), slot)) return false;
        sessionExpired = false; cardOwner = player.getUUID(); cardHand = hand;
        visualCard = heldCard(player);
        var source = net.austizz.ultimatebankingsystem.util.ItemStackDataCompat.getCustomData(visualCard);
        var visible = new CompoundTag();
        for (String key : new String[]{net.austizz.ultimatebankingsystem.payments.CreditCardService.TAG_CARD_NUMBER, net.austizz.ultimatebankingsystem.payments.CreditCardService.TAG_BANK_NAME, net.austizz.ultimatebankingsystem.payments.CreditCardService.TAG_HOLDER_NAME, net.austizz.ultimatebankingsystem.payments.CreditCardService.TAG_EXPIRY_AT, net.austizz.ultimatebankingsystem.payments.CreditCardService.TAG_CVC})
            if (source.contains(key)) visible.put(key, source.get(key).copy());
        net.austizz.ultimatebankingsystem.util.ItemStackDataCompat.setCustomData(visualCard, visible);
        lcd.prepareCard(needsPin); lcd.setStatus(net.austizz.ultimatebankingsystem.atm.AtmLcdState.Status.INSERTING);
        cardStage("INSERTING"); startCardIntake(); return true;
    }
    private void cardStage(String stage) { cardStage = stage; cardStarted = level.getGameTime(); sync(); }
    private void returnCard(ServerPlayer player) {
        if (player != null && player.getUUID().equals(cardOwner)) {
            net.austizz.ultimatebankingsystem.payments.AtmCardCustody.get(player.server).giveBack(player);
            net.austizz.ultimatebankingsystem.payments.AtmSessionService.close(player);
        }
        intakeStarted = -1; intakeStage = 0;
        cardOwner = null; visualCard = ItemStack.EMPTY; cardStage("EMPTY");
        lcd.setStatus(net.austizz.ultimatebankingsystem.atm.AtmLcdState.Status.WELCOME); sync();
    }
    public void releaseView(ServerPlayer player) {
        if (!isViewer(player)) return;
        deposit.abort(player);
        returnCard(player); viewer = null;
        if (phase == Phase.WAIT_CARD || phase == Phase.TAKE_CARD || phase == Phase.CARD_REMOVING) change(Phase.WAIT_CASH);
        sync();
    }
    public void expireSession(ServerPlayer player) {
        if (!isViewer(player) || sessionExpired) return;
        sessionExpired = true;
        net.austizz.ultimatebankingsystem.payments.AtmSessionService.close(player);
        deposit.abort(player);
        if (phase == Phase.IDLE && cardStage.equals("INSERTED")) { cardMotor(); cardStage("EJECTING"); }
        lcd.setStatus(net.austizz.ultimatebankingsystem.atm.AtmLcdState.Status.REMOVING);
        lcd.showNotice("Session expired. Your card is being returned.", System.nanoTime()); sync();
    }
    private void tickTerminal() {
        if (viewer != null) {
            var player = level.getServer().getPlayerList().getPlayer(viewer);
            if (player == null || !player.isAlive() || player.level() != level
                    || player.distanceToSqr(Vec3.atCenterOf(worldPosition)) > 64 || level.getGameTime() - viewPing > 200) {
                if (player != null) net.austizz.ultimatebankingsystem.payments.AtmTerminalService.close(player);
                else { deposit.abort(null); viewer = null; returnCard(null); }
                sync();
            }
        }
        if (cardOwner == null) return;
        var player = level.getServer().getPlayerList().getPlayer(cardOwner);
        if (net.austizz.ultimatebankingsystem.payments.AtmCardCustody.get(level.getServer()).card(cardOwner, globalPos()).isEmpty()) {
            returnCard(null); return;
        }
        if (viewer == null) { returnCard(player); return; }
        if (player != null && !sessionExpired && net.austizz.ultimatebankingsystem.payments.AtmSessionService.expired(player)) {
            expireSession(player);
        }
        double age = cardAge(0);
        if (cardStage.equals("INSERTING") && age >= AtmSoundTiming.CARD_INSERT) cardStage("INSERTED");
        if (cardStage.equals("INSERTED")) {
            var previous = lcd.status();
            lcd.updateCardStatus(net.austizz.ultimatebankingsystem.atm.AtmLcdState.Status.INSERTED, level.getGameTime() * 50_000_000L);
            if (previous != lcd.status()) sync();
        }
        if (cardStage.equals("EJECTING") && age >= AtmSoundTiming.CARD_MOTOR)
            cardStage(phase == Phase.WAIT_CARD || phase == Phase.TAKE_CARD ? "OFFERED" : "REMOVING");
        if (cardStage.equals("REMOVING") && cardAge(0) >= AtmSoundTiming.CARD_PICKUP) { returnCard(player); cardTaken = true; }
    }

    private long intakeStarted = -1, lastCardSound = -AtmSoundTiming.CARD_MOTOR;
    private int intakeStage;
    public void startCardIntake() {
        if (level == null || level.isClientSide || intakeStarted >= 0 && level.getGameTime() - intakeStarted < AtmSoundTiming.CARD_INSERT) return;
        intakeStarted = level.getGameTime(); intakeStage = 0;
    }
    private void playMechanism(net.minecraft.sounds.SoundEvent sound, double height, double forward) {
        playMechanism(sound, height, forward, 0);
    }
    private void playMechanism(net.minecraft.sounds.SoundEvent sound, double height, double forward, double sideways) {
        var facing = getBlockState().getValue(net.austizz.ultimatebankingsystem.block.custom.ATMBlock.FACING);
        level.playSound(null, worldPosition.getX() + .5 + facing.getStepX() * forward - facing.getStepZ() * sideways,
                worldPosition.getY() + height, worldPosition.getZ() + .5 + facing.getStepZ() * forward + facing.getStepX() * sideways,
                sound, SoundSource.BLOCKS, .7F, 1);
    }
    private boolean cardMotor() {
        if (level.getGameTime() - lastCardSound < AtmSoundTiming.CARD_MOTOR - 1) return false;
        lastCardSound = level.getGameTime();
        playMechanism(ModSounds.ATM_CARD_MECHANISM.get(), 1.27, .34);
        return true;
    }
    private void tickCardIntake() {
        if (intakeStarted < 0) return;
        long age = level.getGameTime() - intakeStarted;
        if (intakeStage == 0 && age >= AtmSoundTiming.CARD_APPROACH && cardMotor()) intakeStage = 1;
        if (intakeStage == 1 && age >= AtmSoundTiming.CARD_APPROACH + AtmSoundTiming.CARD_MOTOR && cardMotor()) intakeStage = 2;
        if (age >= AtmSoundTiming.CARD_INSERT) intakeStarted = -1;
    }
    public void prepareReceipt(ServerPlayer player, boolean deposit, long cents) {
        depositReceipt = deposit;
        var data = net.austizz.ultimatebankingsystem.util.ItemStackDataCompat.getCustomData(heldCard(player));
        String number = data.getString(net.austizz.ultimatebankingsystem.payments.CreditCardService.TAG_CARD_NUMBER).replaceAll("[^0-9]", "");
        String bank = data.getString(net.austizz.ultimatebankingsystem.payments.CreditCardService.TAG_BANK_NAME).replaceAll("[\\r\\n]", " ");
        if (bank.isBlank()) bank = "UBS Banking";
        var central = net.austizz.ultimatebankingsystem.bank.handler.BankManager.getCentralBank(player.server);
        var checked = net.austizz.ultimatebankingsystem.payments.CreditCardService.validateCardStack(central, heldCard(player), null);
        var account = checked.valid() ? central.SearchForAccountByAccountId(checked.accountId()) : null;
        var transaction = account == null ? null : account.getTransactions().values().stream()
                .filter(tx -> tx.getTransactionDescription().equals(deposit ? "ATM Cash Deposit" : "ATM Cash Withdrawal")
                        && tx.getAmount().compareTo(java.math.BigDecimal.valueOf(cents, 2)) == 0)
                .max(java.util.Comparator.comparing(net.austizz.ultimatebankingsystem.account.transaction.UserTransaction::getTimestamp)).orElse(null);
        receiptDetails = String.join("\n", "ATM RECEIPT", bank.substring(0, Math.min(bank.length(), 24)),
                deposit ? "CASH DEPOSIT" : "WITHDRAWAL", "$" + java.math.BigDecimal.valueOf(cents, 2).toPlainString(),
                (transaction == null ? java.time.LocalDateTime.now() : transaction.getTimestamp()).format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")),
                "CARD **** " + (number.length() >= 4 ? number.substring(number.length() - 4) : "----"),
                "REF " + (transaction == null ? UUID.randomUUID() : transaction.getTransactionUUID()).toString().substring(0, 8).toUpperCase(java.util.Locale.ROOT), "THANK YOU");
        sync();
    }
    public void offerDepositReceipt(ServerPlayer player) { owner = player.getUUID(); collector = null; change(Phase.RECEIPT_CHOICE); }
    private void finishReceipt() {
        change(Phase.IDLE);
        if (depositReceipt && cardStage.equals("INSERTED")) { lcd.setStatus(net.austizz.ultimatebankingsystem.atm.AtmLcdState.Status.AUTHENTICATED); sync(); }
    }
    public boolean reserveCash(ServerPlayer player, int[] plan, int dollars) {
        if (!canReserve() || level == null || level.isClientSide || dollars <= 0 || plan == null
                || Arrays.stream(plan).anyMatch(n -> n < 0)
                || DollarBills.totalCashValueCentsLong(plan) != dollars * 100L) return false;
        cash = Arrays.copyOf(plan, 12);
        withdrawalDollars = dollars; prepareReceipt(player, false, dollars * 100L);
        owner = player.getUUID(); collector = null;
        change(Phase.WAIT_CARD);
        return true;
    }
    private void change(Phase next) {
        phase = next; phaseStarted = level.getGameTime();
        if (next == Phase.WAIT_CARD) { cardMotor(); if (cardOwner != null) cardStage("EJECTING"); }
        if (next == Phase.DEPOSIT_INSERTING) playMechanism(ModSounds.ATM_CARD_MECHANISM.get(), .975, .52);
        if (next == Phase.DEPOSIT_COUNTING) playMechanism(ModSounds.ATM_CASH_PREPARE.get(), .975, .52);
        if (next == Phase.WAIT_CASH) playMechanism(ModSounds.ATM_CASH_PREPARE.get(), .975, .52);
        if (next == Phase.PRINTING) playMechanism(ModSounds.ATM_RECEIPT_PRINT.get(), 20.315 / 16, .5 - 2.5 / 16, 10.8 / 16 - .5);
        if (next == Phase.DISPENSING && net.austizz.ultimatebankingsystem.atm.AtmDispenseMotion.billCount(cash) > 0)
            playMechanism(ModSounds.ATM_CASH_DISPENSE.get(), .975, .52);
        setChanged(); level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }
    public boolean dispenserAction(ServerPlayer player, String action) {
        if (level == null || level.isClientSide || !player.isAlive() || player.isSpectator()
                || player.distanceToSqr(Vec3.atCenterOf(worldPosition)) > 64) return false;
        if (viewer != null && !isViewer(player)) return false;
        if (action.equals("VIEW_PING")) { if (!isViewer(player)) return false; viewPing = level.getGameTime(); return true; }
        if (action.matches("KEY_[0-9]{1,2}")) {
            int key = Integer.parseInt(action.substring(4));
            if (!isViewer(player) || key >= 24 || !press(key, System.nanoTime())) return false;
            publicButton = key; buttonTick = level.getGameTime(); lcd.press(key);
            if (lcd.status() == net.austizz.ultimatebankingsystem.atm.AtmLcdState.Status.WITHDRAW
                    || lcd.status() == net.austizz.ultimatebankingsystem.atm.AtmLcdState.Status.AUTHENTICATED)
                net.austizz.ultimatebankingsystem.payments.AtmSessionService.cancelWithdrawal(player);
            sync(); return true;
        }
        switch (action) {
            case "DEPOSIT_ABORT" -> { if (!isViewer(player) || !deposit.active()) return false; deposit.abort(player); }
            case "PICK_DEPOSIT" -> { return deposit.pickUp(player); }
            case "RETURN_DEPOSIT" -> { return deposit.returnCash(player); }
            case "DEPOSIT_BACK" -> { return deposit.back(player); }
            case "CARD_MOTOR" -> {
                if (!isViewer(player) || phase != Phase.IDLE || !cardStage.equals("INSERTED") || !cardMotor()) return false;
                net.austizz.ultimatebankingsystem.payments.AtmSessionService.close(player);
                lcd.setStatus(net.austizz.ultimatebankingsystem.atm.AtmLcdState.Status.REMOVING); cardStage("EJECTING");
            }
            case "CARD_BEGIN" -> {
                if (!player.getUUID().equals(owner) || !(phase == Phase.WAIT_CARD || phase == Phase.TAKE_CARD)) return false;
                cardTaken = false;
                if (cardOwner != null) cardStage("REMOVING");
                change(Phase.CARD_REMOVING);
            }
            case "CARD_TAKEN" -> {
                if (!player.getUUID().equals(owner) || phase != Phase.CARD_REMOVING) return false;
                // Real-time client animations can finish before 24 server ticks when TPS is low.
                if (cardOwner == null) cardTaken = true; setChanged();
            }
            case "PICK_CASH" -> {
                if (phase != Phase.CASH_READY || !WalletBankingCashService.resolve(player).canAdd(cash)) return false;
                collector = player.getUUID();
                change(Phase.PICKING_CASH);
            }
            case "RECEIPT_YES", "RECEIPT_NO" -> {
                if (phase != Phase.RECEIPT_CHOICE || !player.getUUID().equals(owner)) return false;
                if (action.equals("RECEIPT_YES")) change(Phase.PRINTING); else finishReceipt();
            }
            case "PICK_RECEIPT" -> {
                if (phase != Phase.RECEIPT_READY) return false;
                collector = player.getUUID();
                change(Phase.PICKING_RECEIPT);
            }
            default -> { return false; }
        }
        return true;
    }
    private boolean rearChecked;

    public static void tick(Level level, BlockPos pos, BlockState state, AtmBlockEntity atm) {
        if (level.isClientSide) return;
        if (!atm.rearChecked && state.getBlock() instanceof net.austizz.ultimatebankingsystem.block.custom.InWallATMBlock wall) {
            atm.rearChecked = wall.restoreRear(level, pos, state);
        }
        atm.tickTerminal();
        atm.deposit.tick();
        atm.tickCardIntake();
        if (!atm.dispensing()) return;
        var p = atm.phase;
        double age = atm.phaseAge(0);
        var ownerPlayer = atm.owner == null ? null : level.getServer().getPlayerList().getPlayer(atm.owner);
        if ((p == Phase.WAIT_CARD || p == Phase.TAKE_CARD || p == Phase.CARD_REMOVING)
                && (atm.viewer == null || ownerPlayer == null || !ownerPlayer.isAlive() || ownerPlayer.level() != level
                || ownerPlayer.distanceToSqr(Vec3.atCenterOf(pos)) > 64)) {
            // An abandoned view returns its card immediately or keeps it in persistent recovery custody.
            atm.change(Phase.WAIT_CASH); return;
        }
        switch (p) {
            case WAIT_CARD -> { if (age >= AtmSoundTiming.CARD_MOTOR) atm.change(Phase.TAKE_CARD); }
            case CARD_REMOVING -> { if (atm.cardTaken && age >= 24) atm.change(Phase.WAIT_CASH); }
            case WAIT_CASH -> { if (age >= AtmSoundTiming.CASH_PREPARE) atm.change(Phase.OPENING); }
            case OPENING -> { if (age >= AtmSoundTiming.SHUTTER_OPEN) atm.change(Phase.DISPENSING); }
            case DISPENSING -> {
                int bills = net.austizz.ultimatebankingsystem.atm.AtmDispenseMotion.billCount(atm.cash);
                if (age >= AtmSoundTiming.cashFeedTicks(bills)) atm.change(Phase.CASH_READY);
                else if (age > 0 && (long) age % AtmSoundTiming.CASH_BILL_PERIOD == 0)
                    atm.playMechanism(ModSounds.ATM_CASH_DISPENSE.get(), .975, .52);
            }
            case PICKING_CASH -> {
                if (age < 26) return;
                var player = level.getServer().getPlayerList().getPlayer(atm.collector);
                if (player == null || !player.isAlive() || player.level() != level || player.distanceToSqr(Vec3.atCenterOf(pos)) > 64) {
                    atm.change(Phase.CASH_READY); return;
                }
                var storage = WalletBankingCashService.resolve(player);
                if (!storage.canAdd(atm.cash) || !storage.add(player, atm.cash)) {
                    atm.change(Phase.CASH_READY); return;
                }
                atm.cash = new int[12]; atm.owner = player.getUUID();
                atm.change(Phase.CLOSING);
            }
            case CLOSING -> { if (age >= 25) atm.change(Phase.RECEIPT_CHOICE); }
            case RECEIPT_CHOICE -> { if (net.austizz.ultimatebankingsystem.atm.AtmDispenseMotion.receiptTimedOut(age)
                    || ownerPlayer == null || ownerPlayer.level() != level || ownerPlayer.distanceToSqr(Vec3.atCenterOf(pos)) > 64) atm.finishReceipt(); }
            case PRINTING -> { if (age >= 40) atm.change(Phase.RECEIPT_READY); }
            case PICKING_RECEIPT -> {
                var player = level.getServer().getPlayerList().getPlayer(atm.collector);
                if (player == null || !player.isAlive() || player.level() != level || player.distanceToSqr(Vec3.atCenterOf(pos)) > 64) {
                    atm.change(Phase.RECEIPT_READY); return;
                }
                if (age == AtmSoundTiming.RECEIPT_TEAR)
                    atm.playMechanism(ModSounds.ATM_RECEIPT_TEAR.get(), 20.315 / 16, .5 - 2.5 / 16, 10.8 / 16 - .5);
                if (age < 22) return;
                var receipt = new ItemStack(Items.PAPER);
                receipt.set(DataComponents.CUSTOM_NAME, Component.literal(atm.depositReceipt ? "ATM deposit receipt" : "ATM withdrawal receipt"));
                receipt.set(DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(atm.receiptDetails.lines().map(Component::literal).map(c -> (Component)c).toList()));
                if (!player.getInventory().add(receipt)) player.drop(receipt, false);
                atm.finishReceipt();
            }
            default -> { }
        }
    }
    public void dropPendingCash() {
        if (level == null || level.isClientSide) return;
        if (deposit.active()) deposit.abort(deposit.owner() == null ? null : level.getServer().getPlayerList().getPlayer(deposit.owner()));
        var viewerPlayer = viewer == null ? null : level.getServer().getPlayerList().getPlayer(viewer);
        if (viewerPlayer != null) net.austizz.ultimatebankingsystem.payments.AtmTerminalService.close(viewerPlayer);
        else if (cardOwner != null) returnCard(level.getServer().getPlayerList().getPlayer(cardOwner));
        var pending = cash; cash = new int[12]; setChanged();
        for (int i = 0; i < pending.length; i++) {
            int count = pending[i];
            while (count > 0) {
                int stack = Math.min(64, count); count -= stack;
                Containers.dropItemStack(level, worldPosition.getX() + .5, worldPosition.getY() + .9, worldPosition.getZ() + .5,
                        new ItemStack(DollarBills.getCashItemForDenominationCents(DollarBills.CASH_DENOMINATIONS_CENTS_DESC[i]), stack));
            }
        }
    }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        deposit.save(tag);
        for(int cell=0;cell<6;cell++) if(wallMaterials[cell]!=null)
            tag.put("atm_wall_"+cell,net.minecraft.nbt.NbtUtils.writeBlockState(wallMaterials[cell]));
        tag.putIntArray("atm_cash", cash); tag.putString("atm_phase", phase.name()); tag.putLong("atm_started", phaseStarted);
        tag.putBoolean("atm_session_expired", sessionExpired); tag.putBoolean("atm_deposit_receipt", depositReceipt); tag.putString("atm_receipt_details", receiptDetails);
        tag.putLong("atm_dollars", withdrawalDollars); tag.putBoolean("atm_card_taken", cardTaken);
        if (cardOwner != null) tag.putUUID("atm_card_owner", cardOwner);
        tag.putString("atm_card_stage", cardStage); tag.putLong("atm_card_started", cardStarted);
        tag.putBoolean("atm_card_offhand", cardHand == net.minecraft.world.InteractionHand.OFF_HAND);
        if (!visualCard.isEmpty()) tag.put("atm_card_visual", visualCard.save(registries));
        if (owner != null) tag.putUUID("atm_owner", owner);
        if (collector != null) tag.putUUID("atm_collector", collector);
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        deposit.load(tag);
        for (int cell = 0; cell < wallMaterials.length; cell++) {
            String key = "atm_wall_" + cell;
            BlockState material = tag.contains(key) ? net.minecraft.nbt.NbtUtils.readBlockState(
                    registries.lookupOrThrow(net.minecraft.core.registries.Registries.BLOCK), tag.getCompound(key)) : null;
            if (wallMaterials[cell] == material) continue;
            wallMaterials[cell] = material;
            if (level != null && level.isClientSide && getBlockState().getBlock() instanceof net.austizz.ultimatebankingsystem.block.custom.InWallATMBlock) {
                var facing = getBlockState().getValue(net.austizz.ultimatebankingsystem.block.custom.ATMBlock.FACING);
                var pos = net.austizz.ultimatebankingsystem.block.custom.InWallATMBlock.partPos(worldPosition, facing, cell);
                var state = level.getBlockState(pos);
                level.sendBlockUpdated(pos, state, state, 3);
            }
        }
        cash = Arrays.copyOf(tag.getIntArray("atm_cash"), 12);
        for (int i = 0; i < cash.length; i++) cash[i] = Math.clamp(cash[i], 0, 1_000_000);
        try { phase = Phase.valueOf(tag.getString("atm_phase")); }
        catch (IllegalArgumentException ex) { phase = Arrays.stream(cash).sum() > 0 ? Phase.CASH_READY : Phase.IDLE; }
        sessionExpired = tag.getBoolean("atm_session_expired"); depositReceipt = tag.getBoolean("atm_deposit_receipt"); receiptDetails = tag.getString("atm_receipt_details");
        phaseStarted = tag.getLong("atm_started"); withdrawalDollars = tag.getLong("atm_dollars");
        cardTaken = tag.getBoolean("atm_card_taken");
        cardOwner = tag.hasUUID("atm_card_owner") ? tag.getUUID("atm_card_owner") : null;
        cardStage = tag.contains("atm_card_stage") ? tag.getString("atm_card_stage") : "EMPTY";
        cardStarted = tag.getLong("atm_card_started");
        cardHand = tag.getBoolean("atm_card_offhand") ? net.minecraft.world.InteractionHand.OFF_HAND : net.minecraft.world.InteractionHand.MAIN_HAND;
        visualCard = ItemStack.parseOptional(registries, tag.getCompound("atm_card_visual"));
        viewer = tag.hasUUID("atm_viewer") ? tag.getUUID("atm_viewer") : null;
        publicButton = tag.contains("atm_button") ? tag.getInt("atm_button") : -1; buttonTick = tag.getLong("atm_button_tick");
        sharedLcd.readPublic(net.austizz.ultimatebankingsystem.atm.AtmLcdSync.load(tag.getCompound("atm_lcd")));
        owner = tag.hasUUID("atm_owner") ? tag.getUUID("atm_owner") : null;
        collector = tag.hasUUID("atm_collector") ? tag.getUUID("atm_collector") : null;
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        var tag = saveWithoutMetadata(registries);
        if (viewer != null) tag.putUUID("atm_viewer", viewer);
        tag.put("atm_lcd", net.austizz.ultimatebankingsystem.atm.AtmLcdSync.save(lcd.publicSnapshot(System.nanoTime())));
        tag.putInt("atm_button", publicButton); tag.putLong("atm_button_tick", buttonTick);
        return tag;
    }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        // Existing ATM placements predate the button renderer's block entity.
        if (event.getChunk() instanceof LevelChunk chunk) {
            chunk.findBlocks(state -> state.is(ModBlocks.ATM_MACHINE.get()),
                    (pos, state) -> chunk.getBlockEntity(pos, LevelChunk.EntityCreationType.IMMEDIATE));
        }
    }
}
