package com.atir.molecularmanipulator.client.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;

/** Two-sided ribbons need no duplicated back faces; solid structures use their own layers. */
public final class TaixuEffectLayers extends RenderType {
    private static final RenderType GLOW = layer("taixu_tianyi_glow", false);
    private static final RenderType CRYSTAL = layer("taixu_tianyi_crystal", true);

    private TaixuEffectLayers(String name, VertexFormat format, VertexFormat.Mode mode, int size,
                             boolean crumbling, boolean sort, Runnable setup, Runnable clear) {
        super(name, format, mode, size, crumbling, sort, setup, clear);
    }

    public static RenderType glow() { return GLOW; }
    public static RenderType crystal() { return CRYSTAL; }

    private static RenderType layer(String name, boolean crystal) {
        return RenderType.create(name, DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS,
                4096, false, false, CompositeState.builder()
                        .setShaderState(POSITION_COLOR_SHADER)
                        .setTransparencyState(crystal ? TRANSLUCENT_TRANSPARENCY : LIGHTNING_TRANSPARENCY)
                        .setDepthTestState(LEQUAL_DEPTH_TEST)
                        .setCullState(NO_CULL)
                        .setLightmapState(NO_LIGHTMAP)
                        .setOverlayState(NO_OVERLAY)
                        .setWriteMaskState(crystal ? COLOR_DEPTH_WRITE : COLOR_WRITE)
                        .createCompositeState(false));
    }
}
