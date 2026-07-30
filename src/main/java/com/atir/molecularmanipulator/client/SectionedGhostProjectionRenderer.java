package com.atir.molecularmanipulator.client;

import com.atir.molecularmanipulator.MolecularManipulator;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Caches ghost projection geometry in 16x16x16 section-sized vertex buffers.
 *
 * <p>The structure scanners can still cheaply compare the expected ghost
 * contents on their normal refresh interval, while model tessellation and GPU
 * uploads only happen for sections whose contents actually changed.</p>
 */
@EventBusSubscriber(modid = MolecularManipulator.MOD_ID, value = Dist.CLIENT,
        bus = EventBusSubscriber.Bus.MOD)
public final class SectionedGhostProjectionRenderer {
    private static final RenderType PROJECTION_TYPE = RenderType.translucent();
    private static final RenderType CONFLICT_TYPE = RenderType.lines();
    private static final int MAX_SECTION_BUILDS_PER_FRAME = 2;
    private static final AtomicLong RESOURCE_GENERATION = new AtomicLong();
    private static final Comparator<ProjectionBlock> BLOCK_ORDER =
            Comparator.comparingLong(block -> block.pos().asLong());

    private final int alpha;
    private final double maxRenderDistanceSquared;
    private final BufferBuilder builder = new BufferBuilder(262_144);
    private final Map<Long, SectionMesh> sections = new HashMap<>();
    private final Map<Long, PendingSection> pendingSections = new LinkedHashMap<>();
    private final List<SectionMesh> visibleSections = new ArrayList<>();
    private long observedResourceGeneration = RESOURCE_GENERATION.get();

    SectionedGhostProjectionRenderer(int alpha, double maxRenderDistance) {
        this.alpha = alpha;
        this.maxRenderDistanceSquared = maxRenderDistance * maxRenderDistance;
    }

