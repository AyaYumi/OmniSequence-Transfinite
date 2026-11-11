package com.atir.molecularmanipulator.registry;

import com.atir.molecularmanipulator.MolecularManipulator;
import com.atir.molecularmanipulator.block.SimulationLanternBlock;
import com.atir.molecularmanipulator.blockentity.SimulationLanternBlockEntity;
import com.atir.molecularmanipulator.item.SimulationLanternItem;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;

/** One portable light, retaining its flame state when placed or picked up. */
public final class SimulationLanternContent {
    public static final DeferredBlock<SimulationLanternBlock> LANTERN = ModContent.BLOCKS.register(
            "simulation_lantern", SimulationLanternBlock::new);
    public static final DeferredItem<SimulationLanternItem> ITEM = ModContent.ITEMS.register(
            "simulation_lantern", () -> new SimulationLanternItem(LANTERN.get(), new Item.Properties().stacksTo(1)));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SimulationLanternBlockEntity>> BLOCK_ENTITY =
            ModContent.BLOCK_ENTITIES.register("simulation_lantern", () -> BlockEntityType.Builder.of(
                    SimulationLanternBlockEntity::new, LANTERN.get()).build(null));

    private SimulationLanternContent() {}
    public static void init() {
        // Old stacks, placed states and block entities keep their contents under the new ID.
        var oldId = MolecularManipulator.id("green_fire_lantern");
        ModContent.BLOCKS.addAlias(oldId, LANTERN.getId());
        ModContent.ITEMS.addAlias(oldId, ITEM.getId());
        ModContent.BLOCK_ENTITIES.addAlias(oldId, BLOCK_ENTITY.getId());
    }
    public static boolean isLantern(ItemStack stack) { return stack.is(ITEM.get()); }
    public static boolean isLit(ItemStack stack) {
        return isLantern(stack) && !Boolean.FALSE.equals(stack.getOrDefault(
                DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY).get(SimulationLanternBlock.LIT));
    }
    public static void setLit(ItemStack stack, boolean lit) {
        stack.set(DataComponents.BLOCK_STATE, stack.getOrDefault(DataComponents.BLOCK_STATE,
                BlockItemStateProperties.EMPTY).with(SimulationLanternBlock.LIT, lit));
    }
    public static ItemStack stack(boolean lit) {
        ItemStack stack = new ItemStack(ITEM.get());
        setLit(stack, lit);
        return stack;
    }
}
