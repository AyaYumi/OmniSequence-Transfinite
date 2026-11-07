package com.atir.molecularmanipulator.client.render;

import com.atir.molecularmanipulator.block.StarComputationEngineCoreBlock;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockElementFace;
import net.minecraft.client.renderer.block.model.BlockFaceUV;
import net.minecraft.client.renderer.block.model.FaceBakery;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.model.ExtraFaceData;
import net.neoforged.neoforge.client.model.IDynamicBakedModel;
import net.neoforged.neoforge.client.model.IQuadTransformer;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * Formed 3x3x3 engine shell with tiered emissive artwork and real edge joins.
 *
 * <p>The atlas contains one 48px face per 16px block. At bake time the model
 * learns which physical direction each UV edge represents after the blockstate
 * rotation, so a connection never relies on a hard-coded facing convention.</p>
 */
public final class StarComputationEngineFormedBakedModel implements IDynamicBakedModel {
    private static final int TIERS = 4;
    private static final int MASKS = 16;
    private static final ExtraFaceData FULLBRIGHT = new ExtraFaceData(-1, 15, 15, false);

    private static final float EPSILON = 0.0001F;
    private static final ChunkRenderTypeSet RENDER_TYPES = ChunkRenderTypeSet.of(RenderType.CUTOUT);
    private static final FaceBakery FACE_BAKERY = new FaceBakery();
    private final TextureAtlasSprite particle;
    private final TextureAtlasSprite[][] base;
    private final TextureAtlasSprite[][] light;
    private final List<BakedQuad>[][] quads;

    @SuppressWarnings("unchecked")
    public StarComputationEngineFormedBakedModel(TextureAtlasSprite particle,
            TextureAtlasSprite[][] base, TextureAtlasSprite[][] light,
            net.minecraft.client.resources.model.ModelState modelState) {
        this.particle = particle;
        this.base = base;
        this.light = light;
        this.quads = new List[TIERS][MASKS];
        bakeFaces(modelState);
    }

    private void bakeFaces(net.minecraft.client.resources.model.ModelState modelState) {
        Vector3f from = new Vector3f(-16, -16, -16);
        Vector3f to = new Vector3f(32, 32, 32);
        for (int tier = 0; tier < TIERS; tier++) {
            for (int connections = 0; connections < MASKS; connections++) {
                quads[tier][connections] = new ArrayList<>(tier == 0 ? 11 : 12);
            }
        }
        for (Direction localFace : Direction.values()) {
            var probe = bake(from, to, localFace, base[0][0], modelState, ExtraFaceData.DEFAULT, true);
            Direction actualFace = probe.getDirection();
            var edges = edgeDirections(probe);
            for (int tier = 0; tier < TIERS; tier++) {
                for (int connections = 0; connections < MASKS; connections++) {
                    // The core keeps its full face; only the selected atlas edge
                    // changes, allowing anchors on multiple horizontal sides.
                    int textureMask = 0;
                    for (int edge = 0; edge < 4; edge++) {
                        if (edges[edge] != null && (connections & connectionBit(edges[edge])) != 0) {
                            textureMask |= 1 << edge;
                        }
                    }
                    quads[tier][connections].add(bake(from, to, localFace, base[tier][textureMask],
                            modelState, ExtraFaceData.DEFAULT, true));
                    // Tiered bottom panels now have their own anchor-pattern glow;
                    // the untouched cyan fallback still has only five light faces.
                    if (localFace != Direction.DOWN || tier != 0) {
                        quads[tier][connections].add(bake(from, to, localFace, light[tier][textureMask],
                                modelState, FULLBRIGHT, false));
                    }
                }
            }
        }
        for (int tier = 0; tier < TIERS; tier++) {
            for (int connections = 0; connections < MASKS; connections++) {
                quads[tier][connections] = List.copyOf(quads[tier][connections]);
            }
        }
    }

    private static BakedQuad bake(Vector3f from, Vector3f to, Direction face,
            TextureAtlasSprite sprite, net.minecraft.client.resources.model.ModelState modelState,
            ExtraFaceData faceData, boolean shade) {
        float[] coordinates = switch (face) {
            case NORTH -> new float[] {0, 0, 6, 6};
            case UP -> new float[] {0, 6, 6, 12};
            case DOWN -> new float[] {6, 6, 12, 12};
            default -> new float[] {6, 0, 12, 6};
        };
        var uv = new BlockFaceUV(coordinates, 0);
        var element = new BlockElementFace(null, BlockElementFace.NO_TINT, "", uv, faceData, null);
        return FACE_BAKERY.bakeQuad(from, to, element, sprite, face, modelState, null, shade);
    }

