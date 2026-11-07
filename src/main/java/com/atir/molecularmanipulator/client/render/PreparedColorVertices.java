package com.atir.molecularmanipulator.client.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.nio.ByteBuffer;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

/** Worker-owned world-space geometry; GPU effect layers need only position and color as input. */
public final class PreparedColorVertices implements VertexConsumer, AutoCloseable {
    private static final int COLOR_STRIDE = 16;
    private final Vector3f transformed = new Vector3f();
    private ByteBuffer storage;
    private int count, stride = COLOR_STRIDE, nextStride = COLOR_STRIDE;
    private long materials, nextMaterials;

    public VertexConsumer begin() {
        count = 0;
        stride = nextStride;
        materials = nextMaterials;
        return this;
    }

    @Override public VertexConsumer addVertex(float x, float y, float z) {
        int bytes = (count + 1) * stride;
        if (storage == null || bytes > storage.capacity()) {
            int capacity = Math.max(65536, Integer.highestOneBit(bytes - 1) << 1);
            storage = storage == null ? MemoryUtil.memAlloc(capacity) : MemoryUtil.memRealloc(storage, capacity);
        }
        long pointer = MemoryUtil.memAddress(storage) + (long) count * stride;
        MemoryUtil.memPutFloat(pointer, x);
        MemoryUtil.memPutFloat(pointer + 4, y);
        MemoryUtil.memPutFloat(pointer + 8, z);
        return this;
    }

    @Override public VertexConsumer addVertex(Matrix4f pose, float x, float y, float z) {
        pose.transformPosition(x, y, z, transformed);
        return addVertex(transformed.x, transformed.y, transformed.z);
    }

    @Override public VertexConsumer setColor(int color) {
        long pointer = MemoryUtil.memAddress(storage) + (long) count * stride;
        MemoryUtil.memPutInt(pointer + 12,
                color & 0xFF00FF00 | color >>> 16 & 255 | (color & 255) << 16);
        if (stride == 24) MemoryUtil.memPutLong(pointer + 16, materials);
        count++;
        return this;
    }

    @Override public VertexConsumer setColor(int r, int g, int b, int a) {
        return setColor(a << 24 | r << 16 | g << 8 | b);
    }

    public void submit(VertexConsumer target) {
        if (count == 0) return;
        if ((count & 3) != 0) throw new IllegalStateException("Incomplete prepared color quad");
        if (target instanceof SingularityShaderVertices adapter) {
            boolean submitted = adapter.submitColor(MemoryUtil.memAddress(storage), count, stride, materials);
            captureTarget(adapter);
            if (submitted) return;
        } else nextStride = COLOR_STRIDE;
        var writer = SodiumVertexBatch.of(target);
        if (stride == COLOR_STRIDE && writer != null && writer.format() == DefaultVertexFormat.POSITION_COLOR) {
            try (var stack = MemoryStack.stackPush()) { writer.push(stack, MemoryUtil.memAddress(storage), count); }
            return;
        }
        long pointer = MemoryUtil.memAddress(storage);
        for (int vertex = 0; vertex < count; vertex++, pointer += stride) {
            int color = MemoryUtil.memGetInt(pointer + 12);
            target.addVertex(MemoryUtil.memGetFloat(pointer), MemoryUtil.memGetFloat(pointer + 4),
                    MemoryUtil.memGetFloat(pointer + 8))
                    .setColor(color & 0xFF00FF00 | color >>> 16 & 255 | (color & 255) << 16);
        }
    }

    /** Render-thread observation only; begin() on the next worker uses this verified target hint. */
    void captureTarget(VertexConsumer target) {
        if (target instanceof SingularityShaderVertices adapter) {
            nextStride = adapter.preparedColorStride();
            nextMaterials = adapter.preparedColorMaterials();
        } else nextStride = COLOR_STRIDE;
    }

    public int count() { return count; }
    @Override public void close() {
        if (storage != null) MemoryUtil.memFree(storage);
        storage = null; count = 0; stride = nextStride = COLOR_STRIDE; materials = nextMaterials = 0;
    }
    @Override public VertexConsumer setUv(float u, float v) { return this; }
    @Override public VertexConsumer setUv1(int u, int v) { return this; }
    @Override public VertexConsumer setUv2(int u, int v) { return this; }
    @Override public VertexConsumer setNormal(float x, float y, float z) { return this; }
}
