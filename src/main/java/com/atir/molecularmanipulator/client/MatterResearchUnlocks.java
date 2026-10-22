package com.atir.molecularmanipulator.client;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import com.atir.molecularmanipulator.crafting.MatterRecipeIndex;
import com.atir.molecularmanipulator.research.MatterResearchRecipe;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;

/** Build unlock presentation once per recipe index/selection, including imported machine recipes. */
final class MatterResearchUnlocks {
    record Entry(ResourceLocation id, List<GenericStack> outputs, Component title, Component summary,
                 List<Component> tooltip) {}
    private MatterResearchUnlocks() {}

    static List<Entry> entries(Level level, MatterRecipeIndex index, RecipeHolder<MatterResearchRecipe> research) {
        if (research==null) return List.of();
        var result=new ArrayList<Entry>();
        for (var id : index.unlocksForResearch(research)) {
            var outputs=new ArrayList<GenericStack>();
            var tooltip=new ArrayList<Component>();
            Component summary=Component.empty();
            var fabrication=index.fabrication(id);
            if(fabrication!=null) {
                var recipe=fabrication.value();
                for(var stack:recipe.results()) outputs.add(new GenericStack(AEItemKey.of(stack),stack.getCount()));
                if(!recipe.fluidResult().isEmpty())outputs.add(new GenericStack(AEFluidKey.of(recipe.fluidResult()),recipe.fluidResult().getAmount()));
                outputs.addAll(recipe.aeOutputs());
                int branches=index.branchCountForResearch(research.id(),recipe);
                summary=Component.translatable("gui.molecularmanipulator.research.unlock_recipe_summary",
                        recipe.processingTime(),DisplayNumbers.compact(recipe.aePerTick()));
                if(branches>1)summary=summary.copy().append(" · ").append(Component.translatable(
                        "gui.molecularmanipulator.research.branch_recipes",branches));
                tooltip.add(Component.translatable("gui.molecularmanipulator.research.recipe_inputs"));
                for(var cost:recipe.ingredients()) {
                    var examples=cost.ingredient().getItems();
                    if(examples.length>0)tooltip.add(amount(examples[0].getHoverName(),cost.count()));
                }
                if(!recipe.fluidInput().isEmpty())tooltip.add(amount(AEFluidKey.of(recipe.fluidInput()).getDisplayName(),recipe.fluidInput().getAmount()));
                for(var input:recipe.aeInputs())tooltip.add(amount(input.what().getDisplayName(),input.amount()));
                if(!recipe.catalysts().isEmpty()) {
                    tooltip.add(Component.translatable("gui.molecularmanipulator.research.recipe_catalysts"));
                    for(var catalyst:recipe.catalysts()) {
                        var examples=catalyst.ingredient().getItems();
                        if(examples.length>0)tooltip.add(amount(examples[0].getHoverName(),catalyst.count()));
                    }
                }
            } else {
                var ordinary=level.getRecipeManager().byKey(id);
                if(ordinary.isPresent()) {
                    var stack=ordinary.get().value().getResultItem(level.registryAccess());
                    if(!stack.isEmpty())outputs.add(new GenericStack(AEItemKey.of(stack),stack.getCount()));
                }
                summary=Component.translatable("gui.molecularmanipulator.research."+(ordinary.isPresent()?"unlock_on_completion":"recipe_unavailable"));
            }
            Component title=outputs.isEmpty()?Component.literal(id.toString()):outputs.getFirst().what().getDisplayName();
            var lines=new ArrayList<Component>();lines.add(title);lines.add(Component.literal(id.toString()));lines.add(summary);
            if(!outputs.isEmpty()) {
                lines.add(Component.translatable("gui.molecularmanipulator.research.recipe_outputs"));
                for(var output:outputs)lines.add(amount(output.what().getDisplayName(),output.amount()));
            }
            lines.addAll(tooltip);
            result.add(new Entry(id,List.copyOf(outputs),title,summary,List.copyOf(lines)));
        }
        return List.copyOf(result);
    }

    private static Component amount(Component name,long amount) {
        return name.copy().append(" ×"+DisplayNumbers.exact(amount));
    }
}
