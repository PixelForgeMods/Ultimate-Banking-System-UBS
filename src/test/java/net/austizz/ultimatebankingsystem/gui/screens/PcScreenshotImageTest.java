package net.austizz.ultimatebankingsystem.gui.screens;

import org.junit.jupiter.api.Test;

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PcScreenshotImageTest {
    @Test
    void reverseDragAndFractionalGuiScalingYieldClippedPixelBounds() {
        var area = PcScreenshotImage.bounds(new Point(90, 70), new Point(20, 10));
        assertEquals(new Rectangle(25, 19, 89, 117), PcScreenshotImage.toPixels(area, 100, 80, 126, 155));
        assertEquals(new Rectangle(126 - 13, 135, 13, 20),
                PcScreenshotImage.toPixels(new Rectangle(90, 70, 20, 20), 100, 80, 126, 155));
        assertTrue(PcScreenshotImage.toPixels(new Rectangle(100, 100, 15, 15), 100, 80, 126, 155).isEmpty());
    }

    @Test
    void freeformPreservesInteriorPixelsAndMakesTheExteriorTransparent() {
        var source = new BufferedImage(20, 20, BufferedImage.TYPE_INT_RGB);
        source.setRGB(5, 5, 0xFF4477CC);
        var pixels = new Rectangle(2, 2, 16, 16);
        var path = List.of(new Point(1, 1), new Point(9, 1), new Point(1, 9));
        var image = PcScreenshotImage.crop(source, pixels, path, 10, 10);

        assertEquals(16, image.getWidth());
        assertEquals(16, image.getHeight());
        assertEquals(0xFF4477CC, image.getRGB(3, 3));
        assertEquals(0, image.getRGB(15, 15) >>> 24);
    }
}
