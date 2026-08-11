package com.atir.molecularmanipulator.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.google.gson.JsonParser;
import net.minecraftforge.common.ForgeConfigSpec;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

class ConfigTranslationCoverageTest {
    private static final String CONFIG_PREFIX =
            "molecularmanipulator.configuration.";
    private static final Set<String> CATEGORY_TRANSLATION_KEYS = Set.of(
            CONFIG_PREFIX + "category.general",
            CONFIG_PREFIX + "category.matter",
            CONFIG_PREFIX + "category.matter.storage",
            CONFIG_PREFIX + "category.matter.entropy",
            CONFIG_PREFIX + "category.matter.speed_cards",
            CONFIG_PREFIX + "category.matter.speed_cards.card_0",
            CONFIG_PREFIX + "category.matter.speed_cards.card_1",
            CONFIG_PREFIX + "category.matter.speed_cards.card_2",
            CONFIG_PREFIX + "category.matter.speed_cards.card_3",
            CONFIG_PREFIX + "category.matter.speed_cards.card_4",
            CONFIG_PREFIX + "category.crafting",
            CONFIG_PREFIX + "category.crafting.order",
            CONFIG_PREFIX + "category.crafting.max_fast",
            CONFIG_PREFIX + "category.crafting.batch_dispatch",
            CONFIG_PREFIX + "category.crafting.compatibility_dispatch",
            CONFIG_PREFIX + "category.display");

    @Test
    void everyDeclaredOptionHasEnglishAndChineseLabelAndTooltip() {
        Set<String> declaredKeys = new HashSet<>();
        collectTranslationKeys(ModConfig.COMMON_SPEC.getSpec(), declaredKeys);
        collectTranslationKeys(ModConfig.CLIENT_SPEC.getSpec(), declaredKeys);

        Set<String> english = loadLanguageKeys("en_us");
        Set<String> chinese = loadLanguageKeys("zh_cn");
        assertFalse(declaredKeys.isEmpty());
        for (String key : declaredKeys) {
            assertNotNull(key, "Every config option must declare a translation key");
            assertTrue(english.contains(key), "Missing English config label: " + key);
            assertTrue(english.contains(key + ".tooltip"),
                    "Missing English config tooltip: " + key + ".tooltip");
            assertTrue(chinese.contains(key), "Missing Chinese config label: " + key);
            assertTrue(chinese.contains(key + ".tooltip"),
                    "Missing Chinese config tooltip: " + key + ".tooltip");
        }
    }

    @Test
    void everyCategoryHasEnglishAndChineseLabelAndTooltip() {
        Set<String> english = loadLanguageKeys("en_us");
        Set<String> chinese = loadLanguageKeys("zh_cn");
        for (String key : CATEGORY_TRANSLATION_KEYS) {
            assertTrue(english.contains(key), "Missing English category label: " + key);
            assertTrue(english.contains(key + ".tooltip"),
                    "Missing English category tooltip: " + key + ".tooltip");
            assertTrue(chinese.contains(key), "Missing Chinese category label: " + key);
            assertTrue(chinese.contains(key + ".tooltip"),
                    "Missing Chinese category tooltip: " + key + ".tooltip");
        }
    }

    @Test
    void globalAndClientConfigSectionsHaveTranslations() {
        Set<String> english = loadLanguageKeys("en_us");
        Set<String> chinese = loadLanguageKeys("zh_cn");
        for (String file : Set.of(
                "omnisequence.transfinite.common.toml",
                "omnisequence.transfinite.client.toml")) {
            String key = CONFIG_PREFIX + "section." + file;
            assertTrue(english.contains(key), "Missing English config section: " + key);
            assertTrue(english.contains(key + ".title"),
                    "Missing English config section title: " + key + ".title");
            assertTrue(chinese.contains(key), "Missing Chinese config section: " + key);
            assertTrue(chinese.contains(key + ".title"),
                    "Missing Chinese config section title: " + key + ".title");
        }
    }

    @Test
    void configurationLanguageKeySetsMatchAcrossLocales() {
        Set<String> english = configurationKeys(loadLanguageKeys("en_us"));
        Set<String> chinese = configurationKeys(loadLanguageKeys("zh_cn"));
        assertEquals(english, chinese,
                "English and Chinese configuration keys must remain identical");
    }

