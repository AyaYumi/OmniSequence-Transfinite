package com.atir.molecularmanipulator.integration.eco;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.crafting.inv.ICraftingInventory;
import appeng.crafting.inv.ListCraftingInventory;
import com.atir.molecularmanipulator.crafting.OmniExactCraftingState;
import com.atir.molecularmanipulator.crafting.OmniExactInventory;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/** Shared physical/accounting code: this class and its complete dependency graph do not link ECO. */
public final class EcoCpuDispatchLedger {
    public static final String NBT_KEY = "molecularmanipulator:ecoDispatchHost";
    private static final BigInteger LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE);
    private final Map<AEKey, BigInteger> ordinaryCredits = new LinkedHashMap<>();
    // These are owned final products, separate from spendable crafting stock.
    private final Map<AEKey, BigInteger> finalStock = new LinkedHashMap<>();
    private final Map<AEKey, BigInteger> deliveryIntent = new LinkedHashMap<>();
    private Stage active;
    private String paused = "";
    private boolean exactOrder;
    private CompoundTag failedSnapshot;

    public boolean exactOrder() { return exactOrder; }
    public void exactOrder(boolean value) { exactOrder = value; }
    public boolean paused() { return !paused.isEmpty() || failedSnapshot != null || !deliveryIntent.isEmpty(); }
    public boolean staging() { return active != null; }
    public boolean hasFinalStock() { return !finalStock.isEmpty() || !deliveryIntent.isEmpty(); }
    public Map<AEKey, BigInteger> finalStock() { return Map.copyOf(finalStock); }
    public void pause(String reason) { paused = reason == null || reason.isEmpty() ? "Uncertain ECO ownership" : reason; }

    public BigInteger available(ListCraftingInventory waiting, OmniExactCraftingState state, AEKey key) {
        long exposed = waiting.list.get(key);
        if (exposed < 0) throw new IllegalStateException("Negative CPU waiting ledger");
        return BigInteger.valueOf(exposed).add(credit(state, key));
    }
    private BigInteger credit(OmniExactCraftingState state, AEKey key) {
        return state == null ? ordinaryCredits.getOrDefault(key, BigInteger.ZERO) : state.uncreditedOutput(key);
    }
    private void debitCredit(OmniExactCraftingState state, AEKey key, BigInteger amount) {
        if (state != null) state.consumeOutputCredit(key, amount);
        else subtract(ordinaryCredits, key, amount);
    }
    public void refill(ListCraftingInventory waiting, OmniExactCraftingState state, AEKey key) {
        long exposed = waiting.list.get(key);
        if (exposed < 0) throw new IllegalStateException("Negative CPU waiting ledger");
        long moved = credit(state, key).min(BigInteger.valueOf(Long.MAX_VALUE - exposed)).longValueExact();
        if (moved > 0) {
            debitCredit(state, key, BigInteger.valueOf(moved));
            waiting.list.set(key, exposed + moved);
        }
    }
    public void received(AEKey key) {
        if (active != null && active.amounts.containsKey(key)) active.received = true;
    }
    public void consume(ListCraftingInventory waiting, OmniExactCraftingState state, AEKey key, BigInteger amount) {
        if (amount.signum() <= 0 || available(waiting, state, key).compareTo(amount) < 0)
            throw new IllegalArgumentException("CPU output exceeds waiting ledger");
        received(key);
        withdraw(waiting, state, key, amount);
        refill(waiting, state, key);
        // KeyCounter.set(key, 0) retains a row. Completion uses physical emptiness,
        // so a fully consumed UUID output must remove its zero waiting entry.
        waiting.list.removeZeros();
        if (state != null) state.recordCompleted(key, amount);
    }
    private void withdraw(ListCraftingInventory waiting, OmniExactCraftingState state, AEKey key, BigInteger amount) {
        var fromCredit = amount.min(credit(state, key));
        debitCredit(state, key, fromCredit);
        long fromWindow = amount.subtract(fromCredit).longValueExact();
        if (fromWindow > 0) waiting.list.set(key, Math.subtractExact(waiting.list.get(key), fromWindow));
    }

    public EcoCpuDispatchHost.Accounting stage(ListCraftingInventory waiting, OmniExactCraftingState state,
            EcoCpuDispatchReceipt receipt, Runnable commit) {
        if (paused() || active != null) return null;
        var amounts = receipt.waiting();
        // Validate all keys before exposing any of this batch's waiting.
        amounts.keySet().forEach(key -> available(waiting, state, key));
        var stage = new Stage(waiting, state, amounts, commit);
        for (var entry : amounts.entrySet()) {
            if (state == null) ordinaryCredits.merge(entry.getKey(), entry.getValue(), BigInteger::add);
            else state.queueOutput(entry.getKey(), entry.getValue());
            refill(waiting, state, entry.getKey());
        }
        active = stage;
        return stage;
    }
    private final class Stage implements EcoCpuDispatchHost.Accounting {
        final ListCraftingInventory waiting;
        final OmniExactCraftingState state;
        final Map<AEKey, BigInteger> amounts;
        final Runnable commit;
        boolean received, settled;
        Stage(ListCraftingInventory waiting, OmniExactCraftingState state,
                Map<AEKey, BigInteger> amounts, Runnable commit) {
            this.waiting = waiting; this.state = state; this.amounts = amounts; this.commit = commit;
        }
        public void commit() {
            if (settled) return;
            // Never repeat a partially failed progress callback.
            settled = true;
            commit.run();
            active = null;
        }
        public void rollback() {
            if (settled) return;
            if (received) throw new IllegalStateException("Rejected ECO batch already returned owned output");
            for (var entry : amounts.entrySet()) {
                if (available(waiting, state, entry.getKey()).compareTo(entry.getValue()) < 0)
                    throw new IllegalStateException("Staged ECO waiting disappeared");
            }
            for (var entry : amounts.entrySet()) {
                withdraw(waiting, state, entry.getKey(), entry.getValue());
                refill(waiting, state, entry.getKey());
            }
            settled = true; active = null;
        }
        public void retain() {
            settled = true; active = null;
            pause("Uncertain ECO submission; staged waiting retained");
        }
    }

    /** Conservative exact input demand; also protects non-ECO callers of the shared CPU host. */
    public static BigInteger reservedInput(IPatternDetails pattern, BigInteger crafts, AEKey key) {
        var result = BigInteger.ZERO;
        if (crafts.signum() <= 0) return result;
        for (var input : pattern.getInputs()) {
            for (var possible : input.getPossibleInputs()) {
                if (key.equals(possible.what())) {
                    result = result.add(BigInteger.valueOf(possible.amount())
                            .multiply(BigInteger.valueOf(input.getMultiplier())).multiply(crafts));
                    break;
                }
            }
        }
        return result;
    }

    /** ALL keys, including the preview copy. No observers run inside the ownership mutation. */
    public static EcoCpuDispatchHost.Lease reserve(OmniExactInventory stock, ListCraftingInventory window,
            Map<AEKey, BigInteger> inputs) {
        if (inputs.isEmpty()) return null;
        var amounts = Map.copyOf(inputs);
        var silent = silent(window);
        for (var entry : amounts.entrySet()) {
            if (entry.getValue().signum() <= 0 || stock.amount(silent, entry.getKey()).compareTo(entry.getValue()) < 0)
                return null;
        }
        amounts.forEach((key, amount) -> stock.extract(silent, key, amount, Actionable.MODULATE));
        return new EcoCpuDispatchHost.Lease() {
            boolean settled;
            public void commit() { settled = true; }
            public void refund() {
                if (settled) return;
                settled = true;
                amounts.forEach((key, amount) -> stock.insertWithoutNotification(window, key, amount));
            }
        };
    }
    public static boolean refillPhysical(OmniExactInventory stock, ListCraftingInventory window) {
        return stock.refill(silent(window));
    }
    private static ICraftingInventory silent(ListCraftingInventory window) {
        return new ICraftingInventory() {
            public long extract(AEKey key, long amount, Actionable mode) {
                long taken = Math.min(Math.max(0, amount), window.list.get(key));
                if (mode == Actionable.MODULATE && taken > 0) window.list.set(key, window.list.get(key) - taken);
                return taken;
            }
            public void insert(AEKey key, long amount, Actionable mode) {
                if (mode == Actionable.MODULATE) window.list.set(key, Math.addExact(window.list.get(key), amount));
            }
            public Iterable<AEKey> findFuzzyTemplates(AEKey key) { return window.findFuzzyTemplates(key); }
        };
    }

    @FunctionalInterface public interface Destination { long insert(AEKey key, long amount, Actionable mode); }
    /** Capacity probe is bounded to one long window, even for an arbitrarily large source offer. */
    public BigInteger finalCapacity(AEKey key, BigInteger offered, BigInteger remaining, Destination destination) {
        // Do not reserve the same destination capacity repeatedly before the previous window drains.
        if (finalStock.getOrDefault(key, BigInteger.ZERO).signum() > 0) return BigInteger.ZERO;
        var limit = offered.min(remaining.subtract(finalStock.getOrDefault(key, BigInteger.ZERO)).max(BigInteger.ZERO))
                .min(LONG_MAX);
        if (limit.signum() == 0) return BigInteger.ZERO;
        long capacity = destination.insert(key, limit.longValueExact(), Actionable.SIMULATE);
        if (capacity < 0 || BigInteger.valueOf(capacity).compareTo(limit) > 0)
            throw new IllegalStateException("Invalid final-output capacity");
        return BigInteger.valueOf(capacity);
    }
    public void ownFinal(AEKey key, BigInteger amount) {
        if (amount.signum() <= 0) throw new IllegalArgumentException("Nonpositive final stock");
        finalStock.merge(key, amount, BigInteger::add);
    }
    /** One window per key per tick. Persist an intent BEFORE invoking foreign ownership code. */
    public void flushFinal(Destination destination, java.util.function.BiConsumer<AEKey, Long> delivered,
            Runnable dirty) {
        if (paused() || staging()) return;
        for (var key : List.copyOf(finalStock.keySet())) {
            long requested = finalStock.get(key).min(LONG_MAX).longValueExact();
            long capacity = destination.insert(key, requested, Actionable.SIMULATE);
            if (capacity < 0 || capacity > requested) throw new IllegalStateException("Invalid final-output capacity");
            if (capacity == 0) continue;
            var amount = BigInteger.valueOf(capacity);
            subtract(finalStock, key, amount);
            deliveryIntent.put(key, amount);
            dirty.run();
            long accepted = destination.insert(key, capacity, Actionable.MODULATE);
            if (accepted < 0 || accepted > capacity) throw new IllegalStateException("Invalid final-output acceptance");
            // A throwing destination leaves the intent intact and can never be automatically replayed.
            if (accepted < capacity) finalStock.merge(key, BigInteger.valueOf(capacity - accepted), BigInteger::add);
            if (accepted > 0) {
                deliveryIntent.put(key, BigInteger.valueOf(accepted));
                delivered.accept(key, accepted);
            }
            deliveryIntent.remove(key);
            dirty.run();
        }
    }
    public void refundFinal(java.util.function.BiConsumer<AEKey, BigInteger> store) {
        if (paused() || staging()) throw new IllegalStateException("Uncertain ECO stock cannot be refunded");
        finalStock.forEach(store);
        finalStock.clear(); ordinaryCredits.clear(); exactOrder = false;
    }
    private static void subtract(Map<AEKey, BigInteger> map, AEKey key, BigInteger amount) {
        var rest = map.getOrDefault(key, BigInteger.ZERO).subtract(amount);
        if (rest.signum() < 0) throw new IllegalStateException("CPU credit over-consumed");
        if (rest.signum() == 0) map.remove(key); else map.put(key, rest);
    }
    public CompoundTag write(HolderLookup.Provider registries) {
        if (failedSnapshot != null) return failedSnapshot.copy();
        var tag = new CompoundTag();
        tag.putInt("version", 1); tag.putBoolean("exactOrder", exactOrder); tag.putString("paused", paused);
        tag.put("credits", writeAmounts(ordinaryCredits, registries));
        tag.put("finalStock", writeAmounts(finalStock, registries));
        tag.put("deliveryIntent", writeAmounts(deliveryIntent, registries));
        if (active != null) tag.putBoolean("interruptedStage", true);
        return tag;
    }
    public void read(CompoundTag tag, HolderLookup.Provider registries) {
        active = null; failedSnapshot = null; paused = "";
        ordinaryCredits.clear(); finalStock.clear(); deliveryIntent.clear();
        exactOrder = tag.getBoolean("exactOrder");
        if (tag.isEmpty()) return;
        try {
            if (tag.getInt("version") != 1) throw new IllegalArgumentException("Unsupported dispatch host version");
            readAmounts(tag.getList("credits", Tag.TAG_COMPOUND), registries, ordinaryCredits);
            readAmounts(tag.getList("finalStock", Tag.TAG_COMPOUND), registries, finalStock);
            readAmounts(tag.getList("deliveryIntent", Tag.TAG_COMPOUND), registries, deliveryIntent);
            paused = tag.getString("paused");
            if (tag.getBoolean("interruptedStage")) pause("Interrupted ECO staged submission");
        } catch (RuntimeException failure) {
            failedSnapshot = tag.copy(); pause("Invalid ECO CPU accounting: " + failure);
        }
    }
    private static ListTag writeAmounts(Map<AEKey, BigInteger> amounts, HolderLookup.Provider registries) {
        var tags = new ListTag();
        amounts.forEach((key, amount) -> {
            var tag = key.toTagGeneric(registries); tag.putString("amount", amount.toString()); tags.add(tag);
        });
        return tags;
    }
    private static void readAmounts(ListTag tags, HolderLookup.Provider registries, Map<AEKey, BigInteger> amounts) {
        for (var value : tags) {
            var tag = (CompoundTag) value;
            var key = AEKey.fromTagGeneric(registries, tag);
            var amount = new BigInteger(tag.getString("amount"));
            if (key == null || amount.signum() <= 0 || amounts.putIfAbsent(key, amount) != null)
                throw new IllegalArgumentException("Invalid CPU output stock");
        }
    }
}
