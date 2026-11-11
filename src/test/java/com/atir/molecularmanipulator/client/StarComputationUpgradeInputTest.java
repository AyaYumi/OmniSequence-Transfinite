package com.atir.molecularmanipulator.client;

import static org.junit.jupiter.api.Assertions.*;

import appeng.client.gui.WidgetContainer;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.style.WidgetStyle;
import appeng.client.gui.widgets.Scrollbar;
import com.atir.molecularmanipulator.client.StarComputationEngineScreen.RequirementViewport;
import com.atir.molecularmanipulator.menu.StarComputationEngineMenu;
import com.atir.molecularmanipulator.starcomputation.StarComputationRecipeCatalog;
import com.atir.molecularmanipulator.starcomputation.StarComputationUpgradeCatalog;
import com.atir.molecularmanipulator.starcomputation.StarComputationUpgradeCatalog.Row;
import com.atir.molecularmanipulator.starcomputation.StarResearchMenuNodes;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import net.minecraft.client.InputType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.lwjgl.glfw.GLFW;

/** Native upgrade-list input must not disappear into AE2's separate WidgetContainer. */
class StarComputationUpgradeInputTest {
    private static final String PREFIX = "gui.molecularmanipulator.star_computation.upgrade.";

    private static Stream<Arguments> viewports() {
        return Stream.of("en_us", "zh_cn").flatMap(language -> Stream.of(
                Arguments.of(language, 960, 540),
                Arguments.of(language, 640, 360),
                Arguments.of(language, 320, 240)));
    }

    @ParameterizedTest(name = "{0}, {1}x{2}")
    @MethodSource("viewports")
    void pointerWheelScrollsWithoutFocusAndPreservesAnotherControlsFocus(String language, int width, int height)
            throws Exception {
        try (var fonts = new VanillaFontTestFixture(language)) {
            var input = input(fonts, width, height, language);
            var screen = input.screen;
            var list = input.requirements;
            // Near the left edge: on a fitted screen these raw coordinates are NOT inside the logical box.
            double x = screen.responsiveScreenX(list.getX() + 2);
            double y = screen.responsiveScreenY(list.getY() + list.getHeight() / 2);
            assertNull(screen.getFocused());
            assertFalse(list.isFocused());

            assertTrue(screen.mouseScrolled(x, y, 0, -1));
            assertPosition(input, 24);
            assertNull(screen.getFocused(), "Hovering and wheeling must not acquire keyboard focus");
            assertFalse(list.isFocused());
            assertTrue(screen.mouseScrolled(x, y, 0, 1));
            assertPosition(input, 0);
            assertNull(screen.getFocused());

            screen.setFocused(input.otherControl);
            for (double[] step : new double[][] {
                    {-1, 24}, {-1, 48}, {1, 24},
                    {-10000, input.maxScroll()}, {-1, input.maxScroll()}, {10000, 0}, {1, 0}}) {
                assertTrue(screen.mouseScrolled(x, y, 0, step[0]));
                assertPosition(input, step[1]);
                assertSame(input.otherControl, screen.getFocused());
                assertTrue(input.otherControl.isFocused());
                assertFalse(list.isFocused(), "Wheel input cannot steal another native control's focus");
                assertFalse(screen.isDragging());
            }
        }
    }

    @ParameterizedTest(name = "{0}, {1}x{2}")
    @MethodSource("viewports")
    void hoveringShowsTheCorrectRowsDetailsWithoutClickingAndFollowsScroll(String language, int width, int height)
            throws Exception {
        try (var fonts = new VanillaFontTestFixture(language)) {
            var input = input(fonts, width, height, language);
            var screen = input.screen;
            var list = input.requirements;
            double x = screen.responsiveScreenX(list.getX() + 15);
            double y = screen.responsiveScreenY(list.getY() + list.getHeight() / 2);
            assertNull(screen.getFocused());
            assertFalse(list.isFocused());
            assertHoveredDetails(input, x, y);
            assertNull(screen.getFocused(), "Reading a tooltip requires no focus or click");
            boolean nestedSeen = false;
            var seen = new java.util.HashSet<String>();
            for (int step = 0; step < 12; step++) {
                assertTrue(screen.mouseScrolled(x, y, 0, -1));
                var hovered = assertHoveredDetails(input, x, y);
                nestedSeen |= hovered.depth() > 0;
                seen.add(hovered.text().getString());
                assertNull(screen.getFocused());
                assertFalse(list.isFocused());
            }
            assertTrue(nestedSeen, "Hover must resolve nested requirement rows, not just the basic checks");
            assertTrue(seen.size() > 3, "The tip must follow the row under the pointer after scrolling");
            screen.setFocused(input.otherControl);
            assertTrue(screen.mouseScrolled(x, y, 0, -10000));
            assertPosition(input, input.maxScroll());
            var last = input.viewport.rows.getLast();
            // The bottom row remains readable even with indentation and a wrapped title.
            double lastY = screen.responsiveScreenY(list.getBottom() - 4);
            assertSame(last.row(), assertHoveredDetails(input, x, lastY));
            assertSame(input.otherControl, screen.getFocused(), "Hovering must not steal another control's focus");
            assertFalse(list.isFocused());
            assertTrue(screen.mouseScrolled(x, y, 0, 10000));
            assertPosition(input, 0);
            assertHoveredDetails(input, x, y);
            assertSame(input.otherControl, screen.getFocused());
        }
    }

