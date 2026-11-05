package com.atir.molecularmanipulator.registry;

import com.atir.molecularmanipulator.MolecularManipulator;
import com.atir.molecularmanipulator.block.HighEnergyLabPowerInterfaceBlock;
import com.atir.molecularmanipulator.block.HighEnergyLabPlatformBlock;
import com.atir.molecularmanipulator.block.HighEnergyLabCradleBlock;
import com.atir.molecularmanipulator.blockentity.HighEnergyLabPowerInterfaceBlockEntity;
import com.atir.molecularmanipulator.entity.HighEnergyLabEffectsEntity;
import com.atir.molecularmanipulator.world.HighEnergyLabPiece;
import com.atir.molecularmanipulator.world.HighEnergyLabStructure;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;

/** The Twin Singularity Observation Ring's devices, structure and visual effects. */
public final class HighEnergyLabContent {
    public static final ResourceLocation ID = MolecularManipulator.id("twin_singularity_observation_ring");
    public static final DeferredBlock<HighEnergyLabPowerInterfaceBlock> POWER_INTERFACE =
            ModContent.BLOCKS.register("twin_singularity_observation_ring_power_interface", HighEnergyLabPowerInterfaceBlock::new);
    public static final DeferredItem<BlockItem> POWER_INTERFACE_ITEM = ModContent.ITEMS.register(
            "twin_singularity_observation_ring_power_interface", () -> new BlockItem(POWER_INTERFACE.get(), new Item.Properties()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<HighEnergyLabPowerInterfaceBlockEntity>> POWER_INTERFACE_BE =
            ModContent.BLOCK_ENTITIES.register("twin_singularity_observation_ring_power_interface", () -> BlockEntityType.Builder.of(
                    HighEnergyLabPowerInterfaceBlockEntity::new, POWER_INTERFACE.get()).build(null));
    public static final DeferredBlock<HighEnergyLabPlatformBlock> PLATFORM = ModContent.BLOCKS.register(
            "twin_singularity_observation_ring_platform", () -> new HighEnergyLabPlatformBlock(net.minecraft.world.level.block.state.BlockBehaviour.Properties
                    .of().strength(5, 1200).requiresCorrectToolForDrops().sound(net.minecraft.world.level.block.SoundType.STONE)));
    public static final DeferredBlock<HighEnergyLabCradleBlock> INPUT_CRADLE = ModContent.BLOCKS.register(
            "twin_singularity_observation_ring_input_cradle", () -> new HighEnergyLabCradleBlock(net.minecraft.world.level.block.state.BlockBehaviour.Properties
                    .of().strength(5, 1200).requiresCorrectToolForDrops().sound(net.minecraft.world.level.block.SoundType.METAL), true));
    public static final DeferredBlock<HighEnergyLabCradleBlock> OUTPUT_CRADLE = ModContent.BLOCKS.register(
            "twin_singularity_observation_ring_output_cradle", () -> new HighEnergyLabCradleBlock(net.minecraft.world.level.block.state.BlockBehaviour.Properties
                    .of().strength(5, 1200).requiresCorrectToolForDrops().sound(net.minecraft.world.level.block.SoundType.METAL), false));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.atir.molecularmanipulator.blockentity.HighEnergyLabCradleBlockEntity>> CRADLE_BE =
            ModContent.BLOCK_ENTITIES.register("twin_singularity_observation_ring_cradle", () -> BlockEntityType.Builder.of(
                    com.atir.molecularmanipulator.blockentity.HighEnergyLabCradleBlockEntity::new, INPUT_CRADLE.get(), OUTPUT_CRADLE.get()).build(null));
    public static final DeferredItem<BlockItem> PLATFORM_ITEM = ModContent.ITEMS.register(
            "twin_singularity_observation_ring_platform", () -> new BlockItem(PLATFORM.get(), new Item.Properties()));
    public static final DeferredItem<BlockItem> INPUT_CRADLE_ITEM = ModContent.ITEMS.register(
            "twin_singularity_observation_ring_input_cradle", () -> new BlockItem(INPUT_CRADLE.get(), new Item.Properties()));
    public static final DeferredItem<BlockItem> OUTPUT_CRADLE_ITEM = ModContent.ITEMS.register(
            "twin_singularity_observation_ring_output_cradle", () -> new BlockItem(OUTPUT_CRADLE.get(), new Item.Properties()));
    private static final DeferredRegister<StructureType<?>> STRUCTURE_TYPES = DeferredRegister.create(
            Registries.STRUCTURE_TYPE, MolecularManipulator.MOD_ID);
    private static final DeferredRegister<StructurePieceType> STRUCTURE_PIECES = DeferredRegister.create(
            Registries.STRUCTURE_PIECE, MolecularManipulator.MOD_ID);
    public static final DeferredHolder<StructureType<?>, StructureType<HighEnergyLabStructure>> STRUCTURE_TYPE =
            STRUCTURE_TYPES.register(ID.getPath(), () -> () -> HighEnergyLabStructure.CODEC);
    public static final DeferredHolder<StructurePieceType, StructurePieceType> STRUCTURE_PIECE =
            STRUCTURE_PIECES.register(ID.getPath(), () -> (StructurePieceType.StructureTemplateType)HighEnergyLabPiece::new);
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(
            Registries.ENTITY_TYPE, MolecularManipulator.MOD_ID);
    public static final DeferredHolder<EntityType<?>, EntityType<HighEnergyLabEffectsEntity>> EFFECTS =
            ENTITIES.register("twin_singularity_observation_ring_effects", () -> EntityType.Builder
                    .<HighEnergyLabEffectsEntity>of(HighEnergyLabEffectsEntity::new, MobCategory.MISC)
                    .sized(1, 1).fireImmune().clientTrackingRange(16).updateInterval(20)
                    .build("molecularmanipulator:twin_singularity_observation_ring_effects"));

    private HighEnergyLabContent() {}

    public static void bindBlockEntity() {
        POWER_INTERFACE.get().setBlockEntity(HighEnergyLabPowerInterfaceBlockEntity.class, POWER_INTERFACE_BE.get(),
                null, (level, pos, state, inlet) -> inlet.serverTick());
    }

    public static void init() {}

    public static void register(IEventBus bus) {
        // Aliases retain existing block states, inventories and entities while new saves use the new IDs.
        for (String part : new String[] {"power_interface", "platform", "input_cradle", "output_cradle"}) {
            var oldId = MolecularManipulator.id("high_energy_lab_" + part);
            var newId = MolecularManipulator.id(ID.getPath() + "_" + part);
            ModContent.BLOCKS.addAlias(oldId, newId);
            ModContent.ITEMS.addAlias(oldId, newId);
        }
        ModContent.BLOCK_ENTITIES.addAlias(MolecularManipulator.id("high_energy_lab_power_interface"), POWER_INTERFACE_BE.getId());
        ModContent.BLOCK_ENTITIES.addAlias(MolecularManipulator.id("high_energy_lab_cradle"), CRADLE_BE.getId());
        ENTITIES.addAlias(MolecularManipulator.id("high_energy_lab_effects"), EFFECTS.getId());
        STRUCTURE_TYPES.addAlias(MolecularManipulator.id("end_high_energy_lab"), ID);
        STRUCTURE_PIECES.addAlias(MolecularManipulator.id("end_high_energy_lab"), ID);
        STRUCTURE_TYPES.register(bus);
        STRUCTURE_PIECES.register(bus);
        ENTITIES.register(bus);
        NeoForge.EVENT_BUS.addListener(HighEnergyLabContent::aliasSavedStructures);
    }

    private static void aliasSavedStructures(ServerAboutToStartEvent event) {
        var registries = event.getServer().registryAccess();
        registries.registryOrThrow(Registries.STRUCTURE).addAlias(MolecularManipulator.id("end_high_energy_lab"), ID);
        registries.registryOrThrow(Registries.STRUCTURE_SET).addAlias(MolecularManipulator.id("end_high_energy_labs"),
                MolecularManipulator.id("twin_singularity_observation_rings"));
    }
}
