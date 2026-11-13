package com.atir.molecularmanipulator;

import com.atir.molecularmanipulator.world.MultiblockChunkLoading;
import com.atir.molecularmanipulator.config.ModConfig;
import com.atir.molecularmanipulator.network.BuildingPayloads;
import com.atir.molecularmanipulator.network.DetachedSignalTimePayload;
import com.atir.molecularmanipulator.network.LanternPayloads;
import com.atir.molecularmanipulator.network.MachineRecipeJsonPayload;
import com.atir.molecularmanipulator.network.MatterFabricationUploadPayload;
import com.atir.molecularmanipulator.network.PatternSearchIndexPayload;
import com.atir.molecularmanipulator.network.SingularityCollectionPayload;
import com.atir.molecularmanipulator.network.WarpScreenPayload;
import com.atir.molecularmanipulator.registry.ModContent;
import com.atir.molecularmanipulator.integration.useless.UselessBigIntegerApiBridge;

import appeng.api.storage.StorageCells;
import com.atir.molecularmanipulator.ae2.CreativeStarSignalDiskHandler;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import org.slf4j.Logger;

@Mod(MolecularManipulator.MOD_ID)
public final class MolecularManipulator {
    public static final String MOD_ID = "molecularmanipulator";
    public static final Logger LOGGER = LogUtils.getLogger();

    public MolecularManipulator() {
        IEventBus modEventBus = net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext.get().getModEventBus();
        ModConfig.register();
        ModContent.register(modEventBus);
        com.atir.molecularmanipulator.registry.HighEnergyLabContent.register(modEventBus);
        com.atir.molecularmanipulator.registry.WarpContent.register(modEventBus);
        StorageCells.addCellHandler(CreativeStarSignalDiskHandler.INSTANCE);
        MultiblockChunkLoading.register();
        modEventBus.addListener(this::commonSetup);
        PatternSearchIndexPayload.register();
        SingularityCollectionPayload.register();
        MachineRecipeJsonPayload.register();
        MatterFabricationUploadPayload.register();
        WarpScreenPayload.register();
        LanternPayloads.register();
        BuildingPayloads.register();
        DetachedSignalTimePayload.register();
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            com.atir.molecularmanipulator.terminal.BuildingTerminalLocator.register();
            UselessBigIntegerApiBridge.register();
            validateMixins();
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
