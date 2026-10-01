package com.atir.molecularmanipulator.integration.jei;

import net.minecraft.world.item.ItemStack;

import java.util.List;

public record TaixuStructureJeiRecipe(List<ItemStack> materials, ItemStack controller) {
}
