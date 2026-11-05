package com.atir.molecularmanipulator.client.render;

import com.atir.molecularmanipulator.MolecularManipulator;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;

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
    private static volatile ShaderInstance armillaryNebula;
    private static volatile ShaderInstance armillaryWellInk;
    private static volatile ShaderInstance armillaryColor;
    private static volatile ShaderInstance lanternSimulation;

    private OmniShaders() {
    }

    public static void register(RegisterShadersEvent event) throws IOException {
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
                        MolecularManipulator.id("armillary_nebula"), DefaultVertexFormat.POSITION),
                shader -> armillaryNebula = shader);
        event.registerShader(new ShaderInstance(event.getResourceProvider(),
                        MolecularManipulator.id("armillary_well_ink"), DefaultVertexFormat.POSITION_TEX_COLOR),
                shader -> armillaryWellInk = shader);
        event.registerShader(new ShaderInstance(event.getResourceProvider(),
                        MolecularManipulator.id("armillary_color"), DefaultVertexFormat.POSITION_COLOR),
                shader -> armillaryColor = shader);
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
    public static ShaderInstance armillaryNebula() { return armillaryNebula; }
    public static ShaderInstance armillaryWellInk() { return armillaryWellInk; }
    public static ShaderInstance armillaryColor() { return armillaryColor; }
    public static ShaderInstance lanternSimulation() { return lanternSimulation; }
}
