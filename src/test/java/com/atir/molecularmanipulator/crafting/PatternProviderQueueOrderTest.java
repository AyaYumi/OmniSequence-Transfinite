package com.atir.molecularmanipulator.crafting;

import static org.junit.jupiter.api.Assertions.*;

import appeng.api.config.Actionable;
import appeng.api.stacks.*;
import appeng.helpers.patternprovider.PatternProviderTarget;
import com.atir.molecularmanipulator.mixin.PatternProviderLogicMixin;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

class PatternProviderQueueOrderTest {
    static {
        if (net.neoforged.fml.loading.LoadingModList.get() == null) {
            net.neoforged.fml.loading.LoadingModList.of(List.of(), List.of(), List.of(), List.of(), java.util.Map.of());
        }
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    private static final class Provider extends PatternProviderLogicMixin {
        @Override public void saveChanges() {}
    }

    private static Field field(String name) throws Exception {
        var field = PatternProviderLogicMixin.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static Method method(String name, Class<?>... parameters) throws Exception {
        var method = PatternProviderLogicMixin.class.getDeclaredMethod(name, parameters);
        method.setAccessible(true);
        return method;
    }

    @Test void retriesKeepFirstQueuedTypeWhenTargetAcceptsOnlyOneType() throws Exception {
        var a = AEItemKey.of(Items.COAL);
        var b = AEItemKey.of(Items.IRON_INGOT);
        var c = AEItemKey.of(Items.GOLD_INGOT);
        var queue = new ArrayList<>(List.of(new GenericStack(b, 2), new GenericStack(c, 2), new GenericStack(a, 2)));
        var provider = new Provider();
        field("sendList").set(provider, queue);
        var delivered = new ArrayList<AEKey>();
        var target = new PatternProviderTarget() {
            AEKey occupyingType;
            @Override public long insert(AEKey key, long amount, Actionable mode) {
                if (occupyingType != null && !occupyingType.equals(key)) return 0;
                if (mode == Actionable.MODULATE) { occupyingType = key; delivered.add(key); }
                return amount;
            }
            @Override public boolean containsPatternInput(Set<AEKey> keys) { return false; }
        };
        var seed = method("molecularmanipulator$seedEveryQueuedIngredient", PatternProviderTarget.class);
        for (int attempt = 0; attempt < 6; attempt++) {
            target.occupyingType = null;
            seed.invoke(provider, target);
            if (attempt == 0) {
                // Resume with the exact saved remainder order, as after an unload/reload.
                queue = new ArrayList<>(queue);
                provider = new Provider();
                field("sendList").set(provider, queue);
            }
        }
        assertEquals(List.of(b, b, c, c, a, a), delivered);
        assertTrue(queue.isEmpty());
    }

    @Test void ordinaryQueuesUseNativeRetryWithoutFairBatchInterception() throws Exception {
        var queue = new ArrayList<>(List.of(new GenericStack(AEItemKey.of(Items.COAL), 1)));
        var provider = new Provider();
        field("sendList").set(provider, queue);
        var callback = new CallbackInfoReturnable<Boolean>("sendStacksOut", true);
        method("molecularmanipulator$sendEveryIngredientFairly", CallbackInfoReturnable.class).invoke(provider, callback);
        assertFalse(callback.isCancelled());
        assertEquals(1, queue.size());
    }
}
