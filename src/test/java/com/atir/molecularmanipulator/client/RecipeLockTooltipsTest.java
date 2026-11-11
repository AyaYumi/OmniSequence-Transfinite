package com.atir.molecularmanipulator.client;

import static org.junit.jupiter.api.Assertions.*;

import com.atir.molecularmanipulator.recipelock.RecipeLocks.ResearchRequirement;
import com.atir.molecularmanipulator.starcomputation.StarResearchNodeRecipe;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.client.resources.language.ClientLanguage;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@Isolated("Temporarily replaces the global Language")
class RecipeLockTooltipsTest {
    private static final ResourceLocation A = ResourceLocation.parse("pack:a");
    private static final ResourceLocation B = ResourceLocation.parse("pack:b");
    private static final ResourceLocation C = ResourceLocation.parse("pack:c");
    private Language previousLanguage;

    @BeforeAll
    static void bootstrap() {
        if (net.neoforged.fml.loading.LoadingModList.get() == null) {
            net.neoforged.fml.loading.LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        }
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    @BeforeEach
    void rememberLanguage() { previousLanguage = Language.getInstance(); }

    @AfterEach
    void restoreLanguage() { Language.inject(previousLanguage); }

    @ParameterizedTest
    @CsvSource({
            "en_us, Research Single-Block Integration to unlock this recipe, Research 自定义星海拓扑 to unlock this recipe, Integration",
            "zh_cn, 需要研究 单方块集成 才能解锁该合成, 需要研究 自定义星海拓扑 才能解锁该合成, 单方块集成"
    })
    void researchNamesUseIntegrationTitlesAndShortLabelsWithoutChangingLiteralCustomTitles(
            String locale, String translated, String literal, String shortLabel) {
        useLanguage(locale);
        var requirement = List.of(new ResearchRequirement(Set.of(A), false, true));
        var material = node("gui.molecularmanipulator.star_computation.node.material");
        assertEquals(List.of(translated), text(RecipeLockTooltips.lines(requirement, Map.of(A, material))));
        assertEquals(shortLabel, Component.translatable("gui.molecularmanipulator.star_computation.node_short.material").getString());
        assertEquals(List.of(literal), text(RecipeLockTooltips.lines(requirement, Map.of(A, node("自定义星海拓扑")))));
    }

    @ParameterizedTest
    @CsvSource({
            "en_us, false, Research A and B to unlock this recipe",
            "en_us, true, Research A or B to unlock this recipe",
            "zh_cn, false, 需要研究 A 且 B 才能解锁该合成",
            "zh_cn, true, 需要研究 A 或 B 才能解锁该合成"
    })
    void singleDeclarationUsesItsModeWithoutExtraParentheses(String locale, boolean any, String expected) {
        useLanguage(locale);
        var requirement = new ResearchRequirement(Set.of(B, A), any, true);
        assertEquals(List.of(expected), text(RecipeLockTooltips.lines(List.of(requirement), letterNodes())));
    }

    @ParameterizedTest
    @CsvSource({
            "en_us, Research (A or B) and C to unlock this recipe, Research (A and B) and C to unlock this recipe",
            "zh_cn, 需要研究 （A 或 B） 且 C 才能解锁该合成, 需要研究 （A 且 B） 且 C 才能解锁该合成"
    })
    void overlappingDeclarationsKeepTheirGroupingAndAlwaysUseAnd(String locale, String any, String all) {
        useLanguage(locale);
        var mandatory = new ResearchRequirement(Set.of(C), false, true);
        var alternative = new ResearchRequirement(Set.of(B, A), true, true);
        var together = new ResearchRequirement(Set.of(B, A), false, true);
        assertEquals(List.of(any), text(RecipeLockTooltips.lines(List.of(alternative, mandatory), letterNodes())));
        assertEquals(List.of(all), text(RecipeLockTooltips.lines(List.of(together, mandatory), letterNodes())));
    }

    @ParameterizedTest
    @CsvSource({
            "en_us, Research A or removed:research to unlock this recipe, Required research is missing or invalid; this recipe cannot be unlocked yet",
            "zh_cn, 需要研究 A 或 removed:research 才能解锁该合成, 所需研究定义缺失或无效，暂时无法解锁"
    })
    void invalidResearchKeepsTheMissingIdAndAddsAWarningEvenWithAKnownAlternative(
            String locale, String expected, String warning) {
        useLanguage(locale);
        var missing = ResourceLocation.parse("removed:research");
        var invalid = new ResearchRequirement(Set.of(missing, A), true, false);
        var lines = RecipeLockTooltips.lines(List.of(invalid), letterNodes());
        assertEquals(List.of(expected, warning), text(lines));
        assertEquals(ChatFormatting.GOLD.getColor(), lines.getFirst().getStyle().getColor().getValue());
        assertEquals(ChatFormatting.RED.getColor(), lines.getLast().getStyle().getColor().getValue());
    }

    @ParameterizedTest
    @CsvSource({
            "en_us, Research pack:a to unlock this recipe",
            "zh_cn, 需要研究 pack:a 才能解锁该合成"
    })
    void emptyResearchTitleFallsBackToItsNodeId(String locale, String expected) {
        useLanguage(locale);
        var requirement = List.of(new ResearchRequirement(Set.of(A), false, true));
        assertEquals(List.of(expected), text(RecipeLockTooltips.lines(requirement, Map.of(A, node("")))));
    }

    @Test
    void noLockProducesNoTooltipEvenWhenResearchDefinitionsExist() {
        assertTrue(RecipeLockTooltips.lines(List.of(), letterNodes()).isEmpty());
    }

    private static void useLanguage(String locale) {
        var location = new PackLocationInfo("recipe-lock-test", Component.literal("Recipe lock tests"),
                PackSource.DEFAULT, Optional.empty());
        try (var resources = new MultiPackResourceManager(PackType.CLIENT_RESOURCES,
                List.of(new PathPackResources(location, Path.of("src/main/resources"))))) {
            Language.inject(ClientLanguage.loadFrom(resources, List.of(locale), false));
        }
    }

    private static StarResearchNodeRecipe node(String title) {
        return new StarResearchNodeRecipe(title, 3, 0, List.of());
    }

    private static Map<ResourceLocation, StarResearchNodeRecipe> letterNodes() {
        return Map.of(A, node("A"), B, node("B"), C, node("C"));
    }

    private static List<String> text(List<Component> lines) {
        return lines.stream().map(Component::getString).toList();
    }
}
