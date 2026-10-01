package com.atir.molecularmanipulator.registry;

import com.atir.molecularmanipulator.block.TaixuCoreBlock;
import com.atir.molecularmanipulator.block.TaixuPartBlock;
import com.atir.molecularmanipulator.block.TaixuSpireBlock;
import com.atir.molecularmanipulator.blockentity.TaixuCoreBlockEntity;
import com.atir.molecularmanipulator.item.TaixuBlockItem;
import java.util.List;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;

/** The Taixu building palette. Production and multiblock rules are added separately. */
public final class TaixuContent {
    public static final DeferredBlock<com.atir.molecularmanipulator.block.TaixuControllerBlock> CONTROLLER =
            ModContent.BLOCKS.register("taixu_creation_nexus", () -> new com.atir.molecularmanipulator.block.TaixuControllerBlock(properties(9)));
    public static final DeferredBlock<Block> CASING = ModContent.BLOCKS.register("taixu_jade_casing", () -> new Block(properties(0)));
    public static final DeferredBlock<Block> GILDED = ModContent.BLOCKS.register("taixu_gilded_block", () -> new Block(properties(0)));
    public static final DeferredBlock<RotatedPillarBlock> PILLAR = ModContent.BLOCKS.register("taixu_pillar", () -> new RotatedPillarBlock(properties(0)));
    public static final DeferredBlock<TaixuPartBlock> RING = part("taixu_ring_track", 5, TaixuPartBlock.Effect.NONE);
    public static final DeferredBlock<TransparentBlock> GLASS = ModContent.BLOCKS.register("taixu_glass", () -> new TransparentBlock(
            properties(0).strength(6.0F, 1800.0F).sound(SoundType.GLASS).noOcclusion()
                    .isViewBlocking((state, level, pos) -> false).isSuffocating((state, level, pos) -> false)));
    public static final DeferredBlock<RotatedPillarBlock> CONDUIT = ModContent.BLOCKS.register("taixu_conduit", () -> new RotatedPillarBlock(properties(10)));
    public static final DeferredBlock<TaixuPartBlock> COLLECTION_NODE = part("taixu_collection_node", 10, TaixuPartBlock.Effect.GATHER);
    public static final DeferredBlock<TaixuCoreBlock> CORE = ModContent.BLOCKS.register("taixu_genesis_core", () -> new TaixuCoreBlock(properties(12).noOcclusion()));
    public static final DeferredBlock<TaixuPartBlock> STABILIZER = part("taixu_stabilizer", 6, TaixuPartBlock.Effect.NONE);
    public static final DeferredBlock<TaixuSpireBlock> SPIRE = ModContent.BLOCKS.register("taixu_crystal_spire", () -> new TaixuSpireBlock(properties(10).noOcclusion().sound(SoundType.AMETHYST)));
    public static final DeferredBlock<TaixuPartBlock> RESOURCE_PORT = part("taixu_resource_port", 4, TaixuPartBlock.Effect.NONE);
    public static final DeferredBlock<StairBlock> STAIRS = ModContent.BLOCKS.register("taixu_jade_stairs", () -> new StairBlock(CASING.get().defaultBlockState(), properties(0)));
    public static final DeferredBlock<SlabBlock> SLAB = ModContent.BLOCKS.register("taixu_jade_slab", () -> new SlabBlock(properties(0)));

    public static final List<DeferredBlock<? extends Block>> PALETTE = List.of(CONTROLLER, CASING, GILDED,
            PILLAR, RING, GLASS, CONDUIT, COLLECTION_NODE, CORE, STABILIZER, SPIRE, RESOURCE_PORT, STAIRS, SLAB);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TaixuCoreBlockEntity>> CORE_BE =
            ModContent.BLOCK_ENTITIES.register("taixu_genesis_core", () -> BlockEntityType.Builder.of(
                    TaixuCoreBlockEntity::new, CORE.get()).build(null));

    static {
        for (var block : PALETTE) {
            ModContent.ITEMS.register(block.getId().getPath(), () -> new TaixuBlockItem(block.get(),
                    new Item.Properties(), block == CONTROLLER));
        }
    }

    private TaixuContent() {}
    private static final net.neoforged.neoforge.registries.DeferredRegister<net.minecraft.world.entity.EntityType<?>> ENTITIES =
            net.neoforged.neoforge.registries.DeferredRegister.create(net.minecraft.core.registries.Registries.ENTITY_TYPE, com.atir.molecularmanipulator.MolecularManipulator.MOD_ID);
    public static final DeferredHolder<net.minecraft.world.entity.EntityType<?>, net.minecraft.world.entity.EntityType<com.atir.molecularmanipulator.entity.TaixuAssemblyEntity>> ASSEMBLY =
            ENTITIES.register("taixu_assembly", () -> net.minecraft.world.entity.EntityType.Builder.<com.atir.molecularmanipulator.entity.TaixuAssemblyEntity>of(
                    com.atir.molecularmanipulator.entity.TaixuAssemblyEntity::new, net.minecraft.world.entity.MobCategory.MISC)
                    .sized(1, 1).noSave().fireImmune().clientTrackingRange(16).updateInterval(20).build("molecularmanipulator:taixu_assembly"));
    public static void registerEntities(net.neoforged.bus.api.IEventBus bus) { ENTITIES.register(bus); }

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.atir.molecularmanipulator.blockentity.TaixuBlockEntity>> CONTROLLER_BE =
            ModContent.BLOCK_ENTITIES.register("taixu_creation_nexus", () -> BlockEntityType.Builder.of(
                    com.atir.molecularmanipulator.blockentity.TaixuBlockEntity::new, CONTROLLER.get()).build(null));
    public static final DeferredHolder<net.minecraft.world.inventory.MenuType<?>, net.minecraft.world.inventory.MenuType<com.atir.molecularmanipulator.menu.TaixuMenu>> MENU =
            ModContent.MENUS.register("taixu_creation_nexus", () -> com.atir.molecularmanipulator.menu.TaixuMenu.TYPE);

    public static void bindBlockEntity() {
        CONTROLLER.get().setBlockEntity(com.atir.molecularmanipulator.blockentity.TaixuBlockEntity.class, CONTROLLER_BE.get(), null, null);
    }

    /** Force palette registration before the shared deferred registers attach to the mod bus. */
    public static void init() {}

    public static void displayItems(CreativeModeTab.Output output) {
        PALETTE.forEach(block -> output.accept(block.get()));
    }

    private static DeferredBlock<TaixuPartBlock> part(String id, int light, TaixuPartBlock.Effect effect) {
        return ModContent.BLOCKS.register(id, () -> new TaixuPartBlock(properties(light), effect));
    }

    private static BlockBehaviour.Properties properties(int light) {
        return BlockBehaviour.Properties.of().strength(10.0F, 1800.0F).requiresCorrectToolForDrops()
                .sound(SoundType.METAL).lightLevel(state -> light);
    }
}
