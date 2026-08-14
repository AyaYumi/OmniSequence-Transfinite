package com.atir.molecularmanipulator.mixin;

import appeng.api.stacks.AEKey;
import appeng.crafting.CraftingTreeNode;
import appeng.crafting.CraftingTreeProcess;
import com.atir.molecularmanipulator.integration.ae2.OmniCraftingTreeProcessBridge;
import com.atir.molecularmanipulator.runtime.CraftingTreeOutputCountGuard;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Prevents malformed pattern candidates from becoming a divisor of zero in AE2. */
@Mixin(value = CraftingTreeNode.class, remap = false)
public abstract class CraftingTreeNodeOutputGuardMixin {
    @WrapOperation(method = "request", at = @At(value = "INVOKE",
            target = "Lappeng/crafting/CraftingTreeProcess;getOutputCount(Lappeng/api/stacks/AEKey;)J"))
    private long molecularmanipulator$guardOutputCount(
            CraftingTreeProcess process, AEKey requestedKey,
            Operation<Long> original) {
        long outputCount = original.call(process, requestedKey);
        return CraftingTreeOutputCountGuard.sanitize(outputCount, () ->
                ((OmniCraftingTreeProcessBridge) process)
                        .molecularmanipulator$setPossible(false));
    }
}
