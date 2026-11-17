package net.austizz.ultimatebankingsystem.client;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AtmCursorShapeTest {
    @Test
    void bothCursorsHaveThickRingsAndDotsButKeepTransparentGaps() {
        for (var shape : List.of(AtmCursorShape.IDLE, AtmCursorShape.HOVER)) {
            assertTrue(opaqueAt(shape, 6.5, 0), "Outer ring must be thicker than the old hairline");
            assertTrue(opaqueAt(shape, 1.2, 0), "Center dot must remain clearly visible");
            assertFalse(opaqueAt(shape, 5.5, 0), "Keep the space between rings clear");
            assertFalse(opaqueAt(shape, 2.5, 0), "Keep the space around the dot clear");
            assertFalse(opaqueAt(shape, 7.9, 0), "Do not add a border beyond the ring");
        }
        assertFalse(opaqueAt(AtmCursorShape.IDLE, 4.5, 0));
        assertTrue(opaqueAt(AtmCursorShape.HOVER, 4.5, 0));
        assertFalse(opaqueAt(AtmCursorShape.IDLE, 1.5, 0));
        assertTrue(opaqueAt(AtmCursorShape.HOVER, 1.5, 0));
    }

    @Test
    void antialiasingUsesAlphaOnlyAndBothMeshesFitTheSameCursorBounds() {
        for (var shape : List.of(AtmCursorShape.IDLE, AtmCursorShape.HOVER)) {
            assertEquals(0, shape.size() % 4);
            assertTrue(shape.stream().anyMatch(v -> v.alpha() == 0));
            assertTrue(shape.stream().allMatch(v -> v.alpha() == 0 || v.alpha() == 255));
            double radius = shape.stream().mapToDouble(v -> Math.hypot(v.x(), v.y())).max().orElseThrow();
            assertEquals(7.7, radius, 1e-5);
        }
    }

    private static boolean opaqueAt(List<AtmCursorShape.Vertex> mesh, double x, double y) {
        for (int i = 0; i < mesh.size(); i += 4) {
            boolean positive = false, negative = false, opaque = true;
            for (int j = 0; j < 4; j++) {
                var a = mesh.get(i + j);
                var b = mesh.get(i + (j + 1) % 4);
                opaque &= a.alpha() == 255;
                double cross = (b.x() - a.x()) * (y - a.y()) - (b.y() - a.y()) * (x - a.x());
                positive |= cross > 1e-7;
                negative |= cross < -1e-7;
            }
            if (opaque && !(positive && negative)) return true;
        }
        return false;
    }
}