    @ParameterizedTest(name = "{0}, {1}x{2}")
    @MethodSource("viewports")
    void outsideHiddenPageAndHorizontalOnlyWheelEventsDoNotScroll(String language, int width, int height)
            throws Exception {
        try (var fonts = new VanillaFontTestFixture(language)) {
            var input = input(fonts, width, height, language);
            var screen = input.screen;
            var list = input.requirements;
            int centerX = list.getX() + list.getWidth() / 2;
            int centerY = list.getY() + list.getHeight() / 2;
            double x = screen.responsiveScreenX(centerX);
            double y = screen.responsiveScreenY(centerY);
            screen.setFocused(list);
            assertTrue(screen.mouseScrolled(x, y, 0, -1));
            assertPosition(input, 24);

            // An empty, initialized AE WidgetContainer must safely decline these misses.
            for (int[] outside : new int[][] {
                    {list.getX() - 12, centerY}, {list.getRight() + 12, centerY},
                    {centerX, list.getY() - 12}, {centerX, list.getBottom() + 12}}) {
                assertFalse(screen.mouseScrolled(screen.responsiveScreenX(outside[0]),
                        screen.responsiveScreenY(outside[1]), 0, -1));
                assertPosition(input, 24);
            }
            assertFalse(screen.mouseScrolled(x, y, 1, 0));
            assertFalse(screen.mouseScrolled(x, y, 0, 0));
            assertPosition(input, 24);

            list.visible = false;
            assertFalse(screen.mouseScrolled(x, y, 0, -1));
            assertPosition(input, 24);
            // Test the screen's page gate independently of the child's visibility (e.g. retained controls).
            list.visible = true;
            ResponsiveScreenTestFixture.set(screen, "upgradePage", false);
            assertFalse(screen.mouseScrolled(x, y, 0, -1));
            ResponsiveScreenTestFixture.set(screen, "recipesPage", true);
            assertFalse(screen.mouseScrolled(x, y, 0, 1));
            assertPosition(input, 24);
            assertSame(list, screen.getFocused());
        }
    }

    @Test
    void wheelMissesStillReachAeWidgetsButNativeRequirementsGetFirstChance() throws Exception {
        try (var fonts = new VanillaFontTestFixture("en_us")) {
            var input = input(fonts, 320, 240, "en_us");
            var screen = input.screen;
            var list = input.requirements;
            var style = new ScreenStyle();
            ResponsiveScreenTestFixture.set(style, "widgets", Map.of("fallback", new WidgetStyle()));
            var widgets = new WidgetContainer(style);
            // Retain AE's real wheel/capture behavior, but avoid sprite-atlas sizing in this headless test.
            var fallback = new Scrollbar() {
                @Override public Rect2i getBounds() { return new Rect2i(0, 0, 12, 116); }
            };
            fallback.setRange(0, 100, 1);
            widgets.add("fallback", fallback);
            ResponsiveScreenTestFixture.set(screen, "widgets", widgets);
            double x = screen.responsiveScreenX(list.getX() + 2);
            double y = screen.responsiveScreenY(list.getY() + list.getHeight() / 2);

            assertTrue(screen.mouseScrolled(x, y, 0, -1));
            assertPosition(input, 24);
            assertEquals(0, fallback.getCurrentScroll(), "Handled native input must not also scroll AE widgets");
            assertTrue(screen.mouseScrolled(screen.responsiveScreenX(list.getX() - 12), y, 0, -1));
            assertPosition(input, 24);
            assertEquals(1, fallback.getCurrentScroll());
            ResponsiveScreenTestFixture.set(screen, "upgradePage", false);
            assertTrue(screen.mouseScrolled(x, y, 0, -1));
            assertPosition(input, 24);
            assertEquals(2, fallback.getCurrentScroll());
            assertFalse(screen.mouseScrolled(x, y, 1, 0));
            assertEquals(2, fallback.getCurrentScroll());
            assertNull(screen.getFocused());
        }
    }

