package com.atir.molecularmanipulator.client.render;

import java.nio.ByteBuffer;
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryUtil;

/** Render-thread source staging. A busy GPU slot falls back to the existing orphaned upload. */
final class EffectSourceStream implements AutoCloseable {
    private static final int MAX_SLOT_BYTES = 8 * 1024 * 1024;
    private static final int FLAGS = GL30.GL_MAP_WRITE_BIT | GL44.GL_MAP_PERSISTENT_BIT | GL44.GL_MAP_COHERENT_BIT;
    private final Slot[] slots = {new Slot(), new Slot(), new Slot()};
    private Slot current;
    private int next;

    /** Caller restores its GL_SHADER_STORAGE_BUFFER binding. Zero selects its original glBufferData path. */
    int upload(ByteBuffer source) {
        current = null;
        if (!GL.getCapabilities().OpenGL44
                || source.remaining() > MAX_SLOT_BYTES) return 0;
        for (int offset = 0; offset < slots.length; offset++) {
            int index = (next + offset) % slots.length;
            Slot slot = slots[index];
            if (!slot.ready()) continue;
            slot.ensure(source.remaining());
            MemoryUtil.memCopy(MemoryUtil.memAddress(source), MemoryUtil.memAddress(slot.mapping), source.remaining());
            // ARB_buffer_storage: coherent client writes are visible to subsequent GL commands.
            // Slot fences still prevent overwriting data that an earlier dispatch is reading.
            current = slot;
            next = (index + 1) % slots.length;
            return slot.buffer;
        }
        return 0;
    }

    void dispatched() {
        if (current != null) {
            current.fence = GL32.glFenceSync(GL32.GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
            current = null;
        }
    }

    @Override public void close() {
        int previous = GL11.glGetInteger(GL43.GL_SHADER_STORAGE_BUFFER_BINDING);
        try { for (Slot slot : slots) slot.close(); }
        finally { GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, previous); }
    }

    private static final class Slot implements AutoCloseable {
        int buffer;
        ByteBuffer mapping;
        long fence;

        boolean ready() {
            if (fence == 0) return true;
            int state = GL32.glClientWaitSync(fence, 0, 0);
            if (state != GL32.GL_ALREADY_SIGNALED && state != GL32.GL_CONDITION_SATISFIED) return false;
            GL32.glDeleteSync(fence); fence = 0;
            return true;
        }

        void ensure(int bytes) {
            if (mapping != null && mapping.capacity() >= bytes) return;
            close();
            int capacity = Math.max(65536, Integer.highestOneBit(bytes - 1) << 1);
            buffer = GL15.glGenBuffers();
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, buffer);
            GL44.glBufferStorage(GL43.GL_SHADER_STORAGE_BUFFER, capacity, FLAGS);
            mapping = GL30.glMapBufferRange(GL43.GL_SHADER_STORAGE_BUFFER, 0, capacity, FLAGS);
            if (mapping == null) throw new IllegalStateException("Cannot map effect source stream");
        }

        @Override public void close() {
            if (fence != 0) { GL32.glDeleteSync(fence); fence = 0; }
            if (buffer == 0) return;
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, buffer);
            if (mapping != null) { GL15.glUnmapBuffer(GL43.GL_SHADER_STORAGE_BUFFER); mapping = null; }
            GL15.glDeleteBuffers(buffer); buffer = 0;
        }
    }
}
