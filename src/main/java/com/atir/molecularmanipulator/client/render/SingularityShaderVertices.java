package com.atir.molecularmanipulator.client.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

/** Completes color-only quads with the entity attributes required by Iris shader packs. */
public final class SingularityShaderVertices implements VertexConsumer {
    /** Squared cross-product magnitude below which a triangle cannot provide a stable normal. */
    private static final float DEGENERATE = 1.0E-12F;
    private static final int OVERLAY = OverlayTexture.NO_OVERLAY;
    private static final int LIGHT = LightTexture.FULL_BRIGHT;

    private final VertexConsumer target;
    private final PreparedEffectVertices prepared;
    /** Native upload boundary; layout capture and all target writes remain on the render thread. */
    @FunctionalInterface
    interface NativeBatch {
        void push(MemoryStack stack, long address, int vertices);
    }

    private final NativeBatch batch;
    private final SingularityVertexLayout layout;
    private final long entityIds;
    private final boolean recalculateNormals;
    private final boolean compact;
    // Pose-transformed callers otherwise allocate a Vector3f for every vertex.
    private final Vector3f transformedPosition = new Vector3f();
    // Corners stay in scalar fields so preparing a quad never allocates; the
    // fourth color both completes and flushes the quad at the shared normal.
    private float x0, y0, z0, x1, y1, z1, x2, y2, z2, x3, y3, z3;
    private int color0, color1, color2, color3;
    private int count;
    private Batch pending;

    public SingularityShaderVertices(VertexConsumer target) { this(target, true); }

    /** Ordinary submission is retained for wrapped consumers and attribute comparisons. */
    SingularityShaderVertices(VertexConsumer target, boolean nativeBatch) {
        this(target, nativeBatch, false, 0);
    }

    /** Compact GPU inputs capture their material on the render thread. */
    SingularityShaderVertices(VertexConsumer target, long materials) {
        this(target, true, true, materials);
    }

    private SingularityShaderVertices(VertexConsumer target, boolean nativeBatch, boolean compact, long materials) {
        this.target = target;
        this.compact = compact;
        prepared = null;
        var writer = nativeBatch ? SodiumVertexBatch.of(target) : null;
        layout = writer == null ? null : SingularityVertexLayout.of(writer.format());
        batch = layout == null ? null : writer::push;
        entityIds = compact ? materials : layout == null ? 0 : layout.entityIds();
        recalculateNormals = layout != null && layout.recalculateNormals();
    }

    /** Captured layout and material state let a worker encode without reading Iris or touching a builder. */
    SingularityShaderVertices(PreparedEffectVertices prepared, SingularityVertexLayout.State state) {
        target = null;
        batch = null;
        this.prepared = prepared;
        compact = false;
        layout = state.layout();
        entityIds = state.entityIds();
        recalculateNormals = state.recalculateNormals();
    }

    /** Captured native sink, also usable without a live GL context or an Iris builder. */
    SingularityShaderVertices(SingularityVertexLayout.State state, NativeBatch batch) {
        target = null;
        prepared = null;
        layout = state.layout();
        entityIds = state.entityIds();
        recalculateNormals = state.recalculateNormals();
        compact = layout.uv < 0;
        this.batch = java.util.Objects.requireNonNull(batch);
    }

    boolean usesNativeBatch() { return layout != null; }
    boolean acceptsPreparedColor() { return batch != null && layout.uv < 0; }

    int preparedColorStride() { return acceptsPreparedColor() ? layout.stride : 16; }
    long preparedColorMaterials() { return entityIds; }

    boolean submitColor(long address, int vertices) { return submitColor(address, vertices, 16, 0); }

