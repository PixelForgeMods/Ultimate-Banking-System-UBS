package net.austizz.ultimatebankingsystem.entity.custom;

import net.austizz.ultimatebankingsystem.item.ModItems;
import net.austizz.ultimatebankingsystem.job.JobService;
import net.austizz.ultimatebankingsystem.job.JobRegistry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/** Static server-placed NPC used for job applications and all registered job turn-ins. */
public final class JobNpcEntity extends PathfinderMob {
    public enum Role {
        MARKET,
        LUMBERJACK_BUYER,
        JOB_BUYER,
        MINER_BUYER,
        FISHERMAN_BUYER,
        FARMER_BUYER,
        HUNTER_BUYER,
        BLACKSMITH_BUYER,
        COURIER_BUYER,
        CHEF_BUYER,
        HERBALIST_BUYER,
        SALVAGER_BUYER,
        TAILOR_BUYER
    }

    private static final EntityDataAccessor<Integer> ROLE =
            SynchedEntityData.defineId(JobNpcEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> JOB_ID =
            SynchedEntityData.defineId(JobNpcEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Float> FIXED_YAW =
            SynchedEntityData.defineId(JobNpcEntity.class, EntityDataSerializers.FLOAT);

    private UUID pendingRemovePlayer;
    private long pendingRemoveUntilTick;

    public JobNpcEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        setNoAi(true);
        setInvulnerable(true);
        setPersistenceRequired();
        noPhysics = false;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(ROLE, Role.MARKET.ordinal());
        builder.define(JOB_ID, JobService.LUMBERJACK_ID);
        builder.define(FIXED_YAW, 0.0F);
    }

    public Role role() {
        int ordinal = entityData.get(ROLE);
        Role[] values = Role.values();
        return ordinal < 0 || ordinal >= values.length ? Role.MARKET : values[ordinal];
    }

    public void setRole(Role role) {
        entityData.set(ROLE, (role == null ? Role.MARKET : role).ordinal());
    }

    public static Role roleForJob(String jobId) {
        if (jobId == null) return Role.LUMBERJACK_BUYER;
        return switch (jobId.trim().toLowerCase(java.util.Locale.ROOT)) {
            case JobService.LUMBERJACK_ID -> Role.LUMBERJACK_BUYER;
            case JobService.MINER_ID -> Role.MINER_BUYER;
            case JobService.FISHERMAN_ID -> Role.FISHERMAN_BUYER;
            case JobService.FARMER_ID -> Role.FARMER_BUYER;
            case JobService.HUNTER_ID -> Role.HUNTER_BUYER;
            case JobService.BLACKSMITH_ID -> Role.BLACKSMITH_BUYER;
            case JobService.COURIER_ID -> Role.COURIER_BUYER;
            case JobService.CHEF_ID -> Role.CHEF_BUYER;
            case JobService.HERBALIST_ID -> Role.HERBALIST_BUYER;
            case JobService.SALVAGER_ID -> Role.SALVAGER_BUYER;
            case JobService.TAILOR_ID -> Role.TAILOR_BUYER;
            default -> Role.JOB_BUYER;
        };
    }

    public boolean isBuyer() {
        return role() != Role.MARKET;
    }

    public boolean isCourierDispatcher() {
        return JobService.COURIER_ID.equals(jobId());
    }

    public String jobId() {
        String jobId = entityData.get(JOB_ID);
        return JobRegistry.get(jobId) == null ? JobService.LUMBERJACK_ID : jobId;
    }

    public void setJobId(String jobId) {
        String normalized = jobId == null ? "" : jobId.trim().toLowerCase(java.util.Locale.ROOT);
        entityData.set(JOB_ID, JobRegistry.get(normalized) == null ? JobService.LUMBERJACK_ID : normalized);
    }

    @Override
    public void tick() {
        super.tick();
        setNoAi(true);
        setDeltaMovement(Vec3.ZERO);
        setInvulnerable(true);
        applyBodyRotation(entityData.get(FIXED_YAW));
        if (getHealth() < getMaxHealth()) setHealth(getMaxHealth());
    }

    @Override
    public void checkDespawn() {
        // Job NPCs are persistent server infrastructure.
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    @Override
    protected void doPush(net.minecraft.world.entity.Entity entity) {
    }

    @Override
    public void push(double x, double y, double z) {
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source != null && source.getEntity() instanceof ServerPlayer player && player.isShiftKeyDown()) {
            handleOperatorRemovalClick(player);
            return true;
        }
        return false;
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS;
        }
        if (serverPlayer.isShiftKeyDown()) {
            return handleOperatorRemovalClick(serverPlayer);
        }
        if (JobService.tryDeliverCourierParcel(serverPlayer, this, hand)) {
            return InteractionResult.CONSUME;
        }
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.SUCCESS;
        }
        if (isBuyer()) {
            if (isCourierDispatcher()) {
                JobService.openCourierDispatch(serverPlayer, this);
                return InteractionResult.CONSUME;
            }
            JobService.openBuyer(serverPlayer, false, "", jobId());
        } else {
            JobService.openMarket(serverPlayer);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        int savedRole = tag.getInt("role");
        Role[] roles = Role.values();
        setRole(savedRole >= 0 && savedRole < roles.length ? roles[savedRole] : Role.MARKET);
        setJobId(tag.contains("job_id") ? tag.getString("job_id") : JobService.LUMBERJACK_ID);
        if (tag.contains("FixedYaw")) {
            entityData.set(FIXED_YAW, Mth.wrapDegrees(tag.getFloat("FixedYaw")));
        } else {
            entityData.set(FIXED_YAW, Mth.wrapDegrees(getYRot()));
        }
        applyBodyRotation(entityData.get(FIXED_YAW));
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("role", role().ordinal());
        tag.putString("job_id", jobId());
        tag.putFloat("FixedYaw", entityData.get(FIXED_YAW));
    }

