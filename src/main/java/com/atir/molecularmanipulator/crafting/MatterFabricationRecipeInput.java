package com.atir.molecularmanipulator.crafting;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeInput;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.List;

public record MatterFabricationRecipeInput(List<ItemStack> stacks, FluidStack fluid) implements RecipeInput {
    public MatterFabricationRecipeInput {
        stacks = List.copyOf(stacks);
        fluid = fluid.copy();
    }

    public MatterFabricationRecipeInput(List<ItemStack> stacks) {
        this(stacks, FluidStack.EMPTY);
    }

    @Override
    public ItemStack getItem(int index) {
        return stacks.get(index);
    }

    @Override
    public int size() {
        return stacks.size();
    }
}
