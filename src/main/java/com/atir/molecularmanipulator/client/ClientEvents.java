package com.atir.molecularmanipulator.client;

import appeng.init.client.InitScreens;
import com.atir.molecularmanipulator.MolecularManipulator;
import com.atir.molecularmanipulator.menu.MolecularManipulatorMenu;
import com.atir.molecularmanipulator.menu.MolecularCenterMenu;
import com.atir.molecularmanipulator.menu.OmniComputationMenu;
import com.atir.molecularmanipulator.registry.ModContent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@EventBusSubscriber(modid = MolecularManipulator.MOD_ID, value = Dist.CLIENT,
        bus = EventBusSubscriber.Bus.MOD)
public final class ClientEvents {
    private ClientEvents() {
    }

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        InitScreens.register(event, MolecularManipulatorMenu.TYPE, MolecularManipulatorScreen::new,
                "/screens/molecular_manipulator.json");
        event.register(ModContent.MOLECULAR_CENTER_MENU.get(), MolecularCenterScreen::new);
        event.register(ModContent.OMNI_COMPUTATION_MENU.get(), OmniComputationScreen::new);
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModContent.MOLECULAR_CENTER_CONTROLLER_BE.get(),
                MolecularCenterRenderer::new);
        event.registerBlockEntityRenderer(ModContent.OMNI_COMPUTATION_CONTROLLER_BE.get(),
                OmniComputationRenderer::new);
    }
}
