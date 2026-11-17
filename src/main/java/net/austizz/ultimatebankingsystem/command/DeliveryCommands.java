package net.austizz.ultimatebankingsystem.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.austizz.ultimatebankingsystem.job.JobService;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Player-facing Courier route commands. The short /job alias is retained for muscle memory. */
@EventBusSubscriber(modid = UltimateBankingSystem.MODID)
public final class DeliveryCommands {
    private DeliveryCommands() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(cancelRoot("delivery"));
        event.getDispatcher().register(cancelRoot("job"));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<net.minecraft.commands.CommandSourceStack>
    cancelRoot(String root) {
        return Commands.literal(root)
                .then(Commands.literal("cancel")
                        .then(Commands.argument("delivery_id", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    return SharedSuggestionProvider.suggest(
                                            JobService.activeCourierDeliveryIds(player), builder);
                                })
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    String result = JobService.cancelCourierOrder(player,
                                            StringArgumentType.getString(context, "delivery_id"));
                                    player.sendSystemMessage(Component.literal(result).withStyle(
                                            result.startsWith("Delivery ") ? ChatFormatting.GREEN : ChatFormatting.RED));
                                    return result.startsWith("Delivery ") ? 1 : 0;
                                })));
    }
}
