package com.atir.molecularmanipulator.research;

import com.atir.molecularmanipulator.blockentity.MatterFabricationBlockEntity;
import com.atir.molecularmanipulator.crafting.MatterFabricationRecipe;
import com.atir.molecularmanipulator.registry.ModContent;
import java.util.Comparator;
import java.util.List;
import java.util.function.ToIntFunction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/** Public server API; recipe definitions are owned by the current RecipeManager, including KubeJS reloads. */
public final class MatterResearchApi {
    private MatterResearchApi() {}

    public static List<MatterResearchRecipe> definitions(Level level) {
        return level.getRecipeManager().getAllRecipesFor(ModContent.MATTER_RESEARCH_RECIPE_TYPE.get()).stream()
                .filter(holder -> holder.value().available())
                .sorted(Comparator.<MatterResearchRecipe>comparingInt(holder -> holder.value().sortOrder())
                        .thenComparing(holder -> holder.id().toString())).toList();
    }

    public static boolean start(MatterFabricationBlockEntity machine, String researchId) {
        requireServer(machine);
        var id = ResourceLocation.tryParse(researchId);
        return id != null && machine.getResearch().start(machine, id);
    }

    public static boolean setPaused(MatterFabricationBlockEntity machine, String researchId, boolean paused) {
        requireServer(machine);
        var id = ResourceLocation.tryParse(researchId);
        boolean changed = id != null && (paused ? machine.getResearch().setPaused(id, true) : machine.getResearch().resume(machine, id));
        if (changed) machine.saveChanges();
        return changed;
    }

    public static boolean isCompleted(MatterFabricationBlockEntity machine, String researchId) {
        var id = ResourceLocation.tryParse(researchId);
        return id != null && machine.getResearch().completed().contains(id);
    }

    public static int completionCount(MatterFabricationBlockEntity machine, String researchId) {
        var id = ResourceLocation.tryParse(researchId);
        return id == null ? 0 : machine.getResearch().completionCount(id);
    }

    /** Default requirements are one completion. Missing or unavailable prerequisites never count as finished. */
    public static boolean prerequisitesMet(MatterResearchRecipe research, List<MatterResearchRecipe> available,
            ToIntFunction<ResourceLocation> completions) {
        return research.prerequisites().stream().allMatch(id -> available.stream()
                .filter(holder -> holder.id().equals(id)).findFirst()
                .map(holder -> completions.applyAsInt(id) >= requiredPrerequisiteLevel(research, holder)).orElse(false));
    }

    public static int requiredPrerequisiteLevel(MatterResearchRecipe research, MatterResearchRecipe prerequisite) {
        int level = research.prerequisiteLevels().getOrDefault(prerequisite.id(), 1);
        return level == 0 ? prerequisite.value().depths().size() : level;
    }

    /** Server administration / KubeJS hook. No material is extracted; this research's current attempt is ended. */
    public static int setCompletionCount(MatterFabricationBlockEntity machine, String researchId, long requested) {
        requireServer(machine);
        if (requested < 0) throw new IllegalArgumentException("Research count cannot be negative");
        var definition = definitions(machine.getLevel()).stream()
                .filter(holder -> holder.id().toString().equals(researchId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown or unavailable research: " + researchId));
        int count = (int) Math.min(requested, definition.value().depths().size());
        machine.getResearch().setCompletionCount(definition.id(), count);
        machine.saveChanges();
        return count;
    }

    public static int setCompleted(MatterFabricationBlockEntity machine, String researchId, boolean completed) {
        return setCompletionCount(machine, researchId, completed ? Long.MAX_VALUE : 0);
    }

    public static int unlockAll(MatterFabricationBlockEntity machine) {
        requireServer(machine);
        var definitions = definitions(machine.getLevel());
        for (var holder : definitions) machine.getResearch().setCompletionCount(holder.id(), holder.value().depths().size());
        machine.saveChanges();
        return definitions.size();
    }

    public record ProductionProfile(long parallel, int ticks) {}

    public static ProductionProfile productionProfile(MatterFabricationBlockEntity machine, MatterFabricationRecipe recipe) {
        long parallel = 1; int ticks = recipe.value().processingTime(); boolean hasOwner = false;
        for (var holder : definitions(machine.getLevel())) {
            int completed = machine.getResearch().completionCount(holder.id());
            if (completed > 0 && holder.value().unlocks().contains(recipe.id())) {
                var depth = holder.value().depths().get(Math.min(completed, holder.value().depths().size()) - 1);
                parallel = Math.max(parallel, depth.parallel());
                int target = depth.ticks(recipe.value().processingTime());
                ticks = hasOwner ? Math.min(ticks, target) : target;
                hasOwner = true;
            }
        }
        return new ProductionProfile(parallel, ticks);
    }

    public static boolean canUseRecipe(MatterFabricationBlockEntity machine, MatterFabricationRecipe recipe) {
        return hasPermission(machine, recipe.id(), recipe.value().requiresResearch());
    }

    /** Query hook for other recipe executors; they must call it themselves to enforce their own permissions. */
    public static boolean isRecipeUnlocked(MatterFabricationBlockEntity machine, String recipeId) {
        var id = ResourceLocation.tryParse(recipeId);
        if (id == null || machine.getLevel() == null) return false;
        var recipe = machine.getLevel().getRecipeManager().byKey(id).orElse(null);
        return recipe != null && hasPermission(machine, id,
                recipe instanceof MatterFabricationRecipe fabrication && fabrication.requiresResearch());
    }

    private static boolean hasPermission(MatterFabricationBlockEntity machine, ResourceLocation id, boolean locked) {
        if (machine.getLevel() == null) return false;
        for (var definition : definitions(machine.getLevel())) {
            if (definition.value().unlocks().contains(id)) {
                locked = true;
                if (machine.getResearch().completed().contains(definition.id())) return true;
            }
        }
        return !locked;
    }

    private static void requireServer(MatterFabricationBlockEntity machine) {
        if (machine.getLevel() == null || machine.getLevel().isClientSide()
                || !machine.getLevel().getServer().isSameThread()) {
            throw new IllegalStateException("Research mutations require the owning server thread");
        }
    }
}
