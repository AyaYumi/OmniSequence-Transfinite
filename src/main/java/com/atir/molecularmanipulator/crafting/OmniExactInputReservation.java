package com.atir.molecularmanipulator.crafting;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.inv.ICraftingInventory;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Reserves extra finite materials beyond the one prototype already extracted by AE2. */
public final class OmniExactInputReservation implements AutoCloseable {
    private final OmniExactInventory stock;
    private final ICraftingInventory window;
    private final Map<AEKey, BigInteger> reserved;
    private final Map<AEKey, BigInteger> unitInputs;
    private final BigInteger requestedCount;
    private boolean closed;

    private OmniExactInputReservation(OmniExactInventory stock, ICraftingInventory window,
            Map<AEKey, BigInteger> reserved, Map<AEKey, BigInteger> unitInputs,
            BigInteger requestedCount) {
        this.stock = stock;
        this.window = window;
        this.reserved = reserved;
        this.unitInputs = unitInputs;
        this.requestedCount = requestedCount;
    }

    public static Map<AEKey, BigInteger> unitInputs(KeyCounter[] prototype, Set<AEKey> infinite) {
        if (prototype == null || prototype.length == 0) throw new IllegalArgumentException("Missing prototype");
        var result = new LinkedHashMap<AEKey, BigInteger>();
        boolean found = false;
        for (var slot : prototype) {
            if (slot == null) throw new IllegalArgumentException("Missing prototype slot");
            for (var entry : slot) {
                if (entry.getKey() == null || entry.getLongValue() <= 0) throw new IllegalArgumentException("Invalid prototype");
                found = true;
                if (!infinite.contains(entry.getKey())) result.merge(entry.getKey(),
                        BigInteger.valueOf(entry.getLongValue()), BigInteger::add);
            }
        }
        if (!found) throw new IllegalArgumentException("Empty prototype");
        return result;
    }

    public static BigInteger maximum(OmniExactInventory stock, ICraftingInventory window,
            Map<AEKey, BigInteger> unitInputs, BigInteger requested) {
        var limit = requested;
        for (var unit : unitInputs.entrySet()) {
            limit = limit.min(stock.amount(window, unit.getKey()).divide(unit.getValue()).add(BigInteger.ONE));
        }
        return limit;
    }

    public static OmniExactInputReservation reserve(OmniExactInventory stock, ICraftingInventory window,
            Map<AEKey, BigInteger> unitInputs, BigInteger count) {
        if (count.signum() <= 0 || maximum(stock, window, unitInputs, count).compareTo(count) < 0) return null;
        var extra = count.subtract(BigInteger.ONE);
        var amounts = new LinkedHashMap<AEKey, BigInteger>();
        unitInputs.forEach((key, unit) -> { if (extra.signum() > 0) amounts.put(key, unit.multiply(extra)); });
        var result = new OmniExactInputReservation(stock, window, amounts,
                new LinkedHashMap<>(unitInputs), count);
        for (var entry : amounts.entrySet()) {
            stock.extract(window, entry.getKey(), entry.getValue(), Actionable.MODULATE);
        }
        return result;
    }

    public void commit() { closed = true; }

    /** Commit only the accepted prefix and return the unused reservation. */
    public void commit(BigInteger acceptedCount) {
        if (closed) return;
        if (acceptedCount == null || acceptedCount.signum() <= 0) {
            close();
            return;
        }
        if (acceptedCount.compareTo(requestedCount) >= 0) {
            closed = true;
            return;
        }
        BigInteger unused = requestedCount.subtract(acceptedCount);
        unitInputs.forEach((key, unit) -> {
            if (unit.signum() > 0) {
                stock.insert(window, key, unit.multiply(unused));
            }
        });
        closed = true;
    }
    @Override public void close() {
        if (closed) return;
        closed = true;
        reserved.forEach((key, amount) -> stock.insert(window, key, amount));
    }
}
