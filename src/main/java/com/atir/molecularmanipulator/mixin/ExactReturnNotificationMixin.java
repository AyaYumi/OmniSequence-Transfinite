package com.atir.molecularmanipulator.mixin;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEKey;
import appeng.crafting.execution.CraftingCpuLogic;
import com.appliedenhancements.api.AelisExactCraftingCpu;
import com.atir.molecularmanipulator.config.ModConfig;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.LinkedHashSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

/** Coalesces observers, never inventory transactions, within one synchronous insertion. */
@Mixin(value = CraftingCpuLogic.class, remap = false, priority = 800)
public abstract class ExactReturnNotificationMixin {
    @Shadow private void postChange(AEKey key) { throw new AssertionError(); }
    @Unique private boolean omnisequence$returnScope, omnisequence$coalesce;
    @Unique private boolean omnisequence$flushing;
    @Unique private AEKey omnisequence$firstChanged;
    @Unique private final LinkedHashSet<AEKey> omnisequence$otherChanged = new LinkedHashSet<>();

    @WrapMethod(method = "insert")
    private long omnisequence$batchReturnNotifications(AEKey key, long amount, Actionable mode, Operation<Long> original) {
        if (omnisequence$returnScope || omnisequence$flushing || mode != Actionable.MODULATE || amount <= 0
                || ((AelisExactCraftingCpu) this).aelis$getRemainingOutput() == null) {
            return original.call(key, amount, mode);
        }
        omnisequence$coalesce = ModConfig.OMNI_COALESCE_RETURN_NOTIFICATIONS.get();
        if (!omnisequence$coalesce) return original.call(key, amount, mode);
        omnisequence$returnScope = true;
        omnisequence$firstChanged = null;
        omnisequence$otherChanged.clear();
        try {
            return original.call(key, amount, mode);
        } finally {
            // All ledgers and job completion are settled before observers run.
            // Reentrant insertions from an observer use the original immediate path.
            omnisequence$returnScope = false;
            omnisequence$flushing = true;
            try {
                if (omnisequence$firstChanged != null) {
                    postChange(omnisequence$firstChanged);
                }
                for (var changed : omnisequence$otherChanged) {
                    postChange(changed);
                }
            } finally {
                omnisequence$flushing = false;
                omnisequence$firstChanged = null; omnisequence$otherChanged.clear();
            }
        }
    }

    @WrapMethod(method = "postChange")
    private void omnisequence$coalesceChangedKey(AEKey key, Operation<Void> original) {
        if (!omnisequence$returnScope) { original.call(key); return; }
        if (omnisequence$coalesce) {
            if (omnisequence$firstChanged == null) omnisequence$firstChanged = key;
            else if (!omnisequence$firstChanged.equals(key)) omnisequence$otherChanged.add(key);
            return;
        }
        original.call(key);
    }

}
