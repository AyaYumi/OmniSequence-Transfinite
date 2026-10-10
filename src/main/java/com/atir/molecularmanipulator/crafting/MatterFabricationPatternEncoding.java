package com.atir.molecularmanipulator.crafting;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.helpers.IPatternTerminalMenuHost;
import appeng.menu.AEBaseMenu;
import appeng.menu.SlotSemantics;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/** Shared encoding context; recipe quantities always come from the server's definition. */
public final class MatterFabricationPatternEncoding {
    private MatterFabricationPatternEncoding() {}

    public static boolean isEncodingMenu(AbstractContainerMenu menu) {
        return menu instanceof AEBaseMenu aeMenu
                && aeMenu.getTarget() instanceof IPatternTerminalMenuHost
                && !aeMenu.getSlots(SlotSemantics.BLANK_PATTERN).isEmpty()
                && !aeMenu.getSlots(SlotSemantics.ENCODED_PATTERN).isEmpty();
    }

    public static ItemStack encode(MatterFabricationRecipe recipe, List<ItemStack> selectedInputs) {
        if (selectedInputs.size() != recipe.ingredients().size()) {
            throw new IllegalArgumentException("Wrong number of selected ingredients");
        }
        var inputs = new LinkedHashMap<AEKey, Long>();
        for (int i = 0; i < selectedInputs.size(); i++) {
            var selected = selectedInputs.get(i);
            var ingredient = recipe.ingredients().get(i);
            if (selected.isEmpty() || !ingredient.ingredient().test(selected)) {
                throw new IllegalArgumentException("Selected item does not match ingredient");
            }
            add(inputs, AEItemKey.of(selected), ingredient.count());
        }
        if (!recipe.fluidInput().isEmpty()) {
            add(inputs, AEFluidKey.of(recipe.fluidInput()), recipe.fluidInput().getAmount());
        }
        for (var stack : recipe.aeInputs()) add(inputs, stack.what(), stack.amount());
        var outputs = new LinkedHashMap<AEKey, Long>();
        for (var stack : recipe.results()) add(outputs, AEItemKey.of(stack), stack.getCount());
        if (!recipe.fluidResult().isEmpty()) {
            add(outputs, AEFluidKey.of(recipe.fluidResult()), recipe.fluidResult().getAmount());
        }
        for (var stack : recipe.aeOutputs()) add(outputs, stack.what(), stack.amount());
        return PatternDetailsHelper.encodeProcessingPattern(stacks(inputs), stacks(outputs));
    }

    private static void add(Map<AEKey, Long> amounts, AEKey key, long amount) {
        if (key == null || amount <= 0) throw new IllegalArgumentException("Invalid recipe resource");
        amounts.merge(key, amount, Math::addExact);
    }

    private static GenericStack[] stacks(Map<AEKey, Long> amounts) {
        return amounts.entrySet().stream().map(entry -> new GenericStack(entry.getKey(), entry.getValue())).toArray(GenericStack[]::new);
    }
}
