package com.atir.molecularmanipulator.registry;

import com.atir.molecularmanipulator.MolecularManipulator;
import com.atir.molecularmanipulator.block.AssemblerMatrixMolecularCoreBlock;
import com.atir.molecularmanipulator.block.MolecularCenterControllerBlock;
import com.atir.molecularmanipulator.block.MolecularCenterPartBlock;
import com.atir.molecularmanipulator.block.MolecularCenterCoreBlock;
import com.atir.molecularmanipulator.block.MolecularCenterGlassBlock;
import com.atir.molecularmanipulator.block.OmniComputationCasingBlock;
import com.atir.molecularmanipulator.block.OmniComputationControllerBlock;
import com.atir.molecularmanipulator.block.OmniComputationGlassBlock;
import com.atir.molecularmanipulator.block.OmniComputationPartBlock;
import com.atir.molecularmanipulator.blockentity.AssemblerMatrixMolecularCoreBlockEntity;
import com.atir.molecularmanipulator.blockentity.MolecularCenterBlockEntity;
import com.atir.molecularmanipulator.blockentity.MolecularCenterShellBlockEntity;
import com.atir.molecularmanipulator.blockentity.OmniComputationCoreBlockEntity;
import com.atir.molecularmanipulator.integration.AdvancedAEIntegration;
import com.atir.molecularmanipulator.menu.MolecularCenterMenu;
import com.atir.molecularmanipulator.menu.OmniComputationMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModContent {
    public static final DeferredRegister<net.minecraft.world.level.block.Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MolecularManipulator.MOD_ID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MolecularManipulator.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, MolecularManipulator.MOD_ID);
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, MolecularManipulator.MOD_ID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MolecularManipulator.MOD_ID);

    public static final RegistryObject<AssemblerMatrixMolecularCoreBlock> ASSEMBLER_MATRIX_MOLECULAR_CORE =
            BLOCKS.register("assembler_matrix_molecular_core", AssemblerMatrixMolecularCoreBlock::new);
    public static final RegistryObject<BlockItem> ASSEMBLER_MATRIX_MOLECULAR_CORE_ITEM =
            ITEMS.register("assembler_matrix_molecular_core",
                    () -> new BlockItem(ASSEMBLER_MATRIX_MOLECULAR_CORE.get(), new Item.Properties()));

    public static final RegistryObject<MolecularCenterControllerBlock> MOLECULAR_CENTER_CONTROLLER =
            BLOCKS.register("molecular_center_controller", () -> new MolecularCenterControllerBlock(
                    net.minecraft.world.level.block.state.BlockBehaviour.Properties.of()
                            .strength(8.0F, 1200.0F).requiresCorrectToolForDrops()
                            .lightLevel(state -> state.getValue(
                                    net.minecraft.world.level.block.state.properties.BlockStateProperties.POWERED)
                                    ? 15 : 6)));
    public static final RegistryObject<BlockItem> MOLECULAR_CENTER_CONTROLLER_ITEM = ITEMS.register(
            "molecular_center_controller", () -> new BlockItem(MOLECULAR_CENTER_CONTROLLER.get(), new Item.Properties()));
    public static final RegistryObject<MolecularCenterPartBlock> MOLECULAR_CENTER_CASING = BLOCKS.register(
            "molecular_center_casing", () -> new MolecularCenterPartBlock(
                    net.minecraft.world.level.block.state.BlockBehaviour.Properties.of()
                            .strength(8.0F, 1200.0F).requiresCorrectToolForDrops()
                            .lightLevel(state -> 7), true));
    public static final RegistryObject<BlockItem> MOLECULAR_CENTER_CASING_ITEM = ITEMS.register(
            "molecular_center_casing", () -> new BlockItem(MOLECULAR_CENTER_CASING.get(), new Item.Properties()));
    public static final RegistryObject<MolecularCenterGlassBlock> MOLECULAR_CENTER_GLASS = BLOCKS.register(
            "molecular_center_glass", () -> new MolecularCenterGlassBlock(
                    net.minecraft.world.level.block.state.BlockBehaviour.Properties.of()
                            .strength(5.0F, 1200.0F).requiresCorrectToolForDrops().noOcclusion()
                            .lightLevel(state -> 12)));
    public static final RegistryObject<BlockItem> MOLECULAR_CENTER_GLASS_ITEM = ITEMS.register(
            "molecular_center_glass", () -> new BlockItem(MOLECULAR_CENTER_GLASS.get(), new Item.Properties()));
    public static final RegistryObject<MolecularCenterPartBlock> MOLECULAR_CENTER_COIL = BLOCKS.register(
            "molecular_center_coil", () -> new MolecularCenterPartBlock(
                    net.minecraft.world.level.block.state.BlockBehaviour.Properties.of()
                            .strength(8.0F, 1200.0F).requiresCorrectToolForDrops()
                            .noOcclusion().lightLevel(state -> 15)));
    public static final RegistryObject<BlockItem> MOLECULAR_CENTER_COIL_ITEM = ITEMS.register(
            "molecular_center_coil", () -> new BlockItem(MOLECULAR_CENTER_COIL.get(), new Item.Properties()));
    public static final RegistryObject<MolecularCenterPartBlock> MOLECULAR_CENTER_STABILIZER = BLOCKS.register(
            "molecular_center_stabilizer", () -> new MolecularCenterPartBlock(
                    net.minecraft.world.level.block.state.BlockBehaviour.Properties.of()
                            .strength(8.0F, 1200.0F).requiresCorrectToolForDrops()
                            .lightLevel(state -> 10)));
    public static final RegistryObject<BlockItem> MOLECULAR_CENTER_STABILIZER_ITEM = ITEMS.register(
            "molecular_center_stabilizer", () -> new BlockItem(MOLECULAR_CENTER_STABILIZER.get(), new Item.Properties()));
    public static final RegistryObject<MolecularCenterCoreBlock> MOLECULAR_CENTER_CORE = BLOCKS.register(
            "molecular_center_core", () -> new MolecularCenterCoreBlock(
                    net.minecraft.world.level.block.state.BlockBehaviour.Properties.of()
                            .strength(8.0F, 1200.0F).requiresCorrectToolForDrops().lightLevel(state -> 15)));
    public static final RegistryObject<BlockItem> MOLECULAR_CENTER_CORE_ITEM = ITEMS.register(
            "molecular_center_core", () -> new BlockItem(MOLECULAR_CENTER_CORE.get(), new Item.Properties()));

    public static final RegistryObject<OmniComputationControllerBlock> OMNI_COMPUTATION_CONTROLLER =
            BLOCKS.register("omni_computation_controller", () -> new OmniComputationControllerBlock(
                    net.minecraft.world.level.block.state.BlockBehaviour.Properties.of()
                            .strength(12.0F, 2400.0F).requiresCorrectToolForDrops()
                            .lightLevel(state -> 15)));
    public static final RegistryObject<BlockItem> OMNI_COMPUTATION_CONTROLLER_ITEM = ITEMS.register(
            "omni_computation_controller",
            () -> new BlockItem(OMNI_COMPUTATION_CONTROLLER.get(), new Item.Properties()));
    public static final RegistryObject<OmniComputationCasingBlock> OMNI_COMPUTATION_CASING =
            BLOCKS.register("omni_computation_casing", () -> new OmniComputationCasingBlock(
                    net.minecraft.world.level.block.state.BlockBehaviour.Properties.of()
                            .strength(10.0F, 2400.0F).requiresCorrectToolForDrops()
                            .noOcclusion().lightLevel(state -> 7)));
    public static final RegistryObject<BlockItem> OMNI_COMPUTATION_CASING_ITEM =
            registerBlockItem("omni_computation_casing", OMNI_COMPUTATION_CASING);
    public static final RegistryObject<OmniComputationGlassBlock> OMNI_COMPUTATION_GLASS =
            BLOCKS.register("omni_computation_glass", () -> new OmniComputationGlassBlock(
                    net.minecraft.world.level.block.state.BlockBehaviour.Properties.of()
                            .strength(8.0F, 2400.0F).requiresCorrectToolForDrops()
                            .noOcclusion().lightLevel(state -> 13)));
    public static final RegistryObject<BlockItem> OMNI_COMPUTATION_GLASS_ITEM =
            registerBlockItem("omni_computation_glass", OMNI_COMPUTATION_GLASS);
    public static final RegistryObject<OmniComputationPartBlock> INFINITE_PARALLEL_MATRIX =
            registerOmniPart("infinite_parallel_matrix", 15);
    public static final RegistryObject<BlockItem> INFINITE_PARALLEL_MATRIX_ITEM =
            registerBlockItem("infinite_parallel_matrix", INFINITE_PARALLEL_MATRIX);
    public static final RegistryObject<OmniComputationPartBlock> INFINITE_CRAFTING_STORAGE =
            registerOmniPart("infinite_crafting_storage", 15);
    public static final RegistryObject<BlockItem> INFINITE_CRAFTING_STORAGE_ITEM =
            registerBlockItem("infinite_crafting_storage", INFINITE_CRAFTING_STORAGE);
    public static final RegistryObject<OmniComputationPartBlock> UNIVERSAL_PATTERN_MATRIX =
            registerTranslucentOmniPart("universal_pattern_matrix", 12);
    public static final RegistryObject<BlockItem> UNIVERSAL_PATTERN_MATRIX_ITEM =
            registerBlockItem("universal_pattern_matrix", UNIVERSAL_PATTERN_MATRIX);
    public static final RegistryObject<OmniComputationPartBlock> COMPUTATION_DATA_ENTANGLER =
            registerOmniPart("computation_data_entangler", 14);
    public static final RegistryObject<BlockItem> COMPUTATION_DATA_ENTANGLER_ITEM =
            registerBlockItem("computation_data_entangler", COMPUTATION_DATA_ENTANGLER);
    public static final RegistryObject<OmniComputationPartBlock> COMPUTATION_ENERGY_STABILIZER =
            registerTranslucentOmniPart("computation_energy_stabilizer", 13);
    public static final RegistryObject<BlockItem> COMPUTATION_ENERGY_STABILIZER_ITEM =
            registerBlockItem("computation_energy_stabilizer", COMPUTATION_ENERGY_STABILIZER);
    public static final RegistryObject<OmniComputationPartBlock> COMPUTATION_OUTPUT_NODE =
            registerOmniPart("computation_output_node", 12);
    public static final RegistryObject<BlockItem> COMPUTATION_OUTPUT_NODE_ITEM =
            registerBlockItem("computation_output_node", COMPUTATION_OUTPUT_NODE);
    public static final RegistryObject<OmniComputationPartBlock> COMPUTATION_CRYSTAL_PYLON =
            registerTranslucentOmniPart("computation_crystal_pylon", 15);
    public static final RegistryObject<BlockItem> COMPUTATION_CRYSTAL_PYLON_ITEM =
            registerBlockItem("computation_crystal_pylon", COMPUTATION_CRYSTAL_PYLON);

    public static final RegistryObject<BlockEntityType<AssemblerMatrixMolecularCoreBlockEntity>>
            ASSEMBLER_MATRIX_MOLECULAR_CORE_BLOCK_ENTITY = BLOCK_ENTITIES.register("assembler_matrix_molecular_core",
                    () -> BlockEntityType.Builder.of(AssemblerMatrixMolecularCoreBlockEntity::new,
                            ASSEMBLER_MATRIX_MOLECULAR_CORE.get()).build(null));
    public static final RegistryObject<BlockEntityType<MolecularCenterBlockEntity>>
            MOLECULAR_CENTER_CONTROLLER_BE = BLOCK_ENTITIES.register("molecular_center_controller",
                    () -> BlockEntityType.Builder.of(MolecularCenterBlockEntity::new,
                            MOLECULAR_CENTER_CONTROLLER.get()).build(null));
    public static final RegistryObject<BlockEntityType<MolecularCenterShellBlockEntity>>
            MOLECULAR_CENTER_SHELL_BE = BLOCK_ENTITIES.register("molecular_center_shell",
                    () -> BlockEntityType.Builder.of(MolecularCenterShellBlockEntity::new,
                            MOLECULAR_CENTER_CASING.get()).build(null));
    public static final RegistryObject<BlockEntityType<OmniComputationCoreBlockEntity>>
            OMNI_COMPUTATION_CONTROLLER_BE = BLOCK_ENTITIES.register("omni_computation_controller",
                    () -> BlockEntityType.Builder.of(OmniComputationCoreBlockEntity::new,
                            OMNI_COMPUTATION_CONTROLLER.get()).build(null));
    public static final RegistryObject<MenuType<MolecularCenterMenu>> MOLECULAR_CENTER_MENU =
            MENUS.register("molecular_center", () -> MolecularCenterMenu.TYPE);
    public static final RegistryObject<MenuType<OmniComputationMenu>> OMNI_COMPUTATION_MENU =
            MENUS.register("omni_computation", () -> OmniComputationMenu.TYPE);

    public static final RegistryObject<CreativeModeTab> MAIN_TAB = CREATIVE_TABS.register(
            "main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.molecularmanipulator"))
                    .icon(() -> ASSEMBLER_MATRIX_MOLECULAR_CORE_ITEM.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        output.accept(ASSEMBLER_MATRIX_MOLECULAR_CORE_ITEM.get());
                        output.accept(MOLECULAR_CENTER_CONTROLLER_ITEM.get());
                        output.accept(MOLECULAR_CENTER_CASING_ITEM.get());
                        output.accept(MOLECULAR_CENTER_GLASS_ITEM.get());
                        output.accept(MOLECULAR_CENTER_COIL_ITEM.get());
                        output.accept(MOLECULAR_CENTER_STABILIZER_ITEM.get());
                        output.accept(MOLECULAR_CENTER_CORE_ITEM.get());
                        if (AdvancedAEIntegration.isLoaded()) {
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

    private static RegistryObject<OmniComputationPartBlock> registerOmniPart(String id, int lightLevel) {
        return BLOCKS.register(id, () -> new OmniComputationPartBlock(
                net.minecraft.world.level.block.state.BlockBehaviour.Properties.of()
                        .strength(10.0F, 2400.0F).requiresCorrectToolForDrops()
                        .lightLevel(state -> lightLevel)));
    }

    private static RegistryObject<OmniComputationPartBlock> registerTranslucentOmniPart(String id, int lightLevel) {
        return BLOCKS.register(id, () -> new OmniComputationPartBlock(
                net.minecraft.world.level.block.state.BlockBehaviour.Properties.of()
                        .strength(10.0F, 2400.0F).requiresCorrectToolForDrops()
                        .noOcclusion().lightLevel(state -> lightLevel)));
    }

    private static <T extends net.minecraft.world.level.block.Block> RegistryObject<BlockItem> registerBlockItem(
            String id, RegistryObject<T> block) {
        return ITEMS.register(id, () -> new BlockItem(block.get(), new Item.Properties()));
    }

    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
        ITEMS.register(eventBus);
        BLOCK_ENTITIES.register(eventBus);
        MENUS.register(eventBus);
        CREATIVE_TABS.register(eventBus);
    }

    public static void bindBlockEntity() {
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
    }
}
