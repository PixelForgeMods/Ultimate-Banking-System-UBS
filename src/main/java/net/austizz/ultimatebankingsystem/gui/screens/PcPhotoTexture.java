package net.austizz.ultimatebankingsystem.gui.screens;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.UUID;

record PcPhotoTexture(ResourceLocation id, int width, int height) implements AutoCloseable {
    static PcPhotoTexture load(PcFileSystem drive, UUID id) throws IOException {
        var entry = drive.get(id);
        if (!PcPhotosContent.canOpen(entry)) throw new IOException("Unsupported image file.");
        return upload(PcPhotoImage.decode(entry.payload(), 0).pixels());
    }

    static PcPhotoTexture upload(BufferedImage source) {
        int width = source.getWidth();
        int height = source.getHeight();
        var nativeImage = new NativeImage(width, height, false);
        var row = new int[width];
        for (int y = 0; y < height; y++) {
            source.getRGB(0, y, width, 1, row, 0, width);
            for (int x = 0; x < width; x++) {
                int argb = row[x];
                int abgr = (argb & 0xFF00FF00) | ((argb >>> 16) & 0xFF) | ((argb & 0xFF) << 16);
                nativeImage.setPixelRGBA(x, y, abgr);
            }
        }
        var texture = new DynamicTexture(nativeImage);
        var manager = Minecraft.getInstance().getTextureManager();
        ResourceLocation id = manager.register("ubs_pc_photo", texture);
        texture.setFilter(true, false);
        return new PcPhotoTexture(id, width, height);
    }

    void draw(GuiGraphics graphics, int x, int y, int drawWidth, int drawHeight) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        graphics.blit(id, x, y, drawWidth, drawHeight, 0f, 0f, width, height, width, height);
        RenderSystem.disableBlend();
    }

    void drawBackground(GuiGraphics graphics, int screenWidth, int screenHeight) {
        double scale = Math.max(screenWidth / (double) width, screenHeight / (double) height);
        int drawWidth = (int) Math.ceil(width * scale);
        int drawHeight = (int) Math.ceil(height * scale);
        graphics.fill(0, 0, screenWidth, screenHeight, 0xFF11151D);
        draw(graphics, (screenWidth - drawWidth) / 2, (screenHeight - drawHeight) / 2, drawWidth, drawHeight);
    }

    void drawFitted(GuiGraphics graphics, int x, int y, int boxWidth, int boxHeight) {
        double scale = Math.min(boxWidth / (double) width, boxHeight / (double) height);
        int drawWidth = Math.max(1, (int) Math.round(width * scale));
        int drawHeight = Math.max(1, (int) Math.round(height * scale));
        draw(graphics, x + (boxWidth - drawWidth) / 2, y + (boxHeight - drawHeight) / 2, drawWidth, drawHeight);
    }

    @Override
    public void close() {
        Minecraft.getInstance().getTextureManager().release(id);
    }
}
