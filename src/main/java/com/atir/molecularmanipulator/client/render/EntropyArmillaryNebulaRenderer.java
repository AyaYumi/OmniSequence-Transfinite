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
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/** Depth-clipped nebula volume, following the project's cosmic/singularity lens composition pipeline. */
@EventBusSubscriber(modid = MolecularManipulator.MOD_ID, value = Dist.CLIENT)
public final class EntropyArmillaryNebulaRenderer {
    // The galaxy fills the inner two belts; the functional core and anchors stay fixed.
    public static final float FIELD_RADIUS = 13.4F;
    private record Volume(Matrix4f pose, float time, float flowTime, int quality, double distanceSqr,
            float fogStart, float fogEnd, int fogShape) {}
    private static final List<Volume> PENDING = new ArrayList<>();
    private static RenderTarget scene;

    private EntropyArmillaryNebulaRenderer() {}

    static boolean visible(int lit, double distanceSqr) { return lit > 0; }

    public static void enqueue(PoseStack poses, int lit, float time, float flowTime, int quality, double distanceSqr) {
        if (visible(lit,distanceSqr)) {
            // AFTER_LEVEL runs after vanilla disables terrain fog. Snapshot it here,
            // while the block entity is being drawn, and use separate uniform names.
            PENDING.add(new Volume(new Matrix4f(poses.last().pose()), quality>0?time:0F,
                    quality>0?flowTime:0F,quality,distanceSqr,RenderSystem.getShaderFogStart(),
                    RenderSystem.getShaderFogEnd(),RenderSystem.getShaderFogShape().getIndex()));
        }
    }

    static Matrix4f viewToNebula(Matrix4f pose, Matrix4f view) {
        return new Matrix4f(view).mul(pose).invert();
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_SKY) { PENDING.clear(); return; }
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) return;
        if (PENDING.isEmpty() || OmniShaders.armillaryNebula() == null) { PENDING.clear(); return; }
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
            RenderSystem.setShader(OmniShaders::armillaryNebula);
            PENDING.sort(Comparator.comparingDouble(Volume::distanceSqr).reversed());
            for (var volume : PENDING) {
                var clipFromLocal = new Matrix4f(event.getProjectionMatrix())
                        .mul(event.getModelViewMatrix()).mul(volume.pose);
                var bounds = SingularityLensProjection.screenBounds(clipFromLocal, FIELD_RADIUS,
                        target.width, target.height);
                if (bounds.isEmpty()) continue;
                copyScene(target, bounds);
                draw(volume,event.getModelViewMatrix(),event.getProjectionMatrix(),bounds);
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
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,readTarget);
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,drawTarget);
        }
    }

    private static void ensureTarget(RenderTarget target) {
        if (scene != null && (scene.width != target.width || scene.height != target.height
                || scene.isStencilEnabled() != target.isStencilEnabled())) {
            scene.destroyBuffers(); scene=null;
        }
        if (scene == null) {
            scene=new TextureTarget(target.width,target.height,true,Minecraft.ON_OSX);
            if (target.isStencilEnabled()) scene.enableStencil();
            scene.setFilterMode(GL11.GL_NEAREST);
        }
    }

    private static void copyScene(RenderTarget target, SingularityLensProjection.ScreenBounds bounds) {
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,target.frameBufferId);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,scene.frameBufferId);
        int right = bounds.x() + bounds.width(), top = bounds.y() + bounds.height();
        GlStateManager._glBlitFrameBuffer(bounds.x(),bounds.y(),right,top,bounds.x(),bounds.y(),right,top,
                GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT,GL11.GL_NEAREST);
        target.bindWrite(false);
    }

    private static void draw(Volume volume, Matrix4f view, Matrix4f projection,
            SingularityLensProjection.ScreenBounds bounds) {
        var shader=OmniShaders.armillaryNebula();
        shader.setSampler("SceneColor",scene.getColorTextureId());
        shader.setSampler("SceneDepth",scene.getDepthTextureId());
        shader.safeGetUniform("SceneSize").set((float)scene.width,(float)scene.height);
        var inverse=SingularityLensProjection.inverseProjection(projection);
        shader.safeGetUniform("InverseProjection").set(inverse);
        shader.safeGetUniform("ViewToNebula").set(viewToNebula(volume.pose,view));
        shader.safeGetUniform("RayOrigin").set(SingularityLensProjection.rayOriginInViewSpace(inverse));
        shader.safeGetUniform("FieldRadius").set(FIELD_RADIUS);
        shader.safeGetUniform("NebulaToCamera").set(volume.pose);
        shader.safeGetUniform("VolumeFogStart").set(volume.fogStart);
        shader.safeGetUniform("VolumeFogEnd").set(volume.fogEnd);
        shader.safeGetUniform("VolumeFogShape").set(volume.fogShape);
        shader.safeGetUniform("NebulaTime").set(volume.time);
        shader.safeGetUniform("FlowTime").set(volume.flowTime);
        shader.safeGetUniform("Detail").set(EntropyArmillaryEffects.isDetailed(volume.quality,volume.distanceSqr)?1F:0F);
        shader.safeGetUniform("Dynamics").set(volume.quality>0?1F:0F);
        float left = 2F*bounds.x()/scene.width-1F, bottom = 2F*bounds.y()/scene.height-1F;
        float right = 2F*(bounds.x()+bounds.width())/scene.width-1F;
        float top = 2F*(bounds.y()+bounds.height())/scene.height-1F;
        var mesh=Tesselator.getInstance().begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION);
        mesh.addVertex(left,bottom,0);mesh.addVertex(right,bottom,0);
        mesh.addVertex(right,top,0);mesh.addVertex(left,top,0);
        BufferUploader.drawWithShader(mesh.buildOrThrow());
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { release(); }

    public static void release() {
        RenderSystem.assertOnRenderThreadOrInit();
        PENDING.clear();
        if (scene != null) {
            int read=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
            int draw=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
            scene.destroyBuffers();scene=null;
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,read);
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,draw);
        }
    }
}
