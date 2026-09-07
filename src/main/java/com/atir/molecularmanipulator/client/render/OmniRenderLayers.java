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
    private static final RenderType TRANSLUCENT_EMISSIVE_COLOR =
            createTranslucentEmissiveColor();
    private static final RenderType ADDITIVE_DEPTH_COLOR = createAdditiveDepthColor();
    private static final RenderType ADDITIVE_COLOR = createAdditiveColor();
    private static final RenderType PLACEMENT_LINES = RenderType.create(
            "omni_placement_lines", DefaultVertexFormat.POSITION_COLOR_NORMAL, VertexFormat.Mode.LINES, 1536,
            CompositeState.builder()
                    .setShaderState(RENDERTYPE_LINES_SHADER)
                    .setLineState(new LineStateShard(java.util.OptionalDouble.of(2.0)))
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setDepthTestState(NO_DEPTH_TEST)
                    .setCullState(NO_CULL)
                    .setWriteMaskState(COLOR_WRITE)
                    .createCompositeState(false));
    private static final ShaderStateShard MOLECULAR_SPECTRAL_SHADER =
            new ShaderStateShard(OmniShaders::molecularSpectral);
    private static final ShaderStateShard MATTER_CONDENSATION_SHADER =
            new ShaderStateShard(OmniShaders::matterCondensation);
    private static final ShaderStateShard SINGULARITY_COMPUTE_SHADER =
            new ShaderStateShard(OmniShaders::singularityCompute);
    private static final RenderType MOLECULAR_SPECTRAL_DEPTH = createEffectColor(
            "omni_molecular_spectral_depth", MOLECULAR_SPECTRAL_SHADER, true);
    private static final RenderType MOLECULAR_SPECTRAL_GLOW = createEffectColor(
            "omni_molecular_spectral_glow", MOLECULAR_SPECTRAL_SHADER, false);
    private static final RenderType MATTER_CONDENSATION_DEPTH = createEffectColor(
            "omni_matter_condensation_depth", MATTER_CONDENSATION_SHADER, true, false);
    private static final RenderType MATTER_CONDENSATION_GLOW = createEffectColor(
            "omni_matter_condensation_glow", MATTER_CONDENSATION_SHADER, false);
    private static final RenderType SINGULARITY_COMPUTE_DEPTH = createEffectColor(
            "omni_singularity_compute_depth", SINGULARITY_COMPUTE_SHADER, true);
    private static final RenderType SINGULARITY_COMPUTE_GLOW = createEffectColor(
            "omni_singularity_compute_glow", SINGULARITY_COMPUTE_SHADER, false);

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

    /**
     * Source-alpha emissive core with depth writes. This keeps curves properly
     * occluded by the structure while allowing their authored alpha to soften
     * the hard opaque center of the additive halo.
     */
    public static RenderType translucentEmissiveColor() {
        return TRANSLUCENT_EMISSIVE_COLOR;
    }

    /** Additive emissive core that still writes depth for correct occlusion. */
    public static RenderType additiveDepthColor() {
        return ADDITIVE_DEPTH_COLOR;
    }

    /** The no-depth-write additive geometry pass. */
    public static RenderType additiveColor() {
        return ADDITIVE_COLOR;
    }

    /** Service sockets remain visible through the well without changing scene depth. */
    public static RenderType placementLines() {
        return PLACEMENT_LINES;
    }

    public static RenderType molecularSpectralDepth() {
        return MOLECULAR_SPECTRAL_DEPTH;
    }

    public static RenderType molecularSpectralGlow() {
        return MOLECULAR_SPECTRAL_GLOW;
    }

    public static RenderType matterCondensationDepth() {
        return MATTER_CONDENSATION_DEPTH;
    }

    public static RenderType matterCondensationGlow() {
        return MATTER_CONDENSATION_GLOW;
    }

    public static RenderType singularityComputeDepth() {
        return SINGULARITY_COMPUTE_DEPTH;
    }

    public static RenderType singularityComputeGlow() {
        return SINGULARITY_COMPUTE_GLOW;
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

    private static RenderType createTranslucentEmissiveColor() {
        List<RenderStateShard> states = List.of(
                NO_TEXTURE,
                POSITION_COLOR_SHADER,
                TRANSLUCENT_TRANSPARENCY,
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
        return new OmniRenderLayers("omni_translucent_emissive_color",
                DefaultVertexFormat.POSITION_COLOR, states);
    }

    private static RenderType createAdditiveDepthColor() {
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
                COLOR_DEPTH_WRITE,
                DEFAULT_LINE,
                NO_COLOR_LOGIC
        );
        return new OmniRenderLayers("omni_additive_depth_color",
                DefaultVertexFormat.POSITION_COLOR, states);
    }

    private static RenderType createEffectColor(String name,
            ShaderStateShard shader, boolean writeDepth) {
        return createEffectColor(name, shader, writeDepth, true);
    }

    private static RenderType createEffectColor(String name,
            ShaderStateShard shader, boolean writeDepth, boolean additive) {
        List<RenderStateShard> states = List.of(
                NO_TEXTURE,
                shader,
                additive ? LIGHTNING_TRANSPARENCY : TRANSLUCENT_TRANSPARENCY,
                LEQUAL_DEPTH_TEST,
                CULL,
                NO_LIGHTMAP,
                NO_OVERLAY,
                NO_LAYERING,
                MAIN_TARGET,
                DEFAULT_TEXTURING,
                writeDepth ? COLOR_DEPTH_WRITE : COLOR_WRITE,
                DEFAULT_LINE,
                NO_COLOR_LOGIC
        );
        return new OmniRenderLayers(name,
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
