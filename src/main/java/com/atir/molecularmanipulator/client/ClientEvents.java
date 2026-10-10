package com.atir.molecularmanipulator.client;

import appeng.init.client.InitScreens;
import com.atir.molecularmanipulator.MolecularManipulator;
import com.atir.molecularmanipulator.client.render.NexusFormedGeometry;
import com.atir.molecularmanipulator.client.render.AutoCrafterGlowGeometry;
import com.atir.molecularmanipulator.client.render.OmniShaders;
import com.atir.molecularmanipulator.client.render.SingularityWhiteHoleRenderer;
import com.atir.molecularmanipulator.client.render.CosmicSingularityRenderer;
import com.atir.molecularmanipulator.client.render.CosmicSingularityPostRenderer;
import com.atir.molecularmanipulator.menu.MolecularManipulatorMenu;
import com.atir.molecularmanipulator.menu.MolecularAutoCrafterMenu;
import com.atir.molecularmanipulator.menu.MolecularCenterMenu;
import com.atir.molecularmanipulator.menu.MatterFabricationMenu;
import com.atir.molecularmanipulator.menu.MatterFabricationPortMenu;
import com.atir.molecularmanipulator.menu.MatterFabricationPatternAssemblyMenu;
import com.atir.molecularmanipulator.menu.OmniComputationMenu;
import com.atir.molecularmanipulator.registry.ModContent;
import com.atir.molecularmanipulator.registry.SingularityContent;
import java.io.IOException;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.client.event.ModelEvent;

@EventBusSubscriber(modid = MolecularManipulator.MOD_ID, value = Dist.CLIENT,
        bus = EventBusSubscriber.Bus.MOD)
public final class ClientEvents {
    private ClientEvents() {
    }

    @SubscribeEvent
    public static void registerScreens(FMLClientSetupEvent event) {
        event.enqueueWork(SingularityRenderCompatibility::initialize);
        event.enqueueWork(SingularityFluidClient::registerRenderLayers);
        event.enqueueWork(() -> {
        InitScreens.register(MolecularManipulatorMenu.TYPE, MolecularManipulatorScreen::new,
                "/screens/molecular_manipulator.json");
        InitScreens.register( ModContent.MOLECULAR_AUTO_CRAFTER_MENU.get(),
                MolecularAutoCrafterScreen::new, "/screens/molecular_auto_crafter.json");
        InitScreens.register( ModContent.MOLECULAR_AUTO_CRAFTER_CONFIG_MENU.get(),
                MolecularAutoCrafterConfigScreen::new, "/screens/molecular_auto_crafter_config.json");
        InitScreens.register( ModContent.MOLECULAR_CENTER_MENU.get(), MolecularCenterScreen::new,
                "/screens/molecular_center.json");
        InitScreens.register(ModContent.OMNI_COMPUTATION_MENU.get(), OmniComputationScreen::new,
                "/screens/omni_computation.json");
        InitScreens.register(ModContent.MATTER_FABRICATION_MENU.get(), MatterFabricationScreen::new,
                "/screens/matter_fabrication.json");
        InitScreens.register(SingularityContent.MENU.get(), SingularityScreen::new, "/screens/event_horizon_singularity_hub.json");
        InitScreens.register(ModContent.MATTER_FABRICATION_PORT_MENU.get(), MatterFabricationPortScreen::new,
                "/screens/matter_fabrication_port.json");
        InitScreens.register(ModContent.MATTER_FABRICATION_PATTERN_ASSEMBLY_MENU.get(),
                MatterFabricationPatternAssemblyScreen::new,
                "/screens/matter_fabrication_pattern_assembly.json");
        });
    }

    @SubscribeEvent
    public static void registerGeometryLoaders(ModelEvent.RegisterGeometryLoaders event) {
        event.register("nexus_formed",
                NexusFormedGeometry.LOADER);
        event.register("auto_crafter_glow",
                AutoCrafterGlowGeometry.LOADER);
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModContent.GHOST_MATTER_BE.get(), GhostMatterRenderer::new);
        event.registerBlockEntityRenderer(SingularityContent.CORE_BE.get(), SingularityCoreRenderer::new);
        event.registerBlockEntityRenderer(ModContent.COSMIC_SINGULARITY_BE.get(), CosmicSingularityRenderer::new);
        event.registerBlockEntityRenderer(SingularityContent.CONTROLLER_BE.get(), SingularityRenderer::new);
        event.registerEntityRenderer(SingularityContent.ASSEMBLY.get(), SingularityAssemblyRenderer::new);
        event.registerBlockEntityRenderer(ModContent.MOLECULAR_CENTER_CONTROLLER_BE.get(),
                MolecularCenterRenderer::new);
        event.registerBlockEntityRenderer(ModContent.OMNI_COMPUTATION_CONTROLLER_BE.get(),
                OmniComputationRenderer::new);
        event.registerBlockEntityRenderer(ModContent.MATTER_FABRICATION_CONTROLLER_BE.get(),
                MatterFabricationRenderer::new);
    }

    @SubscribeEvent
    public static void registerShaders(RegisterShadersEvent event) throws IOException {
        OmniShaders.register(event);
    }

    @SubscribeEvent
    public static void registerReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) resourceManager -> {
            MolecularCenterGhostPreview.onResourceReload();
            OmniComputationGhostPreview.onResourceReload();
            MatterFabricationGhostPreview.onResourceReload();
            SingularityGhostPreview.onResourceReload();
            SingularityAssemblyRenderer.clear();
            SingularityWhiteHoleRenderer.release();
            CosmicSingularityPostRenderer.release();
            com.atir.molecularmanipulator.client.render.GhostMatterExposureRenderer.release();
        });
    }
}
