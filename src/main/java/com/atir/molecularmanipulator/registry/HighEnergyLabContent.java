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
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/** The Twin Singularity Observation Ring's devices, structure and visual effects. */
public final class HighEnergyLabContent {
    public static final ResourceLocation ID = MolecularManipulator.id("twin_singularity_observation_ring");
    public static final RegistryObject<HighEnergyLabPowerInterfaceBlock> POWER_INTERFACE =
            ModContent.BLOCKS.register("twin_singularity_observation_ring_power_interface", HighEnergyLabPowerInterfaceBlock::new);
    public static final RegistryObject<BlockItem> POWER_INTERFACE_ITEM = ModContent.ITEMS.register(
            "twin_singularity_observation_ring_power_interface", () -> new BlockItem(POWER_INTERFACE.get(), new Item.Properties()));
    public static final RegistryObject<BlockEntityType<HighEnergyLabPowerInterfaceBlockEntity>> POWER_INTERFACE_BE =
            ModContent.BLOCK_ENTITIES.register("twin_singularity_observation_ring_power_interface", () -> BlockEntityType.Builder.of(
                    HighEnergyLabPowerInterfaceBlockEntity::new, POWER_INTERFACE.get()).build(null));
    public static final RegistryObject<HighEnergyLabPlatformBlock> PLATFORM = ModContent.BLOCKS.register(
            "twin_singularity_observation_ring_platform", () -> new HighEnergyLabPlatformBlock(net.minecraft.world.level.block.state.BlockBehaviour.Properties
                    .of().strength(5, 1200).requiresCorrectToolForDrops().sound(net.minecraft.world.level.block.SoundType.STONE)));
    public static final RegistryObject<HighEnergyLabCradleBlock> INPUT_CRADLE = ModContent.BLOCKS.register(
            "twin_singularity_observation_ring_input_cradle", () -> new HighEnergyLabCradleBlock(net.minecraft.world.level.block.state.BlockBehaviour.Properties
                    .of().strength(5, 1200).requiresCorrectToolForDrops().sound(net.minecraft.world.level.block.SoundType.METAL), true));
    public static final RegistryObject<HighEnergyLabCradleBlock> OUTPUT_CRADLE = ModContent.BLOCKS.register(
            "twin_singularity_observation_ring_output_cradle", () -> new HighEnergyLabCradleBlock(net.minecraft.world.level.block.state.BlockBehaviour.Properties
                    .of().strength(5, 1200).requiresCorrectToolForDrops().sound(net.minecraft.world.level.block.SoundType.METAL), false));
    public static final RegistryObject<BlockEntityType<com.atir.molecularmanipulator.blockentity.HighEnergyLabCradleBlockEntity>> CRADLE_BE =
            ModContent.BLOCK_ENTITIES.register("twin_singularity_observation_ring_cradle", () -> BlockEntityType.Builder.of(
                    com.atir.molecularmanipulator.blockentity.HighEnergyLabCradleBlockEntity::new, INPUT_CRADLE.get(), OUTPUT_CRADLE.get()).build(null));
    public static final RegistryObject<BlockItem> PLATFORM_ITEM = ModContent.ITEMS.register(
            "twin_singularity_observation_ring_platform", () -> new BlockItem(PLATFORM.get(), new Item.Properties()));
    public static final RegistryObject<BlockItem> INPUT_CRADLE_ITEM = ModContent.ITEMS.register(
            "twin_singularity_observation_ring_input_cradle", () -> new BlockItem(INPUT_CRADLE.get(), new Item.Properties()));
    public static final RegistryObject<BlockItem> OUTPUT_CRADLE_ITEM = ModContent.ITEMS.register(
            "twin_singularity_observation_ring_output_cradle", () -> new BlockItem(OUTPUT_CRADLE.get(), new Item.Properties()));
    private static final DeferredRegister<StructureType<?>> STRUCTURE_TYPES = DeferredRegister.create(
            Registries.STRUCTURE_TYPE, MolecularManipulator.MOD_ID);
    private static final DeferredRegister<StructurePieceType> STRUCTURE_PIECES = DeferredRegister.create(
            Registries.STRUCTURE_PIECE, MolecularManipulator.MOD_ID);
    public static final RegistryObject<StructureType<HighEnergyLabStructure>> STRUCTURE_TYPE =
            STRUCTURE_TYPES.register(ID.getPath(), () -> () -> HighEnergyLabStructure.CODEC);
    public static final RegistryObject<StructurePieceType> STRUCTURE_PIECE =
            STRUCTURE_PIECES.register(ID.getPath(), () -> (StructurePieceType.StructureTemplateType)HighEnergyLabPiece::new);
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(
            Registries.ENTITY_TYPE, MolecularManipulator.MOD_ID);
    public static final RegistryObject<EntityType<HighEnergyLabEffectsEntity>> EFFECTS =
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
        STRUCTURE_TYPES.register(bus);
        STRUCTURE_PIECES.register(bus);
        ENTITIES.register(bus);
    }
}