    /** Returns edge directions in lowU, highU, lowV, highV order. */
    private static Direction[] edgeDirections(BakedQuad quad) {
        int[] data = quad.getVertices();
        float minU = Float.POSITIVE_INFINITY, maxU = Float.NEGATIVE_INFINITY;
        float minV = Float.POSITIVE_INFINITY, maxV = Float.NEGATIVE_INFINITY;
        for (int vertex = 0; vertex < 4; vertex++) {
            int offset = vertex * IQuadTransformer.STRIDE;
            float u = Float.intBitsToFloat(data[offset + IQuadTransformer.UV0]);
            float v = Float.intBitsToFloat(data[offset + IQuadTransformer.UV0 + 1]);
            minU = Math.min(minU, u); maxU = Math.max(maxU, u);
            minV = Math.min(minV, v); maxV = Math.max(maxV, v);
        }
        Direction[] result = new Direction[4];
        result[0] = edgeDirection(quad, data, minU, true, minV, maxV);
        result[1] = edgeDirection(quad, data, maxU, true, minV, maxV);
        result[2] = edgeDirection(quad, data, minV, false, minU, maxU);
        result[3] = edgeDirection(quad, data, maxV, false, minU, maxU);
        return result;
    }

    private static Direction edgeDirection(BakedQuad quad, int[] data, float edge, boolean uEdge,
            float otherMin, float otherMax) {
        float sx = 0, sy = 0, sz = 0;
        int count = 0;
        for (int vertex = 0; vertex < 4; vertex++) {
            int offset = vertex * IQuadTransformer.STRIDE;
            float u = Float.intBitsToFloat(data[offset + IQuadTransformer.UV0]);
            float v = Float.intBitsToFloat(data[offset + IQuadTransformer.UV0 + 1]);
            float coordinate = uEdge ? u : v;
            float other = uEdge ? v : u;
            if (Math.abs(coordinate - edge) <= EPSILON
                    && other >= otherMin - EPSILON && other <= otherMax + EPSILON) {
                sx += Float.intBitsToFloat(data[offset + IQuadTransformer.POSITION]);
                sy += Float.intBitsToFloat(data[offset + IQuadTransformer.POSITION + 1]);
                sz += Float.intBitsToFloat(data[offset + IQuadTransformer.POSITION + 2]);
                count++;
            }
        }
        if (count == 0) return null;
        // The block center is (0.5, 0.5, 0.5); every edge's midpoint points
        // directly toward one of the six world directions after model rotation.
        float dx = sx / count - 0.5F;
        float dy = sy / count - 0.5F;
        float dz = sz / count - 0.5F;
        switch (quad.getDirection().getAxis()) {
            case X -> dx = 0;
            case Y -> dy = 0;
            case Z -> dz = 0;
        }
        return Direction.getNearest(dx, dy, dz);
    }

    private static int connectionBit(Direction direction) {
        return switch (direction) {
            case NORTH -> 1;
            case EAST -> 2;
            case SOUTH -> 4;
            case WEST -> 8;
            default -> 0;
        };
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side,
            RandomSource random, ModelData data, @Nullable RenderType renderType) {
        // This is the orrery's real 3x3x3 central body, not a standalone legacy engine.
        if (state != null && (!StarComputationEngineCoreBlock.isStarOrrery(state)
                || !state.getValue(StarComputationEngineCoreBlock.FORMED))) return List.of();
        if (side != null || renderType != null && renderType != RenderType.CUTOUT) return List.of();
        var visual = data.get(StarComputationEngineModelData.PROPERTY);
        if (visual == null) visual = StarComputationEngineModelData.NONE;
        return quads[visual.tier()][visual.connections()];
    }

    @Override public boolean useAmbientOcclusion() { return false; }
    @Override public boolean isGui3d() { return false; }
    @Override public boolean isCustomRenderer() { return false; }
    @Override public TextureAtlasSprite getParticleIcon() { return particle; }
    @Override public boolean usesBlockLight() { return true; }
    @Override public ItemOverrides getOverrides() { return ItemOverrides.EMPTY; }
    @Override public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource random, ModelData data) {
        return RENDER_TYPES;
    }
}
