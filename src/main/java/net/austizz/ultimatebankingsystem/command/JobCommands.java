package net.austizz.ultimatebankingsystem.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.austizz.ultimatebankingsystem.entity.ModEntities;
import net.austizz.ultimatebankingsystem.entity.custom.JobNpcEntity;
import net.austizz.ultimatebankingsystem.job.JobService;
import net.austizz.ultimatebankingsystem.job.JobRegistry;
import net.austizz.ultimatebankingsystem.npc.skin.NpcSkinService;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Commands for opening job views and placing the server-owned job NPCs. */
@EventBusSubscriber(modid = UltimateBankingSystem.MODID)
public final class JobCommands {
    private JobCommands() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("ubs")
                .then(Commands.literal("job")
                        .executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            JobService.openMarket(player);
                            return 1;
                        })
                        .then(Commands.literal("market").executes(context -> {
                            JobService.openMarket(context.getSource().getPlayerOrException());
                            return 1;
                        }))
                        .then(Commands.literal("status").executes(context -> {
                            JobService.openMarket(context.getSource().getPlayerOrException());
                            return 1;
                        }))
                        .then(Commands.literal("leave").executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            JobService.handleAction(player, new net.austizz.ultimatebankingsystem.network.JobActionPayload(
                                    "LEAVE", "", 0, 0L, true));
                            return 1;
                        })))
                .then(Commands.literal("admin").requires(source -> source.hasPermission(2))
                        .then(Commands.literal("job")
                                .then(Commands.literal("spawn")
                                        .then(Commands.literal("market").executes(context -> spawn(
                                                context.getSource().getPlayerOrException(), JobNpcEntity.Role.MARKET)))
                                        .then(Commands.literal("lumberjack").executes(context -> spawn(
                                                context.getSource().getPlayerOrException(), JobNpcEntity.Role.LUMBERJACK_BUYER)))
                                        .then(Commands.literal("miner").executes(context -> spawnBuyer(
                                                context.getSource().getPlayerOrException(), JobService.MINER_ID)))
                                        .then(Commands.literal("fisherman").executes(context -> spawnBuyer(
                                                context.getSource().getPlayerOrException(), JobService.FISHERMAN_ID)))
                                        .then(Commands.literal("farmer").executes(context -> spawnBuyer(
                                                context.getSource().getPlayerOrException(), JobService.FARMER_ID)))
                                        .then(Commands.literal("hunter").executes(context -> spawnBuyer(
                                                context.getSource().getPlayerOrException(), JobService.HUNTER_ID)))
                                        .then(Commands.literal("blacksmith").executes(context -> spawnBuyer(
                                                context.getSource().getPlayerOrException(), JobService.BLACKSMITH_ID)))
                                        .then(Commands.literal("courier").executes(context -> spawnBuyer(
                                                context.getSource().getPlayerOrException(), JobService.COURIER_ID)))
                                        .then(Commands.literal("chef").executes(context -> spawnBuyer(
                                                context.getSource().getPlayerOrException(), JobService.CHEF_ID)))
                                        .then(Commands.literal("herbalist").executes(context -> spawnBuyer(
                                                context.getSource().getPlayerOrException(), JobService.HERBALIST_ID)))
                                        .then(Commands.literal("salvager").executes(context -> spawnBuyer(
                                                context.getSource().getPlayerOrException(), JobService.SALVAGER_ID)))
                                        .then(Commands.literal("tailor").executes(context -> spawnBuyer(
                                                context.getSource().getPlayerOrException(), JobService.TAILOR_ID)))
                                        .then(Commands.literal("buyer").executes(context -> spawn(
                                                context.getSource().getPlayerOrException(), JobNpcEntity.Role.LUMBERJACK_BUYER))
                                                .then(Commands.argument("job", StringArgumentType.word())
                                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                                                JobRegistry.all().stream().map(definition -> definition.id()), builder))
                                                        .executes(context -> spawnBuyer(
                                                                context.getSource().getPlayerOrException(),
                                                                StringArgumentType.getString(context, "job")))))
                                        .then(Commands.literal("job")
                                                .then(Commands.argument("job", StringArgumentType.word())
                                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                                                JobRegistry.all().stream().map(definition -> definition.id()), builder))
                                                        .executes(context -> spawnBuyer(
                                                                context.getSource().getPlayerOrException(),
                                                                StringArgumentType.getString(context, "job"))))))
                                .then(Commands.literal("test")
                                        .then(Commands.literal("buyer").executes(context -> {
                                            JobService.openBuyer(context.getSource().getPlayerOrException(), true,
                                                    "Operator read-only buyer preview.");
                                            return 1;
                                        })))
                                .then(Commands.literal("npc")
                                        .then(Commands.literal("edit").executes(context -> {
                                            NpcSkinService.openEditor(context.getSource().getPlayerOrException());
                                            return 1;
                                        })))
                                .then(Commands.literal("reload").executes(context -> {
                                    JobService.refreshCatalog(context.getSource().getServer());
                                    context.getSource().sendSuccess(() -> Component.literal(
                                            "UBS job catalog refreshed.").withStyle(ChatFormatting.GREEN), false);
                                    return 1;
                                })))));
    }

    private static int spawn(ServerPlayer player, JobNpcEntity.Role role) {
        return spawn(player, role, JobService.LUMBERJACK_ID);
    }

    private static int spawnBuyer(ServerPlayer player, String jobId) {
        if (JobRegistry.get(jobId) == null) {
            player.sendSystemMessage(Component.literal("Unknown job: " + jobId).withStyle(ChatFormatting.RED));
            return 0;
        }
        return spawn(player, JobNpcEntity.roleForJob(jobId), jobId);
    }

    private static int spawn(ServerPlayer player, JobNpcEntity.Role role, String jobId) {
        if (player == null || player.serverLevel() == null) return 0;
        JobNpcEntity entity = ModEntities.JOB_NPC.get().create(player.serverLevel());
        if (entity == null) return 0;
        Vec3 position = player.position();
        float yaw = Mth.wrapDegrees(player.getYRot() + 180.0F);
        entity.moveTo(position.x, position.y, position.z, yaw, 0.0F);
        entity.setRole(role == JobNpcEntity.Role.JOB_BUYER ? JobNpcEntity.roleForJob(jobId) : role);
        entity.setJobId(jobId);
        entity.alignBodyTo(yaw);
        entity.setPersistenceRequired();
        String label = role == JobNpcEntity.Role.MARKET ? "Job Market"
                : JobRegistry.get(entity.jobId()).buyerName();
        entity.setCustomName(Component.literal(label).withStyle(ChatFormatting.AQUA));
        entity.setCustomNameVisible(true);
        if (!player.serverLevel().noCollision(entity)) {
            player.sendSystemMessage(Component.literal("Not enough space to place the job NPC.")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
        player.serverLevel().addFreshEntity(entity);
        player.sendSystemMessage(Component.literal("Placed " + label + " NPC.").withStyle(ChatFormatting.GREEN));
        return 1;
    }
}
