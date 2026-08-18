package com.atir.molecularmanipulator;

import com.atir.molecularmanipulator.config.ModConfig;
import com.atir.molecularmanipulator.network.LongCraftingRequestPayload;
import com.atir.molecularmanipulator.network.PatternSearchIndexPayload;
import com.atir.molecularmanipulator.registry.ModContent;
import com.atir.molecularmanipulator.sequence.MatterSequenceRegistry;
import appeng.api.upgrades.Upgrades;
import appeng.core.definitions.AEItems;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(MolecularManipulator.MOD_ID)
public final class MolecularManipulator {
    public static final String MOD_ID = "molecularmanipulator";
    public static final Logger LOGGER = LogUtils.getLogger();

    public MolecularManipulator() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModConfig.register();
        ModContent.register(modEventBus);
        modEventBus.addListener(this::commonSetup);
        LongCraftingRequestPayload.register();
        PatternSearchIndexPayload.register();
        MatterSequenceRegistry.loadOrCreate();
        MinecraftForge.EVENT_BUS.addListener(this::serverAboutToStart);
    }


    private void serverAboutToStart(ServerAboutToStartEvent event) {
        MatterSequenceRegistry.loadOrCreate();
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            validateMixins();
            Upgrades.add(AEItems.SPEED_CARD, ModContent.MOLECULAR_CENTER_CONTROLLER.get(), 4);
            ModContent.bindBlockEntity();
        });
    }

    private static void validateMixins() {
        var classLoader = MolecularManipulator.class.getClassLoader();
        try {
            validateMixinTarget("appeng.crafting.pattern.AECraftingPattern$Input", classLoader);
            validateMixinTarget("appeng.crafting.execution.CraftingCpuLogic", classLoader);
            validateMixinTarget("appeng.crafting.CraftingCalculation", classLoader);
            validateMixinTarget("appeng.me.service.CraftingService", classLoader);
            validateMixinTarget("appeng.menu.me.crafting.CraftAmountMenu", classLoader);
            validateMixinTarget("appeng.menu.me.crafting.CraftConfirmMenu", classLoader);
            validateMixinTarget("appeng.me.cells.CreativeCellInventory", classLoader);
            validateMixinTarget(
                    "com.glodblock.github.extendedae.common.inventory.InfinityCellInventory",
                    classLoader);
            validateMixinTarget("appeng.helpers.patternprovider.PatternProviderLogic", classLoader);
            validateMixinTarget("com.glodblock.github.extendedae.common.me.matrix.CalculatorAssemblerMatrix",
                    classLoader);
            validateMixinTarget("com.glodblock.github.extendedae.common.me.matrix.ClusterAssemblerMatrix",
                    classLoader);
            if (ModList.get().isLoaded("advanced_ae")) {
                validateMixinTarget("net.pedroksl.advanced_ae.common.logic.AdvCraftingCPULogic",
                        classLoader);
            }
        } catch (ClassNotFoundException exception) {
            throw new IllegalStateException("A required mixin integration class is unavailable", exception);
        }
    }

    private static void validateMixinTarget(String className, ClassLoader classLoader) throws ClassNotFoundException {
        var target = Class.forName(className, true, classLoader);
        target.getDeclaredFields();
        target.getDeclaredMethods();
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MOD_ID, path);
    }
}
