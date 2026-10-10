package com.atir.molecularmanipulator.client.render;

import com.atir.molecularmanipulator.MolecularManipulator;
import com.atir.molecularmanipulator.blockentity.GhostMatterBlockEntity;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/** Continuous exposure washes and refracts the world/hand image while keeping the HUD readable. */
@EventBusSubscriber(modid = MolecularManipulator.MOD_ID, value = Dist.CLIENT)
public final class GhostMatterExposureRenderer {
    private static RenderTarget scene;
    private static float previousIntensity, intensity;
    private GhostMatterExposureRenderer() {}

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var mc = Minecraft.getInstance();
        previousIntensity = intensity;
        boolean exposed = false;
        if (mc.level != null && mc.player != null && mc.player.isAlive()
                && !mc.player.isCreative() && !mc.player.isSpectator() && !mc.player.isInWaterOrBubble()) {
            var center = BlockPos.containing(mc.player.getBoundingBox().getCenter());
            for (var pos : BlockPos.betweenClosed(center.offset(-2, -2, -2), center.offset(2, 2, 2))) {
                if (mc.level.getBlockEntity(pos) instanceof GhostMatterBlockEntity matter && matter.affects(mc.player)) {
                    exposed = true;
                    break;
                }
            }
        }
        intensity = Mth.clamp(intensity + (exposed ? .2F : -.1F), 0, 1);
        if (mc.level == null || mc.player == null) previousIntensity = intensity = 0;
    }

    public static float intensity() { return intensity; }

    public static void render(float partialTick) {
        var mc = Minecraft.getInstance();
        float strength = Mth.lerp(partialTick, previousIntensity, intensity) * mc.options.screenEffectScale().get().floatValue();
        if (strength <= .001F || mc.level == null || OmniShaders.ghostExposure() == null) return;
        var target = mc.getMainRenderTarget();
        var previousShader = RenderSystem.getShader();
        int read = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int draw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean depthWrite = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        try {
            if (scene != null && (scene.width != target.width || scene.height != target.height)) {
                scene.destroyBuffers();
                scene = null;
            }
            if (scene == null) {
                scene = new TextureTarget(target.width, target.height, false, Minecraft.ON_OSX);
                scene.setFilterMode(GL11.GL_LINEAR);
            }
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, target.frameBufferId);
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, scene.frameBufferId);
            GlStateManager._glBlitFrameBuffer(0, 0, target.width, target.height, 0, 0, scene.width, scene.height,
                    GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
            target.bindWrite(false);
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.disableBlend();
            RenderSystem.disableCull();
            RenderSystem.setShader(OmniShaders::ghostExposure);
            var shader = OmniShaders.ghostExposure();
            shader.setSampler("SceneColor", scene.getColorTextureId());
            shader.safeGetUniform("SceneSize").set((float) scene.width, (float) scene.height);
            shader.safeGetUniform("Exposure").set(strength);
            shader.safeGetUniform("ExposureTime").set((mc.level.getGameTime() + partialTick) / 20F);
            var mesh = Tesselator.getInstance().getBuilder();
            mesh.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
            mesh.vertex(-1, -1, 0).endVertex(); mesh.vertex(1, -1, 0).endVertex();
            mesh.vertex(1, 1, 0).endVertex(); mesh.vertex(-1, 1, 0).endVertex();
            BufferUploader.drawWithShader(mesh.end());
        } finally {
            RenderSystem.setShader(() -> previousShader);
            RenderSystem.depthMask(depthWrite);
            if (depth) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
            if (blend) RenderSystem.enableBlend(); else RenderSystem.disableBlend();
            if (cull) RenderSystem.enableCull(); else RenderSystem.disableCull();
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, read);
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, draw);
        }
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { release(); }

    public static void release() {
        previousIntensity = intensity = 0;
        if (scene != null) { scene.destroyBuffers(); scene = null; }
    }
}
