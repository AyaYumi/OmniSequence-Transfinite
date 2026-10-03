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
import net.minecraft.world.phys.Vec3;
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

/** White core and scene lens, composited after vanilla or Iris finishes the world. */
@EventBusSubscriber(modid = MolecularManipulator.MOD_ID, value = Dist.CLIENT)
public final class SingularityWhiteHoleRenderer {
    private record Lens(Matrix4f pose, SingularityStructureEffects.Frame frame, Vec3 eye) {}
    private static final List<Lens> PENDING = new ArrayList<>();
    private static RenderTarget scene;

    private SingularityWhiteHoleRenderer() {}

    public static void enqueue(PoseStack poses, SingularityStructureEffects.Frame frame, Vec3 eye) {
        PENDING.add(new Lens(new Matrix4f(poses.last().pose()), frame, eye));
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_SKY) {
            PENDING.clear();
            return;
        }
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) return;
        if (PENDING.isEmpty() || OmniShaders.singularityLens() == null) {
            PENDING.clear();
            return;
        }

        var mc = Minecraft.getInstance();
        var target = mc.getMainRenderTarget();
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
            // AFTER_LEVEL follows Fabulous composition and Iris finalization. The separate
            // snapshot prevents reading from the framebuffer we are drawing into.
            ensureTarget(target);
            modelView.last().pose().set(event.getPoseStack().last().pose());
            RenderSystem.applyModelViewMatrix();
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.disableBlend();
            RenderSystem.disableCull();
            RenderSystem.setShader(OmniShaders::singularityLens);
            PENDING.sort(Comparator.comparingDouble((Lens lens) -> lens.eye.subtract(0, 12.5, 0).lengthSqr()).reversed());
            for (var lens : PENDING) {
                copyScene(target);
                draw(lens, event.getPoseStack().last().pose(), event.getProjectionMatrix());
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

    private static void draw(Lens lens, Matrix4f view, Matrix4f projection) {
        var shader = OmniShaders.singularityLens();
        var center = lens.pose.transformPosition(0, 12.5F, 0, new Vector3f());
        view.transformPosition(center);
        shader.setSampler("SceneColor", scene.getColorTextureId());
        shader.setSampler("SceneDepth", scene.getDepthTextureId());
        shader.safeGetUniform("SceneSize").set((float) scene.width, (float) scene.height);
        shader.safeGetUniform("CoreCenter").set(center);
        shader.safeGetUniform("LensProjection").set(projection);
        shader.safeGetUniform("InverseProjection").set(new Matrix4f(projection).invert());
        shader.safeGetUniform("LensTime").set(lens.frame.time() % 24000);
        shader.safeGetUniform("CoreRadius").set(SingularityWhiteHoleProfile.radius(lens.frame));
        shader.safeGetUniform("FieldRadius").set(SingularityWhiteHoleProfile.FIELD_RADIUS);
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
