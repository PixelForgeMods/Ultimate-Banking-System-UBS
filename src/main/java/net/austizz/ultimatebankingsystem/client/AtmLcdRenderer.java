package net.austizz.ultimatebankingsystem.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.austizz.ultimatebankingsystem.atm.AtmLcdState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;

public final class AtmLcdRenderer {
    private static final ResourceLocation WHITE = id("textures/gui/atm/lcd_white.png");
    private static final ResourceLocation REGULAR = id("atm_regular");
    private static final ResourceLocation BOLD = id("atm_bold");
    private static final ResourceLocation ITALIC = id("atm_italic");
    private static final int LIGHT = LightTexture.pack(14, 0);

    private AtmLcdRenderer() {}
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(UltimateBankingSystem.MODID, path); }

    public static void render(PoseStack pose, MultiBufferSource buffers, AtmLcdState state, long now) {
        render(pose,buffers,state,now,null);
    }
    public static void render(PoseStack pose,MultiBufferSource buffers,AtmLcdState state,long now,net.minecraft.core.BlockPos atm) {
        var screen=atm==null?null:AtmControls.screen(atm);
        var bounds = screen==null?AtmControls.lcdBounds():screen.bounds();
        if (bounds == null) return;
        pose.pushPose();
        if(screen!=null && screen.angle()!=0) {
            pose.translate(screen.origin().x/16,screen.origin().y/16,screen.origin().z/16);
            pose.mulPose(Axis.XP.rotationDegrees((float)screen.angle()));
            pose.translate(-screen.origin().x/16,-screen.origin().y/16,-screen.origin().z/16);
        }
        pose.translate(bounds.maxX / 16, bounds.maxY / 16, bounds.minZ / 16 - .0002);
        pose.mulPose(Axis.YP.rotationDegrees(180));
        pose.scale((float) bounds.getXsize() / (16 * AtmLcdLayout.WIDTH),
                (float) -bounds.getYsize() / (16 * AtmLcdLayout.HEIGHT), AtmLcdLayout.LAYER_SPACING);
        var buffer = buffers.getBuffer(RenderType.text(WHITE));
        for (var vertex : AtmLcdLayout.art(state).vertices()) {
            int color = toned(vertex.color());
            buffer.addVertex(pose.last().pose(), vertex.x(), vertex.y(), vertex.z()).setColor(color)
                    .setUv(.5F, .5F).setLight(LIGHT);
        }
        for (var label : AtmLcdLayout.labels(state, now)) text(pose, buffers, label);
        pose.popPose();
    }

    private static void text(PoseStack pose, MultiBufferSource buffers, AtmLcdLayout.Label label) {
        var font = Minecraft.getInstance().font;
        var resource = switch (label.typeface()) { case BOLD -> BOLD; case ITALIC -> ITALIC; default -> REGULAR; };
        var component = Component.literal(label.text()).withStyle(Style.EMPTY.withFont(resource));
        float scale = label.size() / 11;
        if (!label.wrap()) scale = Math.min(scale, label.width() / Math.max(1, font.width(component)));
        pose.pushPose();
        pose.translate(label.x(), label.baseline(), AtmLcdLayout.TEXT_LAYER);
        pose.mulPose(Axis.ZP.rotationDegrees(label.rotation()));
        pose.scale(scale, scale, 1);
        pose.translate(0, -7, 0);
        if (label.wrap()) {
            var lines = font.split(component, Math.max(1, (int) (label.width() / scale)));
            for (int i = 0; i < Math.min(2, lines.size()); i++) {
                font.drawInBatch(lines.get(i), 0, i * 14, toned(label.color()), false, pose.last().pose(),
                        buffers, Font.DisplayMode.NORMAL, 0, LIGHT);
            }
        } else {
            font.drawInBatch(component, 0, 0, toned(label.color()), false, pose.last().pose(),
                    buffers, Font.DisplayMode.NORMAL, 0, LIGHT);
        }
        pose.popPose();
    }

    private static int toned(int color) {
        return 0xFF000000 | ((color >> 16 & 255) * 176 / 255) << 16
                | ((color >> 8 & 255) * 176 / 255) << 8 | (color & 255) * 176 / 255;
    }
}
