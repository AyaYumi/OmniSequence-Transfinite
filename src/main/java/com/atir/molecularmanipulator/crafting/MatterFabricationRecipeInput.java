package com.atir.molecularmanipulator.crafting;

import java.util.List;
import java.util.stream.IntStream;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

/** Detached input snapshot for Forge's Container-based Recipe API. */
public final class MatterFabricationRecipeInput extends SimpleContainer {
    private final FluidStack fluid;
    public MatterFabricationRecipeInput(List<ItemStack> stacks, FluidStack fluid) {
        super(stacks.stream().map(ItemStack::copy).toArray(ItemStack[]::new));
        this.fluid = fluid.copy();
    }
    public MatterFabricationRecipeInput(List<ItemStack> stacks) { this(stacks, FluidStack.EMPTY); }
    public FluidStack fluid() { return fluid; }
    public int size() { return getContainerSize(); }
    public List<ItemStack> stacks() { return IntStream.range(0, size()).mapToObj(this::getItem).toList(); }
}
