package net.austizz.ultimatebankingsystem.payments;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.austizz.ultimatebankingsystem.block.entity.custom.AtmBlockEntity;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import java.util.HashMap;
import java.util.UUID;

@EventBusSubscriber(modid = UltimateBankingSystem.MODID)
public final class AtmTerminalService {
    private static final HashMap<UUID, GlobalPos> VIEWS = new HashMap<>();
    private AtmTerminalService() {}
    public static boolean claim(ServerPlayer player, AtmBlockEntity atm) {
        var pos = GlobalPos.of(player.level().dimension(), atm.getBlockPos());
        if (VIEWS.containsKey(player.getUUID()) && !VIEWS.get(player.getUUID()).equals(pos)) return false;
        if (!atm.claimView(player)) return false;
        VIEWS.put(player.getUUID(), pos); return true;
    }
    public static void close(ServerPlayer player) {
        var pos = VIEWS.remove(player.getUUID());
        if (pos != null) {
            var level = player.server.getLevel(pos.dimension());
            if (level != null && level.hasChunkAt(pos.pos()) && level.getBlockEntity(pos.pos()) instanceof AtmBlockEntity atm)
                atm.releaseView(player);
        }
        AtmDepositCustody.get(player.server).refund(player);
        AtmCardCustody.get(player.server).giveBack(player);
        AtmSessionService.close(player);
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) close(player);
    }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) { VIEWS.remove(player.getUUID()); AtmDepositCustody.get(player.server).refund(player); AtmCardCustody.get(player.server).giveBack(player); }
    }
    @SubscribeEvent public static void death(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) close(player);
    }
    @SubscribeEvent public static void stop(ServerStoppingEvent event) {
        for (var player : event.getServer().getPlayerList().getPlayers()) close(player);
        VIEWS.clear();
    }
}
