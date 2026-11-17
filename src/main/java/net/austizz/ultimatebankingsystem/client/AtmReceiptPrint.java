package net.austizz.ultimatebankingsystem.client;

import java.awt.Color;
import java.awt.Font;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/** The entire receipt is printed before its paper is fed through the slot. */
public final class AtmReceiptPrint {
    private AtmReceiptPrint() {}
    public static BufferedImage print(String details) {
        var image = new BufferedImage(256, 384, BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics();
        g.setColor(new Color(0xF1EEE3)); g.fillRect(0, 0, 256, 384);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new Color(0x30352F));
        String[] lines = details.split("\n", 9);
        for (int i = 0; i < lines.length; i++) {
            g.setFont(new Font(Font.MONOSPACED, i == 0 ? Font.BOLD : Font.PLAIN, i == 0 ? 23 : 15));
            g.drawString(lines[i].substring(0, Math.min(lines[i].length(), 24)), 20, 42 + i * 37);
        }
        g.dispose(); return image;
    }
    public static BufferedImage print(long dollars) {
        var image = new BufferedImage(256, 384, BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics();
        g.setColor(new Color(0xF1EEE3)); g.fillRect(0, 0, image.getWidth(), image.getHeight());
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new Color(0x30352F));
        g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 23));
        g.drawString("ATM RECEIPT", 24, 55);
        g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 20));
        g.drawLine(24, 78, 232, 78);
        g.drawString("WITHDRAWAL", 24, 125);
        g.drawString("$" + dollars + ".00", 24, 170);
        g.drawLine(24, 200, 232, 200);
        g.drawString("THANK YOU", 24, 260);
        g.dispose();
        return image;
    }
}
