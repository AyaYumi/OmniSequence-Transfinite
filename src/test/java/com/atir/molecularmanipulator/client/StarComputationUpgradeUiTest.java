package com.atir.molecularmanipulator.client;

import static org.junit.jupiter.api.Assertions.*;

import com.atir.molecularmanipulator.client.StarComputationEngineScreen.RequirementViewport;
import com.atir.molecularmanipulator.client.StarComputationEngineScreen.UpgradeUiState;
import com.atir.molecularmanipulator.client.gui.theme.OsqFonts;
import com.atir.molecularmanipulator.starcomputation.StarComputationUpgradeCatalog.Row;
import com.atir.molecularmanipulator.starcomputation.StarComputationUpgradeCatalog;
import com.atir.molecularmanipulator.menu.StarComputationEngineMenu;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

/** Focused upgrade UI regressions; deliberately independent of the existing large screen suites. */
class StarComputationUpgradeUiTest {
    private static final String PREFIX = "gui.molecularmanipulator.star_computation.upgrade.";

    private static UpgradeUiState state(int level, int certified, int target, boolean running,
            boolean formed, boolean online, int rings, boolean permitted, boolean waiting,
            boolean configured, boolean met, String status) {
        return new UpgradeUiState(level, certified, target, running, formed, online, rings,
                permitted, waiting, configured, met, status);
    }

    @Test
    void onlyTheNextServerReadyLevelCanStartOrResume() {
        for (int certified = 3; certified < 9; certified++) {
            int next = certified + 1;
            assertTrue(state(next, certified, 0, false, true, true, 9, true, false, true, true, "idle").canStart());
            assertTrue(state(next, certified, next, false, true, true, 9, true, false, true, true, "paused").canStart());
            for (int level = 1; level <= 9; level++) {
                var preview = state(level, certified, 0, false, true, true, 9, true, false, true, true, "idle");
                assertEquals(level == next, preview.canStart(), "Selected L" + level + ", certified L" + certified);
                assertEquals(level <= 3, preview.automatic());
                assertEquals(level > next, preview.preview());
            }
        }
    }

    @Test
    void everySynchronizedGateAndTheWaitingLockPreventsStarting() {
        var blocked = List.of(
                state(4, 3, 0, false, false, true, 9, true, false, true, true, "structure"),
                state(4, 3, 0, false, true, false, 9, true, false, true, true, "offline"),
                state(4, 3, 0, false, true, true, 3, true, false, true, true, "structure"),
                state(4, 3, 0, false, true, true, 9, false, false, true, true, "idle"),
                state(4, 3, 0, false, true, true, 9, true, true, true, true, "idle"),
                state(4, 3, 0, false, true, true, 9, true, false, false, true, "rule_missing"),
                state(4, 3, 0, false, true, true, 9, true, false, true, false, "conditions"),
                state(4, 3, 4, true, true, true, 9, true, false, true, true, "running"),
                state(4, 3, 5, false, true, true, 9, true, false, true, true, "paused"),
                state(4, 3, 0, false, true, true, 9, true, false, true, true, "migration"),
                state(4, 3, 0, false, true, true, 9, true, false, true, true, "restoring"));
        for (var value : blocked) assertFalse(value.canStart(), value.toString());
    }

    @Test
    void pauseRemainsAvailableOfflineAndWhilePreviewingAnotherLevel() {
        for (String reason : List.of("offline", "structure", "conditions", "rule_missing", "energy", "running")) {
            var task = state(9, 3, 4, true, false, false, 0, true, false, false, false, reason);
            assertTrue(task.canPause(), reason);
            assertFalse(task.canStart(), reason);
        }
        assertFalse(state(4, 3, 4, true, false, false, 0, false, false, false, false, "offline").canPause());
        assertFalse(state(4, 3, 4, true, false, false, 0, true, true, false, false, "offline").canPause());
        assertFalse(state(4, 3, 4, false, true, true, 9, true, false, true, true, "paused").canPause());
        assertFalse(state(4, 3, 0, true, true, true, 9, true, false, true, true, "idle").canPause());
    }

    @Test
    void automaticLevelsNeverRequireAnActionAndCertifiedDamageNeverOffersRepayment() {
        for (int level = 1; level <= 3; level++) {
            var automatic = state(level, 3, 0, false, false, false, 0, true, false, false, false, "offline");
            assertTrue(automatic.automatic());
            assertFalse(automatic.canStart());
        }
        for (int level = 4; level <= 9; level++) {
            var damaged = state(level, 9, 0, false, false, false, 2, true, false, false, false, "structure");
            assertTrue(damaged.certified());
            assertFalse(damaged.canStart());
            assertFalse(damaged.canPause());
            assertFalse(damaged.preview());
        }
        assertEquals(9, state(9, 9, 0, false, true, true, 9, true, false, true, true, "complete").selectedLevel());
    }