    @ParameterizedTest(name = "{0}, {1}x{2}")
    @MethodSource("viewports")
    void nativeScrollbarDragsThroughTheScreenOutsideItsBoundsAndReleasesCapture(String language, int width, int height)
            throws Exception {
        try (var fonts = new VanillaFontTestFixture(language)) {
            var input = input(fonts, width, height, language);
            var screen = input.screen;
            var list = input.requirements;
            int grabY = list.getY() + 3;
            // onClick skips only the native click sound; reproduce the focus/drag state set by a left click.
            list.onClick(list.getRight() - 3, grabY);
            screen.setFocused(list);
            screen.setDragging(true);
            assertEquals(true, field(list, "draggingScrollbar"));
            assertPosition(input, 0);
            double startX = screen.responsiveScreenX(list.getRight() - 3);
            double startY = screen.responsiveScreenY(grabY);
            int thumbHeight = (int) invoke(list, "thumbHeight");
            double quarterY = screen.responsiveScreenY(grabY + (list.getHeight() - thumbHeight) / 4);
            assertTrue(screen.mouseDragged(startX, quarterY, 0, 0, quarterY - startY));
            assertEquals(input.maxScroll() / 4.0, input.viewport.scroll, input.maxScroll() * .02,
                    "Raw drag coordinates must be converted to the fitted list's logical coordinates");

            double outsideX = screen.responsiveScreenX(list.getX() - 30);
            double bottomY = screen.responsiveScreenY(list.getBottom() + 30);
            double topY = screen.responsiveScreenY(list.getY() - 30);
            assertTrue(screen.mouseDragged(outsideX, bottomY, 0, outsideX - startX, bottomY - quarterY));
            assertPosition(input, input.maxScroll());
            assertTrue(screen.mouseDragged(outsideX, topY, 0, 0, topY - bottomY));
            assertPosition(input, 0);
            assertTrue(screen.mouseReleased(outsideX, topY, 0));
            assertFalse(screen.isDragging());
            assertEquals(false, field(list, "draggingScrollbar"));
            assertSame(list, screen.getFocused());

            // A stray native callback must not move the released thumb. Avoid AE's unrelated inventory
            // fallback here: its private findSlot access requires the live client's access transformer.
            list.mouseDragged(list.getX() - 30, list.getBottom() + 30, 0, 0, 60);
            assertPosition(input, 0);
            assertFalse(screen.isDragging());
            assertEquals(false, field(list, "draggingScrollbar"));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"en_us", "zh_cn"})
    void keyboardNavigationRemainsAnOptionalFocusedAlternative(String language) throws Exception {
        try (var fonts = new VanillaFontTestFixture(language)) {
            var input = input(fonts, 320, 240, language);
            var screen = input.screen;
            var list = input.requirements;
            assertFalse(list.keyPressed(GLFW.GLFW_KEY_DOWN, 0, 0));
            assertPosition(input, 0);
            screen.setFocused(list);
            for (int[] step : new int[][] {
                    {GLFW.GLFW_KEY_DOWN, RequirementViewport.LINE_HEIGHT}, {GLFW.GLFW_KEY_UP, 0},
                    {GLFW.GLFW_KEY_PAGE_DOWN, list.getHeight() - RequirementViewport.LINE_HEIGHT},
                    {GLFW.GLFW_KEY_PAGE_UP, 0}, {GLFW.GLFW_KEY_END, input.maxScroll()},
                    {GLFW.GLFW_KEY_HOME, 0}, {GLFW.GLFW_KEY_UP, 0}}) {
                assertTrue(screen.keyPressed(step[0], 0, 0));
                assertPosition(input, step[1]);
            }
            list.visible = false;
            assertFalse(list.keyPressed(GLFW.GLFW_KEY_DOWN, 0, 0));
            list.visible = true;
            screen.setFocused(input.otherControl);
            assertFalse(list.keyPressed(GLFW.GLFW_KEY_END, 0, 0));
            assertPosition(input, 0);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"en_us", "zh_cn"})
    void clickingDoesNotLeaveAFullListOutlineButKeyboardFocusDoes(String language) throws Exception {
        try (var fonts = new VanillaFontTestFixture(language)) {
            var input = input(fonts, 320, 240, language);
            var list = input.requirements;
            var client = Minecraft.getInstance();
            var previousInputType = client.getLastInputType();
            try {
                client.setLastInputType(InputType.MOUSE);
                list.onClick(list.getX() + 12, list.getY() + 12);
                input.screen.setFocused(list);
                assertTrue(list.isFocused());
                assertEquals(false, field(list, "draggingScrollbar"));
                assertOutline(input, false);

                client.setLastInputType(InputType.KEYBOARD_TAB);
                assertOutline(input, true);
                input.screen.setFocused(input.otherControl);
                assertOutline(input, false);
                input.screen.setFocused(list);
                client.setLastInputType(InputType.MOUSE);
                assertOutline(input, false);
                assertTrue(list.isFocused(), "Suppress the pointer outline, not native focus itself");
            } finally {
                client.setLastInputType(previousInputType);
            }
        }
    }

