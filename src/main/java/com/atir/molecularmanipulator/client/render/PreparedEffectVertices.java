package com.atir.molecularmanipulator.client.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import java.nio.ByteBuffer;
import org.lwjgl.system.MemoryUtil;

/** Worker-owned complete vertices. The render thread reads them only after joining the task. */
final class PreparedEffectVertices implements AutoCloseable {
    private ByteBuffer storage;
    private SingularityVertexLayout layout;
    private int count;
    private long entityIds;
    private boolean recalculateNormals;

    SingularityShaderVertices begin(SingularityVertexLayout.State state) {
        layout = state.layout();
        entityIds = state.entityIds();
        recalculateNormals = state.recalculateNormals();
        count = 0;
        return new SingularityShaderVertices(this, state);
    }

    long quad() {
        int bytes = (count + 4) * layout.stride;
        if (storage == null || bytes > storage.capacity()) {
            int capacity = Math.max(65536, Integer.highestOneBit(bytes - 1) << 1);
            storage = storage == null ? MemoryUtil.memAlloc(capacity) : MemoryUtil.memRealloc(storage, capacity);
        }
        long address = MemoryUtil.memAddress(storage) + (long) count * layout.stride;
        count += 4;
        return address;
    }

    void submit(VertexConsumer target) {
        if (count == 0) return;
        if (target instanceof SingularityShaderVertices vertices
                && vertices.submitPrepared(layout, entityIds, recalculateNormals, MemoryUtil.memAddress(storage), count)) return;
        // A wrapped or differently formatted target still receives the same colored geometry.
        long address = MemoryUtil.memAddress(storage);
        for (int vertex = 0; vertex < count; vertex++, address += layout.stride) {
            int color = MemoryUtil.memGetInt(address + layout.color);
            target.vertex(MemoryUtil.memGetFloat(address + layout.position),
                    MemoryUtil.memGetFloat(address + layout.position + 4),
                    MemoryUtil.memGetFloat(address + layout.position + 8))
                    .color(color & 0xFF00FF00 | color >>> 16 & 255 | (color & 255) << 16).endVertex();
        }
    }

    int count() { return count; }

    @Override public void close() {
        if (storage != null) MemoryUtil.memFree(storage);
        storage = null;
        count = 0;
    }
}
