package com.atir.molecularmanipulator.integration.jei;

import com.atir.molecularmanipulator.building.BuildingPackage;
import mezz.jei.api.ingredients.subtypes.ISubtypeInterpreter;
import mezz.jei.api.ingredients.subtypes.UidContext;
import net.minecraft.world.item.ItemStack;

/** Package lookups follow the stable building type, never individual archives or deployment receipts. */
final class FoldedBuildingPackageSubtypeInterpreter implements ISubtypeInterpreter<ItemStack> {
    static final FoldedBuildingPackageSubtypeInterpreter INSTANCE = new FoldedBuildingPackageSubtypeInterpreter();

    private FoldedBuildingPackageSubtypeInterpreter() { }

    @Override
    public String getSubtypeData(ItemStack stack, UidContext context) {
        var type = BuildingPackage.type(stack);
        return type == null ? null : type.id;
    }

    @Override
    public String getLegacyStringSubtypeInfo(ItemStack stack, UidContext context) {
        var data = getSubtypeData(stack, context);
        return data == null ? "" : data;
    }
}
