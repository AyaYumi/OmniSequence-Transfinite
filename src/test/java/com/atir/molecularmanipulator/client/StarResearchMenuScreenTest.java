package com.atir.molecularmanipulator.client;

import static org.junit.jupiter.api.Assertions.*;

import appeng.client.gui.WidgetContainer;
import appeng.client.gui.style.ScreenStyle;
import com.atir.molecularmanipulator.menu.StarComputationEngineMenu;
import com.atir.molecularmanipulator.menu.StarComputationEngineMenu.ActionResult;
import com.atir.molecularmanipulator.starcomputation.StarComputationBenefits;
import com.atir.molecularmanipulator.starcomputation.StarComputationRecipeCatalog;
import com.atir.molecularmanipulator.starcomputation.StarComputationUpgradeCatalog;
import com.atir.molecularmanipulator.starcomputation.StarOrreryLayout;
import com.atir.molecularmanipulator.starcomputation.StarResearchMenuNodes;
import com.atir.molecularmanipulator.starcomputation.StarResearchMenuNodes.Node;
import com.atir.molecularmanipulator.starcomputation.StarResearchMenuNodes.Prerequisite;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.lwjgl.glfw.GLFW;

/** Production labels and native list input without a game window, JavaScript or simulated payment. */
class StarResearchMenuScreenTest {
    private static final ResourceLocation MATERIAL = ResourceLocation.parse("molecularmanipulator:material");

