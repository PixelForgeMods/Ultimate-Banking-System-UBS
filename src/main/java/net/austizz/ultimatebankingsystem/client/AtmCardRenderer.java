package net.austizz.ultimatebankingsystem.client;

import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

public final class AtmCardRenderer {
    private AtmCardRenderer() {}

    static void render(RenderLevelStageEvent event, BlockPos atm, Direction facing, AbstractClientPlayer player,
                       ItemStack card, InteractionHand hand, AtmCardMotion.Sample sample) {
        if (!sample.visible() || card.isEmpty()) return;
        var mc = Minecraft.getInstance();
        var pose = event.getPoseStack();
        var camera = event.getCamera().getPosition();
        var buffers = mc.renderBuffers().bufferSource();
        var slot = AtmControls.readerSlot(atm).scale(1.0 / 16);
        int light = LevelRenderer.getLightColor(mc.level, atm.above());
        light = LightTexture.pack(Math.max(10, LightTexture.block(light)), LightTexture.sky(light));
        pose.pushPose();
        pose.translate(atm.getX() - camera.x + .5, atm.getY() - camera.y, atm.getZ() - camera.z + .5);
        pose.mulPose(Axis.YP.rotationDegrees(-AtmControls.rotation(facing)));
        pose.translate(slot.x - .5, slot.y, slot.z - .5);

        if(AtmControls.inWall(atm))pose.mulPose(Axis.XP.rotationDegrees(22.5F));
        draw(pose, buffers, light, player, card, hand, sample);
        pose.popPose();
        buffers.endBatch();
    }
    public static void renderShared(net.austizz.ultimatebankingsystem.block.entity.custom.AtmBlockEntity atm, float partial,
                                    com.mojang.blaze3d.vertex.PoseStack pose, net.minecraft.client.renderer.MultiBufferSource buffers, int light) {
        if (AtmCameraController.isViewing(atm.getBlockPos()) || atm.visualCard().isEmpty()) return;
        var mc = Minecraft.getInstance();
        var player = atm.cardOwner() == null ? null : mc.level.getPlayerByUUID(atm.cardOwner());
        var motion = new AtmCardMotion();
        motion.phase = AtmCardMotion.Phase.INSERTED;
        if (atm.cardStage().equals("OFFERED") || atm.cardStage().equals("REMOVING")) motion.presentCard(1);
        motion.phase = switch (atm.cardStage()) {
            case "INSERTING" -> AtmCardMotion.Phase.INSERTING;
            case "EJECTING" -> AtmCardMotion.Phase.EJECTING;
            case "REMOVING" -> AtmCardMotion.Phase.REMOVING;
            case "OFFERED", "INSERTED" -> AtmCardMotion.Phase.INSERTED;
            default -> AtmCardMotion.Phase.EMPTY;
        };
        long now = System.nanoTime();
        motion.started = now - (long) (atm.cardAge(partial) * 50_000_000L);
        var sample = motion.sample(now);
        if (!sample.visible()) return;
        var slot = AtmControls.readerSlot(atm.getBlockPos()).scale(1.0 / 16);
        pose.pushPose(); pose.translate(slot.x, slot.y, slot.z);
        if(AtmControls.inWall(atm.getBlockPos()))pose.mulPose(Axis.XP.rotationDegrees(22.5F));
        draw(pose, buffers, light, player instanceof AbstractClientPlayer client ? client : null, atm.visualCard(), atm.cardHand(), sample);
        pose.popPose();
    }
    private static void draw(com.mojang.blaze3d.vertex.PoseStack pose, net.minecraft.client.renderer.MultiBufferSource buffers,
                             int light, AbstractClientPlayer player, ItemStack card, InteractionHand hand, AtmCardMotion.Sample sample) {
        var mc = Minecraft.getInstance();
        var mainArm = player == null ? HumanoidArm.RIGHT : player.getMainArm();
        var arm = hand == InteractionHand.MAIN_HAND ? mainArm : mainArm.getOpposite();
        double mirror = arm == HumanoidArm.RIGHT ? 1 : -1;
        pose.pushPose();
        pose.translate(mirror * sample.cardX(), sample.cardY(), sample.cardZ());
        // Short edge fits the slot; the chip leads, with the card face upward.
        pose.mulPose(Axis.YP.rotationDegrees(-90));
        pose.mulPose(Axis.XP.rotationDegrees(90));
        pose.scale(.16F, .16F, .16F);
        mc.getItemRenderer().renderStatic(card, ItemDisplayContext.NONE, light, OverlayTexture.NO_OVERLAY,
                pose, buffers, mc.level, player == null ? 0 : player.getId());
        pose.popPose();

        if (sample.handVisible() && player != null && !player.isInvisible()) {
            var renderer = (PlayerRenderer) mc.getEntityRenderDispatcher().getRenderer(player);
            pose.pushPose();
            double radians = Math.toRadians(75);
            pose.translate(mirror * sample.handX() + mirror * .15,
                    sample.handY() - .30 * Math.cos(radians), sample.handZ() - .30 * Math.sin(radians));
            pose.mulPose(Axis.XP.rotationDegrees(75));
            pose.scale(.4F, .4F, .4F);
            if (arm == HumanoidArm.RIGHT) renderer.renderRightHand(pose, buffers, light, player);
            else renderer.renderLeftHand(pose, buffers, light, player);
            pose.popPose();
        }
    }

}
