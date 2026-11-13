package com.atir.molecularmanipulator.client;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

/** Selection-derived order data. No localized text or live research progress is retained here. */
final class MatterResearchOrders {
    static final View EMPTY = new View(List.of(), List.of(), 0, 0, 0, "idle");

    private List<Entry> source;
    private ResourceLocation selection;
    private View cached = EMPTY;

    /** The panel replaces the immutable source only when its order items actually change. */
    View forSelection(List<Entry> entries, ResourceLocation selected) {
        if (source == entries && Objects.equals(selection, selected)) return cached;
        var filtered = new ArrayList<Entry>();
        var rows = new ArrayList<Row>();
        var missingKeys = new HashSet<AEKey>();
        int queued = 0, active = 0;
        for (var entry : entries) {
            if (entry.owner() != null && !entry.owner().equals(selected)) continue;
            filtered.add(entry);
            if (entry.queued() > 0) queued++;
            if (entry.active() > 0) active++;
            rows.add(new Row(entry.target(), entry.status(), false));
            for (var missing : entry.missing()) {
                rows.add(new Row(missing, entry.status(), true));
                missingKeys.add(missing.what());
            }
        }
        cached = new View(List.copyOf(filtered), List.copyOf(rows), missingKeys.size(), queued, active,
                filtered.isEmpty() ? "idle" : filtered.get(0).status());
        source = entries;
        selection = selected;
        return cached;
    }

    record Entry(GenericStack target, long queued, long active, String status,
                 List<GenericStack> missing, ResourceLocation owner) {
        Entry { missing = List.copyOf(missing); }
    }

    /** Target followed by its missing rows, in exactly the synchronized order. */
    record Row(GenericStack stack, String status, boolean missing) { }

    record View(List<Entry> entries, List<Row> rows, int missingCount, int queued, int active, String status) { }
}
