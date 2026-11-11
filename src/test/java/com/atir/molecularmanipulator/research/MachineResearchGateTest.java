package com.atir.molecularmanipulator.research;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.junit.jupiter.api.Test;

class MachineResearchGateTest {
    static {
        if (net.neoforged.fml.loading.LoadingModList.get() == null) {
            net.neoforged.fml.loading.LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        }
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    private static MatterResearchRecipe definition() {
        return new MatterResearchRecipe("custom", List.of(), List.of(), 600, 512,
                List.of(ResourceLocation.parse("kubejs:output")), List.of(), 1000, 2);
    }

    @Test
    void importsRequireBothThirdCompletionsForAdmissionAndLivePermission() {
        var gated = MachineResearchGate.apply(definition());
        var available = List.of(new RecipeHolder<>(MachineResearchGate.SEQUENCE, definition()),
                new RecipeHolder<>(MachineResearchGate.COMPUTATION, definition()));
        assertEquals(4, gated.stage());
        for (int sequence : new int[] {0, 1, 2, 3, 9}) {
            for (int computation : new int[] {0, 1, 2, 3, 9}) {
                var counts = Map.of(MachineResearchGate.SEQUENCE, sequence, MachineResearchGate.COMPUTATION, computation);
                boolean expected = sequence >= 3 && computation >= 3;
                assertEquals(expected, MatterResearchApi.prerequisitesMet(gated, available, counts::get));
                assertEquals(expected, MachineResearchGate.met(available, counts::get));
            }
        }
        assertFalse(MachineResearchGate.met(List.of(available.getFirst()), id -> 9),
                "A removed or unavailable parent cannot be replaced with its stale completion count");
    }

    @Test
    void customImportsCannotLowerMinimumAndKeepStricterParents() {
        var extra = ResourceLocation.parse("kubejs:custom_parent");
        var configured = definition().withPrerequisiteLevels(Map.of(
                MachineResearchGate.SEQUENCE, 1, MachineResearchGate.COMPUTATION, 5, extra, 2));
        var gated = MachineResearchGate.apply(configured);
        assertEquals(Map.of(MachineResearchGate.SEQUENCE, 3, MachineResearchGate.COMPUTATION, 5, extra, 2),
                gated.prerequisiteLevels());
        assertTrue(gated.prerequisites().contains(extra));
        assertEquals(configured.ingredients(), gated.ingredients());
        assertEquals(configured.unlocks(), gated.unlocks());
        assertEquals(0, MachineResearchGate.apply(definition().withPrerequisiteLevels(
                Map.of(MachineResearchGate.SEQUENCE, 0))).prerequisiteLevels().get(MachineResearchGate.SEQUENCE));
    }

    @Test
    void shortenedCustomMaximumCannotReduceTheThreeRoundFloor() {
        var parent = definition();
        var shortened = new MatterResearchRecipe(parent.title(), parent.prerequisites(), parent.ingredients(),
                parent.duration(), parent.aePerTick(), parent.unlocks(), parent.requiredMods(),
                parent.sortOrder(), parent.stage(), parent.depths().subList(0, 2));
        var available = List.of(new RecipeHolder<>(MachineResearchGate.SEQUENCE, shortened),
                new RecipeHolder<>(MachineResearchGate.COMPUTATION, parent));
        var gated = MachineResearchGate.apply(definition().withPrerequisiteLevels(Map.of(
                MachineResearchGate.SEQUENCE, 0, MachineResearchGate.COMPUTATION, 0)), available);
        assertEquals(3, gated.prerequisiteLevels().get(MachineResearchGate.SEQUENCE));
        assertEquals(0, gated.prerequisiteLevels().get(MachineResearchGate.COMPUTATION));
        var counts = Map.of(MachineResearchGate.SEQUENCE, 2, MachineResearchGate.COMPUTATION, 9);
        assertFalse(MatterResearchApi.prerequisitesMet(gated, available, counts::get));
        assertFalse(MachineResearchGate.met(available, counts::get));
    }

    @Test
    void stageMigrationPreservesPaidBranchesOnceWithoutInventingThirdStageOrCrystallization() {
        var counts = new HashMap<ResourceLocation, Integer>();
        counts.put(id("ae_foundation"), 5);
        counts.put(id("omni_computation"), 2);
        counts.put(id("event_horizon"), 1);
        counts.put(id("machine/eae_crystal_assembler"), 9);
        ResearchStageMigration.migrate(counts, 0);
        assertEquals(5, counts.get(id("star_marrow_materials")));
        assertEquals(5, counts.get(id("eae_materials")));
        assertEquals(5, counts.get(id("quantum_materials")));
        assertEquals(2, counts.get(id("transfinite_fabrication")));
        assertEquals(1, counts.get(id("stellar_fabrication")));
        assertFalse(counts.containsKey(MachineResearchGate.SEQUENCE));
        assertFalse(counts.containsKey(id("star_marrow_crystallization")));
        counts.remove(id("star_marrow_materials"));
        ResearchStageMigration.migrate(counts, ResearchStageMigration.VERSION);
        assertFalse(counts.containsKey(id("star_marrow_materials")), "Migration cannot re-grant reset research");
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.parse("molecularmanipulator:research/" + path);
    }
}
