package com.atir.molecularmanipulator.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = "com.lhy.ae2utility.card.NbtTearPatternContext", remap = false)
public abstract class NbtTearPatternContextMixin {
    @Redirect(method = "clear", at = @At(value = "INVOKE", target = "Ljava/lang/ThreadLocal;remove()V"), require = 0)
    private static void molecularmanipulator$keepThreadLocalSlot(ThreadLocal<?> threadLocal) {
        threadLocal.set(null);
    }
}