    boolean submitColor(long address, int vertices, int sourceStride, long sourceMaterials) {
        if (!acceptsPreparedColor()) return false;
        if (count != 0) throw new IllegalStateException("Prepared vertices cannot interrupt a partial quad");
        if (pending != null) pending.flush();
        try (var stack = MemoryStack.stackPush()) {
            if (layout.stride == sourceStride && (sourceStride == 16 || sourceMaterials == entityIds))
                batch.push(stack, address, vertices);
            else {
                int capacity = Math.min(vertices, Batch.CAPACITY);
                long packed = stack.nmalloc(4, capacity * layout.stride);
                for (int from = 0; from < vertices; from += capacity) {
                    int size = Math.min(capacity, vertices - from);
                    for (int index = 0; index < size; index++) {
                        long source = address + (long) (from + index) * sourceStride;
                        long output = packed + (long) index * layout.stride;
                        MemoryUtil.memPutLong(output, MemoryUtil.memGetLong(source));
                        MemoryUtil.memPutLong(output + 8, MemoryUtil.memGetLong(source + 8));
                        if (layout.stride > 16) MemoryUtil.memPutLong(output + 16, entityIds);
                    }
                    batch.push(stack, packed, size);
                }
            }
        }
        return true;
    }

    boolean submitPrepared(SingularityVertexLayout source, long ids, boolean normals, long address, int vertices) {
        if (batch == null) return false;
        if (layout != source || entityIds != ids || recalculateNormals != normals) {
            // Cold start and target changes may leave full CPU attributes for a compact GPU
            // consumer. All supported source formats share this XYZ/color prefix. Batch-pack
            // once, using the CURRENT target material, instead of replaying/completing quads.
            return source.position == 0 && source.color == 12
                    && submitColor(address, vertices, source.stride, ids);
        }
        if (count != 0) throw new IllegalStateException("Prepared vertices cannot interrupt a partial quad");
        if (pending != null) pending.flush();
        try (var stack = MemoryStack.stackPush()) { batch.push(stack, address, vertices); }
        return true;
    }

    /**
     * Groups complete quads within one render pass. Close before requesting another
     * buffer or appending through the underlying consumer; either can end this builder.
     * Consumers without the native path retain immediate submission.
     */
    public static Batch batch(VertexConsumer consumer) {
        return consumer instanceof SingularityShaderVertices vertices && vertices.batch != null
                && vertices.pending == null ? new Batch(vertices) : Batch.EMPTY;
    }

    public static final class Batch implements AutoCloseable {
        private static final int CAPACITY = 128 * 4;
        private static final Batch EMPTY = new Batch();
        private final SingularityShaderVertices consumer;
        private final MemoryStack stack;
        private final long address;
        private int count;

        private Batch() { consumer = null; stack = null; address = 0; }
        private Batch(SingularityShaderVertices consumer) {
            this.consumer = consumer;
            stack = MemoryStack.stackPush();
            address = stack.nmalloc(4, consumer.layout.stride * CAPACITY);
            consumer.pending = this;
        }

        private void flush() {
            if (count == 0) return;
            int vertices = count;
            // A failed append may have changed the builder, so never replay it on close.
            count = 0;
            consumer.batch.push(stack, address, vertices);
        }

        @Override public void close() {
            if (consumer == null) return;
            try { flush(); }
            finally { consumer.pending = null; stack.close(); }
        }
    }

    @Override public VertexConsumer addVertex(float x, float y, float z) {
        switch (count) {
            case 0 -> { x0 = x; y0 = y; z0 = z; }
            case 1 -> { x1 = x; y1 = y; z1 = z; }
            case 2 -> { x2 = x; y2 = y; z2 = z; }
            default -> { x3 = x; y3 = y; z3 = z; }
        }
        return this;
    }

    /** Keep JOML's exact transform but reuse its destination across vertices. */
    @Override public VertexConsumer addVertex(Matrix4f pose, float x, float y, float z) {
        pose.transformPosition(x, y, z, transformedPosition);
        return addVertex(transformedPosition.x, transformedPosition.y, transformedPosition.z);
    }

    /** Callers already provide ARGB, so avoid splitting and repacking it per vertex. */
    @Override public VertexConsumer setColor(int color) {
        switch (count) {
            case 0 -> color0 = color;
            case 1 -> color1 = color;
            case 2 -> color2 = color;
            default -> color3 = color;
        }
        if (++count == 4) emitQuad();
        return this;
    }

