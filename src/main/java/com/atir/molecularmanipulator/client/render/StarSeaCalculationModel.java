package com.atir.molecularmanipulator.client.render;

import com.atir.molecularmanipulator.MolecularManipulator;
import com.atir.molecularmanipulator.block.StarSeaCalculationGeometry;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.Arrays;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.model.data.ModelData;

/** Shared baked geometry for the real BER and placement ghost, with cached opaque submission. */
@EventBusSubscriber(modid = MolecularManipulator.MOD_ID, value = Dist.CLIENT)
public final class StarSeaCalculationModel {
    public static final ResourceLocation SHELL = new ResourceLocation(
            "molecularmanipulator", "block/star_sea_calculation_mainframe/shell");
    public static final ResourceLocation GLASS = new ResourceLocation(
            "molecularmanipulator", "block/star_sea_calculation_mainframe/glass");

    // One shared shell mesh, never a per-building/per-facing/per-tint cache.
    // The six glass faces and placement ghost stay in their original sorting domain.
    private static final StarOrreryModelCache SHELL_MESH = new StarOrreryModelCache();
    private static volatile CachedQuads shellCache;
    private static volatile CachedQuads glassCache;
    private static final float[] BRIGHTNESS = {1F, 1F, 1F, 1F};
    // Submission is render-thread only; reuse these rather than allocate per face.
    private static final int[] LIGHTMAP = new int[4];

    private StarSeaCalculationModel() {}

    /** Render-thread reload/logout boundary; also safe before any mesh has been allocated. */
    public static void clear() {
        SHELL_MESH.close();
        shellCache = null;
        glassCache = null;
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { clear(); }

    /**
     * Apply once at the core's lower corner, inside the caller's push/pop pair.
     * NORTH occupies x=-2..2, z=0..4, y=0..8, centered at (.5, 0, 2.5).
     * Keep the canonical PART index when rotating the corresponding voxel shape.
     */
    public static void transform(PoseStack poses, Direction facing) {
        transformBuilding(poses, facing);
        poses.scale(8F, 8F, 8F);
        poses.translate(-.5D, 0D, -.5D);
    }

    /** Full-size block coordinates around the building centre, shared with its surrounding code rain. */
    public static void transformBuilding(PoseStack poses, Direction facing) {
        int turns = StarSeaCalculationGeometry.quarterTurns(facing);
        poses.translate(.5D, 0D, .5D);
        poses.mulPose(Axis.YP.rotationDegrees(-90F * turns));
        poses.translate(0D, 0D, 2D);
    }

    /**
     * Draw MINI geometry only: no outer translation/rotation/scale or pose push/pop.
     * Both materials use the supplied world light and overlay. Only glass is
     * translucent for the real building; a ghost puts BOTH in that same layer.
     * Tint and alpha are explicit even for untinted baked quads. The shell's
     * selected glow faces carry baked full-bright light; putBulkData preserves
     * that while the ordinary casing uses world light. Both animated sprites
     * advance in Minecraft's atlas, so cached quads need no per-frame rebuilding.
     */
    public static void draw(PoseStack poses, MultiBufferSource buffers, int light, int overlay,
            float r, float g, float b, float alpha, boolean ghost) {
        if (alpha <= 0F) return;
        // Resolve identities before queuing any draw: a reload must not close a mesh
        // after its marker has already entered Iris's deferred phase.
        var shell = quads(SHELL, false);
        var glass = quads(GLASS, true);
        var translucent = Sheets.translucentCullBlockSheet();
        if (!shell.isEmpty()) {
            var layer = ghost ? translucent : Sheets.cutoutBlockSheet();
            // Native/GPU uploads bypass Sodium's per-quad sprite tracking. Mark all
            // shell sprites each draw, or retain ordinary submission without that hook.
            if (cacheableShell(ghost, overlay, r, g, b)
                    && SodiumVertexBatch.markSpritesActive(shellCache.sprites())) {
                SHELL_MESH.prepare(SingularityEffectLayers.shadersActive());
                // Only the opaque shell may use resident draws. Transparent faces must
                // keep their real vertices in the caller's global sorting domain.
                if (!SHELL_MESH.submitGpu(SHELL, shell, buffers, layer, poses.last(), light, r, alpha)) {
                    var target = buffers.getBuffer(layer);
                    if (!SHELL_MESH.submit(SHELL, shell, target, poses.last(), light, r, alpha))
                        drawQuads(poses, target, shell, light, overlay, r, g, b, alpha);
                }
            } else {
                drawQuads(poses, buffers.getBuffer(layer), shell, light, overlay, r, g, b, alpha);
            }
        }
        if (!glass.isEmpty())
            drawQuads(poses, buffers.getBuffer(translucent), glass, light, overlay, r, g, b, alpha);
    }

    static boolean cacheableShell(boolean ghost, int overlay, float r, float g, float b) {
        // The existing mesh path accepts grayscale tint and the normal BER overlay.
        // Arbitrary preview colors/overlays retain the complete ordinary vertex API.
        return !ghost && overlay == OverlayTexture.NO_OVERLAY && r == g && g == b;
    }

    private static List<BakedQuad> quads(ResourceLocation id, boolean glass) {
        var manager = Minecraft.getInstance().getModelManager();
        var model = manager.getModel(id);
        // Unregistered/not-yet-baked helpers must not draw two missing-model cubes.
        if (model == manager.getMissingModel()) return List.of();
        var cached = glass ? glassCache : shellCache;
        if (cached == null || cached.model() != model) {
            if (!glass) SHELL_MESH.close();
            // Exported faces have NO cullface. Read the null-side list once, not
            // once per direction/render layer, including when drawing a ghost.
            cached = new CachedQuads(model, List.copyOf(model.getQuads(
                    null, null, RandomSource.create(42), ModelData.EMPTY, null)));
            if (glass) glassCache = cached;
            else shellCache = cached;
        }
        return cached.quads();
    }

    static void drawQuads(PoseStack poses, VertexConsumer consumer, List<BakedQuad> quads,
            int light, int overlay, float r, float g, float b, float alpha) {
        Arrays.fill(LIGHTMAP, light);
        for (var quad : quads) {
            // ModelBlockRenderer's RGB arguments affect tinted quads only.
            // putBulkData applies this RGBA to EVERY vertex of these untinted faces.
            consumer.putBulkData(poses.last(), quad, BRIGHTNESS, r, g, b, alpha, LIGHTMAP, overlay, false);
        }
    }

    private record CachedQuads(BakedModel model, List<BakedQuad> quads, List<TextureAtlasSprite> sprites) {
        private CachedQuads(BakedModel model, List<BakedQuad> quads) {
            this(model, quads, quads.stream().map(BakedQuad::getSprite)
                    .filter(sprite -> sprite != null).distinct().toList());
        }
    }
}
