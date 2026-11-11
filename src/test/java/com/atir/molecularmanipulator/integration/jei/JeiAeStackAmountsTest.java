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
import tamaized.ae2jeiintegration.api.integrations.jei.IngredientConverter;

class JeiAeStackAmountsTest {
    static {
        if (net.neoforged.fml.loading.LoadingModList.get() == null) {
            net.neoforged.fml.loading.LoadingModList.of(List.of(), List.of(), List.of(), List.of(), java.util.Map.of());
        }
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    @Test
    void nativeKeyOnlyConverterKeepsTypeKeyAndRecipeQuantity() {
        var type = new IIngredientType<AEKey>() {
            @Override public Class<? extends AEKey> getIngredientClass() { return AEKey.class; }
        };
        var converter = new IngredientConverter<AEKey>() {
            @Override public IIngredientType<AEKey> getIngredientType() { return type; }
            @Override public AEKey getIngredientFromStack(GenericStack stack) { return stack.what(); }
            @Override public GenericStack getStackFromIngredient(AEKey key) { return new GenericStack(key, 1); }
        };
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
    void researchHoverPassesRealItemAndFluidIngredientsWithTheVisibleRowAreaToJei() {
        var area=new net.minecraft.client.renderer.Rect2i(200,150,166,4);
        Object[][] received={null};
        var builder=Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{mezz.jei.api.gui.builder.IClickableIngredientFactory.IBuilder.class},(proxy,method,args)->{
                    assertSame(area,args[0],"JEI must receive the clipped screen-space hit area");
                    return Optional.empty();
                });
        var factory=(mezz.jei.api.gui.builder.IClickableIngredientFactory)Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{mezz.jei.api.gui.builder.IClickableIngredientFactory.class},(proxy,method,args)->{
                    received[0]=args;return builder;
                });
        var named=new net.minecraft.world.item.ItemStack(Items.DIAMOND);
        named.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("Research sample"));
        var item=AEItemKey.of(named);
        MatterFabricationJeiIngredients.clickable(factory,new appeng.client.gui.StackWithBounds(new GenericStack(item,Long.MAX_VALUE),area));
        assertEquals(1,received[0].length);
        var stack=(net.minecraft.world.item.ItemStack)received[0][0];
        assertEquals(1,stack.getCount());assertEquals(item,AEItemKey.of(stack),"Keep the item's components when bookmarking");
        var fluid=appeng.api.stacks.AEFluidKey.of(net.minecraft.world.level.material.Fluids.WATER);
        MatterFabricationJeiIngredients.clickable(factory,new appeng.client.gui.StackWithBounds(new GenericStack(fluid,Long.MAX_VALUE),area));
        assertSame(mezz.jei.api.neoforge.NeoForgeTypes.FLUID_STACK,received[0][0]);
        var fluidStack=(net.neoforged.neoforge.fluids.FluidStack)received[0][1];
        assertEquals(fluid,appeng.api.stacks.AEFluidKey.of(fluidStack));assertEquals(1000,fluidStack.getAmount());
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

    private static IRecipeSlotView slot(String name) {
        return (IRecipeSlotView) Proxy.newProxyInstance(JeiAeStackAmountsTest.class.getClassLoader(),
                new Class<?>[] { IRecipeSlotView.class }, (proxy, method, args) -> {
                    if (method.getName().equals("getSlotName")) return Optional.ofNullable(name);
                    throw new UnsupportedOperationException(method.getName());
                });
    }
}
