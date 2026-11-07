package com.atir.molecularmanipulator.network;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Menu-owned receiver. Publish complete snapshots and create display stacks once, never per frame. */
public final class SingularityCollectionState {
    private long generation = -1;
    private int total;
    private boolean complete;
    private final List<Item> pending = new ArrayList<>();
    private List<ItemStack> resources = List.of();

    public boolean accept(SingularityCollectionPayload chunk) {
        if (chunk.generation() < generation) return false;
        if (chunk.offset() == 0) {
            if (chunk.generation() == generation) return false;
            generation = chunk.generation();
            total = chunk.total();
            complete = false;
            pending.clear();
        } else if (chunk.generation() != generation) return false;
        if (complete || chunk.total() != total || chunk.offset() != pending.size()) return false;
        pending.addAll(chunk.items());
        if (pending.size() == total) {
            resources = pending.stream().map(ItemStack::new).filter(stack -> !stack.isEmpty()).toList();
            pending.clear();
            complete = true;
        }
        return true;
    }

    /** Stable list identity until a different snapshot completes. Used directly by rendering and scrolling. */
    public List<ItemStack> resources() { return resources; }
}
