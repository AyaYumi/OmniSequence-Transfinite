package com.atir.molecularmanipulator.integration.jei;

import com.atir.molecularmanipulator.building.BuildingPackage;
import com.atir.molecularmanipulator.building.BuildingType;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import net.minecraft.world.item.ItemStack;

/** A complete, fresh machine package, not an incremental ring upgrade or an archived machine. */
public record StarOrreryStructureJeiRecipe(BuildingType type) {
    public StarOrreryStructureJeiRecipe {
        if (!Objects.requireNonNull(type, "type").isStarOrrery() || type.orreryLevel() < 1) {
            throw new IllegalArgumentException("A star orrery building type is required");
        }
    }

    public static List<StarOrreryStructureJeiRecipe> recipes() {
        return BuildingType.orderable().stream()
                .filter(BuildingType::isStarOrrery)
                .sorted(Comparator.comparingInt(BuildingType::orreryLevel))
                .map(StarOrreryStructureJeiRecipe::new)
                .toList();
    }

    public List<ItemStack> materials() {
        // JEI's structure renderer displays the full bill of materials, including the core.
        // ItemStack can carry these display quantities; do not clamp them to inventory stack limits.
        return type.materials().entrySet().stream()
                .map(entry -> entry.getKey().toStack(Math.toIntExact(entry.getValue())))
                .toList();
    }

    public ItemStack output() {
        return BuildingPackage.fresh(type);
    }
}
