package com.atir.molecularmanipulator.integration.guideme;

import appeng.api.stacks.GenericStack;
import com.atir.molecularmanipulator.client.DisplayNumbers;
import com.atir.molecularmanipulator.crafting.MatterFabricationRecipe;
import com.atir.molecularmanipulator.registry.ModContent;
import com.atir.molecularmanipulator.research.MatterResearchApi;
import guideme.compiler.tags.RecipeTypeMappingSupplier;
import guideme.document.DefaultStyles;
import guideme.document.block.LytBlock;
import guideme.document.block.LytParagraph;
import guideme.document.block.LytSlotGrid;
import guideme.document.block.recipes.LytStandardRecipeBox;
import java.util.Arrays;
import java.util.stream.Collectors;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.crafting.Ingredient;

/** GuideME's shared recipe extension also supports RecipeFor in the AE2 guide. */
public final class MatterFabricationGuideRecipes implements RecipeTypeMappingSupplier {
    @Override
    public void collect(RecipeTypeMappings mappings) {
        mappings.add(ModContent.MATTER_FABRICATION_RECIPE_TYPE.get(), MatterFabricationGuideRecipes::create);
    }

    public static LytBlock create(MatterFabricationRecipe holder) {
        var recipe = holder.value();
        var box = LytStandardRecipeBox.builder()
                .icon(ModContent.MATTER_FABRICATION_CONTROLLER_ITEM.get())
                .title(Component.translatable("gui.molecularmanipulator.fabrication.jei_title").getString());
        int inputCount = recipe.ingredients().size() + recipe.aeInputs().size();
        if (inputCount > 0) {
            int columns = Math.min(3, inputCount);
            var inputs = new LytSlotGrid(columns, ((inputCount + columns - 1) / columns));
            inputs.setRenderEmptySlots(false);
            for (int i = 0; i < recipe.ingredients().size(); i++) {
                var counted = recipe.ingredients().get(i);
                inputs.setIngredient(i % columns, i / columns, Ingredient.of(Arrays.stream(counted.ingredient().getItems())
                        .map(stack -> stack.copyWithCount(counted.count()))));
            }
            for (int i = 0; i < recipe.aeInputs().size(); i++) {
                int index = recipe.ingredients().size() + i;
                inputs.setItem(index % columns, index / columns, GenericStack.wrapInItemStack(recipe.aeInputs().get(i)));
            }
            box.input(inputs);
        }
        if (!recipe.results().isEmpty()) {
            var outputs = new LytSlotGrid(recipe.results().size(), 1);
            for (int i = 0; i < recipe.results().size(); i++) outputs.setItem(i, 0, recipe.results().get(i).copy());
            box.output(outputs);
        }
        var level = Minecraft.getInstance().level;
        String owners = level == null ? "" : MatterResearchApi.definitions(level).stream()
                .filter(research -> research.value().unlocks().contains(holder.id()))
                .map(research -> Component.translatable(research.value().title()).getString())
                .collect(Collectors.joining(" / "));
        box.addTop(paragraph(owners.isEmpty()
                ? Component.translatable("gui.molecularmanipulator.fabrication.jei."
                        + (recipe.requiresResearch() ? "unassigned" : "no_research")).getString()
                : text("unlock", owners)));
        if (!recipe.fluidInput().isEmpty()) box.addBottom(paragraph(text("fluid_input",
                recipe.fluidInput().getDisplayName().getString(), DisplayNumbers.exact(recipe.fluidInput().getAmount()))));
        if (!recipe.fluidResult().isEmpty()) box.addBottom(paragraph(text("fluid_output",
                recipe.fluidResult().getDisplayName().getString(), DisplayNumbers.exact(recipe.fluidResult().getAmount()))));
        box.addBottom(paragraph(text("stats", DisplayNumbers.exact(recipe.processingTime() / 20.0),
                DisplayNumbers.exact(recipe.aePerTick()))));
        return box.build(holder);
    }

    private static String text(String key, Object... args) {
        return Component.translatable("guide.molecularmanipulator.fabrication." + key, args).getString();
    }

    private static LytParagraph paragraph(String text) {
        var paragraph = new LytParagraph();
        paragraph.setStyle(DefaultStyles.CRAFTING_RECIPE_TYPE);
        paragraph.appendText(text);
        return paragraph;
    }
}