    @Test
    void anchorUpgradesBuildMissingRingsAndKeepSequentialLowAndHighProgression() {
        for (int level = 1; level <= 3; level++) {
            var ready = new UpgradeUiState(level, 3, 0, false, true, true, level - 1,
                    true, false, true, true, "idle", true);
            assertFalse(ready.automatic(), "Automatic ring placement still requires a button press");
            assertFalse(ready.certified());
            assertTrue(ready.canStart(), "Low-level building follows physical rings, not the L3 certification ceiling");
            assertFalse(new UpgradeUiState(level, 3, 0, false, true, true, level,
                    true, false, true, true, "idle", true).canStart(), "An already built low ring is not charged again");
            if (level > 1) assertFalse(new UpgradeUiState(level, 3, 0, false, true, true, level - 2,
                    true, false, true, true, "idle", true).canStart(), "Low rings cannot be skipped");
        }
        for (int level = 4; level <= 9; level++) {
            assertTrue(new UpgradeUiState(level, level - 1, 0, false, true, true, 0,
                    true, false, true, true, "idle", true).canStart(), "Building may supply missing inner and target rings");
            assertFalse(new UpgradeUiState(level, level - 2, 0, false, true, true, 9,
                    true, false, true, true, "idle", true).canStart(), "High-level certification cannot be skipped");
        }
        for (String reason : List.of("anchor_materials", "anchor_space", "anchor_chunks", "anchor_permission", "anchor_failed")) {
            assertFalse(new UpgradeUiState(4, 3, 0, false, true, true, 3,
                    true, false, true, false, reason, true).canStart(), reason);
        }
        assertFalse(new UpgradeUiState(4, 3, 0, false, true, false, 3,
                true, false, true, true, "offline", true).canStart(), "Positive-cost building still requires a ready network");
    }

    @Test
    void synchronizedZeroEnergyBuildingDoesNotNeedAnOnlineGridOrAnExistingTargetRing() throws Exception {
        var screen = ResponsiveScreenTestFixture.allocate(StarComputationEngineScreen.class);
        var menu = ResponsiveScreenTestFixture.allocate(StarComputationEngineMenu.class);
        ResponsiveScreenTestFixture.set(screen, "menu", menu);
        ResponsiveScreenTestFixture.set(screen, "pendingVersion", -1);
        ResponsiveScreenTestFixture.set(screen, "selectedUpgradeLevel", 4);
        menu.canConfigure = menu.formed = menu.starOrrery = true;
        menu.networkOnline = false;
        menu.litRings = 3;
        menu.certifiedLevel = 3;
        menu.upgradeStatus = "idle";
        var entries = new ArrayList<>(StarComputationUpgradeCatalog.EMPTY.entries());
        entries.set(3, new StarComputationUpgradeCatalog.Entry(4, true, 0, 1, true, List.of(), true));
        menu.upgrades = new StarComputationUpgradeCatalog(entries);
        var method = StarComputationEngineScreen.class.getDeclaredMethod("upgradeState");
        method.setAccessible(true);
        assertTrue(((UpgradeUiState) method.invoke(screen)).canStart());
        entries.set(3, new StarComputationUpgradeCatalog.Entry(4, true, 1000, 100, true, List.of(), true));
        menu.upgrades = new StarComputationUpgradeCatalog(entries);
        assertFalse(((UpgradeUiState) method.invoke(screen)).canStart(), "A positive calibration cost still requires ME");
        entries.set(3, new StarComputationUpgradeCatalog.Entry(4, true, 1000, 100, true, List.of()));
        menu.upgrades = new StarComputationUpgradeCatalog(entries);
        menu.networkOnline = true;
        assertFalse(((UpgradeUiState) method.invoke(screen)).canStart(), "Legacy rules still require hand-built target rings");
        menu.litRings = 4;
        assertTrue(((UpgradeUiState) method.invoke(screen)).canStart());
    }

