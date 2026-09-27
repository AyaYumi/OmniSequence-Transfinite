package com.atir.molecularmanipulator.client.render;

import appeng.client.render.cablebus.CubeBuilder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.ChunkRenderTypeSet;
import net.minecraftforge.client.model.IDynamicBakedModel;
import net.minecraftforge.client.model.data.ModelData;
import org.jetbrains.annotations.Nullable;

/** Simple two-pass block model: the auto crafter base plus a full-bright line layer. */
public final class AutoCrafterGlowBakedModel implements IDynamicBakedModel {
    private static final ChunkRenderTypeSet RENDER_TYPES =
            ChunkRenderTypeSet.of(RenderType.cutout(), RenderType.translucent());
    private final TextureAtlasSprite base;
    private final TextureAtlasSprite glow;

    AutoCrafterGlowBakedModel(TextureAtlasSprite base, TextureAtlasSprite glow) {
        this.base = base;
        this.glow = glow;
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side,
            RandomSource random, ModelData data, RenderType renderType) {
        if (side == null) {
            return Collections.emptyList();
        }
        if (renderType != null && renderType != RenderType.cutout()
                && renderType != RenderType.translucent()) {
            return Collections.emptyList();
        }
        var quads = new ArrayList<BakedQuad>(6);
        var builder = new CubeBuilder(quads);
        builder.setDrawFaces(java.util.EnumSet.of(side));
        if (renderType == null || renderType == RenderType.cutout()) {
            builder.setTexture(base);
            builder.addCube(0, 0, 0, 16, 16, 16);
        } else {
            builder.setEmissiveMaterial(true);
            builder.setTexture(glow);
            builder.addCube(-0.01F, -0.01F, -0.01F, 16.01F, 16.01F, 16.01F);
        }
        return quads;
    }

    @Override public boolean useAmbientOcclusion() { return true; }
    @Override public boolean isGui3d() { return true; }
    @Override public boolean isCustomRenderer() { return false; }
    @Override public TextureAtlasSprite getParticleIcon() { return base; }
    @Override public boolean usesBlockLight() { return true; }
    @Override public ItemOverrides getOverrides() { return ItemOverrides.EMPTY; }
    @Override public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource random, ModelData data) {
        return RENDER_TYPES;
    }
}
