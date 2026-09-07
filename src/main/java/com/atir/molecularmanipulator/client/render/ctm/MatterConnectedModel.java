package com.atir.molecularmanipulator.client.render.ctm;

import com.atir.molecularmanipulator.client.render.ctm.MatterConnectedTextureRules.Face;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.IQuadTransformer;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;
import org.jetbrains.annotations.Nullable;

/** Connected native 16px faces; neither their center glyphs nor item models are rescaled. */
public final class MatterConnectedModel extends BakedModelWrapper<BakedModel> {
    private static final ModelProperty<Long> CONNECTIONS = new ModelProperty<>();
    private static final float EPSILON = 0.0001F;
    private static volatile int generation;
    private static volatile LookupContext production = new LookupContext(MatterConnectedModel::gameModel,
            sprite -> Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(sprite));
    private final LookupContext context;
    private final MatterGoldEmissive gold = new MatterGoldEmissive();
    private final Map<BakedQuad, Map<Integer, List<BakedQuad>>> splitCache = Collections.synchronizedMap(new IdentityHashMap<>());
    private final Map<BakedQuad, Optional<FaceQuad>> quadMetadata = Collections.synchronizedMap(new IdentityHashMap<>());

    public MatterConnectedModel(BakedModel original, Function<BlockState, BakedModel> modelLookup,
            Function<ResourceLocation, TextureAtlasSprite> spriteLookup) {
        this(original, new LookupContext(modelLookup, spriteLookup));
    }

    /** Tests without icon faces can omit a separate atlas; missing filler sprites keep the original face. */
    public MatterConnectedModel(BakedModel original, Function<BlockState, BakedModel> modelLookup) {
        this(original, modelLookup, sprite -> null);
    }

    MatterConnectedModel(BakedModel original, LookupContext context) {
        super(Objects.requireNonNull(original));
        this.context = context;
    }

    public static int generation() {
        return generation;
    }

    static synchronized void installed(LookupContext context) {
        production = context;
        generation++;
    }

    public static ModelData modelData(long packedMasks) {
        long used = packedMasks & ((1L << 54) - 1);
        return used == 0 ? ModelData.EMPTY : ModelData.of(CONNECTIONS, used);
    }

    public static long connectionMasks(Function<BlockPos, BlockState> states, BlockPos pos, BlockState state) {
        return production.connections(states, pos, state);
    }

    public static long connectionMasks(BlockGetter view, BlockPos pos, BlockState state) {
        return production.connections(view, pos, state);
    }

    public static long connectionMasks(Function<BlockPos, BlockState> states, BlockPos pos, BlockState state,
            Function<BlockState, BakedModel> modelLookup) {
        return new LookupContext(modelLookup, sprite -> null).connections(states, pos, state);
    }

    @Override
    public ModelData getModelData(BlockAndTintGetter level, BlockPos pos, BlockState state, ModelData data) {
        var inherited = originalModel.getModelData(level, pos, state, data);
        long masks = context.connections(level, pos, state);
        return masks == 0 && !inherited.has(CONNECTIONS) ? inherited : inherited.derive().with(CONNECTIONS, masks).build();
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random,
            ModelData data, @Nullable RenderType renderType) {
        Long packed = data.get(CONNECTIONS);
        if (state != null && side != null && packed != null && (packed & (1L << (48 + side.ordinal()))) != 0) return List.of();
        var quads = originalModel.getQuads(state, side, random, data, renderType);
        if (state == null || quads.isEmpty()) return quads;
        if (packed == null || packed == 0) return gold.apply(quads);
        var result = new ArrayList<BakedQuad>();
        for (var quad : quads) {
            if ((packed & (1L << (48 + quad.getDirection().ordinal()))) != 0
                    && quadMetadata.computeIfAbsent(quad, key -> Optional.ofNullable(inspect(key))).isPresent()) continue;
            int mask = (int)(packed >>> (quad.getDirection().ordinal() * 8)) & 255;
            if (mask == 0) {
                result.add(quad);
                continue;
            }
            var byMask = splitCache.computeIfAbsent(quad, ignored -> new ConcurrentHashMap<>());
            result.addAll(byMask.computeIfAbsent(mask, ignored -> split(quad, mask)));
        }
        return gold.apply(result);
    }