    @Test
    void anchorBlockersAndConstructionActionsHaveResolvedLocalizedCopy() throws Exception {
        var method = StarComputationEngineScreen.class.getDeclaredMethod("knownUpgradeStatus", String.class);
        method.setAccessible(true);
        for (String language : List.of("en_us", "zh_cn")) try (var runtime = new VanillaFontTestFixture(language)) {
            for (String status : List.of("anchor_materials", "anchor_space", "anchor_chunks", "anchor_permission", "anchor_failed")) {
                assertEquals(status, method.invoke(null, status));
                String text = Component.translatable(PREFIX + "status." + status).getString();
                assertFalse(text.contains(PREFIX), language + ": " + status);
            }
            for (String key : List.of("build", "upgrade", "construction")) {
                String text = Component.translatable(PREFIX + key, 4).getString();
                assertFalse(text.contains(PREFIX));
                assertFalse(text.contains("%s"));
                assertTrue(text.contains("4"));
            }
            for (String key : List.of("anchor_materials_hint", "auto_build_help", "no_energy", "anchor_space_title"))
                assertFalse(Component.translatable(PREFIX + key).getString().contains(PREFIX));
            String supply = Component.translatable(PREFIX + "anchor_supply", 10, 20, 30).getString();
            assertTrue(supply.contains("10") && supply.contains("20") && supply.contains("30"));
            assertFalse(supply.contains("%"));
        }
    }

    @Test
    void variableHeightViewportRetainsAllRowsAndCanReachTheLastNestedTarget() {
        var source = new ArrayList<Row>();
        for (int i = 0; i < 2048; i++) source.add(new Row(i % 34, i % 3 == 0,
                Component.literal("Complete the research needed for upgrade " + i),
                List.of(Component.literal("Finish research target " + i + " at a connected Research Well."))));
        var viewport = new RequirementViewport();
        viewport.rebuild(source, 170, (text, width) -> chunks(text.getString(), Math.max(1, width / 5)));
        assertEquals(source.size(), viewport.rows.size(), "No fixed condition count or silent truncation");
        int top = 0;
        for (int i = 0; i < source.size(); i++) {
            var row = viewport.rows.get(i);
            assertSame(source.get(i), row.row());
            assertEquals(source.get(i).text().getString(), String.join("", row.lines()));
            assertEquals(top, row.top());
            assertEquals(i, viewport.rowAt(row.top()));
            assertEquals(i, viewport.rowAt(row.top() + row.height() - 0.1));
            assertEquals(Math.min(source.get(i).depth(), 6) * 4, row.indent());
            top += row.height();
        }
        assertEquals(top, viewport.contentHeight);
        assertEquals(33, viewport.rows.get(33).row().depth(), "Deep server depth is retained, not rewritten");
        assertEquals(24, viewport.rows.get(33).indent(), "Text width is preserved even at depth 33");
        viewport.scrollTo(Double.MAX_VALUE, 116);
        assertEquals(viewport.maxScroll(116), viewport.scroll);
        assertEquals(source.size() - 1, viewport.rowAt(viewport.scroll + 115));
        viewport.scrollTo(-100, 116);
        assertEquals(0, viewport.scroll);
        assertEquals(-1, viewport.rowAt(-1));
        assertEquals(-1, viewport.rowAt(viewport.contentHeight));
    }

    @Test
    void viewportReflowClampsOldScrollWithoutDroppingSmallOrEmptyLists() {
        var viewport = new RequirementViewport();
        var rows = List.of(new Row(0, false, Component.literal("A long title")), new Row(1, true, Component.literal("Target")));
        viewport.rebuild(rows, 170, (text, width) -> chunks(text.getString(), 1));
        viewport.scrollTo(10000, 116);
        assertTrue(viewport.scroll > 0);
        viewport.rebuild(rows, 170, (text, width) -> List.of(text.getString()));
        viewport.scrollTo(viewport.scroll, 116);
        assertEquals(0, viewport.scroll);
        assertEquals(2, viewport.rows.size());
        viewport.rebuild(List.of(), 170, (text, width) -> List.of(text.getString()));
        viewport.scrollTo(Double.NaN, 116);
        assertEquals(0, viewport.scroll);
        assertEquals(0, viewport.contentHeight);
        assertEquals(-1, viewport.rowAt(0));
    }

