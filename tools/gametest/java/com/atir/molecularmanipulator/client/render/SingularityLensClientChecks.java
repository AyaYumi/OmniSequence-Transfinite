package com.atir.molecularmanipulator.client.render;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.ShaderInstance;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

/** Execute the actual lens fragment shaders on a real client GPU, without opening a player world. */
@EventBusSubscriber(modid = "molecularmanipulator", value = Dist.CLIENT)
public final class SingularityLensClientChecks {
    private static final int SIZE = 192;
    private static int ticks;
    private static boolean finished;

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("omnisequence.lens_client_check") || finished) return;
        var mc = Minecraft.getInstance();
        if (mc.screen instanceof AccessibilityOnboardingScreen) {
            mc.options.onboardingAccessibilityFinished();
            mc.setScreen(new TitleScreen());
        }
        if (++ticks > 600) {
            finished = true;
            LogUtils.getLogger().error("LENS_BOBBING_CLIENT_FAILED shaders/title screen did not become ready");
            mc.stop();
            return;
        }
        if (!(mc.screen instanceof TitleScreen) || OmniShaders.singularityLens() == null || OmniShaders.cosmicLens() == null) return;
        finished = true;
        try {
            checkShaders(mc);
            LogUtils.getLogger().info("LENS_BOBBING_CLIENT_PASS shaders=2 effects=3 phases=9 rayOrigin=true depthOcclusion=true");
        } catch (Throwable failure) {
            LogUtils.getLogger().error("LENS_BOBBING_CLIENT_FAILED", failure);
        } finally {
            mc.stop();
        }
    }

    private static void checkShaders(Minecraft mc) {
        var scene = new TextureTarget(SIZE, SIZE, true, Minecraft.ON_OSX);
        var output = new TextureTarget(SIZE, SIZE, false, Minecraft.ON_OSX);
        var previousShader = RenderSystem.getShader();
        boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        boolean depthWrite = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        try {
            scene.setClearColor(.12F, .18F, .24F, 1);
            output.setClearColor(.12F, .18F, .24F, 1);
            for (int kind = 0; kind < 3; kind++) {
                var shader = kind == 0 ? OmniShaders.singularityLens() : OmniShaders.cosmicLens();
                var center = new Vector3f(0, 0, kind == 0 ? -24 : -6);
                float radius = kind == 0 ? 3.8F : .3F;
                float field = kind == 0 ? 12.5F : 1.25F;
                boolean white = kind != 2;
                for (int phase = -1; phase < 8; phase++) {
                    var bob = phase < 0 ? new Matrix4f() : walkBob(phase * (float) Math.PI / 4);
                    var projection = new Matrix4f().perspective((float) Math.toRadians(70), 1, .05F, 256).mul(bob);
                    // Independent expected origin: undo the actual affine bob matrix, not a clip-space point.
                    var origin = new Matrix4f(bob).invert().transformPosition(new Vector3f());
                    var projected = projection.transform(new Vector4f(center, 1));
                    int x = (int) ((projected.x / projected.w * .5F + .5F) * SIZE);
                    int y = (int) ((projected.y / projected.w * .5F + .5F) * SIZE);
                    draw(scene, output, shader, projection, origin, center, radius, field, white, 1);
                    int[] pixel = pixel(x, y);
                    check(white ? pixel[0] > 245 && pixel[1] > 245 && pixel[2] > 245
                                    : pixel[0] < 10 && pixel[1] < 10 && pixel[2] < 10,
                            "World center must hit the actual " + (kind == 0 ? "Hub white" : white ? "small white" : "black")
                                    + " core with phase=" + phase + ", RGB=" + java.util.Arrays.toString(pixel));
                    // Depth from a point halfway to the core must occlude it, in the same bobbed projection.
                    var foreground = new Vector3f(center).sub(origin).mul(.5F).add(origin);
                    var clip = projection.transform(new Vector4f(foreground, 1));
                    double foregroundDepth = (clip.z / clip.w) * .5 + .5;
                    draw(scene, output, shader, projection, origin, center, radius, field, white, foregroundDepth);
                    pixel = pixel(x, y);
                    check(Math.abs(pixel[0] - 31) <= 1 && Math.abs(pixel[1] - 46) <= 1 && Math.abs(pixel[2] - 61) <= 1,
                            "Foreground must remain unchanged with phase=" + phase + ", RGB=" + java.util.Arrays.toString(pixel));
                }
            }
        } finally {
            scene.destroyBuffers();
            output.destroyBuffers();
            GL11.glClearDepth(1);
            RenderSystem.setShader(() -> previousShader);
            RenderSystem.depthMask(depthWrite);
            if (depth) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
            if (blend) RenderSystem.enableBlend(); else RenderSystem.disableBlend();
            if (cull) RenderSystem.enableCull(); else RenderSystem.disableCull();
            mc.getMainRenderTarget().bindWrite(true);
        }
    }

    private static void draw(TextureTarget scene, TextureTarget output, ShaderInstance shader, Matrix4f projection,
            Vector3f origin, Vector3f center, float radius, float field, boolean white, double sceneDepth) {
        RenderSystem.depthMask(true);
        scene.clear(Minecraft.ON_OSX);
        scene.bindWrite(true);
        GL11.glClearDepth(sceneDepth);
        GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
        GL11.glClearDepth(1);
        output.clear(Minecraft.ON_OSX);
        output.bindWrite(true);
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableBlend();
        RenderSystem.disableCull();
        RenderSystem.setShader(() -> shader);
        shader.setSampler("SceneColor", scene.getColorTextureId());
        shader.setSampler("SceneDepth", scene.getDepthTextureId());
        shader.safeGetUniform("SceneSize").set((float) SIZE, (float) SIZE);
        shader.safeGetUniform("CoreCenter").set(center);
        var inverse = SingularityLensProjection.inverseProjection(projection);
        var actualOrigin = SingularityLensProjection.viewOrigin(inverse);
        check(actualOrigin.distance(origin) < 1.0E-5F, "Production origin must match the independent inverse-bob eye");
        shader.safeGetUniform("ViewOrigin").set(actualOrigin);
        shader.safeGetUniform("LensProjection").set(projection);
        shader.safeGetUniform("InverseProjection").set(inverse);
        shader.safeGetUniform("CoreRadius").set(radius);
        shader.safeGetUniform("FieldRadius").set(field);
        shader.safeGetUniform("LensTime").set(0F);
        shader.safeGetUniform("HoleKind").set(white ? 1F : 0F);
        var mesh = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        mesh.addVertex(-1, -1, 0); mesh.addVertex(1, -1, 0);
        mesh.addVertex(1, 1, 0); mesh.addVertex(-1, 1, 0);
        BufferUploader.drawWithShader(mesh.buildOrThrow());
    }

    /** Vanilla GameRenderer.bobView's translation and rotations for a walking amplitude of 0.1. */
    private static Matrix4f walkBob(float phase) {
        float bob = .1F;
        return new Matrix4f().translation((float) Math.sin(phase) * bob * .5F, -(float) Math.abs(Math.cos(phase)) * bob, 0)
                .rotateZ((float) Math.toRadians(Math.sin(phase) * bob * 3))
                .rotateX((float) Math.toRadians(Math.abs(Math.cos(phase - .2F) * bob) * 5));
    }

    private static int[] pixel(int x, int y) {
        var pixel = BufferUtils.createByteBuffer(4);
        GL11.glReadPixels(x, y, 1, 1, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixel);
        return new int[]{pixel.get(0) & 255, pixel.get(1) & 255, pixel.get(2) & 255};
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
