package net.austizz.ultimatebankingsystem.gui.screens;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PcPhotoImageTest {
    @TempDir Path directory;

    @Test
    void transparentImagesRoundTripAndEditsPreserveTheSource() throws IOException {
        var original = new BufferedImage(9, 4, BufferedImage.TYPE_INT_ARGB);
        original.setRGB(0, 0, 0xFFFF0000);
        original.setRGB(8, 3, 0xFF0000FF);
        byte[] source = PcPhotoImage.encodePng(original);
        var drive = new PcFileSystem(directory.resolve("photos.drive"));
        var id = drive.createBinary(PcFileSystem.DOCUMENTS, "Photo.PNG", source);
        var decoded = PcPhotoImage.decode(drive.get(id).payload(), 0);
        assertEquals("PNG", decoded.format());
        assertEquals(0, decoded.pixels().getRGB(1, 1));
        assertEquals(0xFFFF0000, PcPhotoImage.flip(decoded.pixels(), true).getRGB(8, 0));
        var rotated = PcPhotoImage.rotate(decoded.pixels(), true);
        assertEquals(4, rotated.getWidth());
        assertEquals(9, rotated.getHeight());
        assertEquals(0xFFFF0000, rotated.getRGB(3, 0));
        assertEquals(0xFF0000FF, rotated.getRGB(0, 8));
        var copy = drive.createBinary(PcFileSystem.DOCUMENTS, "Edited.png",
                PcPhotoImage.encodePng(PcPhotoImage.resize(rotated, 8, 18)));
        var reloaded = new PcFileSystem(directory.resolve("photos.drive"));
        assertArrayEquals(source, reloaded.get(id).payload());
        assertEquals(18, PcPhotoImage.decode(reloaded.get(copy).payload(), 0).pixels().getHeight());
    }

    @Test
    void readerSurvivesInvalidInputAndProducesBoundedThumbnails() throws IOException {
        var image = new BufferedImage(72, 36, BufferedImage.TYPE_INT_ARGB);
        var thumb = PcPhotoImage.decode(PcPhotoImage.encodePng(image), 16).pixels();
        assertTrue(thumb.getWidth() <= 16 && thumb.getHeight() <= 16);
        assertThrows(IOException.class, () -> PcPhotoImage.decode(new byte[]{1, 2, 3}, 0));
        assertThrows(IOException.class, () -> PcPhotoImage.resize(image, 1, 4097));
        assertTrue(PcPhotoImage.supports("WallPaper.JPEG"));
    }
}
