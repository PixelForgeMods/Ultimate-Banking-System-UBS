package net.austizz.ultimatebankingsystem.client;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;

class AtmReceiptPrintTest {
    @Test void transactionMetadataIsPrintedAsOneOpaqueSurface() throws Exception {
        String text="ATM RECEIPT\nExample Bank\nCASH DEPOSIT\n$30.00\n2026-11-08 15:42\nCARD **** 9012\nREF ABCD1234\nTHANK YOU";
        var image=AtmReceiptPrint.print(text);
        for(int i=0;i<8;i++) {
            int ink=0;
            for(int y=19+i*37;y<=44+i*37;y++) for(int x=0;x<256;x++) {
                int pixel=image.getRGB(x,y); assertEquals(255,pixel>>>24);
                if((pixel>>16&255)<100) ink++;
            }
            assertTrue(ink>20,"Every receipt detail must be inked before feeding");
        }
        var output=Path.of(System.getProperty("ubs.testBuildDir","build"),"test-artifacts/atm-detailed-deposit-receipt.png");
        Files.createDirectories(output.getParent()); ImageIO.write(image,"png",output.toFile());
    }
    @Test void completeReceiptContainsItsInkBeforeAnyFeedAnimationStarts() throws Exception {
        var image = AtmReceiptPrint.print(100);
        assertEquals(384, image.getHeight(), "Trim excess paper while preserving printed content");
        for (int[] region : new int[][]{{20, 60}, {100, 180}, {235, 270}}) {
            int ink = 0;
            for (int y = region[0]; y <= region[1]; y++) for (int x = 0; x < image.getWidth(); x++) {
                int pixel = image.getRGB(x, y);
                assertEquals(255, pixel >>> 24, "Paper and ink form one opaque surface");
                if ((pixel >> 16 & 255) < 100) ink++;
            }
            assertTrue(ink > 50, "Header, amount and footer must all be printed before feeding");
        }
        var other = AtmReceiptPrint.print(200);
        int changed = 0;
        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
            if (image.getRGB(x, y) != other.getRGB(x, y)) {
                assertTrue(y >= 145 && y <= 175, "Only the withdrawal amount should differ between receipts");
                changed++;
            }
        }
        assertTrue(changed > 0);
        var output = Path.of(System.getProperty("ubs.testBuildDir", "build"), "test-artifacts/atm-printed-receipt.png");
        Files.createDirectories(output.getParent());
        ImageIO.write(image, "png", output.toFile());
    }
}
