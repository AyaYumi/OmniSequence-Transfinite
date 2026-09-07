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
    public VertexConsumer addVertex(float x, float y, float z) {
        if (pending) throw new AssertionError("Previous vertex has no color");
        if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)) {
            throw new AssertionError("Non-finite vertex");
        }
        this.x = x;
        this.y = y;
        this.z = z;
        pending = true;
        return this;
    }

    @Override
    public VertexConsumer setColor(int red, int green, int blue, int alpha) {
        if (!pending) throw new AssertionError("Color without a position");
        vertices.add(new Vertex(x, y, z, alpha << 24 | red << 16 | green << 8 | blue));
        pending = false;
        return this;
    }

    @Override
    public VertexConsumer setUv(float u, float v) {
        throw new AssertionError("UV is not in POSITION_COLOR");
    }

    @Override
    public VertexConsumer setUv1(int u, int v) {
        throw new AssertionError("Overlay is not in POSITION_COLOR");
    }

    @Override
    public VertexConsumer setUv2(int u, int v) {
        throw new AssertionError("Lightmap is not in POSITION_COLOR");
    }

    @Override
    public VertexConsumer setNormal(float x, float y, float z) {
        throw new AssertionError("Normal is not in POSITION_COLOR");
    }

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
