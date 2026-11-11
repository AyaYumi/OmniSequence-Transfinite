package com.atir.molecularmanipulator.client.render;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.ByteBuffer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryUtil;

/** Opt in with -DstarOrrery.gpuTests=true; requires an isolated, invisible OpenGL 4.4 context. */
@EnabledIfSystemProperty(named = "starOrrery.gpuTests", matches = "true")
class EffectSourceStreamTest {
    private static long window;
    private static int program, countUniform, offsetUniform;

    @BeforeAll static void openContext() {
        assertTrue(GLFW.glfwInit(), "GLFW is unavailable");
        GLFW.glfwDefaultWindowHints();
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 4);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 4);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE);
        window = GLFW.glfwCreateWindow(32, 32, "Effect source stream contracts", 0, 0);
        assertNotEquals(0L, window, "OpenGL 4.4 is unavailable");
        GLFW.glfwMakeContextCurrent(window);
        assertTrue(GL.createCapabilities().OpenGL44, "OpenGL 4.4 is required");
        int shader = GL20.glCreateShader(GL43.GL_COMPUTE_SHADER);
        program = GL20.glCreateProgram();
        try {
            GL20.glShaderSource(shader, """
                    #version 440 core
                    layout(local_size_x = 64) in;
                    layout(std430, binding = 0) readonly buffer Source { uint source[]; };
                    layout(std430, binding = 1) writeonly buffer Result { uint result[]; };
                    uniform uint Count;
                    uniform uint OutputOffset;
                    void main() {
                        uint i = gl_GlobalInvocationID.x;
                        if (i < Count) result[OutputOffset + i] = source[i] ^ 0xA53C97E1u;
                    }
                    """);
            GL20.glCompileShader(shader);
            assertEquals(GL11.GL_TRUE, GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS), GL20.glGetShaderInfoLog(shader));
            GL20.glAttachShader(program, shader);
            GL20.glLinkProgram(program);
            assertEquals(GL11.GL_TRUE, GL20.glGetProgrami(program, GL20.GL_LINK_STATUS), GL20.glGetProgramInfoLog(program));
        } finally { GL20.glDeleteShader(shader); }
        countUniform = GL20.glGetUniformLocation(program, "Count");
        offsetUniform = GL20.glGetUniformLocation(program, "OutputOffset");
        assertTrue(countUniform >= 0 && offsetUniform >= 0);
        assertNoGlError();
    }

    @AfterAll static void closeContext() {
        if (window != 0) {
            GL20.glUseProgram(0);
            if (program != 0) GL20.glDeleteProgram(program);
            GL.setCapabilities(null);
            GLFW.glfwMakeContextCurrent(0);
            GLFW.glfwDestroyWindow(window);
        }
        GLFW.glfwTerminate();
    }

    @Test void computeReadsCoherentUploadsAcrossSlotReuseAndGrowth() throws ReflectiveOperationException {
        int maxWords = 16385, prefix = 12;
        ByteBuffer source = MemoryUtil.memAlloc(prefix + maxWords * Integer.BYTES + 8);
        int output = GL15.glGenBuffers();
        try (var stream = new EffectSourceStream()) {
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, output);
            GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER, 3L * maxWords * Integer.BYTES, GL15.GL_STREAM_READ);
            GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 1, output);
            GL20.glUseProgram(program);
            int[] previousBuffers = new int[3];
            for (int round = 0; round < 5; round++) {
                // Cross the initial 64 KiB capacity, then reuse both grown and smaller uploads.
                int words = round < 2 || round == 4 ? 129 : maxWords;
                int[][] expected = new int[3][words];
                long[] previousFences = fences(stream);
                for (int slot = 0; slot < 3; slot++) {
                    source.clear().position(prefix).limit(prefix + words * Integer.BYTES);
                    for (int i = 0; i < words; i++) {
                        int value = (round * 3 + slot + 1) * 0x1F123BB5 ^ i * 0x9E3779B9;
                        source.putInt(prefix + i * Integer.BYTES, value);
                        expected[slot][i] = value ^ 0xA53C97E1;
                    }
                    int buffer = stream.upload(source);
                    assertNotEquals(0, buffer, "A completed slot must be reusable");
                    assertEquals(prefix, source.position());
                    assertEquals(prefix + words * Integer.BYTES, source.limit());
                    if (round != 0 && round != 2) assertEquals(previousBuffers[slot], buffer, "Reuse existing storage");
                    previousBuffers[slot] = buffer;
                    if (previousFences[slot] != 0) assertFalse(GL32.glIsSync(previousFences[slot]), "Retire the completed fence");
                    GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 0, buffer);
                    GL30.glUniform1ui(countUniform, words);
                    GL30.glUniform1ui(offsetUniform, slot * maxWords);
                    // No barrier, flush or wait between the CPU upload and its compute reader.
                    GL43.glDispatchCompute((words + 63) / 64, 1, 1);
                    stream.dispatched();
                    long[] dispatchedFences = fences(stream);
                    assertNotEquals(0L, dispatchedFences[slot]);
                    assertTrue(GL32.glIsSync(dispatchedFences[slot]));
                    stream.dispatched();
                    assertArrayEquals(dispatchedFences, fences(stream), "Do not replace a fence on a second dispatched call");
                }
                // Only shader -> CPU readback needs a barrier; this is not part of the upload path.
                GL43.glMemoryBarrier(GL43.GL_BUFFER_UPDATE_BARRIER_BIT);
                GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, output);
                for (int slot = 0; slot < 3; slot++) {
                    int[] actual = new int[words];
                    GL15.glGetBufferSubData(GL43.GL_SHADER_STORAGE_BUFFER, (long) slot * maxWords * Integer.BYTES, actual);
                    assertArrayEquals(expected[slot], actual, "round=" + round + ", slot=" + slot);
                }
                GL11.glFinish(); // Make the next round's fence retirement deterministic.
            }
            assertNoGlError();
        } finally {
            GL15.glDeleteBuffers(output);
            MemoryUtil.memFree(source);
        }
    }

    @Test void oversizedUploadSelectsFallbackWithoutChangingSourceBindingOrFences() throws ReflectiveOperationException {
        int prefix = 8;
        ByteBuffer source = MemoryUtil.memAlloc(prefix + 8 * 1024 * 1024 + 1);
        int sentinel = GL15.glGenBuffers();
        try (var stream = new EffectSourceStream()) {
            source.position(prefix).limit(prefix + Integer.BYTES);
            assertNotEquals(0, stream.upload(source));
            stream.dispatched();
            assertNotEquals(0, stream.upload(source)); // Leave a current slot to catch stale fencing after fallback.
            long[] before = fences(stream);
            source.limit(source.capacity());
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, sentinel);
            assertEquals(0, stream.upload(source));
            assertEquals(prefix, source.position());
            assertEquals(source.capacity(), source.limit());
            assertEquals(sentinel, GL11.glGetInteger(GL43.GL_SHADER_STORAGE_BUFFER_BINDING));
            stream.dispatched();
            assertArrayEquals(before, fences(stream), "Fallback must neither retire nor create slot fences");
            assertTrue(GL32.glIsSync(before[0]));
            assertNoGlError();
        } finally {
            GL15.glDeleteBuffers(sentinel);
            MemoryUtil.memFree(source);
        }
    }

    @Test void closeDeletesSlotBuffersAndFencesAndPreservesExternalGlState() throws ReflectiveOperationException {
        ByteBuffer source = MemoryUtil.memCalloc(Integer.BYTES);
        int[] external = new int[3], uploaded = new int[3];
        GL15.glGenBuffers(external);
        try (var stream = new EffectSourceStream()) {
            for (int slot = 0; slot < 3; slot++) {
                uploaded[slot] = stream.upload(source);
                assertNotEquals(0, uploaded[slot]);
                stream.dispatched();
            }
            long[] pendingFences = fences(stream);
            int alignment = GL11.glGetInteger(GL43.GL_SHADER_STORAGE_BUFFER_OFFSET_ALIGNMENT);
            for (int buffer : external) {
                GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, buffer);
                GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER, 4L * alignment, GL15.GL_STREAM_DRAW);
            }
            GL30.glBindBufferRange(GL43.GL_SHADER_STORAGE_BUFFER, 0, external[0], alignment, 2L * alignment);
            GL30.glBindBufferRange(GL43.GL_SHADER_STORAGE_BUFFER, 1, external[1], 2L * alignment, alignment);
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, external[2]);
            GL15.glBindBuffer(GL43.GL_DISPATCH_INDIRECT_BUFFER, external[0]);
            GL20.glUseProgram(program);
            long[] before = bindingState();
            stream.close();
            assertArrayEquals(before, bindingState());
            for (int buffer : uploaded) assertFalse(GL15.glIsBuffer(buffer), "Delete each slot buffer");
            for (long fence : pendingFences) {
                assertNotEquals(0L, fence);
                assertFalse(GL32.glIsSync(fence), "Delete each outstanding fence");
            }
            assertArrayEquals(new long[3], fences(stream));
            for (Object slot : (Object[]) field(stream, "slots")) {
                assertEquals(0, field(slot, "buffer"));
                assertNull(field(slot, "mapping"));
            }
            stream.close();
            assertArrayEquals(before, bindingState(), "Close is idempotent");
            assertNoGlError();
        } finally {
            GL15.glDeleteBuffers(external);
            MemoryUtil.memFree(source);
        }
    }

    private static long[] bindingState() {
        long[] state = new long[9];
        state[0] = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        state[1] = GL11.glGetInteger(GL43.GL_SHADER_STORAGE_BUFFER_BINDING);
        state[2] = GL11.glGetInteger(GL43.GL_DISPATCH_INDIRECT_BUFFER_BINDING);
        for (int slot = 0; slot < 2; slot++) {
            state[3 + slot * 3] = GL30.glGetIntegeri(GL43.GL_SHADER_STORAGE_BUFFER_BINDING, slot);
            state[4 + slot * 3] = GL32.glGetInteger64i(GL43.GL_SHADER_STORAGE_BUFFER_START, slot);
            state[5 + slot * 3] = GL32.glGetInteger64i(GL43.GL_SHADER_STORAGE_BUFFER_SIZE, slot);
        }
        return state;
    }

    // Observe fence ownership without adding production test hooks or timing-dependent GPU workloads.
    private static long[] fences(EffectSourceStream stream) throws ReflectiveOperationException {
        Object[] slots = (Object[]) field(stream, "slots");
        long[] fences = new long[slots.length];
        for (int slot = 0; slot < slots.length; slot++) fences[slot] = (long) field(slots[slot], "fence");
        return fences;
    }

    private static Object field(Object object, String name) throws ReflectiveOperationException {
        var field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(object);
    }

    private static void assertNoGlError() {
        assertEquals(GL11.GL_NO_ERROR, GL11.glGetError());
    }
}