    @Test
    void everySpeedCardTierHasDistinctTranslatedOptions() {
        Set<String> english = loadLanguageKeys("en_us");
        for (int cards = 0; cards <= 4; cards++) {
            for (String suffix : Set.of(
                    "parallel", "cycle_ticks", "cooling_multiplier")) {
                String key = CONFIG_PREFIX + "matter_speed_card_"
                        + cards + "_" + suffix;
                assertTrue(english.contains(key), "Missing tiered config label: " + key);
                assertTrue(english.contains(key + ".tooltip"),
                        "Missing tiered config tooltip: " + key + ".tooltip");
            }
        }
        assertFalse(english.contains(CONFIG_PREFIX + "matter_speed_card_parallel"));
        assertFalse(english.contains(CONFIG_PREFIX + "matter_speed_card_cycle_ticks"));
        assertFalse(english.contains(
                CONFIG_PREFIX + "matter_speed_card_cooling_multiplier"));
    }

    @Test
    void commonSpecContainsEverySupportedGroupedOption() {
        var expected = new HashSet<>(List.of(
                "general.pattern_pages",
                "general.build_blocks_per_tick",
                "general.idle_power",
                "matter.storage.sequence_capacity",
                "matter.storage.entropy_capacity",
                "matter.entropy.cooling_per_second",
                "crafting.order.max_amount",
                "crafting.max_fast.mode",
                "crafting.max_fast.max_nodes",
                "crafting.max_fast.compile_budget_ms",
                "crafting.max_fast.diagnostics",
                "crafting.batch_dispatch.enabled",
                "crafting.batch_dispatch.max_work_units",
                "crafting.compatibility_dispatch.max_calls_per_tick",
                "crafting.compatibility_dispatch.max_time_us"));
        for (int cards = 0; cards <= 4; cards++) {
            String prefix = "matter.speed_cards.card_" + cards + ".";
            expected.add(prefix + "parallel_operations");
            expected.add(prefix + "cycle_ticks");
            expected.add(prefix + "cooling_multiplier");
        }
        assertEquals(Set.copyOf(expected), optionPaths(ModConfig.COMMON_SPEC),
                "The Forge common config schema must contain every supported grouped option");
    }

    @Test
    void clientSpecContainsEverySupportedOption() {
        assertEquals(Set.of(
                        "display.matter_sequence_tooltip_mode",
                        "display.dynamic_effect_level"),
                optionPaths(ModConfig.CLIENT_SPEC),
                "The Forge client config schema must contain every supported option");
    }

    private static void collectTranslationKeys(
            UnmodifiableConfig config, Set<String> destination) {
        for (var entry : config.entrySet()) {
            Object value = entry.getRawValue();
            if (value instanceof UnmodifiableConfig section) {
                collectTranslationKeys(section, destination);
            } else if (value instanceof ForgeConfigSpec.ValueSpec valueSpec) {
                destination.add(valueSpec.getTranslationKey());
            }
        }
    }

    private static Set<String> optionPaths(ForgeConfigSpec spec) {
        var result = new HashSet<String>();
        collectOptionPaths(spec.getSpec(), "", result);
        return Set.copyOf(result);
    }

    private static void collectOptionPaths(UnmodifiableConfig config,
            String parent, Set<String> destination) {
        for (var entry : config.entrySet()) {
            String path = parent.isEmpty()
                    ? entry.getKey()
                    : parent + "." + entry.getKey();
            Object value = entry.getRawValue();
            if (value instanceof UnmodifiableConfig section) {
                collectOptionPaths(section, path, destination);
            } else if (value instanceof ForgeConfigSpec.ValueSpec) {
                destination.add(path);
            }
        }
    }

    private static Set<String> loadLanguageKeys(String language) {
        String resource = "/assets/molecularmanipulator/lang/"
                + language + ".json";
        try (var stream = ConfigTranslationCoverageTest.class
                .getResourceAsStream(resource)) {
            assertNotNull(stream, "Missing language resource: " + resource);
            try (var reader = new InputStreamReader(stream,
                    StandardCharsets.UTF_8)) {
                return Set.copyOf(JsonParser.parseReader(reader)
                        .getAsJsonObject().keySet());
            }
        } catch (java.io.IOException exception) {
            throw new AssertionError("Could not read " + resource, exception);
        }
    }

    private static Set<String> configurationKeys(Set<String> allKeys) {
        var result = new HashSet<String>();
        for (String key : allKeys) {
            if (key.startsWith(CONFIG_PREFIX)) {
                result.add(key);
            }
        }
        return Set.copyOf(result);
    }
}
