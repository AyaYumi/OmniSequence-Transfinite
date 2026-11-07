package com.atir.molecularmanipulator.client.render;

import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.logging.LogUtils;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Real production GLSL on the client GPU; never creates or opens a player world. */
@Mod.EventBusSubscriber(modid = "molecularmanipulator", value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SingularityLensClientChecks {
    private static final int SIZE = 192;
    private static final int EFFECTS = 3;
    private static final int PHASES = 9; // Bob disabled, then eight walking phases.
    private static final String REPORT = "singularity-lens-result.json";
    private static final List<Map<String, Object>> CASES = new ArrayList<>();
    private static final Map<String, String> GPU = new LinkedHashMap<>();
    private static int ticks, coreChecks, depthChecks, originChecks, berPoseChecks;
    private static boolean finished;

    private SingularityLensClientChecks() {}

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || finished
                || !Boolean.getBoolean("omnisequence.lens_client_check")) return;
        var mc = Minecraft.getInstance();
        try {
            checkNoWorld(mc);
            if (++ticks > 600) throw new AssertionError("Shaders/title screen did not become ready in 600 ticks");
            if (mc.screen instanceof AccessibilityOnboardingScreen) {
                // 1.20.1 has no Options.onboardingAccessibilityFinished(); use its actual close path.
                mc.screen.onClose();
            }
            if (!(mc.screen instanceof TitleScreen) || mc.getOverlay() != null
                    || OmniShaders.singularityLens() == null || OmniShaders.cosmicLens() == null) return;
            RenderSystem.assertOnRenderThread();
            checkShaders();
            checkNoWorld(mc);
            check(coreChecks == EFFECTS * PHASES && depthChecks == EFFECTS * PHASES
                            && originChecks == EFFECTS * PHASES && berPoseChecks == EFFECTS * PHASES,
                    "Incomplete lens GPU coverage");
            finish(mc, null);
        } catch (Throwable failure) {
            finish(mc, failure);
        }
    }

    private static void checkNoWorld(Minecraft mc) {
        check(mc.level == null && mc.player == null && mc.getSingleplayerServer() == null,
                "Lens fixture must stay on the title screen, without a client level or integrated server");
    }

    private static void finish(Minecraft mc, Throwable failure) {
        finished = true;
        String runId = System.getProperty("omnisequence.lens_client_check.run_id", "manual");
        var report = new LinkedHashMap<String, Object>();
        report.put("suite", "singularity-lens");
        report.put("runId", runId);
        report.put("passed", failure == null);
        report.put("width", SIZE);
        report.put("height", SIZE);
        report.put("shaders", 2);
        report.put("effects", EFFECTS);
        report.put("phases", PHASES);
        report.put("titleScreenOnly", mc.screen instanceof TitleScreen && mc.level == null
                && mc.player == null && mc.getSingleplayerServer() == null);
        report.put("coreChecks", coreChecks);
        report.put("depthChecks", depthChecks);
        report.put("originChecks", originChecks);
        report.put("berPoseChecks", berPoseChecks);
        report.put("rayOrigin", originChecks == EFFECTS * PHASES);
        report.put("depthOcclusion", depthChecks == EFFECTS * PHASES);
        report.put("berViewRotationOnce", berPoseChecks == EFFECTS * PHASES);
        report.put("gpu", GPU);
        report.put("cases", CASES);
        var stack = new StringWriter();
        if (failure != null) failure.printStackTrace(new PrintWriter(stack));
        report.put("error", stack.toString());
        try {
            var path = mc.gameDirectory.toPath().resolve(REPORT);
            Files.createDirectories(path.getParent());
            Files.writeString(path, new GsonBuilder().setPrettyPrinting().create().toJson(report), StandardCharsets.UTF_8);
        } catch (Throwable saveFailure) {
            if (failure != null) saveFailure.addSuppressed(failure);
            failure = saveFailure; // A success marker is forbidden if the report could not be persisted.
        }
        if (failure == null) {
            LogUtils.getLogger().info("LENS_BOBBING_CLIENT_PASS shaders=2 effects=3 phases=9 "
                    + "rayOrigin=true depthOcclusion=true berViewRotationOnce=true runId={}", runId);
        } else {
            LogUtils.getLogger().error("LENS_BOBBING_CLIENT_FAILED runId=" + runId, failure);
        }
        mc.stop();
    }

    private static void checkShaders() {
        GPU.put("vendor", GL11.glGetString(GL11.GL_VENDOR));
        GPU.put("renderer", GL11.glGetString(GL11.GL_RENDERER));
        GPU.put("version", GL11.glGetString(GL11.GL_VERSION));
        GPU.put("glsl", GL11.glGetString(GL20.GL_SHADING_LANGUAGE_VERSION));
        checkGl("before fixture");
        var previousShader = RenderSystem.getShader();
        var state = new SavedGlState();
        TextureTarget scene = null;
        TextureTarget output = null;
        try {
            RenderSystem.disableScissor();
            RenderSystem.colorMask(true, true, true, true);
            GL11.glDisable(GL30.GL_FRAMEBUFFER_SRGB);
            RenderSystem.activeTexture(GL13.GL_TEXTURE0);
            scene = new TextureTarget(SIZE, SIZE, true, Minecraft.ON_OSX);
            output = new TextureTarget(SIZE, SIZE, false, Minecraft.ON_OSX);
            scene.setClearColor(.12F, .18F, .24F, 1);
            output.setClearColor(.12F, .18F, .24F, 1);
            checkProgram(OmniShaders.singularityLens());
            checkProgram(OmniShaders.cosmicLens());
            for (int kind = 0; kind < EFFECTS; kind++) {
                var shader = kind == 0 ? OmniShaders.singularityLens() : OmniShaders.cosmicLens();
                float localY = kind == 0 ? 12.5F : 0;
                float radius = kind == 0 ? 3.8F : .3F;
                float field = kind == 0 ? 12.5F : 1.25F;
                boolean white = kind != 2;
                String effect = kind == 0 ? "hub-white" : white ? "cosmic-white" : "cosmic-black";
                var expectedCenter = new Vector3f(0, 0, kind == 0 ? -24 : -6);
                // GameRenderer.renderLevel supplies Z-roll, X-pitch, Y-(yaw+180) to LevelRenderer.
                // LevelRenderer then translates that same pose by block minus camera coordinates.
                var camera = new PoseStack();
                camera.mulPose(Axis.ZP.rotationDegrees(4));
                camera.mulPose(Axis.XP.rotationDegrees(-12));
                camera.mulPose(Axis.YP.rotationDegrees(27 + 180));
                var view = new Matrix4f(camera.last().pose());
                var relativeAnchor = new Matrix4f(view).invert().transformPosition(new Vector3f(expectedCenter));
                var berPose = new Matrix4f(view).translate(relativeAnchor.x, relativeAnchor.y - localY, relativeAnchor.z);
                var savedPose = new Matrix4f(berPose);
                for (int phase = -1; phase < PHASES - 1; phase++) {
                    var row = new LinkedHashMap<String, Object>();
                    row.put("effect", effect);
                    row.put("phase", phase);
                    row.put("shader", shader.getName());
                    CASES.add(row);
                    String context = effect + " phase=" + phase;
                    // Do not multiply view again: the 1.20.1 BER pose already includes it.
                    var center = SingularityLensProjection.centerInViewSpace(berPose, localY);
                    check(center.distance(expectedCenter) < 1.0E-4F && berPose.equals(savedPose),
                            context + ": BER center must include camera rotation exactly once, without mutating the pose");
                    berPoseChecks++;
                    var bob = phase < 0 ? new Matrix4f() : walkBob(phase * (float) Math.PI / 4);
                    var projection = new Matrix4f().perspective((float) Math.toRadians(70), 1, .05F, 256).mul(bob);
                    var savedProjection = new Matrix4f(projection);
                    var inverse = SingularityLensProjection.inverseProjection(projection);
                    var savedInverse = new Matrix4f(inverse);
                    // Independent expected eye: invert the affine bob, not any clip-space/depth point.
                    var origin = new Matrix4f(bob).invert().transformPosition(new Vector3f());
                    var actualOrigin = SingularityLensProjection.viewOrigin(inverse);
                    row.put("center", vector(center));
                    row.put("viewOrigin", vector(actualOrigin));
                    row.put("expectedOrigin", vector(origin));
                    check(actualOrigin.distance(origin) < 1.0E-5F && projection.equals(savedProjection)
                                    && inverse.equals(savedInverse),
                            context + ": production origin must equal the inverse-bob eye without mutating matrices");
                    check(phase < 0 ? origin.length() < 1.0E-6F : origin.length() > .01F,
                            context + ": walking must actually translate the ray origin");
                    originChecks++;
                    // Pixel location and foreground depth come from the independent world anchor.
                    var projected = projection.transform(new Vector4f(expectedCenter, 1));
                    check(projected.w > 0, context + ": expected anchor must be in front of the camera");
                    int x = (int) ((projected.x / projected.w * .5F + .5F) * SIZE);
                    int y = (int) ((projected.y / projected.w * .5F + .5F) * SIZE);
                    check(x >= 0 && x < SIZE && y >= 0 && y < SIZE, context + ": anchor must be inside the fixture");
                    row.put("pixel", new int[]{x, y});
                    draw(scene, output, shader, projection, inverse, actualOrigin, center, radius, field, white, 1);
                    int[] pixel = pixel(x, y);
                    row.put("coreRgb", pixel);
                    check(white ? pixel[0] > 245 && pixel[1] > 245 && pixel[2] > 245
                                    : pixel[0] < 10 && pixel[1] < 10 && pixel[2] < 10,
                            context + ": world center must hit its actual core; RGB=" + Arrays.toString(pixel));
                    coreChecks++;
                    // A point halfway from this same eye to the core must occlude the actual GLSL.
                    var foreground = new Vector3f(expectedCenter).sub(origin).mul(.5F).add(origin);
                    var clip = projection.transform(new Vector4f(foreground, 1));
                    double foregroundDepth = (clip.z / clip.w) * .5 + .5;
                    check(foregroundDepth > 0 && foregroundDepth < 1, context + ": invalid foreground depth");
                    row.put("foregroundDepth", foregroundDepth);
                    draw(scene, output, shader, projection, inverse, actualOrigin, center, radius, field, white, foregroundDepth);
                    pixel = pixel(x, y);
                    row.put("foregroundRgb", pixel);
                    check(Math.abs(pixel[0] - 31) <= 1 && Math.abs(pixel[1] - 46) <= 1 && Math.abs(pixel[2] - 61) <= 1,
                            context + ": foreground must remain unchanged; RGB=" + Arrays.toString(pixel));
                    depthChecks++;
                    row.put("passed", true);
                }
            }
            checkGl("after all lens draws");
        } finally {
            if (scene != null) scene.destroyBuffers();
            if (output != null) output.destroyBuffers();
            RenderSystem.setShader(() -> previousShader);
            state.restore();
        }
    }

    private static void checkProgram(ShaderInstance shader) {
        check(GL20.glGetProgrami(shader.getId(), GL20.GL_LINK_STATUS) == GL11.GL_TRUE,
                "Production shader did not link: " + shader.getName());
        for (String name : new String[]{"SceneColor", "SceneDepth"}) {
            check(GL20.glGetUniformLocation(shader.getId(), name) >= 0,
                    "Production shader is missing active sampler " + shader.getName() + "/" + name);
        }
    }

    private static Uniform uniform(ShaderInstance shader, String name) {
        var uniform = shader.getUniform(name);
        check(uniform != null, "Production shader is missing active uniform " + shader.getName() + "/" + name);
        return uniform;
    }

    private static void draw(TextureTarget scene, TextureTarget output, ShaderInstance shader, Matrix4f projection,
            Matrix4f inverse, Vector3f origin, Vector3f center, float radius, float field, boolean white, double sceneDepth) {
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
        uniform(shader, "SceneSize").set((float) SIZE, (float) SIZE);
        uniform(shader, "CoreCenter").set(center);
        uniform(shader, "ViewOrigin").set(origin);
        uniform(shader, "LensProjection").set(projection);
        uniform(shader, "InverseProjection").set(inverse);
        uniform(shader, "CoreRadius").set(radius);
        uniform(shader, "FieldRadius").set(field);
        uniform(shader, "LensTime").set(0F);
        if (shader == OmniShaders.cosmicLens()) uniform(shader, "HoleKind").set(white ? 1F : 0F);
        // Forge 1.20.1's BufferBuilder/Tesselator API, just as used by the production post-renderers.
        var mesh = Tesselator.getInstance().getBuilder();
        mesh.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        mesh.vertex(-1, -1, 0).endVertex(); mesh.vertex(1, -1, 0).endVertex();
        mesh.vertex(1, 1, 0).endVertex(); mesh.vertex(-1, 1, 0).endVertex();
        BufferUploader.drawWithShader(mesh.end());
        checkGl("drawing " + shader.getName());
    }

    /** Exact GameRenderer.bobView PoseStack operations in Forge 47.4.20; phase = f1 * PI, f2 = 0.1. */
    private static Matrix4f walkBob(float phase) {
        float bob = .1F;
        var poses = new PoseStack();
        poses.translate(Mth.sin(phase) * bob * .5F, -Math.abs(Mth.cos(phase) * bob), 0);
        poses.mulPose(Axis.ZP.rotationDegrees(Mth.sin(phase) * bob * 3));
        poses.mulPose(Axis.XP.rotationDegrees(Math.abs(Mth.cos(phase - .2F) * bob) * 5));
        return new Matrix4f(poses.last().pose());
    }

    private static float[] vector(Vector3f value) { return new float[]{value.x, value.y, value.z}; }

    private static int[] pixel(int x, int y) {
        var pixel = BufferUtils.createByteBuffer(4);
        GL11.glReadPixels(x, y, 1, 1, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixel);
        checkGl("reading GPU pixel");
        return new int[]{pixel.get(0) & 255, pixel.get(1) & 255, pixel.get(2) & 255};
    }

    private static void checkGl(String operation) {
        int error = GL11.glGetError();
        check(error == GL11.GL_NO_ERROR, operation + ": OpenGL error 0x" + Integer.toHexString(error));
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    /** Restore state even on an assertion failure, without rebinding the wrong main framebuffer. */
    private static final class SavedGlState {
        private final int readTarget = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        private final int drawTarget = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        private final int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        private final int activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        private final int[] textures = new int[2];
        private final int[] viewport = new int[4];
        private final int[] scissorBox = new int[4];
        private final float[] clearColor = new float[4];
        private final double clearDepth = GL11.glGetDouble(GL11.GL_DEPTH_CLEAR_VALUE);
        private final java.nio.ByteBuffer colorMask = BufferUtils.createByteBuffer(4);
        private final boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        private final boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        private final boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        private final boolean scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        private final boolean srgb = GL11.glIsEnabled(GL30.GL_FRAMEBUFFER_SRGB);
        private final boolean depthWrite = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        private final int srcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
        private final int dstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        private final int srcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
        private final int dstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        private final int equationRgb = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_RGB);
        private final int equationAlpha = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_ALPHA);

        private SavedGlState() {
            GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
            GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, scissorBox);
            GL11.glGetFloatv(GL11.GL_COLOR_CLEAR_VALUE, clearColor);
            GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK, colorMask);
            for (int i = 0; i < textures.length; i++) {
                RenderSystem.activeTexture(GL13.GL_TEXTURE0 + i);
                textures[i] = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            }
            RenderSystem.activeTexture(activeTexture);
        }

        private void restore() {
            GL11.glClearDepth(clearDepth);
            RenderSystem.clearColor(clearColor[0], clearColor[1], clearColor[2], clearColor[3]);
            RenderSystem.colorMask(colorMask.get(0) != 0, colorMask.get(1) != 0,
                    colorMask.get(2) != 0, colorMask.get(3) != 0);
            RenderSystem.depthMask(depthWrite);
            RenderSystem.blendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
            GL20.glBlendEquationSeparate(equationRgb, equationAlpha);
            if (depth) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
            if (blend) RenderSystem.enableBlend(); else RenderSystem.disableBlend();
            if (cull) RenderSystem.enableCull(); else RenderSystem.disableCull();
            if (scissor) RenderSystem.enableScissor(scissorBox[0], scissorBox[1], scissorBox[2], scissorBox[3]);
            else RenderSystem.disableScissor();
            if (srgb) GL11.glEnable(GL30.GL_FRAMEBUFFER_SRGB); else GL11.glDisable(GL30.GL_FRAMEBUFFER_SRGB);
            for (int i = 0; i < textures.length; i++) {
                RenderSystem.activeTexture(GL13.GL_TEXTURE0 + i);
                RenderSystem.bindTexture(textures[i]);
            }
            RenderSystem.activeTexture(activeTexture);
            GL20.glUseProgram(program);
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readTarget);
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawTarget);
            RenderSystem.viewport(viewport[0], viewport[1], viewport[2], viewport[3]);
        }
    }
}
