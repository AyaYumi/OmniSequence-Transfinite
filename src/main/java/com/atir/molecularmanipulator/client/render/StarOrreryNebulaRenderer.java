package com.atir.molecularmanipulator.client.render;

import com.atir.molecularmanipulator.MolecularManipulator;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

/** Depth-clipped volume integration, followed by a native-resolution, depth-aware composition. */
@EventBusSubscriber(modid = MolecularManipulator.MOD_ID, value = Dist.CLIENT)
public final class StarOrreryNebulaRenderer {
    public static final float FIELD_RADIUS = 13.4F;
    private record Volume(Matrix4f pose, float time, float flowTime, int quality, double distanceSqr,
            float fogStart, float fogEnd, int fogShape) {}
    private static final List<Volume> PENDING = new ArrayList<>();
    private static RenderTarget scene, volumeTarget;

    private StarOrreryNebulaRenderer() {}

    static boolean visible(int lit, double distanceSqr) { return lit > 0; }

    public static void enqueue(PoseStack poses, int lit, float time, float flowTime, int quality, double distanceSqr) {
        if (visible(lit, distanceSqr)) {
            // AFTER_LEVEL runs after vanilla disables terrain fog. Capture its real values here.
            PENDING.add(new Volume(ForgeRenderSupport.cameraRelativePose(poses.last()), quality > 0 ? time : 0F,
                    quality > 0 ? flowTime : 0F, quality, distanceSqr, RenderSystem.getShaderFogStart(),
                    RenderSystem.getShaderFogEnd(), RenderSystem.getShaderFogShape().getIndex()));
        }
    }

    static Matrix4f viewToNebula(Matrix4f pose, Matrix4f view) {
        return new Matrix4f(view).mul(pose).invert();
    }

    static int marchDimension(int size) { return Math.max(1, (size + 1) / 2); }

    /** Tiny distant volumes remain native-resolution; no blur or extra integration pass for them. */
    static boolean reducedResolution(SingularityLensProjection.ScreenBounds bounds) {
        return (long) bounds.width() * bounds.height() >= 128L * 128;
    }

