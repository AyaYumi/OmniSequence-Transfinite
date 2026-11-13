package com.atir.molecularmanipulator.client.render;

import com.atir.molecularmanipulator.MolecularManipulator;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RegisterShadersEvent;

import java.io.IOException;

/** Client-owned core shaders used by the three multiblock effect families. */
@OnlyIn(Dist.CLIENT)
public final class OmniShaders {
    private static volatile ShaderInstance molecularSpectral;
    private static volatile ShaderInstance matterCondensation;
    private static volatile ShaderInstance singularityCompute;
    private static volatile ShaderInstance singularityLens;
    private static volatile ShaderInstance cosmicLens;
    private static volatile ShaderInstance ghostExposure;
    private static volatile ShaderInstance starOrreryNebula;
    private static volatile ShaderInstance starOrreryNebulaComposite;
    private static volatile ShaderInstance starOrreryWellInk;
    private static volatile ShaderInstance starOrreryColor;
    private static volatile ShaderInstance lanternSimulation;

    private OmniShaders() {
    }

    public static void register(RegisterShadersEvent event) throws IOException {
        // Keep every JSON blend descriptor equal to vanilla's srcalpha/1-srcalpha.
        // In 1.20.1 ShaderInstance.apply shares BlendMode.lastApplied across all draws;
        // opaque/additive descriptors can make the GUI shader overwrite the vignette's
        // multiplicative blend and cover the world with its black texture instead.
        // RenderTypes own depth/glow blending; full-screen passes disable it explicitly.
        event.registerShader(new ShaderInstance(event.getResourceProvider(),
                        MolecularManipulator.id("molecular_spectral"),
                        DefaultVertexFormat.POSITION_COLOR),
                shader -> molecularSpectral = shader);
        event.registerShader(new ShaderInstance(event.getResourceProvider(),
                        MolecularManipulator.id("matter_condensation"),
                        DefaultVertexFormat.POSITION_COLOR),
                shader -> matterCondensation = shader);
        event.registerShader(new ShaderInstance(event.getResourceProvider(),
                        MolecularManipulator.id("singularity_compute"),
                        DefaultVertexFormat.POSITION_COLOR),
                shader -> singularityCompute = shader);
        event.registerShader(new ShaderInstance(event.getResourceProvider(),
                        MolecularManipulator.id("singularity_lens"), DefaultVertexFormat.POSITION),
                shader -> singularityLens = shader);
        event.registerShader(new ShaderInstance(event.getResourceProvider(),
                        MolecularManipulator.id("cosmic_lens"), DefaultVertexFormat.POSITION),
                shader -> cosmicLens = shader);
        event.registerShader(new ShaderInstance(event.getResourceProvider(),
                        MolecularManipulator.id("ghost_exposure"), DefaultVertexFormat.POSITION),
                shader -> ghostExposure = shader);
        event.registerShader(new ShaderInstance(event.getResourceProvider(),
                        MolecularManipulator.id("star_orrery_nebula"), DefaultVertexFormat.POSITION),
                shader -> starOrreryNebula = shader);
        event.registerShader(new ShaderInstance(event.getResourceProvider(),
                        MolecularManipulator.id("star_orrery_nebula_composite"), DefaultVertexFormat.POSITION),
                shader -> starOrreryNebulaComposite = shader);
        event.registerShader(new ShaderInstance(event.getResourceProvider(),
                        MolecularManipulator.id("star_orrery_well_ink"), OmniRenderLayers.WELL_INK_FORMAT),
                shader -> starOrreryWellInk = shader);
        event.registerShader(new ShaderInstance(event.getResourceProvider(),
                        MolecularManipulator.id("star_orrery_color"), DefaultVertexFormat.POSITION_COLOR),
                shader -> starOrreryColor = shader);
        event.registerShader(new ShaderInstance(event.getResourceProvider(),
                        MolecularManipulator.id("lantern_simulation"), DefaultVertexFormat.POSITION),
                shader -> {
                    LanternSimulationRenderer.release();
                    lanternSimulation = shader;
                });
    }

    public static ShaderInstance molecularSpectral() {
        ShaderInstance shader = molecularSpectral;
        return shader != null ? shader : GameRenderer.getPositionColorShader();
    }

    public static ShaderInstance matterCondensation() {
        ShaderInstance shader = matterCondensation;
        return shader != null ? shader : GameRenderer.getPositionColorShader();
    }

    public static ShaderInstance singularityCompute() {
        ShaderInstance shader = singularityCompute;
        return shader != null ? shader : GameRenderer.getPositionColorShader();
    }

    public static ShaderInstance singularityLens() { return singularityLens; }

    public static ShaderInstance cosmicLens() { return cosmicLens; }
    public static ShaderInstance ghostExposure() { return ghostExposure; }
    public static ShaderInstance starOrreryNebula() { return starOrreryNebula; }
    public static ShaderInstance starOrreryNebulaComposite() { return starOrreryNebulaComposite; }
    public static ShaderInstance starOrreryWellInk() { return starOrreryWellInk; }
    public static ShaderInstance starOrreryColor() { return starOrreryColor; }
    public static ShaderInstance lanternSimulation() { return lanternSimulation; }
}
