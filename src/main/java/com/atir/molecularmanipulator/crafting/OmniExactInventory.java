package com.atir.molecularmanipulator.crafting;

import appeng.api.config.Actionable;
import appeng.api.config.FuzzyMode;
import appeng.api.stacks.AEKey;
import appeng.crafting.inv.ICraftingInventory;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

/** Physical stock above AE2's long window. Independent of the job, including after cancel. */
public final class OmniExactInventory {
    private final Map<AEKey, BigInteger> overflow = new LinkedHashMap<>();

    public BigInteger amount(ICraftingInventory window, AEKey key) {
        return BigInteger.valueOf(window.extract(key, Long.MAX_VALUE, Actionable.SIMULATE))
                .add(overflow.getOrDefault(key, BigInteger.ZERO));
    }

    public Map<AEKey, BigInteger> overflow() { return Map.copyOf(overflow); }
    public boolean isEmpty() { return overflow.isEmpty(); }
    public void clear() { overflow.clear(); }

    public void insert(ICraftingInventory window, AEKey key, BigInteger amount) {
        if (amount.signum() < 0) throw new IllegalArgumentException("Negative exact stock");
        long stored = window.extract(key, Long.MAX_VALUE, Actionable.SIMULATE);
        long intoWindow = amount.min(BigInteger.valueOf(Long.MAX_VALUE - stored)).longValueExact();
        if (intoWindow > 0) window.insert(key, intoWindow, Actionable.MODULATE);
        var excess = amount.subtract(BigInteger.valueOf(intoWindow));
        if (excess.signum() > 0) overflow.merge(key, excess, BigInteger::add);
    }

    /** For a server-thread ownership transfer; the CPU notifies observers after both ledgers settle. */
    public void insertWithoutNotification(appeng.crafting.inv.ListCraftingInventory window, AEKey key, BigInteger amount) {
        if (amount.signum() < 0) throw new IllegalArgumentException("Negative exact stock");
        long stored = window.list.get(key);
        long intoWindow = amount.min(BigInteger.valueOf(Long.MAX_VALUE - stored)).longValueExact();
        if (intoWindow > 0) window.list.set(key, stored + intoWindow);
        var excess = amount.subtract(BigInteger.valueOf(intoWindow));
        if (excess.signum() > 0) overflow.merge(key, excess, BigInteger::add);
    }

    public BigInteger extract(ICraftingInventory window, AEKey key, BigInteger requested, Actionable mode) {
        if (requested.signum() < 0) throw new IllegalArgumentException("Negative exact extraction");
        var accepted = requested.min(amount(window, key));
        if (mode == Actionable.SIMULATE || accepted.signum() == 0) return accepted;
        var extra = overflow.getOrDefault(key, BigInteger.ZERO);
        var fromOverflow = extra.min(accepted);
        setOverflow(key, extra.subtract(fromOverflow));
        long fromWindow = accepted.subtract(fromOverflow).longValueExact();
        if (fromWindow > 0 && window.extract(key, fromWindow, Actionable.MODULATE) != fromWindow) {
            throw new IllegalStateException("Exact inventory changed during extraction");
        }
        return accepted;
    }

    /** Exposes a bounded refund window; the caller performs at most one storage pass per tick. */
    public boolean refill(ICraftingInventory window) {
        boolean changed = false;
        for (var key : java.util.List.copyOf(overflow.keySet())) {
            long stored = window.extract(key, Long.MAX_VALUE, Actionable.SIMULATE);
            var extra = overflow.get(key);
            long moved = extra.min(BigInteger.valueOf(Long.MAX_VALUE - stored)).longValueExact();
            if (moved > 0) {
                setOverflow(key, extra.subtract(BigInteger.valueOf(moved)));
                window.insert(key, moved, Actionable.MODULATE);
                changed = true;
            }
        }
        return changed;
    }

    private void setOverflow(AEKey key, BigInteger amount) {
        if (amount.signum() == 0) overflow.remove(key); else overflow.put(key, amount);
    }

    public ICraftingInventory wrap(ICraftingInventory window) {
        return wrap(window, key -> {});
    }

    public ICraftingInventory wrap(ICraftingInventory window, java.util.function.Consumer<AEKey> changed) {
        return new ICraftingInventory() {
            @Override public void insert(AEKey key, long amount, Actionable mode) {
                if (mode == Actionable.MODULATE) {
                    OmniExactInventory.this.insert(window, key, BigInteger.valueOf(amount));
                    if (amount > 0) changed.accept(key);
                }
            }
            @Override public long extract(AEKey key, long amount, Actionable mode) {
                if (amount <= 0) return 0;
                long accepted = OmniExactInventory.this.extract(window, key, BigInteger.valueOf(amount), mode).longValueExact();
                if (accepted > 0 && mode == Actionable.MODULATE) changed.accept(key);
                return accepted;
            }
            @Override public Iterable<AEKey> findFuzzyTemplates(AEKey key) {
                var keys = new LinkedHashSet<AEKey>();
                window.findFuzzyTemplates(key).forEach(keys::add);
                for (var candidate : overflow.keySet()) {
                    if (key.fuzzyEquals(candidate, FuzzyMode.IGNORE_ALL)) keys.add(candidate);
                }
                return keys;
            }
        };
    }

    public ListTag write(HolderLookup.Provider registries) {
        var result = new ListTag();
        overflow.forEach((key, amount) -> {
            var entry = key.toTagGeneric(registries);
            entry.putString("amount", amount.toString());
            result.add(entry);
        });
        return result;
    }

    public void read(ListTag entries, HolderLookup.Provider registries) {
        var restored = new LinkedHashMap<AEKey, BigInteger>();
        for (var tag : entries) {
            var entry = (CompoundTag) tag;
            var key = AEKey.fromTagGeneric(registries, entry);
            var amount = new BigInteger(entry.getString("amount"));
            if (key == null || amount.signum() <= 0 || restored.putIfAbsent(key, amount) != null) {
                throw new IllegalArgumentException("Invalid exact inventory stock");
            }
        }
        overflow.clear(); overflow.putAll(restored);
    }
}
