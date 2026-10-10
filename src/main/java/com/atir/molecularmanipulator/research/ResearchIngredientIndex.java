package com.atir.molecularmanipulator.research;

import appeng.api.stacks.AEItemKey;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Ingredient;

/** Index ordinary item/tag costs by item; custom ingredients retain their actual predicate. */
final class ResearchIngredientIndex {
    private final List<MatterResearchRecipe.Cost> costs;
    private final Map<Item, BitSet> ordinary = new HashMap<>();
    private final List<Integer> custom = new ArrayList<>();
    ResearchIngredientIndex(List<MatterResearchRecipe.Cost> costs) {
        this.costs = costs;
        for (int row = 0; row < costs.size(); row++) {
            Ingredient ingredient = costs.get(row).ingredient();
            if (!ingredient.isSimple()) { custom.add(row); continue; }
            for (var example : ingredient.getItems()) ordinary.computeIfAbsent(example.getItem(), ignored -> new BitSet()).set(row);
        }
    }
    static Map<Item, List<AEItemKey>> indexStock(Map<AEItemKey, Long> stock) {
        var indexed = new HashMap<Item, List<AEItemKey>>();
        stock.forEach((key, amount) -> { if (amount > 0) indexed.computeIfAbsent(key.getItem(), ignored -> new ArrayList<>()).add(key); });
        return indexed;
    }
    Map<AEItemKey, BitSet> matches(Map<AEItemKey, Long> stock, Map<Item, List<AEItemKey>> indexed) {
        if (!custom.isEmpty()) return matches(stock);
        var result = new LinkedHashMap<AEItemKey, BitSet>();
        ordinary.forEach((item, rows) -> {
            for (var key : indexed.getOrDefault(item, List.of())) {
                if (stock.getOrDefault(key, 0L) > 0) result.put(key, rows);
            }
        });
        return result;
    }
    Map<AEItemKey, BitSet> matches(Map<AEItemKey, Long> stock) {
        var result = new LinkedHashMap<AEItemKey, BitSet>();
        for (var entry : stock.entrySet()) {
            if (entry.getValue() <= 0) continue;
            var key = entry.getKey();
            var rows = ordinary.get(key.getItem());
            BitSet accepted = rows == null ? new BitSet() : (BitSet) rows.clone();
            if (!custom.isEmpty()) {
                var stack = key.toStack();
                for (int row : custom) if (costs.get(row).ingredient().test(stack)) accepted.set(row);
            }
            if (!accepted.isEmpty()) result.put(key, accepted);
        }
        return result;
    }
}
