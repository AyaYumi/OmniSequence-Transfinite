package com.atir.molecularmanipulator.integration.jei;

import appeng.api.client.AEKeyRendering;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.client.gui.StackWithBounds;
import com.atir.molecularmanipulator.ae2.StarSignalKey;
import com.atir.molecularmanipulator.client.AEStackIcon;
import com.atir.molecularmanipulator.client.DisplayNumbers;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import mezz.jei.api.gui.builder.IClickableIngredientFactory;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.neoforge.NeoForgeTypes;
import mezz.jei.api.runtime.IClickableIngredient;
import mezz.jei.api.ingredients.IIngredientRenderer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.TooltipFlag;
import tamaized.ae2jeiintegration.api.integrations.jei.IngredientConverter;
import tamaized.ae2jeiintegration.api.integrations.jei.IngredientConverters;

/** Keeps addon AE keys in the same JEI ingredient type as their native recipe categories. */
final class MatterFabricationJeiIngredients {
    private MatterFabricationJeiIngredients() {}

    static void registerStarSignalConverter() {
        IngredientConverters.register(new IngredientConverter<StarSignalKey>() {
            @Override public mezz.jei.api.ingredients.IIngredientType<StarSignalKey> getIngredientType() {
                return StarSignalJeiIngredient.TYPE;
            }

            @Override public StarSignalKey getIngredientFromStack(GenericStack stack) {
                return stack.what() instanceof StarSignalKey signal ? signal : null;
            }

            @Override public GenericStack getStackFromIngredient(StarSignalKey ingredient) {
                return new GenericStack(ingredient, 1);
            }
        });
    }

    static boolean add(IRecipeSlotBuilder slot, GenericStack stack) {
        for (var converter : IngredientConverters.getConverters()) {
            if (addWithConverter(slot, stack, converter)) return true;
        }
        return false;
    }

    static <T> boolean addWithConverter(IRecipeSlotBuilder slot, GenericStack stack,
            IngredientConverter<T> converter) {
        T ingredient = converter.getIngredientFromStack(stack);
        if (ingredient == null) return false;
        var type = converter.getIngredientType();
        // Key-only ingredients (including lightning) cannot carry their recipe quantity.
        slot.setSlotName(JeiAeStackAmounts.slotName(stack.amount()));
        slot.addIngredient(type, ingredient);
        slot.setCustomRenderer(type, new KeyRenderer<>(stack));
        return true;
    }

    static Optional<? extends IClickableIngredient<?>> clickable(IClickableIngredientFactory factory, StackWithBounds stack) {
        var key=stack.stack().what();
        if(key instanceof AEItemKey item)
            return factory.createBuilder(item.toStack()).buildWithArea(stack.bounds());
        if(key instanceof AEFluidKey fluid)
            return factory.createBuilder(NeoForgeTypes.FLUID_STACK,fluid.toStack(1000))
                    .buildWithArea(stack.bounds());
        if(key instanceof StarSignalKey signal)
            return factory.createBuilder(StarSignalJeiIngredient.TYPE,signal).buildWithArea(stack.bounds());
        for(var converter:IngredientConverters.getConverters()) {
            var ingredient=clickableWithConverter(factory,stack,converter);
            if(ingredient.isPresent())return ingredient;
        }
        return Optional.empty();
    }

    private static <T> Optional<? extends IClickableIngredient<?>> clickableWithConverter(
            IClickableIngredientFactory factory, StackWithBounds stack, IngredientConverter<T> converter) {
        T ingredient=converter.getIngredientFromStack(stack.stack());
        return ingredient==null?Optional.empty():factory.createBuilder(converter.getIngredientType(),ingredient)
                .buildWithArea(stack.bounds());
    }

    private record KeyRenderer<T>(GenericStack stack) implements IIngredientRenderer<T> {
        @Override public void render(GuiGraphics graphics, T ingredient) {
            AEStackIcon.draw(graphics, stack, 0, 0);
        }

        @Override public List<Component> getTooltip(T ingredient, TooltipFlag flag) {
            var lines = new ArrayList<>(AEKeyRendering.getTooltip(stack.what()));
            lines.add(Component.translatable("gui.molecularmanipulator.jei_required_count",
                    DisplayNumbers.exact(stack.amount())));
            return lines;
        }
    }
}
