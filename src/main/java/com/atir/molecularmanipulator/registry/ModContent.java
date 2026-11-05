package com.atir.molecularmanipulator.registry;

import com.atir.molecularmanipulator.MolecularManipulator;
import com.atir.molecularmanipulator.ae2.StarSignalKey;
import com.atir.molecularmanipulator.ae2.StarSignalKeyType;
import com.atir.molecularmanipulator.block.AssemblerMatrixMolecularCoreBlock;
import com.atir.molecularmanipulator.block.EntropyAnchorBlock;
import com.atir.molecularmanipulator.block.StarMarrowParentRockBlock;
import com.atir.molecularmanipulator.block.StarComputationEngineCasingBlock;
import com.atir.molecularmanipulator.block.StarComputationEngineCoreBlock;
import com.atir.molecularmanipulator.block.StarComputationObservationPortBlock;
import com.atir.molecularmanipulator.block.CosmicSingularityBlock;
import com.atir.molecularmanipulator.block.GravityCrystalBlock;
import com.atir.molecularmanipulator.block.GhostMatterBlock;
import com.atir.molecularmanipulator.block.MolecularManipulatorBlock;
import com.atir.molecularmanipulator.block.MolecularAutoCrafterBlock;
import com.atir.molecularmanipulator.block.MatterFabricationControllerBlock;
import com.atir.molecularmanipulator.block.MatterFabricationPatternAssemblyBlock;
import com.atir.molecularmanipulator.block.MatterFabricationPortBlock;
import com.atir.molecularmanipulator.block.MatterFabricationPortType;
import com.atir.molecularmanipulator.block.MolecularCenterControllerBlock;
import com.atir.molecularmanipulator.block.MolecularCenterPartBlock;
import com.atir.molecularmanipulator.block.MolecularCenterCoreBlock;
import com.atir.molecularmanipulator.block.MolecularCenterGlassBlock;
import com.atir.molecularmanipulator.block.OmniComputationCasingBlock;
import com.atir.molecularmanipulator.block.OmniComputationControllerBlock;
import com.atir.molecularmanipulator.block.OmniComputationGlassBlock;
import com.atir.molecularmanipulator.block.OmniComputationPartBlock;
import com.atir.molecularmanipulator.block.TransfiniteComputeNexusBlock;
import com.atir.molecularmanipulator.blockentity.AssemblerMatrixMolecularCoreBlockEntity;
import com.atir.molecularmanipulator.blockentity.CosmicSingularityBlockEntity;
import com.atir.molecularmanipulator.blockentity.GravityCrystalBlockEntity;
import com.atir.molecularmanipulator.blockentity.GhostMatterBlockEntity;
import com.atir.molecularmanipulator.blockentity.MolecularManipulatorBlockEntity;
import com.atir.molecularmanipulator.blockentity.MatterFabricationBlockEntity;
import com.atir.molecularmanipulator.blockentity.MatterFabricationPatternAssemblyBlockEntity;
import com.atir.molecularmanipulator.blockentity.MatterFabricationPortBlockEntity;
import com.atir.molecularmanipulator.blockentity.MolecularCenterBlockEntity;
import com.atir.molecularmanipulator.blockentity.MolecularAutoCrafterBlockEntity;
import com.atir.molecularmanipulator.blockentity.MolecularCenterCrystalBlockEntity;
import com.atir.molecularmanipulator.blockentity.MolecularCenterShellBlockEntity;
import com.atir.molecularmanipulator.blockentity.OmniComputationCoreBlockEntity;
import com.atir.molecularmanipulator.blockentity.StarComputationEngineCoreBlockEntity;
import com.atir.molecularmanipulator.integration.AdvancedAEIntegration;
import com.atir.molecularmanipulator.item.CreativeStarSignalDiskItem;
import com.atir.molecularmanipulator.item.StarMarrowSeedItem;
import com.atir.molecularmanipulator.item.BuildingTerminalItem;
import com.atir.molecularmanipulator.menu.BuildingTerminalMenu;
import com.atir.molecularmanipulator.item.SingularityCosmicItem;
import com.atir.molecularmanipulator.crafting.MatterFabricationRecipe;
import com.atir.molecularmanipulator.crafting.MatterMachineImportRecipe;
import com.atir.molecularmanipulator.research.MatterResearchRecipe;
import com.atir.molecularmanipulator.menu.MatterFabricationMenu;
import com.atir.molecularmanipulator.menu.MatterFabricationPortMenu;
import com.atir.molecularmanipulator.menu.MatterFabricationPatternAssemblyMenu;
import com.atir.molecularmanipulator.menu.MolecularManipulatorMenu;
import com.atir.molecularmanipulator.menu.MolecularCenterMenu;
import com.atir.molecularmanipulator.menu.MolecularAutoCrafterMenu;
import com.atir.molecularmanipulator.menu.MolecularAutoCrafterConfigMenu;
import com.atir.molecularmanipulator.menu.OmniComputationMenu;
import com.atir.molecularmanipulator.menu.StarComputationEngineMenu;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import appeng.items.storage.BasicStorageCell;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.RegisterEvent;

