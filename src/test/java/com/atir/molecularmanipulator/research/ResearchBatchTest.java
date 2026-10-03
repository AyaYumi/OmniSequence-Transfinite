package com.atir.molecularmanipulator.research;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import org.junit.jupiter.api.Test;

class ResearchBatchTest {
    static {
        if (net.neoforged.fml.loading.LoadingModList.get() == null)
            net.neoforged.fml.loading.LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap();
    }
    private static MatterResearchRecipe.Cost cost(net.minecraft.world.item.Item item, long amount) {
        return new MatterResearchRecipe.Cost(Ingredient.of(item), amount);
    }
    private static ResearchDepth depth(long multiplier, Optional<List<MatterResearchRecipe.Cost>> override) {
        return new ResearchDepth(multiplier, 1, 1, 0, override);
    }
    private static MatterResearchRecipe recipe(List<MatterResearchRecipe.Cost> costs, List<ResearchDepth> depths) {
        return new MatterResearchRecipe("batch", List.of(), costs, 20, 0, List.of(), List.of(), 0, 1, depths);
    }
    @Test void remainingRoundsMergeEquivalentIngredientsAndRespectOverrides() {
        var definition = recipe(List.of(cost(Items.QUARTZ, 3)), List.of(
                depth(1, Optional.empty()), depth(2, Optional.empty()),
                depth(100, Optional.of(List.of(cost(Items.DIAMOND, 5), cost(Items.QUARTZ, 7))))));
        var result = ResearchBatch.costs(definition, 2, 3);
        assertEquals(2, result.size()); assertEquals(13, result.getFirst().count());
        assertTrue(result.getFirst().ingredient().test(Items.QUARTZ.getDefaultInstance()));
        assertEquals(5, result.getLast().count());
    }
    @Test void preservesCountsAboveIntegerMaximum() {
        var definition = recipe(List.of(cost(Items.QUARTZ, 1_000_000_000L)), List.of(depth(1, Optional.empty()), depth(4, Optional.empty())));
        assertEquals(5_000_000_000L, ResearchBatch.costs(definition, 1, 2).getFirst().count());
    }
    @Test void rejectsOverflowBeforeAnyMaterialPayment() {
        var definition = recipe(List.of(cost(Items.QUARTZ, Long.MAX_VALUE)), List.of(depth(1, Optional.empty()), depth(1, Optional.empty())));
        assertThrows(ArithmeticException.class, () -> ResearchBatch.costs(definition, 1, 2));
        var multiplication = recipe(List.of(cost(Items.QUARTZ, Long.MAX_VALUE)), List.of(depth(2, Optional.empty())));
        assertThrows(ArithmeticException.class, () -> ResearchBatch.costs(multiplication, 1, 1));
    }
    @Test void rejectsInvalidRangesAndAllowsEmptyCostOverrides() {
        var definition = recipe(List.of(cost(Items.QUARTZ, 1)), List.of(depth(1, Optional.of(List.of()))));
        assertEquals(List.of(), ResearchBatch.costs(definition, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> ResearchBatch.costs(definition, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> ResearchBatch.costs(definition, 1, 2));
    }
}
