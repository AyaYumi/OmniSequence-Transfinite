package com.atir.molecularmanipulator.crafting;

import appeng.api.config.Actionable;
import appeng.api.config.FuzzyMode;
import appeng.api.stacks.AEKey;
import appeng.crafting.inv.ICraftingInventory;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/** Execution-time view for inputs supplied by explicitly marked infinite cells. */
public final class OmniInfiniteCraftingInventory implements ICraftingInventory {
    private final ICraftingInventory delegate;
    private final Set<AEKey> infiniteKeys;

    public OmniInfiniteCraftingInventory(
            ICraftingInventory delegate, Set<AEKey> infiniteKeys) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.infiniteKeys = Set.copyOf(infiniteKeys);
    }

    @Override
    public void insert(AEKey what, long amount, Actionable mode) {
        if (!infiniteKeys.contains(what)) {
            delegate.insert(what, amount, mode);
        }
    }

    @Override
    public long extract(AEKey what, long amount, Actionable mode) {
        if (amount <= 0) {
            return 0;
        }
        return infiniteKeys.contains(what)
                ? amount
                : delegate.extract(what, amount, mode);
    }

    @Override
    public Iterable<AEKey> findFuzzyTemplates(AEKey input) {
        var result = new LinkedHashSet<AEKey>();
        delegate.findFuzzyTemplates(input).forEach(result::add);
        for (var key : infiniteKeys) {
            if (input.fuzzyEquals(key, FuzzyMode.IGNORE_ALL)) {
                result.add(key);
            }
        }
        return new ArrayList<>(result);
    }
}
