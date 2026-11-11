package com.atir.molecularmanipulator.research;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.ToIntFunction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;

/** Shared minimum for built-in and data-pack machine imports, including already completed imports. */
public final class MachineResearchGate {
    public static final ResourceLocation SEQUENCE = id("sequence_array");
    public static final ResourceLocation COMPUTATION = id("omni_computation");
    public static final int REQUIRED_COMPLETIONS = 3;

    private MachineResearchGate() {}

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("molecularmanipulator", "research/" + path);
    }

    public static boolean isMachineResearch(ResourceLocation id) {
        return id.getNamespace().equals("molecularmanipulator") && id.getPath().startsWith("research/machine/");
    }

    public static MatterResearchRecipe apply(MatterResearchRecipe research) {
        var levels = new LinkedHashMap<>(research.prerequisiteLevels());
        for (var id : List.of(SEQUENCE, COMPUTATION)) {
            // Zero means maximum, and a custom stricter requirement must remain stricter.
            levels.compute(id, (ignored, level) -> level == null ? REQUIRED_COMPLETIONS
                    : level == 0 ? 0 : Math.max(level, REQUIRED_COMPLETIONS));
        }
        return new MatterResearchRecipe(research.title(), research.prerequisites(), research.ingredients(),
                research.duration(), research.aePerTick(), research.unlocks(), research.requiredMods(),
                research.sortOrder(), Math.max(4, research.stage()), research.depths(), levels);
    }

    public static MatterResearchRecipe apply(MatterResearchRecipe research,
            List<RecipeHolder<MatterResearchRecipe>> available) {
        var gated = apply(research);
        var levels = new LinkedHashMap<>(gated.prerequisiteLevels());
        for (var id : List.of(SEQUENCE, COMPUTATION)) {
            // A data pack may shorten a parent's depths; "max" must never lower the three-round floor.
            if (levels.get(id) == 0 && available.stream().filter(holder -> holder.id().equals(id))
                    .mapToInt(holder -> holder.value().depths().size()).findFirst().orElse(0) < REQUIRED_COMPLETIONS) {
                levels.put(id, REQUIRED_COMPLETIONS);
            }
        }
        return gated.withPrerequisiteLevels(levels);
    }

    public static boolean met(List<RecipeHolder<MatterResearchRecipe>> available,
            ToIntFunction<ResourceLocation> completions) {
        return List.of(SEQUENCE, COMPUTATION).stream().allMatch(id ->
                available.stream().anyMatch(holder -> holder.id().equals(id))
                        && completions.applyAsInt(id) >= REQUIRED_COMPLETIONS);
    }
}
