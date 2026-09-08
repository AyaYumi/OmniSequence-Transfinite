package com.atir.molecularmanipulator.research;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Each entry is the total benefit after completing that numbered research, including the initial unlock. */
public record ResearchDepth(long materialMultiplier, long parallel, long speedMultiplier, int processingTicks,
        Optional<List<MatterResearchRecipe.Cost>> ingredients) {
    public static final Codec<ResearchDepth> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResearchCodecs.POSITIVE_LONG.optionalFieldOf("material_multiplier", 1L).forGetter(ResearchDepth::materialMultiplier),
            ResearchCodecs.POSITIVE_LONG.fieldOf("parallel").forGetter(ResearchDepth::parallel),
            ResearchCodecs.POSITIVE_LONG.optionalFieldOf("speed_multiplier", 1L).forGetter(ResearchDepth::speedMultiplier),
            Codec.intRange(0, Integer.MAX_VALUE).optionalFieldOf("processing_ticks", 0).forGetter(ResearchDepth::processingTicks),
            MatterResearchRecipe.Cost.CODEC.listOf().optionalFieldOf("ingredients").forGetter(ResearchDepth::ingredients)
    ).apply(instance, ResearchDepth::new));
    public static final List<ResearchDepth> DEFAULTS = defaults();

    public ResearchDepth {
        if (materialMultiplier < 1 || parallel < 1 || speedMultiplier < 1 || processingTicks < 0) {
            throw new IllegalArgumentException("Invalid depth benefit or material multiplier");
        }
        ingredients = ingredients.map(List::copyOf);
    }

    public int ticks(int baseTicks) {
        return processingTicks > 0 ? processingTicks : (int) ((Math.max(1, baseTicks) - 1L) / speedMultiplier + 1);
    }

    public List<MatterResearchRecipe.Cost> costs(List<MatterResearchRecipe.Cost> base) {
        if (ingredients.isPresent()) return ingredients.get();
        return base.stream().map(cost -> new MatterResearchRecipe.Cost(cost.ingredient(), Math.multiplyExact(cost.count(), materialMultiplier))).toList();
    }

    private static List<ResearchDepth> defaults() {
        var result = new ArrayList<ResearchDepth>();
        for (int i = 0; i < 9; i++) {
            result.add(new ResearchDepth(1L << i, i == 8 ? Long.MAX_VALUE : 1L << (i * 8),
                    1L << i, i == 8 ? 1 : 0, Optional.empty()));
        }
        return List.copyOf(result);
    }
}
