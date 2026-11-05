package com.atir.molecularmanipulator.registry;

import com.atir.molecularmanipulator.MolecularManipulator;
import com.atir.molecularmanipulator.block.EndWarpPadBlock;
import com.atir.molecularmanipulator.block.WarpCoordinateInscriptionBlock;
import com.atir.molecularmanipulator.blockentity.EndWarpPadBlockEntity;
import com.atir.molecularmanipulator.item.EndWarpPadItem;
import com.atir.molecularmanipulator.world.EndWarpRuinStructure;
import com.atir.molecularmanipulator.world.EndWarpTemplatePiece;
import com.atir.molecularmanipulator.world.FixedStructurePlacement;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacementType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Registrations for the fixed End coordinate warp and its editable pad. */
public final class WarpContent {
    private static final DeferredRegister<StructureType<?>> STRUCTURE_TYPES = DeferredRegister.create(
            Registries.STRUCTURE_TYPE, MolecularManipulator.MOD_ID);
    private static final DeferredRegister<StructurePieceType> STRUCTURE_PIECES = DeferredRegister.create(
            Registries.STRUCTURE_PIECE, MolecularManipulator.MOD_ID);
    private static final DeferredRegister<StructurePlacementType<?>> STRUCTURE_PLACEMENTS = DeferredRegister.create(
            Registries.STRUCTURE_PLACEMENT, MolecularManipulator.MOD_ID);

    public static final DeferredBlock<EndWarpPadBlock> WARP_PAD = ModContent.BLOCKS.register(
            "end_warp_pad", EndWarpPadBlock::new);
    public static final DeferredItem<BlockItem> WARP_PAD_ITEM = ModContent.ITEMS.register(
            "end_warp_pad", () -> new EndWarpPadItem(WARP_PAD.get(), new Item.Properties()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<EndWarpPadBlockEntity>> WARP_PAD_BE =
            ModContent.BLOCK_ENTITIES.register("end_warp_pad", () -> BlockEntityType.Builder.of(
                    EndWarpPadBlockEntity::new, WARP_PAD.get()).build(null));

    public static final DeferredBlock<WarpCoordinateInscriptionBlock> COORDINATE_INSCRIPTION = ModContent.BLOCKS.register(
            "warp_coordinate_inscription", () -> new WarpCoordinateInscriptionBlock(
                    BlockBehaviour.Properties.ofFullCopy(Blocks.END_STONE_BRICKS)));
    public static final DeferredItem<BlockItem> COORDINATE_INSCRIPTION_ITEM = ModContent.ITEMS.register(
            "warp_coordinate_inscription", () -> new BlockItem(COORDINATE_INSCRIPTION.get(), new Item.Properties()));

    public static final DeferredHolder<StructureType<?>, StructureType<EndWarpRuinStructure>> RUIN_STRUCTURE_TYPE =
            STRUCTURE_TYPES.register("end_warp_ruin", () -> () -> EndWarpRuinStructure.CODEC);
    public static final DeferredHolder<StructurePieceType, StructurePieceType> RUIN_PIECE_TYPE =
            STRUCTURE_PIECES.register("end_warp_ruin", () -> (StructurePieceType.StructureTemplateType)
                    EndWarpTemplatePiece::new);
    public static final DeferredHolder<StructurePlacementType<?>, StructurePlacementType<FixedStructurePlacement>>
            FIXED_PLACEMENT = STRUCTURE_PLACEMENTS.register("fixed", () -> () -> FixedStructurePlacement.CODEC);

    private WarpContent() {}

    public static void init() {}

    public static void register(IEventBus bus) {
        STRUCTURE_TYPES.register(bus);
        STRUCTURE_PIECES.register(bus);
        STRUCTURE_PLACEMENTS.register(bus);
    }
}
