package com.atir.molecularmanipulator.client.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Render layers for programmatic multiblock geometry.
 *
 * <p>The two color layers intentionally use the full-bright position/color
 * shader.  {@link #solidEmissiveColor()} writes depth and is suitable for the
 * physical, block-like silhouette.  {@link #additiveColor()} keeps the
 * LEQUAL depth test but does not write depth, allowing a second pass to build
 * a controlled glow without turning the shape into a particle effect.</p>
 */
@OnlyIn(Dist.CLIENT)
public final class OmniRenderLayers extends RenderType {

    private static final Map<ResourceLocation, RenderType> ADDITIVE_TEXTURE_CACHE =
            new ConcurrentHashMap<>();
    private static final RenderType SOLID_EMISSIVE_COLOR = createSolidEmissiveColor();
    private static final RenderType ADDITIVE_COLOR = createAdditiveColor();

    private OmniRenderLayers(String name, VertexFormat vertexFormat,
            List<RenderStateShard> states) {
        super(name, vertexFormat, VertexFormat.Mode.QUADS, 4096,
                false, true,
                () -> states.forEach(RenderStateShard::setupRenderState),
                () -> states.forEach(RenderStateShard::clearRenderState));
    }

    /** A vanilla emissive textured layer for optional texture overlays. */
    public static RenderType translucentEmissive(ResourceLocation texture) {
        return RenderType.entityTranslucentEmissive(texture, false);
    }

    /**
     * Source-alpha additive textured layer.  Texture geometry remains depth
     * tested, but its fragments do not write depth so overlapping ribbons can
     * accumulate light.
     */
    public static RenderType additiveEmissive(ResourceLocation texture) {
        return ADDITIVE_TEXTURE_CACHE.computeIfAbsent(texture,
                OmniRenderLayers::createAdditiveEmissive);
    }

    /** The depth-writing, full-bright geometry pass. */
    public static RenderType solidEmissiveColor() {
        return SOLID_EMISSIVE_COLOR;
    }

    /** The no-depth-write additive geometry pass. */
    public static RenderType additiveColor() {
        return ADDITIVE_COLOR;
    }

    private static RenderType createSolidEmissiveColor() {
        List<RenderStateShard> states = List.of(
                NO_TEXTURE,
                POSITION_COLOR_SHADER,
                NO_TRANSPARENCY,
                LEQUAL_DEPTH_TEST,
                CULL,
                NO_LIGHTMAP,
                NO_OVERLAY,
                NO_LAYERING,
                MAIN_TARGET,
                DEFAULT_TEXTURING,
                COLOR_DEPTH_WRITE,
                DEFAULT_LINE,
                NO_COLOR_LOGIC
        );
        return new OmniRenderLayers("omni_solid_emissive_color",
                DefaultVertexFormat.POSITION_COLOR, states);
    }

    private static RenderType createAdditiveColor() {
        List<RenderStateShard> states = List.of(
                NO_TEXTURE,
                POSITION_COLOR_SHADER,
                LIGHTNING_TRANSPARENCY,
                LEQUAL_DEPTH_TEST,
                CULL,
                NO_LIGHTMAP,
                NO_OVERLAY,
                NO_LAYERING,
                MAIN_TARGET,
                DEFAULT_TEXTURING,
                COLOR_WRITE,
                DEFAULT_LINE,
                NO_COLOR_LOGIC
        );
        return new OmniRenderLayers("omni_additive_color",
                DefaultVertexFormat.POSITION_COLOR, states);
    }

    private static RenderType createAdditiveEmissive(ResourceLocation texture) {
        TextureStateShard textureState = new TextureStateShard(texture, false, false);
        List<RenderStateShard> states = List.of(
                textureState,
                RENDERTYPE_ENTITY_TRANSLUCENT_EMISSIVE_SHADER,
                LIGHTNING_TRANSPARENCY,
                LEQUAL_DEPTH_TEST,
                NO_CULL,
                NO_LIGHTMAP,
                OVERLAY,
                NO_LAYERING,
                MAIN_TARGET,
                DEFAULT_TEXTURING,
                COLOR_WRITE,
                DEFAULT_LINE,
                NO_COLOR_LOGIC
        );
        return new OmniRenderLayers("omni_additive_emissive_" + texture,
                DefaultVertexFormat.NEW_ENTITY, states);
    }
}