    private record Input(StarComputationEngineScreen screen, AbstractWidget requirements,
                         RequirementViewport viewport, Button otherControl) {
        int maxScroll() { return viewport.maxScroll(requirements.getHeight()); }
    }

    /** Initialize production controls without a game window, world, transport, or another test's private fixture. */
    private static Input input(VanillaFontTestFixture fonts, int width, int height, String language) throws Exception {
        var screen = ResponsiveScreenTestFixture.allocate(StarComputationEngineScreen.class);
        var menu = ResponsiveScreenTestFixture.allocate(StarComputationEngineMenu.class);
        var ui = new OsqUi("star_computation_engine");
        for (String name : List.of("children", "renderables", "narratables", "screenWidgets", "nodeButtons", "levelButtons"))
            ResponsiveScreenTestFixture.set(screen, name, new ArrayList<>());
        ResponsiveScreenTestFixture.set(screen, "screenContent", new ResponsiveScreenWidgets());
        ResponsiveScreenTestFixture.set(screen, "widgets", new WidgetContainer(new ScreenStyle()));
        ResponsiveScreenTestFixture.set(screen, "menu", menu);
        ResponsiveScreenTestFixture.set(screen, "ui", ui);
        ResponsiveScreenTestFixture.set(screen, "font", fonts.font);
        ResponsiveScreenTestFixture.set(screen, "minecraft", Minecraft.getInstance());
        assertEquals(400, ui.layout.width);
        assertEquals(344, ui.layout.height);
        screen.width = width;
        screen.height = height;
        ResponsiveScreenTestFixture.set(screen, "imageWidth", 400);
        ResponsiveScreenTestFixture.set(screen, "imageHeight", 344);
        ResponsiveScreenTestFixture.set(screen, "leftPos", (width - 400) / 2);
        ResponsiveScreenTestFixture.set(screen, "topPos", (height - 344) / 2);
        ResponsiveScreenTestFixture.set(screen, "pendingVersion", -1);
        ResponsiveScreenTestFixture.set(screen, "upgradePage", true);
        ResponsiveScreenTestFixture.set(screen, "selectedUpgradeLevel", 4);
        menu.canConfigure = menu.formed = menu.networkOnline = menu.starOrrery = true;
        menu.litRings = 9;
        menu.certifiedLevel = 3;
        menu.upgradeStatus = "idle";
        menu.actionResult = StarComputationEngineMenu.ActionResult.NONE;
        menu.catalog = StarComputationRecipeCatalog.EMPTY;
        menu.researchNodes = StarResearchMenuNodes.FALLBACK;
        var rows = new ArrayList<Row>();
        boolean chinese = language.equals("zh_cn");
        for (int i = 0; i < 24; i++) rows.add(new Row(i % 8, i % 3 == 0,
                Component.literal(i % 7 == 0
                        ? (chinese ? "完成升级所需的第 " + i + " 项构筑井研究，并在同一座构筑井中完成后续的量子构序准备工作"
                                : "Finish Research Well study " + i + " and complete the subsequent quantum sequencing preparations in that same well")
                        : (chinese ? "完成第 " + i + " 项构筑井研究" : "Finish Research Well study " + i)),
                List.of(Component.literal(chinese ? "第 " + i + " 项：将构筑井接入当前 ME 网络，然后在构筑井中完成这项研究。"
                        : "Study " + i + ": connect the Research Well to this ME network and finish this study in the well."),
                        Component.literal(chinese ? "研究必须在同一座构筑井内完成，不能将多座构筑井的进度相加。"
                                : "Finish the research in one well; progress from different wells is not combined."))));
        var entries = new ArrayList<>(StarComputationUpgradeCatalog.EMPTY.entries());
        entries.set(3, new StarComputationUpgradeCatalog.Entry(4, true, 100000, 256, false, rows));
        menu.upgrades = new StarComputationUpgradeCatalog(entries);
        ui.hide(entry -> "ctl".equals(entry.kind) || "edge".equals(entry.kind));
        invoke(screen, "initControls");
        invoke(screen, "updateControls");
        var list = (AbstractWidget) field(screen, "upgradeRequirements");
        var viewport = (RequirementViewport) field(list, "viewport");
        assertTrue(screen.children().contains(list));
        assertTrue(screen.screenContent.owns(list), "The list must be a native owned child, not an external overlay");
        assertTrue(viewport.rows.stream().anyMatch(row -> row.lines().size() > 1), "Custom long titles must genuinely wrap");
        assertTrue(viewport.rows.stream().anyMatch(row -> row.row().depth() > 0 && row.lines().size() == 1),
                "Keep ordinary nested titles short enough to scan on one line");
        assertTrue(viewport.maxScroll(list.getHeight()) > 48);
        var otherControl = (Button) ((List<?>) field(screen, "levelButtons")).getFirst();
        return new Input(screen, list, viewport, otherControl);
    }

