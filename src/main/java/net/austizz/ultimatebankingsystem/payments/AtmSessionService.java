package net.austizz.ultimatebankingsystem.payments;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.austizz.ultimatebankingsystem.bank.centralbank.CentralBank;
import net.austizz.ultimatebankingsystem.bank.handler.BankManager;
import net.austizz.ultimatebankingsystem.network.*;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import java.util.HashMap;
import java.util.UUID;
import java.util.function.Consumer;
import java.time.format.DateTimeFormatter;

@EventBusSubscriber(modid = UltimateBankingSystem.MODID)
public final class AtmSessionService {
    private static final HashMap<UUID, Session> SESSIONS = new HashMap<>();
    private static final class Session {
        final UUID token = UUID.randomUUID();
        final UUID card;
        final UUID account;
        final BlockPos atm;
        final ResourceKey<Level> dimension;
        final long expires = System.currentTimeMillis() + 300_000;
        int sequence;
        int approvedWithdrawal;
        Session(Player player, BlockPos atm, UUID card, UUID account) { this.card = card; this.account = account; this.atm = atm.immutable(); dimension = player.level().dimension(); }
    }
    private AtmSessionService() {}

    static UUID open(Player player, BlockPos atm, UUID card, UUID account) {
        var session = new Session(player, atm, card, account);
        SESSIONS.put(player.getUUID(), session);
        return session.token;
    }

    public static boolean authorizes(ServerPlayer player, UUID account,
            net.austizz.ultimatebankingsystem.block.entity.custom.AtmBlockEntity atm) {
        var session = SESSIONS.get(player.getUUID());
        if (session == null || atm == null || session.expires < System.currentTimeMillis()
                || !session.account.equals(account) || !session.atm.equals(atm.getBlockPos())
                || !session.dimension.equals(player.level().dimension()) || !atm.isViewer(player)
                || player.level().getBlockEntity(session.atm) != atm) return false;
        var bank = BankManager.getCentralBank(player.server);
        var card = AtmCardInsertionService.validate(player, session.atm, bank, session.card);
        if (!card.accepted()) return false;
        var checked = CreditCardService.validateCardStack(bank, card.card(), null);
        return checked.valid() && account.equals(checked.accountId());
    }
    public static boolean expired(Player player) {
        return expired(player, System.currentTimeMillis());
    }
    static boolean expired(Player player, long now) {
        var session = SESSIONS.get(player.getUUID());
        return session != null && session.expires <= now;
    }
    public static void close(Player player) { SESSIONS.remove(player.getUUID()); }
    public static void cancelWithdrawal(Player player) {
        var session = SESSIONS.get(player.getUUID());
        if (session != null) session.approvedWithdrawal = 0;
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) { close(event.getEntity()); }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { SESSIONS.clear(); }

