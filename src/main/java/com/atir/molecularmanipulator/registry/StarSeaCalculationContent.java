package com.atir.molecularmanipulator.registry;

import com.atir.molecularmanipulator.block.StarSeaCalculationMainframeBlock;
import com.atir.molecularmanipulator.blockentity.StarSeaCalculationMainframeBlockEntity;
import com.atir.molecularmanipulator.item.StarSeaCalculationMainframeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.RegistryObject;

/** The complete, directly placeable M1 building; no user-built assembly recipe. */
public final class StarSeaCalculationContent {
    public static final String ID = "star_sea_calculation_mainframe";
    public static final RegistryObject<StarSeaCalculationMainframeBlock> BLOCK = ModContent.BLOCKS.register(
            ID, StarSeaCalculationMainframeBlock::new);
    public static final RegistryObject<StarSeaCalculationMainframeItem> ITEM = ModContent.ITEMS.register(
            ID, () -> new StarSeaCalculationMainframeItem(BLOCK.get(), new Item.Properties().stacksTo(1)));
    public static final RegistryObject<BlockEntityType<StarSeaCalculationMainframeBlockEntity>> BLOCK_ENTITY =
            ModContent.BLOCK_ENTITIES.register(ID, () -> BlockEntityType.Builder.of(
                    StarSeaCalculationMainframeBlockEntity::new, BLOCK.get()).build(null));

    private StarSeaCalculationContent() {}

    public static void init() {}
}
