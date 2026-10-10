package com.atir.molecularmanipulator.integration.jei;

import static org.junit.jupiter.api.Assertions.*;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Optional;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.ingredients.IIngredientType;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

class JeiAeStackAmountsTest {
    static {
        if (net.minecraftforge.fml.loading.LoadingModList.get() == null) {
            net.minecraftforge.fml.loading.LoadingModList.of(List.of(), List.of(), null);
        }
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    @Test
    void nativeKeyOnlyConverterKeepsTypeKeyAndRecipeQuantity() throws Exception {
        var type = new IIngredientType<AEKey>() {
            @Override public Class<? extends AEKey> getIngredientClass() { return AEKey.class; }
        };
        var converter = new TestConverter(type);
        // This models the lightning converter: the ingredient carries the key, never the count.
        for (var key : List.of(AEItemKey.of(Items.DIAMOND), AEItemKey.of(Items.EMERALD))) {
            for (long amount : new long[] {1, 4, 128, 4096, Long.MAX_VALUE}) {
                String[] name = {null};
                Object[] ingredient = {null};
                var slotBuilder = (IRecipeSlotBuilder) Proxy.newProxyInstance(getClass().getClassLoader(),
                        new Class<?>[] { IRecipeSlotBuilder.class }, (proxy, method, args) -> {
                            if (method.getName().equals("setSlotName")) name[0] = (String) args[0];
                            if (method.getName().equals("addIngredient")) {
                                assertSame(type, args[0]);
                                ingredient[0] = args[1];
                            }
                            return proxy;
                        });
                assertTrue(MatterFabricationJeiIngredients.addWithConverter(slotBuilder, new GenericStack(key, amount), converter));
                assertSame(key, ingredient[0]);
                var slot = slot(name[0]);
                var converted = converter.getStackFromIngredient((AEKey) ingredient[0]);
                assertEquals(1, converted.amount());
                var restored = JeiAeStackAmounts.restore(slot, converted);
                assertSame(key, restored.what());
                assertEquals(amount, restored.amount());
                assertEquals(amount, JeiAeStackAmounts.amount(slot, 1));
            }
        }
    }

    @Test
    void unrelatedSlotsAndInvalidMetadataRetainTheOriginalQuantity() {
        var stack = new GenericStack(AEItemKey.of(Items.DIAMOND), 16);
        for (var name : new String[] {"", "othermod:amount/128", "molecularmanipulator:ae_stack_amount/0",
                "molecularmanipulator:ae_stack_amount/-4", "molecularmanipulator:ae_stack_amount/not_a_number",
                "molecularmanipulator:ae_stack_amount/9223372036854775808"}) {
            assertSame(stack, JeiAeStackAmounts.restore(slot(name), stack));
            assertEquals(16, JeiAeStackAmounts.amount(slot(name), 16));
        }
        assertSame(stack, JeiAeStackAmounts.restore(slot(null), stack));
        assertNull(JeiAeStackAmounts.restore(slot(JeiAeStackAmounts.slotName(128)), null));
    }

    public record TestConverter(IIngredientType<AEKey> type) {
        public IIngredientType<AEKey> getIngredientType() { return type; }
        public AEKey getIngredientFromStack(GenericStack stack) { return stack.what(); }
        public GenericStack getStackFromIngredient(AEKey key) { return new GenericStack(key, 1); }
    }

    private static IRecipeSlotView slot(String name) {
        return (IRecipeSlotView) Proxy.newProxyInstance(JeiAeStackAmountsTest.class.getClassLoader(),
                new Class<?>[] { IRecipeSlotView.class }, (proxy, method, args) -> {
                    if (method.getName().equals("getSlotName")) return Optional.ofNullable(name);
                    throw new UnsupportedOperationException(method.getName());
                });
    }
}
