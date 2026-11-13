package com.atir.molecularmanipulator.client.render;

import com.atir.molecularmanipulator.MolecularManipulator;
import com.atir.molecularmanipulator.world.DetachedSignalRealm;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.RenderLevelStageEvent;

/** The orrery's actual 3x5 code glyphs, released into world-space columns that drift upward and dissolve. */
@EventBusSubscriber(modid = MolecularManipulator.MOD_ID, value = Dist.CLIENT)
public final class DetachedSignalRenderer {
    private static final int CELL = 16;
    private static final int HEIGHT = 48;
    private static final int[] COLORS = {0x71BFFF, 0xBF91FF, 0xF0C77C, 0x7EE6B3, 0xEF9CC7};

    private DetachedSignalRenderer() {}

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        var mc = Minecraft.getInstance();
        if (!DetachedSignalRealm.isRealm(mc.level) || mc.player == null) return;
        var camera = event.getCamera().getPosition();
        var poses = event.getPoseStack();
        var layer = OmniRenderLayers.detachedSignalStreams();
        var flags = new ForgeGlState();
        var shader = RenderSystem.getShader();
        // The client already follows the realm's selected rate: do not scale its clock again.
        double seconds = (mc.level.getGameTime() + event.getPartialTick()) / 20D;
        boolean reduced = mc.options.particles().get() != ParticleStatus.ALL;
        int radius = reduced ? 2 : 3;
        int cellX = (int) Math.floor(camera.x / CELL), cellZ = (int) Math.floor(camera.z / CELL);
        int bandY = (int) Math.floor(camera.y / HEIGHT);
        poses.pushPose();
        try {
            // Iris batches the shared entity source and ignores endBatch(layer). At this
            // late stage, submit our own mesh so this frame's glyphs are actually drawn.
            var mesh = ForgeRenderSupport.begin(layer.mode(), layer.format());
            VertexConsumer out = layer.format() == DefaultVertexFormat.NEW_ENTITY
                    ? new SingularityShaderVertices(mesh) : mesh;
            for (int x = cellX - radius; x <= cellX + radius; x++) {
                for (int z = cellZ - radius; z <= cellZ + radius; z++) {
                    for (int band = bandY - 1; band <= bandY + 1; band++) {
                        var bounds = new AABB(x * (double) CELL - 2D, band * (double) HEIGHT - 1D, z * (double) CELL - 2D,
                                (x + 1D) * CELL + 2D, (band + 1D) * HEIGHT + 1D, (z + 1D) * CELL + 2D);
                        if (!event.getFrustum().isVisible(bounds)) continue;
                        renderCell(poses, out, camera, x, band, z, seconds, reduced ? 1 : 2);
                    }
                }
            }
            layer.end(mesh, RenderSystem.getVertexSorting());
        } finally {
            poses.popPose();
            RenderSystem.setShader(() -> shader);
            flags.close();
        }
    }

    static void renderCell(PoseStack poses, VertexConsumer out, Vec3 camera, int cellX, int bandY, int cellZ,
            double seconds, int columns) {
        poses.pushPose();
        // Subtract in double precision before the reusable glyph writer emits float vertices.
        poses.translate(cellX * (double) CELL - camera.x, bandY * (double) HEIGHT - camera.y, cellZ * (double) CELL - camera.z);
        for (int column = 0; column < columns; column++) {
            long seed = mix(cellX * 73428767L ^ cellZ * 912931L ^ bandY * 438289L ^ column * 19349663L);
            double x = 1D + unit(seed) * 14D, z = 1D + unit(mix(seed)) * 14D;
            double speed = 1.6D + unit(mix(seed + 1)) * 1.4D;
            double phase = fract(seconds * speed / HEIGHT + unit(mix(seed + 2)));
            double head = phase * HEIGHT;
            int rgb = COLORS[(int) Math.floorMod(seed, COLORS.length)];
            double pixel = .09D + unit(mix(seed + 3)) * .04D;
            // Cylindrical billboards preserve upright glyphs, including while looking up or down.
            var towardCamera = new Vec3(camera.x - (cellX * (double) CELL + x), 0D,
                    camera.z - (cellZ * (double) CELL + z));
            var tangent = towardCamera.lengthSqr() < .001D ? new Vec3(1D, 0D, 0D)
                    : new Vec3(towardCamera.z, 0D, -towardCamera.x).normalize();
            for (int row = 0; row < 7; row++) {
                double y = head - row * .86D;
                if (y < .6D || y > HEIGHT - .3D) continue;
                double worldX = cellX * (double) CELL + x, worldZ = cellZ * (double) CELL + z;
                double distance = Math.sqrt(camera.distanceToSqr(worldX, bandY * (double) HEIGHT + y, worldZ));
                double edge = Math.min(1D, Math.min(y / 2D, (HEIGHT - y) / 7D));
                double fade = com.atir.molecularmanipulator.util.MathCompat.clamp((64D - distance) / 18D, 0D, 1D) * edge;
                int alpha = (int) ((225 - row * 25) * fade);
                if (alpha <= 0) continue;
                double spread = phase * phase * (1D + row * .12D);
                var origin = new Vec3(x + Math.sin(seconds * .23D + seed % 127 + row * .19D) * spread,
                        y, z + Math.cos(seconds * .19D + seed % 83 + row * .17D) * spread);
                int glyph = StarOrreryCoreEffects.glyphMask((int) seed + row * 11 + (int) Math.floor(seconds * 2D));
                StarOrreryCoreEffects.glyph(poses.last(), out, origin, tangent, glyph, pixel,
                        row == 0 ? 0xE6F4FF : rgb, alpha);
            }
        }
        poses.popPose();
    }

    private static long mix(long value) {
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }

    private static double unit(long value) { return (value >>> 11) * 0x1.0p-53; }
    private static double fract(double value) { return value - Math.floor(value); }
}
