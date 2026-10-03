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
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Lenses the completed scene around the independently placed holes. */
@EventBusSubscriber(modid = MolecularManipulator.MOD_ID, value = Dist.CLIENT)
public final class CosmicSingularityPostRenderer {
    private record Hole(Matrix4f pose, boolean white, float time, double distanceSqr) {}

    private static final List<Hole> PENDING = new ArrayList<>();
    private static RenderTarget scene;

    private CosmicSingularityPostRenderer() {}

    public static void enqueue(PoseStack poses, boolean white, float time, double distanceSqr) {
        PENDING.add(new Hole(new Matrix4f(poses.last().pose()), white, time, distanceSqr));
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
        modelView.pushPose();
        try {
            ensureTarget(target);
            modelView.setIdentity();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.disableBlend();
            RenderSystem.disableCull();
            RenderSystem.setShader(OmniShaders::cosmicLens);
            PENDING.sort(Comparator.comparingDouble(Hole::distanceSqr).reversed());
            for (var hole : PENDING) {
                copyScene(target);
                draw(hole, event.getProjectionMatrix());
            }
        } finally {
            PENDING.clear();
            modelView.popPose();
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

    private static void copyScene(RenderTarget target) {
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, target.frameBufferId);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, scene.frameBufferId);
        GlStateManager._glBlitFrameBuffer(0, 0, target.width, target.height, 0, 0, scene.width, scene.height,
                GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT, GL11.GL_NEAREST);
        target.bindWrite(false);
    }

    private static void draw(Hole hole, Matrix4f projection) {
        var shader = OmniShaders.cosmicLens();
        var center = hole.pose.transformPosition(0, 0, 0, new Vector3f());
        // Forge BER poses already contain the camera view rotation. AFTER_LEVEL
        // supplies a projection pose, so applying it here projects the center twice.
        shader.setSampler("SceneColor", scene.getColorTextureId());
        shader.setSampler("SceneDepth", scene.getDepthTextureId());
        shader.safeGetUniform("SceneSize").set((float) scene.width, (float) scene.height);
        shader.safeGetUniform("CoreCenter").set(center);
        shader.safeGetUniform("LensProjection").set(projection);
        shader.safeGetUniform("InverseProjection").set(new Matrix4f(projection).invert());
        shader.safeGetUniform("LensTime").set(hole.time);
        shader.safeGetUniform("CoreRadius").set(0.30F);
        shader.safeGetUniform("FieldRadius").set(1.25F);
        shader.safeGetUniform("HoleKind").set(hole.white ? 1.0F : 0.0F);
        var mesh = Tesselator.getInstance().getBuilder();
        mesh.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        mesh.vertex(-1, -1, 0).endVertex(); mesh.vertex(1, -1, 0).endVertex();
        mesh.vertex(1, 1, 0).endVertex(); mesh.vertex(-1, 1, 0).endVertex();
        BufferUploader.drawWithShader(mesh.end());
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
