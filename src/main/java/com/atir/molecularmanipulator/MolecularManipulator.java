package com.atir.molecularmanipulator;

import com.atir.molecularmanipulator.world.MultiblockChunkLoading;
import com.atir.molecularmanipulator.config.ModConfig;
import com.atir.molecularmanipulator.network.PatternSearchIndexPayload;
import com.atir.molecularmanipulator.network.MatterFabricationUploadPayload;
import com.atir.molecularmanipulator.network.WarpScreenPayload;
import com.atir.molecularmanipulator.registry.ModContent;
import com.atir.molecularmanipulator.integration.useless.UselessBigIntegerApiBridge;
import appeng.api.AECapabilities;
import appeng.api.storage.StorageCells;
import com.atir.molecularmanipulator.ae2.CreativeStarSignalDiskHandler;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.capabilities.Capabilities;
import org.slf4j.Logger;

@Mod(MolecularManipulator.MOD_ID)
public final class MolecularManipulator {
    public static final String MOD_ID = "molecularmanipulator";
    public static final Logger LOGGER = LogUtils.getLogger();

    public MolecularManipulator(IEventBus modEventBus, ModContainer modContainer) {
        ModConfig.register(modContainer);
        ModContent.register(modEventBus);
        com.atir.molecularmanipulator.registry.HighEnergyLabContent.register(modEventBus);
        com.atir.molecularmanipulator.registry.WarpContent.register(modEventBus);
        StorageCells.addCellHandler(CreativeStarSignalDiskHandler.INSTANCE);
        modEventBus.addListener(MultiblockChunkLoading::register);
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::registerCapabilities);
        modEventBus.addListener(PatternSearchIndexPayload::register);
        modEventBus.addListener(MatterFabricationUploadPayload::register);
        modEventBus.addListener(WarpScreenPayload::register);
        modEventBus.addListener(com.atir.molecularmanipulator.network.LanternPayloads::register);
        modEventBus.addListener(com.atir.molecularmanipulator.network.BuildingPayloads::register);
    }

    private void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(AECapabilities.IN_WORLD_GRID_NODE_HOST,
                com.atir.molecularmanipulator.registry.BuildingContent.ASSEMBLER_BE.get(), (blockEntity, context) -> blockEntity);
        event.registerBlockEntity(AECapabilities.IN_WORLD_GRID_NODE_HOST,
                com.atir.molecularmanipulator.registry.HighEnergyLabContent.POWER_INTERFACE_BE.get(),
                (blockEntity, context) -> blockEntity);
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK,
                com.atir.molecularmanipulator.registry.HighEnergyLabContent.POWER_INTERFACE_BE.get(),
                (blockEntity, side) -> blockEntity.getEnergyStorage(side));
        event.registerBlockEntity(AECapabilities.IN_WORLD_GRID_NODE_HOST,
                ModContent.STAR_COMPUTATION_ENGINE_CORE_BE.get(), (blockEntity, context) -> blockEntity);
        event.registerBlock(AECapabilities.IN_WORLD_GRID_NODE_HOST,
                (level, pos, state, blockEntity, side) ->
                        com.atir.molecularmanipulator.starcomputation.StarComputationInteraction.gridHost(level, pos),
                ModContent.STAR_COMPUTATION_ENGINE_CASING.get(), ModContent.STAR_COMPUTATION_OBSERVATION_PORT.get(),
                ModContent.BASE_ENTROPY_ANCHOR.get(), ModContent.QUANTUM_ENTROPY_ANCHOR.get(), ModContent.TERMINAL_ENTROPY_ANCHOR.get());
        event.registerBlockEntity(AECapabilities.IN_WORLD_GRID_NODE_HOST,
                com.atir.molecularmanipulator.registry.SingularityContent.CONTROLLER_BE.get(), (blockEntity, context) -> blockEntity);
        event.registerBlockEntity(AECapabilities.IN_WORLD_GRID_NODE_HOST,
                ModContent.MOLECULAR_CENTER_SHELL_BE.get(), (blockEntity, context) -> blockEntity);
        event.registerBlockEntity(AECapabilities.IN_WORLD_GRID_NODE_HOST,
                ModContent.MOLECULAR_MANIPULATOR_BLOCK_ENTITY.get(), (blockEntity, context) -> blockEntity);
        event.registerBlockEntity(AECapabilities.IN_WORLD_GRID_NODE_HOST,
                ModContent.MOLECULAR_AUTO_CRAFTER_BE.get(), (blockEntity, context) -> blockEntity);
        event.registerBlockEntity(AECapabilities.IN_WORLD_GRID_NODE_HOST,
                ModContent.OMNI_COMPUTATION_CONTROLLER_BE.get(), (blockEntity, context) -> blockEntity);
        event.registerBlockEntity(AECapabilities.IN_WORLD_GRID_NODE_HOST,
                ModContent.MATTER_FABRICATION_CONTROLLER_BE.get(), (blockEntity, context) -> blockEntity);
        event.registerBlockEntity(AECapabilities.IN_WORLD_GRID_NODE_HOST,
                ModContent.MATTER_FABRICATION_PATTERN_ASSEMBLY_BE.get(),
                (blockEntity, context) -> blockEntity);
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK,
                ModContent.MOLECULAR_CENTER_CONTROLLER_BE.get(),
                (blockEntity, side) -> com.atir.molecularmanipulator.building.BuildingTransfer.restoring(blockEntity) ? null : blockEntity.getExternalPatternInventory());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK,
                ModContent.MATTER_FABRICATION_CONTROLLER_BE.get(),
                (blockEntity, side) -> com.atir.molecularmanipulator.building.BuildingTransfer.restoring(blockEntity) ? null : blockEntity.getExposedItemHandler(side));
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK,
                ModContent.MATTER_FABRICATION_PORT_BE.get(),
                (blockEntity, side) -> com.atir.molecularmanipulator.building.BuildingTransfer.restoring(blockEntity) ? null : blockEntity.getExternalItemHandler());
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK,
                ModContent.MATTER_FABRICATION_PORT_BE.get(),
                (blockEntity, side) -> com.atir.molecularmanipulator.building.BuildingTransfer.restoring(blockEntity) ? null : blockEntity.getExternalFluidHandler());
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
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
