package com.atir.molecularmanipulator;

import com.atir.molecularmanipulator.world.MultiblockChunkLoading;
import com.atir.molecularmanipulator.config.ModConfig;
import com.atir.molecularmanipulator.network.PatternSearchIndexPayload;
import com.atir.molecularmanipulator.registry.ModContent;
import com.atir.molecularmanipulator.sequence.MatterSequenceRegistry;
import appeng.api.upgrades.Upgrades;
import appeng.core.definitions.AEItems;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import org.slf4j.Logger;

@Mod(MolecularManipulator.MOD_ID)
public final class MolecularManipulator {
    public static final String MOD_ID = "molecularmanipulator";
    public static final Logger LOGGER = LogUtils.getLogger();

    public MolecularManipulator() {
        IEventBus modEventBus = net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext.get().getModEventBus();
        ModConfig.register();
        ModContent.register(modEventBus);
        MultiblockChunkLoading.register();
        modEventBus.addListener(this::commonSetup);
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
            validateMixinTarget("appeng.crafting.execution.CraftingCpuLogic", classLoader);
            validateMixinTarget("appeng.crafting.CraftingCalculation", classLoader);
            validateMixinTarget("appeng.me.service.CraftingService", classLoader);
            validateMixinTarget("appeng.helpers.patternprovider.PatternProviderLogic", classLoader);
            validateMixinTarget("com.glodblock.github.extendedae.common.me.matrix.CalculatorAssemblerMatrix",
                    classLoader);
            validateMixinTarget("com.glodblock.github.extendedae.common.me.matrix.ClusterAssemblerMatrix",
                    classLoader);
        } catch (ClassNotFoundException exception) {
            throw new IllegalStateException("Required AE2 or ExtendedAE classes are unavailable", exception);
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
