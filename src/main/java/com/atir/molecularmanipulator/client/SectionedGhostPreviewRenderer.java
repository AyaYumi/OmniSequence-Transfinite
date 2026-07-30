package com.atir.molecularmanipulator.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Caches ghost block models in static 16x16x16 section VBOs.
 *
 * <p>The supplied block list is compared section by section. Unchanged sections retain their
 * GPU buffers, while changed and removed sections are rebuilt or closed on the render thread.</p>
 */
final class SectionedGhostPreviewRenderer implements AutoCloseable {
    private static final double BOUNDS_INFLATION = 0.003;
    private static final float MODEL_SCALE = 1.003F;
    private static final int MAX_SECTION_BUILDS_PER_FRAME = 2;
    private static final Comparator<GhostBlock> BLOCK_ORDER =
            Comparator.comparingLong(block -> block.pos().asLong());

    private final int projectionAlpha;
    private final Map<SectionKey, SectionMesh> sections = new HashMap<>();
    private final Map<SectionKey, List<GhostBlock>> pendingSections = new LinkedHashMap<>();
    private final List<SectionMesh> visibleSections = new ArrayList<>();
    private Vec3 sortCamera = Vec3.ZERO;
    private final Comparator<SectionMesh> farToNear =
            (left, right) -> Double.compare(right.distanceToSqr(sortCamera), left.distanceToSqr(sortCamera));

    SectionedGhostPreviewRenderer(int projectionAlpha) {
        this.projectionAlpha = projectionAlpha;
    }

    boolean isEmpty() {
        return sections.isEmpty() && pendingSections.isEmpty();
    }

    void update(List<GhostBlock> blocks) {
        RenderSystem.assertOnRenderThread();
        Map<SectionKey, List<GhostBlock>> nextSections = new LinkedHashMap<>();
        for (var block : blocks) {
            nextSections.computeIfAbsent(SectionKey.of(block.pos()), ignored -> new ArrayList<>())
                    .add(block);
        }
        nextSections.replaceAll((ignored, sectionBlocks) -> {
            sectionBlocks.sort(BLOCK_ORDER);
            return List.copyOf(sectionBlocks);
        });

        var existingIterator = sections.entrySet().iterator();
        while (existingIterator.hasNext()) {
            var existing = existingIterator.next();
            if (!nextSections.containsKey(existing.getKey())) {
                existing.getValue().close();
                existingIterator.remove();
            }
        }
        pendingSections.keySet().removeIf(key -> !nextSections.containsKey(key));

        for (var next : nextSections.entrySet()) {
            var existing = sections.get(next.getKey());
            if (existing != null && existing.blocks().equals(next.getValue())) {
                pendingSections.remove(next.getKey());
                continue;
            }
            if (!next.getValue().equals(pendingSections.get(next.getKey()))) {
                pendingSections.remove(next.getKey());
                pendingSections.put(next.getKey(), next.getValue());
            }
        }
    }

    void render(RenderLevelStageEvent event) {
        RenderSystem.assertOnRenderThread();
        buildPendingSections();
        if (sections.isEmpty()) {
            return;
        }

        sortCamera = event.getCamera().getPosition();
        visibleSections.clear();
        for (var section : sections.values()) {
            if (event.getFrustum().isVisible(section.bounds())) {
                visibleSections.add(section);
            }
        }
        if (visibleSections.isEmpty()) {
            return;
        }
        visibleSections.sort(farToNear);

        renderMeshes(event, RenderType.translucent(), false);
        renderMeshes(event, RenderType.lines(), true);
    }

    private void buildPendingSections() {
        int built = 0;
        var iterator = pendingSections.entrySet().iterator();
        while (iterator.hasNext() && built < MAX_SECTION_BUILDS_PER_FRAME) {
            var pending = iterator.next();
            var replacement = buildSection(pending.getKey(), pending.getValue());
            var existing = sections.put(pending.getKey(), replacement);
            iterator.remove();
            if (existing != null) {
                existing.close();
            }
            built++;
        }
    }

    private void renderMeshes(RenderLevelStageEvent event, RenderType renderType, boolean conflicts) {
        boolean hasMesh = false;
        for (var section : visibleSections) {
            if (section.buffer(conflicts) != null) {
                hasMesh = true;
                break;
            }
        }
        if (!hasMesh) {
            return;
        }

        renderType.setupRenderState();
        try {
            var shader = RenderSystem.getShader();
            if (shader == null) {
                return;
            }
            if (shader.CHUNK_OFFSET != null) {
                shader.CHUNK_OFFSET.set(0.0F, 0.0F, 0.0F);
            }
            var camera = event.getCamera().getPosition();
            for (var section : visibleSections) {
                var buffer = section.buffer(conflicts);
                if (buffer == null) {
                    continue;
                }
                Matrix4f modelView = new Matrix4f(event.getModelViewMatrix())
                        .translate(
                                (float) (section.originX() - camera.x),
                                (float) (section.originY() - camera.y),
                                (float) (section.originZ() - camera.z));
                buffer.bind();
                buffer.drawWithShader(modelView, event.getProjectionMatrix(), shader);
            }
        } finally {
            VertexBuffer.unbind();
            renderType.clearRenderState();
        }
    }

