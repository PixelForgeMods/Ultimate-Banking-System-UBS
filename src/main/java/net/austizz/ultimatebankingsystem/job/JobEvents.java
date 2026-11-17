package net.austizz.ultimatebankingsystem.job;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.ItemFishedEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.util.List;

/** Event bridge for job objectives that are completed through normal world actions. */
@EventBusSubscriber(modid = UltimateBankingSystem.MODID)
public final class JobEvents {
    private JobEvents() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.isCanceled() || !(event.getPlayer() instanceof ServerPlayer player)) {
            return;
        }
        JobService.recordBlockBreak(player, event.getState());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onFishing(ItemFishedEvent event) {
        if (event.isCanceled() || !(event.getEntity() instanceof ServerPlayer player)) return;
        JobService.recordFishingCatch(player, event.getDrops());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onMobDrops(LivingDropsEvent event) {
        if (event.isCanceled()) return;
        ServerPlayer player = playerFromDamage(event.getSource());
        if (player == null) return;
        List<ItemStack> drops = event.getDrops().stream().map(item -> item.getItem().copy()).toList();
        JobService.recordFarmerAnimalDrops(player, event.getEntity(), drops);
        JobService.recordHunterDrops(player, drops);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onCrafted(PlayerEvent.ItemCraftedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        JobService.recordCrafted(player, event.getCrafting());
    }

    private static ServerPlayer playerFromDamage(DamageSource source) {
        if (source == null) return null;
        ServerPlayer causing = asServerPlayer(source.getEntity());
        if (causing != null) return causing;
        Entity direct = source.getDirectEntity();
        if (direct instanceof Projectile projectile) {
            ServerPlayer owner = asServerPlayer(projectile.getOwner());
            if (owner != null) return owner;
        }
        return asServerPlayer(direct);
    }

    private static ServerPlayer asServerPlayer(Entity entity) {
        return entity instanceof ServerPlayer player ? player : null;
    }
}
