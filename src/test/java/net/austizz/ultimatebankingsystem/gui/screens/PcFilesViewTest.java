package net.austizz.ultimatebankingsystem.gui.screens;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PcFilesViewTest {
    @Test
    void allExplorerGlyphsHaveATransparentAtlasTile() throws IOException {
        String path = "/assets/ultimatebankingsystem/textures/gui/pc/files_icons.png";
        try (var texture = getClass().getResourceAsStream(path)) {
            assertNotNull(texture);
            var image = ImageIO.read(texture);
            int tile = PcFilesIcon.TILE;
            assertEquals(PcFilesIcon.COLUMNS * tile, image.getWidth());
            assertEquals(PcFilesIcon.ROWS * tile, image.getHeight());
            for (var icon : PcFilesIcon.values()) {
                int left = icon.ordinal() % PcFilesIcon.COLUMNS * tile;
                int top = icon.ordinal() / PcFilesIcon.COLUMNS * tile;
                int[] pixels = image.getRGB(left, top, tile, tile, null, 0, tile);
                assertEquals(0, pixels[0] >>> 24, icon.source);
                assertTrue(java.util.Arrays.stream(pixels).anyMatch(pixel -> (pixel >>> 24) != 0), icon.source);
                if (icon.large) {
                    String asset = "/assets/ultimatebankingsystem/textures/gui/pc/files_icons/" + icon.source + ".png";
                    try (var largeTexture = getClass().getResourceAsStream(asset)) {
                        assertNotNull(largeTexture, asset);
                        var largeIcon = ImageIO.read(largeTexture);
                        assertEquals(PcDesktopScreen.HIGH_RES_FILE_ICON_PIXELS, largeIcon.getWidth(), asset);
                        assertEquals(PcDesktopScreen.HIGH_RES_FILE_ICON_PIXELS, largeIcon.getHeight(), asset);
                        assertEquals(0, largeIcon.getRGB(0, 0) >>> 24, asset);
                    }
                }
            }
        }
    }

    @Test
    void presetsRoundTripAndWheelStepsClampWithoutWrapping() {
        for (var view : PcFilesView.values()) {
            assertTrue(PcFilesView.validStep(view.step));
            assertEquals(view, PcFilesView.at(view.step));
        }
        assertEquals(PcFilesView.LIST.step, PcFilesView.zoom(PcFilesView.DETAILS.step, 1));
        assertEquals(PcFilesView.TILES.step, PcFilesView.zoom(PcFilesView.DETAILS.step, -1));
        assertEquals(PcFilesView.CONTENT.step, PcFilesView.zoom(PcFilesView.CONTENT.step, Integer.MIN_VALUE));
        assertEquals(PcFilesView.EXTRA_LARGE.step, PcFilesView.zoom(PcFilesView.LARGE.step, Integer.MAX_VALUE));
    }

    @Test
    void eachIconStepIncreasesTheSizeAcrossAllPresets() {
        for (int step = PcFilesView.SMALL.step; step < PcFilesView.EXTRA_LARGE.step; step++) {
            assertTrue(PcFilesView.iconSize(step) < PcFilesView.iconSize(step + 1));
        }
    }
}
