package com.atir.molecularmanipulator.crafting;

import appeng.api.stacks.KeyCounter;

public final class OmniCraftingSnapshotContext {
    private static final ThreadLocal<KeyCounter> SNAPSHOT = new ThreadLocal<>();

    private OmniCraftingSnapshotContext() {
    }

    public static void set(KeyCounter snapshot) {
        SNAPSHOT.set(snapshot);
    }

    public static KeyCounter get() {
        return SNAPSHOT.get();
    }

    public static void clear() {
        SNAPSHOT.remove();
    }
}