    @Override public VertexConsumer setColor(int r, int g, int b, int a) {
        return setColor(a << 24 | r << 16 | g << 8 | b);
    }

    private void emitQuad() {
        if (compact || layout != null && (layout.uv < 0 || recalculateNormals)) {
            // Iris replaces the supplied normal in world rendering. Calculate its
            // diagonal normal directly, without first normalizing an unused triangle.
            emit(0F, 0F, 0F);
            return;
        }
        // Derive the normal from transformed corners so winding survives poses.
        float ox = x0, oy = y0, oz = z0;
        float e1x = x1 - ox, e1y = y1 - oy, e1z = z1 - oz;
        float e2x = x2 - ox, e2y = y2 - oy, e2z = z2 - oz;
        float nx = e1y * e2z - e1z * e2y;
        float ny = e1z * e2x - e1x * e2z;
        float nz = e1x * e2y - e1y * e2x;
        float lengthSquared = nx * nx + ny * ny + nz * nz;
        if (lengthSquared < DEGENERATE) {
            // A collinear first triangle may still leave a usable second one.
            e1x = e2x; e1y = e2y; e1z = e2z;
            e2x = x3 - ox; e2y = y3 - oy; e2z = z3 - oz;
            nx = e1y * e2z - e1z * e2y;
            ny = e1z * e2x - e1x * e2z;
            nz = e1x * e2y - e1y * e2x;
            lengthSquared = nx * nx + ny * ny + nz * nz;
            if (lengthSquared < DEGENERATE) {
                emit(0.0F, 1.0F, 0.0F);
                return;
            }
        }
        float inverseLength = 1.0F / (float) Math.sqrt(lengthSquared);
        emit(nx * inverseLength, ny * inverseLength, nz * inverseLength);
    }

    private void emit(float nx, float ny, float nz) {
        if (layout != null) {
            emitNative(nx, ny, nz);
        } else if (compact) {
            compactVertex(x0, y0, z0, color0);
            compactVertex(x1, y1, z1, color1);
            compactVertex(x2, y2, z2, color2);
            compactVertex(x3, y3, z3, color3);
        } else {
            target.addVertex(x0, y0, z0, color0, 0.0F, 0.0F, OVERLAY, LIGHT, nx, ny, nz);
            target.addVertex(x1, y1, z1, color1, 1.0F, 0.0F, OVERLAY, LIGHT, nx, ny, nz);
            target.addVertex(x2, y2, z2, color2, 1.0F, 1.0F, OVERLAY, LIGHT, nx, ny, nz);
            target.addVertex(x3, y3, z3, color3, 0.0F, 1.0F, OVERLAY, LIGHT, nx, ny, nz);
        }
        count = 0;
    }

    private void compactVertex(float x, float y, float z, int color) {
        target.addVertex(x, y, z).setColor(color)
                .setUv1((int) entityIds & 65535, (int) (entityIds >>> 16) & 65535)
                .setUv2((int) (entityIds >>> 32) & 65535, 0);
    }

