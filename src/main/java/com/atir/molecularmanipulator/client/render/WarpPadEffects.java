package com.atir.molecularmanipulator.client.render;

import com.atir.molecularmanipulator.block.WarpPadGeometry;
import com.atir.molecularmanipulator.world.WarpAnimation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.Vec3;

/** Horizontal energy rings and key outlines, in the approved pad's canonical coordinates. */
public final class WarpPadEffects {
    private static final int GOLD = 0xF1CC83;
    private static final int LIGHT = 0xCCF6DF;

    private WarpPadEffects() {}

    public static void render(PoseStack poses, VertexConsumer out, int phase, float age) {
        if (!WarpAnimation.active(phase, age)) return;
        var pose = poses.last();
        var center = WarpPadGeometry.STANDING_OFFSET.add(0, .035, 0);
        boolean charging = phase == WarpAnimation.CHARGING;
        float progress = WarpAnimation.ease(age / (charging ? WarpAnimation.CHARGE_TICKS : WarpAnimation.ARRIVAL_TICKS));
        float radius = charging ? 1.12F - .92F * progress : .2F + .94F * progress;
        int alpha = charging ? Math.round(170 * WarpAnimation.ease(age / 5)) : Math.round(220 * (1 - progress));
        OmniRenderGeometry.torus(pose, out, center, radius, .012F, 64, 4,
                OmniRenderGeometry.argb(charging ? GOLD : LIGHT, alpha));
        OmniRenderGeometry.torus(pose, out, center, radius, .038F, 64, 4,
                OmniRenderGeometry.argb(charging ? GOLD : LIGHT, alpha / 4));

        if (charging) {
            for (int key = 0; key < WarpPadGeometry.KEYS.length; key++) {
                float locked = WarpAnimation.keyLock(age, key);
                if (locked <= 0) continue;
                var surface = WarpPadGeometry.KEYS[key];
                var normal = surface.normal().scale(.012);
                for (int edge = 0; edge < 4; edge++) {
                    OmniRenderGeometry.beam(pose, out, surface.vertices()[edge].add(normal),
                            surface.vertices()[(edge + 1) % 4].add(normal), .009F, .009F,
                            OmniRenderGeometry.argb(LIGHT, Math.round(190 * locked)));
                }
            }
            // Short light traces travel inward along the pad, without a vertical beam.
            for (int ray = 0; ray < 8; ray++) {
                double angle = ray * Math.PI / 4 + age * .025;
                double r = .22 + .86 * (1 - (age * .065 + ray / 8F) % 1);
                var radial = new Vec3(Math.cos(angle), 0, Math.sin(angle));
                OmniRenderGeometry.beam(pose, out, center.add(radial.scale(r)),
                        center.add(radial.scale(r + .10)), .013F, .013F,
                        OmniRenderGeometry.argb(GOLD, alpha));
            }
        } else {
            OmniRenderGeometry.torus(pose, out, center.add(0, .025, 0), radius * .7F, .009F, 48, 4,
                    OmniRenderGeometry.argb(GOLD, alpha / 2));
        }
    }
}
