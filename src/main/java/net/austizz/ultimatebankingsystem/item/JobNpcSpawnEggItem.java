package net.austizz.ultimatebankingsystem.item;

import net.austizz.ultimatebankingsystem.entity.ModEntities;
import net.austizz.ultimatebankingsystem.entity.custom.JobNpcEntity;
import net.austizz.ultimatebankingsystem.job.JobDefinition;
import net.austizz.ultimatebankingsystem.job.JobRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Places a configured job NPC while reusing the single synchronized job-NPC entity type. */
public final class JobNpcSpawnEggItem extends Item {
    private final JobNpcEntity.Role role;
    private final String jobId;

    public JobNpcSpawnEggItem(JobNpcEntity.Role role, String jobId) {
        super(new Item.Properties().stacksTo(16));
        this.role = role == null ? JobNpcEntity.Role.MARKET : role;
        String normalizedJobId = jobId == null ? "" : jobId.trim().toLowerCase(java.util.Locale.ROOT);
        if (JobRegistry.get(normalizedJobId) == null) {
            throw new IllegalArgumentException("Unknown job NPC job id: " + jobId);
        }
        this.jobId = normalizedJobId;
    }

    public JobNpcEntity.Role role() {
        return role;
    }

    public String jobId() {
        return jobId;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS;
        }
        if (!(context.getPlayer() instanceof ServerPlayer player)) {
            return InteractionResult.FAIL;
        }

        JobNpcEntity npc = ModEntities.JOB_NPC.get().create(serverLevel);
        if (npc == null) {
            return InteractionResult.FAIL;
        }

        BlockPos spawnPos = context.getClickedPos().relative(context.getClickedFace());
        Vec3 spawnCenter = Vec3.atBottomCenterOf(spawnPos);
        float yaw = Mth.wrapDegrees(player.getYRot() + 180.0F);
        npc.moveTo(spawnCenter.x, spawnCenter.y, spawnCenter.z, yaw, 0.0F);
        npc.setRole(role);
        npc.setJobId(jobId);
        npc.alignBodyTo(yaw);
        npc.setPersistenceRequired();

        JobDefinition definition = JobRegistry.get(jobId);
        String label = role == JobNpcEntity.Role.MARKET
                ? "Job Market"
                : definition.buyerName();
        npc.setCustomName(Component.literal(label).withStyle(ChatFormatting.AQUA));
        npc.setCustomNameVisible(true);

        if (!serverLevel.noCollision(npc)) {
            npc.discard();
            player.sendSystemMessage(Component.literal("Not enough space to place the job NPC.")
                    .withStyle(ChatFormatting.RED));
            return InteractionResult.FAIL;
        }

        serverLevel.addFreshEntity(npc);
        if (!player.getAbilities().instabuild) {
            context.getItemInHand().shrink(1);
        }
        player.sendSystemMessage(Component.literal("Placed " + label + " NPC.")
                .withStyle(ChatFormatting.GREEN));
        return InteractionResult.CONSUME;
    }
}
