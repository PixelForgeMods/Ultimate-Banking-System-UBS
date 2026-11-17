package net.austizz.ultimatebankingsystem.entity;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.austizz.ultimatebankingsystem.entity.custom.JobNpcEntity;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;

/** Captures the deliberate operator attack gesture before invulnerability consumes the hit. */
@EventBusSubscriber(modid = UltimateBankingSystem.MODID)
public final class JobNpcInteractionEvents {
    private JobNpcInteractionEvents() {
    }

    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !(event.getTarget() instanceof JobNpcEntity npc)
                || !player.isShiftKeyDown()) {
            return;
        }
        event.setCanceled(true);
        npc.handleOperatorRemovalClick(player);
    }
}