    @Test
    void realDefaultFontKeepsOnlyReadableTitlesInTheListAndDetailsInTooltips() throws Exception {
        for (String language : List.of("en_us", "zh_cn")) try (var runtime = new VanillaFontTestFixture(language)) {
            for (var tab : List.of(new Object[] {"gui.molecularmanipulator.star_computation.overview", 52},
                    new Object[] {PREFIX + "tab", 52}, new Object[] {"gui.molecularmanipulator.star_computation.recipes", 58})) {
                String text = Component.translatable((String) tab[0]).getString();
                assertTrue(OsqFonts.widthF(runtime.font, text, VanillaFontTestFixture.DEFAULT_FONT, 7.5f)
                        <= (int) tab[1] - 10, language + " tab: " + text);
            }
            for (int level = 1; level <= 9; level++) assertTrue(OsqFonts.widthF(runtime.font, "L" + level,
                    VanillaFontTestFixture.DEFAULT_FONT, 7.5f) <= 12);
            boolean chinese = language.equals("zh_cn");
            var title = Component.literal(chinese ? "完成构序阵列研究" : "Finish Sequence Array research");
            var instructions = Component.literal(chinese
                    ? "将构筑井接入此 ME 网络，再在同一座构筑井内完成这项研究。"
                    : "Connect a Research Well to this ME network, then finish this research in that same well.");
            // Long instructions must not inflate or wrap the list title.
            var progress = Component.literal(chinese ? "当前进度：0 / 1。完成研究后，这项条件会自动勾选。"
                    : "Progress: 0 / 1. This requirement will be checked automatically once the research is complete.");
            var source = new Row(33, false, title, List.of(instructions, progress));
            var viewport = new RequirementViewport();
            viewport.rebuild(List.of(source), 170,
                    (value, width) -> OsqFonts.wrap(runtime.font, value.getString(), VanillaFontTestFixture.DEFAULT_FONT, 7.5f, width));
            var row = viewport.rows.getFirst();
            assertEquals(title.getString().replaceAll("\\s+", ""), String.join("", row.lines()).replaceAll("\\s+", ""));
            assertTrue(row.lines().size() <= 2, language + ": Short titles should remain easy to scan");
            assertEquals(row.lines().size() * RequirementViewport.LINE_HEIGHT + 6, row.height());
            for (String line : row.lines()) assertTrue(OsqFonts.widthF(runtime.font, line,
                    VanillaFontTestFixture.DEFAULT_FONT, 7.5f) <= 124.5f, language + ": " + line);
            String visible = String.join("", row.lines());
            assertFalse(visible.contains("kubejs:"));
            assertFalse(visible.contains("[material]"));
            assertFalse(visible.contains(instructions.getString()));
            var tooltip = StarComputationEngineScreen.requirementTooltip(source);
            assertEquals(title.getString(), tooltip.getFirst().getString());
            assertEquals(List.of(instructions, progress), tooltip.subList(2, tooltip.size()));
            assertEquals(4, tooltip.size(), "Only title, status and the supplied details belong in the tip");
            assertFalse(tooltip.stream().anyMatch(value -> value.getString().contains(PREFIX)));
            assertFalse(tooltip.stream().anyMatch(value -> value.getString().contains("%s")));
            for (boolean met : List.of(false, true)) {
                var plain = new Row(0, met, title);
                var nested = new Row(33, met, title);
                var plainTip = StarComputationEngineScreen.requirementTooltip(plain);
                assertEquals(2, plainTip.size(), "Legacy rows still get a title and a clear status");
                assertEquals(Component.translatable(PREFIX + (met ? "met" : "incomplete")).getString(),
                        plainTip.get(1).getString());
                assertEquals(plainTip, StarComputationEngineScreen.requirementTooltip(nested),
                        "Internal rule depth and scrolling instructions must not clutter a condition's tip");
            }
            assertNotEquals(StarComputationEngineScreen.requirementTooltip(new Row(0, true, title)).get(1).getString(),
                    StarComputationEngineScreen.requirementTooltip(new Row(0, false, title)).get(1).getString());
        }
    }

    @Test
    void pageNavigationDoesNotSendCalibrationCommandsAndLegacyRecipeHooksRemain() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/atir/molecularmanipulator/client/StarComputationEngineScreen.java"));
        assertTrue(source.contains("private boolean recipesPage;"));
        assertTrue(source.contains("private void setPage(boolean recipes)"));
        assertTrue(source.contains("ui.tab(\"upgrade\""));
        String navigation = source.substring(source.indexOf("private void setPage(boolean recipes)"), source.indexOf("private void selectNode"));
        assertFalse(navigation.contains("request("));
        assertFalse(navigation.contains("requestStartUpgrade"));
        assertFalse(navigation.contains("requestPauseUpgrade"));
        assertFalse(source.contains("void onClose("));
        assertTrue(source.contains("rows.addAll(selectedUpgrade().rows())"));
        assertTrue(source.contains("request(() -> menu.requestStartUpgrade(selectedUpgradeLevel))"));
        assertTrue(source.contains("request(menu::requestPauseUpgrade)"));
        assertFalse(source.contains("FtbQuestsProgress"));
        assertFalse(source.contains("getBlockEntity("));
        assertFalse(source.contains("getRecipeManager("));
    }

    private static List<String> chunks(String text, int length) {
        var result = new ArrayList<String>();
        for (int start = 0; start < text.length(); start += length)
            result.add(text.substring(start, Math.min(text.length(), start + length)));
        return result;
    }
}
