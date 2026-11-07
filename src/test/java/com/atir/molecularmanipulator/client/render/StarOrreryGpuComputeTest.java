package com.atir.molecularmanipulator.client.render;

import static org.junit.jupiter.api.Assertions.*;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

/**
 * Opt in with -DstarOrrery.gpuTests=true; opens an isolated, invisible GL context, never the game.
 * Runtime needs matching platform natives for lwjgl, lwjgl-glfw and lwjgl-opengl on the classpath
 * (or extracted libraries via -Dorg.lwjgl.librarypath). An explicit opt-in requires a working GL 4.3 context.
 */
@EnabledIfSystemProperty(named = "starOrrery.gpuTests", matches = "true")
class StarOrreryGpuComputeTest {
    private static long window;
    private static int rain, rainReference, transform;

    @BeforeAll static void openContextAndCompileTheActualShaders() throws IOException {
        assertTrue(GLFW.glfwInit(), "GLFW is unavailable");
        GLFW.glfwDefaultWindowHints();
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 4);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 3);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE);
        window = GLFW.glfwCreateWindow(32, 32, "Star orrery compute contracts", 0, 0);
        assertNotEquals(0L, window, "OpenGL 4.3 is unavailable");
        GLFW.glfwMakeContextCurrent(window);
        assertTrue(GL.createCapabilities().OpenGL43, "OpenGL 4.3 is required");
        if (!RenderSystem.isOnRenderThread()) RenderSystem.initRenderThread();
        rain = compile("star_orrery_rain.comp");
        rainReference = compile("star_orrery_rain_reference.comp");
        transform = compile("star_orrery_transform.comp");
        assertEquals(GL11.GL_NO_ERROR, GL11.glGetError());
    }

    @AfterAll static void closeContext() {
        if (window != 0) {
            if (rain != 0) GL20.glDeleteProgram(rain);
            if (rainReference != 0) GL20.glDeleteProgram(rainReference);
            if (transform != 0) GL20.glDeleteProgram(transform);
            GlStateManager._glUseProgram(0);
            GL.setCapabilities(null);
            GLFW.glfwDestroyWindow(window);
        }
        GLFW.glfwTerminate();
    }

    // The uncached shader is the GPU reference: driver boundary rounding can differ from Java.
    // RainGeometryTest separately checks the cached algorithm against the complete CPU renderer.
    @Test void rainMatchesUncachedShaderVerticesAndIndirectCountsAtAllTiersLodsAndBoundaryTimes() {
        var templates = MemoryUtil.memCalloc(StarOrreryRainGeometry.TEMPLATE_BYTES);
        int[] buffers = new int[4];
        GL15.glGenBuffers(buffers);
        try {
            StarOrreryRainGeometry.writeTemplates(templates);
            templates.flip();
            upload(buffers[3], templates);
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, buffers[2]);
            GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER, 32L, GL15.GL_DYNAMIC_DRAW);
            var poses = new PoseStack();
            poses.translate(3.25, -17.5, 41.5);
            poses.mulPose(Axis.YP.rotationDegrees(37));
            poses.mulPose(Axis.XP.rotationDegrees(-24));
            poses.scale(-.75F, 1.25F, .4F);
            try (var stack = MemoryStack.stackPush()) {
                for (int program : new int[]{rainReference, rain}) {
                    GL20.glUseProgram(program);
                    GL20.glUniformMatrix4fv(uniform(program, "Pose"), false, poses.last().pose().get(stack.mallocFloat(16)));
                    GL30.glUniform2ui(uniform(program, "Material"), 0x456789AB, 0xCDEF);
                }
                for (int slot = 0; slot < 4; slot++) GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, slot, buffers[slot]);
                for (int lit = 1; lit <= 9; lit++) for (boolean detailed : new boolean[]{false, true}) {
                    var columns = StarOrreryRainGeometry.columns(lit, detailed);
                    var columnBytes = MemoryUtil.memAlloc(columns.size() * StarOrreryRainGeometry.COLUMN_BYTES);
                    try {
                        columns.forEach(column -> column.write(columnBytes));
                        columnBytes.flip();
                        upload(buffers[0], columnBytes);
                    } finally { MemoryUtil.memFree(columnBytes); }
                    for (int program : new int[]{rainReference, rain}) {
                        GL20.glUseProgram(program);
                        GL30.glUniform1ui(uniform(program, "ColumnCount"), columns.size());
                        GL20.glUniform1i(uniform(program, "Detailed"), detailed ? 1 : 0);
                    }
                    for (boolean glow : new boolean[]{false, true}) {
                        int maxBytes = StarOrreryRainGeometry.maxQuads(lit, detailed, glow) * 4 * 24;
                        var reference = MemoryUtil.memAlloc(maxBytes);
                        var encoded = MemoryUtil.memAlloc(maxBytes);
                        try {
                            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, buffers[1]);
                            GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER, maxBytes, GL15.GL_DYNAMIC_DRAW);
                            for (float time : new float[]{-12F, -.5F, 0F, Math.nextDown(.5F), .5F, Math.nextUp(.5F),
                                    3.5F, Math.nextDown(12F), 12F, Math.nextUp(12F), 11999.5F, 12000F}) {
                                String sample = "lit=" + lit + ", detailed=" + detailed + ", glow=" + glow + ", time=" + time;
                                int[] expected = null;
                                for (int program : new int[]{rainReference, rain}) {
                                    GL20.glUseProgram(program);
                                    GL20.glUniform1i(uniform(program, "Glow"), glow ? 1 : 0);
                                    GL20.glUniform1f(uniform(program, "FlowTime"), time);
                                    // Independent initial contents catch missing writes, not only changed writes.
                                    for (int buffer : new int[]{buffers[1], buffers[2]}) {
                                        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, buffer);
                                        GL43.glClearBufferData(GL43.GL_SHADER_STORAGE_BUFFER, GL30.GL_R32UI,
                                                GL30.GL_RED_INTEGER, GL11.GL_UNSIGNED_INT,
                                                new int[]{program == rainReference ? 0x6D6D6D6D : 0x37373737});
                                    }
                                    GL43.glDispatchCompute(1, 1, 1);
                                    // Readback is test-only. Runtime never reads compute output back to the CPU.
                                    GL43.glMemoryBarrier(GL43.GL_BUFFER_UPDATE_BARRIER_BIT | GL43.GL_SHADER_STORAGE_BARRIER_BIT);
                                    int[] indirect = new int[8];
                                    GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, buffers[2]);
                                    GL15.glGetBufferSubData(GL43.GL_SHADER_STORAGE_BUFFER, 0L, indirect);
                                    int liveBytes = indirect[0] / 6 * 4 * 24;
                                    assertEquals(0, indirect[0] % 6, sample);
                                    assertTrue(liveBytes >= 0 && liveBytes <= maxBytes, sample);
                                    GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, buffers[1]);
                                    if (program == rainReference) {
                                        expected = indirect;
                                        reference.clear();
                                        GL15.glGetBufferSubData(GL43.GL_SHADER_STORAGE_BUFFER, 0L, reference);
                                        reference.limit(liveBytes);
                                    } else {
                                        assertArrayEquals(expected, indirect, sample);
                                        encoded.clear();
                                        GL15.glGetBufferSubData(GL43.GL_SHADER_STORAGE_BUFFER, 0L, encoded);
                                        encoded.limit(liveBytes);
                                        assertEquals(-1, reference.mismatch(encoded), sample);
                                    }
                                }
                            }
                        } finally {
                            MemoryUtil.memFree(reference);
                            MemoryUtil.memFree(encoded);
                        }
                    }
                }
            }
            assertEquals(GL11.GL_NO_ERROR, GL11.glGetError());
        } finally {
            GL15.glDeleteBuffers(buffers);
            MemoryUtil.memFree(templates);
        }
    }

    @Test void modelUpdatesPreserveUnalignedStaticBytesAcrossRepeatedMirroredAndLitTransforms() {
        int stride = 54, quads = 65, count = quads * 4, bytes = stride * count;
        var source = MemoryUtil.memAlloc(bytes + 16);
        var seed = MemoryUtil.memAlloc(bytes + 16);
        var actual = MemoryUtil.memAlloc(bytes + 16);
        int[] buffers = new int[2];
        GL15.glGenBuffers(buffers);
        try {
            MemoryUtil.memSet(MemoryUtil.memAddress(source), 0x6D, source.capacity());
            for (int vertex = 0; vertex < count; vertex++) {
                int at = vertex * stride, corner = vertex & 3;
                source.putFloat(at, (vertex / 4) * .25F + (corner == 1 || corner == 2 ? 1F : 0F));
                source.putFloat(at + 4, corner >= 2 ? 1F : 0F);
                source.putFloat(at + 8, -.5F);
                source.putInt(at + 28, (corner * 32) | (240 - corner * 16) << 16);
                putDirection(source, at + 32, new Vector3f(0, 0, 1));
                putDirection(source, at + 50, new Vector3f(1, 0, 0));
                source.put(at + 53, (byte) ((vertex & 4) == 0 ? -127 : 127));
            }
            seed.put(source.duplicate()).flip();
            // Distinct output padding catches a hidden full-template recopy on subsequent transforms.
            for (int vertex = 0; vertex < count; vertex++) seed.put(vertex * stride + 35, (byte) 0x37);
            upload(buffers[0], source);
            upload(buffers[1], seed);
            GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 0, buffers[0]);
            GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 1, buffers[1]);
            GL20.glUseProgram(transform);
            GL20.glUniform4i(uniform(transform, "Layout"), stride, 12, 28, 32);
            GL20.glUniform3i(uniform(transform, "Extended"), 36, 42, 50);
            GL30.glUniform1ui(uniform(transform, "QuadCount"), quads);
            for (int step = 0; step < 6; step++) {
                var pose = new Matrix4f().translation(3.25F + step, -17.5F, 41.5F)
                        .rotateY(.71F * step).rotateX(-.24F).scale((step & 1) == 0 ? .75F : -.75F, 1.25F, .4F);
                var normalPose = pose.normal(new Matrix3f());
                boolean normalize = step % 3 != 0, mirrored = pose.determinant3x3() < 0;
                int light = step % 2 == 0 ? 0x00F00020 : 0x003000F0;
                int color = step % 2 == 0 ? 0x59BFBFBF : 0xFFFFFFFF;
                int material = 0x456789AB + step, item = 0xCDEF - step;
                try (var stack = MemoryStack.stackPush()) {
                    GL20.glUniformMatrix4fv(uniform(transform, "Pose"), false, pose.get(stack.mallocFloat(16)));
                    GL20.glUniformMatrix3fv(uniform(transform, "NormalPose"), false, normalPose.get(stack.mallocFloat(9)));
                }
                GL30.glUniform1ui(uniform(transform, "SceneLight"), light);
                GL30.glUniform1ui(uniform(transform, "Color"), color);
                GL30.glUniform2ui(uniform(transform, "Material"), material, item);
                GL20.glUniform1i(uniform(transform, "NormalizeNormal"), normalize ? 1 : 0);
                GL20.glUniform1i(uniform(transform, "Mirrored"), mirrored ? 1 : 0);
                GL43.glMemoryBarrier(GL43.GL_SHADER_STORAGE_BARRIER_BIT);
                GL43.glDispatchCompute((quads + 63) / 64, 1, 1);
                GL43.glMemoryBarrier(GL43.GL_BUFFER_UPDATE_BARRIER_BIT | GL43.GL_SHADER_STORAGE_BARRIER_BIT);
                GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, buffers[1]);
                GL15.glGetBufferSubData(GL43.GL_SHADER_STORAGE_BUFFER, 0L, actual);
                var normal = normalPose.transform(new Vector3f(0, 0, 1));
                if (normalize) normal.normalize();
                if (mirrored) normal.negate();
                var tangent = pose.transformDirection(new Vector3f(1, 0, 0)).normalize();
                for (int vertex = 0; vertex < count; vertex++) {
                    int at = vertex * stride;
                    var p = pose.transformPosition(source.getFloat(at), source.getFloat(at + 4), source.getFloat(at + 8), new Vector3f());
                    assertEquals(p.x, actual.getFloat(at), 2E-5);
                    assertEquals(p.y, actual.getFloat(at + 4), 2E-5);
                    assertEquals(p.z, actual.getFloat(at + 8), 2E-5);
                    assertEquals(color, actual.getInt(at + 12));
                    int baked = source.getInt(at + 28);
                    assertEquals(Math.max(light & 65535, baked & 65535)
                            | Math.max(light >>> 16, baked >>> 16) << 16, actual.getInt(at + 28));
                    assertDirection(actual, at + 32, normal);
                    assertDirection(actual, at + 50, tangent);
                    assertEquals(material, actual.getInt(at + 36));
                    assertEquals((short) item, actual.getShort(at + 40));
                    for (int offset : new int[]{16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 35,
                            42, 43, 44, 45, 46, 47, 48, 49, 53}) {
                        assertEquals(seed.get(at + offset), actual.get(at + offset), "Static byte " + (at + offset));
                    }
                }
                for (int at = bytes; at < actual.capacity(); at++) assertEquals(seed.get(at), actual.get(at), "Dispatch guard " + at);
            }
            assertEquals(GL11.GL_NO_ERROR, GL11.glGetError());
        } finally {
            GL15.glDeleteBuffers(buffers);
            MemoryUtil.memFree(source);
            MemoryUtil.memFree(seed);
            MemoryUtil.memFree(actual);
        }
    }

    @Test void computeScopeRestoresPackOwnedSsboRangesAndGenericProgramAndIndirectBindings() {
        int[] buffers = new int[4];
        GL15.glGenBuffers(buffers);
        try {
            int alignment = GL11.glGetInteger(GL43.GL_SHADER_STORAGE_BUFFER_OFFSET_ALIGNMENT);
            for (int buffer : buffers) {
                GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, buffer);
                GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER, alignment * 8L, GL15.GL_DYNAMIC_DRAW);
            }
            GL30.glBindBufferRange(GL43.GL_SHADER_STORAGE_BUFFER, 0, buffers[0], alignment, alignment * 2L);
            GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 1, buffers[1]);
            GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 2, 0);
            GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 3, 0);
            GlStateManager._glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, buffers[2]);
            GlStateManager._glBindBuffer(GL43.GL_DISPATCH_INDIRECT_BUFFER, buffers[3]);
            GlStateManager._glUseProgram(rain);
            long[] before = bindingState();
            try (var stack = MemoryStack.stackPush(); var bindings = new StarOrreryComputeBindings(stack, 4, true)) {
                for (int index = 0; index < 4; index++) GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, index, buffers[3]);
                GlStateManager._glBindBuffer(GL43.GL_DISPATCH_INDIRECT_BUFFER, buffers[0]);
                GlStateManager._glUseProgram(transform);
            }
            assertArrayEquals(before, bindingState());
            assertEquals(GL11.GL_NO_ERROR, GL11.glGetError());
        } finally { GL15.glDeleteBuffers(buffers); }
    }

    private static long[] bindingState() {
        long[] values = new long[15];
        values[0] = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        values[1] = GL11.glGetInteger(GL43.GL_SHADER_STORAGE_BUFFER_BINDING);
        values[2] = GL11.glGetInteger(GL43.GL_DISPATCH_INDIRECT_BUFFER_BINDING);
        for (int index = 0; index < 4; index++) {
            values[3 + index * 3] = GL30.glGetIntegeri(GL43.GL_SHADER_STORAGE_BUFFER_BINDING, index);
            values[4 + index * 3] = GL32.glGetInteger64i(GL43.GL_SHADER_STORAGE_BUFFER_START, index);
            values[5 + index * 3] = GL32.glGetInteger64i(GL43.GL_SHADER_STORAGE_BUFFER_SIZE, index);
        }
        return values;
    }

    private static void putDirection(ByteBuffer data, int at, Vector3f direction) {
        data.put(at, (byte) (direction.x * 127F));
        data.put(at + 1, (byte) (direction.y * 127F));
        data.put(at + 2, (byte) (direction.z * 127F));
    }

    private static void assertDirection(ByteBuffer data, int at, Vector3f direction) {
        assertEquals((int) (Math.clamp(direction.x, -1F, 1F) * 127F), data.get(at), 1);
        assertEquals((int) (Math.clamp(direction.y, -1F, 1F) * 127F), data.get(at + 1), 1);
        assertEquals((int) (Math.clamp(direction.z, -1F, 1F) * 127F), data.get(at + 2), 1);
    }

    private static void upload(int buffer, ByteBuffer data) {
        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, buffer);
        GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER, data, GL15.GL_DYNAMIC_DRAW);
    }

    private static int uniform(int program, String name) {
        int location = GL20.glGetUniformLocation(program, name);
        assertTrue(location >= 0, "Missing uniform " + name);
        return location;
    }

    private static int compile(String resource) throws IOException {
        String source;
        try (var input = StarOrreryGpuComputeTest.class.getResourceAsStream(
                "/assets/molecularmanipulator/shaders/core/" + resource)) {
            if (input == null) throw new IOException("Missing shader " + resource);
            source = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        int shader = GL20.glCreateShader(GL43.GL_COMPUTE_SHADER), program = GL20.glCreateProgram();
        try {
            GL20.glShaderSource(shader, source);
            GL20.glCompileShader(shader);
            assertEquals(GL11.GL_TRUE, GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS), GL20.glGetShaderInfoLog(shader));
            GL20.glAttachShader(program, shader);
            GL20.glLinkProgram(program);
            assertEquals(GL11.GL_TRUE, GL20.glGetProgrami(program, GL20.GL_LINK_STATUS), GL20.glGetProgramInfoLog(program));
            return program;
        } catch (RuntimeException | Error failure) {
            GL20.glDeleteProgram(program);
            throw failure;
        } finally { GL20.glDeleteShader(shader); }
    }
}
