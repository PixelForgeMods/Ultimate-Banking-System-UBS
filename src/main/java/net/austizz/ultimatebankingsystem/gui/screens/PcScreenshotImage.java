package net.austizz.ultimatebankingsystem.gui.screens;

import java.awt.AlphaComposite;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.util.List;

final class PcScreenshotImage {
    private PcScreenshotImage() {}

    static Rectangle bounds(Point first, Point second) {
        return new Rectangle(Math.min(first.x, second.x), Math.min(first.y, second.y),
                Math.abs(first.x - second.x), Math.abs(first.y - second.y));
    }

    static Rectangle toPixels(Rectangle area, int uiWidth, int uiHeight, int pixelWidth, int pixelHeight) {
        var clipped = area.intersection(new Rectangle(0, 0, uiWidth, uiHeight));
        if (clipped.isEmpty()) return new Rectangle();
        double sx = pixelWidth / (double) uiWidth;
        double sy = pixelHeight / (double) uiHeight;
        int left = (int) Math.floor(clipped.x * sx);
        int top = (int) Math.floor(clipped.y * sy);
        int right = Math.min(pixelWidth, (int) Math.ceil((clipped.x + clipped.width) * sx));
        int bottom = Math.min(pixelHeight, (int) Math.ceil((clipped.y + clipped.height) * sy));
        return new Rectangle(left, top, right - left, bottom - top);
    }

    static BufferedImage crop(BufferedImage source, Rectangle pixels, List<Point> outline, int uiWidth, int uiHeight) {
        if (pixels.isEmpty()) return null;
        var result = new BufferedImage(pixels.width, pixels.height, BufferedImage.TYPE_INT_ARGB);
        var graphics = result.createGraphics();
        try {
            if (!outline.isEmpty()) {
                var path = new Path2D.Double(Path2D.WIND_EVEN_ODD);
                double sx = source.getWidth() / (double) uiWidth;
                double sy = source.getHeight() / (double) uiHeight;
                path.moveTo(outline.getFirst().x * sx - pixels.x, outline.getFirst().y * sy - pixels.y);
                for (var point : outline) path.lineTo(point.x * sx - pixels.x, point.y * sy - pixels.y);
                path.closePath();
                graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                graphics.fill(path);
                graphics.setComposite(AlphaComposite.SrcIn);
            }
            graphics.drawImage(source, -pixels.x, -pixels.y, null);
        } finally {
            graphics.dispose();
        }
        return result;
    }
}