    @SuppressWarnings("unchecked")
    private static Row assertHoveredDetails(Input input, double rawX, double rawY) throws Exception {
        var screen = input.screen;
        var list = input.requirements;
        double logicalX = screen.logicalMouseX(rawX), logicalY = screen.logicalMouseY(rawY);
        int index = input.viewport.rowAt(logicalY - list.getY() + input.viewport.scroll);
        assertTrue(index >= 0);
        var row = input.viewport.rows.get(index).row();
        var expected = StarComputationEngineScreen.requirementTooltip(row);
        var direct = list.getClass().getDeclaredMethod("tooltipAt", double.class, double.class);
        direct.setAccessible(true);
        assertEquals(expected, direct.invoke(list, logicalX, logicalY));
        var routed = StarComputationEngineScreen.class.getDeclaredMethod("upgradeTooltip", double.class, double.class);
        routed.setAccessible(true);
        var actual = (List<Component>) routed.invoke(screen,
                logicalX - (int) field(screen, "leftPos"), logicalY - (int) field(screen, "topPos"));
        assertEquals(expected, actual, "Screen hover routing must reach the actual row at the responsive coordinates");
        assertEquals(row.text().getString(), actual.getFirst().getString());
        assertEquals(row.details(), actual.subList(2, actual.size()));
        assertFalse(row.details().isEmpty(), "Use real instruction-bearing rows for hover coverage");
        assertEquals(row.details().size() + 2, actual.size(), "Only title, status and row-specific help belong here");
        return row;
    }

    private static void assertPosition(Input input, double expected) throws Exception {
        assertEquals(expected, input.viewport.scroll, .0001);
        assertEquals(expected, (double) field(input.screen, "upgradeScroll"), .0001,
                "Persisted screen scroll must follow the visible viewport");
    }

    private static void assertOutline(Input input, boolean expected) throws Exception {
        var list = input.requirements;
        var recorder = new UiRenderRecorder();
        var render = list.getClass().getDeclaredMethod("renderWidget", GuiGraphics.class, int.class, int.class, float.class);
        render.setAccessible(true);
        render.invoke(list, recorder, list.getX() + 12, list.getY() + 12, 0f);
        assertFalse(recorder.text.isEmpty(), "Execute the real row renderer, not a synthetic focus-only widget");
        assertEquals(1, recorder.clips);
        assertEquals(0, recorder.clipDepth);
        int w = list.getWidth(), h = list.getHeight();
        for (int[] edge : new int[][] {{0, 0, w, 1}, {0, h - 1, w, h}, {0, 1, 1, h - 1}, {w - 1, 1, w, h - 1}}) {
            assertEquals(expected, recorder.rects.stream().anyMatch(rect -> rect.left() == edge[0]
                    && rect.top() == edge[1] && rect.right() == edge[2] && rect.bottom() == edge[3]),
                    "Only keyboard focus should draw full-widget borders (row markers are separate)");
        }
    }

    private static Object invoke(Object target, String name) throws Exception {
        var method = target.getClass().getDeclaredMethod(name);
        method.setAccessible(true);
        return method.invoke(target);
    }

    private static Object field(Object target, String name) throws Exception {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                var field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(name);
    }
}
