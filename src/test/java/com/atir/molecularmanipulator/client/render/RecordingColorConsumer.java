package com.atir.molecularmanipulator.client.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;

/** Rejects any attribute that is not part of the internal POSITION_COLOR format. */
public final class RecordingColorConsumer implements VertexConsumer {
    private final List<Vertex> vertices = new ArrayList<>();
    private float x;
    private float y;
    private float z;
    private boolean pending;

    @Override
    public VertexConsumer vertex(double x, double y, double z) {
        if (pending) throw new AssertionError("Previous vertex has no color");
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            throw new AssertionError("Non-finite vertex");
        }
        this.x = (float) x;
        this.y = (float) y;
        this.z = (float) z;
        pending = true;
        return this;
    }

    @Override
    public VertexConsumer color(int red, int green, int blue, int alpha) {
        if (!pending) throw new AssertionError("Color without a position");
        vertices.add(new Vertex(x, y, z, alpha << 24 | red << 16 | green << 8 | blue));
        pending = false;
        return this;
    }

    @Override
    public VertexConsumer uv(float u, float v) {
        throw new AssertionError("UV is not in POSITION_COLOR");
    }

    @Override
    public VertexConsumer overlayCoords(int u, int v) {
        throw new AssertionError("Overlay is not in POSITION_COLOR");
    }

    @Override
    public VertexConsumer uv2(int u, int v) {
        throw new AssertionError("Lightmap is not in POSITION_COLOR");
    }

    @Override
    public VertexConsumer normal(float x, float y, float z) {
        throw new AssertionError("Normal is not in POSITION_COLOR");
    }

    @Override public void endVertex() { if (pending) throw new AssertionError("Vertex has no color"); }
    @Override public void defaultColor(int r, int g, int b, int a) { throw new AssertionError("Unexpected default color"); }
    @Override public void unsetDefaultColor() {}

    public List<Vertex> vertices() {
        if (pending) throw new AssertionError("Final vertex has no color");
        return vertices;
    }

    public record Vertex(float x, float y, float z, int argb) {
        public int rgb() {
            return argb & 0xFFFFFF;
        }

        public int alpha() {
            return argb >>> 24;
        }

        public double radius() {
            return Math.sqrt(x * x + y * y + z * z);
        }
    }
}
