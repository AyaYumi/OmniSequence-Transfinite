package com.atir.molecularmanipulator.registry;

import com.atir.molecularmanipulator.block.SimulationLanternBlock;
import com.atir.molecularmanipulator.blockentity.SimulationLanternBlockEntity;
import com.atir.molecularmanipulator.item.SimulationLanternItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.RegistryObject;

/** One portable light, retaining its flame state when placed or picked up. */
public final class SimulationLanternContent {
    public static final RegistryObject<SimulationLanternBlock> LANTERN = ModContent.BLOCKS.register(
            "simulation_lantern", SimulationLanternBlock::new);
    public static final RegistryObject<SimulationLanternItem> ITEM = ModContent.ITEMS.register(
            "simulation_lantern", () -> new SimulationLanternItem(LANTERN.get(), new Item.Properties().stacksTo(1)));
    public static final RegistryObject<BlockEntityType<SimulationLanternBlockEntity>> BLOCK_ENTITY =
            ModContent.BLOCK_ENTITIES.register("simulation_lantern", () -> BlockEntityType.Builder.of(
                    SimulationLanternBlockEntity::new, LANTERN.get()).build(null));

    private SimulationLanternContent() {}
    public static void init() {}
    public static boolean isLantern(ItemStack stack) { return stack.is(ITEM.get()); }
    public static boolean isLit(ItemStack stack) {
        var state = stack.getTagElement("BlockStateTag");
        // Vanilla block-item properties are strings in 1.20.1; an absent/invalid value keeps the default flame.
        return isLantern(stack) && (state == null
                || !"false".equals(state.getString(SimulationLanternBlock.LIT.getName())));
    }
    public static void setLit(ItemStack stack, boolean lit) {
        stack.getOrCreateTagElement("BlockStateTag").putString(SimulationLanternBlock.LIT.getName(),
                Boolean.toString(lit));
    }
    public static ItemStack stack(boolean lit) {
        ItemStack stack = new ItemStack(ITEM.get());
        setLit(stack, lit);
        return stack;
    }
}
