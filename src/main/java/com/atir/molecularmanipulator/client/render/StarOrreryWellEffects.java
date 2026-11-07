package com.atir.molecularmanipulator.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;

/** Transparent, outward-bleeding ink in the open well, not geometric concentric light rails. */
public final class StarOrreryWellEffects {
    public static final double SURFACE_Y = -34.72D;
    public static final float MAX_RADIUS = 4.94F;

    private StarOrreryWellEffects() {}

    static boolean visible(int lit, int quality, double distanceSqr) {
        return lit > 0 && StarOrreryEffects.effectsVisible(quality, distanceSqr);
    }

    public static void render(PoseStack poses, MultiBufferSource buffers, int lit, int quality,
            double distanceSqr) {
        if (!visible(lit, quality, distanceSqr) || OmniShaders.starOrreryWellInk() == null) return;
        surface(poses, buffers.getBuffer(OmniRenderLayers.starOrreryWellInk()), lit, quality, distanceSqr);
    }

    /** UVs remain in the fixed well plane; the fragment shader clips the circle and never writes depth. */
    static void surface(PoseStack poses, VertexConsumer out, int lit, int quality, double distanceSqr) {
        if (!visible(lit, quality, distanceSqr)) return;
        int color = StarOrreryEffects.tierColor(Math.clamp(lit - 1, 0, 8));
        // Alpha carries per-well detail, not opacity. This avoids shared mutable
        // uniforms when multiple machines of different tiers/LODs share a batch.
        int detail = StarOrreryEffects.isDetailed(quality, distanceSqr) ? 255 : 128;
        vertex(poses.last(), out, -MAX_RADIUS, -MAX_RADIUS, 0, 0, color, detail);
        vertex(poses.last(), out, -MAX_RADIUS, MAX_RADIUS, 0, 1, color, detail);
        vertex(poses.last(), out, MAX_RADIUS, MAX_RADIUS, 1, 1, color, detail);
        vertex(poses.last(), out, MAX_RADIUS, -MAX_RADIUS, 1, 0, color, detail);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer out, float x, float z,
            float u, float v, int color, int detail) {
        out.addVertex(pose, x, (float) SURFACE_Y, z).setUv(u, v)
                .setColor(color >> 16 & 255, color >> 8 & 255, color & 255, detail);
    }
}
