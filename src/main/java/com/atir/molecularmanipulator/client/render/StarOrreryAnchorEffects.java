package com.atir.molecularmanipulator.client.render;

import com.atir.molecularmanipulator.starcomputation.StarOrreryLayout;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/** Fixed datum frames with tier-coloured travelling scans and centred pulsing nuclei. */
public final class StarOrreryAnchorEffects {
    public static final double DETAIL_DISTANCE = 48D;
    public static final double DATUM_DISTANCE = 112D;
    public static final float CYCLE_SECONDS = 3.2F;
    private static final float EXTENT = .202F;
    private static final float GAP = .065F;
    private static final float WIDTH = .007F;
    private static final BlockPos[][] MARKERS = markers();

    private StarOrreryAnchorEffects() {}

    private static BlockPos[][] markers() {
        var result = new BlockPos[StarOrreryLayout.RINGS][];
        for (int ring = 0; ring < result.length; ring++) {
            result[ring] = new BlockPos[StarOrreryLayout.slotCount(ring)];
            for (int slot = 0; slot < result[ring].length; slot++) {
                result[ring][slot] = StarOrreryLayout.offset(Direction.NORTH, ring, slot);
            }
        }
        return result;
    }

    static BlockPos marker(int ring, int slot) { return MARKERS[ring][slot]; }

    /** Distance is measured to each real anchor, not just to the controller. */
    static int detailLevel(double distanceSqr) {
        if (distanceSqr >= DATUM_DISTANCE * DATUM_DISTANCE) return 0;
        return distanceSqr >= DETAIL_DISTANCE * DETAIL_DISTANCE ? 1 : 2;
    }

    /** Scheduling only: keep small distant passes synchronous when GPU models already leave little overlap. */
    static boolean hasParallelWork(int lit, Vec3 camera) {
        int detailed = 0;
        for (int ring = 0; ring < lit; ring++) for (var marker : MARKERS[ring]) {
            double dx = camera.x - marker.getX(), dy = camera.y - marker.getY(), dz = camera.z - marker.getZ();
            if (detailLevel(dx * dx + dy * dy + dz * dz) == 2 && ++detailed >= 12) return true;
        }
        return false;
    }

    /** Shared tier identity on the frame, nucleus and moving scan, not just a tiny LED. */
    public static int tierColor(int ring) { return StarOrreryEffects.tierColor(ring); }

    /** Continuous local operation, staggered so the entire array never flashes in unison. */
    static float scanProgress(int ring, int slot, float time) {
        int count = MARKERS[ring].length;
        if (slot < 0 || slot >= count) throw new IndexOutOfBoundsException("Anchor slot " + slot);
        double phase = time / (double) CYCLE_SECONDS - slot * .61803398875D - ring * .173D;
        return (float) (phase - Math.floor(phase));
    }

    private static int segmentColor(int accent, int segment, float phase, boolean animated) {
        float travel = phase * 24F;
        float distance = Math.abs(segment - travel);
        distance = Math.min(distance, 24F - distance);
        float highlight = animated ? Math.max(0F, 1F - distance / 3F) : 0F;
        int body = mix(0x354047, accent, .70F);
        return mix(body, mix(accent, 0xFFF5DE, .28F), highlight);
    }

    /** The pose and camera are in the shared NORTH layout, before any ring motion. */
    public static void render(PoseStack poses, VertexConsumer out, int litRings, int effects,
            Vec3 camera, float time, boolean glow) {
        if (out == null || glow && effects < 2) return;
        int lit = com.atir.molecularmanipulator.util.MathCompat.clamp(litRings, 0, StarOrreryLayout.RINGS);
        renderRange(poses, out, 0, lit, effects, camera, time, glow);
    }

    /** Disjoint ring ranges retain the original ring/slot order when their results are concatenated. */
    static void renderRange(PoseStack poses, VertexConsumer out, int ringFrom, int ringTo, int effects,
            Vec3 camera, float time, boolean glow) {
        if (out == null || glow && effects < 2) return;
        var pose = poses.last();
        for (int ring = ringFrom; ring < ringTo; ring++) {
            for (int slot = 0; slot < MARKERS[ring].length; slot++) {
                var p = MARKERS[ring][slot];
                double dx = camera.x - p.getX(), dy = camera.y - p.getY(), dz = camera.z - p.getZ();
                int detail = detailLevel(dx * dx + dy * dy + dz * dz);
                if (glow && detail != 2) continue;
                float x = p.getX(), y = p.getY(), z = p.getZ();
                int accent = tierColor(ring);
                boolean animated = effects > 0;
                float phase = animated ? scanProgress(ring, slot, time) : 0F;
                if (glow) {
                    scan(pose, out, x, y, z, phase, .009F, accent, 28);
                    continue;
                }
                float pulse = animated ? .5F - .5F * (float) Math.cos(phase * Math.PI * 2D) : 0F;
                cell(pose, out, x, y, z, .033F + pulse * .009F, accent, pulse);
                if (detail == 2) {
                    int segment = 0;
                    for (int plane = 0; plane < 3; plane++) {
                        for (int sign = -1; sign <= 1; sign += 2) {
                            for (int half = -1; half <= 1; half += 2) {
                                float from = half < 0 ? -EXTENT : GAP;
                                float to = half < 0 ? -GAP : EXTENT;
                                planeLine(pose, out, x, y, z, plane, sign * EXTENT, from,
                                        sign * EXTENT, to, segmentColor(accent, segment++, phase, animated));
                                planeLine(pose, out, x, y, z, plane, from, sign * EXTENT,
                                        to, sign * EXTENT, segmentColor(accent, segment++, phase, animated));
                            }
                        }
                    }
                    line(pose, out, x + .13F, y + EXTENT, z, x + .19F, y + EXTENT, z,
                            WIDTH, accent, 255);
                    // The moving scan has a real colored depth-pass body: it remains
                    // readable without additive bloom or a particular shader pack.
                    if (animated) scan(pose, out, x, y, z, phase, .005F,
                            mix(accent, 0xFFF5DE, .22F), 255);
                } else if (detail == 1) {
                    int color = mix(accent, 0xFFF5DE, pulse * .25F);
                    line(pose, out, x - .11F, y, z, x + .11F, y, z, WIDTH, color, 255);
                    line(pose, out, x, y - .11F, z, x, y + .11F, z, WIDTH, color, 255);
                    line(pose, out, x, y, z - .11F, x, y, z + .11F, WIDTH, color, 255);
                }
            }
        }
    }

