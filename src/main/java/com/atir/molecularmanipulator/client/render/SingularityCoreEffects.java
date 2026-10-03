package com.atir.molecularmanipulator.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.world.phys.Vec3;

/** All geometry stays inside one block; normal frustum and depth occlusion apply. */
public final class SingularityCoreEffects {
    private SingularityCoreEffects() {}

    public static void render(PoseStack poses, VertexConsumer consumer, float ticks, float phase,
            boolean detailed, boolean glow) {
        float spin = ticks * 1.5F + phase;
        float bob = (float) Math.sin(Math.toRadians(ticks * 3 + phase)) * 0.025F;
        float scale = glow ? 1.08F : 1.0F;
        int cyan = OmniRenderGeometry.argb(0x8BDEF4, glow ? 52 : 175);
        int white = OmniRenderGeometry.argb(0xEAFBFF, glow ? 60 : 235);
        int gold = OmniRenderGeometry.argb(0xE8CD91, glow ? 42 : 190);
        poses.pushPose();
        poses.translate(0, bob, 0);
        OmniRenderGeometry.octahedron(poses.last(), consumer, Vec3.ZERO,
                0.155F * scale, 0.225F * scale, 0.155F * scale, spin, cyan);
        OmniRenderGeometry.octahedron(poses.last(), consumer, Vec3.ZERO,
                0.05F * scale, 0.16F * scale, 0.05F * scale, -spin, white);
        orbit(poses, consumer, spin, 28, 0.31F, detailed, glow, cyan);
        orbit(poses, consumer, -spin * 0.8F, 104, 0.37F, detailed, glow, gold);
        if (detailed) {
            // Two counter-wound rising ribbons, made from short tapered beams.
            for (int strand = 0; strand < 2; strand++) {
                for (int i = 0; i < 12; i++) {
                    double angle = Math.toRadians(spin * 2 + strand * 180 + i * 18);
                    double next = angle + Math.toRadians(18);
                    double y = -0.29 + i * 0.046;
                    OmniRenderGeometry.beam(poses.last(), consumer,
                            new Vec3(Math.cos(angle) * 0.19, y, Math.sin(angle) * 0.19),
                            new Vec3(Math.cos(next) * 0.19, y + 0.046, Math.sin(next) * 0.19),
                            glow ? 0.006F : 0.003F, glow ? 0.006F : 0.003F,
                            OmniRenderGeometry.argb(0xB5EDFF, glow ? 28 : 90));
                }
            }
        }
        poses.popPose();
    }

    private static void orbit(PoseStack poses, VertexConsumer consumer, float spin, float tilt,
            float radius, boolean detailed, boolean glow, int color) {
        poses.pushPose();
        poses.mulPose(Axis.YP.rotationDegrees(spin * 0.35F));
        poses.mulPose(Axis.XP.rotationDegrees(tilt));
        poses.mulPose(Axis.YP.rotationDegrees(spin));
        OmniRenderGeometry.torus(poses.last(), consumer, Vec3.ZERO, radius,
                glow ? 0.012F : 0.006F, detailed ? 48 : 24, 4, color);
        int motes = detailed ? 4 : 2;
        for (int i = 0; i < motes; i++) {
            double a = Math.PI * 2 * i / motes;
            OmniRenderGeometry.octahedron(poses.last(), consumer,
                    new Vec3(Math.cos(a) * radius, 0, Math.sin(a) * radius),
                    0.021F, 0.032F, 0.021F, i * 90, color);
        }
        poses.popPose();
    }
}
