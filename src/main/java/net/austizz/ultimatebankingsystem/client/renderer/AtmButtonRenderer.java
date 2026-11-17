package net.austizz.ultimatebankingsystem.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.austizz.ultimatebankingsystem.block.custom.ATMBlock;
import net.austizz.ultimatebankingsystem.block.entity.custom.AtmBlockEntity;
import net.austizz.ultimatebankingsystem.client.AtmControls;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.ModelResourceLocation;

public final class AtmButtonRenderer implements BlockEntityRenderer<AtmBlockEntity> {
    public AtmButtonRenderer(BlockEntityRendererProvider.Context context) {
        AtmControls.reload(Minecraft.getInstance().getResourceManager());
    }

    @Override
    public void render(AtmBlockEntity atm, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int light, int overlay) {
        var mc = Minecraft.getInstance();
        pose.pushPose();
        pose.translate(.5, 0, .5);
        pose.mulPose(Axis.YP.rotationDegrees(-AtmControls.rotation(atm.getBlockState().getValue(ATMBlock.FACING))));
        pose.translate(-.5, 0, -.5);
        long now = System.nanoTime();
        for (var button : AtmControls.buttons(atm.getBlockPos())) {
            double pressed = AtmControls.pressAmount(atm.pressedAt(button.index()), now);
            var offset = button.depression(pressed).scale(1.0 / 16);
            pose.pushPose();
            pose.translate(offset.x, offset.y, offset.z);
            var model = mc.getModelManager().getModel(ModelResourceLocation.standalone(AtmControls.model(button.index(),atm.getBlockPos())));
            mc.getBlockRenderer().getModelRenderer().renderModel(pose.last(), buffers.getBuffer(Sheets.cutoutBlockSheet()),
                    atm.getBlockState(), model, 1, 1, 1, light, overlay);
            pose.popPose();
            if (!button.keypad()) {
                var indicator = mc.getModelManager().getModel(ModelResourceLocation.standalone(
                        AtmControls.indicator(button.index(), pressed > 0)));
                mc.getBlockRenderer().getModelRenderer().renderModel(pose.last(), buffers.getBuffer(Sheets.cutoutBlockSheet()),
                        atm.getBlockState(), indicator, 1, 1, 1, light, overlay);
            }
        }
        var color = net.austizz.ultimatebankingsystem.atm.AtmDispenseMotion.readerLight(atm.phase(), atm.phaseAge(partialTick));
        var readerLed = mc.getModelManager().getModel(ModelResourceLocation.standalone(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(net.austizz.ultimatebankingsystem.UltimateBankingSystem.MODID, AtmControls.inWall(atm.getBlockPos())?"block/atm/in_wall/card_reader_led":"block/atm/card_reader_led")));
        mc.getBlockRenderer().getModelRenderer().renderModel(pose.last(), buffers.getBuffer(Sheets.cutoutBlockSheet()),
                atm.getBlockState(), readerLed, color.red(), color.green(), color.blue(), light, overlay);
        boolean inserted = !atm.cardStage().equals("EMPTY")
                || switch (atm.phase()) { case WAIT_CARD, TAKE_CARD, CARD_REMOVING -> true; default -> false; };
        var guideColor = net.austizz.ultimatebankingsystem.atm.AtmDispenseMotion.guideLight(inserted);
        var guide = mc.getModelManager().getModel(ModelResourceLocation.standalone(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(net.austizz.ultimatebankingsystem.UltimateBankingSystem.MODID, AtmControls.inWall(atm.getBlockPos())?"block/atm/in_wall/card_reader_guide":"block/atm/card_reader_guide")));
        mc.getBlockRenderer().getModelRenderer().renderModel(pose.last(), buffers.getBuffer(Sheets.cutoutBlockSheet()),
                atm.getBlockState(), guide, guideColor.red(), guideColor.green(), guideColor.blue(), light, overlay);
        AtmDispenserRenderer.render(atm, partialTick, pose, buffers, light, overlay);
        net.austizz.ultimatebankingsystem.client.AtmCardRenderer.renderShared(atm, partialTick, pose, buffers, light);
        var display = atm.sharedLcd();
        display.showDispenser(atm.phase(), atm.phaseAge(partialTick));
        display.showDepositDetails(atm.deposit().cents(), atm.deposit().detected());
        net.austizz.ultimatebankingsystem.client.AtmLcdRenderer.render(pose, buffers, display, now,atm.getBlockPos());
        pose.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(AtmBlockEntity atm) {
        return true; // The LCD buttons sit above the single block's base cell.
    }
}
