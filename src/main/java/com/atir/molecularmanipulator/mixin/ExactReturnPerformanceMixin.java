package com.atir.molecularmanipulator.mixin;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEKey;
import appeng.crafting.execution.CraftingCpuLogic;
import com.appliedenhancements.api.AelisExactCraftingCpu;
import com.atir.molecularmanipulator.config.ModConfig;
import com.atir.molecularmanipulator.crafting.OmniExactReturnProfiler;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.LinkedHashSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

/** Coalesces observers, never inventory transactions, within one synchronous insertion. */
@Mixin(value = CraftingCpuLogic.class, remap = false, priority = 800)
public abstract class ExactReturnPerformanceMixin implements com.atir.molecularmanipulator.crafting.OmniExactReturnTiming {
    @Override public boolean omnisequence$isProfilingReturn() { return omnisequence$profile && omnisequence$returnScope; }
    @Override public void omnisequence$addReturnInventoryTime(long nanos) { omnisequence$inventoryNanos += nanos; }
    @Override public void omnisequence$addReturnAccountingTime(long nanos) { omnisequence$accountingNanos += nanos; }
    @Shadow private void postChange(AEKey key) { throw new AssertionError(); }
    @Unique private boolean omnisequence$returnScope, omnisequence$coalesce, omnisequence$profile;
    @Unique private boolean omnisequence$flushing;
    @Unique private AEKey omnisequence$firstChanged;
    @Unique private final LinkedHashSet<AEKey> omnisequence$otherChanged = new LinkedHashSet<>();
    @Unique private long omnisequence$accountingNanos, omnisequence$inventoryNanos, omnisequence$notificationNanos;
    @Unique private long omnisequence$notificationsRequested, omnisequence$notificationsSent;
    @Unique private int omnisequence$sampleSequence;

    @WrapMethod(method = "insert")
    private long omnisequence$batchReturnNotifications(AEKey key, long amount, Actionable mode, Operation<Long> original) {
        if (omnisequence$returnScope || omnisequence$flushing || mode != Actionable.MODULATE || amount <= 0
                || ((AelisExactCraftingCpu) this).aelis$getRemainingOutput() == null) {
            return original.call(key, amount, mode);
        }
        omnisequence$coalesce = ModConfig.OMNI_COALESCE_RETURN_NOTIFICATIONS.get();
        boolean profiling = ModConfig.OMNI_PROFILE_EXACT_RETURNS.get();
        omnisequence$profile = profiling && ++omnisequence$sampleSequence
                % ModConfig.OMNI_RETURN_PROFILE_SAMPLE_INTERVAL.get() == 0;
        if (profiling && !omnisequence$profile) OmniExactReturnProfiler.recordUnprofiled();
        if (!omnisequence$coalesce && !omnisequence$profile) return original.call(key, amount, mode);
        omnisequence$returnScope = true;
        omnisequence$firstChanged = null;
        omnisequence$otherChanged.clear();
        omnisequence$accountingNanos = omnisequence$inventoryNanos = omnisequence$notificationNanos = 0;
        omnisequence$notificationsRequested = omnisequence$notificationsSent = 0;
        long start = omnisequence$profile ? System.nanoTime() : 0;
        try {
            return original.call(key, amount, mode);
        } finally {
            // All ledgers and job completion are settled before observers run.
            // Reentrant insertions from an observer use the original immediate path.
            omnisequence$returnScope = false;
            omnisequence$flushing = true;
            long notifyStart = omnisequence$profile ? System.nanoTime() : 0;
            try {
                if (omnisequence$firstChanged != null) {
                    postChange(omnisequence$firstChanged); omnisequence$notificationsSent++;
                }
                for (var changed : omnisequence$otherChanged) {
                    postChange(changed); omnisequence$notificationsSent++;
                }
            } finally {
                omnisequence$flushing = false;
                if (omnisequence$profile) {
                    omnisequence$notificationNanos += System.nanoTime() - notifyStart;
                    OmniExactReturnProfiler.record(System.nanoTime() - start, omnisequence$accountingNanos,
                            omnisequence$inventoryNanos, omnisequence$notificationNanos,
                            omnisequence$notificationsRequested, omnisequence$notificationsSent);
                }
                omnisequence$profile = false;
                omnisequence$firstChanged = null; omnisequence$otherChanged.clear();
            }
        }
    }

    @WrapMethod(method = "postChange")
    private void omnisequence$coalesceChangedKey(AEKey key, Operation<Void> original) {
        if (!omnisequence$returnScope) { original.call(key); return; }
        omnisequence$notificationsRequested++;
        if (omnisequence$coalesce) {
            if (omnisequence$firstChanged == null) omnisequence$firstChanged = key;
            else if (!omnisequence$firstChanged.equals(key)) omnisequence$otherChanged.add(key);
            return;
        }
        long start = omnisequence$profile ? System.nanoTime() : 0;
        try { original.call(key); } finally {
            omnisequence$notificationsSent++;
            if (omnisequence$profile) omnisequence$notificationNanos += System.nanoTime() - start;
        }
    }

}
