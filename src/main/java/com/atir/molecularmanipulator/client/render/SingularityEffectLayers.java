package com.atir.molecularmanipulator.client.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.MultiBufferSource;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.resources.ResourceLocation;
import java.lang.reflect.Method;

/** Two-sided ribbons need no duplicated back faces; solid structures use their own layers. */
public final class SingularityEffectLayers extends RenderType {
    private static final RenderType GLOW = layer("singularity_tianyi_glow", false);
    private static final RenderType CRYSTAL = layer("singularity_tianyi_crystal", true);
    private static final ResourceLocation EFFECT_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "molecularmanipulator", "textures/effect/singularity_white.png");
    private static final ResourceLocation STAR_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "molecularmanipulator", "textures/effect/singularity_star.png");
    private static final RenderType STAR = RenderType.create("singularity_stellar_core",
            DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 32768, false, false,
            CompositeState.builder().setShaderState(RENDERTYPE_ENTITY_SOLID_SHADER)
                    .setTextureState(new TextureStateShard(STAR_TEXTURE, false, false))
                    .setTransparencyState(NO_TRANSPARENCY).setDepthTestState(LEQUAL_DEPTH_TEST)
                    .setCullState(NO_CULL).setLightmapState(LIGHTMAP).setOverlayState(OVERLAY)
                    .setWriteMaskState(COLOR_DEPTH_WRITE).createCompositeState(false));
    private static final RenderType SHADER_GLOW = shaderLayer("singularity_shader_glow", false);
    private static final RenderType SHADER_CRYSTAL = shaderLayer("singularity_shader_crystal", true);
    private static final RenderType HELIAN_GLOW = entityLayer("singularity_helian_glow", false);
    private static final RenderType HELIAN_CRYSTAL = entityLayer("singularity_helian_crystal", true);
    private static final Method IRIS_ACTIVE = irisActiveMethod();
    private static final Object IRIS_API = irisApi();

    private SingularityEffectLayers(String name, VertexFormat format, VertexFormat.Mode mode, int size,
                             boolean crumbling, boolean sort, Runnable setup, Runnable clear) {
        super(name, format, mode, size, crumbling, sort, setup, clear);
    }

    public static RenderType glow() { return GLOW; }
    public static RenderType crystal() { return CRYSTAL; }
    public static VertexConsumer glow(MultiBufferSource buffers) { return consumer(buffers, false); }
    public static VertexConsumer crystal(MultiBufferSource buffers) { return consumer(buffers, true); }
    public static VertexConsumer star(MultiBufferSource buffers) {
        return shadersActive() ? new SingularityShaderVertices(buffers.getBuffer(STAR)) : buffers.getBuffer(CRYSTAL);
    }

    private static VertexConsumer consumer(MultiBufferSource buffers, boolean crystal) {
        boolean shaders = shadersActive();
        boolean entity = shaders && entityEffectsActive();
        var target = buffers.getBuffer(shaders ? entity ? crystal ? HELIAN_CRYSTAL : HELIAN_GLOW
                : crystal ? SHADER_CRYSTAL : SHADER_GLOW : crystal ? CRYSTAL : GLOW);
        return shaders ? new SingularityShaderVertices(target) : target;
    }
    private static Method irisActiveMethod() {
        try { return Class.forName("net.irisshaders.iris.api.v0.IrisApi").getMethod("isShaderPackInUse"); }
        catch (ReflectiveOperationException | LinkageError ignored) { return null; }
    }
    private static Object irisApi() {
        if (IRIS_ACTIVE == null) return null;
        try { return IRIS_ACTIVE.getDeclaringClass().getMethod("getInstance").invoke(null); }
        catch (ReflectiveOperationException | LinkageError ignored) { return null; }
    }
    public static boolean shadersActive() {
        if (IRIS_API == null) return false;
        try { return Boolean.TRUE.equals(IRIS_ACTIVE.invoke(IRIS_API)); }
        catch (ReflectiveOperationException ignored) { return false; }
    }
    private static boolean entityEffectsActive() {
        try {
            var iris = Class.forName("net.irisshaders.iris.Iris");
            var config = iris.getMethod("getIrisConfig").invoke(null);
            var name = (java.util.Optional<?>) config.getClass().getMethod("getShaderPackName").invoke(config);
            var pack = name.map(Object::toString).orElse("").toLowerCase(java.util.Locale.ROOT);
            return pack.contains("helian") || pack.contains("photon") || pack.contains("iterationrp");
        } catch (ReflectiveOperationException | LinkageError ignored) { return false; }
    }
    private static RenderType entityLayer(String name, boolean crystal) {
        return RenderType.create(name, DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS,
                4096, false, true, CompositeState.builder()
                        .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
                        .setTextureState(new TextureStateShard(EFFECT_TEXTURE, false, false))
                        .setTransparencyState(crystal ? TRANSLUCENT_TRANSPARENCY : LIGHTNING_TRANSPARENCY)
                        .setDepthTestState(LEQUAL_DEPTH_TEST).setCullState(NO_CULL)
                        .setLightmapState(LIGHTMAP).setOverlayState(OVERLAY)
                        // Deferred transparent programs reconstruct these effects from depthtex0.
                        .setWriteMaskState(COLOR_DEPTH_WRITE)
                        .createCompositeState(false));
    }
    private static RenderType shaderLayer(String name, boolean crystal) {
        // Beacon rendering supplies the emissive pass used by Photon and iterationRP.
        return RenderType.create(name, DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS,
                4096, false, crystal, CompositeState.builder()
                        .setShaderState(RENDERTYPE_BEACON_BEAM_SHADER)
                        .setTextureState(new TextureStateShard(EFFECT_TEXTURE, false, false))
                        .setTransparencyState(crystal ? TRANSLUCENT_TRANSPARENCY : LIGHTNING_TRANSPARENCY)
                        .setDepthTestState(LEQUAL_DEPTH_TEST).setCullState(NO_CULL)
                        .setLightmapState(NO_LIGHTMAP).setOverlayState(NO_OVERLAY)
                        .setWriteMaskState(crystal ? COLOR_DEPTH_WRITE : COLOR_WRITE)
                        .createCompositeState(false));
    }

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
