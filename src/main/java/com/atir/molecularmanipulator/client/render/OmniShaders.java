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
        MolecularManipulator.LOGGER.info(
                "Registered dedicated multiblock shaders: molecular_spectral, matter_condensation, singularity_compute");
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
}