    @BeforeAll
    static void bootstrap() {
        if (net.neoforged.fml.loading.LoadingModList.get() == null) {
            net.neoforged.fml.loading.LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        }
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    @Test
    void selectedDefinitionPriceLabelActualLevelAndBothThresholdScalesFollowCurrentMenuSnapshot() throws Exception {
        var menu = menu();
        var screen = screen(menu, 0);
        replace(menu, new Node(0, MATERIAL, "Overridden materials", 2, 7, List.of(), "", false));
        assertSame(menu.node(0), call(screen, "selected"));
        assertEquals("Overridden materials", name(menu.node(0)));
        assertTrue(menu.canUnlock(StarComputationEngineMenu.NODES.get(1)), "Legacy API resolves the material ID, not list slot 1");
        assertEquals(StarComputationBenefits.threshold(2), menu.nodeThreshold(StarComputationEngineMenu.NODES.get(1)));
        menu.starOrrery = true;
        assertEquals(StarOrreryLayout.threshold(2), menu.nodeThreshold(menu.node(0)));
        assertEquals("gui.molecularmanipulator.star_computation.pay_unlock", label(screen).getKey());
        assertArrayEquals(new Object[] {"7"}, label(screen).getArgs());

        replace(menu, new Node(0, MATERIAL, "Reloaded materials", 8, Long.MAX_VALUE, List.of(), "", false));
        assertEquals("Reloaded materials", name((Node) call(screen, "selected")));
        menu.effectiveEntropy = Long.MAX_VALUE;
        assertEquals(ActionResult.INSUFFICIENT_ENTROPY, menu.unlockStatus(menu.node(0)), "Entropy cannot fake an actual level");
        assertTrue(label(screen).getKey().endsWith("result.insufficient_entropy"));
        menu.benefitLevel = 8;
        assertTrue(label(screen).getKey().endsWith("result.insufficient_information"));
        menu.infoBalance = Long.MAX_VALUE;
        assertTrue(menu.canUnlock(menu.node(0)));
        assertArrayEquals(new Object[] {"9,223,372,036,854,775,807"}, label(screen).getArgs());
    }

    @Test
    void customPrerequisitesUseSyncedPermanentStateAndShowFullNamesIdsAndCommandHints() throws Exception {
        var menu = menu();
        var screen = screen(menu, 0);
        var custom = ResourceLocation.parse("kubejs:first_contact");
        var node = new Node(0, MATERIAL, "New material definition", 1, 0,
                List.of(new Prerequisite(custom, "First contact", false)), "", false);
        replace(menu, node);
        menu.unlockMask = StarComputationEngineMenu.ALL_UNLOCKS;
        assertFalse(menu.canUnlock(menu.node(0)), "Saved bits do not replace synchronized prerequisite ownership");
        assertTrue(label(screen).getKey().endsWith("result.missing_prerequisites"));
        var summary = (Component) invoke(screen, "prerequisiteNames", Node.class, node);
        assertTrue(((TranslatableContents) summary.getContents()).getKey().endsWith("prerequisites_needed"));
        assertEquals("First contact", ((Component) ((TranslatableContents) summary.getContents()).getArgs()[0]).getString());
        @SuppressWarnings("unchecked") var lines = (List<Component>) invoke(screen, "researchTooltip", Node.class, node);
        assertTrue(lines.stream().anyMatch(line -> line.getString().equals("/starresearch unlock kubejs:first_contact")));
        var prerequisiteLine = lines.stream().filter(line -> line.getContents() instanceof TranslatableContents contents
                && contents.getKey().endsWith("prerequisites_needed")).findFirst().orElseThrow();
        var fullTitle = (Component) ((TranslatableContents) prerequisiteLine.getContents()).getArgs()[0];
        assertTrue(fullTitle.getString().contains("First contact [kubejs:first_contact]"));
        call(screen, "selectPrerequisite");
        assertSame(menu.node(0), call(screen, "selected"), "An absent prerequisite has a command hint, not an invented row");
        replace(menu, new Node(0, MATERIAL, node.title(), 1, 0,
                List.of(new Prerequisite(custom, "First contact", true)), "", false));
        assertTrue(menu.canUnlock(menu.node(0)));
    }

    @Test
    void prerequisiteNavigationResolvesCustomIdsBeyondTheViewportInsteadOfOldBuiltinMasks() throws Exception {
        var menu = menu();
        menu.researchNodes = directory(14);
        var screen = screen(menu, 1);
        var parent = menu.node(13);
        replace(menu, new Node(1, menu.node(1).id(), "Material override", 1, 0, List.of(
                new Prerequisite(ResourceLocation.parse("removed:node"), "Removed", false),
                new Prerequisite(menu.node(2).id(), "Paid parent", true),
                new Prerequisite(parent.id(), "Renamed custom parent", false)), "", false));
        call(screen, "selectPrerequisite");
        assertEquals(parent.id(), ((Node) call(screen, "selected")).id());
        assertEquals(13, field(screen, "selectedNode"));
        assertEquals(9, field(screen, "researchScroll"), "Selecting a prerequisite reveals its existing viewport row");
    }

    @Test
    void invalidDefinitionsShowErrorsAndPermanentPurchasesUseTheSyncedFlagNotListOrLegacyBits() throws Exception {
        var menu = menu();
        var screen = screen(menu, 0);
        var custom = ResourceLocation.parse("kubejs:broken_override");
        var invalid = new Node(0, custom, "Broken override", 1, 0, List.of(), "Missing prerequisite: removed:node", false);
        replace(menu, invalid);
        menu.unlockMask = StarComputationEngineMenu.ALL_UNLOCKS;
        assertFalse(menu.canUnlock(invalid));
        assertTrue(label(screen).getKey().endsWith("result.invalid_node"));
        @SuppressWarnings("unchecked") var lines = (List<Component>) invoke(screen, "researchTooltip", Node.class, invalid);
        assertTrue(lines.stream().anyMatch(line -> line.getString().contains("Missing prerequisite: removed:node")));
        menu.unlockMask = 0;
        replace(menu, new Node(0, custom, invalid.title(), 1, 0, List.of(), invalid.error(), true));
        assertTrue(label(screen).getKey().endsWith("unlocked_permanent"));
        assertFalse(menu.canUnlock(menu.node(0)));
    }

    @Test
    void fallbackSnapshotExposesOnlyTheFreeL3MaterialAndKeepsHiddenSaveBitsOutOfTheDirectory() throws Exception {
        var menu = menu();
        menu.unlockMask = StarComputationEngineMenu.ALL_UNLOCKS & ~(1 << 1);
        menu.researchNodes = StarResearchMenuNodes.fallback(menu.unlockMask);
        assertSame(menu.researchNodes.nodes(), menu.nodes());
        assertEquals(5, StarComputationEngineMenu.NODES.size(), "Archival compatibility is not the runtime directory");
        assertEquals(1, menu.nodes().size());
        var material = menu.node(0);
        assertEquals(MATERIAL, material.id());
        assertEquals(0, material.index());
        assertEquals(3, material.requiredLevel());
        assertEquals(0, material.informationCost());
        assertTrue(material.prerequisites().isEmpty());
        assertFalse(material.unlocked(), "Hidden purchased foundation/quantum/terminal/transfinite nodes stay hidden");
        assertEquals(StarComputationBenefits.threshold(3), menu.nodeThreshold(StarComputationEngineMenu.NODES.get(1)));
        assertFalse(menu.canUnlock(StarComputationEngineMenu.NODES.getFirst()));
        assertEquals(0, menu.nodeThreshold(StarComputationEngineMenu.NODES.getFirst()));
        menu.benefitLevel = 3;
        menu.infoBalance = 0;
        assertTrue(menu.canUnlock(material));
        menu.unlockMask |= 1 << 1;
        assertFalse(menu.node(0).unlocked(), "A mask change alone does not replace synchronized permanent state");
        menu.researchNodes = StarResearchMenuNodes.fallback(menu.unlockMask);
        assertTrue(menu.node(0).unlocked(), "Material's saved bit is still bit 1, not visible list index 0");
    }

    @Test
    void syncedResearchWithoutRecipeLocksShowsAnEmptyStateRatherThanWaitingForever() throws Exception {
        var menu = menu();
        var screen = screen(menu, 0);
        replace(menu, new Node(0, ResourceLocation.parse("kubejs:standalone"), "Standalone", 3, 0,
                List.of(), "", false));
        menu.catalog = StarComputationRecipeCatalog.EMPTY;
        var waiting = (Component) invoke(screen, "catalogPlaceholder", Node.class, menu.node(0));
        assertTrue(((TranslatableContents) waiting.getContents()).getKey().endsWith("catalog_waiting"));
        menu.catalog = new StarComputationRecipeCatalog(List.of(List.of()));
        var empty = (Component) invoke(screen, "catalogPlaceholder", Node.class, menu.node(0));
        assertTrue(((TranslatableContents) empty.getContents()).getKey().endsWith("catalog_empty"));
        @SuppressWarnings("unchecked") var tooltip = (List<Component>) call(screen, "contentsTooltip");
        assertTrue(tooltip.contains(empty));
        assertFalse(tooltip.contains(waiting));
    }

    private static Stream<Arguments> viewports() {
        return Stream.of("en_us", "zh_cn").flatMap(language -> Stream.of(
                Arguments.of(language, 960, 540), Arguments.of(language, 640, 360), Arguments.of(language, 320, 240)));
    }

    @ParameterizedTest(name = "{0}, {1}x{2}")
    @MethodSource("viewports")
    void wheelAnywhereInTheListNeedsNoFocusAndEveryCustomNodeRemainsPointerSelectable(String language, int width, int height)
            throws Exception {
        try (var fonts = new VanillaFontTestFixture(language)) {
            var input = input(fonts, width, height);
            var screen = input.screen;
            var first = input.rows.getFirst();
            double x = screen.responsiveScreenX(first.getX() + 2);
            // Deliberately between buttons: wheel input covers the entire list, not just a focused child.
            double y = screen.responsiveScreenY(first.getBottom() + 1);
            assertNull(screen.getFocused());
            assertTrue(screen.mouseScrolled(x, y, 0, -1));
            assertEquals(1, field(screen, "researchScroll"));
            assertNull(screen.getFocused(), "Wheel input must not acquire focus");
            assertTrue(screen.mouseScrolled(x, y, 0, 1));
            assertEquals(0, field(screen, "researchScroll"));

            screen.setFocused(input.guide);
            var visited = new HashSet<ResourceLocation>();
            for (int offset = 0; offset <= 9; offset++) {
                assertEquals(offset, field(screen, "researchScroll"));
                assertSame(input.guide, screen.getFocused());
                for (int row = 0; row < 5; row++) {
                    var button = input.rows.get(row);
                    assertTrue(button.visible && button.active);
                    assertTrue(screen.screenContent.owns(button));
                    double rawX = screen.responsiveScreenX(button.getX() + button.getWidth() / 2);
                    double rawY = screen.responsiveScreenY(button.getY() + button.getHeight() / 2);
                    assertTrue(button.isMouseOver(screen.logicalMouseX(rawX), screen.logicalMouseY(rawY)));
                    button.onPress();
                    var selected = (Node) call(screen, "selected");
                    assertEquals(offset + row, selected.index());
                    assertEquals(selected.id(), field(screen, "selectedResearchId"));
                    visited.add(selected.id());
                    assertNotNull(button.getTooltip());
                }
                // The narrow scrollbar itself must also be a wheel target at responsive scale.
                assertTrue(screen.mouseScrolled(screen.responsiveScreenX(input.scrollbar.getX() + 2), y, 0, -1));
                assertFalse(screen.isDragging());
            }
            assertEquals(new HashSet<>(input.menu.nodes().stream().map(Node::id).toList()), visited);
            assertEquals(9, field(screen, "researchScroll"), "Bottom wheel events clamp without wrapping");
            assertSame(input.guide, screen.getFocused());
            for (int i = 0; i < 12; i++) assertTrue(screen.mouseScrolled(x, y, 0, 1));
            assertEquals(0, field(screen, "researchScroll"));
            assertSame(input.guide, screen.getFocused());
            var bounds = screen.responsiveBounds();
            assertTrue(bounds.getX() >= 3 && bounds.getY() >= 3);
            assertTrue(bounds.getX() + bounds.getWidth() <= width - 3 && bounds.getY() + bounds.getHeight() <= height - 3);

            assertFalse(screen.mouseScrolled(x, y, 1, 0));
            assertFalse(screen.mouseScrolled(screen.responsiveScreenX(first.getX() - 8), y, 0, -1));
            assertFalse(screen.mouseScrolled(x, screen.responsiveScreenY(first.getY() - 8), 0, -1));
            input.scrollbar.visible = false;
            assertFalse(screen.mouseScrolled(x, y, 0, -1));
            input.scrollbar.visible = true;
            ResponsiveScreenTestFixture.set(screen, "recipesPage", false);
            assertFalse(screen.mouseScrolled(x, y, 0, -1));
            assertEquals(0, field(screen, "researchScroll"));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"en_us", "zh_cn"})
    void scrollbarDragsThroughResponsiveScreenCoordinatesOutsideItsBoundsAndReleasesCapture(String language) throws Exception {
        try (var fonts = new VanillaFontTestFixture(language)) {
            var input = input(fonts, 320, 240);
            var screen = input.screen;
            var bar = input.scrollbar;
            assertTrue(bar.visible && screen.screenContent.owns(bar));
            int grabY = bar.getY() + 2;
            // As in the upgrade-input fixture, skip only the native click sound and reproduce native capture.
            assertTrue(bar.isMouseOver(screen.logicalMouseX(screen.responsiveScreenX(bar.getX() + 2)),
                    screen.logicalMouseY(screen.responsiveScreenY(grabY))));
            bar.onClick(bar.getX() + 2, grabY);
            screen.setFocused(bar);
            screen.setDragging(true);
            assertEquals(true, field(bar, "draggingScrollbar"));
            int thumb = (int) callOn(bar, "thumbHeight");
            double x = screen.responsiveScreenX(bar.getX() + 2);
            double start = screen.responsiveScreenY(grabY);
            double halfway = screen.responsiveScreenY(grabY + (bar.getHeight() - thumb) / 2);
            assertTrue(screen.mouseDragged(x, halfway, 0, 0, halfway - start));
            assertEquals(4.5, (int) field(screen, "researchScroll"), 1, "Drag transforms must use logical list coordinates");
            double outside = screen.responsiveScreenX(bar.getX() - 30);
            double bottom = screen.responsiveScreenY(bar.getBottom() + 30);
            double top = screen.responsiveScreenY(bar.getY() - 30);
            assertTrue(screen.mouseDragged(outside, bottom, 0, outside - x, bottom - halfway));
            assertEquals(9, field(screen, "researchScroll"));
            var ink = new UiRenderRecorder();
            var render = bar.getClass().getDeclaredMethod("renderWidget", GuiGraphics.class, int.class, int.class, float.class);
            render.setAccessible(true);
            render.invoke(bar, ink, -1, -1, 0f);
            assertEquals(2, ink.rects.size(), "Render the real track and thumb, not a synthetic scrollbar");
            assertEquals(bar.getBottom(), ink.rects.getLast().bottom());
            assertTrue(screen.mouseDragged(outside, top, 0, 0, top - bottom));
            assertEquals(0, field(screen, "researchScroll"));
            assertTrue(screen.mouseReleased(outside, top, 0));
            assertFalse(screen.isDragging());
            assertEquals(false, field(bar, "draggingScrollbar"));
            bar.mouseDragged(bar.getX(), bar.getBottom() + 40, 0, 0, 80);
            assertEquals(0, field(screen, "researchScroll"), "Released scrollbar callbacks cannot keep moving the list");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"en_us", "zh_cn"})
    void focusedKeyboardNavigationSelectsAndRevealsEveryNodeIncludingEndHomeAndPageJumps(String language) throws Exception {
        try (var fonts = new VanillaFontTestFixture(language)) {
            var input = input(fonts, 320, 240);
            var screen = input.screen;
            screen.setFocused(input.scrollbar);
            for (int[] step : new int[][] {
                    {GLFW.GLFW_KEY_END, 13}, {GLFW.GLFW_KEY_DOWN, 13}, {GLFW.GLFW_KEY_HOME, 0}, {GLFW.GLFW_KEY_UP, 0},
                    {GLFW.GLFW_KEY_DOWN, 1}, {GLFW.GLFW_KEY_UP, 0}, {GLFW.GLFW_KEY_PAGE_DOWN, 5},
                    {GLFW.GLFW_KEY_PAGE_DOWN, 10}, {GLFW.GLFW_KEY_PAGE_UP, 5}, {GLFW.GLFW_KEY_HOME, 0}}) {
                assertTrue(screen.keyPressed(step[0], 0, 0));
                assertRevealed(input, step[1]);
            }
            for (int index = 1; index < input.menu.nodes().size(); index++) {
                assertTrue(screen.keyPressed(GLFW.GLFW_KEY_DOWN, 0, 0));
                assertRevealed(input, index);
            }
            for (int index = input.menu.nodes().size() - 2; index >= 0; index--) {
                assertTrue(screen.keyPressed(GLFW.GLFW_KEY_UP, 0, 0));
                assertRevealed(input, index);
            }
        }
    }

    @Test
    void customPurchaseControlsFollowSyncedOwnershipAndPrerequisiteNavigationRevealsTheLastRow() throws Exception {
        try (var fonts = new VanillaFontTestFixture("en_us")) {
            var input = input(fonts, 320, 240);
            var screen = input.screen;
            var menu = input.menu;
            var child = menu.node(1);
            var parent = menu.node(13);
            replace(menu, new Node(1, child.id(), child.title(), 1, 7,
                    List.of(new Prerequisite(parent.id(), parent.title(), false)), "", false));
            invoke(screen, "selectNode", int.class, 1);
            var unlock = (Button) field(screen, "unlock");
            var prerequisites = (Button) field(screen, "prerequisites");
            assertFalse(unlock.active);
            assertTrue(prerequisites.visible && prerequisites.active);
            prerequisites.onPress();
            assertEquals(parent.id(), ((Node) call(screen, "selected")).id());
            assertEquals(9, field(screen, "researchScroll"));
            assertTrue(input.rows.getLast().visible);
            assertTrue(unlock.visible && unlock.active, "Custom IDs beyond row five are purchasable");
            assertArrayEquals(new Object[] {"7"}, label(screen).getArgs());
            unlock.onPress(); // Real callback, with explicitly server-side/no-op headless transport.
            assertEquals(0, field(screen, "pendingVersion"));
            assertFalse(unlock.active);
            assertFalse(menu.node(13).unlocked(), "The client must never invent successful payment or ownership");
            replace(menu, new Node(13, parent.id(), parent.title(), 1, 7, List.of(), "", true));
            menu.resultVersion++;
            screen.containerTick();
            assertEquals(-1, field(screen, "pendingVersion"));
            assertEquals(0, menu.unlockMask, "Custom ownership needs no legacy bit");
            assertFalse(unlock.active);
            assertTrue(label(screen).getKey().endsWith("unlocked_permanent"));
        }
    }

    @Test
    void selectedIdSurvivesReorderingAndRemovalSelectsTheDefaultRatherThanTheOldSlotOccupant() throws Exception {
        try (var fonts = new VanillaFontTestFixture("en_us")) {
            var input = input(fonts, 320, 240);
            var screen = input.screen;
            invoke(screen, "selectNode", int.class, 12);
            var id = input.menu.node(12).id();
            var nodes = new ArrayList<>(input.menu.nodes());
            Collections.swap(nodes, 1, 12);
            input.menu.researchNodes = reindex(nodes);
            call(screen, "updateControls");
            assertEquals(id, ((Node) call(screen, "selected")).id());
            assertEquals(1, field(screen, "selectedNode"));
            assertEquals(id, field(screen, "selectedResearchId"));
            nodes.remove(1);
            input.menu.researchNodes = reindex(nodes);
            call(screen, "updateControls");
            assertEquals(MATERIAL, ((Node) call(screen, "selected")).id());
            assertEquals(0, field(screen, "selectedNode"));
            input.menu.researchNodes = StarResearchMenuNodes.FALLBACK;
            call(screen, "updateControls");
            assertEquals(0, field(screen, "researchScroll"));
            assertFalse(input.scrollbar.visible);
            assertEquals(1, input.rows.stream().filter(button -> button.visible).count());
        }
    }

    private record Input(StarComputationEngineScreen screen, StarComputationEngineMenu menu,
                         List<Button> rows, AbstractWidget scrollbar, Button guide) {}

    @SuppressWarnings("unchecked")
    private static Input input(VanillaFontTestFixture fonts, int width, int height) throws Exception {
        var menu = menu();
        menu.researchNodes = directory(14);
        var player = ResponsiveScreenTestFixture.allocate(net.minecraft.server.level.ServerPlayer.class);
        ResponsiveScreenTestFixture.set(player, "level", ResponsiveScreenTestFixture.allocate(net.minecraft.server.level.ServerLevel.class));
        ResponsiveScreenTestFixture.set(menu, "playerInventory", new net.minecraft.world.entity.player.Inventory(player));
        var screen = screen(menu, 0);
        var ui = new OsqUi("star_computation_engine");
        for (String name : List.of("children", "renderables", "narratables", "screenWidgets", "nodeButtons", "levelButtons"))
            ResponsiveScreenTestFixture.set(screen, name, new ArrayList<>());
        ResponsiveScreenTestFixture.set(screen, "screenContent", new ResponsiveScreenWidgets());
        ResponsiveScreenTestFixture.set(screen, "widgets", new WidgetContainer(new ScreenStyle()));
        ResponsiveScreenTestFixture.set(screen, "ui", ui);
        ResponsiveScreenTestFixture.set(screen, "font", fonts.font);
        ResponsiveScreenTestFixture.set(screen, "minecraft", Minecraft.getInstance());
        ResponsiveScreenTestFixture.set(screen, "recipesPage", true);
        call(screen, "configureRecipePage");
        screen.width = width;
        screen.height = height;
        ResponsiveScreenTestFixture.set(screen, "imageWidth", ui.layout.width);
        ResponsiveScreenTestFixture.set(screen, "imageHeight", ui.layout.height - 27);
        ResponsiveScreenTestFixture.set(screen, "leftPos", (width - ui.layout.width) / 2);
        ResponsiveScreenTestFixture.set(screen, "topPos", (height - (ui.layout.height - 27)) / 2);
        ui.hide(entry -> "ctl".equals(entry.kind) || "edge".equals(entry.kind));
        call(screen, "initControls");
        call(screen, "updateControls");
        var rows = (List<Button>) field(screen, "nodeButtons");
        var bar = (AbstractWidget) field(screen, "researchScrollbar");
        assertEquals(5, rows.size(), "Five existing visual rows are a viewport, not a directory size limit");
        assertTrue(screen.children().contains(bar));
        return new Input(screen, menu, rows, bar, (Button) field(screen, "guide"));
    }

    private static void assertRevealed(Input input, int index) throws Exception {
        int scroll = (int) field(input.screen, "researchScroll");
        assertEquals(index, ((Node) call(input.screen, "selected")).index());
        assertEquals(input.menu.node(index).id(), field(input.screen, "selectedResearchId"));
        assertTrue(index >= scroll && index < scroll + 5);
        var row = input.rows.get(index - scroll);
        assertSame(row, input.screen.getFocused());
        assertTrue(row.visible && row.active && row.isFocused());
    }

    private static StarResearchMenuNodes directory(int count) {
        var nodes = new ArrayList<Node>();
        nodes.add(StarResearchMenuNodes.FALLBACK.node(0));
        for (int index = 1; index < count; index++) nodes.add(new Node(index,
                ResourceLocation.parse("kubejs:research_" + index), "Custom study " + index, 1, 7, List.of(), "", false));
        return new StarResearchMenuNodes(nodes);
    }

    private static StarResearchMenuNodes reindex(List<Node> source) {
        var nodes = new ArrayList<Node>();
        for (var node : source) nodes.add(new Node(nodes.size(), node.id(), node.title(), node.requiredLevel(),
                node.informationCost(), node.prerequisites(), node.error(), node.unlocked()));
        return new StarResearchMenuNodes(nodes);
    }

    private static StarComputationEngineMenu menu() throws Exception {
        var menu = ResponsiveScreenTestFixture.allocate(StarComputationEngineMenu.class);
        menu.researchNodes = StarResearchMenuNodes.FALLBACK;
        menu.upgrades = StarComputationUpgradeCatalog.EMPTY;
        menu.catalog = StarComputationRecipeCatalog.EMPTY;
        menu.canConfigure = menu.formed = menu.networkOnline = true;
        menu.benefitLevel = 2;
        menu.infoBalance = 7;
        menu.actionResult = ActionResult.NONE;
        return menu;
    }

    private static StarComputationEngineScreen screen(StarComputationEngineMenu menu, int selected) throws Exception {
        var screen = ResponsiveScreenTestFixture.allocate(StarComputationEngineScreen.class);
        ResponsiveScreenTestFixture.set(screen, "menu", menu);
        ResponsiveScreenTestFixture.set(screen, "pendingVersion", -1);
        ResponsiveScreenTestFixture.set(screen, "selectedNode", selected);
        return screen;
    }

    private static void replace(StarComputationEngineMenu menu, Node node) {
        var nodes = new ArrayList<>(menu.nodes());
        nodes.set(node.index(), node);
        menu.researchNodes = new StarResearchMenuNodes(nodes);
    }

    private static String name(Node node) throws Exception {
        return ((Component) invoke(null, "nodeName", Node.class, node)).getString();
    }

    private static TranslatableContents label(StarComputationEngineScreen screen) throws Exception {
        return (TranslatableContents) ((Component) call(screen, "unlockLabel")).getContents();
    }

    private static Object call(Object target, String name) throws Exception {
        Method method = StarComputationEngineScreen.class.getDeclaredMethod(name);
        method.setAccessible(true);
        return method.invoke(target);
    }

    private static Object callOn(Object target, String name) throws Exception {
        Method method = target.getClass().getDeclaredMethod(name);
        method.setAccessible(true);
        return method.invoke(target);
    }

    private static Object invoke(Object target, String name, Class<?> type, Object argument) throws Exception {
        Method method = StarComputationEngineScreen.class.getDeclaredMethod(name, type);
        method.setAccessible(true);
        return method.invoke(target, argument);
    }

    private static Object field(Object target, String name) throws Exception {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) try {
            var field = type.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(target);
        } catch (NoSuchFieldException ignored) { }
        throw new NoSuchFieldException(name);
    }
}
