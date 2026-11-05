package com.atir.molecularmanipulator.registry;

import com.atir.molecularmanipulator.block.GreenFireLanternBlock;
import com.atir.molecularmanipulator.blockentity.GreenFireLanternBlockEntity;
import com.atir.molecularmanipulator.item.GreenFireLanternItem;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;

/** One portable light, retaining its flame state when placed or picked up. */
public final class GreenFireLanternContent {
    public static final DeferredBlock<GreenFireLanternBlock> LANTERN = ModContent.BLOCKS.register(
            "green_fire_lantern", GreenFireLanternBlock::new);
    public static final DeferredItem<GreenFireLanternItem> ITEM = ModContent.ITEMS.register(
            "green_fire_lantern", () -> new GreenFireLanternItem(LANTERN.get(), new Item.Properties().stacksTo(1)));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GreenFireLanternBlockEntity>> BLOCK_ENTITY =
            ModContent.BLOCK_ENTITIES.register("green_fire_lantern", () -> BlockEntityType.Builder.of(
                    GreenFireLanternBlockEntity::new, LANTERN.get()).build(null));

    private GreenFireLanternContent() {}
    public static void init() {}
    public static boolean isLantern(ItemStack stack) { return stack.is(ITEM.get()); }
    public static boolean isLit(ItemStack stack) {
        return isLantern(stack) && !Boolean.FALSE.equals(stack.getOrDefault(
                DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY).get(GreenFireLanternBlock.LIT));
    }
    public static void setLit(ItemStack stack, boolean lit) {
        stack.set(DataComponents.BLOCK_STATE, stack.getOrDefault(DataComponents.BLOCK_STATE,
                BlockItemStateProperties.EMPTY).with(GreenFireLanternBlock.LIT, lit));
    }
    public static ItemStack stack(boolean lit) {
        ItemStack stack = new ItemStack(ITEM.get());
        setLit(stack, lit);
        return stack;
    }
}
