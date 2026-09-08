package com.atir.molecularmanipulator.network;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.inventories.InternalInventory;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Predicate;

/**
 * Builds the language-neutral part of the client search index. Display names
 * are intentionally resolved from the AE keys on the client so dedicated
 * servers do not force their language onto search results.
 */
public final class PatternSearchIndexBuilder {
    private static final int MAX_KEYS_PER_SIDE = 16;

    private PatternSearchIndexBuilder() {
    }

    public static List<PatternSearchIndexEntry> build(InternalInventory inventory, int slots,
            Level level, Predicate<ItemStack> supportedPattern) {
        int limit = Math.max(0, Math.min(slots, inventory.size()));
        var result = new ArrayList<PatternSearchIndexEntry>();
        for (int sourceSlot = 0; sourceSlot < limit; sourceSlot++) {
            ItemStack stack = inventory.getStackInSlot(sourceSlot);
            if (stack.isEmpty() || !supportedPattern.test(stack)) {
                continue;
            }

            try {
                var details = PatternDetailsHelper.decodePattern(stack, level);
                if (details == null) {
                    continue;
                }

                var inputs = new LinkedHashSet<AEKey>();
                for (var input : details.getInputs()) {
                    if (input == null || inputs.size() >= MAX_KEYS_PER_SIDE) {
                        continue;
                    }
                    for (var possible : input.getPossibleInputs()) {
                        if (possible != null) {
                            AEKey key = lightweightKey(possible.what());
                            if (key != null) {
                                inputs.add(key);
                            }
                        }
                        if (inputs.size() >= MAX_KEYS_PER_SIDE) {
                            break;
                        }
                    }
                }

                var outputs = new LinkedHashSet<AEKey>();
                for (var output : details.getOutputs()) {
                    if (output != null) {
                        AEKey key = lightweightKey(output.what());
                        if (key != null) {
                            outputs.add(key);
                        }
                    }
                    if (outputs.size() >= MAX_KEYS_PER_SIDE) {
                        break;
                    }
                }
                if (!inputs.isEmpty() || !outputs.isEmpty()) {
                    result.add(new PatternSearchIndexEntry(
                            sourceSlot,
                            List.copyOf(inputs),
                            List.copyOf(outputs)));
                }
            } catch (RuntimeException | LinkageError ignored) {
                // Invalid or temporarily undecodable patterns keep their normal
                // slot/tooltip behavior but are omitted from the search index.
            }
        }
        return List.copyOf(result);
    }

    public static List<PatternSearchIndexPayload> chunks(int containerId, long generation,
            List<PatternSearchIndexEntry> entries) {
        return PatternSearchIndexPayload.createChunks(containerId, generation, 0, entries);
    }

    private static AEKey lightweightKey(AEKey key) {
        return key instanceof AEItemKey || key instanceof AEFluidKey
                ? key.dropSecondary()
                : null;
    }
}
