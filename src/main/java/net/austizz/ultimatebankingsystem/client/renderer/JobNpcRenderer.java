package net.austizz.ultimatebankingsystem.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.austizz.ultimatebankingsystem.client.NpcSkinClientCache;
import net.austizz.ultimatebankingsystem.entity.custom.JobNpcEntity;
import net.austizz.ultimatebankingsystem.npc.skin.NpcSkinTarget;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

/** Reuses the UBS humanoid assets while keeping the job NPC visually distinct by role. */
public final class JobNpcRenderer extends HumanoidMobRenderer<JobNpcEntity, PlayerModel<JobNpcEntity>> {
    private final PlayerModel<JobNpcEntity> wideModel;
    private final PlayerModel<JobNpcEntity> slimModel;

    public JobNpcRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
        this.wideModel = this.model;
        this.slimModel = new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER_SLIM), true);
    }

    @Override
    public void render(JobNpcEntity entity,
                       float entityYaw,
                       float partialTicks,
                       PoseStack poseStack,
                       net.minecraft.client.renderer.MultiBufferSource buffer,
                       int packedLight) {
        NpcSkinTarget target = skinTarget(entity);
        this.model = NpcSkinClientCache.usesSlimModel(target.id(), target.defaultSlimModel())
                ? this.slimModel : this.wideModel;
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(JobNpcEntity entity) {
        NpcSkinTarget target = skinTarget(entity);
        return NpcSkinClientCache.textureFor(target.id(), target.defaultTexture());
    }

    private static NpcSkinTarget skinTarget(JobNpcEntity entity) {
        return entity == null || !entity.isBuyer()
                ? NpcSkinTarget.JOB_MARKET
                : NpcSkinTarget.forJob(entity.jobId());
    }

    @Override
    protected void scale(JobNpcEntity entity, PoseStack poseStack, float partialTickTime) {
        super.scale(entity, poseStack, partialTickTime);
        poseStack.scale(0.98F, 0.98F, 0.98F);
    }
}
