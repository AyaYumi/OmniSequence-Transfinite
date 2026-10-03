package com.atir.molecularmanipulator.client;

import com.atir.molecularmanipulator.MolecularManipulator;
import com.atir.molecularmanipulator.registry.ModFluids;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;

public final class SingularityFluidClient {
    public static final ResourceLocation STILL = MolecularManipulator.id("block/singularity_sequence_matter_still");
    public static final ResourceLocation FLOWING = MolecularManipulator.id("block/singularity_sequence_matter_flow");

    public static void registerExtensions(java.util.function.Consumer<IClientFluidTypeExtensions> consumer) {
        consumer.accept(new IClientFluidTypeExtensions() {
            @Override public ResourceLocation getStillTexture() { return STILL; }
            @Override public ResourceLocation getFlowingTexture() { return FLOWING; }
            @Override public int getTintColor() { return 0xFFFFFFFF; }
        });
    }

    public static void registerRenderLayers() {
        ItemBlockRenderTypes.setRenderLayer(ModFluids.SEQUENCE_MATTER.get(), RenderType.translucent());
        ItemBlockRenderTypes.setRenderLayer(ModFluids.FLOWING_SEQUENCE_MATTER.get(), RenderType.translucent());
    }

    private SingularityFluidClient() {}
}
