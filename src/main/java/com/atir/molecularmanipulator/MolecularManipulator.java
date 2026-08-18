package com.atir.molecularmanipulator;

import com.atir.molecularmanipulator.config.ConfigFileMigration;
import com.atir.molecularmanipulator.config.ModConfig;
import com.atir.molecularmanipulator.network.LongCraftingRequestPayload;
import com.atir.molecularmanipulator.network.PatternSearchIndexPayload;
import com.atir.molecularmanipulator.registry.ModContent;
import com.atir.molecularmanipulator.sequence.MatterSequenceRegistry;
import appeng.api.AECapabilities;
import appeng.api.upgrades.Upgrades;
import appeng.core.definitions.AEItems;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import org.slf4j.Logger;

@Mod(MolecularManipulator.MOD_ID)
public final class MolecularManipulator {
    public static final String MOD_ID = "molecularmanipulator";
    public static final Logger LOGGER = LogUtils.getLogger();

    public MolecularManipulator(IEventBus modEventBus, ModContainer modContainer) {
        ModConfig.register(modContainer);
        ModContent.register(modEventBus);
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::registerCapabilities);
        modEventBus.addListener(LongCraftingRequestPayload::register);
        modEventBus.addListener(PatternSearchIndexPayload::register);
        MatterSequenceRegistry.loadOrCreate();
        NeoForge.EVENT_BUS.addListener(this::serverAboutToStart);
    }

    private void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(AECapabilities.IN_WORLD_GRID_NODE_HOST,
                ModContent.MOLECULAR_CENTER_SHELL_BE.get(), (blockEntity, context) -> blockEntity);
        event.registerBlockEntity(AECapabilities.IN_WORLD_GRID_NODE_HOST,
                ModContent.MOLECULAR_MANIPULATOR_BLOCK_ENTITY.get(), (blockEntity, context) -> blockEntity);
        event.registerBlockEntity(AECapabilities.IN_WORLD_GRID_NODE_HOST,
                ModContent.OMNI_COMPUTATION_CONTROLLER_BE.get(), (blockEntity, context) -> blockEntity);
        event.registerBlockEntity(AECapabilities.IN_WORLD_GRID_NODE_HOST,
                ModContent.MATTER_FABRICATION_CONTROLLER_BE.get(), (blockEntity, context) -> blockEntity);
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK,
                ModContent.MATTER_FABRICATION_CONTROLLER_BE.get(),
                (blockEntity, side) -> blockEntity.getExposedItemHandler(side));
    }

    private void serverAboutToStart(ServerAboutToStartEvent event) {
        ConfigFileMigration.migrateServerConfig(event.getServer());
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
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
