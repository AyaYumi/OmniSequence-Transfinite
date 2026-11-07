package com.atir.molecularmanipulator.integration.jei;

import com.atir.molecularmanipulator.building.BuildingPackage;
import com.atir.molecularmanipulator.building.BuildingType;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public record SingularityStructureJeiRecipe(List<ItemStack> materials, ItemStack output) {
    public static SingularityStructureJeiRecipe recipe() {
        var type = BuildingType.EVENT_HORIZON_SINGULARITY;
        return new SingularityStructureJeiRecipe(type.materials().entrySet().stream()
                .map(entry -> entry.getKey().toStack(Math.toIntExact(entry.getValue()))).toList(), BuildingPackage.fresh(type));
    }
}
