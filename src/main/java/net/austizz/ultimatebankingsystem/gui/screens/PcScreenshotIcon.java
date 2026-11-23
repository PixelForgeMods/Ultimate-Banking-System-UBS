package net.austizz.ultimatebankingsystem.gui.screens;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

// Lucide, revision c26ce5515667640ca2753e095ba319d225fc0d8d. See META-INF/licenses/lucide-static.txt.
enum PcScreenshotIcon {
    CAMERA, RECTANGLE, WINDOW, FULL_SCREEN, LASSO, MARKUP, PIPETTE, CLOSE, CHEVRON_DOWN, UNDO, SAVE;

    private static final int TILE = 128;
    private static final int ATLAS_WIDTH = values().length * TILE;
    private static final ResourceLocation ATLAS = ResourceLocation.fromNamespaceAndPath(
            "ultimatebankingsystem", "textures/gui/pc/screenshot_icons.png");

    void draw(GuiGraphics graphics, int x, int y, int size, int color) {
        RenderSystem.enableBlend();
        graphics.setColor(((color >>> 16) & 255) / 255f, ((color >>> 8) & 255) / 255f,
                (color & 255) / 255f, (color >>> 24) / 255f);
        graphics.blit(ATLAS, x, y, size, size, ordinal() * TILE, 0, TILE, TILE, ATLAS_WIDTH, TILE);
        graphics.setColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();
    }
}
