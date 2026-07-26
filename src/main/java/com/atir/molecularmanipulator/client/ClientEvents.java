package com.atir.molecularmanipulator.client;

import appeng.init.client.InitScreens;
import com.atir.molecularmanipulator.MolecularManipulator;
import com.atir.molecularmanipulator.menu.MolecularCenterMenu;
import com.atir.molecularmanipulator.menu.OmniComputationMenu;
import com.atir.molecularmanipulator.registry.ModContent;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@EventBusSubscriber(modid = MolecularManipulator.MOD_ID, value = Dist.CLIENT,
        bus = EventBusSubscriber.Bus.MOD)
public final class ClientEvents {
    private ClientEvents() {
    }

    @SubscribeEvent
    public static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            InitScreens.register(ModContent.MOLECULAR_MANIPULATOR_MENU.get(), MolecularManipulatorScreen::new,
                    "/screens/molecular_manipulator.json");
            MenuScreens.register(ModContent.MOLECULAR_CENTER_MENU.get(), MolecularCenterScreen::new);
            MenuScreens.register(ModContent.OMNI_COMPUTATION_MENU.get(), OmniComputationScreen::new);
        });
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModContent.MOLECULAR_CENTER_CONTROLLER_BE.get(),
                MolecularCenterRenderer::new);
        event.registerBlockEntityRenderer(ModContent.OMNI_COMPUTATION_CONTROLLER_BE.get(),
                OmniComputationRenderer::new);
    }
}