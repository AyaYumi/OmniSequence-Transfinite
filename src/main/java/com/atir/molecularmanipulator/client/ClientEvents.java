package com.atir.molecularmanipulator.client;

import appeng.init.client.InitScreens;
import appeng.api.client.AEKeyRendering;
import com.atir.molecularmanipulator.MolecularManipulator;
import com.atir.molecularmanipulator.ae2.StarSignalKey;
import com.atir.molecularmanipulator.ae2.StarSignalKeyType;
import com.atir.molecularmanipulator.client.render.StarOrreryRenderer;
import com.atir.molecularmanipulator.client.render.NexusFormedGeometry;
import com.atir.molecularmanipulator.client.render.AutoCrafterGlowGeometry;
import com.atir.molecularmanipulator.client.render.OmniShaders;
import com.atir.molecularmanipulator.client.render.SingularityWhiteHoleRenderer;
import com.atir.molecularmanipulator.client.render.CosmicSingularityRenderer;
import com.atir.molecularmanipulator.client.render.CosmicSingularityPostRenderer;
import com.atir.molecularmanipulator.client.render.StarSignalRenderer;
import com.atir.molecularmanipulator.menu.MolecularManipulatorMenu;
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
        event.enqueueWork(() -> com.atir.molecularmanipulator.network.WarpScreenPayload.setClientHandler(
                com.atir.molecularmanipulator.client.render.WarpTransitionOverlay::begin));
        event.enqueueWork(() -> com.atir.molecularmanipulator.network.DetachedSignalTimePayload.setClientHandler(
                DetachedSignalClientTime::receive));
        event.enqueueWork(() -> {
            com.atir.molecularmanipulator.network.LanternPayloads.setClientHandler(LanternClient::receive);
            net.minecraft.client.renderer.item.ItemProperties.register(
                    com.atir.molecularmanipulator.registry.SimulationLanternContent.ITEM.get(),
                    MolecularManipulator.id("lit"), (stack, level, entity, seed) ->
                            com.atir.molecularmanipulator.registry.SimulationLanternContent.isLit(stack) ? 1 : 0);
        });
        event.enqueueWork(SingularityFluidClient::registerRenderLayers);
        event.enqueueWork(() -> AEKeyRendering.register(
                StarSignalKeyType.INSTANCE,
                StarSignalKey.class,
                new StarSignalRenderer()));
        event.enqueueWork(() -> {
            InitScreens.register(ModContent.BUILDING_ACCESS_TERMINAL_MENU.get(), BuildingTerminalScreen::new,
                    "/screens/building_access_terminal.json");
            InitScreens.register(MolecularManipulatorMenu.TYPE, MolecularManipulatorScreen::new,
                    "/screens/molecular_manipulator.json");
            InitScreens.register(ModContent.MOLECULAR_AUTO_CRAFTER_MENU.get(),
                    MolecularAutoCrafterScreen::new, "/screens/molecular_auto_crafter.json");
            InitScreens.register(ModContent.MOLECULAR_AUTO_CRAFTER_CONFIG_MENU.get(),
                    MolecularAutoCrafterConfigScreen::new, "/screens/molecular_auto_crafter_config.json");
            InitScreens.register(ModContent.MOLECULAR_CENTER_MENU.get(), MolecularCenterScreen::new,
                    "/screens/molecular_center.json");
            InitScreens.register(ModContent.OMNI_COMPUTATION_MENU.get(), OmniComputationScreen::new,
                    "/screens/omni_computation.json");
            InitScreens.register(ModContent.STAR_COMPUTATION_ENGINE_MENU.get(), StarComputationEngineScreen::new,
                    "/screens/star_computation_engine.json");
            InitScreens.register(ModContent.MATTER_FABRICATION_MENU.get(), MatterFabricationScreen::new,
                    "/screens/matter_fabrication.json");
            InitScreens.register(SingularityContent.MENU.get(), SingularityScreen::new,
                    "/screens/event_horizon_singularity_hub.json");
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
    public static void registerAdditionalModels(ModelEvent.RegisterAdditional event) {
        event.register(com.atir.molecularmanipulator.client.render.StarSeaCalculationModel.SHELL);
        event.register(com.atir.molecularmanipulator.client.render.StarSeaCalculationModel.GLASS);
        for (String form : StarOrreryRenderer.FORMS) {
            for (String part : StarOrreryRenderer.PARTS) event.register(StarOrreryRenderer.model(form, part));
            for (int ring = 1; ring <= 9; ring++) {
                event.register(StarOrreryRenderer.model(form, "ring_" + ring));
            }
        }
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModContent.STAR_COMPUTATION_ENGINE_CORE_BE.get(), StarOrreryRenderer::new);
        event.registerBlockEntityRenderer(com.atir.molecularmanipulator.registry.StarSeaCalculationContent.BLOCK_ENTITY.get(),
                com.atir.molecularmanipulator.client.render.StarSeaCalculationRenderer::new);
        event.registerBlockEntityRenderer(com.atir.molecularmanipulator.registry.WarpContent.WARP_PAD_BE.get(),
                com.atir.molecularmanipulator.client.render.EndWarpPadRenderer::new);
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
            com.atir.molecularmanipulator.client.gui.theme.OsqSpriteRenderer.clear();
            com.atir.molecularmanipulator.client.gui.theme.OsqFonts.clearInkMetrics();
            com.atir.molecularmanipulator.client.render.EarlyFrameGeometry.clear();
            com.atir.molecularmanipulator.client.render.ParallelEffectGeometry.clear();
            com.atir.molecularmanipulator.client.render.GpuEffectLayers.clear();
            StarOrreryRenderer.clear();
            com.atir.molecularmanipulator.client.render.StarSeaCalculationModel.clear();
            StarSeaCalculationPlacementPreview.clear();
            com.atir.molecularmanipulator.client.render.StarOrreryNebulaRenderer.release();
            MolecularCenterGhostPreview.onResourceReload();
            OmniComputationGhostPreview.onResourceReload();
            MatterFabricationGhostPreview.onResourceReload();
            StarChartClient.onResourceReload();
            SingularityGhostPreview.onResourceReload();
            SingularityAssemblyRenderer.clear();
            SingularityWhiteHoleRenderer.release();
            CosmicSingularityPostRenderer.release();
            com.atir.molecularmanipulator.client.render.LanternSimulationRenderer.release();
            com.atir.molecularmanipulator.client.render.GhostMatterExposureRenderer.release();
        });
    }
}