    public void alignBodyTo(float yaw) {
        float normalized = Mth.wrapDegrees(yaw);
        entityData.set(FIXED_YAW, normalized);
        applyBodyRotation(normalized);
    }

    private void applyBodyRotation(float yaw) {
        float normalized = Mth.wrapDegrees(yaw);
        setYRot(normalized);
        yRotO = normalized;
        setYBodyRot(normalized);
        yBodyRotO = normalized;
        setYHeadRot(normalized);
        yHeadRotO = normalized;
        setXRot(0.0F);
        xRotO = 0.0F;
    }

    /** Handles the operator's deliberate two-step removal gesture. */
    public InteractionResult handleOperatorRemovalClick(ServerPlayer player) {
        if (player == null || !player.hasPermissions(2)) {
            if (player != null) {
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "Only an operator can remove job NPCs.").withStyle(net.minecraft.ChatFormatting.RED));
            }
            return InteractionResult.CONSUME;
        }

        long now = level().getGameTime();
        if (player.getUUID().equals(pendingRemovePlayer) && now <= pendingRemoveUntilTick) {
            ItemStack egg = spawnEgg();
            if (!egg.isEmpty()) {
                if (!player.getInventory().add(egg)) {
                    player.drop(egg, false);
                }
            }
            pendingRemovePlayer = null;
            pendingRemoveUntilTick = 0L;
            JobService.onCourierNpcRemoved(this);
            discard();
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "Job NPC removed." + (egg.isEmpty() ? "" : " Spawn item returned.")
            ).withStyle(net.minecraft.ChatFormatting.GREEN));
            return InteractionResult.CONSUME;
        }

        pendingRemovePlayer = player.getUUID();
        pendingRemoveUntilTick = now + 100L;
        player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                "Shift-left-click this job NPC again within 5 seconds to remove it."
        ).withStyle(net.minecraft.ChatFormatting.YELLOW));
        return InteractionResult.CONSUME;
    }

    private ItemStack spawnEgg() {
        return switch (role()) {
            case MARKET -> new ItemStack(ModItems.JOB_MARKET_SPAWN_EGG.get());
            case LUMBERJACK_BUYER -> new ItemStack(ModItems.LUMBERJACK_BUYER_SPAWN_EGG.get());
            case MINER_BUYER -> new ItemStack(ModItems.MINER_BUYER_SPAWN_EGG.get());
            case FISHERMAN_BUYER -> new ItemStack(ModItems.FISHERMAN_BUYER_SPAWN_EGG.get());
            case FARMER_BUYER -> new ItemStack(ModItems.FARMER_BUYER_SPAWN_EGG.get());
            case HUNTER_BUYER -> new ItemStack(ModItems.HUNTER_BUYER_SPAWN_EGG.get());
            case BLACKSMITH_BUYER -> new ItemStack(ModItems.BLACKSMITH_BUYER_SPAWN_EGG.get());
            case COURIER_BUYER -> new ItemStack(ModItems.COURIER_BUYER_SPAWN_EGG.get());
            case CHEF_BUYER -> new ItemStack(ModItems.CHEF_BUYER_SPAWN_EGG.get());
            case HERBALIST_BUYER -> new ItemStack(ModItems.HERBALIST_BUYER_SPAWN_EGG.get());
            case SALVAGER_BUYER -> new ItemStack(ModItems.SALVAGER_BUYER_SPAWN_EGG.get());
            case TAILOR_BUYER -> new ItemStack(ModItems.TAILOR_BUYER_SPAWN_EGG.get());
            case JOB_BUYER -> new ItemStack(ModItems.LUMBERJACK_BUYER_SPAWN_EGG.get());
        };
    }
}