    private List<BakedQuad> split(BakedQuad quad, int mask) {
        var info = quadMetadata.computeIfAbsent(quad, key -> Optional.ofNullable(inspect(key))).orElse(null);
        if (info == null) return List.of(quad);
        var patches = MatterConnectedTextureRules.patches(info.face(), mask);
        if (patches.stream().noneMatch(patch -> patch.remapped())) return List.of(quad);
        TextureAtlasSprite filler = null;
        if (patches.stream().anyMatch(patch -> patch.useCasingBackground(info.face()))) {
            var name = ResourceLocation.fromNamespaceAndPath("molecularmanipulator", "block/matter_fabrication_casing_top");
            filler = context.spriteLookup.apply(name);
            if (filler == null || !filler.contents().name().equals(name)) return List.of(quad);
        }
        var result = new ArrayList<BakedQuad>(patches.size());
        for (var patch : patches) {
            var sprite = patch.useCasingBackground(info.face()) ? filler : quad.getSprite();
            int[] output = new int[quad.getVertices().length];
            for (int vertex = 0; vertex < 4; vertex++) {
                float x = info.uCorners()[vertex] == 0 ? patch.x0() : patch.x1();
                float y = info.vCorners()[vertex] == 0 ? patch.y0() : patch.y1();
                float u = info.uCorners()[vertex] == 0 ? patch.u0() : patch.u1();
                float v = info.vCorners()[vertex] == 0 ? patch.v0() : patch.v1();
                int offset = vertex * IQuadTransformer.STRIDE;
                System.arraycopy(quad.getVertices(), offset, output, offset, IQuadTransformer.STRIDE);
                Vec3 point = info.position(x, y);
                output[offset + IQuadTransformer.POSITION] = Float.floatToRawIntBits((float)point.x);
                output[offset + IQuadTransformer.POSITION + 1] = Float.floatToRawIntBits((float)point.y);
                output[offset + IQuadTransformer.POSITION + 2] = Float.floatToRawIntBits((float)point.z);
                output[offset + IQuadTransformer.COLOR] = info.interpolatePacked(IQuadTransformer.COLOR, x, y, 8);
                if (IQuadTransformer.UV2 >= 0) output[offset + IQuadTransformer.UV2] = info.interpolatePacked(IQuadTransformer.UV2, x, y, 16);
                float sourceU = info.minU() + u * (info.maxU() - info.minU());
                float sourceV = info.minV() + v * (info.maxV() - info.minV());
                output[offset + IQuadTransformer.UV0] = Float.floatToRawIntBits(sprite == quad.getSprite() ? sourceU
                        : sprite.getU(Math.clamp(quad.getSprite().getUOffset(sourceU), 2.5F / 16, 13.5F / 16)));
                output[offset + IQuadTransformer.UV0 + 1] = Float.floatToRawIntBits(sprite == quad.getSprite() ? sourceV
                        : sprite.getV(Math.clamp(quad.getSprite().getVOffset(sourceV), 2.5F / 16, 13.5F / 16)));
            }
            result.add(new BakedQuad(output, quad.getTintIndex(), quad.getDirection(), sprite,
                    quad.isShade(), quad.hasAmbientOcclusion()));
        }
        return List.copyOf(result);
    }

    /** Exposes the same actual baked UV axes for native-quad tests and world-mask exporters. */
    @Nullable
    public static Face face(BakedQuad quad) {
        var metadata = inspect(quad);
        return metadata == null ? null : metadata.face();
    }

