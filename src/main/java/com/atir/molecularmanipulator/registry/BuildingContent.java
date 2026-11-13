package com.atir.molecularmanipulator.registry;

import com.atir.molecularmanipulator.block.FoldingAssemblerBlock;
import com.atir.molecularmanipulator.blockentity.FoldingAssemblerBlockEntity;
import com.atir.molecularmanipulator.item.StarChartCalibratorItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.RegistryObject;

public final class BuildingContent {
    public static final RegistryObject<FoldingAssemblerBlock> ASSEMBLER = ModContent.BLOCKS.register("folding_assembler", FoldingAssemblerBlock::new);
    public static final RegistryObject<BlockItem> ASSEMBLER_ITEM = ModContent.ITEMS.register("folding_assembler",
            () -> new BlockItem(ASSEMBLER.get(), new Item.Properties()));
    public static final RegistryObject<StarChartCalibratorItem> CALIBRATOR = ModContent.ITEMS.register("star_chart_calibrator",
            () -> new StarChartCalibratorItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<BlockEntityType<FoldingAssemblerBlockEntity>> ASSEMBLER_BE =
            ModContent.BLOCK_ENTITIES.register("folding_assembler", () -> BlockEntityType.Builder.of(FoldingAssemblerBlockEntity::new, ASSEMBLER.get()).build(null));
    private BuildingContent() {}
    public static void init() {}
    public static void bind() {
        ASSEMBLER.get().setBlockEntity(FoldingAssemblerBlockEntity.class, ASSEMBLER_BE.get(), null,
                (level, pos, state, entity) -> entity.serverTick());
    }
}