    private void emitNative(float nx, float ny, float nz) {
        int normal = pack(nx, ny, nz, 0F), tangent = 0;
        if (layout.tangent >= 0) {
            // Iris uses the two diagonals, including for nonplanar and triangle-like quads.
            // Its normal can differ from the adapter's first-triangle fallback.
            float ax = x2 - x0, ay = y2 - y0, az = z2 - z0;
            float bx = x3 - x1, by = y3 - y1, bz = z3 - z1;
            float fx = ay * bz - az * by, fy = az * bx - ax * bz, fz = ax * by - ay * bx;
            transformedPosition.set(fx, fy, fz).normalize();
            fx = transformedPosition.x; fy = transformedPosition.y; fz = transformedPosition.z;
            if (recalculateNormals) normal = pack(fx, fy, fz, 0F);

            // With the adapter's fixed corner UVs, Iris's tangent is edge 0→1 and
            // its bitangent is edge 0→2 minus edge 0→1. Keep its zero-length rule.
            float tx = x1 - x0, ty = y1 - y0, tz = z1 - z0;
            float ux = ax - tx, uy = ay - ty, uz = az - tz;
            float ti = inverseLength(tx, ty, tz), ui = inverseLength(ux, uy, uz);
            tx *= ti; ty *= ti; tz *= ti;
            ux *= ui; uy *= ui; uz *= ui;
            float dot = ux * (ty * fz - tz * fy) + uy * (tz * fx - tx * fz) + uz * (tx * fy - ty * fx);
            tangent = pack(tx, ty, tz, dot < 0F ? -1F : 1F);
        }
        if (prepared != null) {
            writeQuad(prepared.quad(), normal, tangent);
        } else if (pending != null) {
            writeQuad(pending.address + (long) pending.count * layout.stride, normal, tangent);
            pending.count += 4;
            if (pending.count == Batch.CAPACITY) pending.flush();
        } else {
            // Callers without an explicit pass scope keep the immediate lifecycle.
            try (var stack = MemoryStack.stackPush()) {
                long vertices = stack.nmalloc(4, layout.stride * 4);
                writeQuad(vertices, normal, tangent);
                batch.push(stack, vertices, 4);
            }
        }
    }

    private void writeQuad(long vertices, int normal, int tangent) {
        vertex(vertices, x0, y0, z0, color0, 0F, 0F, normal, tangent);
        vertex(vertices + layout.stride, x1, y1, z1, color1, 1F, 0F, normal, tangent);
        vertex(vertices + layout.stride * 2L, x2, y2, z2, color2, 1F, 1F, normal, tangent);
        vertex(vertices + layout.stride * 3L, x3, y3, z3, color3, 0F, 1F, normal, tangent);
    }

    private void vertex(long pointer, float x, float y, float z, int color, float u, float v, int normal, int tangent) {
        MemoryUtil.memPutFloat(pointer + layout.position, x);
        MemoryUtil.memPutFloat(pointer + layout.position + 4, y);
        MemoryUtil.memPutFloat(pointer + layout.position + 8, z);
        MemoryUtil.memPutInt(pointer + layout.color, color & 0xFF00FF00 | color >>> 16 & 255 | (color & 255) << 16);
        if (layout.uv < 0) {
            if (layout.overlay >= 0) MemoryUtil.memPutLong(pointer + layout.overlay, entityIds);
            return;
        }
        MemoryUtil.memPutFloat(pointer + layout.uv, u);
        MemoryUtil.memPutFloat(pointer + layout.uv + 4, v);
        MemoryUtil.memPutInt(pointer + layout.overlay, OVERLAY);
        MemoryUtil.memPutInt(pointer + layout.light, LIGHT);
        MemoryUtil.memPutInt(pointer + layout.normal, normal);
        if (layout.tangent >= 0) {
            MemoryUtil.memPutInt(pointer + layout.entities, (int) entityIds);
            MemoryUtil.memPutShort(pointer + layout.entities + 4, (short) (entityIds >>> 32));
            MemoryUtil.memPutFloat(pointer + layout.midUv, .5F);
            MemoryUtil.memPutFloat(pointer + layout.midUv + 4, .5F);
            MemoryUtil.memPutInt(pointer + layout.tangent, tangent);
        }
    }

    private static float inverseLength(float x, float y, float z) {
        float squared = x * x + y * y + z * z;
        return squared == 0F ? 1F : (float) (1D / Math.sqrt(squared));
    }

    private static int pack(float x, float y, float z, float w) {
        return (int) (x * 127F) & 255 | ((int) (y * 127F) & 255) << 8
                | ((int) (z * 127F) & 255) << 16 | ((int) (w * 127F) & 255) << 24;
    }

    @Override public VertexConsumer setUv(float u, float v) { return this; }
    @Override public VertexConsumer setUv1(int u, int v) { return this; }
    @Override public VertexConsumer setUv2(int u, int v) { return this; }
    @Override public VertexConsumer setNormal(float x, float y, float z) { return this; }
}
