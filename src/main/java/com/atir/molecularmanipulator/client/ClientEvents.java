package com.atir.molecularmanipulator.client;

import appeng.init.client.InitScreens;
import appeng.api.client.AEKeyRendering;
import com.atir.molecularmanipulator.MolecularManipulator;
import com.atir.molecularmanipulator.ae2.StarSignalKey;
import com.atir.molecularmanipulator.ae2.StarSignalKeyType;
import com.atir.molecularmanipulator.client.render.StarOrreryRenderer;
import com.atir.molecularmanipulator.client.render.StarComputationEngineFormedGeometry;
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
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;

@EventBusSubscriber(modid = MolecularManipulator.MOD_ID, value = Dist.CLIENT,
        bus = EventBusSubscriber.Bus.MOD)
public final class ClientEvents {
    private ClientEvents() {
    }

    @SubscribeEvent
    public static void registerResourcePacks(net.neoforged.neoforge.event.AddPackFindersEvent event) {
        com.atir.molecularmanipulator.client.gui.theme.OsqResourcePacks.register(event);
    }

    @SubscribeEvent
    public static void clientSetup(net.neoforged.fml.event.lifecycle.FMLClientSetupEvent event) {
        event.enqueueWork(() -> net.neoforged.fml.ModList.get().getModContainerById(MolecularManipulator.MOD_ID)
                .ifPresent(container -> container.registerExtensionPoint(
                        net.neoforged.neoforge.client.gui.IConfigScreenFactory.class,
                        (minecraft, parent) -> new net.neoforged.neoforge.client.gui.ConfigurationScreen(container, parent))));
        event.enqueueWork(SingularityRenderCompatibility::initialize);
        event.enqueueWork(() -> com.atir.molecularmanipulator.network.WarpScreenPayload.setClientHandler(
                com.atir.molecularmanipulator.client.render.WarpTransitionOverlay::begin));
        event.enqueueWork(() -> {
            com.atir.molecularmanipulator.network.LanternPayloads.setClientHandler(LanternClient::receive);
            net.minecraft.client.renderer.item.ItemProperties.register(
                    com.atir.molecularmanipulator.registry.GreenFireLanternContent.ITEM.get(),
                    MolecularManipulator.id("lit"), (stack, level, entity, seed) ->
                            com.atir.molecularmanipulator.registry.GreenFireLanternContent.isLit(stack) ? 1 : 0);
        });
        event.enqueueWork(SingularityFluidClient::registerRenderLayers);
        event.enqueueWork(() -> AEKeyRendering.register(
                StarSignalKeyType.INSTANCE,
                StarSignalKey.class,
                new StarSignalRenderer()));
    }

    @SubscribeEvent
    public static void registerFluidExtensions(
            net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent event) {
        SingularityFluidClient.registerExtensions(event);
    }

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        InitScreens.register(event, ModContent.BUILDING_ACCESS_TERMINAL_MENU.get(), BuildingTerminalScreen::new,
                "/screens/building_access_terminal.json");
        InitScreens.register(event, MolecularManipulatorMenu.TYPE, MolecularManipulatorScreen::new,
                "/screens/molecular_manipulator.json");
        InitScreens.register(event, ModContent.MOLECULAR_AUTO_CRAFTER_MENU.get(),
                MolecularAutoCrafterScreen::new, "/screens/molecular_auto_crafter.json");
        InitScreens.register(event, ModContent.MOLECULAR_AUTO_CRAFTER_CONFIG_MENU.get(),
                MolecularAutoCrafterConfigScreen::new, "/screens/molecular_auto_crafter_config.json");
        InitScreens.register(event, ModContent.MOLECULAR_CENTER_MENU.get(), MolecularCenterScreen::new,
                "/screens/molecular_center.json");
        InitScreens.register(event, ModContent.OMNI_COMPUTATION_MENU.get(), OmniComputationScreen::new,
                "/screens/omni_computation.json");
        InitScreens.register(event, ModContent.STAR_COMPUTATION_ENGINE_MENU.get(), StarComputationEngineScreen::new,
                "/screens/star_computation_engine.json");
        InitScreens.register(event, ModContent.MATTER_FABRICATION_MENU.get(), MatterFabricationScreen::new,
                "/screens/matter_fabrication.json");
        InitScreens.register(event, SingularityContent.MENU.get(), SingularityScreen::new, "/screens/event_horizon_singularity_hub.json");
        InitScreens.register(event, ModContent.MATTER_FABRICATION_PORT_MENU.get(), MatterFabricationPortScreen::new,
                "/screens/matter_fabrication_port.json");
        InitScreens.register(event, ModContent.MATTER_FABRICATION_PATTERN_ASSEMBLY_MENU.get(),
                MatterFabricationPatternAssemblyScreen::new,
                "/screens/matter_fabrication_pattern_assembly.json");
    }

    @SubscribeEvent
    public static void registerGeometryLoaders(ModelEvent.RegisterGeometryLoaders event) {
        event.register(ResourceLocation.fromNamespaceAndPath(MolecularManipulator.MOD_ID, "star_computation_engine_formed"),
                StarComputationEngineFormedGeometry.LOADER);
        event.register(ResourceLocation.fromNamespaceAndPath(MolecularManipulator.MOD_ID, "nexus_formed"),
                NexusFormedGeometry.LOADER);
        event.register(ResourceLocation.fromNamespaceAndPath(MolecularManipulator.MOD_ID, "auto_crafter_glow"),
                AutoCrafterGlowGeometry.LOADER);
    }

    @SubscribeEvent
    public static void registerAdditionalModels(ModelEvent.RegisterAdditional event) {
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
