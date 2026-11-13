package com.atir.molecularmanipulator.client;

import static org.junit.jupiter.api.Assertions.*;

import appeng.api.stacks.AEItemKey;
import com.atir.molecularmanipulator.client.MatterResearchPanel.Prerequisite;
import com.atir.molecularmanipulator.client.gui.theme.OsqSpriteRenderer;
import com.atir.molecularmanipulator.menu.MatterFabricationMenu;
import com.atir.molecularmanipulator.research.MatterResearchApi;
import com.atir.molecularmanipulator.research.MatterResearchMenuState;
import com.atir.molecularmanipulator.research.MatterResearchRecipe;
import com.atir.molecularmanipulator.research.ResearchDepth;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.joml.Vector3f;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.lwjgl.glfw.GLFW;

/** Real panel drawing, synchronized state and native hover contracts; no server or JEI simulation. */
@Isolated("Uses the existing client/font fixture and temporarily suppresses only the GPU slot frame")
class MatterResearchPrerequisitesTest {
    private static final String PREFIX = "gui.molecularmanipulator.research.";
    private static Object oldWindow;
    private static long keyWindow;

    @BeforeAll
    static void bootstrap() throws Exception {
        Class.forName(UiRenderRecorder.class.getName());
        // Screen.hasShiftDown reads GLFW even in drawForeground/update. Only key polling needs
        // a native handle: this invisible NO_API window creates no OpenGL context or game client.
        oldWindow = field(Minecraft.getInstance(), "window");
        assertTrue(GLFW.glfwInit());
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_CLIENT_API, GLFW.GLFW_NO_API);
        try { keyWindow = GLFW.glfwCreateWindow(1, 1, "Research prerequisite test keys", 0, 0); }
        finally { GLFW.glfwDefaultWindowHints(); }
        assertNotEquals(0L, keyWindow);
        var window = ResponsiveScreenTestFixture.allocate(com.mojang.blaze3d.platform.Window.class);
        ResponsiveScreenTestFixture.set(window, "window", keyWindow);
        ResponsiveScreenTestFixture.set(Minecraft.getInstance(), "window", window);
    }

    @AfterAll
    static void restoreWindow() throws Exception {
        ResponsiveScreenTestFixture.set(Minecraft.getInstance(), "window", oldWindow);
        if (keyWindow != 0) GLFW.glfwDestroyWindow(keyWindow);
        // Like the existing GPU tests, do not terminate GLFW underneath another suite.
    }

    @ParameterizedTest
    @ValueSource(strings = {"zh_cn", "en_us"})
    void defaultRequirementIsOneRoundAndDrawsBeforeAGenuineMaterial(String language) throws Exception {
        try (var fonts = new VanillaFontTestFixture(language)) {
            var parent = definition("foundation", "research.molecularmanipulator.ae_foundation", List.of(), List.of(), 9, Map.of());
            var child = definition("child", "research.molecularmanipulator.sequence_array", List.of(parent.id()),
                    List.of(cost(Items.IRON_INGOT, 64)), 9, Map.of());
            var input = panel(fonts, child, parent);
            var state = state(child);
            sync(input, state);
            var requirement = prerequisites(input, child).getFirst();
            assertEquals(new Prerequisite(parent.id(), parent.value().title(), 0, 1, true), requirement);
            assertFalse(requirement.met());
            assertGate(input, child, false);

            var ink = draw(input);
            assertClip(ink);
            assertEquals(2, field(input.panel, "contentRows"));
            assertRow(fonts, ink, tr("prerequisite_name", requirement.name()), 182, 213, 378, input.ui.palette.ink);
            assertRow(fonts, ink, tr("prerequisite_progress", 0, 1), 194, 213, 378, input.ui.palette.warn);
            assertRow(fonts, ink, Items.IRON_INGOT.getDescription(), 206, 235, 378, input.ui.palette.ink);
            assertRow(fonts, ink, Component.literal("0 / 64"), 218, 235, 378, input.ui.palette.ink);
            assertEquals(1, ink.items.size());
            assertEquals(213, ink.items.getFirst().x());
            assertEquals(198, ink.items.getFirst().y(), "The material moves down by exactly one 24px prerequisite row");
            assertTooltip(input.panel, 250, 180, requirement);
            assertNull(input.panel.ingredientAt(250, 180), "A research prerequisite is not a JEI item");
            assertIngredient(input.panel, 250, 200, Items.IRON_INGOT, 64, 197);
            assertEquals(List.of(Items.IRON_INGOT.getDescription().getString(),
                    tr("quantity_required", "64").getString(), tr("quantity_available", "0").getString()),
                    strings(input.panel.tooltipAt(250, 200)));
            assertProjectTooltip(input, child, requirement);
            assertHeading(ink, "prerequisites_materials");

            state.getAsJsonObject("counts").addProperty(parent.id().toString(), 1);
            sync(input, state);
            assertSame(child, field(input.panel, "selectedDefinition"));
            requirement = prerequisites(input, child).getFirst();
            assertTrue(requirement.met(), "The default must not silently become the parent's nine-round maximum");
            assertGate(input, child, true);
            ink = draw(input);
            assertRow(fonts, ink, tr("prerequisite_progress", 1, 1), 194, 213, 378, input.ui.palette.ok);
            assertTooltip(input.panel, 250, 195, requirement);
            assertProjectTooltip(input, child, requirement);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"zh_cn", "en_us"})
    void satisfyingTheVisibleRequirementDoesNotBypassExistingMachineOrLiveSelectionGates(String language) throws Exception {
        try (var fonts = new VanillaFontTestFixture(language)) {
            var parent = parent("parent", "Parent", 9);
            var child = definition("child", "Gated study", List.of(parent.id()), List.of(), 9, Map.of());
            var input = panel(fonts, child, parent);
            for (String blocker : List.of("none", "unformed", "building", "dismantling", "updating", "offline", "unaffordable", "stale_live")) {
                input.menu.formed = !blocker.equals("unformed");
                input.menu.building = blocker.equals("building");
                input.menu.dismantling = blocker.equals("dismantling");
                input.menu.updatingStructure = blocker.equals("updating");
                var state = state(child);
                state.getAsJsonObject("counts").addProperty(parent.id().toString(), 1);
                var live = state.getAsJsonObject("live");
                live.addProperty("online", !blocker.equals("offline"));
                live.addProperty("affordable", !blocker.equals("unaffordable"));
                if (blocker.equals("stale_live")) live.addProperty("id", parent.id().toString());
                sync(input, state);
                var requirement = prerequisites(input, child).getFirst();
                assertTrue(requirement.met());
                assertEquals(blocker.equals("none"), ((Button) field(input.panel, "action")).active, blocker);
                assertEquals(!blocker.equals("stale_live"), ((Button) field(input.panel, "order")).active,
                        "Ordering retains its existing live-selection/prerequisite gate, not the start button's machine gate: " + blocker);
                var ink = draw(input);
                assertRow(fonts, ink, requirement.progress(), 194, 213, 378, input.ui.palette.ok);
                assertTooltip(input.panel, 250, 180, requirement);
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"zh_cn", "en_us"})
    void multipleRequirementsKeepTheirOrderAndResolveExplicitThreeAndAllMaxIndependently(String language) throws Exception {
        try (var fonts = new VanillaFontTestFixture(language)) {
            var one = parent("one", "One round", 9);
            var three = parent("three", "Three rounds", 9);
            var all = parent("all", "All rounds", 5);
            var child = definition("child", "Custom study", List.of(one.id(), three.id(), all.id()), List.of(), 9,
                    Map.of(three.id(), 3, all.id(), 0));
            var input = panel(fonts, child, one, three, all);
            var state = state(child);
            var counts = state.getAsJsonObject("counts");
            counts.addProperty(one.id().toString(), 1);
            counts.addProperty(three.id().toString(), 2);
            counts.addProperty(all.id().toString(), 4);
            sync(input, state);
            var requirements = prerequisites(input, child);
            assertEquals(List.of(one.id(), three.id(), all.id()), requirements.stream().map(Prerequisite::id).toList());
            assertEquals(List.of(1, 3, 5), requirements.stream().map(Prerequisite::required).toList());
            assertEquals(List.of(true, false, false), requirements.stream().map(Prerequisite::met).toList());
            assertGate(input, child, false);
            var ink = draw(input);
            assertEquals(4, field(input.panel, "contentRows"), "Three prerequisites and one no-materials row");
            for (int row = 0; row < requirements.size(); row++) {
                var requirement = requirements.get(row);
                assertRow(fonts, ink, tr("prerequisite_name", requirement.name()), 182 + row * 24, 213, 378, input.ui.palette.ink);
                assertRow(fonts, ink, requirement.progress(), 194 + row * 24, 213, 378,
                        requirement.met() ? input.ui.palette.ok : input.ui.palette.warn);
                assertTooltip(input.panel, 250, 180 + row * 24, requirement);
                assertNull(input.panel.ingredientAt(250, 180 + row * 24));
                assertProjectTooltip(input, child, requirement);
            }
            counts.addProperty(three.id().toString(), 3);
            sync(input, state);
            assertGate(input, child, false, "Meeting explicit three must not bypass the independent all-max parent");
            counts.addProperty(all.id().toString(), 5);
            sync(input, state);
            assertGate(input, child, true);
            assertTrue(prerequisites(input, child).stream().allMatch(Prerequisite::met));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"zh_cn", "en_us"})
    void countsStayLiveAndExplicitZeroOverridesTheLegacyCompletedFallback(String language) throws Exception {
        try (var fonts = new VanillaFontTestFixture(language)) {
            var parent = parent("parent", "research.molecularmanipulator.ae_foundation", 9);
            var child = definition("child", "Live study", List.of(parent.id()), List.of(), 9, Map.of());
            var input = panel(fonts, child, parent);
            var state = state(child);
            state.remove("counts"); // An old save has only the legacy completed set.
            state.getAsJsonArray("completed").add(parent.id().toString());
            int[] expectedCounts = {1, 0, 6, 1, 0};
            for (int step = 0; step < expectedCounts.length; step++) {
                int expected = expectedCounts[step];
                if (step == 4) state.getAsJsonArray("completed").remove(0);
                if (step == 1 || step == 2) {
                    var counts = new JsonObject();
                    counts.addProperty(parent.id().toString(), expected);
                    state.add("counts", counts);
                } else state.remove("counts");
                sync(input, state);
                var requirement = prerequisites(input, child).getFirst();
                assertEquals(expected, requirement.completed());
                assertEquals(expected > 0, requirement.met());
                assertGate(input, child, expected > 0);
                var ink = draw(input);
                assertRow(fonts, ink, tr("prerequisite_progress", expected, 1), 194, 213, 378,
                        expected > 0 ? input.ui.palette.ok : input.ui.palette.warn);
                assertTooltip(input.panel, 250, 180, requirement);
                assertProjectTooltip(input, child, requirement);
                assertSame(child, field(input.panel, "selectedDefinition"), "Counts do not require a definition-cache rebuild");
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"zh_cn", "en_us"})
    void removedDefinitionsRemainUnavailableEvenWithSavedCompletionsAndUnresolvedMax(String language) throws Exception {
        try (var fonts = new VanillaFontTestFixture(language)) {
            var missing = ResourceLocation.parse("removed:research/a_very_long_pack_defined_prerequisite_that_is_no_longer_installed");
            for (int requested : new int[] {1, 3, 0}) {
                var child = definition("child", "Missing parent", List.of(missing), List.of(), 9,
                        requested == 1 ? Map.of() : Map.of(missing, requested));
                var input = panel(fonts, child);
                var state = state(child);
                state.getAsJsonArray("completed").add(missing.toString());
                state.getAsJsonObject("counts").addProperty(missing.toString(), 9);
                sync(input, state);
                var requirement = prerequisites(input, child).getFirst();
                assertEquals(missing.toString(), requirement.title());
                assertEquals(missing.toString(), requirement.name().getString());
                assertEquals(9, requirement.completed());
                assertEquals(requested, requirement.required());
                assertFalse(requirement.available());
                assertFalse(requirement.met());
                assertGate(input, child, false);
                var ink = draw(input);
                assertRow(fonts, ink, requested == 0 ? tr("prerequisite_max_progress", 9)
                        : tr("prerequisite_progress", 9, requested), 194, 213, 378, input.ui.palette.warn);
                assertTooltip(input.panel, 250, 180, requirement);
                assertProjectTooltip(input, child, requirement);
                assertTrue(projectTooltip(input, child).contains(tr("unavailable").getString()));
                var title = listText(ink).getFirst();
                assertTrue(title.value().endsWith("…"), "The visible ID may be fitted, but the hover keeps the entire ID");
                assertInk(fonts, title, 182, 213, 378);
                assertNull(input.panel.ingredientAt(250, 180));
                assertEquals(tr("unavailable").getString(), input.panel.tooltipAt(250, 180).getLast().getString());
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"zh_cn", "en_us"})
    void sameIdReloadsReplaceNamesRequirementsAndTheParentsCurrentMaximum(String language) throws Exception {
        try (var fonts = new VanillaFontTestFixture(language)) {
            var parent = parent("parent", "Original parent", 4);
            var child = definition("child", "Original child", List.of(parent.id()), List.of(), 9, Map.of());
            var input = panel(fonts, child, parent);
            var state = state(child);
            state.getAsJsonObject("counts").addProperty(parent.id().toString(), 2);
            sync(input, state);
            assertGate(input, child, true);
            var explicit = new RecipeHolder<>(child.id(), child.value().withPrerequisiteLevels(Map.of(parent.id(), 3)));
            definitions(input.panel, List.of(explicit, parent));
            input.panel.update(true);
            assertSame(explicit, field(input.panel, "selectedDefinition"));
            assertEquals(3, prerequisites(input, explicit).getFirst().required());
            assertGate(input, explicit, false);

            var max = new RecipeHolder<>(child.id(), child.value().withPrerequisiteLevels(Map.of(parent.id(), 0)));
            definitions(input.panel, List.of(max, parent));
            state.getAsJsonObject("counts").addProperty(parent.id().toString(), 4);
            sync(input, state);
            assertEquals(4, prerequisites(input, max).getFirst().required());
            assertGate(input, max, true);
            var replacement = parent("parent", language.equals("zh_cn") ? "重载后的前置研究" : "Reloaded parent", 7);
            definitions(input.panel, List.of(max, replacement));
            input.panel.update(true); // Same state JSON and selected ID, new recipe-manager snapshot.
            var requirement = prerequisites(input, max).getFirst();
            assertSame(max, field(input.panel, "selectedDefinition"));
            assertEquals(replacement.value().title(), requirement.title());
            assertEquals(7, requirement.required(), "All-max is resolved against the current parent, never cached as four");
            assertEquals(4, requirement.completed());
            assertGate(input, max, false);
            var ink = draw(input);
            assertRow(fonts, ink, tr("prerequisite_name", requirement.name()), 182, 213, 378, input.ui.palette.ink);
            assertRow(fonts, ink, tr("prerequisite_progress", 4, 7), 194, 213, 378, input.ui.palette.warn);
            assertFalse(listText(ink).stream().anyMatch(text -> text.value().contains("Original parent")));
            assertProjectTooltip(input, max, requirement);
            assertTooltip(input.panel, 250, 180, requirement);
            state.getAsJsonObject("counts").addProperty(parent.id().toString(), 7);
            sync(input, state);
            assertGate(input, max, true);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"zh_cn", "en_us"})
    void materialAvailabilityAndPaidQuantitiesKeepCostIndicesAfterTwoPrefixRows(String language) throws Exception {
        try (var fonts = new VanillaFontTestFixture(language)) {
            var a = parent("a", "Parent A", 9);
            var b = parent("b", "Parent B", 9);
            var costs = List.of(cost(Items.IRON_INGOT, 64), cost(Items.GOLD_INGOT, 128), cost(Items.DIAMOND, 256));
            var child = definition("child", "Indexed materials", List.of(a.id(), b.id()), costs, 9, Map.of());
            var input = panel(fonts, child, a, b);
            for (String mode : List.of("stock", "paid", "batch", "short_arrays")) {
                var state = state(child);
                state.getAsJsonObject("live").add("available", numbers(11, 22, 33));
                state.getAsJsonObject("live").add("batch_available", numbers(41, 52, 63));
                boolean batch = mode.equals("batch");
                if (!mode.equals("stock")) {
                    var task = task(batch);
                    task.add("paid", mode.equals("short_arrays") ? numbers(7) : numbers(7, 13, 17));
                    state.getAsJsonObject("tasks").add(child.id().toString(), task);
                }
                if (mode.equals("short_arrays")) state.getAsJsonObject("live").add("available", numbers(11, 22));
                sync(input, state);
                input.panel.restoreSelection(new MatterResearchPanel.Selection(child.id(), 0, 0));
                var first = draw(input);
                assertEquals(5, field(input.panel, "contentRows"));
                assertEquals(1, first.items.size());
                assertEquals(222, first.items.getFirst().y(), "Two requirements prefix COST[0], not COST[2]");
                assertIngredient(input.panel, 250, 230, Items.IRON_INGOT, batch ? 192 : 64, 221);
                assertTrue(input.panel.scroll(250, 200, -2));
                var ink = draw(input);
                long[] covered = switch (mode) {
                    case "stock" -> new long[] {11, 22, 33};
                    case "paid" -> new long[] {18, 35, 50};
                    case "batch" -> new long[] {48, 65, 80};
                    default -> new long[] {18, 22, 0};
                };
                assertEquals(3, ink.items.size());
                for (int index = 0; index < costs.size(); index++) {
                    var cost = costs.get(index);
                    var item = cost.ingredient().getItems()[0].getItem();
                    long required = cost.count() * (batch ? 3 : 1);
                    assertSame(item, ink.items.get(index).stack().getItem(), mode);
                    assertEquals(174 + index * 24, ink.items.get(index).y());
                    assertRow(fonts, ink, Component.literal(covered[index] + " / " + required),
                            194 + index * 24, 235, 378, input.ui.palette.ink);
                    assertIngredient(input.panel, 250, 180 + index * 24, item, required, 173 + index * 24);
                    long available = mode.equals("short_arrays") && index == 2 ? 0 : (batch ? 41 : 11) + index * 11;
                    assertEquals(tr("quantity_available", Long.toString(available)).getString(),
                            input.panel.tooltipAt(250, 180 + index * 24).getLast().getString());
                }
                assertClip(ink);
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"zh_cn", "en_us"})
    void paidTasksKeepTheirMaterialTermsButRequirementsFollowTheCurrentDefinition(String language) throws Exception {
        try (var fonts = new VanillaFontTestFixture(language)) {
            var parent = parent("parent", "Current parent", 9);
            var oldParent = parent("old_parent", "Obsolete parent", 9);
            var child = definition("child", "Reloaded study", List.of(parent.id()), List.of(cost(Items.DIAMOND, 999)), 9,
                    Map.of(parent.id(), 3));
            var retained = definition("child", "Retained payment terms", List.of(oldParent.id()),
                    List.of(cost(Items.IRON_INGOT, 64)), 9, Map.of());
            var input = panel(fonts, child, parent, oldParent);
            var state = state(child);
            var task = task(false);
            task.add("paid", numbers(9));
            state.getAsJsonObject("tasks").add(child.id().toString(), task);
            state.getAsJsonObject("counts").addProperty(parent.id().toString(), 2);
            sync(input, state);
            @SuppressWarnings("unchecked") var terms = (Map<ResourceLocation, MatterResearchRecipe>) field(input.panel, "taskTerms");
            terms.put(child.id(), retained.value());
            var ink = draw(input);
            var requirement = prerequisites(input, child).getFirst();
            assertEquals(3, requirement.required());
            assertRow(fonts, ink, tr("prerequisite_name", requirement.name()), 182, 213, 378, input.ui.palette.ink);
            assertRow(fonts, ink, tr("prerequisite_progress", 2, 3), 194, 213, 378, input.ui.palette.warn);
            assertFalse(listText(ink).stream().anyMatch(text -> text.value().contains("Obsolete parent")));
            assertIngredient(input.panel, 250, 205, Items.IRON_INGOT, 64, 197);
            assertRow(fonts, ink, Component.literal("9 / 64"), 218, 235, 378, input.ui.palette.ink);
            assertTooltip(input.panel, 250, 180, requirement);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"zh_cn", "en_us"})
    void noCostAndMaxedHintsOccupyTheirOwnRowWithoutOverlappingPrerequisites(String language) throws Exception {
        try (var fonts = new VanillaFontTestFixture(language)) {
            var parents = List.of(parent("a", "Parent A", 9), parent("b", "Parent B", 9), parent("c", "Parent C", 9));
            for (int prefix = 0; prefix <= parents.size(); prefix++) for (boolean maxed : List.of(false, true)) {
                var child = definition("child", "Empty materials", parents.subList(0, prefix).stream().map(RecipeHolder::id).toList(),
                        maxed ? List.of(cost(Items.DIAMOND, 64)) : List.of(), 9, Map.of());
                var input = panel(fonts, child, parents.toArray(RecipeHolder[]::new));
                var state = state(child);
                state.getAsJsonObject("counts").addProperty(child.id().toString(), maxed ? 9 : 0);
                sync(input, state);
                var ink = draw(input);
                assertEquals(prefix + 1, field(input.panel, "contentRows"));
                if (prefix == 3) {
                    assertTrue(input.panel.scroll(250, 200, -1));
                    ink = draw(input);
                }
                int visiblePrefix = Math.min(prefix, 2);
                String message = tr(maxed ? "max_hint" : "no_materials").getString();
                var hint = listText(ink).getLast();
                assertTrue(hint.value().equals(message) || hint.value().endsWith("…")
                        && message.startsWith(hint.value().substring(0, hint.value().length() - 1)), hint.toString());
                float[] hintBounds = bounds(fonts, hint);
                assertInk(fonts, hint, prefix == 0 ? 210 : 185 + visiblePrefix * 24, 213, 378);
                assertTrue(ink.items.isEmpty(), "The empty/maxed row must never retain a stale material icon");
                for (int row = 0; row < visiblePrefix; row++) {
                    var requirement = prerequisites(input, child).get(row + (prefix == 3 ? 1 : 0));
                    var progress = listText(ink).get(row * 2 + 1);
                    assertTrue(bounds(fonts, progress)[3] + 3 <= hintBounds[1], "No empty-state text on top of prerequisite progress");
                    assertTooltip(input.panel, 250, 180 + row * 24, requirement);
                }
                assertNull(input.panel.ingredientAt(250, 180 + visiblePrefix * 24));
                assertTrue(input.panel.tooltipAt(250, 180 + visiblePrefix * 24).isEmpty());
                assertClip(ink);
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"zh_cn", "en_us"})
    void shrinkingAMaxedListKeepsTheFirstRedrawsPrerequisiteTooltipLive(String language) throws Exception {
        try (var fonts = new VanillaFontTestFixture(language)) {
            var parent = parent("parent", "Parent", 9);
            var child = definition("child", "Study", List.of(parent.id()),
                    List.of(cost(Items.IRON_INGOT, 64), cost(Items.GOLD_INGOT, 128), cost(Items.DIAMOND, 256)), 9, Map.of());
            var input = panel(fonts, child, parent);
            var state = state(child);
            draw(input);
            input.panel.scroll(250, 200, -1);
            draw(input);
            assertEquals(24.0, input.panel.selection().scroll());
            state.getAsJsonObject("counts").addProperty(child.id().toString(), 9);
            sync(input, state);
            draw(input);
            assertEquals(0.0, input.panel.selection().scroll());
            assertTooltip(input.panel, 250, 180, prerequisites(input, child).getFirst());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"zh_cn", "en_us"})
    void headingFollowsSelectionReloadAndMaterialUnlockOrderViewSwitches(String language) throws Exception {
        try (var fonts = new VanillaFontTestFixture(language)) {
            var parent = parent("parent", "Parent", 9);
            var child = definition("child", "Study", List.of(parent.id()), List.of(), 9, Map.of());
            var input = panel(fonts, child, parent);
            assertHeading(draw(input), "prerequisites_materials");
            input.panel.restoreSelection(new MatterResearchPanel.Selection(parent.id(), 0, 0));
            assertHeading(draw(input), "materials");
            input.panel.restoreSelection(new MatterResearchPanel.Selection(child.id(), 0, 0));
            assertHeading(draw(input), "prerequisites_materials");
            input.panel.showDetails(true);
            var orders = draw(input);
            assertFalse(orders.text.stream().anyMatch(text -> text.value().equals(tr("prerequisites_materials").getString())));
            assertTrue(input.panel.tooltipAt(250, 180).isEmpty());
            input.panel.showDetails(false);
            assertHeading(draw(input), "prerequisites_materials");
            ResponsiveScreenTestFixture.set(input.panel, "showUnlocks", true);
            input.panel.update(true);
            var unlocks = draw(input);
            exact(unlocks, Component.translatable("gui.molecularmanipulator.fab.s054").getString());
            assertFalse(unlocks.text.stream().anyMatch(text -> text.value().equals(tr("prerequisites_materials").getString())));
            assertTrue(input.panel.tooltipAt(250, 180).isEmpty());
            input.panel.showDetails(false);
            var independent = definition("child", "No longer dependent", List.of(), List.of(), 9, Map.of());
            definitions(input.panel, List.of(independent, parent));
            input.panel.update(true);
            assertSame(independent, field(input.panel, "selectedDefinition"));
            assertHeading(draw(input), "materials");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"zh_cn", "en_us"})
    void wheelReachesMaterialsAfterManyPrerequisitesAndHoverHonorsTheClipAndLastRenderedSelection(String language) throws Exception {
        try (var fonts = new VanillaFontTestFixture(language)) {
            var parents = List.of(parent("a", "Parent A", 9), parent("b", "Parent B", 9),
                    parent("c", "Parent C", 9), parent("d", "Parent D", 9));
            var child = definition("child", "Scrollable study", parents.stream().map(RecipeHolder::id).toList(),
                    List.of(cost(Items.IRON_INGOT, 64), cost(Items.GOLD_INGOT, 128), cost(Items.DIAMOND, 256)), 9, Map.of());
            var input = panel(fonts, child, parents.toArray(RecipeHolder[]::new));
            var ink = draw(input);
            assertClip(ink);
            assertEquals(7, field(input.panel, "contentRows"));
            assertEquals(6, listText(ink).size(), "Exactly three complete prerequisite rows are rendered");
            assertTrue(ink.items.isEmpty());
            assertTrue(input.panel.tooltipAt(250, 246).isEmpty(), "The spare four clip pixels cannot expose an undrawn fourth row");
            for (double[] outside : new double[][] {{211, 180}, {378, 180}, {250, 171}, {250, 172}, {250, 248}}) {
                assertTrue(input.panel.tooltipAt(outside[0], outside[1]).isEmpty());
                assertNull(input.panel.ingredientAt(outside[0], outside[1]));
            }
            assertTooltip(input.panel, 212, 173, prerequisites(input, child).getFirst());
            assertTrue(input.panel.scroll(383, 247, -0.25), "The list's scrollbar needs no widget focus for wheel input");
            assertEquals(24.0, input.panel.selection().scroll());
            assertTrue(input.panel.tooltipAt(250, 180).isEmpty(), "Rows from before the wheel event are stale until drawn again");
            draw(input);
            assertTooltip(input.panel, 250, 180, prerequisites(input, child).get(1));
            assertTrue(input.panel.scroll(250, 200, -1));
            ink = draw(input);
            assertTooltip(input.panel, 250, 180, prerequisites(input, child).get(2));
            assertNull(input.panel.ingredientAt(250, 180));
            assertIngredient(input.panel, 250, 230, Items.IRON_INGOT, 64, 221);
            assertEquals(1, ink.items.size());
            assertTrue(input.panel.scroll(250, 200, -100));
            assertEquals(96.0, input.panel.selection().scroll());
            ink = draw(input);
            assertEquals(3, ink.items.size());
            assertIngredient(input.panel, 250, 180, Items.IRON_INGOT, 64, 173);
            assertIngredient(input.panel, 250, 204, Items.GOLD_INGOT, 128, 197);
            assertIngredient(input.panel, 250, 228, Items.DIAMOND, 256, 221);
            assertTrue(input.panel.scroll(250, 200, -1));
            assertEquals(96.0, input.panel.selection().scroll(), "The bottom does not wrap");
            assertTrue(input.panel.scroll(250, 200, 100));
            assertEquals(0.0, input.panel.selection().scroll());
            draw(input);
            for (double[] outside : new double[][] {{203, 200}, {392, 200}, {250, 171}, {250, 248}})
                assertFalse(input.panel.scroll(outside[0], outside[1], -1));
            assertFalse(input.panel.scroll(250, 200, 0));
            input.panel.update(false);
            assertFalse(input.panel.scroll(250, 200, -1));
            assertTrue(input.panel.tooltipAt(250, 180).isEmpty());
            assertNull(input.panel.ingredientAt(250, 180));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"zh_cn", "en_us"})
    void responsiveJeiLookupNeverTurnsThePrefixIntoAnIngredient(String language) throws Exception {
        try (var fonts = new VanillaFontTestFixture(language)) {
            var parent = parent("parent", "Parent", 9);
            var child = definition("child", "Study", List.of(parent.id()), List.of(cost(Items.DIAMOND, 4096)), 9, Map.of());
            var input = panel(fonts, child, parent);
            draw(input);
            var screen = ResponsiveScreenTestFixture.allocate(MatterFabricationScreen.class);
            ResponsiveScreenTestFixture.set(screen, "researchPanel", input.panel);
            ResponsiveScreenTestFixture.set(screen, "researchPage", true);
            for (int[] viewport : new int[][] {{854, 480}, {320, 240}, {567, 240}}) {
                int width = viewport[0], height = viewport[1], left = (width - 400) / 2, top = (height - 380) / 2;
                screen.width = width; screen.height = height;
                ResponsiveScreenTestFixture.set(screen, "imageWidth", 400);
                ResponsiveScreenTestFixture.set(screen, "imageHeight", 380);
                ResponsiveScreenTestFixture.set(screen, "leftPos", left);
                ResponsiveScreenTestFixture.set(screen, "topPos", top);
                int x = screen.responsiveScreenX(left + 250);
                assertNull(screen.researchIngredientAt(x, screen.responsiveScreenY(top + 180)));
                int y = screen.responsiveScreenY(top + 205);
                var material = screen.researchIngredientAt(x, y);
                assertNotNull(material);
                assertEquals(AEItemKey.of(Items.DIAMOND), material.stack().what());
                assertEquals(4096, material.stack().amount());
                assertTrue(material.bounds().contains(x, y));
                assertEquals(screen.responsiveScreenY(top + 197), material.bounds().getY());
                assertEquals(screen.responsiveScreenY(top + 221) - material.bounds().getY(), material.bounds().getHeight());
            }
            ResponsiveScreenTestFixture.set(screen, "researchPage", false);
            assertNull(screen.researchIngredientAt(250, 205));
        }
    }

    private record Panel(MatterResearchPanel panel, MatterFabricationMenu menu, OsqUi ui) {}

    @SafeVarargs
    private static Panel panel(VanillaFontTestFixture fonts, RecipeHolder<MatterResearchRecipe> child,
            RecipeHolder<MatterResearchRecipe>... parents) throws Exception {
        var menu = ResponsiveScreenTestFixture.allocate(MatterFabricationMenu.class);
        menu.researchState = new MatterResearchMenuState("{\"completed\":[],\"tasks\":{}}");
        menu.formed = true;
        // Existing fixture convention: real callbacks, server-side/no-op selection transport, no live world.
        var player = ResponsiveScreenTestFixture.allocate(net.minecraft.server.level.ServerPlayer.class);
        ResponsiveScreenTestFixture.set(player, "level", ResponsiveScreenTestFixture.allocate(net.minecraft.server.level.ServerLevel.class));
        ResponsiveScreenTestFixture.set(menu, "playerInventory", new net.minecraft.world.entity.player.Inventory(player));
        var panel = new MatterResearchPanel(menu, fonts.font, 0, 0, button -> {});
        var definitions = new ArrayList<RecipeHolder<MatterResearchRecipe>>();
        definitions.add(child); definitions.addAll(List.of(parents));
        definitions(panel, List.copyOf(definitions));
        var input = new Panel(panel, menu, (OsqUi) field(panel, "ui"));
        sync(input, state(child));
        return input;
    }

    private static RecipeHolder<MatterResearchRecipe> parent(String id, String title, int max) {
        return definition(id, title, List.of(), List.of(), max, Map.of());
    }

    private static RecipeHolder<MatterResearchRecipe> definition(String id, String title, List<ResourceLocation> prerequisites,
            List<MatterResearchRecipe.Cost> costs, int max, Map<ResourceLocation, Integer> levels) {
        return new RecipeHolder<>(ResourceLocation.parse("test:" + id), new MatterResearchRecipe(title, prerequisites, costs,
                100, 0, List.of(), List.of(), 0, 1, ResearchDepth.DEFAULTS.subList(0, max), levels));
    }

    private static MatterResearchRecipe.Cost cost(Item item, long count) { return new MatterResearchRecipe.Cost(Ingredient.of(item), count); }
    private static Component tr(String key, Object... args) { return Component.translatable(PREFIX + key, args); }
    private static List<String> strings(List<Component> lines) { return lines.stream().map(Component::getString).toList(); }
    private static JsonArray numbers(long... values) {
        var result = new JsonArray();
        for (long value : values) result.add(value);
        return result;
    }

    private static JsonObject state(RecipeHolder<MatterResearchRecipe> child) {
        var state = new JsonObject();
        state.add("completed", new JsonArray()); state.add("tasks", new JsonObject()); state.add("counts", new JsonObject());
        var live = new JsonObject();
        live.addProperty("id", child.id().toString()); live.addProperty("online", true); live.addProperty("affordable", true);
        state.add("live", live);
        return state;
    }

    private static JsonObject task(boolean ordered) {
        var task = new JsonObject();
        task.addProperty("first_round", 1); task.addProperty("round", ordered ? 2 : 1);
        task.addProperty("ordered", ordered); task.addProperty("paused", false);
        task.addProperty("progress", 20); task.addProperty("status", "running");
        return task;
    }

    private static void sync(Panel input, JsonObject state) {
        input.menu.researchState = new MatterResearchMenuState(state.toString());
        input.panel.update(true);
    }

    private static void definitions(MatterResearchPanel panel, List<RecipeHolder<MatterResearchRecipe>> definitions) throws Exception {
        var method = MatterResearchPanel.class.getDeclaredMethod("updateDefinitions", List.class);
        method.setAccessible(true); method.invoke(panel, definitions);
    }

    @SuppressWarnings("unchecked")
    private static List<Prerequisite> prerequisites(Panel input, RecipeHolder<MatterResearchRecipe> child) throws Exception {
        var method = MatterResearchPanel.class.getDeclaredMethod("prerequisites", MatterResearchRecipe.class);
        method.setAccessible(true);
        return (List<Prerequisite>) method.invoke(input.panel, child.value());
    }

    private static void assertGate(Panel input, RecipeHolder<MatterResearchRecipe> child, boolean expected) throws Exception {
        assertGate(input, child, expected, "The visible requirement must agree with the existing admission gate");
    }
    private static void assertGate(Panel input, RecipeHolder<MatterResearchRecipe> child, boolean expected, String reason) throws Exception {
        @SuppressWarnings("unchecked") var definitions = (List<RecipeHolder<MatterResearchRecipe>>) field(input.panel, "definitions");
        var requirements = prerequisites(input, child);
        assertEquals(expected, MatterResearchApi.prerequisitesMet(child.value(), definitions,
                id -> requirements.stream().filter(requirement -> requirement.id().equals(id)).findFirst().orElseThrow().completed()), reason);
        assertEquals(expected, ((Button) field(input.panel, "action")).active, reason);
        assertEquals(expected, ((Button) field(input.panel, "order")).active, reason);
    }

    private static void assertTooltip(MatterResearchPanel panel, double x, double y, Prerequisite requirement) {
        assertEquals(List.of(requirement.name().getString(), requirement.id().toString(), requirement.progress().getString(),
                tr(!requirement.available() ? "unavailable" : requirement.met() ? "prerequisite_satisfied" : "prerequisite").getString()),
                strings(panel.tooltipAt(x, y)));
        assertEquals(strings(requirement.tooltip()), strings(panel.tooltipAt(x, y)));
        assertFalse(panel.tooltipAt(x, y).stream().anyMatch(line -> line.getString().contains(PREFIX) || line.getString().contains("%s")));
    }

    private static String projectTooltip(Panel input, RecipeHolder<MatterResearchRecipe> child) throws Exception {
        @SuppressWarnings("unchecked") var rows = (List<Button>) field(input.panel, "rows");
        @SuppressWarnings("unchecked") var definitions = (List<RecipeHolder<MatterResearchRecipe>>) field(input.panel, "definitions");
        var row = rows.get(definitions.indexOf(child));
        assertNotNull(row.getTooltip());
        return ((Component) field(row.getTooltip(), "message")).getString();
    }
    private static void assertProjectTooltip(Panel input, RecipeHolder<MatterResearchRecipe> child, Prerequisite requirement) throws Exception {
        String tooltip = projectTooltip(input, child);
        assertTrue(tooltip.contains(tr("prerequisite_name", requirement.name()).getString() + "\n" + requirement.progress().getString()), tooltip);
        assertFalse(tooltip.contains(PREFIX));
    }

    private static void assertIngredient(MatterResearchPanel panel, int x, int y, Item item, long amount, int rowTop) {
        var ingredient = panel.ingredientAt(x, y);
        assertNotNull(ingredient);
        assertEquals(AEItemKey.of(item), ingredient.stack().what());
        assertEquals(amount, ingredient.stack().amount());
        assertEquals(212, ingredient.bounds().getX()); assertEquals(rowTop, ingredient.bounds().getY());
        assertEquals(166, ingredient.bounds().getWidth()); assertEquals(24, ingredient.bounds().getHeight());
        assertTrue(ingredient.bounds().contains(x, y));
    }

    private static void assertHeading(UiRenderRecorder ink, String key) {
        String heading = tr(key).getString();
        assertFalse(heading.contains(PREFIX));
        assertEquals(1, ink.text.stream().filter(text -> text.value().equals(heading)).count());
        assertFalse(ink.text.stream().anyMatch(text -> text.value().equals(tr(key.equals("materials") ? "prerequisites_materials" : "materials").getString())));
    }
    private static void assertClip(UiRenderRecorder ink) {
        assertEquals(1, ink.clips); assertEquals(0, ink.clipDepth); assertEquals(2, ink.flushes);
        var clip = ink.scissors.getFirst();
        assertEquals(211, clip.getX()); assertEquals(172, clip.getY());
        assertEquals(168, clip.getWidth()); assertEquals(76, clip.getHeight());
    }
    private static UiRenderRecorder.Text exact(UiRenderRecorder ink, String value) {
        return ink.text.stream().filter(text -> text.value().equals(value)).findFirst()
                .orElseThrow(() -> new AssertionError("Missing exact text " + value + " in " + ink.text));
    }
    private static List<UiRenderRecorder.Text> listText(UiRenderRecorder ink) {
        return ink.text.stream().filter(text -> !text.value().isEmpty() && text.transform().m30() >= 211
                && text.transform().m31() >= 172 && text.transform().m31() < 248).toList();
    }
    private static void assertRow(VanillaFontTestFixture fonts, UiRenderRecorder ink, Component value,
            int center, int left, int right, int color) {
        var text = listText(ink).stream().filter(candidate -> candidate.value().equals(value.getString())
                && Math.abs((bounds(fonts, candidate)[1] + bounds(fonts, candidate)[3]) / 2 - center) < 1.25f)
                .findFirst().orElseThrow(() -> new AssertionError("Missing row text " + value.getString() + " at " + center + " in " + listText(ink)));
        assertEquals(color, text.color());
        assertInk(fonts, text, center, left, right);
    }
    private static void assertInk(VanillaFontTestFixture fonts, UiRenderRecorder.Text text, int center, int left, int right) {
        var bounds = bounds(fonts, text);
        assertEquals(7.5f / 9, text.transform().m00(), .001f, "Use readable real default-font metrics, not a shrunken replacement font");
        assertFalse(text.shadow());
        assertTrue(bounds[0] >= left - .5f && bounds[2] <= right + .5f, text.toString());
        assertEquals(center, (bounds[1] + bounds[3]) / 2, 1.25f, text.toString());
    }
    private static float[] bounds(VanillaFontTestFixture fonts, UiRenderRecorder.Text text) {
        float cursor = text.x(), minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY;
        for (int cp : text.value().codePoints().toArray()) {
            var glyph = fonts.glyph(VanillaFontTestFixture.DEFAULT_FONT, cp);
            var bounds = fonts.inkBounds(glyph);
            if (bounds[0] < bounds[2] && bounds[1] < bounds[3]) {
                minX = Math.min(minX, cursor + bounds[0]); maxX = Math.max(maxX, cursor + bounds[2]);
                minY = Math.min(minY, text.y() + bounds[1]); maxY = Math.max(maxY, text.y() + bounds[3]);
            }
            cursor += glyph.getAdvance();
        }
        var a = text.transform().transformPosition(new Vector3f(minX, minY, 0));
        var b = text.transform().transformPosition(new Vector3f(maxX, maxY, 0));
        return new float[] {a.x, a.y, b.x, b.y};
    }

    /** Only the decorative 18x18 slot mesh is suppressed; the production list, text, item and hover code all execute. */
    private static UiRenderRecorder draw(Panel input) throws Exception {
        var ink = new UiRenderRecorder();
        var pose = new org.joml.Matrix4f(ink.pose().last().pose());
        var renderer = OsqSpriteRenderer.class;
        var keyType = Class.forName(renderer.getName() + "$Key");
        var meshType = Class.forName(renderer.getName() + "$Mesh");
        var keyConstructor = keyType.getDeclaredConstructor(ResourceLocation.class, int.class, int.class, boolean.class);
        var meshConstructor = meshType.getDeclaredConstructor(ResourceLocation.class, float[].class, boolean.class);
        keyConstructor.setAccessible(true); meshConstructor.setAccessible(true);
        var key = keyConstructor.newInstance(ResourceLocation.parse(input.ui.sprite("slot")), 18, 18, false);
        @SuppressWarnings("unchecked") var cache = (Map<Object, Object>) field(renderer, "CACHE");
        var savedCache = new LinkedHashMap<>(cache);
        var blend = field(GlStateManager.class, "BLEND");
        var mode = field(blend, "mode");
        var thread = declaredField(RenderSystem.class, "renderThread");
        var enabled = declaredField(mode.getClass(), "enabled");
        var src = declaredField(blend.getClass(), "srcRgb");
        var dst = declaredField(blend.getClass(), "dstRgb");
        var srcAlpha = declaredField(blend.getClass(), "srcAlpha");
        var dstAlpha = declaredField(blend.getClass(), "dstAlpha");
        var oldThread = thread.get(null); var oldEnabled = enabled.get(mode);
        var oldSrc = src.get(blend); var oldDst = dst.get(blend);
        var oldSrcAlpha = srcAlpha.get(blend); var oldDstAlpha = dstAlpha.get(blend);
        try {
            thread.set(null, Thread.currentThread()); enabled.set(mode, true);
            src.set(blend, 770); dst.set(blend, 771); srcAlpha.set(blend, 1); dstAlpha.set(blend, 0);
            cache.put(key, meshConstructor.newInstance(null, new float[0], false));
            input.panel.drawForeground(ink);
            assertEquals(pose, ink.pose().last().pose(), "Drawing restores the caller's pose");
            return ink;
        } finally {
            cache.clear(); cache.putAll(savedCache);
            thread.set(null, oldThread); enabled.set(mode, oldEnabled);
            src.set(blend, oldSrc); dst.set(blend, oldDst); srcAlpha.set(blend, oldSrcAlpha); dstAlpha.set(blend, oldDstAlpha);
        }
    }

    private static Field declaredField(Class<?> type, String name) throws Exception {
        var field = type.getDeclaredField(name); field.setAccessible(true); return field;
    }
    private static Object field(Object target, String name) throws Exception {
        Class<?> owner = target instanceof Class<?> type ? type : target.getClass();
        return declaredField(owner, name).get(target instanceof Class<?> ? null : target);
    }
}