    private static void planeLine(PoseStack.Pose pose, VertexConsumer out, float x, float y, float z,
            int plane, float a, float b, float c, float d, int rgb) {
        switch (plane) {
            case 0 -> line(pose, out, x + a, y + b, z, x + c, y + d, z, WIDTH, rgb, 255);
            case 1 -> line(pose, out, x + a, y, z + b, x + c, y, z + d, WIDTH, rgb, 255);
            case 2 -> line(pose, out, x, y + a, z + b, x, y + c, z + d, WIDTH, rgb, 255);
            default -> throw new IllegalArgumentException("Datum plane " + plane);
        }
    }

    private static void scan(PoseStack.Pose pose, VertexConsumer out, float x, float y, float z,
            float phase, float width, int color, int alpha) {
        float position = -.16F + .32F * (1F - Math.abs(phase * 2F - 1F));
        // Two orthogonal travelling bars, never an opaque or translucent square plane.
        line(pose, out, x + position, y - .16F, z, x + position, y + .16F, z, width, color, alpha);
        line(pose, out, x, y + position, z - .16F, x, y + position, z + .16F, width, color, alpha);
    }

    private static void cell(PoseStack.Pose pose, VertexConsumer out, float x, float y, float z,
            float r, int accent, float pulse) {
        int dark = mix(0x2C373E, accent, .65F);
        int side = mix(accent, 0xFFF5DE, .12F + pulse * .22F);
        int top = mix(accent, 0xFFF5DE, .30F + pulse * .26F);
        quad(pose,out,x-r,y-r,z-r, x+r,y-r,z-r, x+r,y+r,z-r, x-r,y+r,z-r, dark,255);
        quad(pose,out,x+r,y-r,z+r, x-r,y-r,z+r, x-r,y+r,z+r, x+r,y+r,z+r, side,255);
        quad(pose,out,x-r,y-r,z+r, x-r,y-r,z-r, x-r,y+r,z-r, x-r,y+r,z+r, dark,255);
        quad(pose,out,x+r,y-r,z-r, x+r,y-r,z+r, x+r,y+r,z+r, x+r,y+r,z-r, side,255);
        quad(pose,out,x-r,y+r,z-r, x+r,y+r,z-r, x+r,y+r,z+r, x-r,y+r,z+r, top,255);
        quad(pose,out,x-r,y-r,z+r, x+r,y-r,z+r, x+r,y-r,z-r, x-r,y-r,z-r, dark,255);
    }

    private static int mix(int a, int b, float amount) {
        int red = Math.round((a >> 16 & 255) * (1F - amount) + (b >> 16 & 255) * amount);
        int green = Math.round((a >> 8 & 255) * (1F - amount) + (b >> 8 & 255) * amount);
        int blue = Math.round((a & 255) * (1F - amount) + (b & 255) * amount);
        return red << 16 | green << 8 | blue;
    }

    /** Two crossed ribbons per axis-aligned bar; the existing layers are NO_CULL. */
    private static void line(PoseStack.Pose pose, VertexConsumer out,
            float ax,float ay,float az,float bx,float by,float bz,float w,int rgb,int alpha) {
        if (ax != bx) {
            quad(pose,out,ax,ay-w,az, bx,by-w,bz, bx,by+w,bz, ax,ay+w,az, rgb,alpha);
            quad(pose,out,ax,ay,az-w, bx,by,bz-w, bx,by,bz+w, ax,ay,az+w, rgb,alpha);
        } else if (ay != by) {
            quad(pose,out,ax-w,ay,az, bx-w,by,bz, bx+w,by,bz, ax+w,ay,az, rgb,alpha);
            quad(pose,out,ax,ay,az-w, bx,by,bz-w, bx,by,bz+w, ax,ay,az+w, rgb,alpha);
        } else {
            quad(pose,out,ax-w,ay,az, bx-w,by,bz, bx+w,by,bz, ax+w,ay,az, rgb,alpha);
            quad(pose,out,ax,ay-w,az, bx,by-w,bz, bx,by+w,bz, ax,ay+w,az, rgb,alpha);
        }
    }

    private static void quad(PoseStack.Pose pose, VertexConsumer out,
            float ax,float ay,float az,float bx,float by,float bz,float cx,float cy,float cz,
            float dx,float dy,float dz,int rgb,int alpha) {
        int color = OmniRenderGeometry.argb(rgb, alpha);
        out.vertex(pose.pose(),ax,ay,az).color(color).endVertex(); out.vertex(pose.pose(),bx,by,bz).color(color).endVertex();
        out.vertex(pose.pose(),cx,cy,cz).color(color).endVertex(); out.vertex(pose.pose(),dx,dy,dz).color(color).endVertex();
    }
}