    /** Round outwards when mapping rectangles, including odd window sizes and boundary texels. */
    static SingularityLensProjection.ScreenBounds scaleBounds(SingularityLensProjection.ScreenBounds bounds,
            int fromWidth, int fromHeight, int toWidth, int toHeight) {
        int left = (int) ((long) bounds.x() * toWidth / fromWidth);
        int bottom = (int) ((long) bounds.y() * toHeight / fromHeight);
        int right = (int) (((long) (bounds.x() + bounds.width()) * toWidth + fromWidth - 1) / fromWidth);
        int top = (int) (((long) (bounds.y() + bounds.height()) * toHeight + fromHeight - 1) / fromHeight);
        return new SingularityLensProjection.ScreenBounds(left, bottom, right - left, top - bottom);
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_SKY) { PENDING.clear(); return; }
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) return;
        if (PENDING.isEmpty() || OmniShaders.starOrreryNebula() == null
                || OmniShaders.starOrreryNebulaComposite() == null) { PENDING.clear(); return; }
        var target = Minecraft.getInstance().getMainRenderTarget();
        if (target.width <= 0 || target.height <= 0) { PENDING.clear(); return; }
        try (var state = new SavedState()) {
            RenderSystem.disableScissor();
            GL11.glDisable(GL11.GL_STENCIL_TEST);
            RenderSystem.disableColorLogicOp();
            RenderSystem.colorMask(true, true, true, true);
            ensureTargets(target);
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.disableBlend();
            RenderSystem.disableCull();
            // One inverse projection/eye for every machine and both stages in this frame.
            var inverse = SingularityLensProjection.inverseProjection(event.getProjectionMatrix());
            var eye = SingularityLensProjection.viewOrigin(inverse);
            PENDING.sort(Comparator.comparingDouble(Volume::distanceSqr).reversed());
            for (var volume : PENDING) {
                var clipFromLocal = new Matrix4f(event.getProjectionMatrix())
                        .mul(ForgeRenderSupport.viewMatrix()).mul(volume.pose);
                var bounds = SingularityLensProjection.screenBounds(clipFromLocal, FIELD_RADIUS,
                        target.width, target.height);
                if (bounds.isEmpty()) continue;
                boolean reduced = reducedResolution(bounds);
                var marchBounds = scaleBounds(bounds, target.width, target.height, volumeTarget.width, volumeTarget.height)
                        .expand(1, volumeTarget.width, volumeTarget.height);
                // Every depth/color sample used by the padded low-resolution pass must be fresh.
                var sourceBounds = scaleBounds(marchBounds, volumeTarget.width, volumeTarget.height,
                        target.width, target.height).expand(1, target.width, target.height);
                copyScene(target, reduced ? sourceBounds : bounds);
                if (reduced) {
                    volumeTarget.bindWrite(true);
                    var shader = OmniShaders.starOrreryNebula();
                    configureVolume(shader, volume, ForgeRenderSupport.viewMatrix(), inverse, eye);
                    RenderSystem.setShader(() -> shader);
                    drawQuad(marchBounds, volumeTarget.width, volumeTarget.height);
                }
                target.bindWrite(true);
                var composite = OmniShaders.starOrreryNebulaComposite();
                configureVolume(composite, volume, ForgeRenderSupport.viewMatrix(), inverse, eye);
                composite.setSampler("SceneColor", scene.getColorTextureId());
                composite.setSampler("VolumeColor", volumeTarget.getColorTextureId());
                composite.safeGetUniform("SceneSize").set((float) scene.width, (float) scene.height);
                composite.safeGetUniform("MarchBounds").set((float) marchBounds.x(), (float) marchBounds.y(),
                        (float) (marchBounds.x() + marchBounds.width() - 1),
                        (float) (marchBounds.y() + marchBounds.height() - 1));
                composite.safeGetUniform("IntegrateDirect").set(reduced ? 0 : 1);
                RenderSystem.setShader(() -> composite);
                drawQuad(bounds, target.width, target.height);
            }
        } finally { PENDING.clear(); }
    }

    private static void ensureTargets(RenderTarget target) {
        if (scene != null && (scene.width != target.width || scene.height != target.height
                || scene.isStencilEnabled() != target.isStencilEnabled())) destroyTargets();
        if (scene == null) {
            scene = new TextureTarget(target.width, target.height, true, Minecraft.ON_OSX);
            if (target.isStencilEnabled()) scene.enableStencil();
            scene.setFilterMode(GL11.GL_NEAREST);
        }
        if (volumeTarget == null) {
            volumeTarget = new TextureTarget(marchDimension(target.width), marchDimension(target.height),
                    false, Minecraft.ON_OSX);
            // Radiance can exceed one. Alpha holds transmittance; an RGBA8 target clips highlights.
            GlStateManager._bindTexture(volumeTarget.getColorTextureId());
            GlStateManager._texImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_RGBA16F,
                    volumeTarget.width, volumeTarget.height, 0, GL11.GL_RGBA, GL11.GL_FLOAT, null);
            GlStateManager._bindTexture(0);
        }
    }

    private static void copyScene(RenderTarget target, SingularityLensProjection.ScreenBounds bounds) {
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, target.frameBufferId);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, scene.frameBufferId);
        int right = bounds.x() + bounds.width(), top = bounds.y() + bounds.height();
        GlStateManager._glBlitFrameBuffer(bounds.x(), bounds.y(), right, top, bounds.x(), bounds.y(), right, top,
                GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT, GL11.GL_NEAREST);
    }

    private static void configureVolume(ShaderInstance shader, Volume volume, Matrix4f view,
            Matrix4f inverse, Vector3f eye) {
        shader.setSampler("SceneDepth", scene.getDepthTextureId());
        shader.safeGetUniform("MarchSize").set((float) volumeTarget.width, (float) volumeTarget.height);
        shader.safeGetUniform("InverseProjection").set(inverse);
        shader.safeGetUniform("ViewToNebula").set(viewToNebula(volume.pose, view));
        shader.safeGetUniform("RayOrigin").set(eye);
        shader.safeGetUniform("FieldRadius").set(FIELD_RADIUS);
        shader.safeGetUniform("NebulaToCamera").set(volume.pose);
        shader.safeGetUniform("VolumeFogStart").set(volume.fogStart);
        shader.safeGetUniform("VolumeFogEnd").set(volume.fogEnd);
        shader.safeGetUniform("VolumeFogShape").set(volume.fogShape);
        shader.safeGetUniform("NebulaTime").set(volume.time);
        shader.safeGetUniform("FlowTime").set(volume.flowTime);
        shader.safeGetUniform("Detail").set(StarOrreryEffects.isDetailed(volume.quality, volume.distanceSqr) ? 1F : 0F);
        shader.safeGetUniform("Dynamics").set(volume.quality > 0 ? 1F : 0F);
    }

    private static void drawQuad(SingularityLensProjection.ScreenBounds bounds, int width, int height) {
        float left = 2F * bounds.x() / width - 1F, bottom = 2F * bounds.y() / height - 1F;
        float right = 2F * (bounds.x() + bounds.width()) / width - 1F;
        float top = 2F * (bounds.y() + bounds.height()) / height - 1F;
        var mesh = ForgeRenderSupport.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        mesh.vertex(left, bottom, 0).endVertex(); mesh.vertex(right, bottom, 0).endVertex();
        mesh.vertex(right, top, 0).endVertex(); mesh.vertex(left, top, 0).endVertex();
        BufferUploader.drawWithShader(mesh.end());
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { release(); }

    private static void destroyTargets() {
        if (scene != null) { scene.destroyBuffers(); scene = null; }
        if (volumeTarget != null) { volumeTarget.destroyBuffers(); volumeTarget = null; }
    }

    public static void release() {
        RenderSystem.assertOnRenderThreadOrInit();
        PENDING.clear();
        if (scene != null || volumeTarget != null) {
            try (var state = new SavedState()) { destroyTargets(); }
        }
    }

    /** Allocation/resize and the extra viewport/sampler must not escape into Iris or the next effect. */
    private static final class SavedState implements AutoCloseable {
        private final ForgeGlState flags = new ForgeGlState();
        private final ShaderInstance shader = RenderSystem.getShader();
        private final int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        private final int read = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        private final int draw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        private final int array = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        private final int buffer = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        private final boolean stencil = GL11.glIsEnabled(GL11.GL_STENCIL_TEST);
        private final int[] viewport = new int[4], textures = new int[3];
        private final float[] clearColor = new float[4];
        private final double clearDepth = GL11.glGetDouble(GL11.GL_DEPTH_CLEAR_VALUE);
        private final int activeTexture = GlStateManager._getActiveTexture();
        private final int activeBinding = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);

        SavedState() {
            GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
            GL11.glGetFloatv(GL11.GL_COLOR_CLEAR_VALUE, clearColor);
            for (int unit = 0; unit < textures.length; unit++) {
                GlStateManager._activeTexture(GL13.GL_TEXTURE0 + unit);
                textures[unit] = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            }
            GlStateManager._activeTexture(activeTexture);
        }

        @Override public void close() {
            RenderSystem.setShader(() -> shader);
            GlStateManager._glUseProgram(program);
            BufferUploader.invalidate();
            GlStateManager._glBindVertexArray(array);
            GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, buffer);
            for (int unit = 0; unit < textures.length; unit++) {
                GlStateManager._activeTexture(GL13.GL_TEXTURE0 + unit);
                GlStateManager._bindTexture(textures[unit]);
            }
            GlStateManager._activeTexture(activeTexture);
            GlStateManager._bindTexture(activeBinding);
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, read);
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, draw);
            RenderSystem.viewport(viewport[0], viewport[1], viewport[2], viewport[3]);
            RenderSystem.clearColor(clearColor[0], clearColor[1], clearColor[2], clearColor[3]);
            RenderSystem.clearDepth(clearDepth);
            if (stencil) GL11.glEnable(GL11.GL_STENCIL_TEST); else GL11.glDisable(GL11.GL_STENCIL_TEST);
            flags.close();
        }
    }
}
