package com.atir.molecularmanipulator.client;

import appeng.init.client.InitScreens;
import com.atir.molecularmanipulator.MolecularManipulator;
import com.atir.molecularmanipulator.client.render.OmniShaders;
import com.atir.molecularmanipulator.menu.MolecularManipulatorMenu;
import com.atir.molecularmanipulator.menu.MolecularCenterMenu;
import com.atir.molecularmanipulator.menu.MatterFabricationMenu;
import com.atir.molecularmanipulator.menu.MatterFabricationPortMenu;
import com.atir.molecularmanipulator.menu.MatterFabricationPatternAssemblyMenu;
import com.atir.molecularmanipulator.menu.OmniComputationMenu;
import com.atir.molecularmanipulator.registry.ModContent;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.client.event.RegisterShadersEvent;

import java.io.IOException;

@EventBusSubscriber(modid = MolecularManipulator.MOD_ID, value = Dist.CLIENT,
        bus = EventBusSubscriber.Bus.MOD)
public final class ClientEvents {
    private ClientEvents() {
    }

    @SubscribeEvent
    public static void registerScreens(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
        InitScreens.register(MolecularManipulatorMenu.TYPE, MolecularManipulatorScreen::new,
                "/screens/molecular_manipulator.json");
        InitScreens.register(ModContent.MOLECULAR_CENTER_MENU.get(), MolecularCenterScreen::new,
                "/screens/molecular_center.json");
        InitScreens.register(ModContent.OMNI_COMPUTATION_MENU.get(), OmniComputationScreen::new,
                "/screens/omni_computation.json");
        InitScreens.register(ModContent.MATTER_FABRICATION_MENU.get(), MatterFabricationScreen::new,
                "/screens/matter_fabrication.json");
        InitScreens.register(ModContent.MATTER_FABRICATION_PORT_MENU.get(), MatterFabricationPortScreen::new,
                "/screens/matter_fabrication_port.json");
        InitScreens.register(ModContent.MATTER_FABRICATION_PATTERN_ASSEMBLY_MENU.get(),
                MatterFabricationPatternAssemblyScreen::new,
                "/screens/matter_fabrication_pattern_assembly.json");
        });
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
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
        });
    }
}
