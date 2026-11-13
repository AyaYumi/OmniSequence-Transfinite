package com.atir.molecularmanipulator.client.render;

import com.mojang.blaze3d.platform.GlStateManager;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryStack;

/** Only the orrery compute paths use this scope; restore pack-owned bindings before any graphics draw. */
final class StarOrreryComputeBindings implements AutoCloseable {
    private final int program, buffer, indirect;
    private final boolean dispatchIndirect;
    private final IntBuffer bindings;
    private final LongBuffer starts, sizes;

    StarOrreryComputeBindings(MemoryStack stack, int count, boolean dispatchIndirect) {
        this.dispatchIndirect = dispatchIndirect;
        program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        buffer = GL11.glGetInteger(GL43.GL_SHADER_STORAGE_BUFFER_BINDING);
        indirect = dispatchIndirect ? GL11.glGetInteger(GL43.GL_DISPATCH_INDIRECT_BUFFER_BINDING) : 0;
        bindings = stack.mallocInt(count);
        starts = stack.mallocLong(count);
        sizes = stack.mallocLong(count);
        for (int index = 0; index < count; index++) {
            int binding = GL30.glGetIntegeri(GL43.GL_SHADER_STORAGE_BUFFER_BINDING, index);
            bindings.put(index, binding);
            // An unbound slot cannot have a range. Avoid two driver queries for each such slot.
            starts.put(index, binding == 0 ? 0L : GL32.glGetInteger64i(GL43.GL_SHADER_STORAGE_BUFFER_START, index));
            sizes.put(index, binding == 0 ? 0L : GL32.glGetInteger64i(GL43.GL_SHADER_STORAGE_BUFFER_SIZE, index));
        }
    }

    @Override public void close() {
        for (int index = 0; index < bindings.capacity(); index++) {
            if (bindings.get(index) != 0 && sizes.get(index) > 0)
                GL30.glBindBufferRange(GL43.GL_SHADER_STORAGE_BUFFER, index, bindings.get(index),
                        starts.get(index), sizes.get(index));
            else GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, index, bindings.get(index));
        }
        if (dispatchIndirect) GlStateManager._glBindBuffer(GL43.GL_DISPATCH_INDIRECT_BUFFER, indirect);
        // Indexed binds also change the generic binding, so restore it last.
        GlStateManager._glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, buffer);
        GlStateManager._glUseProgram(program);
    }
}
