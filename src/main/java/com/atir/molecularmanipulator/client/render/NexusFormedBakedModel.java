package com.atir.molecularmanipulator.client.render;

import appeng.blockentity.crafting.CraftingCubeModelData;
import appeng.client.render.cablebus.CubeBuilder;
import com.atir.molecularmanipulator.client.render.NexusFormedLayout.Box;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.model.IDynamicBakedModel;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.jetbrains.annotations.Nullable;

/** Powered nexus using AE2 crafting-storage rings, inner panel, and a full-bright overlay. */
public final class NexusFormedBakedModel implements IDynamicBakedModel {
    private static final ChunkRenderTypeSet RENDER_TYPES = ChunkRenderTypeSet.of(RenderType.CUTOUT);
    private final TextureAtlasSprite ringCorner;
    private final TextureAtlasSprite ringHor;
    private final TextureAtlasSprite ringVer;
    private final TextureAtlasSprite lightBase;
    private final TextureAtlasSprite light;

    public NexusFormedBakedModel(TextureAtlasSprite ringCorner, TextureAtlasSprite ringHor,
            TextureAtlasSprite ringVer, TextureAtlasSprite lightBase, TextureAtlasSprite light) {
        this.ringCorner = ringCorner;
        this.ringHor = ringHor;
        this.ringVer = ringVer;
        this.lightBase = lightBase;
        this.light = light;
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand,
            ModelData extraData, RenderType renderType) {
        if (side == null) return Collections.emptyList();
        EnumSet<Direction> connections = extraData.get(CraftingCubeModelData.CONNECTIONS);
        if (connections == null) connections = EnumSet.noneOf(Direction.class);
        List<BakedQuad> quads = new ArrayList<>();
        var builder = new CubeBuilder(quads);
        builder.setDrawFaces(EnumSet.of(side));
        builder.setTexture(ringCorner);
        for (Box box : NexusFormedLayout.corners(side, connections)) add(builder, box);
        for (var stripe : NexusFormedLayout.stripes(side, connections)) {
            builder.setTexture(stripe.vertical() ? ringVer : ringHor);
            add(builder, stripe.box());
        }
        Box inner = NexusFormedLayout.inner(side, connections);
        builder.setTexture(lightBase);
        add(builder, inner);
        builder.setEmissiveMaterial(state != null && state.getValue(BlockStateProperties.POWERED));
        builder.setTexture(light);
        add(builder, inner);
        builder.setEmissiveMaterial(false);
        return quads;
    }

    private static void add(CubeBuilder builder, Box box) {
        builder.addCube(box.x1(), box.y1(), box.z1(), box.x2(), box.y2(), box.z2());
    }

    @Override
    public boolean useAmbientOcclusion() {
        return false;
    }

    @Override
    public boolean isGui3d() {
        return false;
    }

    @Override
    public boolean isCustomRenderer() {
        return false;
    }

    @Override
    public TextureAtlasSprite getParticleIcon() {
        return ringCorner;
    }

    @Override
    public boolean usesBlockLight() {
        return false;
    }

    @Override
    public ItemOverrides getOverrides() {
        return ItemOverrides.EMPTY;
    }

    @Override
    public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource rand, ModelData data) {
        return RENDER_TYPES;
    }
}
