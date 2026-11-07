package com.atir.molecularmanipulator.client.render;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import org.joml.Matrix4f;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryStack;

/**
 * Opt in with -DstarOrrery.gpuTests=true and matching LWJGL/GLFW/OpenGL platform natives.
 * An explicit opt-in must compile/link and render the production GLSL 150 in a real hidden
 * GL 4.3 context; context/resource/shader failures are failures, never assumptions or skips.
 * No Minecraft initialization, reference medium, screenshot golden, or timing assertions.
 */
@EnabledIfSystemProperty(named = "starOrrery.gpuTests", matches = "true")
@Isolated("Owns process-global GLFW state")
@Execution(ExecutionMode.SAME_THREAD)
class StarOrreryNebulaGpuTest {
    private static final String SHADERS = "/assets/molecularmanipulator/shaders/";
    private static final float FIELD_RADIUS = 13.4F;
    private static long window;
    private static int marchProgram, compositeProgram, vao, vertices;
    private static boolean capabilities;

    @BeforeAll static void openContextAndLinkBothProductionStages() throws IOException {
        assertTrue(GLFW.glfwInit(), "GLFW is unavailable (GPU tests were explicitly enabled)");
        GLFW.glfwDefaultWindowHints();
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 4);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 3);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE);
        window = GLFW.glfwCreateWindow(32, 32, "Star orrery nebula contracts", 0, 0);
        assertNotEquals(0L, window, "OpenGL 4.3 is unavailable (GPU tests were explicitly enabled)");
        GLFW.glfwMakeContextCurrent(window);
        var gl = GL.createCapabilities();
        capabilities = true;
        assertTrue(gl.OpenGL43, "OpenGL 4.3 is required");
        marchProgram = link("star_orrery_nebula.fsh");
        compositeProgram = link("star_orrery_nebula_composite.fsh");
        vao = GL30.glGenVertexArrays();
        vertices = GL15.glGenBuffers();
        GL30.glBindVertexArray(vao);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vertices);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, new float[]{-1, -1, 0, 3, -1, 0, -1, 3, 0}, GL15.GL_STATIC_DRAW);
        GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 12, 0L);
        GL20.glEnableVertexAttribArray(0);
        for (int flag : new int[]{GL11.GL_BLEND, GL11.GL_DEPTH_TEST, GL11.GL_CULL_FACE,
                GL11.GL_STENCIL_TEST, GL11.GL_DITHER, GL30.GL_FRAMEBUFFER_SRGB}) GL11.glDisable(flag);
        GL11.glColorMask(true, true, true, true);
        assertEquals(GL11.GL_NO_ERROR, GL11.glGetError());
    }

    @AfterEach void noGlErrors() {
        assertEquals(GL11.GL_NO_ERROR, GL11.glGetError(), "Unexpected GL error");
    }

    @AfterAll static void closeContext() {
        if (capabilities) {
            GL20.glUseProgram(0);
            if (marchProgram != 0) GL20.glDeleteProgram(marchProgram);
            if (compositeProgram != 0) GL20.glDeleteProgram(compositeProgram);
            if (vertices != 0) GL15.glDeleteBuffers(vertices);
            if (vao != 0) GL30.glDeleteVertexArrays(vao);
            GL.setCapabilities(null);
        }
        if (window != 0) GLFW.glfwDestroyWindow(window);
        GLFW.glfwTerminate();
    }

    @Test void emptyRaysDepthClippingAndFogWriteIdentityInsteadOfLeavingStaleRadiance() {
        try (var rig = new Rig(65, 49)) {
            float[] background = checkerboard(rig.width, rig.height);
            rig.scene.upload(background);
            Volume normal = rig.volume(60, 256, new Matrix4f().translation(0, 0, -28), 1);
            Volume[] empty = {
                    rig.volume(60, 256, new Matrix4f().translation(1000, 0, -28), 1),
                    normal,
                    new Volume(normal.projection, normal.pose, 0, 0, 1)
            };
            for (int sample = 0; sample < empty.length; sample++) {
                rig.depth(sample == 1 ? projectedDepth(normal.projection, 3) : 1);
                // A discard, unwritten pixel, or accidental blending must retain this poison and fail.
                rig.volume.clear(7, 5, 3, 0);
                rig.march(empty[sample], rig.marchBounds());
                assertIdentity(rig.volume.read(), "empty fixture " + sample);
                assertPixels(background, rig.composite(empty[sample], false, rig.marchBounds(), rig.sceneBounds()),
                        0, "No-contribution half pass must preserve every native checkerboard pixel");
                assertPixels(background, rig.composite(empty[sample], true, rig.marchBounds(), rig.sceneBounds()),
                        0, "No-contribution direct pass must preserve the scene");
            }
        }
    }

    @Test void compositeKeepsNativeCheckerboardAndHdrAtEveryPixelOfAnOddSizedScene() {
        try (var rig = new Rig(67, 51)) {
            float[] background = checkerboard(rig.width, rig.height);
            rig.scene.upload(background);
            rig.depth(1);
            // Exactly representable half-floats isolate native SceneColor*T+L from integration.
            float[] light = {.75F, .25F, .5F};
            float transmittance = .375F;
            rig.volume.clear(light[0], light[1], light[2], transmittance);
            float[] expected = background.clone();
            for (int at = 0; at < expected.length; at += 4) {
                for (int channel = 0; channel < 3; channel++) {
                    expected[at + channel] = background[at + channel] * transmittance + light[channel];
                }
                expected[at + 3] = 1;
            }
            Volume volume = rig.volume(60, 256, new Matrix4f().translation(0, 0, -28), 1);
            float[] actual = rig.composite(volume, false, rig.marchBounds(), rig.sceneBounds());
            assertPixels(expected, actual, 2E-6F, "Native composition, including last odd row/column");
            assertTrue(maxRgb(actual) > 2, "HDR scene color must not be clamped or downsampled");
        }
    }

    @Test void actualHalfResolutionIntegratorRetainsHdrRadianceAndTransmittanceAtOddSizes() {
        try (var rig = new Rig(129, 97)) {
            assertEquals(65, rig.volume.width);
            assertEquals(49, rig.volume.height);
            rig.scene.upload(solid(rig.width, rig.height, .125F, .25F, .375F));
            rig.depth(1);
            for (float detail : new float[]{0, 1}) {
                Volume volume = rig.volume(60, 256, new Matrix4f().translation(0, 0, -28), detail);
                rig.volume.clear(7, 5, 3, 0);
                rig.march(volume, rig.marchBounds());
                float[] integrated = rig.volume.read();
                assertVolume(integrated);
                assertTrue(maxRgb(integrated) > 1.05F, "The actual medium must exercise RGBA16F radiance > 1");
                float[] composed = rig.composite(volume, false, rig.marchBounds(), rig.sceneBounds());
                assertFinite(composed);
                assertTrue(maxRgb(composed) > 1, "Actual two-stage output must retain highlights");
                // Both odd-sized targets have a texel exactly at UV .5; no resolve approximation here.
                int low = ((rig.volume.height / 2) * rig.volume.width + rig.volume.width / 2) * 4;
                int nativeAt = ((rig.height / 2) * rig.width + rig.width / 2) * 4;
                for (int channel = 0; channel < 3; channel++) {
                    float background = (channel + 1) * .125F;
                    assertEquals(background * integrated[low + 3] + integrated[low + channel],
                            composed[nativeAt + channel], 2E-5F, "Centered HDR composition, detail=" + detail);
                }
            }
        }
    }

    @Test void halfResolveAgreesWithFullDirectForTheSameSmoothCoreAtBothStepCounts() {
        try (var rig = new Rig(161, 121)) {
            rig.scene.upload(solid(rig.width, rig.height, .125F, .25F, .375F));
            for (float detail : new float[]{0, 1}) {
                // A short segment viewed from inside the bulge is genuinely smooth. Looking at the
                // core from outside still intersects subpixel stellarPoints and is not a smooth fixture.
                // Both paths use the unchanged production medium, including its noise and stars.
                Volume volume = rig.volume(55, 256, new Matrix4f(), detail);
                rig.depth(projectedDepth(volume.projection, .25F));
                rig.volume.clear(7, 5, 3, 0);
                // The small-volume/direct branch must ignore even a completely poisoned volume target.
                float[] direct = rig.composite(volume, true, rig.marchBounds(), rig.sceneBounds());
                rig.march(volume, rig.marchBounds());
                float[] half = rig.composite(volume, false, rig.marchBounds(), rig.sceneBounds());
                assertFinite(direct);
                assertFinite(half);
                double sum = 0, maximum = 0;
                for (int at = 0; at < half.length; at += 4) for (int channel = 0; channel < 3; channel++) {
                    double error = Math.abs(half[at + channel] - direct[at + channel]);
                    sum += error;
                    maximum = Math.max(maximum, error);
                }
                double mean = sum / (rig.width * rig.height * 3);
                // Spatial interpolation plus RGBA16F is not bit-identical to native integration.
                // Mean < 2% and worst < 12% of unit radiance allow small stellar/dust features,
                // but reject a wrong ray, medium, step count, or transmittance convention.
                assertTrue(mean < .02, "Mean direct/half error: " + mean);
                assertTrue(maximum < .12, "Maximum direct/half error: " + maximum);
                assertTrue(maxRgb(direct) > 1, "Reference must contain a real, bright volume");
                assertTrue(rgbDifference(direct, rig.scene.read()) > .1F, "Reference cannot be an identity image");
            }
        }
    }

    @Test void depthAwareEdgesRejectForegroundLightAndFallbackResolvesUnsampledOnePixelOpenings() {
        try (var rig = new Rig(65, 49)) {
            float[] background = checkerboard(rig.width, rig.height);
            rig.scene.upload(background);
            Volume volume = rig.volume(60, 256, new Matrix4f().translation(0, 0, -28), 1);
            int thinColumn = 31;
            // With 65 native / 33 march texels, no half-resolution sample sees column 31.
            for (int x = 0; x < rig.volume.width; x++) {
                assertNotEquals(thinColumn, (int) ((x + .5) * rig.width / rig.volume.width));
            }
            float foreground = projectedDepth(volume.projection, 3);
            for (int fixture = 0; fixture < 3; fixture++) {
                float[] depths = new float[rig.width * rig.height];
                for (int y = 0; y < rig.height; y++) for (int x = 0; x < rig.width; x++) {
                    boolean blocked = switch (fixture) {
                        case 0 -> x < 32;           // A wide step exercises depth-aware weighting.
                        case 1 -> x == thinColumn; // No matching taps: fallback must NOT leak light.
                        default -> x != thinColumn; // Inverse slit: fallback must NOT erase the volume.
                    };
                    depths[y * rig.width + x] = blocked ? foreground : 1;
                }
                rig.depth(depths);
                float[] direct = rig.composite(volume, true, rig.marchBounds(), rig.sceneBounds());
                rig.volume.clear(7, 5, 3, 0);
                rig.march(volume, rig.marchBounds());
                if (fixture == 2) assertIdentity(rig.volume.read(), "Half pass cannot see the sky slit");
                float[] half = rig.composite(volume, false, rig.marchBounds(), rig.sceneBounds());
                assertFinite(half);
                for (int pixel = 0; pixel < depths.length; pixel++) {
                    if (depths[pixel] != 1) {
                        for (int channel = 0; channel < 4; channel++) assertEquals(background[pixel * 4 + channel],
                                half[pixel * 4 + channel], 0, "Light leaked onto foreground, fixture=" + fixture
                                        + ", x=" + pixel % rig.width + ", y=" + pixel / rig.width);
                    }
                }
                if (fixture == 2) {
                    int at = ((rig.height / 2) * rig.width + thinColumn) * 4;
                    float contribution = 0;
                    for (int channel = 0; channel < 3; channel++) {
                        contribution += Math.abs(direct[at + channel] - background[at + channel]);
                    }
                    assertTrue(contribution > .1F, "The unsampled sky opening must see the actual nebula");
                    assertPixels(direct, half, 2E-5F, "No matching depth taps must integrate at native resolution");
                } else {
                    assertTrue(rgbDifference(half, background) > .1F, "Unoccluded nebula must remain visible");
                }
            }
        }
    }

    @Test void skyDepthIntegratesPastTheProjectionFarPlaneWhileFiniteDepthStillOccludes() {
        try (var rig = new Rig(65, 49)) {
            float[] background = solid(rig.width, rig.height, .125F, .25F, .375F);
            rig.scene.upload(background);
            // Entire sphere starts beyond the projection's far plane (8 < 28 - 13.4).
            Volume volume = rig.volume(60, 8, new Matrix4f().translation(0, 0, -28), 1);
            float[] sky = null;
            for (float depth : new float[]{1, Math.nextDown(1F), .999998F}) {
                rig.depth(depth);
                rig.volume.clear(7, 5, 3, 0);
                rig.march(volume, rig.marchBounds());
                float[] integrated = rig.volume.read();
                assertVolume(integrated);
                float[] actual = rig.composite(volume, false, rig.marchBounds(), rig.sceneBounds());
                assertFinite(actual);
                if (depth >= .999999F) {
                    assertTrue(maxRgb(integrated) > 1, "Sky must not clip the nebula at the far plane");
                    if (sky == null) sky = actual;
                    else assertPixels(sky, actual, 2E-5F, "Both sky-depth sentinel values must agree");
                } else {
                    assertIdentity(integrated, "Finite geometry before the sphere");
                    assertPixels(background, actual, 0, "Finite depth must still occlude");
                }
            }
        }
    }

    @Test void consecutiveVolumesCanReuseBoundedHalfTargetWithoutBorrowingStaleTexels() {
        try (var rig = new Rig(65, 49)) {
            float[] background = solid(rig.width, rig.height, .125F, .25F, .375F);
            rig.scene.upload(background);
            rig.depth(1);
            Volume first = rig.volume(60, 256, new Matrix4f().translation(-4, 0, -28), 1);
            Volume second = rig.volume(60, 256, new Matrix4f().translation(8, 0, -28), 0);
            Rect firstMarch = new Rect(0, 0, 23, 25), firstScene = new Rect(0, 0, 45, 49);
            Rect secondMarch = new Rect(15, 5, 18, 16), secondScene = new Rect(29, 9, 36, 33);
            rig.volume.clear(7, 5, 3, 0);
            rig.march(first, firstMarch);
            float[] afterFirst = rig.composite(first, false, firstMarch, firstScene);
            assertTrue(rgbDifference(afterFirst, background) > .1F, "First volume must actually contribute");
            rig.scene.upload(afterFirst); // The next machine samples the current, already composited scene.
            rig.march(second, secondMarch); // Deliberately no clear; outside bounds belongs to the first machine.
            float[] reusedVolume = rig.volume.read();
            float[] reused = rig.composite(second, false, secondMarch, secondScene);
            rig.volume.clear(13, 11, 9, 0); // Independent outside contents must never affect this machine.
            rig.march(second, secondMarch);
            float[] freshVolume = rig.volume.read();
            for (int y = secondMarch.y; y < secondMarch.top(); y++) for (int x = secondMarch.x; x < secondMarch.right(); x++) {
                int at = (y * rig.volume.width + x) * 4;
                for (int channel = 0; channel < 4; channel++) assertEquals(freshVolume[at + channel],
                        reusedVolume[at + channel], 0, "Every bounded texel must be overwritten, including identities");
            }
            float[] fresh = rig.composite(second, false, secondMarch, secondScene);
            assertPixels(fresh, reused, 0, "MarchBounds must exclude stale samples at padded rectangle edges");
            assertTrue(rgbDifference(reused, afterFirst) > .01F, "Second volume must actually contribute");
        }
    }

    private record Rect(int x, int y, int width, int height) {
        int right() { return x + width; }
        int top() { return y + height; }
    }

    private record Volume(Matrix4f projection, Matrix4f pose, float detail, float fogStart, float fogEnd) {}

    /** Test-only raw-GL analogue of the two passes, not a second implementation of the medium. */
    private static final class Rig implements AutoCloseable {
        final int width, height, depth;
        final Target scene, volume, output;

        Rig(int width, int height) {
            this.width = width;
            this.height = height;
            scene = new Target(width, height, GL30.GL_RGBA32F);
            volume = new Target((width + 1) / 2, (height + 1) / 2, GL30.GL_RGBA16F);
            output = new Target(width, height, GL30.GL_RGBA32F);
            depth = texture(width, height, GL30.GL_DEPTH_COMPONENT32F, GL11.GL_DEPTH_COMPONENT);
        }

        Rect marchBounds() { return new Rect(0, 0, volume.width, volume.height); }
        Rect sceneBounds() { return new Rect(0, 0, width, height); }
        Volume volume(float fov, float far, Matrix4f pose, float detail) {
            return new Volume(new Matrix4f().perspective((float) Math.toRadians(fov), (float) width / height, .1F, far),
                    pose, detail, 1000, 2000);
        }
        void depth(float value) {
            float[] values = new float[width * height];
            java.util.Arrays.fill(values, value);
            depth(values);
        }
        void depth(float[] values) {
            bindTexture(0, depth);
            GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, width, height, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, values);
        }
        void configure(int program, Volume field) {
            GL20.glUseProgram(program);
            bindTexture(0, depth);
            GL20.glUniform1i(uniform(program, "SceneDepth"), 0);
            try (var stack = MemoryStack.stackPush()) {
                GL20.glUniformMatrix4fv(uniform(program, "InverseProjection"), false,
                        new Matrix4f(field.projection).invert().get(stack.mallocFloat(16)));
                GL20.glUniformMatrix4fv(uniform(program, "ViewToNebula"), false,
                        new Matrix4f(field.pose).invert().get(stack.mallocFloat(16)));
                GL20.glUniformMatrix4fv(uniform(program, "NebulaToCamera"), false, field.pose.get(stack.mallocFloat(16)));
            }
            GL20.glUniform3f(uniform(program, "RayOrigin"), 0, 0, 0);
            GL20.glUniform1f(uniform(program, "FieldRadius"), FIELD_RADIUS);
            GL20.glUniform1f(uniform(program, "NebulaTime"), 3.25F);
            GL20.glUniform1f(uniform(program, "FlowTime"), 7.5F);
            GL20.glUniform1f(uniform(program, "Detail"), field.detail);
            GL20.glUniform1f(uniform(program, "Dynamics"), 1);
            GL20.glUniform1f(uniform(program, "VolumeFogStart"), field.fogStart);
            GL20.glUniform1f(uniform(program, "VolumeFogEnd"), field.fogEnd);
            GL20.glUniform1i(uniform(program, "VolumeFogShape"), 0);
            GL20.glUniform2f(uniform(program, "MarchSize"), volume.width, volume.height);
        }
        void march(Volume field, Rect bounds) {
            volume.bind();
            configure(marchProgram, field);
            draw(bounds);
        }
        float[] composite(Volume field, boolean direct, Rect marchBounds, Rect bounds) {
            // Discard preserves the original native scene, exactly as drawing back onto the main target does.
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, scene.framebuffer);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, output.framebuffer);
            GL30.glBlitFramebuffer(0, 0, width, height, 0, 0, width, height, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
            output.bind();
            configure(compositeProgram, field);
            bindTexture(1, scene.color);
            bindTexture(2, volume.color);
            GL20.glUniform1i(uniform(compositeProgram, "SceneColor"), 1);
            GL20.glUniform1i(uniform(compositeProgram, "VolumeColor"), 2);
            GL20.glUniform2f(uniform(compositeProgram, "SceneSize"), width, height);
            GL20.glUniform4f(uniform(compositeProgram, "MarchBounds"), marchBounds.x, marchBounds.y,
                    marchBounds.right() - 1, marchBounds.top() - 1);
            GL20.glUniform1i(uniform(compositeProgram, "IntegrateDirect"), direct ? 1 : 0);
            draw(bounds);
            return output.read();
        }
        @Override public void close() {
            scene.close(); volume.close(); output.close();
            GL11.glDeleteTextures(depth);
        }
    }

    private static final class Target implements AutoCloseable {
        final int width, height, color, framebuffer;
        Target(int width, int height, int format) {
            this.width = width;
            this.height = height;
            color = texture(width, height, format, GL11.GL_RGBA);
            framebuffer = GL30.glGenFramebuffers();
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, color, 0);
            GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);
            GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
            assertEquals(GL30.GL_FRAMEBUFFER_COMPLETE, GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER),
                    "Incomplete test framebuffer, format=" + format);
            assertEquals(format, GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_INTERNAL_FORMAT));
        }
        void bind() {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer);
            GL11.glViewport(0, 0, width, height);
        }
        void upload(float[] pixels) {
            bindTexture(0, color);
            GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, width, height, GL11.GL_RGBA, GL11.GL_FLOAT, pixels);
        }
        void clear(float r, float g, float b, float a) {
            bind();
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL30.glClearBufferfv(GL11.GL_COLOR, 0, new float[]{r, g, b, a});
        }
        float[] read() {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, framebuffer);
            float[] pixels = new float[width * height * 4];
            GL11.glReadPixels(0, 0, width, height, GL11.GL_RGBA, GL11.GL_FLOAT, pixels);
            return pixels;
        }
        @Override public void close() {
            GL30.glDeleteFramebuffers(framebuffer);
            GL11.glDeleteTextures(color);
        }
    }

    private static int texture(int width, int height, int internalFormat, int format) {
        int texture = GL11.glGenTextures();
        bindTexture(0, texture);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, internalFormat, width, height, 0, format, GL11.GL_FLOAT, (ByteBuffer) null);
        return texture;
    }

    private static void bindTexture(int unit, int texture) {
        GL13.glActiveTexture(GL13.GL_TEXTURE0 + unit);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
    }

    private static void draw(Rect bounds) {
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(bounds.x, bounds.y, bounds.width, bounds.height);
        GL30.glBindVertexArray(vao);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

    private static float projectedDepth(Matrix4f projection, float distance) {
        var point = projection.transform(new org.joml.Vector4f(0, 0, -distance, 1));
        return (point.z / point.w + 1) * .5F;
    }

    private static float[] checkerboard(int width, int height) {
        float[] pixels = solid(width, height, 0, 0, 0);
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            int at = (y * width + x) * 4;
            pixels[at] = ((x + y) & 1) == 0 ? 4 : .125F;
            pixels[at + 1] = .25F + (x % 8) * .125F;
            pixels[at + 2] = (y % 8) * .25F;
        }
        return pixels;
    }

    private static float[] solid(int width, int height, float r, float g, float b) {
        float[] pixels = new float[width * height * 4];
        for (int at = 0; at < pixels.length; at += 4) {
            pixels[at] = r; pixels[at + 1] = g; pixels[at + 2] = b; pixels[at + 3] = 1;
        }
        return pixels;
    }

    private static void assertFinite(float[] pixels) {
        for (int at = 0; at < pixels.length; at++) assertTrue(Float.isFinite(pixels[at]), "Non-finite component " + at);
    }

    private static void assertVolume(float[] pixels) {
        assertFinite(pixels);
        for (int at = 0; at < pixels.length; at += 4) {
            for (int channel = 0; channel < 3; channel++) assertTrue(pixels[at + channel] >= 0
                    && pixels[at + channel] <= 1.301F, "Radiance must be capped at 1.3, component " + (at + channel));
            assertTrue(pixels[at + 3] >= 0 && pixels[at + 3] <= 1, "Alpha must encode transmittance");
        }
    }

    private static void assertIdentity(float[] pixels, String message) {
        for (int at = 0; at < pixels.length; at++) assertEquals(at % 4 == 3 ? 1F : 0F, pixels[at], 0,
                message + ", component=" + at);
    }

    private static void assertPixels(float[] expected, float[] actual, float tolerance, String message) {
        assertEquals(expected.length, actual.length);
        assertFinite(actual);
        for (int at = 0; at < actual.length; at++) assertEquals(expected[at], actual[at], tolerance,
                message + ", component=" + at);
    }

    private static float maxRgb(float[] pixels) {
        float maximum = Float.NEGATIVE_INFINITY;
        for (int at = 0; at < pixels.length; at++) if (at % 4 != 3) maximum = Math.max(maximum, pixels[at]);
        return maximum;
    }

    private static float rgbDifference(float[] a, float[] b) {
        float maximum = 0;
        for (int at = 0; at < a.length; at++) if (at % 4 != 3) maximum = Math.max(maximum, Math.abs(a[at] - b[at]));
        return maximum;
    }

    private static int uniform(int program, String name) {
        int location = GL20.glGetUniformLocation(program, name);
        assertTrue(location >= 0, "Missing active uniform " + name);
        return location;
    }

    private static int link(String fragment) throws IOException {
        int program = GL20.glCreateProgram(), vertex = 0, pixel = 0;
        try {
            vertex = compile(GL20.GL_VERTEX_SHADER, SHADERS + "core/cosmic_lens.vsh");
            pixel = compile(GL20.GL_FRAGMENT_SHADER, SHADERS + "core/" + fragment);
            GL20.glAttachShader(program, vertex);
            GL20.glAttachShader(program, pixel);
            GL20.glBindAttribLocation(program, 0, "Position");
            GL30.glBindFragDataLocation(program, 0, "fragColor");
            GL20.glLinkProgram(program);
            assertEquals(GL11.GL_TRUE, GL20.glGetProgrami(program, GL20.GL_LINK_STATUS),
                    fragment + " link: " + GL20.glGetProgramInfoLog(program));
            return program;
        } catch (IOException | RuntimeException | Error failure) {
            GL20.glDeleteProgram(program);
            throw failure;
        } finally {
            if (vertex != 0) GL20.glDeleteShader(vertex);
            if (pixel != 0) GL20.glDeleteShader(pixel);
        }
    }

    private static int compile(int type, String resource) throws IOException {
        String source = expandImports(resource, false, new HashSet<>());
        assertTrue(source.stripLeading().startsWith("#version 150"), "Must test production GLSL 150: " + resource);
        assertFalse(source.contains("#moj_import"), "Unexpanded import in " + resource);
        int shader = GL20.glCreateShader(type);
        try {
            GL20.glShaderSource(shader, source);
            GL20.glCompileShader(shader);
            assertEquals(GL11.GL_TRUE, GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS),
                    resource + " compile: " + GL20.glGetShaderInfoLog(shader));
            return shader;
        } catch (RuntimeException | Error failure) {
            GL20.glDeleteShader(shader);
            throw failure;
        }
    }

    /** Only resolve moj_import and included #version directives; never alter the production math. */
    private static String expandImports(String resource, boolean include, Set<String> imported) throws IOException {
        String source;
        try (var input = StarOrreryNebulaGpuTest.class.getResourceAsStream(resource)) {
            if (input == null) throw new IOException("Missing actual shader resource " + resource);
            source = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        var directive = Pattern.compile("#moj_import\\s+<([^>]+)>\\s*");
        var result = new StringBuilder();
        for (String line : source.lines().toList()) {
            String trimmed = line.strip();
            var match = directive.matcher(trimmed);
            if (match.matches()) {
                String name = match.group(1), namespace = "minecraft";
                int colon = name.indexOf(':');
                if (colon >= 0) {
                    namespace = name.substring(0, colon);
                    name = name.substring(colon + 1);
                }
                String path = "/assets/" + namespace + "/shaders/include/" + name;
                if (imported.add(path)) result.append(expandImports(path, true, imported));
            } else if (!(include && trimmed.startsWith("#version"))) {
                result.append(line).append('\n');
            }
        }
        return result.toString();
    }
}