    @Nullable
    private static FaceQuad inspect(BakedQuad quad) {
        var sprite = quad.getSprite();
        var texture = sprite.contents().name();
        if (!texture.getNamespace().equals("molecularmanipulator")
                || !texture.getPath().startsWith("block/matter_fabrication_")) return null;
        int[] data = quad.getVertices();
        if (data.length != 4 * IQuadTransformer.STRIDE) return null;
        Vec3[] positions = new Vec3[4];
        float[] us = new float[4], vs = new float[4];
        float minU = Float.POSITIVE_INFINITY, maxU = Float.NEGATIVE_INFINITY;
        float minV = Float.POSITIVE_INFINITY, maxV = Float.NEGATIVE_INFINITY;
        Direction normal = quad.getDirection();
        double plane = normal.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1 : 0;
        for (int vertex = 0; vertex < 4; vertex++) {
            int offset = vertex * IQuadTransformer.STRIDE;
            positions[vertex] = new Vec3(Float.intBitsToFloat(data[offset]), Float.intBitsToFloat(data[offset + 1]), Float.intBitsToFloat(data[offset + 2]));
            if (!Double.isFinite(positions[vertex].lengthSqr()) || Math.abs(component(positions[vertex], normal.getAxis()) - plane) > EPSILON) return null;
            us[vertex] = Float.intBitsToFloat(data[offset + IQuadTransformer.UV0]);
            vs[vertex] = Float.intBitsToFloat(data[offset + IQuadTransformer.UV0 + 1]);
            minU = Math.min(minU, us[vertex]); maxU = Math.max(maxU, us[vertex]);
            minV = Math.min(minV, vs[vertex]); maxV = Math.max(maxV, vs[vertex]);
        }
        if (!Float.isFinite(minU + maxU + minV + maxV) || maxU - minU < 1.0E-7F || maxV - minV < 1.0E-7F) return null;
        int[] uCorners = new int[4], vCorners = new int[4], corners = {-1, -1, -1, -1};
        for (int vertex = 0; vertex < 4; vertex++) {
            float u = (us[vertex] - minU) / (maxU - minU), v = (vs[vertex] - minV) / (maxV - minV);
            if (Math.min(Math.abs(u), Math.abs(u - 1)) > EPSILON || Math.min(Math.abs(v), Math.abs(v - 1)) > EPSILON) return null;
            uCorners[vertex] = Math.round(u); vCorners[vertex] = Math.round(v);
            int corner = uCorners[vertex] + vCorners[vertex] * 2;
            if (corners[corner] != -1) return null;
            corners[corner] = vertex;
        }
        var origin = positions[corners[0]];
        var uVector = positions[corners[1]].subtract(origin);
        var vVector = positions[corners[2]].subtract(origin);
        Direction uDirection = unitAxis(uVector), vDirection = unitAxis(vVector);
        if (uDirection == null || vDirection == null || uDirection.getAxis() == vDirection.getAxis()
                || uDirection.getAxis() == normal.getAxis() || vDirection.getAxis() == normal.getAxis()
                || positions[corners[3]].distanceToSqr(origin.add(uVector).add(vVector)) > EPSILON * EPSILON) return null;
        for (var position : positions) {
            if (position.x < -EPSILON || position.x > 1 + EPSILON || position.y < -EPSILON || position.y > 1 + EPSILON
                    || position.z < -EPSILON || position.z > 1 + EPSILON) return null;
        }
        return new FaceQuad(quad, new Face(texture.toString(), uDirection, vDirection), positions, corners, uCorners, vCorners, minU, maxU, minV, maxV);
    }

    @Nullable
    private static Direction unitAxis(Vec3 vector) {
        for (var direction : Direction.values()) {
            if (vector.distanceToSqr(new Vec3(direction.getStepX(), direction.getStepY(), direction.getStepZ())) < EPSILON * EPSILON) return direction;
        }
        return null;
    }

    private static double component(Vec3 point, Direction.Axis axis) {
        return switch (axis) { case X -> point.x; case Y -> point.y; case Z -> point.z; };
    }

    private record FaceQuad(BakedQuad quad, Face face, Vec3[] positions, int[] corners, int[] uCorners, int[] vCorners,
            float minU, float maxU, float minV, float maxV) {
        Vec3 position(float u, float v) {
            return positions[corners[0]].lerp(positions[corners[1]], u)
                    .lerp(positions[corners[2]].lerp(positions[corners[3]], u), v);
        }

        int interpolatePacked(int attribute, float u, float v, int bits) {
            int result = 0;
            long mask = (1L << bits) - 1;
            for (int shift = 0; shift < 32; shift += bits) {
                double a = (quad.getVertices()[corners[0] * IQuadTransformer.STRIDE + attribute] >>> shift) & mask;
                double b = (quad.getVertices()[corners[1] * IQuadTransformer.STRIDE + attribute] >>> shift) & mask;
                double c = (quad.getVertices()[corners[2] * IQuadTransformer.STRIDE + attribute] >>> shift) & mask;
                double d = (quad.getVertices()[corners[3] * IQuadTransformer.STRIDE + attribute] >>> shift) & mask;
                result |= (int)Math.round((a + (b - a) * u) * (1 - v) + (c + (d - c) * u) * v) << shift;
            }
            return result;
        }
    }

