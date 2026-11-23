package net.austizz.ultimatebankingsystem.gui.screens;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.Set;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;

final class PcPhotoImage {
    private static final int MAX_EDGE = 4096;
    private static final Set<String> EXTENSIONS = Set.of("png", "jpg", "jpeg", "gif", "bmp", "tif", "tiff");
    private static final Set<String> FORMATS = Set.of("png", "jpeg", "gif", "bmp", "tiff");

    record Decoded(BufferedImage pixels, String format, int sourceWidth, int sourceHeight) {}

    private PcPhotoImage() {}

    static java.util.List<String> extensions() { return EXTENSIONS.stream().sorted().toList(); }

    static boolean supports(String name) {
        return EXTENSIONS.contains(PcFileType.extension(name));
    }

    static Decoded decode(byte[] bytes, int thumbnailEdge) throws IOException {
        try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IOException("This image is damaged or uses an unsupported format.");
            var reader = readers.next();
            try {
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!FORMATS.contains(format)) throw new IOException("Unsupported image format.");
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                checkSize(width, height);
                var params = reader.getDefaultReadParam();
                if (thumbnailEdge > 0) {
                    int sample = Math.max(1, Math.ceilDiv(Math.max(width, height), thumbnailEdge));
                    params.setSourceSubsampling(sample, sample, 0, 0);
                }
                return new Decoded(reader.read(0, params), format.equals("jpeg") ? "JPEG" : format.toUpperCase(Locale.ROOT),
                        width, height);
            } finally {
                reader.dispose();
            }
        } catch (RuntimeException e) {
            throw new IOException("The image could not be decoded.", e);
        }
    }

    static void checkSize(int width, int height) throws IOException {
        if (width < 1 || height < 1 || width > MAX_EDGE || height > MAX_EDGE) {
            throw new IOException("Image dimensions must be between 1 and 4096 pixels.");
        }
    }

    static BufferedImage rotate(BufferedImage input, boolean clockwise) {
        int width = input.getWidth();
        int height = input.getHeight();
        var output = new BufferedImage(height, width, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = output.createGraphics();
        try {
            graphics.translate(clockwise ? height : 0, clockwise ? 0 : width);
            graphics.rotate(clockwise ? Math.PI / 2 : -Math.PI / 2);
            graphics.drawImage(input, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        return output;
    }

    static BufferedImage flip(BufferedImage input, boolean horizontal) {
        int width = input.getWidth();
        int height = input.getHeight();
        var output = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = output.createGraphics();
        try {
            graphics.drawImage(input, horizontal ? width : 0, horizontal ? 0 : height,
                    horizontal ? -width : width, horizontal ? height : -height, null);
        } finally {
            graphics.dispose();
        }
        return output;
    }

    static BufferedImage resize(BufferedImage input, int width, int height) throws IOException {
        checkSize(width, height);
        var output = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = output.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.drawImage(input, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }
        return output;
    }

    static byte[] encodePng(BufferedImage image) throws IOException {
        var bytes = new ByteArrayOutputStream();
        if (!ImageIO.write(image, "PNG", bytes)) throw new IOException("PNG encoding is unavailable.");
        return bytes.toByteArray();
    }
}
