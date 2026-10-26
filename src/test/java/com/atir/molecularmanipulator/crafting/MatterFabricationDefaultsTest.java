package com.atir.molecularmanipulator.crafting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class MatterFabricationDefaultsTest {
    private static final Path RECIPES = Path.of("src/main/resources/data/molecularmanipulator/recipe");

    @Test
    void allBuiltInFabricationRecipesUseTwentySeconds() throws IOException {
        int checked = 0;
        try (var files = Files.walk(RECIPES)) {
            for (var path : files.filter(file -> file.toString().endsWith(".json")).toList()) {
                var json = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
                if (!"molecularmanipulator:matter_fabrication".equals(json.get("type").getAsString())) continue;
                assertEquals(400, json.get("processing_time").getAsInt(), path.toString());
                checked++;
            }
        }
        assertTrue(checked > 0);
    }

    @Test
    void builtInResearchKeepsItsDurationAndOmitsLegacySpeedBenefits() throws IOException {
        try (var files = Files.walk(RECIPES.resolve("research"))) {
            for (var path : files.filter(file -> file.toString().endsWith(".json")).toList()) {
                var json = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
                assertEquals(600, json.get("duration").getAsInt(), path.toString());
                for (var depth : json.getAsJsonArray("depths")) {
                    assertFalse(depth.getAsJsonObject().has("speed_multiplier"), path.toString());
                    assertFalse(depth.getAsJsonObject().has("processing_ticks"), path.toString());
                }
            }
        }
    }
}
