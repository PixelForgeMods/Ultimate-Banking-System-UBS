package net.austizz.ultimatebankingsystem.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AtmTouchTest {
    @Test void touchTargetsMatchAllEightExistingMenuActions() {
        for(int row=0;row<4;row++) {
            assertEquals(16+row,AtmControls.touchIndex(200,251+126*row));
            assertEquals(20+row,AtmControls.touchIndex(800,251+126*row));
        }
        assertEquals(19,AtmControls.touchIndex(160,630));
        assertEquals(23,AtmControls.touchIndex(850,630));
        for(double[] p:new double[][]{{512,250},{0,630},{1024,630},{200,90},{200,720},{Double.NaN,250}})
            assertEquals(-1,AtmControls.touchIndex(p[0],p[1]));
    }
}
