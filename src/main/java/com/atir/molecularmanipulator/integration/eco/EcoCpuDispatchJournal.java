package com.atir.molecularmanipulator.integration.eco;

import appeng.api.stacks.AEKey;
import java.math.BigInteger;
import java.util.Map;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

/** An interrupted or indeterminate intent is never replayed or converted into refundable CPU stock. */
public final class EcoCpuDispatchJournal {
    private EcoCpuDispatchReceipt pending;
    private CompoundTag restored;
    private boolean suspended;
    private String reason = "";

    public boolean suspended() { return suspended; }
    public boolean pending() { return pending != null || restored != null; }
    public void arm(EcoCpuDispatchReceipt receipt) {
        if (suspended || pending()) throw new IllegalStateException("Unsettled ECO dispatch");
        pending = receipt;
    }
    public void complete() {
        if (suspended) throw new IllegalStateException("Cannot clear uncertain ECO ownership");
        pending = null;
    }
    public void suspend(String message) { suspended = true; reason = message; }

    public CompoundTag write(HolderLookup.Provider registries) {
        var result = new CompoundTag();
        result.putInt("version", 1);
        result.putBoolean("suspended", suspended);
        result.putString("reason", reason);
        if (restored != null) result.put("intent", restored.copy());
        else if (pending != null) {
            var intent = new CompoundTag();
            intent.putUUID("job", pending.jobId());
            intent.put("pattern", pending.pattern().getDefinition().toTagGeneric(registries));
            intent.putString("crafts", pending.crafts().toString());
            intent.putBoolean("exact", pending.exact());
            intent.put("inputs", amounts(pending.inputs(), registries));
            intent.put("outputs", amounts(pending.outputs(), registries));
            intent.put("remainders", amounts(pending.remainders(), registries));
            result.put("intent", intent);
        }
        return result;
    }
    public void read(CompoundTag tag) {
        if (tag.getInt("version") != 1) throw new IllegalArgumentException("Unsupported ECO dispatch version");
        pending = null;
        // Keep the original intent even if its keys cannot currently be decoded. No refund is inferred.
        restored = tag.contains("intent") ? tag.getCompound("intent").copy() : null;
        suspended = tag.getBoolean("suspended") || restored != null;
        reason = tag.getString("reason");
        if (restored != null && reason.isEmpty()) reason = "Interrupted ECO provider submission";
    }
    private static ListTag amounts(Map<AEKey, BigInteger> amounts, HolderLookup.Provider registries) {
        var result = new ListTag();
        amounts.forEach((key, amount) -> {
            var entry = new CompoundTag();
            entry.put("key", key.toTagGeneric(registries));
            entry.putString("amount", amount.toString());
            result.add(entry);
        });
        return result;
    }
}
