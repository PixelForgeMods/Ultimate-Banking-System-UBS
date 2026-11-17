package net.austizz.ultimatebankingsystem.job;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.austizz.ultimatebankingsystem.entity.custom.JobNpcEntity;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** Bridges player lifecycle events to persisted job route cleanup. */
@EventBusSubscriber(modid = UltimateBankingSystem.MODID)
public final class JobLifecycleEvents {
    private JobLifecycleEvents() {
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            JobService.onPlayerLogin(player);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            JobService.onPlayerLogout(player);
        }
    }

    @SubscribeEvent
    public static void onEntityLeave(EntityLeaveLevelEvent event) {
        if (event.getEntity() instanceof JobNpcEntity npc
                && npc.isRemoved() && npc.getRemovalReason() != null) {
            JobService.onCourierNpcRemoved(npc);
        }
    }
}
