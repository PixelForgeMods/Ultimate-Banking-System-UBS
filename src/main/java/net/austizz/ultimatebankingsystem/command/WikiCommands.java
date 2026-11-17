package net.austizz.ultimatebankingsystem.command;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.austizz.ultimatebankingsystem.network.WikiOpenPayload;
import net.minecraft.commands.Commands;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = UltimateBankingSystem.MODID)
public final class WikiCommands {
    private WikiCommands() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("wiki").executes(context -> {
            PacketDistributor.sendToPlayer(context.getSource().getPlayerOrException(), new WikiOpenPayload());
            return 1;
        }));
        event.getDispatcher().register(Commands.literal("ubs").then(Commands.literal("wiki")
                .executes(context -> {
                    PacketDistributor.sendToPlayer(context.getSource().getPlayerOrException(), new WikiOpenPayload());
                    return 1;
                })));
    }
}
