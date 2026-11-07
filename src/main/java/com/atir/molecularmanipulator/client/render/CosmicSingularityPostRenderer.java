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
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Lenses the completed scene around the independently placed holes. */
@EventBusSubscriber(modid = MolecularManipulator.MOD_ID, value = Dist.CLIENT)
public final class CosmicSingularityPostRenderer {
    private record Hole(Matrix4f pose, boolean white, float time, double distanceSqr,
            float coreRadius, float fieldRadius) {}

    private static final List<Hole> PENDING = new ArrayList<>();
    private static RenderTarget scene;

    private CosmicSingularityPostRenderer() {}

    public static void enqueue(PoseStack poses, boolean white, float time, double distanceSqr) {
        enqueue(poses, white, time, distanceSqr, 0.30F, 1.25F);
    }

    /** The laboratory uses the placed holes' shader at its authored aperture sizes. */
    public static void enqueue(PoseStack poses, boolean white, float time, double distanceSqr,
            float coreRadius, float fieldRadius) {
        PENDING.add(new Hole(new Matrix4f(poses.last().pose()), white, time, distanceSqr,
                coreRadius, fieldRadius));
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_SKY) {
            PENDING.clear();
            return;
        }
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) return;
        if (PENDING.isEmpty() || OmniShaders.cosmicLens() == null) {
            PENDING.clear();
            return;
        }

        var target = Minecraft.getInstance().getMainRenderTarget();
        var previousShader = RenderSystem.getShader();
        int readTarget = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int drawTarget = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        boolean depthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean depthWrite = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        var modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        try {
            ensureTarget(target);
            modelView.set(event.getModelViewMatrix());
            RenderSystem.applyModelViewMatrix();
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.disableBlend();
            RenderSystem.disableCull();
            RenderSystem.setShader(OmniShaders::cosmicLens);
            PENDING.sort(Comparator.comparingDouble(Hole::distanceSqr).reversed());
            for (var hole : PENDING) {
                var center = SingularityLensProjection.centerInViewSpace(hole.pose, 0, event.getModelViewMatrix());
                var bounds = SingularityLensProjection.screenBounds(center, hole.fieldRadius,
                        event.getProjectionMatrix(), target.width, target.height);
                if (bounds.isEmpty()) continue;
                // Refraction samples up to 12% of the screen height beyond the output pixel.
                copyScene(target, bounds.expand((int) Math.ceil(target.height * .12D) + 2,
                        target.width, target.height));
                draw(hole, event.getModelViewMatrix(), event.getProjectionMatrix(), bounds);
            }
        } finally {
            PENDING.clear();
            modelView.popMatrix();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setShader(() -> previousShader);
            RenderSystem.depthMask(depthWrite);
            if (depthTest) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
            if (blend) RenderSystem.enableBlend(); else RenderSystem.disableBlend();
            if (cull) RenderSystem.enableCull(); else RenderSystem.disableCull();
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readTarget);
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawTarget);
        }
    }

    private static void ensureTarget(RenderTarget target) {
        if (scene != null && (scene.width != target.width || scene.height != target.height
                || scene.isStencilEnabled() != target.isStencilEnabled())) {
            scene.destroyBuffers();
            scene = null;
        }
        if (scene == null) {
            scene = new TextureTarget(target.width, target.height, true, Minecraft.ON_OSX);
            if (target.isStencilEnabled()) scene.enableStencil();
            scene.setFilterMode(GL11.GL_LINEAR);
        }
    }

    private static void copyScene(RenderTarget target, SingularityLensProjection.ScreenBounds bounds) {
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, target.frameBufferId);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, scene.frameBufferId);
        int right = bounds.x() + bounds.width(), top = bounds.y() + bounds.height();
        GlStateManager._glBlitFrameBuffer(bounds.x(), bounds.y(), right, top,
                bounds.x(), bounds.y(), right, top,
                GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT, GL11.GL_NEAREST);
        target.bindWrite(false);
    }

    private static void draw(Hole hole, Matrix4f view, Matrix4f projection,
            SingularityLensProjection.ScreenBounds bounds) {
        var shader = OmniShaders.cosmicLens();
        var center = SingularityLensProjection.centerInViewSpace(hole.pose, 0, view);
        var inverseProjection = SingularityLensProjection.inverseProjection(projection);
        shader.setSampler("SceneColor", scene.getColorTextureId());
        shader.setSampler("SceneDepth", scene.getDepthTextureId());
        shader.safeGetUniform("SceneSize").set((float) scene.width, (float) scene.height);
        shader.safeGetUniform("CoreCenter").set(center);
        shader.safeGetUniform("ViewOrigin").set(SingularityLensProjection.viewOrigin(inverseProjection));
        shader.safeGetUniform("LensProjection").set(projection);
        shader.safeGetUniform("InverseProjection").set(inverseProjection);
        shader.safeGetUniform("LensTime").set(hole.time);
        shader.safeGetUniform("CoreRadius").set(hole.coreRadius);
        shader.safeGetUniform("FieldRadius").set(hole.fieldRadius);
        shader.safeGetUniform("HoleKind").set(hole.white ? 1.0F : 0.0F);
        float left = 2F * bounds.x() / scene.width - 1F, bottom = 2F * bounds.y() / scene.height - 1F;
        float right = 2F * (bounds.x() + bounds.width()) / scene.width - 1F;
        float top = 2F * (bounds.y() + bounds.height()) / scene.height - 1F;
        var mesh = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        mesh.addVertex(left, bottom, 0); mesh.addVertex(right, bottom, 0);
        mesh.addVertex(right, top, 0); mesh.addVertex(left, top, 0);
        BufferUploader.drawWithShader(mesh.buildOrThrow());
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { release(); }

    public static void release() {
        RenderSystem.assertOnRenderThreadOrInit();
        PENDING.clear();
        if (scene != null) {
            int readTarget = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
            int drawTarget = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
            scene.destroyBuffers();
            scene = null;
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readTarget);
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawTarget);
        }
    }
}