    static final class LookupContext {
        final Function<BlockState, BakedModel> modelLookup;
        final Function<ResourceLocation, TextureAtlasSprite> spriteLookup;
        private final Map<StateFace, Optional<FaceQuad>> faces = new ConcurrentHashMap<>();

        LookupContext(Function<BlockState, BakedModel> modelLookup, Function<ResourceLocation, TextureAtlasSprite> spriteLookup) {
            this.modelLookup = Objects.requireNonNull(modelLookup);
            this.spriteLookup = Objects.requireNonNull(spriteLookup);
        }

        long connections(Function<BlockPos, BlockState> sampler, BlockPos pos, BlockState state) {
            if (state == null || state.isAir()) return 0;
            return connections(new SamplerView(at -> at.equals(pos) ? state : sampler.apply(at)), pos, state);
        }

        long connections(BlockGetter view, BlockPos pos, BlockState state) {
            if (state == null || state.isAir()) return 0;
            long packed = 0;
            for (var direction : Direction.values()) {
                if (!Block.shouldRenderFace(state, view, pos, direction, pos.relative(direction))) {
                    packed |= 1L << (48 + direction.ordinal());
                    continue;
                }
                var self = exposedFace(view, pos, direction);
                int mask = MatterConnectedTextureRules.connections(pos, direction, self,
                        (at, side) -> exposedFace(view, at, side));
                packed |= (long)mask << (direction.ordinal() * 8);
            }
            return packed;
        }

        @Nullable
        private Face exposedFace(BlockGetter view, BlockPos pos, Direction direction) {
            var state = view.getBlockState(pos);
            if (state.isAir() || !Block.shouldRenderFace(state, view, pos, direction, pos.relative(direction))) return null;
            BakedModel model = modelLookup.apply(state);
            while (model instanceof MatterConnectedModel wrapped) model = wrapped.originalModel;
            if (model == null) return null;
            // A reload can publish the new bake before the dispatcher swaps its old map; model identity
            // keeps that brief transition from pinning old UV axes in a new generation's state cache.
            return faces.computeIfAbsent(new StateFace(state, direction, model), key -> {
                FaceQuad selected = null;
                var quads = new ArrayList<>(key.model().getQuads(state, direction, RandomSource.create(0), ModelData.EMPTY, null));
                quads.addAll(key.model().getQuads(state, null, RandomSource.create(0), ModelData.EMPTY, null));
                for (var quad : quads) {
                    if (quad.getDirection() != direction) continue;
                    var info = inspect(quad);
                    if (info == null) continue;
                    if (selected != null && !selected.face().equals(info.face())) return Optional.empty();
                    selected = info;
                }
                return Optional.ofNullable(selected);
            }).map(FaceQuad::face).orElse(null);
        }
    }

    private record StateFace(BlockState state, Direction direction, BakedModel model) {
    }

    /** Shape visibility sees the same planned overlay as the face-neighbor sampler. */
    private record SamplerView(Function<BlockPos, BlockState> sampler) implements BlockGetter {
        @Override public BlockState getBlockState(BlockPos pos) {
            var state = sampler.apply(pos);
            return state == null ? Blocks.AIR.defaultBlockState() : state;
        }
        @Override public FluidState getFluidState(BlockPos pos) { return getBlockState(pos).getFluidState(); }
        @Override @Nullable public BlockEntity getBlockEntity(BlockPos pos) { return null; }
        @Override public int getHeight() { return 4096; }
        @Override public int getMinBuildHeight() { return -2048; }
    }

    private static BakedModel gameModel(BlockState state) {
        return Minecraft.getInstance().getBlockRenderer().getBlockModel(state);
    }
}