    public static void handle(AtmActionRequestPayload request, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                var session = SESSIONS.get(player.getUUID());
                execute(player, request, BankManager.getCentralBank(player.server), response -> {
                    if (session != null && player.level().getBlockEntity(session.atm) instanceof net.austizz.ultimatebankingsystem.block.entity.custom.AtmBlockEntity atm && atm.isViewer(player)) {
                        atm.lcd().actionResult(response.success(), response.balance(), response.message());
                        if (!response.success() && atm.deposit().active()) atm.lcd().showNotice(response.message(), System.nanoTime());
                        atm.sync();
                    }
                    if (session != null && player.level().getBlockEntity(session.atm) instanceof net.austizz.ultimatebankingsystem.block.entity.custom.AtmBlockEntity terminal && terminal.deposit().active()) sendDepositStock(player, session.atm);
                    PacketDistributor.sendToPlayer(player, response);
                });
            }
        });
    }

    private static String applyWithdrawalSettings(ServerPlayer player, CentralBank bank, Session session,
            net.austizz.ultimatebankingsystem.account.AccountHolder account, AtmActionRequestPayload request, long gameTime) {
        int mode = request.billOption() == -1 ? 2 : request.billOption();
        if (mode < 0 || mode > 3) return "Invalid limit selection.";
        var policy = ModPayloads.buildAtmWithdrawalPolicyInput(bank, bank.getBank(account.getBankId()), account, java.math.BigDecimal.ZERO, gameTime);
        var maxSingle = account.getConfiguredWithdrawalLimit().min(policy.bankSingleLimit());
        var maxDaily = account.getConfiguredDailyWithdrawalLimit().min(policy.bankPlayerDailyLimit()).min(policy.bankDailyLimit());
        java.math.BigDecimal value = java.math.BigDecimal.ZERO;
        if (mode != 3) {
            if (!request.amount().matches("[1-9][0-9]{0,6}")) return "Enter a positive whole dollar limit (up to 7 digits).";
            value = new java.math.BigDecimal(request.amount());
            if (value.compareTo(mode == 1 ? maxDaily : maxSingle) > 0) return "This limit exceeds the bank/server maximum.";
        }
        if (mode == 2 && request.durationMinutes() != 15 && request.durationMinutes() != 60 && request.durationMinutes() != 1440 && request.durationMinutes() != 20)
            return "Choose a valid temporary duration.";
        boolean raising = switch (mode) {
            case 0 -> value.compareTo(account.getPersonalWithdrawalLimit().min(maxSingle)) > 0
                    || value.compareTo(account.getEffectiveWithdrawalLimit(gameTime).min(maxSingle)) > 0;
            case 1 -> value.compareTo(account.getEffectiveDailyWithdrawalLimit().min(maxDaily)) > 0;
            case 2 -> value.compareTo(account.getEffectiveWithdrawalLimit(gameTime).min(maxSingle)) > 0
                    || value.compareTo(account.getPersonalWithdrawalLimit().min(maxSingle)) > 0;
            default -> maxSingle.compareTo(account.getEffectiveWithdrawalLimit(gameTime).min(maxSingle)) > 0
                    || maxSingle.compareTo(account.getPersonalWithdrawalLimit().min(maxSingle)) > 0
                    || maxDaily.compareTo(account.getEffectiveDailyWithdrawalLimit().min(maxDaily)) > 0;
        };
        if (raising) {
            if (request.pin().isEmpty()) return "PIN_REQUIRED";
            var verified = AtmPinService.verify(player, session.atm, session.card, request.pin(), bank);
            if (!verified.accepted()) return verified.message();
        }
        switch (mode) {
            case 0 -> { if (!account.setPersonalWithdrawalLimit(value)) return "Invalid personal withdrawal limit."; }
            case 1 -> { if (!account.setPersonalDailyWithdrawalLimit(value)) return "Invalid personal daily limit."; }
            case 2 -> { if (!account.setTemporaryWithdrawalLimitUntil(value, gameTime, System.currentTimeMillis() + request.durationMinutes() * 60_000L)) return "Invalid temporary limit."; }
            default -> account.resetPersonalWithdrawalLimits();
        }
        return "";
    }

    public static void sendDepositStock(ServerPlayer player, BlockPos pos) {
        var counts = WalletBankingCashService.depositAvailableCounts(player);
        PacketDistributor.sendToPlayer(player, new AtmDepositStockPayload(pos, java.util.stream.IntStream.range(0,7).map(i -> Math.max(0, counts[i])).boxed().toList()));
    }
    public static void handleFeed(AtmDepositFeedPayload request, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            var session = SESSIONS.get(player.getUUID());
            executeFeed(player, request, BankManager.getCentralBank(player.server), response -> {
                if (session != null) sendDepositStock(player, session.atm);
                PacketDistributor.sendToPlayer(player, response);
            });
        });
    }
    public static void executeFeed(ServerPlayer player, AtmDepositFeedPayload request, CentralBank bank, Consumer<AtmActionResultPayload> reply) {
        execute(player, new AtmActionRequestPayload(request.token(), request.requestId(), request.sequence(), "FEED_DEPOSIT", ""),
                bank, reply, request.bills().stream().mapToInt(Integer::intValue).toArray());
    }
    public static void execute(ServerPlayer player, AtmActionRequestPayload request, CentralBank bank, Consumer<AtmActionResultPayload> reply) {
        execute(player, request, bank, reply, null);
    }
    private static void execute(ServerPlayer player, AtmActionRequestPayload request, CentralBank bank, Consumer<AtmActionResultPayload> reply, int[] depositPlan) {
        var session = SESSIONS.get(player.getUUID());
        if (session == null || !session.token.equals(request.token())) {
            reply.accept(new AtmActionResultPayload(request.requestId(), false, "", "Please reinsert your card and sign in."));
            return;
        }
        if (request.action().equals("END")) { close(player); return; }
        if (session.expires < System.currentTimeMillis() || !session.dimension.equals(player.level().dimension())) {
            if (player.level().getBlockEntity(session.atm) instanceof net.austizz.ultimatebankingsystem.block.entity.custom.AtmBlockEntity machine) machine.expireSession(player);
            else close(player);
            reply.accept(new AtmActionResultPayload(request.requestId(), false, "", "Session expired. Your card is being returned."));
            return;
        }
        if (request.sequence() != session.sequence + 1) {
            reply.accept(new AtmActionResultPayload(request.requestId(), false, "", "This request has already been processed or is out of order."));
            return;
        }
        session.sequence = request.sequence();
        var card = AtmCardInsertionService.validate(player, session.atm, bank, session.card);
        if (!card.accepted()) {
            close(player);
            reply.accept(new AtmActionResultPayload(request.requestId(), false, "", card.message()));
            return;
        }
        var validated = CreditCardService.validateCardStack(bank, card.card(), null);
        if (!validated.valid()) { close(player); reply.accept(new AtmActionResultPayload(request.requestId(), false, "", validated.message())); return; }
        var account = bank.SearchForAccountByAccountId(validated.accountId());
        if (!session.account.equals(validated.accountId())) {
            close(player);
            reply.accept(new AtmActionResultPayload(request.requestId(), false, "", "Card account changed. Please sign in again."));
            return;
        }
        switch (request.action()) {
            case "OPEN_DEPOSIT", "FEED_DEPOSIT", "DEPOSIT_COUNT", "DEPOSIT_CONFIRM", "DEPOSIT_RETURN" -> {
                var terminal = (net.austizz.ultimatebankingsystem.block.entity.custom.AtmBlockEntity) player.level().getBlockEntity(session.atm);
                boolean accepted = switch (request.action()) {
                    case "OPEN_DEPOSIT" -> !account.isFrozen() && terminal.deposit().open(player, account.getAccountUUID());
                    case "FEED_DEPOSIT" -> !account.isFrozen() && depositPlan != null && terminal.deposit().feed(player, depositPlan);
                    case "DEPOSIT_COUNT" -> terminal.deposit().count(player);
                    case "DEPOSIT_CONFIRM" -> terminal.deposit().confirm(player, account);
                    case "DEPOSIT_RETURN" -> terminal.deposit().returnCash(player);
                    default -> false;
                };
                reply.accept(new AtmActionResultPayload(request.requestId(), accepted, account.getBalance().toPlainString(),
                        accepted ? request.action().equals("DEPOSIT_CONFIRM") ? "Deposit complete." : "" : "Deposit could not continue. Check the cash, account and feeder, or return your bills."));
            }
            case "ACCOUNT_INFO", "SET_PRIMARY", "LIMITS", "SET_LIMIT" -> {
                boolean info = request.action().equals("ACCOUNT_INFO") || request.action().equals("SET_PRIMARY");
                boolean owner = account.getPlayerUUID().equals(player.getUUID());
                if ((request.action().equals("SET_PRIMARY") || request.action().equals("SET_LIMIT")) && !owner) {
                    reply.accept(new AtmActionResultPayload(request.requestId(), false, "", "Only the account owner can change primary-account and limit settings.")); return;
                }
                if (request.action().equals("SET_PRIMARY")) bank.setPrimaryAccountForPlayer(player.getUUID(), account.getAccountUUID(), !account.isPrimaryAccount());
                long gameTime = player.server.overworld().getGameTime();
                if (request.action().equals("SET_LIMIT")) {
                    String error = applyWithdrawalSettings(player, bank, session, account, request, gameTime);
                    if (!error.isEmpty()) { reply.accept(new AtmActionResultPayload(request.requestId(), false, "", error)); return; }
                }
                var policy = ModPayloads.buildAtmWithdrawalPolicyInput(bank, bank.getBank(account.getBankId()), account, java.math.BigDecimal.ZERO, gameTime);
                var limits = net.austizz.ultimatebankingsystem.account.AtmWithdrawalPolicy.evaluate(policy);
                var maxSingle = account.getConfiguredWithdrawalLimit().min(policy.bankSingleLimit());
                var maxDaily = account.getConfiguredDailyWithdrawalLimit().min(policy.bankPlayerDailyLimit()).min(policy.bankDailyLimit());
                var issuer = bank.getBank(account.getBankId());
                var date = DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm");
                String text = info ? String.join("\n", "Account: " + account.getAccountUUID(), "Bank: " + (issuer == null ? bank.getBankName() : issuer.getBankName()),
                        "Type: " + account.getAccountType(), "Created: " + account.getDateOfCreation().format(date), "Balance: $" + account.getBalance().toPlainString(),
                        "Primary account: " + (account.isPrimaryAccount() ? "Yes" : "No"))
                        : String.join("\n", "Bank maximum / withdrawal: $" + maxSingle.toPlainString(),
                        "Personal / withdrawal: $" + account.getPersonalWithdrawalLimit().min(maxSingle).toPlainString() + " (active: $" + account.getEffectiveWithdrawalLimit(gameTime).min(maxSingle).toPlainString() + ")",
                        "Bank daily maximum: $" + maxDaily.toPlainString(),
                        "Personal daily limit: $" + account.getEffectiveDailyWithdrawalLimit().min(maxDaily).toPlainString(),
                        "Temporary limit: " + (account.getTemporaryWithdrawalLimitIfActive(gameTime) == null ? "Not active" : "$" + account.getTemporaryWithdrawalLimitIfActive(gameTime).min(maxSingle).toPlainString()),
                        "Temporary ends: " + (account.getTemporaryWithdrawalLimitExpiresAtEpochMillis(gameTime) < 0 ? "Not active" : java.time.Instant.ofEpochMilli(account.getTemporaryWithdrawalLimitExpiresAtEpochMillis(gameTime)).atZone(java.time.ZoneId.systemDefault()).format(date)),
                        "Withdrawn today: $" + account.getDailyWithdrawnAmount().toPlainString(),
                        "Daily remaining: $" + limits.accountDailyRemaining().min(limits.bankPlayerDailyRemaining()).min(limits.bankDailyRemaining()).toPlainString(),
                        "Daily reset: " + java.time.Instant.ofEpochMilli(account.getDailyWithdrawalResetEpochMillis()).atZone(java.time.ZoneId.systemDefault()).format(date));
                reply.accept(new AtmActionResultPayload(request.requestId(), true, account.getBalance().toPlainString(), text));
            }
            case "PAY_LIST", "PAY_PLAYERS", "PAY_SEND", "PAY_ACCEPT", "PAY_DECLINE" -> {
                if (!account.getPlayerUUID().equals(player.getUUID())) {
                    reply.accept(new AtmActionResultPayload(request.requestId(), false, "", "Use your own card to manage your pay requests.")); return;
                }
                var terminal = (net.austizz.ultimatebankingsystem.block.entity.custom.AtmBlockEntity) player.level().getBlockEntity(session.atm);
                if (!terminal.canReserve()) { reply.accept(new AtmActionResultPayload(request.requestId(),false,"","Finish the current ATM operation first.")); return; }
                if (request.action().equals("PAY_LIST") || request.action().equals("PAY_PLAYERS")) {
                    boolean players=request.action().equals("PAY_PLAYERS");
                    java.util.List<net.austizz.ultimatebankingsystem.atm.AtmPayRequestPage.Entry> rows;
                    if(players) rows=player.server.getPlayerList().getPlayers().stream().filter(p->!p.getUUID().equals(player.getUUID()))
                            .sorted(java.util.Comparator.comparing(p->p.getGameProfile().getName(),String.CASE_INSENSITIVE_ORDER))
                            .map(p->new net.austizz.ultimatebankingsystem.atm.AtmPayRequestPage.Entry(p.getUUID(),p.getGameProfile().getName(),"","")).toList();
                    else rows=net.austizz.ultimatebankingsystem.payrequest.PayRequestManager.getPendingForPayer(player.getUUID()).stream()
                            .map(p->{
                                String name=ModPayloads.resolveServerPlayerName(player.server,p.getRequesterUUID());
                                return new net.austizz.ultimatebankingsystem.atm.AtmPayRequestPage.Entry(p.getRequestId(),name,p.getAmount().toPlainString(),java.time.Instant.ofEpochMilli(p.getCreatedAtMillis()).atZone(java.time.ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MM/dd HH:mm")));}).toList();
                    int size=players?3:1, pages=Math.max(1,(rows.size()+size-1)/size), page=Math.clamp(request.billOption(),0,pages-1), from=page*size;
                    String display=new net.austizz.ultimatebankingsystem.atm.AtmPayRequestPage(page,pages,rows.subList(from,Math.min(from+size,rows.size()))).encode();
                    reply.accept(new AtmActionResultPayload(request.requestId(),true,account.getBalance().toPlainString(),display));
                } else if(request.action().equals("PAY_SEND")) {
                    if(!request.amount().matches("[0-9]{1,7}(\\.[0-9]{1,2})?") || new java.math.BigDecimal(request.amount()).signum()<=0) {
                        reply.accept(new AtmActionResultPayload(request.requestId(),false,"","Enter a positive amount with at most two decimal places.")); return;
                    }
                    var target=player.server.getPlayerList().getPlayer(request.target());
                    if(target==null) { reply.accept(new AtmActionResultPayload(request.requestId(),false,"","That player is no longer online. Choose again.")); return; }
                    ModPayloads.executePayRequestCreate(player,new PayRequestCreatePayload(account.getAccountUUID(),target.getGameProfile().getName(),request.amount(),account.getAccountUUID().toString()),
                            result->reply.accept(new AtmActionResultPayload(request.requestId(),result.success(),account.getBalance().toPlainString(),result.message())));
                } else ModPayloads.executePayRequestAction(player,new PayRequestActionPayload(account.getAccountUUID(),request.target(),request.action().equals("PAY_DECLINE")?"decline":"accept_account",account.getAccountUUID().toString()),
                        result->reply.accept(new AtmActionResultPayload(request.requestId(),result.success(),account.getBalance().toPlainString(),result.message())));
            }
            case "BALANCE" -> reply.accept(new AtmActionResultPayload(request.requestId(), true, account.getBalance().toPlainString(), "Current account balance"));
            case "HISTORY" -> {
                String history = account.getTransactions().values().stream()
                        .sorted(java.util.Comparator.comparing(net.austizz.ultimatebankingsystem.account.transaction.UserTransaction::getTimestamp).reversed())
                        .limit(4).map(tx -> tx.getTimestamp().format(DateTimeFormatter.ofPattern("MM/dd")) + "  "
                                + (account.getAccountUUID().equals(tx.getReceiverUUID()) ? "+$" : "-$") + tx.getAmount().toPlainString()
                                + "  " + tx.getTransactionDescription().replace('\n', ' ').replace('\r', ' ').substring(0, Math.min(45, tx.getTransactionDescription().length())))
                        .collect(java.util.stream.Collectors.joining("\n"));
                reply.accept(new AtmActionResultPayload(request.requestId(), true, account.getBalance().toPlainString(), history.isEmpty() ? "No recent transactions." : history));
            }
            case "CHECK_WITHDRAW", "WITHDRAW", "DEPOSIT" -> {
                if (!request.amount().matches("[1-9][0-9]{0,6}")) {
                    reply.accept(new AtmActionResultPayload(request.requestId(), false, account.getBalance().toPlainString(), "Enter a positive whole dollar amount (up to 7 digits)."));
                    return;
                }
                if (!request.action().equals("DEPOSIT")) {
                    if (!(player.level().getBlockEntity(session.atm) instanceof net.austizz.ultimatebankingsystem.block.entity.custom.AtmBlockEntity dispenser) || !dispenser.canReserve()) {
                        reply.accept(new AtmActionResultPayload(request.requestId(), false, account.getBalance().toPlainString(), "This ATM already has cash or a receipt waiting. Please collect it first."));
                        return;
                    }
                    int dollars = Integer.parseInt(request.amount());
                    if (request.action().equals("CHECK_WITHDRAW")) {
                        session.approvedWithdrawal = 0;
                        ModPayloads.executeAtmWithdrawal(player, new WithdrawRequestPayload(account.getAccountUUID(), request.amount()), r -> {
                            if (r.success()) session.approvedWithdrawal = dollars;
                            reply.accept(new AtmActionResultPayload(request.requestId(), r.success(), r.newBalance(),
                                    r.success() ? "Choose whether you would like to select your bills." : r.errorMessage()));
                        }, dispenser, null, true);
                        return;
                    }
                    var options = net.austizz.ultimatebankingsystem.atm.AtmBillOptions.forAmount(dollars);
                    if (session.approvedWithdrawal != dollars || request.billOption() < -1 || request.billOption() >= options.size()) {
                        reply.accept(new AtmActionResultPayload(request.requestId(), false, account.getBalance().toPlainString(), "Please confirm the amount and choose a valid bill combination."));
                        return;
                    }
                    int[] plan = request.billOption() == -1 ? null : options.get(request.billOption()).counts();
                    session.approvedWithdrawal = 0;
                    ModPayloads.executeAtmWithdrawal(player, new WithdrawRequestPayload(account.getAccountUUID(), request.amount()),
                            r -> reply.accept(new AtmActionResultPayload(request.requestId(), r.success(), r.newBalance(), r.success() ? "Preparing your cash. Please wait." : r.errorMessage())), dispenser, plan, false);
                } else {
                    ModPayloads.executeAtmDeposit(player, new DepositRequestPayload(account.getAccountUUID(), request.amount()),
                            r -> reply.accept(new AtmActionResultPayload(request.requestId(), r.success(), r.newBalance(), r.success() ? "Cash deposited from your wallet or inventory." : r.errorMessage())),
                            (net.austizz.ultimatebankingsystem.block.entity.custom.AtmBlockEntity) player.level().getBlockEntity(session.atm));
                }
            }
            default -> reply.accept(new AtmActionResultPayload(request.requestId(), false, "", "Unknown ATM action."));
        }
    }
}
