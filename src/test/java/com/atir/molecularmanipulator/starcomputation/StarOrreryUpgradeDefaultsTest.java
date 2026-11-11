package com.atir.molecularmanipulator.starcomputation;

import static org.junit.jupiter.api.Assertions.*;

import com.atir.molecularmanipulator.starcomputation.StarComputationUpgradeRecipe.Condition;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Resource-only schema/value regression; no world, ME grid, KubeJS or optional FTB mods required. */
class StarOrreryUpgradeDefaultsTest {
    private static final Path DIRECTORY = Path.of(
            "src/main/resources/data/molecularmanipulator/recipe/star_orrery_upgrade");

    @BeforeAll
    static void bootstrap() {
        if (net.neoforged.fml.loading.LoadingModList.get() == null) {
            net.neoforged.fml.loading.LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        }
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    @Test
    void onlyTheSixConfigurableLevelsHaveDefaultDeclarations() throws IOException {
        try (var files = Files.list(DIRECTORY)) {
            assertEquals(List.of("l4.json", "l5.json", "l6.json", "l7.json", "l8.json", "l9.json"),
                    files.map(path -> path.getFileName().toString()).sorted().toList());
        }
    }

    @ParameterizedTest(name = "L{0}: zero-energy automatic build with cumulative anchor requirements")
    @CsvSource({
            "4, 96,  48,   0",
            "5, 96, 104,   0",
            "6, 96, 168,   0",
            "7, 96, 168,  72",
            "8, 96, 168, 152",
            "9, 96, 168, 240"
    })
    void defaultsDecodeWithAutomaticBuildingAndAllEarlierRingAnchorRequirements(
            int level, int referenceCount, int quantumCount, int terminalCount) throws IOException {
        var path = DIRECTORY.resolve("l" + level + ".json");
        try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            var json = JsonParser.parseReader(reader).getAsJsonObject();
            assertEquals(Set.of("type", "level", "energy", "ae_per_tick", "auto_build", "conditions"), json.keySet());
            assertEquals("molecularmanipulator:star_orrery_upgrade", json.get("type").getAsString());
            var decoded = StarComputationUpgradeRecipe.CODEC.codec().parse(JsonOps.INSTANCE, json);
            assertTrue(decoded.error().isEmpty(), decoded::toString);
            var recipe = decoded.getOrThrow();
            assertNull(recipe.error());
            assertEquals(level, recipe.level());
            assertEquals(0, recipe.energy());
            assertEquals(1, recipe.aePerTick());
            assertTrue(recipe.autoBuild());
            assertEquals(recipe, StarComputationUpgradeRecipe.CODEC.codec().parse(JsonOps.INSTANCE,
                    StarComputationUpgradeRecipe.CODEC.codec().encodeStart(JsonOps.INSTANCE, recipe)
                            .getOrThrow()).getOrThrow());

            var root = recipe.conditions();
            assertEquals("anchors", root.type(), "Defaults require anchor stock without research, machine or node gates");
            assertFalse(root.group());
            assertTrue(root.children().isEmpty());
            assertEquals("all", root.match());
            var expectedAnchors = new LinkedHashMap<String, Integer>();
            expectedAnchors.put("reference", referenceCount);
            expectedAnchors.put("quantum", quantumCount);
            if (terminalCount > 0) expectedAnchors.put("terminal", terminalCount);
            assertEquals(expectedAnchors, targets(root));
        }
    }

    private static Map<String, Integer> targets(Condition leaf) {
        var result = new LinkedHashMap<String, Integer>();
        for (var target : leaf.targets()) {
            assertNull(result.put(target.id(), target.count()), "Duplicate target: " + target.id());
        }
        return result;
    }
}
