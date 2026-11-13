package com.atir.molecularmanipulator.research;

import static com.atir.molecularmanipulator.starcomputation.StarComputationRecipeCodecs.optionalField;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Each entry is the total benefit after completing that numbered research, including the initial unlock. */
public record ResearchDepth(long materialMultiplier, long parallel, long speedMultiplier, int processingTicks,
        Optional<List<MatterResearchRecipe.Cost>> ingredients) {
    public static final Codec<ResearchDepth> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            optionalField(ResearchCodecs.POSITIVE_LONG, "material_multiplier", 1L).forGetter(ResearchDepth::materialMultiplier),
            ResearchCodecs.POSITIVE_LONG.fieldOf("parallel").forGetter(ResearchDepth::parallel),
            optionalField(ResearchCodecs.POSITIVE_LONG, "speed_multiplier", 1L).forGetter(ResearchDepth::speedMultiplier),
            optionalField(Codec.intRange(0, Integer.MAX_VALUE), "processing_ticks", 0).forGetter(ResearchDepth::processingTicks),
            optionalField(MatterResearchRecipe.Cost.CODEC.listOf(), "ingredients").forGetter(ResearchDepth::ingredients)
    ).apply(instance, ResearchDepth::new));
    public static final List<ResearchDepth> DEFAULTS = defaults();

    public ResearchDepth {
        if (materialMultiplier < 1 || parallel < 1 || speedMultiplier < 1 || processingTicks < 0) {
            throw new IllegalArgumentException("Invalid depth benefit or material multiplier");
        }
        ingredients = ingredients.map(List::copyOf);
    }

    /** Legacy speed fields remain decodable; research now grants parallelism only. */
    public int ticks(int baseTicks) {
        return Math.max(1, baseTicks);
    }

    public List<MatterResearchRecipe.Cost> costs(List<MatterResearchRecipe.Cost> base) {
        if (ingredients.isPresent()) return ingredients.get();
        return base.stream().map(cost -> new MatterResearchRecipe.Cost(cost.ingredient(), Math.multiplyExact(cost.count(), materialMultiplier))).toList();
    }

    private static List<ResearchDepth> defaults() {
        var result = new ArrayList<ResearchDepth>();
        for (int i = 0; i < 9; i++) {
            result.add(new ResearchDepth(1L << i, i == 8 ? Long.MAX_VALUE : 1L << (i * 8),
                    1L, 0, Optional.empty()));
        }
        return List.copyOf(result);
    }
}
