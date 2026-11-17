package net.austizz.ultimatebankingsystem.client;

import net.austizz.ultimatebankingsystem.atm.AtmLcdState;
import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class AtmLcdLayoutTest {
    @Test
    void overlappingCardAndLanguageButtonSurfacesHaveSeparateDepths() {
        var mesh = AtmLcdLayout.ART.vertices();
        assertTrue(mesh.stream().allMatch(v -> v.z() < AtmLcdLayout.TEXT_LAYER), "Text must sit ahead of every artwork layer");
        for (float[] point : new float[][]{{750, 270}, {100, 620}, {800, 456}}) {
            float previousDepth = -1;
            int surfaces = 0;
            for (int i = 0; i < mesh.size(); i += 4) {
                boolean positive = false, negative = false;
                float area = 0;
                for (int j = 0; j < 4; j++) {
                    var a = mesh.get(i + j);
                    var b = mesh.get(i + (j + 1) % 4);
                    float cross = (b.x() - a.x()) * (point[1] - a.y()) - (b.y() - a.y()) * (point[0] - a.x());
                    positive |= cross > .001F;
                    negative |= cross < -.001F;
                    area += a.x() * b.y() - b.x() * a.y();
                }
                if (Math.abs(area) > .001F && !(positive && negative)) {
                    float depth = mesh.get(i).z();
                    assertTrue(depth > previousDepth, "Overlapping LCD surfaces must not share a depth");
                    previousDepth = depth;
                    surfaces++;
                }
            }
            assertTrue(surfaces >= 3, "Probe must cover background and nested artwork");
        }
    }

    @Test
    void physicalBottomButtonsSelectLanguageAndOtherKeysDoNot() {
        var lcd = new AtmLcdState();
        assertEquals("Welcome", lcd.title());
        lcd.press(23);
        assertEquals("Bienvenido", lcd.title());
        for (int i = 0; i < 23; i++) if (i != 19) lcd.press(i);
        assertTrue(lcd.spanish());
        lcd.press(19);
        assertEquals("Please insert your card", lcd.primary(0));
        assertFalse(new AtmLcdState().spanish());
    }

    @Test
    void cardStatusAndTemporaryErrorsStayOnTheLcd() {
        var lcd = new AtmLcdState();
        lcd.showNotice("Hold a valid credit card.", 10);
        assertEquals("Please try again", lcd.primary(11));
        assertEquals("Hold a valid credit card.", lcd.secondary(11));
        assertEquals("to begin your transaction.", lcd.secondary(4_000_000_010L));
        for (var status : new AtmLcdState.Status[]{AtmLcdState.Status.WELCOME, AtmLcdState.Status.CHECKING, AtmLcdState.Status.INSERTING, AtmLcdState.Status.INSERTED, AtmLcdState.Status.REMOVING}) {
            lcd.setStatus(status);
            assertFalse(lcd.primary(5_000_000_000L).isBlank());
            assertFalse(lcd.secondary(5_000_000_000L).isBlank());
        }
        lcd.setStatus(AtmLcdState.Status.INSERTED);
        assertEquals("Card accepted", lcd.title());
        lcd.press(23);
        assertEquals("Tarjeta aceptada", lcd.title());
        lcd.clearNotice();
        assertEquals("", lcd.notice(11));
    }

    @Test
    void welcomeArtworkMatchesReferenceLayoutWithoutUsingItsPixels() throws Exception {
        var image = preview(new AtmLcdState(), "atm-lcd-welcome");
        var assets = Path.of("src/main/resources/assets/ultimatebankingsystem");
        var output = Path.of(System.getProperty("ubs.testBuildDir", "build"), "test-artifacts/atm-lcd-welcome.png");
        var reference = ImageIO.read(assets.resolve("textures/block/atm_machine.png").toFile()).getSubimage(0, 256, 1024, 768);
        long error = 0;
        for (int y = 0; y < 768; y++) for (int x = 0; x < 1024; x++) {
            int a = image.getRGB(x, y), b = reference.getRGB(x, y);
            for (int shift : new int[]{0, 8, 16}) error += Math.abs((a >> shift & 255) - (b >> shift & 255));
        }
        double meanError = error / (1024.0 * 768 * 3);
        assertTrue(meanError < 14, "Reference layout/color difference: " + meanError + "; inspect " + output);
    }
    static BufferedImage preview(AtmLcdState state, String name) throws Exception {
        var image = new BufferedImage(1024, 768, BufferedImage.TYPE_INT_RGB);
        var g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        var mesh = AtmLcdLayout.art(state).vertices();
        assertEquals(0, mesh.size() % 4);
        for (int i = 0; i < mesh.size(); i += 4) {
            var path = new Path2D.Float();
            for (int j = 0; j < 4; j++) {
                var v = mesh.get(i + j);
                assertTrue(v.x() >= 0 && v.x() <= 1024 && v.y() >= 0 && v.y() <= 768);
                if (j == 0) path.moveTo(v.x(), v.y()); else path.lineTo(v.x(), v.y());
            }
            path.closePath();
            g.setColor(new Color(mesh.get(i).color()));
            g.fill(path);
        }
        var assets = Path.of("src/main/resources/assets/ultimatebankingsystem");
        for (var label : AtmLcdLayout.labels(state, 0)) {
            String type = label.typeface().name().toLowerCase(java.util.Locale.ROOT);
            var font = Font.createFont(Font.TRUETYPE_FONT, assets.resolve("font/atm_" + type + ".ttf").toFile())
                    .deriveFont(label.size());
            g.setFont(font);
            float width = g.getFontMetrics().stringWidth(label.text());
            if (!label.wrap() && width > label.width()) g.setFont(font.deriveFont(label.size() * label.width() / width));
            var transform = g.getTransform();
            g.translate(label.x(), label.baseline());
            g.rotate(Math.toRadians(label.rotation()));
            g.setColor(new Color(label.color()));
            g.drawString(label.text(), 0, 0);
            g.setTransform(transform);
        }
        g.dispose();
        var output = Path.of(System.getProperty("ubs.testBuildDir", "build"), "test-artifacts/" + name + ".png");
        Files.createDirectories(output.getParent());
        ImageIO.write(image, "png", output.toFile());
        return image;
    }

}
