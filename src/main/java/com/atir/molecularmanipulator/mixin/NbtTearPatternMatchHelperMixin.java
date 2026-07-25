package com.atir.molecularmanipulator.mixin;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import com.atir.molecularmanipulator.MolecularManipulator;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;

import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicBoolean;

@Pseudo
@Mixin(targets = "com.lhy.ae2utility.card.NbtTearPatternMatchHelper", remap = false)
public abstract class NbtTearPatternMatchHelperMixin {
    @Unique
    private static final AtomicBoolean MOLECULARMANIPULATOR_LOGGED_EMPTY_PROVIDER = new AtomicBoolean();

    @WrapMethod(method = {"matchesCraftingPatternInput", "matchesProcessingPatternInput"},
            require = 0, expect = 0)
    private static boolean molecularmanipulator$handleMissingPatternProvider(AEKey input,
            GenericStack template, Operation<Boolean> original) {
        try {
            return original.call(input, template);
        } catch (NoSuchElementException exception) {
            if (MOLECULARMANIPULATOR_LOGGED_EMPTY_PROVIDER.compareAndSet(false, true)) {
                MolecularManipulator.LOGGER.warn(
                        "AE2Utility NBT Tear queried a pattern without an available provider; using exact matching instead",
                        exception);
            }
            return input != null && template != null && input.matches(template);
        }
    }
}