public final class ModContent {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MolecularManipulator.MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MolecularManipulator.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MolecularManipulator.MOD_ID);
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, MolecularManipulator.MOD_ID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MolecularManipulator.MOD_ID);
    public static final DeferredRegister<RecipeType<?>> RECIPE_TYPES =
            DeferredRegister.create(Registries.RECIPE_TYPE, MolecularManipulator.MOD_ID);
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, MolecularManipulator.MOD_ID);
    public static final DeferredBlock<MolecularManipulatorBlock> MOLECULAR_MANIPULATOR =
            BLOCKS.register("molecular_manipulator", MolecularManipulatorBlock::new);
    public static final DeferredItem<BuildingTerminalItem> BUILDING_ACCESS_TERMINAL = ITEMS.register(
            "building_access_terminal", () -> new BuildingTerminalItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<com.atir.molecularmanipulator.item.FoldedBuildingPackageItem> FOLDED_BUILDING_PACKAGE = ITEMS.register(
            "folded_building_package", () -> new com.atir.molecularmanipulator.item.FoldedBuildingPackageItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<BlockItem> MOLECULAR_MANIPULATOR_ITEM =
            ITEMS.register("molecular_manipulator",
                    () -> new BlockItem(MOLECULAR_MANIPULATOR.get(), new Item.Properties()));
    public static final DeferredBlock<AssemblerMatrixMolecularCoreBlock> ASSEMBLER_MATRIX_MOLECULAR_CORE =
            BLOCKS.register("assembler_matrix_molecular_core", AssemblerMatrixMolecularCoreBlock::new);
    public static final DeferredItem<BlockItem> ASSEMBLER_MATRIX_MOLECULAR_CORE_ITEM =
            ITEMS.register("assembler_matrix_molecular_core",
                    () -> new BlockItem(ASSEMBLER_MATRIX_MOLECULAR_CORE.get(), new Item.Properties()));

    public static final DeferredBlock<MolecularAutoCrafterBlock> MOLECULAR_AUTO_CRAFTER =
            BLOCKS.register("molecular_auto_crafter", MolecularAutoCrafterBlock::new);
    public static final DeferredItem<BlockItem> MOLECULAR_AUTO_CRAFTER_ITEM =
            registerBlockItem("molecular_auto_crafter", MOLECULAR_AUTO_CRAFTER);

    public static final DeferredBlock<MolecularCenterControllerBlock> MOLECULAR_CENTER_CONTROLLER =
            BLOCKS.register("molecular_center_controller", () -> new MolecularCenterControllerBlock(
                    net.minecraft.world.level.block.state.BlockBehaviour.Properties.of()
                            .strength(8.0F, 1200.0F).requiresCorrectToolForDrops()
                            .lightLevel(state -> state.getValue(
                                    net.minecraft.world.level.block.state.properties.BlockStateProperties.POWERED)
                                    ? 15 : 6)));
    public static final DeferredItem<BlockItem> MOLECULAR_CENTER_CONTROLLER_ITEM = ITEMS.register(
            "molecular_center_controller", () -> new BlockItem(MOLECULAR_CENTER_CONTROLLER.get(), new Item.Properties()));
    public static final DeferredBlock<MolecularCenterPartBlock> MOLECULAR_CENTER_CASING = BLOCKS.register(
            "molecular_center_casing", () -> new MolecularCenterPartBlock(
                    net.minecraft.world.level.block.state.BlockBehaviour.Properties.of()
                            .strength(8.0F, 1200.0F).requiresCorrectToolForDrops()
                            .lightLevel(state -> 7), true));
    public static final DeferredItem<BlockItem> MOLECULAR_CENTER_CASING_ITEM = ITEMS.register(
            "molecular_center_casing", () -> new BlockItem(MOLECULAR_CENTER_CASING.get(), new Item.Properties()));
    public static final DeferredBlock<MolecularCenterGlassBlock> MOLECULAR_CENTER_GLASS = BLOCKS.register(
            "molecular_center_glass", () -> new MolecularCenterGlassBlock(
                    net.minecraft.world.level.block.state.BlockBehaviour.Properties.of()
                            .strength(5.0F, 1200.0F).requiresCorrectToolForDrops().noOcclusion()
                            .lightLevel(state -> 12)));
    public static final DeferredItem<BlockItem> MOLECULAR_CENTER_GLASS_ITEM = ITEMS.register(
            "molecular_center_glass", () -> new BlockItem(MOLECULAR_CENTER_GLASS.get(), new Item.Properties()));
    public static final DeferredBlock<MolecularCenterPartBlock> MOLECULAR_CENTER_COIL = BLOCKS.register(
            "molecular_center_coil", () -> new MolecularCenterPartBlock(
                    net.minecraft.world.level.block.state.BlockBehaviour.Properties.of()
                            .strength(8.0F, 1200.0F).requiresCorrectToolForDrops()
                            .noOcclusion().lightLevel(state -> 15), false, true));
    public static final DeferredItem<BlockItem> MOLECULAR_CENTER_COIL_ITEM = ITEMS.register(
            "molecular_center_coil", () -> new BlockItem(MOLECULAR_CENTER_COIL.get(), new Item.Properties()));
    public static final DeferredBlock<MolecularCenterPartBlock> MOLECULAR_CENTER_STABILIZER = BLOCKS.register(
            "molecular_center_stabilizer", () -> new MolecularCenterPartBlock(
                    net.minecraft.world.level.block.state.BlockBehaviour.Properties.of()
                            .strength(8.0F, 1200.0F).requiresCorrectToolForDrops()
                            .lightLevel(state -> 10)));
    public static final DeferredItem<BlockItem> MOLECULAR_CENTER_STABILIZER_ITEM = ITEMS.register(
            "molecular_center_stabilizer", () -> new BlockItem(MOLECULAR_CENTER_STABILIZER.get(), new Item.Properties()));
    public static final DeferredBlock<MolecularCenterCoreBlock> MOLECULAR_CENTER_CORE = BLOCKS.register(
            "molecular_center_core", () -> new MolecularCenterCoreBlock(
                    net.minecraft.world.level.block.state.BlockBehaviour.Properties.of()
                            .strength(8.0F, 1200.0F).requiresCorrectToolForDrops().lightLevel(state -> 15)));
    public static final DeferredItem<BlockItem> MOLECULAR_CENTER_CORE_ITEM = ITEMS.register(
            "molecular_center_core", () -> new BlockItem(MOLECULAR_CENTER_CORE.get(), new Item.Properties()));

    public static final DeferredBlock<MatterFabricationControllerBlock> MATTER_FABRICATION_CONTROLLER =
            BLOCKS.register("matter_fabrication_controller", () -> new MatterFabricationControllerBlock(
                    net.minecraft.world.level.block.state.BlockBehaviour.Properties.of()
                            .strength(10.0F, 1800.0F).requiresCorrectToolForDrops()
                            .lightLevel(state -> 0)));
    public static final DeferredItem<BlockItem> MATTER_FABRICATION_CONTROLLER_ITEM = ITEMS.register(
            "matter_fabrication_controller",
            () -> new BlockItem(MATTER_FABRICATION_CONTROLLER.get(), new Item.Properties()));
    public static final DeferredBlock<MolecularCenterPartBlock> MATTER_FABRICATION_CASING = BLOCKS.register(
            "matter_fabrication_casing", () -> new MolecularCenterPartBlock(
                    net.minecraft.world.level.block.state.BlockBehaviour.Properties.of()
                            .strength(10.0F, 1800.0F).requiresCorrectToolForDrops()
                            .lightLevel(state -> 0), false));
    public static final DeferredItem<BlockItem> MATTER_FABRICATION_CASING_ITEM = registerBlockItem(
            "matter_fabrication_casing", MATTER_FABRICATION_CASING);
    public static final DeferredBlock<MolecularCenterGlassBlock> MATTER_FABRICATION_GLASS = BLOCKS.register(
            "matter_fabrication_glass", () -> new MolecularCenterGlassBlock(
                    net.minecraft.world.level.block.state.BlockBehaviour.Properties.of()
                            .strength(6.0F, 1800.0F).requiresCorrectToolForDrops()
                            .noOcclusion().lightLevel(state -> 0)));
    public static final DeferredItem<BlockItem> MATTER_FABRICATION_GLASS_ITEM = registerBlockItem(
            "matter_fabrication_glass", MATTER_FABRICATION_GLASS);
    public static final DeferredBlock<MolecularCenterPartBlock> MATTER_FABRICATION_COIL = BLOCKS.register(
            "matter_fabrication_coil", () -> new MolecularCenterPartBlock(
                    net.minecraft.world.level.block.state.BlockBehaviour.Properties.of()
                            .strength(10.0F, 1800.0F).requiresCorrectToolForDrops()
                            .noOcclusion().lightLevel(state -> 0), false));
    public static final DeferredItem<BlockItem> MATTER_FABRICATION_COIL_ITEM = registerBlockItem(
            "matter_fabrication_coil", MATTER_FABRICATION_COIL);
    public static final DeferredBlock<MolecularCenterPartBlock> MATTER_FABRICATION_STABILIZER = BLOCKS.register(
            "matter_fabrication_stabilizer", () -> new MolecularCenterPartBlock(
                    net.minecraft.world.level.block.state.BlockBehaviour.Properties.of()
                            .strength(10.0F, 1800.0F).requiresCorrectToolForDrops()
                            .lightLevel(state -> 0), false));
    public static final DeferredItem<BlockItem> MATTER_FABRICATION_STABILIZER_ITEM = registerBlockItem(
            "matter_fabrication_stabilizer", MATTER_FABRICATION_STABILIZER);
    public static final DeferredBlock<MolecularCenterCoreBlock> MATTER_FABRICATION_CORE = BLOCKS.register(
            "matter_fabrication_core", () -> new MolecularCenterCoreBlock(
                    net.minecraft.world.level.block.state.BlockBehaviour.Properties.of()
                            .strength(10.0F, 1800.0F).requiresCorrectToolForDrops()
                            .lightLevel(state -> 0)));
    public static final DeferredItem<BlockItem> MATTER_FABRICATION_CORE_ITEM = registerBlockItem(
            "matter_fabrication_core", MATTER_FABRICATION_CORE);
    public static final DeferredBlock<MatterFabricationPortBlock> MATTER_FABRICATION_ITEM_INPUT =
            registerMatterPort("matter_fabrication_item_input", MatterFabricationPortType.ITEM_INPUT, 0);
    public static final DeferredItem<BlockItem> MATTER_FABRICATION_ITEM_INPUT_ITEM = registerBlockItem(
            "matter_fabrication_item_input", MATTER_FABRICATION_ITEM_INPUT);
    public static final DeferredBlock<MatterFabricationPortBlock> MATTER_FABRICATION_ITEM_OUTPUT =
            registerMatterPort("matter_fabrication_item_output", MatterFabricationPortType.ITEM_OUTPUT, 0);
    public static final DeferredItem<BlockItem> MATTER_FABRICATION_ITEM_OUTPUT_ITEM = registerBlockItem(
            "matter_fabrication_item_output", MATTER_FABRICATION_ITEM_OUTPUT);
    public static final DeferredBlock<MatterFabricationPortBlock> MATTER_FABRICATION_FLUID_INPUT =
            registerMatterPort("matter_fabrication_fluid_input", MatterFabricationPortType.FLUID_INPUT, 0);
    public static final DeferredItem<BlockItem> MATTER_FABRICATION_FLUID_INPUT_ITEM = registerBlockItem(
            "matter_fabrication_fluid_input", MATTER_FABRICATION_FLUID_INPUT);
    public static final DeferredBlock<MatterFabricationPortBlock> MATTER_FABRICATION_FLUID_OUTPUT =
            registerMatterPort("matter_fabrication_fluid_output", MatterFabricationPortType.FLUID_OUTPUT, 0);
    public static final DeferredItem<BlockItem> MATTER_FABRICATION_FLUID_OUTPUT_ITEM = registerBlockItem(
            "matter_fabrication_fluid_output", MATTER_FABRICATION_FLUID_OUTPUT);
    public static final DeferredBlock<MatterFabricationPatternAssemblyBlock> MATTER_FABRICATION_PATTERN_ASSEMBLY =
            BLOCKS.register("matter_fabrication_pattern_assembly", MatterFabricationPatternAssemblyBlock::new);
    public static final DeferredItem<BlockItem> MATTER_FABRICATION_PATTERN_ASSEMBLY_ITEM = registerBlockItem(
            "matter_fabrication_pattern_assembly", MATTER_FABRICATION_PATTERN_ASSEMBLY);

    public static final DeferredBlock<StarComputationEngineCoreBlock> STAR_COMPUTATION_ENGINE_CORE =
            BLOCKS.register("star_computation_engine_core", () -> new StarComputationEngineCoreBlock(
                    net.minecraft.world.level.block.state.BlockBehaviour.Properties.of()
                            .strength(10.0F, 1800.0F).requiresCorrectToolForDrops()
                            .lightLevel(state -> state.getValue(StarComputationEngineCoreBlock.FORMED) ? 15 : 8)));
    public static final DeferredItem<BlockItem> STAR_COMPUTATION_ENGINE_CORE_ITEM = registerBlockItem(
            "star_computation_engine_core", STAR_COMPUTATION_ENGINE_CORE);
    public static final DeferredBlock<StarComputationEngineCasingBlock> STAR_COMPUTATION_ENGINE_CASING =
            BLOCKS.register("star_computation_engine_casing", () -> new StarComputationEngineCasingBlock(
                    net.minecraft.world.level.block.state.BlockBehaviour.Properties.of()
                            .strength(8.0F, 1200.0F).requiresCorrectToolForDrops()
                            .lightLevel(state -> state.getValue(StarComputationEngineCasingBlock.FORMED) ? 10 : 3)));
    public static final DeferredItem<BlockItem> STAR_COMPUTATION_ENGINE_CASING_ITEM = registerBlockItem(
            "star_computation_engine_casing", STAR_COMPUTATION_ENGINE_CASING);
    /** Real foundation blocks retain collision and drops beneath the formed pedestal model. */
    public static final DeferredBlock<com.atir.molecularmanipulator.block.EntropyArmillaryFoundationBlock> ENTROPY_ARMILLARY_PLINTH = BLOCKS.register(
            "entropy_armillary_plinth", () -> new com.atir.molecularmanipulator.block.EntropyArmillaryFoundationBlock(
                    net.minecraft.world.level.block.state.BlockBehaviour.Properties.of()
                            .strength(8.0F, 1200.0F).requiresCorrectToolForDrops()));
    public static final DeferredItem<BlockItem> ENTROPY_ARMILLARY_PLINTH_ITEM = registerBlockItem(
            "entropy_armillary_plinth", ENTROPY_ARMILLARY_PLINTH);
    public static final DeferredBlock<com.atir.molecularmanipulator.block.EntropyArmillaryFoundationBlock> ENTROPY_ARMILLARY_TRIM = BLOCKS.register(
            "entropy_armillary_trim", () -> new com.atir.molecularmanipulator.block.EntropyArmillaryFoundationBlock(
                    net.minecraft.world.level.block.state.BlockBehaviour.Properties.of()
                            .strength(8.0F, 1200.0F).requiresCorrectToolForDrops().lightLevel(state -> 5)));
    public static final DeferredItem<BlockItem> ENTROPY_ARMILLARY_TRIM_ITEM = registerBlockItem(
            "entropy_armillary_trim", ENTROPY_ARMILLARY_TRIM);
    public static final DeferredBlock<StarComputationObservationPortBlock> STAR_COMPUTATION_OBSERVATION_PORT =
            BLOCKS.register("star_computation_observation_port", () -> new StarComputationObservationPortBlock(
                    net.minecraft.world.level.block.state.BlockBehaviour.Properties.of()
                            .strength(5.0F, 1200.0F).requiresCorrectToolForDrops()
                            .noOcclusion()
                            .lightLevel(state -> state.getValue(StarComputationObservationPortBlock.FORMED) ? 13 : 8)));
    public static final DeferredItem<BlockItem> STAR_COMPUTATION_OBSERVATION_PORT_ITEM = registerBlockItem(
            "star_computation_observation_port", STAR_COMPUTATION_OBSERVATION_PORT);
    public static final DeferredBlock<EntropyAnchorBlock> BASE_ENTROPY_ANCHOR =
            BLOCKS.register("base_entropy_anchor", () -> new EntropyAnchorBlock(
                    net.minecraft.world.level.block.state.BlockBehaviour.Properties.of()
                            .strength(8.0F, 1200.0F).requiresCorrectToolForDrops()
                            .lightLevel(state -> 7)));
    public static final DeferredItem<BlockItem> BASE_ENTROPY_ANCHOR_ITEM = registerBlockItem(
            "base_entropy_anchor", BASE_ENTROPY_ANCHOR);
    public static final DeferredBlock<EntropyAnchorBlock> QUANTUM_ENTROPY_ANCHOR =
            BLOCKS.register("quantum_entropy_anchor", () -> new EntropyAnchorBlock(
                    net.minecraft.world.level.block.state.BlockBehaviour.Properties.of()
                            .strength(9.0F, 1500.0F).requiresCorrectToolForDrops()
                            .lightLevel(state -> 11)));
    public static final DeferredItem<BlockItem> QUANTUM_ENTROPY_ANCHOR_ITEM = registerBlockItem(
            "quantum_entropy_anchor", QUANTUM_ENTROPY_ANCHOR);
    public static final DeferredBlock<EntropyAnchorBlock> TERMINAL_ENTROPY_ANCHOR =
            BLOCKS.register("terminal_entropy_anchor", () -> new EntropyAnchorBlock(
                    net.minecraft.world.level.block.state.BlockBehaviour.Properties.of()
                            .strength(10.0F, 1800.0F).requiresCorrectToolForDrops()
                            .lightLevel(state -> 15)));
    public static final DeferredItem<BlockItem> TERMINAL_ENTROPY_ANCHOR_ITEM = registerBlockItem(
            "terminal_entropy_anchor", TERMINAL_ENTROPY_ANCHOR);
    public static final DeferredItem<Item> STAR_MARROW_ALLOY_INGOT = ITEMS.register(
            "star_marrow_alloy_ingot", () -> new Item(new Item.Properties()));
    public static final DeferredItem<Item> STAR_MARROW_CRYSTAL = ITEMS.register(
            "star_marrow_crystal", () -> new Item(new Item.Properties()));
    public static final DeferredItem<Item> STAR_MARROW_CRYSTAL_DUST = ITEMS.register(
            "star_marrow_crystal_dust", () -> new Item(new Item.Properties()));
    public static final DeferredItem<Item> STAR_CHART_PROCESSOR = ITEMS.register(
            "star_chart_processor", () -> new Item(new Item.Properties()));
    public static final DeferredItem<Item> STAR_CHART_CIRCUIT_BOARD = ITEMS.register(
            "star_chart_circuit_board", () -> new Item(new Item.Properties()));
    public static final DeferredItem<Item> STAR_SIGNAL_STORAGE_COMPONENT = ITEMS.register(
            "star_signal_storage_component", () -> new Item(new Item.Properties()));
    public static final DeferredItem<Item> PRIMORDIAL_CRYSTAL_CORE = ITEMS.register(
            "primordial_crystal_core", () -> new Item(new Item.Properties()));
    public static final DeferredItem<Item> STAR_MARROW_SEED = ITEMS.register(
            "star_marrow_seed", () -> new StarMarrowSeedItem(new Item.Properties()));
    public static final DeferredItem<BasicStorageCell> STAR_SIGNAL_STORAGE_CELL = ITEMS.register(
            "star_signal_storage_cell",
            // AE2 takes capacity in KiB: 256 * 1024 KiB = 256 MiB.
            () -> new BasicStorageCell(new Item.Properties().stacksTo(1), 0.5, 256 * 1024, 1, 1,
                    StarSignalKeyType.INSTANCE));
    public static final DeferredItem<Item> CREATIVE_STAR_SIGNAL_DISK = ITEMS.register(
            "creative_star_signal_disk",
            () -> new CreativeStarSignalDiskItem(new Item.Properties().stacksTo(1)));
    public static final DeferredBlock<AmethystClusterBlock> STAR_MARROW_CRYSTAL_BUD_SMALL =
            registerEntropyQuartzCluster("star_marrow_crystal_bud_small", 0.35F, 3.0F, 4.0F, 4);
    public static final DeferredItem<BlockItem> STAR_MARROW_CRYSTAL_BUD_SMALL_ITEM =
            registerBlockItem("star_marrow_crystal_bud_small", STAR_MARROW_CRYSTAL_BUD_SMALL);
    public static final DeferredBlock<AmethystClusterBlock> STAR_MARROW_CRYSTAL_BUD_MEDIUM =
            registerEntropyQuartzCluster("star_marrow_crystal_bud_medium", 0.45F, 4.0F, 3.0F, 6);
    public static final DeferredItem<BlockItem> STAR_MARROW_CRYSTAL_BUD_MEDIUM_ITEM =
            registerBlockItem("star_marrow_crystal_bud_medium", STAR_MARROW_CRYSTAL_BUD_MEDIUM);
    public static final DeferredBlock<AmethystClusterBlock> STAR_MARROW_CRYSTAL_BUD_LARGE =
            registerEntropyQuartzCluster("star_marrow_crystal_bud_large", 0.55F, 5.0F, 3.0F, 8);
    public static final DeferredItem<BlockItem> STAR_MARROW_CRYSTAL_BUD_LARGE_ITEM =
            registerBlockItem("star_marrow_crystal_bud_large", STAR_MARROW_CRYSTAL_BUD_LARGE);
    public static final DeferredBlock<AmethystClusterBlock> STAR_MARROW_CRYSTAL_CLUSTER =
            registerEntropyQuartzCluster("star_marrow_crystal_cluster", 0.7F, 7.0F, 3.0F, 12);
    public static final DeferredItem<BlockItem> STAR_MARROW_CRYSTAL_CLUSTER_ITEM =
            registerBlockItem("star_marrow_crystal_cluster", STAR_MARROW_CRYSTAL_CLUSTER);
    public static final DeferredBlock<StarMarrowParentRockBlock> HEAVILY_DAMAGED_STAR_MARROW_PARENT_ROCK =
            BLOCKS.register("heavily_damaged_star_marrow_parent_rock", () -> new StarMarrowParentRockBlock(
                    parentRockProperties(1), null, 0));
    public static final DeferredItem<BlockItem> HEAVILY_DAMAGED_STAR_MARROW_PARENT_ROCK_ITEM =
            registerBlockItem("heavily_damaged_star_marrow_parent_rock", HEAVILY_DAMAGED_STAR_MARROW_PARENT_ROCK);
    public static final DeferredBlock<StarMarrowParentRockBlock> DAMAGED_STAR_MARROW_PARENT_ROCK =
            BLOCKS.register("damaged_star_marrow_parent_rock", () -> new StarMarrowParentRockBlock(
                    parentRockProperties(2), () -> HEAVILY_DAMAGED_STAR_MARROW_PARENT_ROCK.get(), 12));
    public static final DeferredItem<BlockItem> DAMAGED_STAR_MARROW_PARENT_ROCK_ITEM =
            registerBlockItem("damaged_star_marrow_parent_rock", DAMAGED_STAR_MARROW_PARENT_ROCK);
    public static final DeferredBlock<StarMarrowParentRockBlock> FLAWED_STAR_MARROW_PARENT_ROCK =
            BLOCKS.register("flawed_star_marrow_parent_rock", () -> new StarMarrowParentRockBlock(
                    parentRockProperties(3), () -> DAMAGED_STAR_MARROW_PARENT_ROCK.get(), 12));
    public static final DeferredItem<BlockItem> FLAWED_STAR_MARROW_PARENT_ROCK_ITEM =
            registerBlockItem("flawed_star_marrow_parent_rock", FLAWED_STAR_MARROW_PARENT_ROCK);
    public static final DeferredBlock<StarMarrowParentRockBlock> FLAWLESS_STAR_MARROW_PARENT_ROCK =
            BLOCKS.register("flawless_star_marrow_parent_rock", () -> new StarMarrowParentRockBlock(
                    // Flawless parent rock is permanent; only damaged stages decay.
                    parentRockProperties(4), null, 0));
    public static final DeferredItem<BlockItem> FLAWLESS_STAR_MARROW_PARENT_ROCK_ITEM =
            registerBlockItem("flawless_star_marrow_parent_rock", FLAWLESS_STAR_MARROW_PARENT_ROCK);

    public static final DeferredBlock<TransfiniteComputeNexusBlock> TRANSFINITE_COMPUTE_NEXUS =
            BLOCKS.register("transfinite_compute_nexus", TransfiniteComputeNexusBlock::new);
    public static final DeferredItem<BlockItem> TRANSFINITE_COMPUTE_NEXUS_ITEM =
            registerBlockItem("transfinite_compute_nexus", TRANSFINITE_COMPUTE_NEXUS);

    public static final DeferredBlock<OmniComputationControllerBlock> OMNI_COMPUTATION_CONTROLLER =
            BLOCKS.register("omni_computation_controller", () -> new OmniComputationControllerBlock(
                    net.minecraft.world.level.block.state.BlockBehaviour.Properties.of()
                            .strength(12.0F, 2400.0F).requiresCorrectToolForDrops()
                            .lightLevel(state -> 15)));
    public static final DeferredItem<BlockItem> OMNI_COMPUTATION_CONTROLLER_ITEM = ITEMS.register(
            "omni_computation_controller",
            () -> new BlockItem(OMNI_COMPUTATION_CONTROLLER.get(), new Item.Properties()));
    public static final DeferredBlock<OmniComputationCasingBlock> OMNI_COMPUTATION_CASING =
            BLOCKS.register("omni_computation_casing", () -> new OmniComputationCasingBlock(
                    net.minecraft.world.level.block.state.BlockBehaviour.Properties.of()
                            .strength(10.0F, 2400.0F).requiresCorrectToolForDrops()
                            .noOcclusion().lightLevel(state -> 7)));
    public static final DeferredItem<BlockItem> OMNI_COMPUTATION_CASING_ITEM =
            registerBlockItem("omni_computation_casing", OMNI_COMPUTATION_CASING);
    public static final DeferredBlock<OmniComputationGlassBlock> OMNI_COMPUTATION_GLASS =
            BLOCKS.register("omni_computation_glass", () -> new OmniComputationGlassBlock(
                    net.minecraft.world.level.block.state.BlockBehaviour.Properties.of()
                            .strength(8.0F, 2400.0F).requiresCorrectToolForDrops()
                            .noOcclusion().lightLevel(state -> 13)));
    public static final DeferredItem<BlockItem> OMNI_COMPUTATION_GLASS_ITEM =
            registerBlockItem("omni_computation_glass", OMNI_COMPUTATION_GLASS);
    public static final DeferredBlock<OmniComputationPartBlock> INFINITE_PARALLEL_MATRIX =
            registerOmniPart("infinite_parallel_matrix", 15);
    public static final DeferredItem<BlockItem> INFINITE_PARALLEL_MATRIX_ITEM =
            registerBlockItem("infinite_parallel_matrix", INFINITE_PARALLEL_MATRIX);
    public static final DeferredBlock<OmniComputationPartBlock> INFINITE_CRAFTING_STORAGE =
            registerOmniPart("infinite_crafting_storage", 15);
    public static final DeferredItem<BlockItem> INFINITE_CRAFTING_STORAGE_ITEM =
            registerBlockItem("infinite_crafting_storage", INFINITE_CRAFTING_STORAGE);
    public static final DeferredBlock<OmniComputationPartBlock> UNIVERSAL_PATTERN_MATRIX =
            registerTranslucentOmniPart("universal_pattern_matrix", 12);
    public static final DeferredItem<BlockItem> UNIVERSAL_PATTERN_MATRIX_ITEM =
            registerBlockItem("universal_pattern_matrix", UNIVERSAL_PATTERN_MATRIX);
    public static final DeferredBlock<OmniComputationPartBlock> COMPUTATION_DATA_ENTANGLER =
            registerOmniPart("computation_data_entangler", 14);
    public static final DeferredItem<BlockItem> COMPUTATION_DATA_ENTANGLER_ITEM =
            registerBlockItem("computation_data_entangler", COMPUTATION_DATA_ENTANGLER);
    public static final DeferredBlock<OmniComputationPartBlock> COMPUTATION_ENERGY_STABILIZER =
            registerTranslucentOmniPart("computation_energy_stabilizer", 13);
    public static final DeferredItem<BlockItem> COMPUTATION_ENERGY_STABILIZER_ITEM =
            registerBlockItem("computation_energy_stabilizer", COMPUTATION_ENERGY_STABILIZER);
    public static final DeferredBlock<OmniComputationPartBlock> COMPUTATION_OUTPUT_NODE =
            registerOmniPart("computation_output_node", 12);
    public static final DeferredItem<BlockItem> COMPUTATION_OUTPUT_NODE_ITEM =
            registerBlockItem("computation_output_node", COMPUTATION_OUTPUT_NODE);
    public static final DeferredBlock<OmniComputationPartBlock> COMPUTATION_CRYSTAL_PYLON =
            registerTranslucentOmniPart("computation_crystal_pylon", 15);
    public static final DeferredItem<BlockItem> COMPUTATION_CRYSTAL_PYLON_ITEM =
            registerBlockItem("computation_crystal_pylon", COMPUTATION_CRYSTAL_PYLON);

    public static final DeferredBlock<CosmicSingularityBlock> BLACK_HOLE_BLOCK = BLOCKS.register("black_hole",
            () -> new CosmicSingularityBlock(cosmicProperties(4), CosmicSingularityBlock.Kind.BLACK_HOLE));
    public static final DeferredBlock<CosmicSingularityBlock> WHITE_HOLE_BLOCK = BLOCKS.register("white_hole",
            () -> new CosmicSingularityBlock(cosmicProperties(15), CosmicSingularityBlock.Kind.WHITE_HOLE));
    public static final DeferredItem<BlockItem> BLACK_HOLE = ITEMS.register("black_hole",
            () -> new SingularityCosmicItem(BLACK_HOLE_BLOCK.get(), "black_hole",
                    net.minecraft.ChatFormatting.DARK_GRAY, new Item.Properties()));
    public static final DeferredItem<BlockItem> WHITE_HOLE = ITEMS.register("white_hole",
            () -> new SingularityCosmicItem(WHITE_HOLE_BLOCK.get(), "white_hole",
                    net.minecraft.ChatFormatting.AQUA, new Item.Properties()));
    public static final DeferredItem<Item> MINIATURE_SUPERNOVA = ITEMS.register("miniature_supernova",
            () -> new Item(new Item.Properties()));
    public static final DeferredBlock<GravityCrystalBlock> GRAVITY_CRYSTAL_BLOCK = BLOCKS.register("gravity_crystal",
            () -> new GravityCrystalBlock(net.minecraft.world.level.block.state.BlockBehaviour.Properties.of()
                    .strength(2.5F, 6F).requiresCorrectToolForDrops().noOcclusion()
                    .lightLevel(state -> state.getValue(GravityCrystalBlock.POWERED) ? 2 : 10)
                    .sound(net.minecraft.world.level.block.SoundType.AMETHYST)));
    public static final DeferredItem<BlockItem> GRAVITY_CRYSTAL = ITEMS.register("gravity_crystal",
            () -> new SingularityCosmicItem(GRAVITY_CRYSTAL_BLOCK.get(), "gravity_crystal",
                    net.minecraft.ChatFormatting.LIGHT_PURPLE, new Item.Properties()));
    public static final DeferredBlock<GhostMatterBlock> GHOST_MATTER_BLOCK = BLOCKS.register("ghost_matter",
            () -> new GhostMatterBlock(net.minecraft.world.level.block.state.BlockBehaviour.Properties.of()
                    .strength(-1F, 3600000F).noOcclusion().noCollission()
                    .lightLevel(state -> state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED) ? 0 : 7)
                    .sound(net.minecraft.world.level.block.SoundType.AMETHYST)));
    public static final DeferredItem<BlockItem> GHOST_MATTER = ITEMS.register("ghost_matter",
            () -> new SingularityCosmicItem(GHOST_MATTER_BLOCK.get(), "ghost_matter",
                    net.minecraft.ChatFormatting.GREEN, new Item.Properties()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GravityCrystalBlockEntity>> GRAVITY_CRYSTAL_BE =
            BLOCK_ENTITIES.register("gravity_crystal", () -> BlockEntityType.Builder.of(
                    GravityCrystalBlockEntity::new, GRAVITY_CRYSTAL_BLOCK.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GhostMatterBlockEntity>> GHOST_MATTER_BE =
            BLOCK_ENTITIES.register("ghost_matter", () -> BlockEntityType.Builder.of(
                    GhostMatterBlockEntity::new, GHOST_MATTER_BLOCK.get()).build(null));
    static {
        ITEMS.addAlias(MolecularManipulator.id("singularity_sequence_matter"),
                ResourceLocation.withDefaultNamespace("air"));
    }
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CosmicSingularityBlockEntity>> COSMIC_SINGULARITY_BE =
            BLOCK_ENTITIES.register("cosmic_singularity", () -> BlockEntityType.Builder.of(
                    CosmicSingularityBlockEntity::new, BLACK_HOLE_BLOCK.get(), WHITE_HOLE_BLOCK.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MolecularManipulatorBlockEntity>>
            MOLECULAR_MANIPULATOR_BLOCK_ENTITY = BLOCK_ENTITIES.register("molecular_manipulator",
                    () -> BlockEntityType.Builder.of(MolecularManipulatorBlockEntity::new,
                            MOLECULAR_MANIPULATOR.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AssemblerMatrixMolecularCoreBlockEntity>>
            ASSEMBLER_MATRIX_MOLECULAR_CORE_BLOCK_ENTITY = BLOCK_ENTITIES.register("assembler_matrix_molecular_core",
                    () -> BlockEntityType.Builder.of(AssemblerMatrixMolecularCoreBlockEntity::new,
                            ASSEMBLER_MATRIX_MOLECULAR_CORE.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MolecularCenterBlockEntity>>
            MOLECULAR_CENTER_CONTROLLER_BE = BLOCK_ENTITIES.register("molecular_center_controller",
                    () -> BlockEntityType.Builder.of(MolecularCenterBlockEntity::new,
                            MOLECULAR_CENTER_CONTROLLER.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MolecularCenterShellBlockEntity>>
            MOLECULAR_CENTER_SHELL_BE = BLOCK_ENTITIES.register("molecular_center_shell",
                    () -> BlockEntityType.Builder.of(MolecularCenterShellBlockEntity::new,
                            MOLECULAR_CENTER_CASING.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MolecularCenterCrystalBlockEntity>>
            MOLECULAR_CENTER_CRYSTAL_BE = BLOCK_ENTITIES.register("molecular_center_crystal",
                    () -> BlockEntityType.Builder.of(MolecularCenterCrystalBlockEntity::new,
                            MOLECULAR_CENTER_COIL.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<OmniComputationCoreBlockEntity>>
            OMNI_COMPUTATION_CONTROLLER_BE = BLOCK_ENTITIES.register("omni_computation_controller",
                    () -> BlockEntityType.Builder.of(OmniComputationCoreBlockEntity::new,
                            OMNI_COMPUTATION_CONTROLLER.get(), TRANSFINITE_COMPUTE_NEXUS.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<StarComputationEngineCoreBlockEntity>>
            STAR_COMPUTATION_ENGINE_CORE_BE = BLOCK_ENTITIES.register("star_computation_engine_core",
                    () -> BlockEntityType.Builder.of(StarComputationEngineCoreBlockEntity::new,
                            STAR_COMPUTATION_ENGINE_CORE.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MolecularAutoCrafterBlockEntity>>
            MOLECULAR_AUTO_CRAFTER_BE = BLOCK_ENTITIES.register("molecular_auto_crafter",
                    () -> BlockEntityType.Builder.of(MolecularAutoCrafterBlockEntity::new,
                            MOLECULAR_AUTO_CRAFTER.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MatterFabricationBlockEntity>>
            MATTER_FABRICATION_CONTROLLER_BE = BLOCK_ENTITIES.register("matter_fabrication_controller",
                    () -> BlockEntityType.Builder.of(MatterFabricationBlockEntity::new,
                            MATTER_FABRICATION_CONTROLLER.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MatterFabricationPortBlockEntity>>
            MATTER_FABRICATION_PORT_BE = BLOCK_ENTITIES.register("matter_fabrication_port",
                    () -> BlockEntityType.Builder.of(MatterFabricationPortBlockEntity::new,
                            MATTER_FABRICATION_ITEM_INPUT.get(), MATTER_FABRICATION_ITEM_OUTPUT.get(),
                            MATTER_FABRICATION_FLUID_INPUT.get(), MATTER_FABRICATION_FLUID_OUTPUT.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MatterFabricationPatternAssemblyBlockEntity>>
            MATTER_FABRICATION_PATTERN_ASSEMBLY_BE = BLOCK_ENTITIES.register(
                    "matter_fabrication_pattern_assembly",
                    () -> BlockEntityType.Builder.of(MatterFabricationPatternAssemblyBlockEntity::new,
                            MATTER_FABRICATION_PATTERN_ASSEMBLY.get()).build(null));
    public static final DeferredHolder<MenuType<?>, MenuType<BuildingTerminalMenu>> BUILDING_ACCESS_TERMINAL_MENU =
            MENUS.register("building_access_terminal", () -> BuildingTerminalMenu.TYPE);
    public static final DeferredHolder<MenuType<?>, MenuType<MolecularManipulatorMenu>> MOLECULAR_MANIPULATOR_MENU =
            MENUS.register("molecular_manipulator", () -> MolecularManipulatorMenu.TYPE);
    public static final DeferredHolder<MenuType<?>, MenuType<MolecularCenterMenu>> MOLECULAR_CENTER_MENU =
            MENUS.register("molecular_center", () -> MolecularCenterMenu.TYPE);
    public static final DeferredHolder<MenuType<?>, MenuType<OmniComputationMenu>> OMNI_COMPUTATION_MENU =
            MENUS.register("omni_computation", () -> OmniComputationMenu.TYPE);
    public static final DeferredHolder<MenuType<?>, MenuType<StarComputationEngineMenu>> STAR_COMPUTATION_ENGINE_MENU =
            MENUS.register("star_computation_engine", () -> StarComputationEngineMenu.TYPE);
    public static final DeferredHolder<MenuType<?>, MenuType<MolecularAutoCrafterMenu>> MOLECULAR_AUTO_CRAFTER_MENU =
            MENUS.register("molecular_auto_crafter", () -> MolecularAutoCrafterMenu.TYPE);
    public static final DeferredHolder<MenuType<?>, MenuType<MolecularAutoCrafterConfigMenu>> MOLECULAR_AUTO_CRAFTER_CONFIG_MENU =
            MENUS.register("molecular_auto_crafter_config", () -> MolecularAutoCrafterConfigMenu.TYPE);
    public static final DeferredHolder<MenuType<?>, MenuType<MatterFabricationMenu>> MATTER_FABRICATION_MENU =
            MENUS.register("matter_fabrication", () -> MatterFabricationMenu.TYPE);
    public static final DeferredHolder<MenuType<?>, MenuType<MatterFabricationPortMenu>>
            MATTER_FABRICATION_PORT_MENU = MENUS.register(
                    "matter_fabrication_port", () -> MatterFabricationPortMenu.TYPE);
    public static final DeferredHolder<MenuType<?>, MenuType<MatterFabricationPatternAssemblyMenu>>
            MATTER_FABRICATION_PATTERN_ASSEMBLY_MENU = MENUS.register(
                    "matter_fabrication_pattern_assembly",
                    () -> MatterFabricationPatternAssemblyMenu.TYPE);
    public static final DeferredHolder<RecipeType<?>, RecipeType<MatterFabricationRecipe>>
            MATTER_FABRICATION_RECIPE_TYPE = RECIPE_TYPES.register("matter_fabrication",
                    () -> RecipeType.simple(MolecularManipulator.id("matter_fabrication")));
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<MatterFabricationRecipe>>
            MATTER_FABRICATION_RECIPE_SERIALIZER = RECIPE_SERIALIZERS.register("matter_fabrication",
                    MatterFabricationRecipe.Serializer::new);

    public static final DeferredHolder<RecipeType<?>, RecipeType<MatterResearchRecipe>> MATTER_RESEARCH_RECIPE_TYPE =
            RECIPE_TYPES.register("matter_research", () -> RecipeType.simple(MolecularManipulator.id("matter_research")));
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<MatterResearchRecipe>> MATTER_RESEARCH_RECIPE_SERIALIZER =
            RECIPE_SERIALIZERS.register("matter_research", MatterResearchRecipe.Serializer::new);

    public static final DeferredHolder<RecipeType<?>, RecipeType<MatterMachineImportRecipe>> MATTER_MACHINE_IMPORT_RECIPE_TYPE =
            RECIPE_TYPES.register("matter_machine_import", () -> RecipeType.simple(MolecularManipulator.id("matter_machine_import")));
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<MatterMachineImportRecipe>> MATTER_MACHINE_IMPORT_RECIPE_SERIALIZER =
            RECIPE_SERIALIZERS.register("matter_machine_import", MatterMachineImportRecipe.Serializer::new);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN_TAB = CREATIVE_TABS.register(
            "main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.molecularmanipulator"))
                    .icon(() -> MOLECULAR_MANIPULATOR_ITEM.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        output.accept(BUILDING_ACCESS_TERMINAL.get());
                        output.accept(FOLDED_BUILDING_PACKAGE.get());
                        output.accept(BuildingContent.CALIBRATOR.get());
                        output.accept(BuildingContent.ASSEMBLER_ITEM.get());
                        for (var building : com.atir.molecularmanipulator.building.BuildingType.values())
                            output.accept(com.atir.molecularmanipulator.building.BuildingPackage.fresh(building));
                        output.accept(MOLECULAR_MANIPULATOR_ITEM.get());
                        output.accept(ASSEMBLER_MATRIX_MOLECULAR_CORE_ITEM.get());
                        output.accept(MOLECULAR_AUTO_CRAFTER_ITEM.get());
                        output.accept(MOLECULAR_CENTER_CONTROLLER_ITEM.get());
                        output.accept(MOLECULAR_CENTER_CASING_ITEM.get());
                        output.accept(MOLECULAR_CENTER_GLASS_ITEM.get());
                        output.accept(MOLECULAR_CENTER_COIL_ITEM.get());
                        output.accept(MOLECULAR_CENTER_STABILIZER_ITEM.get());
                        output.accept(MOLECULAR_CENTER_CORE_ITEM.get());
                        output.accept(MATTER_FABRICATION_CONTROLLER_ITEM.get());
                        output.accept(MATTER_FABRICATION_CASING_ITEM.get());
                        output.accept(MATTER_FABRICATION_GLASS_ITEM.get());
                        output.accept(MATTER_FABRICATION_COIL_ITEM.get());
                        output.accept(MATTER_FABRICATION_STABILIZER_ITEM.get());
                        output.accept(MATTER_FABRICATION_CORE_ITEM.get());
                        output.accept(MATTER_FABRICATION_ITEM_INPUT_ITEM.get());
                        output.accept(MATTER_FABRICATION_ITEM_OUTPUT_ITEM.get());
                        output.accept(MATTER_FABRICATION_FLUID_INPUT_ITEM.get());
                        output.accept(MATTER_FABRICATION_FLUID_OUTPUT_ITEM.get());
                        output.accept(MATTER_FABRICATION_PATTERN_ASSEMBLY_ITEM.get());
                        output.accept(STAR_COMPUTATION_ENGINE_CORE_ITEM.get());
                        output.accept(STAR_COMPUTATION_ENGINE_CASING_ITEM.get());
                        output.accept(ENTROPY_ARMILLARY_PLINTH_ITEM.get());
                        output.accept(ENTROPY_ARMILLARY_TRIM_ITEM.get());
                        output.accept(STAR_COMPUTATION_OBSERVATION_PORT_ITEM.get());
                        output.accept(BASE_ENTROPY_ANCHOR_ITEM.get());
                        output.accept(QUANTUM_ENTROPY_ANCHOR_ITEM.get());
                        output.accept(TERMINAL_ENTROPY_ANCHOR_ITEM.get());
                        output.accept(STAR_MARROW_ALLOY_INGOT.get());
                        output.accept(STAR_MARROW_CRYSTAL.get());
                        output.accept(STAR_MARROW_CRYSTAL_DUST.get());
                        output.accept(STAR_CHART_PROCESSOR.get());
                        output.accept(STAR_CHART_CIRCUIT_BOARD.get());
                        output.accept(STAR_SIGNAL_STORAGE_COMPONENT.get());
                        output.accept(PRIMORDIAL_CRYSTAL_CORE.get());
                        output.accept(STAR_MARROW_SEED.get());
                        output.accept(STAR_SIGNAL_STORAGE_CELL.get());
                        output.accept(CREATIVE_STAR_SIGNAL_DISK.get());
                        output.accept(GenericStack.wrapInItemStack(StarSignalKey.INSTANCE, 1));
                        output.accept(STAR_MARROW_CRYSTAL_BUD_SMALL_ITEM.get());
                        output.accept(STAR_MARROW_CRYSTAL_BUD_MEDIUM_ITEM.get());
                        output.accept(STAR_MARROW_CRYSTAL_BUD_LARGE_ITEM.get());
                        output.accept(STAR_MARROW_CRYSTAL_CLUSTER_ITEM.get());
                        output.accept(FLAWLESS_STAR_MARROW_PARENT_ROCK_ITEM.get());
                        output.accept(FLAWED_STAR_MARROW_PARENT_ROCK_ITEM.get());
                        output.accept(DAMAGED_STAR_MARROW_PARENT_ROCK_ITEM.get());
                        output.accept(HEAVILY_DAMAGED_STAR_MARROW_PARENT_ROCK_ITEM.get());
                        output.accept(HighEnergyLabContent.POWER_INTERFACE_ITEM.get());
                        output.accept(HighEnergyLabContent.PLATFORM_ITEM.get());
                        output.accept(HighEnergyLabContent.INPUT_CRADLE_ITEM.get());
                        output.accept(HighEnergyLabContent.OUTPUT_CRADLE_ITEM.get());
                        output.accept(WarpContent.WARP_PAD_ITEM.get());
                        output.accept(WarpContent.COORDINATE_INSCRIPTION_ITEM.get());
                        output.accept(GreenFireLanternContent.ITEM.get());
                        SingularityContent.displayItems(output);
                        output.accept(BLACK_HOLE.get());
                        output.accept(WHITE_HOLE.get());
                        output.accept(MINIATURE_SUPERNOVA.get());
                        output.accept(GRAVITY_CRYSTAL.get());
                        output.accept(GHOST_MATTER.get());
                        output.accept(ModFluids.SEQUENCE_MATTER_BUCKET.get());
                        if (AdvancedAEIntegration.isLoaded()) {
                            output.accept(TRANSFINITE_COMPUTE_NEXUS_ITEM.get());
                            output.accept(OMNI_COMPUTATION_CONTROLLER_ITEM.get());
                            output.accept(OMNI_COMPUTATION_CASING_ITEM.get());
                            output.accept(OMNI_COMPUTATION_GLASS_ITEM.get());
                            output.accept(INFINITE_PARALLEL_MATRIX_ITEM.get());
                            output.accept(INFINITE_CRAFTING_STORAGE_ITEM.get());
                            output.accept(UNIVERSAL_PATTERN_MATRIX_ITEM.get());
                            output.accept(COMPUTATION_DATA_ENTANGLER_ITEM.get());
                            output.accept(COMPUTATION_ENERGY_STABILIZER_ITEM.get());
                            output.accept(COMPUTATION_OUTPUT_NODE_ITEM.get());
                            output.accept(COMPUTATION_CRYSTAL_PYLON_ITEM.get());
                        }
                    })
                    .build());

    private ModContent() {
    }

    private static DeferredBlock<OmniComputationPartBlock> registerOmniPart(String id, int lightLevel) {
        return BLOCKS.register(id, () -> new OmniComputationPartBlock(
                net.minecraft.world.level.block.state.BlockBehaviour.Properties.of()
                        .strength(10.0F, 2400.0F).requiresCorrectToolForDrops()
                        .lightLevel(state -> lightLevel)));
    }

    private static net.minecraft.world.level.block.state.BlockBehaviour.Properties cosmicProperties(int light) {
        return net.minecraft.world.level.block.state.BlockBehaviour.Properties.of()
                .strength(2.0F, 60.0F).noOcclusion().noCollission()
                .isViewBlocking((state, level, pos) -> false)
                .isSuffocating((state, level, pos) -> false)
                .lightLevel(state -> light);
    }

    private static DeferredBlock<OmniComputationPartBlock> registerTranslucentOmniPart(String id, int lightLevel) {
        return BLOCKS.register(id, () -> new OmniComputationPartBlock(
                net.minecraft.world.level.block.state.BlockBehaviour.Properties.of()
                        .strength(10.0F, 2400.0F).requiresCorrectToolForDrops()
                        .noOcclusion().lightLevel(state -> lightLevel)));
    }

    private static DeferredBlock<MatterFabricationPortBlock> registerMatterPort(
            String id, MatterFabricationPortType type, int lightLevel) {
        return BLOCKS.register(id, () -> new MatterFabricationPortBlock(
                net.minecraft.world.level.block.state.BlockBehaviour.Properties.of()
                        .strength(10.0F, 1800.0F).requiresCorrectToolForDrops()
                        .lightLevel(state -> lightLevel), type));
    }

    private static DeferredBlock<AmethystClusterBlock> registerEntropyQuartzCluster(
            String id, float strength, float height, float width, int lightLevel) {
        return BLOCKS.register(id, () -> new AmethystClusterBlock(height, width,
                net.minecraft.world.level.block.state.BlockBehaviour.Properties.of()
                        .strength(strength)
                        .noOcclusion()
                        .noCollission()
                        .isViewBlocking((state, level, pos) -> false)
                        .isSuffocating((state, level, pos) -> false)
                        .lightLevel(state -> lightLevel)
                        .sound(SoundType.AMETHYST)));
    }

    private static net.minecraft.world.level.block.state.BlockBehaviour.Properties parentRockProperties(int lightLevel) {
        return net.minecraft.world.level.block.state.BlockBehaviour.Properties.of()
                .strength(2.5F, 6F)
                .requiresCorrectToolForDrops()
                .randomTicks()
                .lightLevel(state -> lightLevel)
                .sound(SoundType.AMETHYST);
    }

    private static <T extends net.minecraft.world.level.block.Block> DeferredItem<BlockItem> registerBlockItem(
            String id, DeferredBlock<T> block) {
        return ITEMS.register(id, () -> new BlockItem(block.get(), new Item.Properties()));
    }

    public static void register(IEventBus eventBus) {
        ModFluids.register(eventBus);
        HighEnergyLabContent.init();
        WarpContent.init();
        GreenFireLanternContent.init();
        BuildingContent.init();
        SingularityContent.init();
        SingularityContent.registerEntities(eventBus);
        BLOCKS.register(eventBus);
        ITEMS.register(eventBus);
        BLOCK_ENTITIES.register(eventBus);
        MENUS.register(eventBus);
        CREATIVE_TABS.register(eventBus);
        RECIPE_TYPES.register(eventBus);
        RECIPE_SERIALIZERS.register(eventBus);
        eventBus.addListener(ModContent::registerAeKeyTypes);
    }

    public static void registerAeKeyTypes(RegisterEvent event) {
        if (!event.getRegistryKey().equals(AEKeyType.REGISTRY_KEY)) {
            return;
        }
        event.register(
                AEKeyType.REGISTRY_KEY,
                StarSignalKeyType.INSTANCE.getId(),
                () -> StarSignalKeyType.INSTANCE
        );
    }

    public static void bindBlockEntity() {
        HighEnergyLabContent.bindBlockEntity();
        BuildingContent.bind();
        SingularityContent.bindBlockEntity();
        TRANSFINITE_COMPUTE_NEXUS.get().setBlockEntity(
                OmniComputationCoreBlockEntity.class, OMNI_COMPUTATION_CONTROLLER_BE.get(), null, null);
        MOLECULAR_AUTO_CRAFTER.get().setBlockEntity(
                MolecularAutoCrafterBlockEntity.class, MOLECULAR_AUTO_CRAFTER_BE.get(), null, null);
        MOLECULAR_MANIPULATOR.get().setBlockEntity(
                MolecularManipulatorBlockEntity.class,
                MOLECULAR_MANIPULATOR_BLOCK_ENTITY.get(),
                null,
                null);
        ASSEMBLER_MATRIX_MOLECULAR_CORE.get().setBlockEntity(
                AssemblerMatrixMolecularCoreBlockEntity.class,
                ASSEMBLER_MATRIX_MOLECULAR_CORE_BLOCK_ENTITY.get(),
                null,
                null);
        MOLECULAR_CENTER_CONTROLLER.get().setBlockEntity(
                MolecularCenterBlockEntity.class,
                MOLECULAR_CENTER_CONTROLLER_BE.get(),
                null,
                null);
        OMNI_COMPUTATION_CONTROLLER.get().setBlockEntity(
                OmniComputationCoreBlockEntity.class,
                OMNI_COMPUTATION_CONTROLLER_BE.get(),
                null,
                null);
        MATTER_FABRICATION_CONTROLLER.get().setBlockEntity(
                MatterFabricationBlockEntity.class,
                MATTER_FABRICATION_CONTROLLER_BE.get(),
                null,
                null);
        MATTER_FABRICATION_ITEM_INPUT.get().setBlockEntity(
                MatterFabricationPortBlockEntity.class, MATTER_FABRICATION_PORT_BE.get(), null, null);
        MATTER_FABRICATION_ITEM_OUTPUT.get().setBlockEntity(
                MatterFabricationPortBlockEntity.class, MATTER_FABRICATION_PORT_BE.get(), null, null);
        MATTER_FABRICATION_FLUID_INPUT.get().setBlockEntity(
                MatterFabricationPortBlockEntity.class, MATTER_FABRICATION_PORT_BE.get(), null, null);
        MATTER_FABRICATION_FLUID_OUTPUT.get().setBlockEntity(
                MatterFabricationPortBlockEntity.class, MATTER_FABRICATION_PORT_BE.get(), null, null);
        MATTER_FABRICATION_PATTERN_ASSEMBLY.get().setBlockEntity(
                MatterFabricationPatternAssemblyBlockEntity.class,
                MATTER_FABRICATION_PATTERN_ASSEMBLY_BE.get(), null, null);
    }
}
