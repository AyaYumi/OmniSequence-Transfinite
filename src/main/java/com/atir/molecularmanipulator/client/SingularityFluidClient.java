package com.atir.molecularmanipulator.client;

import com.atir.molecularmanipulator.MolecularManipulator;
import com.atir.molecularmanipulator.registry.ModFluids;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

public final class SingularityFluidClient {
    public static final ResourceLocation STILL = MolecularManipulator.id("block/singularity_sequence_matter_still");
    public static final ResourceLocation FLOWING = MolecularManipulator.id("block/singularity_sequence_matter_flow");

    public static void registerExtensions(RegisterClientExtensionsEvent event) {
        event.registerFluidType(new IClientFluidTypeExtensions() {
            @Override public ResourceLocation getStillTexture() { return STILL; }
            @Override public ResourceLocation getFlowingTexture() { return FLOWING; }
            @Override public int getTintColor() { return 0xFFFFFFFF; }
        }, ModFluids.SEQUENCE_MATTER_TYPE.get());
    }

    public static void registerRenderLayers() {
        ItemBlockRenderTypes.setRenderLayer(ModFluids.SEQUENCE_MATTER.get(), RenderType.translucent());
        ItemBlockRenderTypes.setRenderLayer(ModFluids.FLOWING_SEQUENCE_MATTER.get(), RenderType.translucent());
    }

    private SingularityFluidClient() {}
}
