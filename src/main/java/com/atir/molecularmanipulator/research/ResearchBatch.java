package com.atir.molecularmanipulator.research;

import com.mojang.serialization.JsonOps;
import java.util.LinkedHashMap;
import java.util.List;
import net.minecraft.world.item.crafting.Ingredient;

/** Remaining research rounds use their actual replacement costs and checked long totals. */
public final class ResearchBatch {
    private ResearchBatch() {}
    public static List<MatterResearchRecipe.Cost> costs(MatterResearchRecipe definition, int first, int last) {
        if (first < 1 || last < first || last > definition.depths().size()) throw new IllegalArgumentException("Invalid research range");
        var totals = new LinkedHashMap<String, MatterResearchRecipe.Cost>();
        for (int round = first; round <= last; round++) for (var cost : definition.costsFor(round)) {
            String key = Ingredient.CODEC.encodeStart(JsonOps.INSTANCE, cost.ingredient()).getOrThrow().toString();
            totals.merge(key, cost, (a, b) -> new MatterResearchRecipe.Cost(a.ingredient(), Math.addExact(a.count(), b.count())));
        }
        return List.copyOf(totals.values());
    }
}
