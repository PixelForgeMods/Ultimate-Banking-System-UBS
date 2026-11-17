package net.austizz.ultimatebankingsystem.client;

import java.util.ArrayList;
import java.util.List;

final class AtmCursorShape {
    record Vertex(float x, float y, int alpha) {}

    static final List<Vertex> IDLE = build(false);
    static final List<Vertex> HOVER = build(true);

    private AtmCursorShape() {}

    private static List<Vertex> build(boolean hover) {
        var vertices = new ArrayList<Vertex>();
        ring(vertices, hover ? 6 : 6.1F, 7.5F);
        if (hover) ring(vertices, 3.95F, 5.05F);
        float dot = hover ? 1.65F : 1.35F;
        band(vertices, 0, dot, 255, 255);
        band(vertices, dot, dot + .2F, 255, 0);
        return List.copyOf(vertices);
    }

    private static void ring(List<Vertex> vertices, float inner, float outer) {
        band(vertices, inner - .2F, inner, 0, 255);
        band(vertices, inner, outer, 255, 255);
        band(vertices, outer, outer + .2F, 255, 0);
    }

    private static void band(List<Vertex> vertices, float inner, float outer, int innerAlpha, int outerAlpha) {
        for (int segment = 0; segment < 64; segment++) {
            double start = segment * Math.PI / 32, end = (segment + 1) * Math.PI / 32;
            vertices.add(vertex(inner, start, innerAlpha));
            vertices.add(vertex(inner, end, innerAlpha));
            vertices.add(vertex(outer, end, outerAlpha));
            vertices.add(vertex(outer, start, outerAlpha));
        }
    }

    private static Vertex vertex(float radius, double angle, int alpha) {
        return new Vertex((float) Math.cos(angle) * radius, (float) Math.sin(angle) * radius, alpha);
    }
}
