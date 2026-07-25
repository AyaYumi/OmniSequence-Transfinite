package com.atir.molecularmanipulator.mixin;

import appeng.api.stacks.AEKey;
import com.atir.molecularmanipulator.cache.PatternValidityCache;
import com.atir.molecularmanipulator.cache.RemainingKeyCache;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.ConcurrentHashMap;

@Mixin(targets = "appeng.crafting.pattern.AECraftingPattern$Input", remap = false)
public abstract class AECraftingPatternInputMixin {
    @Unique
    private static final int MOLECULARMANIPULATOR_MULTI_CACHE_LIMIT = 32;
    @Unique
    private volatile RemainingKeyCache molecularmanipulator$remainingKeyCache;
    @Unique
    private volatile PatternValidityCache molecularmanipulator$validityCache;
    @Unique
    private volatile ConcurrentHashMap<AEKey, RemainingKeyCache> molecularmanipulator$remainingKeyCaches;
    @Unique
    private volatile ConcurrentHashMap<AEKey, PatternValidityCache> molecularmanipulator$validityCaches;

    @Inject(method = "isValid", at = @At("HEAD"), cancellable = true)
    private void molecularmanipulator$reuseValidity(AEKey input, Level level,
            CallbackInfoReturnable<Boolean> callback) {
        var cache = molecularmanipulator$validityCache;
        if (cache != null && cache.level() == level
                && (cache.input() == input || cache.input().equals(input))) {
            callback.setReturnValue(cache.valid());
            return;
        }
        var caches = molecularmanipulator$validityCaches;
        if (caches != null) {
            cache = caches.get(input);
            if (cache != null && cache.level() == level) {
                molecularmanipulator$validityCache = cache;
                callback.setReturnValue(cache.valid());
            }
        }
    }

    @Inject(method = "isValid", at = @At("RETURN"))
    private void molecularmanipulator$cacheValidity(AEKey input, Level level,
            CallbackInfoReturnable<Boolean> callback) {
        var next = new PatternValidityCache(input, level, callback.getReturnValue());
        var previous = molecularmanipulator$validityCache;
        molecularmanipulator$validityCache = next;
        if (previous == null || previous.level() == level
                && (previous.input() == input || previous.input().equals(input))) {
            return;
        }
        var caches = molecularmanipulator$validityCaches;
        if (caches == null) {
            synchronized (this) {
                caches = molecularmanipulator$validityCaches;
                if (caches == null) {
                    caches = new ConcurrentHashMap<>();
                    caches.put(previous.input(), previous);
                    molecularmanipulator$validityCaches = caches;
                }
            }
        }
        molecularmanipulator$putBounded(caches, input, next);
    }

    @Inject(method = "getRemainingKey", at = @At("HEAD"), cancellable = true)
    private void molecularmanipulator$reuseRemainingKey(AEKey template,
            CallbackInfoReturnable<AEKey> callback) {
        var cache = molecularmanipulator$remainingKeyCache;
        if (cache != null && (cache.input() == template || cache.input().equals(template))) {
            callback.setReturnValue(cache.output());
            return;
        }
        var caches = molecularmanipulator$remainingKeyCaches;
        if (caches != null) {
            cache = caches.get(template);
            if (cache != null) {
                molecularmanipulator$remainingKeyCache = cache;
                callback.setReturnValue(cache.output());
            }
        }
    }

    @Inject(method = "getRemainingKey", at = @At("RETURN"))
    private void molecularmanipulator$cacheRemainingKey(AEKey template,
            CallbackInfoReturnable<AEKey> callback) {
        var next = new RemainingKeyCache(template, callback.getReturnValue());
        var previous = molecularmanipulator$remainingKeyCache;
        molecularmanipulator$remainingKeyCache = next;
        if (previous == null || previous.input() == template || previous.input().equals(template)) {
            return;
        }
        var caches = molecularmanipulator$remainingKeyCaches;
        if (caches == null) {
            synchronized (this) {
                caches = molecularmanipulator$remainingKeyCaches;
                if (caches == null) {
                    caches = new ConcurrentHashMap<>();
                    caches.put(previous.input(), previous);
                    molecularmanipulator$remainingKeyCaches = caches;
                }
            }
        }
        molecularmanipulator$putBounded(caches, template, next);
    }

    @Unique
    private static <T> void molecularmanipulator$putBounded(
            ConcurrentHashMap<AEKey, T> cache, AEKey key, T value) {
        if (cache.size() >= MOLECULARMANIPULATOR_MULTI_CACHE_LIMIT && !cache.containsKey(key)) {
            cache.clear();
        }
        cache.put(key, value);
    }

}
