package com.atir.molecularmanipulator.client.render;

import com.atir.molecularmanipulator.MolecularManipulator;
import com.atir.molecularmanipulator.blockentity.StarComputationEngineCoreBlockEntity;
import com.atir.molecularmanipulator.config.ModConfig;
import com.atir.molecularmanipulator.starcomputation.StarOrreryLayout;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import org.joml.Quaternionf;

/** Opt-in star_orrery preview. Animation is client-only; verified rings and operating state are synchronized. */
@EventBusSubscriber(modid = MolecularManipulator.MOD_ID, value = Dist.CLIENT)
public final class StarOrreryRenderer implements BlockEntityRenderer<StarComputationEngineCoreBlockEntity>,
        EarlyFrameGeometry.Source<StarComputationEngineCoreBlockEntity, StarOrreryRenderer.Snapshot> {
    public static final String[] FORMS = {"reference", "quantum", "terminal"};
    // Floating pedestal artwork replaces only the formed visual. Real foundation
    // blocks, their validation and collisions remain owned by the server.
    public static final String[] PARTS = {"floating_pedestal", "floating_pedestal_glow",
            "floating_pedestal_dial", "floating_pedestal_dial_glow",
            "floating_front_gem", "energy_well", "gear", "well_beams", "energy_ring", "float_crystal"};
    private static final Map<ModelResourceLocation, List<BakedQuad>> QUADS = new HashMap<>();
    private static final StarOrreryModelCache MODELS = new StarOrreryModelCache();
    private static final StarOrreryParallelEffects PARALLEL = new StarOrreryParallelEffects();
    // One persistent CPU batch per early-frame helper; no block-entity references are retained.
    private static final IdentityHashMap<ParallelEffectGeometry, StarOrreryModelBatch> CPU_BATCHES =
            new IdentityHashMap<>();
    private static final Map<StarComputationEngineCoreBlockEntity, StarOrreryCoreEffects.AccretionClock> CLOCKS =
            new java.util.WeakHashMap<>();
    private static final int FULL = LightTexture.FULL_BRIGHT;
    private static final float[] QUAD_BRIGHTNESS = {1F, 1F, 1F, 1F};
    // All model submission runs on the render thread; reuse these for every quad.
    private static final int[] QUAD_LIGHT = new int[4];
    // Rendering happens on the client render thread; reuse the plane quaternion
    // so the per-ring loop does not allocate one object per belt per frame.
    private static final Quaternionf RING_NORMAL = new Quaternionf();

    public StarOrreryRenderer(BlockEntityRendererProvider.Context context) {}

    public static ModelResourceLocation model(String form, String part) {
        return ModelResourceLocation.standalone(MolecularManipulator.id("block/star_orrery/" + form + "/" + part));
    }

    public static void clear() {
        if (!RenderSystem.isOnRenderThread()) { RenderSystem.recordRenderCall(StarOrreryRenderer::clear); return; }
        PARALLEL.close();
        StarOrreryGpuRain.clear();
        // Free every persistent CPU output before invalidating the mesh templates they copied.
        Throwable failure = null;
        try { releaseCpuBatches(); }
        catch (RuntimeException | Error error) { failure = error; }
        try { StarOrreryModelBatch.clear(); }
        catch (RuntimeException | Error error) {
            if (failure == null) failure = error;
            else if (error != failure) failure.addSuppressed(error);
        }
        QUADS.clear(); CLOCKS.clear(); MODELS.close();
        if (failure instanceof RuntimeException error) throw error;
        if (failure instanceof Error error) throw error;
    }

    /** Releases this helper's persistent CPU batch when its early-frame entry goes away. */
    @Override
    public void release(ParallelEffectGeometry geometry) {
        StarOrreryModelBatch batch;
        synchronized (CPU_BATCHES) { batch = CPU_BATCHES.remove(geometry); }
        if (batch != null) batch.release();
    }

    private static void releaseCpuBatches() {
        ArrayList<StarOrreryModelBatch> batches;
        synchronized (CPU_BATCHES) {
            if (CPU_BATCHES.isEmpty()) return;
            batches = new ArrayList<>(CPU_BATCHES.values());
            CPU_BATCHES.clear();
        }
        Throwable failure = null;
        for (var batch : batches) {
            try { batch.release(); }
            catch (RuntimeException | Error error) {
                if (failure == null) failure = error;
                else if (error != failure) failure.addSuppressed(error);
            }
        }
        if (failure instanceof RuntimeException error) throw error;
        if (failure instanceof Error error) throw error;
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { clear(); }

    /** Omit only the well's solid luminous cap; its metal lip and walls stay intact. */
    static boolean keepWellMaterial(String part, ResourceLocation texture) {
        return !part.equals("energy_well") || !texture.getNamespace().equals(MolecularManipulator.MOD_ID)
                || !texture.getPath().endsWith("/wellglow");
    }

    /** Same NORTH/EAST/SOUTH/WEST transform as Layout.rotationFor, expressed as a right-handed Y rotation. */
    static float facingAngle(Direction facing) {
        return switch (facing) {
            case NORTH -> 0;
            case EAST -> -90;
            case SOUTH -> -180;
            case WEST -> 90;
            default -> throw new IllegalArgumentException("Horizontal facing required");
        };
    }

    /** Inverse of the fixed machine-facing pose, never of the animated ring pose. */
    static Vec3 cameraInLayout(Direction facing, Vec3 relative) {
        double angle = Math.toRadians(facingAngle(facing));
        double sin = Math.sin(angle), cos = Math.cos(angle);
        return new Vec3(relative.x * cos - relative.z * sin, relative.y,
                relative.x * sin + relative.z * cos);
    }

    static Quaternionf normalRotation(double yaw, double pitch) {
        return applyNormal(new Quaternionf(), yaw, pitch);
    }

    /** Reuses {@code target} so a batched per-ring pass stays allocation free. */
    static Quaternionf applyNormal(Quaternionf target, double yaw, double pitch) {
        return StarOrreryMotion.restRotation(target, yaw, pitch);
    }

    @Override
    public void render(StarComputationEngineCoreBlockEntity be, float partialTick, PoseStack poses,
            MultiBufferSource buffers, int packedLight, int overlay) {
        if (!be.isStarOrrery() || !be.isFormed() || be.getLevel() == null) return;
        var snapshot = EarlyFrameGeometry.take(this, be, partialTick, poses);
        if (snapshot != null && (!snapshot.usable() || snapshot.packedLight != packedLight)) {
            snapshot.close();
            snapshot = null;
        }
        if (snapshot == null) {
            renderFrame(be, partialTick, poses, buffers, packedLight, null);
            return;
        }
        var frame = snapshot;
        try (frame) {
            renderFrame(be, partialTick, poses, buffers, packedLight, frame);
        }
    }

    /**
     * Captures only the rigid model draws. Dynamic well/ring/noise/nebula geometry stays at the
     * real BER position, and no mutable clock or random animation state is sampled twice.
     */
    @Override
    public Snapshot capture(StarComputationEngineCoreBlockEntity be, float partialTick,
            PoseStack base, ParallelEffectGeometry geometry) {
        // Shader packs own the GPU model path; never capture the CPU copy path while active.
        if (SingularityEffectLayers.shadersActive()) return null;
        if (!be.isStarOrrery() || !be.isFormed() || be.getLevel() == null) return null;
        MODELS.prepare(false);
        int effects = ModConfig.DYNAMIC_EFFECT_LEVEL.get();
        var cameraRelative = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition()
                .subtract(be.getBlockPos().getCenter());
        double distance = cameraRelative.lengthSqr();
        boolean detailed = StarOrreryEffects.isDetailed(effects, distance);
        long gameTime = be.getLevel().getGameTime();
        float t = StarOrreryMotion.timeSeconds(gameTime, partialTick, effects);
        String form = FORMS[Math.clamp(be.getVisualTier() - 1, 0, 2)];
        int lit = be.getLitRings();
        int packedLight = LevelRenderer.getLightColor(be.getLevel(), be.getBlockPos());
        StarOrreryModelBatch batch;
        synchronized (CPU_BATCHES) {
            batch = CPU_BATCHES.computeIfAbsent(geometry, ignored -> new StarOrreryModelBatch());
        }
        if (batch.begin() == null) return null;
        try {
            var poses = copyPose(base);
            poses.translate(.5, .5, .5);
            var facing = be.getBlockState().getValue(HorizontalDirectionalBlock.FACING);
            poses.mulPose(Axis.YP.rotationDegrees(facingAngle(facing)));
            modelPass(poses, form, lit, effects, t, gameTime, partialTick, packedLight, distance,
                    new CaptureSink(batch));
            if (batch.count() == 0 || !batch.seal()) {
                batch.close();
                return null;
            }
            return new Snapshot(batch, form, lit, effects, t, gameTime, packedLight,
                    cameraRelative, distance, detailed);
        } catch (BudgetExceeded exceeded) {
            // Only the explicit bounded-capture fallback keeps the ordinary render path.
            batch.close();
            return null;
        } catch (RuntimeException | Error failure) {
            try { batch.close(); }
            catch (RuntimeException | Error closing) { failure.addSuppressed(closing); }
            throw failure;
        }
    }

    private void renderFrame(StarComputationEngineCoreBlockEntity be, float partialTick,
            PoseStack poses, MultiBufferSource buffers, int packedLight, Snapshot snapshot) {
        // The capture already prepared the mesh cache. Avoid closing live meshes while its workers
        // still own outputs; fallback frames do the ordinary render-thread preparation here.
        if (snapshot == null) {
            boolean shaders = SingularityEffectLayers.shadersActive();
            // Shader packs take the GPU path: free idle CPU template outputs before their mesh
            // templates can be closed by prepare().
            if (shaders) releaseCpuBatches();
            MODELS.prepare(shaders);
        }
        // Do not lose the actual machine silhouette when dynamic effects are disabled.
        int effects = snapshot != null ? snapshot.effects : ModConfig.DYNAMIC_EFFECT_LEVEL.get();
        var cameraRelative = snapshot != null ? snapshot.cameraRelative
                : Minecraft.getInstance().gameRenderer.getMainCamera().getPosition()
                        .subtract(be.getBlockPos().getCenter());
        double distance = snapshot != null ? snapshot.distance : cameraRelative.lengthSqr();
        boolean detailed = snapshot != null ? snapshot.detailed
                : StarOrreryEffects.isDetailed(effects, distance);
        long gameTime = snapshot != null ? snapshot.gameTime : be.getLevel().getGameTime();
        // A visible ring keeps turning past the light-effect LOD boundary.
        // Reset only when animation is disabled, never when the camera moves.
        float t = snapshot != null ? snapshot.t
                : StarOrreryMotion.timeSeconds(gameTime, partialTick, effects);
        String form = snapshot != null ? snapshot.form
                : FORMS[Math.clamp(be.getVisualTier() - 1, 0, 2)];
        int lit = snapshot != null ? snapshot.lit : be.getLitRings();
        poses.pushPose();
        try {
            poses.translate(.5, .5, .5);
            var facing = be.getBlockState().getValue(HorizontalDirectionalBlock.FACING);
            float yaw = facingAngle(facing);
            poses.mulPose(Axis.YP.rotationDegrees(yaw));
            var anchorCamera = cameraInLayout(facing, cameraRelative);
            float flowTime = CLOCKS.computeIfAbsent(be, key -> new StarOrreryCoreEffects.AccretionClock())
                    .sample((be.getLevel().getGameTime() + (double) partialTick) / 20D,
                            be.getVisualActivity(), effects > 0 && lit > 0);

            var gpuRain = StarOrreryGpuRain.capture(poses, buffers, lit, effects, distance, flowTime);
            boolean parallelWork = !MODELS.gpuActive() || StarOrreryAnchorEffects.hasParallelWork(lit, anchorCamera);
            StarOrreryModelBatch.Cursor cursor = snapshot == null ? null : snapshot.openCursor();
            try {
                try (var prepared = parallelWork ? PARALLEL.start(poses, lit, effects, anchorCamera, t, flowTime,
                        distance, detailed, gpuRain == null) : null) {
                    DrawSink sink = (drawPoses, drawForm, part, light, translucent, brightness, alpha) -> {
                        var command = prepare(drawForm, part, translucent);
                        if (command == null) return;
                        if (cursor != null && cursor.submit(command.id, command.type, command.quads,
                                light, brightness, alpha, buffers)) return;
                        drawPrepared(drawPoses, command, buffers, light, brightness, alpha);
                    };
                    // Visual replacement only: the server still requires and preserves the
                    // physical foundation blocks, including their collisions and drops.
                    modelPass(poses, form, lit, effects, t, gameTime, partialTick, packedLight, distance, sink);

                    // A fixed, depth-tested ink surface replaces the former geometric ripple rails.
                    StarOrreryWellEffects.render(poses, buffers, lit, effects, distance);

                    // Keep the real datum anchors independent of the nebula and ring poses.
                    StarOrreryNebulaRenderer.enqueue(poses, lit, t, flowTime, effects, distance);
                    int effectRings = StarOrreryEffects.visibleRings(lit, effects, distance);
                    var crystal = OmniRenderLayers.starOrreryCrystal(buffers);
                    try (var batch = SingularityShaderVertices.batch(crystal)) {
                        if (prepared != null) prepared.crystal(crystal);
                        else {
                            StarOrreryAnchorEffects.render(poses, crystal, lit, effects, anchorCamera, t, false);
                            if (effectRings > 0) StarOrreryEffects.render(poses, crystal, effectRings, t, detailed, false);
                        }
                    }

                    // Close each batch before requesting the next consumer: shader-compatible
                    // buffers may flush on a layer switch. Falling code uses its own emissive
                    // program so every trail character participates in shader-pack bloom.
                    if (effectRings > 0) {
                        if (gpuRain != null) gpuRain.submit(false);
                        else {
                            var rain = OmniRenderLayers.starOrreryRain(buffers, false);
                            try (var batch = SingularityShaderVertices.batch(rain)) {
                                if (prepared != null) prepared.rain(rain, false);
                                else {
                                    StarOrreryCoreEffects.renderRain(poses, rain, lit, effects, distance, flowTime, false);
                                    StarOrreryCoreEffects.renderBuildingRain(poses, rain, lit, effects, distance, flowTime, t, false);
                                }
                            }
                        }
                        var glow = OmniRenderLayers.starOrreryGlow(buffers);
                        try (var batch = SingularityShaderVertices.batch(glow)) {
                            if (prepared != null) prepared.glow(glow);
                            else {
                                StarOrreryEffects.render(poses, glow, effectRings, t, detailed, true);
                                StarOrreryAnchorEffects.render(poses, glow, lit, effects, anchorCamera, t, true);
                            }
                        }
                        if (gpuRain != null) gpuRain.submit(true);
                        else {
                            var rainGlow = OmniRenderLayers.starOrreryRain(buffers, true);
                            try (var batch = SingularityShaderVertices.batch(rainGlow)) {
                                if (prepared != null) prepared.rain(rainGlow, true);
                                else {
                                    StarOrreryCoreEffects.renderRain(poses, rainGlow, lit, effects, distance, flowTime, true);
                                    StarOrreryCoreEffects.renderBuildingRain(poses, rainGlow, lit, effects, distance, flowTime, t, true);
                                }
                            }
                        }
                    }
                }
            } finally {
                if (cursor != null) cursor.close();
            }
        } finally {
            poses.popPose();
        }
    }

    /**
     * The rigid model traversal shared by the dry capture and the real BER. Keeping this sequence
     * in one place is what makes each prepared command line up with its original draw position.
     */
    private static void modelPass(PoseStack poses, String form, int lit, int effects, float t,
            long gameTime, float partialTick, int packedLight, double distance, DrawSink sink) {
        draw(poses, sink, form, "floating_pedestal", packedLight, false, 1);
        draw(poses, sink, form, "floating_pedestal_glow", FULL, true, 1);
        // The inverted crystal supports are retired; the well floats above open rings.
        poses.pushPose();
        poses.translate(0, StarOrreryPedestalMotion.DIAL_Y, 0);
        poses.mulPose(Axis.XP.rotation(StarOrreryPedestalMotion.DIAL_TILT));
        poses.mulPose(Axis.YP.rotation(StarOrreryPedestalMotion.dialRotation(
                gameTime, partialTick, lit, effects)));
        draw(poses, sink, form, "floating_pedestal_dial", packedLight, false, 1);
        draw(poses, sink, form, "floating_pedestal_dial_glow", FULL, true, 1);
        poses.popPose();
        draw(poses, sink, form, "floating_front_gem", FULL, false, 1);
        draw(poses, sink, form, "energy_well", FULL, false, 1);
        // Retain the original small gear, not its enlarged duplicate across the nebula.
        draw(poses, sink, form, "gear", packedLight, false, 1);

        // Draw only the server-verified, upgraded rings. Their entire planes
        // tumble and precess around the core, with self-spin local to each plane.
        // Fixed datum anchors are submitted later, outside these transforms,
        // at their selectable/collidable block positions.
        for (int ring = 0; ring < lit; ring++) {
            String tierForm = FORMS[StarOrreryLayout.tierOf(ring)];
            poses.pushPose();
            poses.mulPose(StarOrreryMotion.planeRotation(RING_NORMAL, ring, lit, t));
            poses.mulPose(Axis.YP.rotation(StarOrreryEffects.beltSpin(ring, lit, t)));
            draw(poses, sink, tierForm, "ring_" + (ring + 1), packedLight, false, 1);
            if (effects > 1) {
                embeddedCrystals(poses, sink, tierForm, ring);
            }
            poses.popPose();
        }

        if (effects > 1) {
            draw(poses, sink, form, "well_beams", FULL, true, .25F);
        }
        poses.pushPose();
        poses.translate(0, .3 * Math.sin(t * 1.3), 0);
        poses.mulPose(Axis.YP.rotation(t * .5F));
        draw(poses, sink, form, "energy_ring", FULL, true, 1);
        poses.popPose();
        for (int j = 0; j < 6; j++) {
            double a = Math.PI * 2 * j / 6;
            poses.pushPose();
            poses.translate(8.5 * Math.cos(a), -38.5 + 14 + j % 2 * 4 + .4 * Math.sin(t * 1.7 + j), 8.5 * Math.sin(a));
            poses.mulPose(Axis.YP.rotation(t * .8F + j));
            float phase = j % 3 == 1 && lit > 0 && StarOrreryEffects.effectsVisible(effects, distance)
                    ? StarOrreryCoreEffects.glitch(j, t) : 0F;
            poses.translate(phase * .22D * Math.sin(t * 70D), 0, phase * .12D);
            draw(poses, sink, form, "float_crystal", FULL, true, 1, 1F - phase * .65F);
            poses.popPose();
        }
    }

    /**
     * Replace the old hanging chains with float crystals set directly into the
     * rotating belt. The pose already contains the ring plane and belt spin, so
     * these ornaments co-move with the filigree and never cancel gravity.
     */
    private static void embeddedCrystals(PoseStack poses, DrawSink sink, String form, int ring) {
        int count = StarOrreryEffects.embeddedCrystalCount(ring);
        for (int index = 0; index < count; index++) {
            poses.pushPose();
            poses.translate(StarOrreryEffects.embeddedCrystalX(ring, index),
                    StarOrreryEffects.embeddedCrystalY(ring, index),
                    StarOrreryEffects.embeddedCrystalZ(ring, index));
            poses.mulPose(Axis.YP.rotation(StarOrreryEffects.embeddedCrystalYaw(ring, index)));
            poses.mulPose(Axis.ZP.rotationDegrees(-90));
            poses.scale(StarOrreryEffects.EMBEDDED_CRYSTAL_VERTICAL_SCALE,
                    StarOrreryEffects.EMBEDDED_CRYSTAL_TANGENT_SCALE,
                    StarOrreryEffects.EMBEDDED_CRYSTAL_RADIAL_SCALE);
            draw(poses, sink, form, "float_crystal", FULL, true, 1);
            poses.popPose();
        }
    }

    private static void draw(PoseStack poses, DrawSink sink, String form, String part,
            int light, boolean translucent, float brightness) {
        draw(poses, sink, form, part, light, translucent, brightness, 1F);
    }

    private static void draw(PoseStack poses, DrawSink sink, String form, String part,
            int light, boolean translucent, float brightness, float alpha) {
        sink.draw(poses, form, part, light, translucent, brightness, alpha);
    }

    /** Resolves the baked quads and exact original layer without touching any vertex consumer. */
    private static Prepared prepare(String form, String part, boolean translucent) {
        var mc = Minecraft.getInstance();
        // All imported models deliberately disable automatic culling: their
        // oversized geometry lives entirely in the null-side quad list.
        var chunkLayer = translucent ? RenderType.translucent() : RenderType.cutout();
        // Filter the cap once at bake-cache time, not once per frame. No opaque
        // disk remains underneath the transparent ink, including at effects level 0.
        var id = model(form, part);
        var quads = QUADS.computeIfAbsent(id, key ->
                mc.getModelManager().getModel(key).getQuads(null, null, RandomSource.create(42), ModelData.EMPTY, chunkLayer)
                        .stream().filter(quad -> keepWellMaterial(part, quad.getSprite().contents().name())).toList());
        if (quads.isEmpty()) return null;
        boolean glow = part.equals("well_beams") || part.equals("floating_pedestal_glow")
                || part.equals("floating_pedestal_dial_glow");
        // Narrow well/pedestal light stays additive, without an opaque depth plane.
        var layer = glow ? OmniRenderLayers.additiveEmissive(TextureAtlas.LOCATION_BLOCKS)
                : translucent ? Sheets.translucentCullBlockSheet() : Sheets.cutoutBlockSheet();
        return new Prepared(id, quads, layer, translucent, glow);
    }

    /** Submits one command through the same GPU/cached/packed order used before early capture. */
    private static void drawPrepared(PoseStack poses, Prepared prepared, MultiBufferSource buffers,
            int light, float brightness, float alpha) {
        if (!prepared.translucent && !prepared.glow && MODELS.submitGpu(prepared.id, prepared.quads, buffers,
                prepared.type, poses.last(), light, brightness, alpha)) return;
        var buffer = buffers.getBuffer(prepared.type);
        if (MODELS.submit(prepared.id, prepared.quads, buffer, poses.last(), light, brightness, alpha)) return;
        // ModelBlockRenderer only applies its RGB arguments to tinted quads.
        // These OBJ quads are untinted, so submit explicitly to preserve the
        // authored material colors and local phase transparency.
        java.util.Arrays.fill(QUAD_LIGHT, light);
        for (var quad : prepared.quads) {
            buffer.putBulkData(poses.last(), quad, QUAD_BRIGHTNESS,
                    brightness, brightness, brightness, alpha, QUAD_LIGHT, OverlayTexture.NO_OVERLAY, false);
        }
    }

    private static PoseStack copyPose(PoseStack base) {
        var poses = new PoseStack();
        poses.last().pose().set(base.last().pose());
        poses.last().normal().set(base.last().normal());
        return poses;
    }

    private interface DrawSink {
        void draw(PoseStack poses, String form, String part, int light, boolean translucent,
                float brightness, float alpha);
    }

    private record Prepared(ModelResourceLocation id, List<BakedQuad> quads, RenderType type,
            boolean translucent, boolean glow) {}

    private static final class CaptureSink implements DrawSink {
        private final StarOrreryModelBatch batch;

        private CaptureSink(StarOrreryModelBatch batch) { this.batch = batch; }

        @Override public void draw(PoseStack poses, String form, String part, int light,
                boolean translucent, float brightness, float alpha) {
            var command = prepare(form, part, translucent);
            if (command == null) return;
            // Render-thread only: bake the immutable path once, then publish it to workers.
            var mesh = MODELS.mesh(command.id, command.quads);
            if (!batch.add(command.id, command.type, command.quads, mesh, poses.last(),
                    light, brightness, alpha)) {
                throw BudgetExceeded.INSTANCE;
            }
        }
    }

    private static final class BudgetExceeded extends RuntimeException {
        private static final BudgetExceeded INSTANCE = new BudgetExceeded();
        private BudgetExceeded() { super(null, null, false, false); }
    }

    /** Frozen capture values plus one owned model-command batch. */
    public static final class Snapshot implements EarlyFrameGeometry.Snapshot {
        private final StarOrreryModelBatch batch;
        private final String form;
        private final int lit, effects, packedLight;
        private final float t;
        private final long gameTime;
        private final Vec3 cameraRelative;
        private final double distance;
        private final boolean detailed;
        private boolean closed;

        private Snapshot(StarOrreryModelBatch batch, String form, int lit, int effects, float t,
                long gameTime, int packedLight, Vec3 cameraRelative, double distance, boolean detailed) {
            this.batch = batch;
            this.form = form;
            this.lit = lit;
            this.effects = effects;
            this.t = t;
            this.gameTime = gameTime;
            this.packedLight = packedLight;
            this.cameraRelative = cameraRelative;
            this.distance = distance;
            this.detailed = detailed;
        }

        private boolean usable() { return !closed && batch.usable(); }

        private StarOrreryModelBatch.Cursor openCursor() {
            return closed ? null : batch.openCursor();
        }

        @Override public void close() {
            if (closed) return;
            closed = true;
            batch.close();
        }
    }

    /** Enclose the cylindrical terrain-fog range plus every corner of the oversized model. */
    static int viewDistance(double terrainDistance) {
        return (int) Math.ceil(Math.sqrt(2D) * terrainDistance + Math.sqrt(3D) * 42D);
    }

    @Override public int getViewDistance() {
        var mc = Minecraft.getInstance();
        return viewDistance(Math.max(mc.options.getEffectiveRenderDistance() * 16D,
                mc.gameRenderer.getRenderDistance()));
    }
    @Override public boolean shouldRenderOffScreen(StarComputationEngineCoreBlockEntity be) { return true; }
    @Override public AABB getRenderBoundingBox(StarComputationEngineCoreBlockEntity be) {
        return new AABB(be.getBlockPos()).inflate(42);
    }
}
