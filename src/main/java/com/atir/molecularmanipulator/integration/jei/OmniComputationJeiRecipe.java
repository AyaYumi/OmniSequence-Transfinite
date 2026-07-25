package com.atir.molecularmanipulator.integration.jei;

import net.minecraft.world.item.ItemStack;

import java.util.List;

public record OmniComputationJeiRecipe(List<ItemStack> materials, ItemStack controller) {
}