    @SubscribeEvent
    public static void registerReloadListener(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) resourceManager ->
                RESOURCE_GENERATION.incrementAndGet());
    }

    boolean isInRenderRange(Vec3 cameraPosition, BlockPos anchor) {
        return cameraPosition.distanceToSqr(anchor.getCenter()) <= maxRenderDistanceSquared;
    }

    boolean isEmpty() {
        return sections.isEmpty() && pendingSections.isEmpty();
    }

    void update(Collection<ProjectionBlock> blocks) {
        RenderSystem.assertOnRenderThread();

        var desiredSections = new TreeMap<Long, List<ProjectionBlock>>();
        for (var block : blocks) {
            desiredSections.computeIfAbsent(SectionPos.asLong(block.pos()), ignored -> new ArrayList<>())
                    .add(block);
        }
        for (var sectionBlocks : desiredSections.values()) {
            sectionBlocks.sort(BLOCK_ORDER);
        }

        ensureResourcesCurrent();
        reconcilePendingSections(desiredSections);
    }

    void render(RenderLevelStageEvent event) {
        RenderSystem.assertOnRenderThread();
        ensureResourcesCurrent();
        rebuildPendingSections();
        if (sections.isEmpty()) {
            return;
        }

        var cameraPosition = event.getCamera().getPosition();
        visibleSections.clear();
        for (var section : sections.values()) {
            if (event.getFrustum().isVisible(section.bounds())) {
                visibleSections.add(section);
            }
        }
        if (visibleSections.isEmpty()) {
            return;
        }
        visibleSections.sort((left, right) -> Double.compare(
                right.bounds().distanceToSqr(cameraPosition),
                left.bounds().distanceToSqr(cameraPosition)));

        renderProjectionSections(event, cameraPosition);
        renderConflictSections(event, cameraPosition);
    }

    void clear() {
        var discarded = List.copyOf(sections.values());
        sections.clear();
        pendingSections.clear();
        visibleSections.clear();
        observedResourceGeneration = RESOURCE_GENERATION.get();
        closeMeshes(discarded);
    }

    private void ensureResourcesCurrent() {
        long resourceGeneration = RESOURCE_GENERATION.get();
        if (resourceGeneration == observedResourceGeneration) {
            return;
        }

        var desiredSections = new TreeMap<Long, List<ProjectionBlock>>();
        for (var entry : sections.entrySet()) {
            desiredSections.put(entry.getKey(), entry.getValue().blocks());
        }
        for (var entry : pendingSections.entrySet()) {
            desiredSections.put(entry.getKey(), entry.getValue().blocks());
        }

        pendingSections.clear();
        for (var entry : desiredSections.entrySet()) {
            pendingSections.put(entry.getKey(),
                    new PendingSection(entry.getValue(), resourceGeneration));
        }
        observedResourceGeneration = resourceGeneration;
    }

    private void reconcilePendingSections(Map<Long, List<ProjectionBlock>> desiredSections) {
        var discarded = new ArrayList<SectionMesh>();

        var sectionIterator = sections.entrySet().iterator();
        while (sectionIterator.hasNext()) {
            var entry = sectionIterator.next();
            if (!desiredSections.containsKey(entry.getKey())) {
                discarded.add(entry.getValue());
                sectionIterator.remove();
            }
        }
        pendingSections.keySet().removeIf(key -> !desiredSections.containsKey(key));

        for (var entry : desiredSections.entrySet()) {
            long sectionKey = entry.getKey();
            List<ProjectionBlock> desiredBlocks = entry.getValue();
            var existing = sections.get(sectionKey);
            if (existing != null
                    && existing.resourceGeneration() == observedResourceGeneration
                    && existing.blocks().equals(desiredBlocks)) {
                pendingSections.remove(sectionKey);
            } else {
                var pending = pendingSections.get(sectionKey);
                if (pending == null
                        || pending.resourceGeneration() != observedResourceGeneration
                        || !pending.blocks().equals(desiredBlocks)) {
                    pendingSections.put(sectionKey,
                            new PendingSection(desiredBlocks, observedResourceGeneration));
                }
            }
        }

        closeMeshes(discarded);
    }

    private void rebuildPendingSections() {
        int rebuilt = 0;
        var iterator = pendingSections.entrySet().iterator();
        while (rebuilt < MAX_SECTION_BUILDS_PER_FRAME && iterator.hasNext()) {
            var entry = iterator.next();
            long sectionKey = entry.getKey();
            var pending = entry.getValue();
            var replacement = bakeSection(sectionKey, pending);
            var previous = sections.put(sectionKey, replacement);
            iterator.remove();
            if (previous != null) {
                closeMeshes(List.of(previous));
            }
            rebuilt++;
        }
    }

    private SectionMesh bakeSection(long sectionKey, PendingSection pending) {
        List<ProjectionBlock> sourceBlocks = pending.blocks();
        var blocks = List.copyOf(sourceBlocks);
        var sectionPos = SectionPos.of(sectionKey);
        var origin = sectionPos.origin();
        var bounds = new AABB(
                origin.getX(), origin.getY(), origin.getZ(),
                origin.getX() + SectionPos.SECTION_SIZE,
                origin.getY() + SectionPos.SECTION_SIZE,
                origin.getZ() + SectionPos.SECTION_SIZE).inflate(0.004);

        var projectionBuffer = bakeProjection(blocks, origin);
        try {
            var conflictBuffer = bakeConflicts(blocks, origin);
            return new SectionMesh(origin, bounds, blocks, pending.resourceGeneration(),
                    projectionBuffer, conflictBuffer);
        } catch (RuntimeException | Error failure) {
            closeBuffer(projectionBuffer);
            throw failure;
        }
    }

    private VertexBuffer bakeProjection(List<ProjectionBlock> blocks, BlockPos origin) {
        builder.begin(PROJECTION_TYPE.mode(), PROJECTION_TYPE.format());
        var consumer = new ProjectionVertexConsumer(builder, alpha);
        var dispatcher = Minecraft.getInstance().getBlockRenderer();
        var models = new IdentityHashMap<BlockState, BakedModel>();
        var poseStack = new PoseStack();

        for (var block : blocks) {
            var pos = block.pos();
            poseStack.pushPose();
            poseStack.translate(
                    pos.getX() - origin.getX() + 0.5,
                    pos.getY() - origin.getY() + 0.5,
                    pos.getZ() - origin.getZ() + 0.5);
            poseStack.scale(1.003F, 1.003F, 1.003F);
            poseStack.translate(-0.5, -0.5, -0.5);
            var model = models.computeIfAbsent(block.expectedState(), dispatcher::getBlockModel);
            dispatcher.getModelRenderer().renderModel(
                    poseStack.last(), consumer, block.expectedState(), model,
                    1.0F, 1.0F, 1.0F, LightTexture.FULL_BRIGHT,
                    OverlayTexture.NO_OVERLAY);
            poseStack.popPose();
        }

        return upload(builder.endOrDiscardIfEmpty());
    }

    private VertexBuffer bakeConflicts(List<ProjectionBlock> blocks, BlockPos origin) {
        boolean hasConflicts = false;
        for (var block : blocks) {
            if (block.conflict()) {
                hasConflicts = true;
                break;
            }
        }
        if (!hasConflicts) {
            return null;
        }

        builder.begin(CONFLICT_TYPE.mode(), CONFLICT_TYPE.format());
        var poseStack = new PoseStack();
        for (var block : blocks) {
            if (!block.conflict()) {
                continue;
            }
            var pos = block.pos();
            double x = pos.getX() - origin.getX();
            double y = pos.getY() - origin.getY();
            double z = pos.getZ() - origin.getZ();
            LevelRenderer.renderLineBox(poseStack, builder,
                    new AABB(x, y, z, x + 1.0, y + 1.0, z + 1.0).inflate(0.003),
                    1.0F, 0.12F, 0.12F, 0.9F);
        }
        return upload(builder.endOrDiscardIfEmpty());
    }

    private static VertexBuffer upload(BufferBuilder.RenderedBuffer renderedBuffer) {
        if (renderedBuffer == null) {
            return null;
        }
        VertexBuffer vertexBuffer = null;
        boolean uploadInvoked = false;
        try {
            vertexBuffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
            vertexBuffer.bind();
            uploadInvoked = true;
            vertexBuffer.upload(renderedBuffer);
            return vertexBuffer;
        } catch (RuntimeException | Error failure) {
            closeBuffer(vertexBuffer);
            throw failure;
        } finally {
            if (!uploadInvoked) {
                renderedBuffer.release();
            }
            VertexBuffer.unbind();
        }
    }

    private void renderProjectionSections(RenderLevelStageEvent event, Vec3 cameraPosition) {
        boolean hasProjection = false;
        for (var section : visibleSections) {
            if (section.projectionBuffer() != null) {
                hasProjection = true;
                break;
            }
        }
        if (!hasProjection) {
            return;
        }

        PROJECTION_TYPE.setupRenderState();
        try {
            ShaderInstance shader = RenderSystem.getShader();
            if (shader != null) {
                resetChunkOffset(shader);
                for (var section : visibleSections) {
                    if (section.projectionBuffer() != null) {
                        drawSection(section.projectionBuffer(), section.origin(), event,
                                cameraPosition, shader);
                    }
                }
            }
        } finally {
            VertexBuffer.unbind();
            PROJECTION_TYPE.clearRenderState();
        }
    }

    private void renderConflictSections(RenderLevelStageEvent event, Vec3 cameraPosition) {
        boolean hasConflicts = false;
        for (var section : visibleSections) {
            if (section.conflictBuffer() != null) {
                hasConflicts = true;
                break;
            }
        }
        if (!hasConflicts) {
            return;
        }

        CONFLICT_TYPE.setupRenderState();
        try {
            ShaderInstance shader = RenderSystem.getShader();
            if (shader != null) {
                resetChunkOffset(shader);
                for (var section : visibleSections) {
                    if (section.conflictBuffer() != null) {
                        drawSection(section.conflictBuffer(), section.origin(), event,
                                cameraPosition, shader);
                    }
                }
            }
        } finally {
            VertexBuffer.unbind();
            CONFLICT_TYPE.clearRenderState();
        }
    }

    private static void resetChunkOffset(ShaderInstance shader) {
        if (shader.CHUNK_OFFSET != null) {
            shader.CHUNK_OFFSET.set(0.0F, 0.0F, 0.0F);
        }
    }

    private static void drawSection(VertexBuffer buffer, BlockPos origin,
                                    RenderLevelStageEvent event, Vec3 cameraPosition,
                                    ShaderInstance shader) {
        var poseStack = event.getPoseStack();
        poseStack.pushPose();
        try {
            poseStack.translate(
                    origin.getX() - cameraPosition.x,
                    origin.getY() - cameraPosition.y,
                    origin.getZ() - cameraPosition.z);
            buffer.bind();
            buffer.drawWithShader(poseStack.last().pose(), event.getProjectionMatrix(), shader);
        } finally {
            poseStack.popPose();
        }
    }

    private static void closeMeshes(Collection<SectionMesh> meshes) {
        if (meshes.isEmpty()) {
            return;
        }
        Runnable close = () -> {
            for (var mesh : meshes) {
                if (mesh.projectionBuffer() != null) {
                    mesh.projectionBuffer().close();
                }
                if (mesh.conflictBuffer() != null) {
                    mesh.conflictBuffer().close();
                }
            }
        };
        if (RenderSystem.isOnRenderThread()) {
            close.run();
        } else {
            RenderSystem.recordRenderCall(close::run);
        }
    }

    private static void closeBuffer(VertexBuffer buffer) {
        if (buffer != null) {
            buffer.close();
        }
    }

    record ProjectionBlock(BlockPos pos, BlockState expectedState, boolean conflict) {
        ProjectionBlock {
            pos = pos.immutable();
            Objects.requireNonNull(expectedState, "expectedState");
        }
    }

    private record PendingSection(List<ProjectionBlock> blocks, long resourceGeneration) {
        private PendingSection {
            blocks = List.copyOf(blocks);
        }
    }

    private record SectionMesh(BlockPos origin, AABB bounds, List<ProjectionBlock> blocks,
                               long resourceGeneration,
                               VertexBuffer projectionBuffer,
                               VertexBuffer conflictBuffer) {
    }

    private static final class ProjectionVertexConsumer implements VertexConsumer {
        private final VertexConsumer delegate;
        private final int alpha;

        private ProjectionVertexConsumer(VertexConsumer delegate, int alpha) {
            this.delegate = delegate;
            this.alpha = alpha;
        }

        @Override
        public VertexConsumer vertex(double x, double y, double z) {
            delegate.vertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer color(int red, int green, int blue, int sourceAlpha) {
            delegate.color(red, green, blue, Math.min(sourceAlpha, alpha));
            return this;
        }

        @Override
        public VertexConsumer uv(float u, float v) {
            delegate.uv(u, v);
            return this;
        }

        @Override
        public VertexConsumer overlayCoords(int u, int v) {
            delegate.overlayCoords(u, v);
            return this;
        }

        @Override
        public VertexConsumer uv2(int u, int v) {
            delegate.uv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            delegate.normal(x, y, z);
            return this;
        }

        @Override
        public void endVertex() {
            delegate.endVertex();
        }

        @Override
        public void defaultColor(int red, int green, int blue, int sourceAlpha) {
            delegate.defaultColor(red, green, blue, Math.min(sourceAlpha, alpha));
        }

        @Override
        public void unsetDefaultColor() {
            delegate.unsetDefaultColor();
        }
    }
}
