package com.atir.molecularmanipulator.crafting;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeInput;

import java.util.List;

public record MatterFabricationRecipeInput(List<ItemStack> stacks) implements RecipeInput {
    public MatterFabricationRecipeInput {
        stacks = List.copyOf(stacks);
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