    private SectionMesh buildSection(SectionKey key, List<GhostBlock> blocks) {
        RenderSystem.assertOnRenderThread();
        int originX = SectionPos.sectionToBlockCoord(key.x());
        int originY = SectionPos.sectionToBlockCoord(key.y());
        int originZ = SectionPos.sectionToBlockCoord(key.z());

        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (var block : blocks) {
            var pos = block.pos();
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }
        var blockBuffer = buildBlockBuffer(blocks, originX, originY, originZ);
        VertexBuffer conflictBuffer;
        try {
            conflictBuffer = buildConflictBuffer(blocks, originX, originY, originZ);
        } catch (RuntimeException | Error exception) {
            if (blockBuffer != null) {
                blockBuffer.close();
            }
            throw exception;
        }
        var bounds = new AABB(
                minX, minY, minZ,
                maxX + 1.0, maxY + 1.0, maxZ + 1.0).inflate(BOUNDS_INFLATION);
        return new SectionMesh(
                blocks, bounds,
                (minX + maxX + 1.0) * 0.5,
                (minY + maxY + 1.0) * 0.5,
                (minZ + maxZ + 1.0) * 0.5,
                originX, originY, originZ, blockBuffer, conflictBuffer);
    }

    private VertexBuffer buildBlockBuffer(
            List<GhostBlock> blocks, int originX, int originY, int originZ) {
        var renderType = RenderType.translucent();
        var builder = Tesselator.getInstance().begin(renderType.mode(), renderType.format());
        var consumer = new ProjectionVertexConsumer(builder, projectionAlpha);
        var dispatcher = Minecraft.getInstance().getBlockRenderer();
        var models = new IdentityHashMap<BlockState, BakedModel>();
        var poseStack = new PoseStack();
        for (var block : blocks) {
            var pos = block.pos();
            poseStack.pushPose();
            poseStack.translate(
                    pos.getX() - originX + 0.5,
                    pos.getY() - originY + 0.5,
                    pos.getZ() - originZ + 0.5);
            poseStack.scale(MODEL_SCALE, MODEL_SCALE, MODEL_SCALE);
            poseStack.translate(-0.5, -0.5, -0.5);
            var model = models.computeIfAbsent(block.expectedState(), dispatcher::getBlockModel);
            dispatcher.getModelRenderer().renderModel(
                    poseStack.last(), consumer, block.expectedState(), model,
                    1.0F, 1.0F, 1.0F, LightTexture.FULL_BRIGHT,
                    OverlayTexture.NO_OVERLAY);
            poseStack.popPose();
        }
        return upload(builder.build());
    }

    private static VertexBuffer buildConflictBuffer(
            List<GhostBlock> blocks, int originX, int originY, int originZ) {
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

        var renderType = RenderType.lines();
        var builder = Tesselator.getInstance().begin(renderType.mode(), renderType.format());
        var poseStack = new PoseStack();
        for (var block : blocks) {
            if (!block.conflict()) {
                continue;
            }
            var pos = block.pos();
            double x = pos.getX() - originX;
            double y = pos.getY() - originY;
            double z = pos.getZ() - originZ;
            LevelRenderer.renderLineBox(
                    poseStack, builder,
                    new AABB(x, y, z, x + 1.0, y + 1.0, z + 1.0)
                            .inflate(BOUNDS_INFLATION),
                    1.0F, 0.12F, 0.12F, 0.9F);
        }
        return upload(builder.build());
    }

    private static VertexBuffer upload(com.mojang.blaze3d.vertex.MeshData meshData) {
        if (meshData == null) {
            return null;
        }
        VertexBuffer buffer;
        try {
            buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
        } catch (RuntimeException | Error exception) {
            meshData.close();
            throw exception;
        }
        try {
            try {
                buffer.bind();
            } catch (RuntimeException | Error exception) {
                meshData.close();
                throw exception;
            }
            buffer.upload(meshData);
            return buffer;
        } catch (RuntimeException | Error exception) {
            buffer.close();
            throw exception;
        } finally {
            VertexBuffer.unbind();
        }
    }

    @Override
    public void close() {
        if (!RenderSystem.isOnRenderThread()) {
            RenderSystem.recordRenderCall(this::close);
            return;
        }
        for (var section : sections.values()) {
            section.close();
        }
        sections.clear();
        pendingSections.clear();
        visibleSections.clear();
    }

    record GhostBlock(BlockPos pos, BlockState expectedState, boolean conflict) {
        GhostBlock {
            pos = pos.immutable();
        }
    }

    private record SectionKey(int x, int y, int z) {
        static SectionKey of(BlockPos pos) {
            return new SectionKey(
                    SectionPos.blockToSectionCoord(pos.getX()),
                    SectionPos.blockToSectionCoord(pos.getY()),
                    SectionPos.blockToSectionCoord(pos.getZ()));
        }
    }

    private record SectionMesh(
            List<GhostBlock> blocks,
            AABB bounds,
            double centerX,
            double centerY,
            double centerZ,
            int originX,
            int originY,
            int originZ,
            VertexBuffer blockBuffer,
            VertexBuffer conflictBuffer) implements AutoCloseable {
        double distanceToSqr(Vec3 point) {
            double x = centerX - point.x;
            double y = centerY - point.y;
            double z = centerZ - point.z;
            return x * x + y * y + z * z;
        }

        VertexBuffer buffer(boolean conflicts) {
            return conflicts ? conflictBuffer : blockBuffer;
        }

        @Override
        public void close() {
            RenderSystem.assertOnRenderThread();
            if (blockBuffer != null) {
                blockBuffer.close();
            }
            if (conflictBuffer != null) {
                conflictBuffer.close();
            }
        }
    }

    private static final class ProjectionVertexConsumer implements VertexConsumer {
        private final VertexConsumer delegate;
        private final int alpha;

        private ProjectionVertexConsumer(VertexConsumer delegate, int alpha) {
            this.delegate = delegate;
            this.alpha = alpha;
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            delegate.addVertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int sourceAlpha) {
            delegate.setColor(red, green, blue, Math.min(sourceAlpha, alpha));
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            delegate.setUv(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            delegate.setUv1(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            delegate.setUv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            delegate.setNormal(x, y, z);
            return this;
        }
    }
}
