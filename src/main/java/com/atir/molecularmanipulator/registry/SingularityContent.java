package com.atir.molecularmanipulator.registry;

import com.atir.molecularmanipulator.block.SingularityCoreBlock;
import com.atir.molecularmanipulator.block.SingularityPartBlock;
import com.atir.molecularmanipulator.block.SingularitySpireBlock;
import com.atir.molecularmanipulator.blockentity.SingularityCoreBlockEntity;
import com.atir.molecularmanipulator.item.SingularityBlockItem;
import java.util.List;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.GlassBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.registries.RegistryObject;


/** The Singularity building palette. Production and multiblock rules are added separately. */
public final class SingularityContent {
    public static final RegistryObject<com.atir.molecularmanipulator.block.SingularityControllerBlock> CONTROLLER =
            ModContent.BLOCKS.register("event_horizon_singularity_hub", () -> new com.atir.molecularmanipulator.block.SingularityControllerBlock(properties(9)));
    public static final RegistryObject<Block> CASING = ModContent.BLOCKS.register("singularity_base_casing", () -> new Block(properties(0)));
    public static final RegistryObject<Block> GILDED = ModContent.BLOCKS.register("gravity_gilded_block", () -> new Block(properties(0)));
    public static final RegistryObject<RotatedPillarBlock> PILLAR = ModContent.BLOCKS.register("spacetime_anchor_pillar", () -> new RotatedPillarBlock(properties(0)));
    public static final RegistryObject<SingularityPartBlock> RING = part("singularity_ring_track", 5, SingularityPartBlock.Effect.NONE);
    public static final RegistryObject<GlassBlock> GLASS = ModContent.BLOCKS.register("event_horizon_lens", () -> new GlassBlock(
            properties(0).strength(6.0F, 1800.0F).sound(SoundType.GLASS).noOcclusion()
                    .isViewBlocking((state, level, pos) -> false).isSuffocating((state, level, pos) -> false)));
    public static final RegistryObject<RotatedPillarBlock> CONDUIT = ModContent.BLOCKS.register("event_horizon_flux_pillar", () -> new RotatedPillarBlock(properties(10)));
    public static final RegistryObject<SingularityPartBlock> COLLECTION_NODE = part("black_hole_capture_node", 10, SingularityPartBlock.Effect.GATHER);
    public static final RegistryObject<SingularityCoreBlock> CORE = ModContent.BLOCKS.register("white_hole_resource_core", () -> new SingularityCoreBlock(properties(12).noOcclusion()));
    public static final RegistryObject<SingularityPartBlock> STABILIZER = part("event_horizon_stabilizer", 6, SingularityPartBlock.Effect.NONE);
    public static final RegistryObject<SingularitySpireBlock> SPIRE = ModContent.BLOCKS.register("singularity_crystal_tower", () -> new SingularitySpireBlock(properties(10).noOcclusion().sound(SoundType.AMETHYST)));
    public static final RegistryObject<StairBlock> STAIRS = ModContent.BLOCKS.register("singularity_base_stairs", () -> new StairBlock(() -> CASING.get().defaultBlockState(), properties(0)));
    public static final RegistryObject<SlabBlock> SLAB = ModContent.BLOCKS.register("singularity_base_slab", () -> new SlabBlock(properties(0)));

    public static final List<RegistryObject<? extends Block>> PALETTE = List.of(CONTROLLER, CASING, GILDED,
            PILLAR, RING, GLASS, CONDUIT, COLLECTION_NODE, CORE, STABILIZER, SPIRE, STAIRS, SLAB);

    public static final RegistryObject<BlockEntityType<SingularityCoreBlockEntity>> CORE_BE =
            ModContent.BLOCK_ENTITIES.register("white_hole_resource_core", () -> BlockEntityType.Builder.of(
                    SingularityCoreBlockEntity::new, CORE.get()).build(null));

    static {
        for (var block : PALETTE) {
            ModContent.ITEMS.register(block.getId().getPath(), () -> new SingularityBlockItem(block.get(),
                    new Item.Properties(), block == CONTROLLER));
        }
    }

    private SingularityContent() {}
    private static final net.minecraftforge.registries.DeferredRegister<net.minecraft.world.entity.EntityType<?>> ENTITIES =
            net.minecraftforge.registries.DeferredRegister.create(net.minecraft.core.registries.Registries.ENTITY_TYPE, com.atir.molecularmanipulator.MolecularManipulator.MOD_ID);
    public static final RegistryObject<net.minecraft.world.entity.EntityType<com.atir.molecularmanipulator.entity.SingularityAssemblyEntity>> ASSEMBLY =
            ENTITIES.register("singularity_assembly", () -> net.minecraft.world.entity.EntityType.Builder.<com.atir.molecularmanipulator.entity.SingularityAssemblyEntity>of(
                    com.atir.molecularmanipulator.entity.SingularityAssemblyEntity::new, net.minecraft.world.entity.MobCategory.MISC)
                    .sized(1, 1).noSave().fireImmune().clientTrackingRange(16).updateInterval(20).build("molecularmanipulator:singularity_assembly"));
    public static void registerEntities(net.minecraftforge.eventbus.api.IEventBus bus) { ENTITIES.register(bus); }

    public static final RegistryObject<BlockEntityType<com.atir.molecularmanipulator.blockentity.SingularityBlockEntity>> CONTROLLER_BE =
            ModContent.BLOCK_ENTITIES.register("event_horizon_singularity_hub", () -> BlockEntityType.Builder.of(
                    com.atir.molecularmanipulator.blockentity.SingularityBlockEntity::new, CONTROLLER.get()).build(null));
    public static final RegistryObject<net.minecraft.world.inventory.MenuType<com.atir.molecularmanipulator.menu.SingularityMenu>> MENU =
            ModContent.MENUS.register("event_horizon_singularity_hub", () -> com.atir.molecularmanipulator.menu.SingularityMenu.TYPE);

    public static void bindBlockEntity() {
        CONTROLLER.get().setBlockEntity(com.atir.molecularmanipulator.blockentity.SingularityBlockEntity.class, CONTROLLER_BE.get(), null, null);
    }

    /** Force palette registration before the shared deferred registers attach to the mod bus. */
    public static void init() {}

    public static void displayItems(CreativeModeTab.Output output) {
        PALETTE.forEach(block -> output.accept(block.get()));
    }

    private static RegistryObject<SingularityPartBlock> part(String id, int light, SingularityPartBlock.Effect effect) {
        return ModContent.BLOCKS.register(id, () -> new SingularityPartBlock(properties(light), effect));
    }

    private static BlockBehaviour.Properties properties(int light) {
        return BlockBehaviour.Properties.of().strength(10.0F, 1800.0F).requiresCorrectToolForDrops()
                .sound(SoundType.METAL).lightLevel(state -> light);
    }
}
