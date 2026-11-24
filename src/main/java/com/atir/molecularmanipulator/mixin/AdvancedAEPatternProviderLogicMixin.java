package com.atir.molecularmanipulator.mixin;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.pattern.AEProcessingPattern;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Cancellable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "net.pedroksl.advanced_ae.common.logic.AdvPatternProviderLogic", remap = false)
public abstract class AdvancedAEPatternProviderLogicMixin {
    @WrapOperation(method = "pushPattern(Lappeng/api/crafting/IPatternDetails;[Lappeng/api/stacks/KeyCounter;)Z",
            at = @At(value = "INVOKE", target = "Lappeng/api/crafting/IPatternDetails;pushInputsToExternalInventory("
                    + "[Lappeng/api/stacks/KeyCounter;Lappeng/api/crafting/IPatternDetails$PatternInputSink;)V"),
            require = 0)
    private void molecularmanipulator$pushProcessingInputs(IPatternDetails pattern, KeyCounter[] inputHolder,
            IPatternDetails.PatternInputSink sink, Operation<Void> original,
            @Cancellable CallbackInfoReturnable<Boolean> pushResult) {
        if (pattern.getClass() != AEProcessingPattern.class) {
            original.call(pattern, inputHolder, sink);
            return;
        }

        var sparseInputs = ((AEProcessingPattern) pattern).getSparseInputs();
        int slotCount = sparseInputs.size();
        if (slotCount == pattern.getInputs().length) {
            original.call(pattern, inputHolder, sink);
            return;
        }

        // AE2 checks each sparse slot as it inserts it. Check every slot before the sink can write anything.
        var remaining = new KeyCounter();
        for (var counter : inputHolder) {
            remaining.addAll(counter);
        }
        for (int slot = 0; slot < slotCount; slot++) {
            var input = sparseInputs.get(slot);
            if (input == null) {
                continue;
            }
            var key = input.what();
            long amount = input.amount();
            if (remaining.get(key) < amount) {
                pushResult.setReturnValue(false);
                return;
            }
            remaining.remove(key, amount);
        }

        for (int slot = 0; slot < slotCount; slot++) {
            var input = sparseInputs.get(slot);
            if (input != null) {
                sink.pushInput(input.what(), input.amount());
            }
        }
    }
}
