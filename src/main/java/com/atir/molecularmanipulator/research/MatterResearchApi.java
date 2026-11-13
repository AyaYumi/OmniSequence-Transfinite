package com.atir.molecularmanipulator.research;

import com.atir.molecularmanipulator.blockentity.MatterFabricationBlockEntity;
import com.atir.molecularmanipulator.crafting.MatterFabricationRecipe;
import com.atir.molecularmanipulator.crafting.MatterRecipeIndex;
import com.atir.molecularmanipulator.starcomputation.StarComputationNetwork;
import com.atir.molecularmanipulator.recipelock.RecipeLocks;
import java.util.List;
import java.util.function.ToIntFunction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/** Public server API; recipe definitions are owned by the current RecipeManager, including KubeJS reloads. */
public final class MatterResearchApi {
    private MatterResearchApi() {}

    public static List<MatterResearchRecipe> definitions(Level level) {
        return MatterRecipeIndex.get(level).research();
    }

    public static boolean start(MatterFabricationBlockEntity machine, String researchId) {
        requireServer(machine);
        var id = ResourceLocation.tryParse(researchId);
        return id != null && machine.getResearch().start(machine, id);
    }

    public static boolean orderMissing(MatterFabricationBlockEntity machine, ResourceLocation id) {
        requireServer(machine);
        return id != null && machine.getResearch().orderMissing(machine, id);
    }

    public static boolean orderMissing(MatterFabricationBlockEntity machine,ResourceLocation id,boolean toMaximum) {
        requireServer(machine);return id!=null && machine.getResearch().orderMissing(machine,id,toMaximum);
    }
    public static boolean stopPreparation(MatterFabricationBlockEntity machine,ResourceLocation id) {
        requireServer(machine);return id!=null && machine.getResearch().stopPreparation(machine,id);
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
        return id != null && machine.getResearch().completionCount(id) > 0;
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
        machine.getResearch().setCompletionCount(machine, definition.id(), count);
        machine.saveChanges();
        return count;
    }

    public static int setCompleted(MatterFabricationBlockEntity machine, String researchId, boolean completed) {
        return setCompletionCount(machine, researchId, completed ? Long.MAX_VALUE : 0);
    }

    public static int unlockAll(MatterFabricationBlockEntity machine) {
        requireServer(machine);
        var definitions = definitions(machine.getLevel());
        for (var holder : definitions) machine.getResearch().setCompletionCount(machine, holder.id(), holder.value().depths().size());
        machine.saveChanges();
        return definitions.size();
    }

    private static final ResourceLocation FOUNDATION_RESEARCH =
            new ResourceLocation("molecularmanipulator", "research/ae_foundation");

    public record ProductionProfile(long parallel, int ticks) {}

    public static ProductionProfile productionProfile(MatterFabricationBlockEntity machine, MatterFabricationRecipe recipe) {
        var index = MatterRecipeIndex.get(machine.getLevel());
        long parallel = productionParallel(recipe.value().requiresResearch(), index.researchFor(recipe.id()),
                index.research(FOUNDATION_RESEARCH), machine.getResearch()::completionCount);
        int ticks = StarComputationNetwork.forGrid(machine.getMainNode().getGrid())
                .processingTicks(recipe.value().processingTime());
        return new ProductionProfile(parallel, ticks);
    }

    static long productionParallel(boolean requiresResearch, List<MatterResearchRecipe> owners,
            @Nullable MatterResearchRecipe foundation, ToIntFunction<ResourceLocation> completions) {
        // Ownerless basic recipes stay usable before research and inherit only
        // foundation parallelism. Explicit owners (including unfinished ones) win;
        // research never substitutes for StarComputation speed or recipe unlocks.
        if (owners.isEmpty() && !requiresResearch && foundation != null) {
            owners = List.of(foundation);
        }
        long parallel = 1;
        for (var holder : owners) {
            int completed = completions.applyAsInt(holder.id());
            if (completed > 0) {
                var depth = holder.value().depths().get(Math.min(completed, holder.value().depths().size()) - 1);
                parallel = Math.max(parallel, depth.parallel());
            }
        }
        return parallel;
    }

    public static boolean canUseRecipe(MatterFabricationBlockEntity machine, MatterFabricationRecipe recipe) {
        return hasPermission(machine, recipe.id(), recipe.value().requiresResearch(), recipe.value().sourceRecipeId());
    }

    /** Query hook for other recipe executors; they must call it themselves to enforce their own permissions. */
    public static boolean isRecipeUnlocked(MatterFabricationBlockEntity machine, String recipeId) {
        var id = ResourceLocation.tryParse(recipeId);
        if (id == null || machine.getLevel() == null) return false;
        var recipe = MatterRecipeIndex.get(machine.getLevel()).fabrication(id);
        if (recipe != null) return canUseRecipe(machine, recipe);
        var other = machine.getLevel().getRecipeManager().byKey(id).orElse(null);
        if (other == null) return false;
        if (other instanceof MatterFabricationRecipe fabrication) {
            return hasPermission(machine, id, fabrication.requiresResearch(), fabrication.sourceRecipeId());
        }
        return hasPermission(machine, id, false, null);
    }

    private static boolean hasPermission(MatterFabricationBlockEntity machine, ResourceLocation id, boolean locked,
            @Nullable ResourceLocation sourceRecipeId) {
        if (machine.getLevel() == null) return false;
        // Global recipe rules are additional to this well's original research.
        // Always use the live machine context; connecting to a grid never copies its unlocks.
        if (!RecipeLocks.allowed(machine, id)
                || sourceRecipeId != null && !RecipeLocks.allowed(machine, sourceRecipeId)) return false;
        var index = MatterRecipeIndex.get(machine.getLevel());
        // Recheck the minimum even for old completion records or an alternative research owner.
        if (index.requiresMachineResearchGate(id)
                && !MachineResearchGate.met(index.research(), machine.getResearch()::completionCount)) return false;
        for (var definition : index.researchFor(id)) {
            locked = true;
            if (machine.getResearch().completionCount(definition.id()) > 0) return true;
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
