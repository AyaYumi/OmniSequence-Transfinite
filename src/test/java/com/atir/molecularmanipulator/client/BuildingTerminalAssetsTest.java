package com.atir.molecularmanipulator.client;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class BuildingTerminalAssetsTest {
    private static final Path RESOURCES = Path.of("src/main/resources");
    private static final Path ASSETS = RESOURCES.resolve("assets/molecularmanipulator");
    private static final Path JAVA = Path.of("src/main/java/com/atir/molecularmanipulator");

    @Test
    void itemHasAnIconAndAnAe2OnlySurvivalRecipe() throws Exception {
        var model = JsonParser.parseString(Files.readString(ASSETS.resolve("models/item/building_access_terminal.json"))).getAsJsonObject();
        assertEquals("minecraft:item/generated", model.get("parent").getAsString());
        assertEquals("molecularmanipulator:item/building_access_terminal", model.getAsJsonObject("textures").get("layer0").getAsString());
        var image = ImageIO.read(ASSETS.resolve("textures/item/building_access_terminal.png").toFile());
        assertEquals(16, image.getWidth());
        assertEquals(16, image.getHeight());
        assertTrue(image.getColorModel().hasAlpha());
        var recipe = JsonParser.parseString(Files.readString(RESOURCES.resolve("data/molecularmanipulator/recipe/building_access_terminal.json"))).getAsJsonObject();
        assertEquals("minecraft:crafting_shaped", recipe.get("type").getAsString());
        assertEquals("molecularmanipulator:building_access_terminal", recipe.getAsJsonObject("result").get("id").getAsString());
        for (var ingredient : recipe.getAsJsonObject("key").entrySet()) {
            String id = ingredient.getValue().getAsJsonObject().get("item").getAsString();
            assertTrue(id.startsWith("minecraft:") || id.startsWith("ae2:"), "Terminal recipe must not require an optional addon: " + id);
        }
    }

    @Test
    void bothLanguagesCoverEveryAccessStateAndNavigationAction() throws Exception {
        for (String language : List.of("zh_cn", "en_us")) {
            var lang = JsonParser.parseString(Files.readString(ASSETS.resolve("lang/" + language + ".json"))).getAsJsonObject();
            assertTrue(lang.has("item.molecularmanipulator.building_access_terminal"));
            for (String suffix : List.of("title", "subtitle", "return", "action.open", "action.remove", "scroll_hint", "scroll_usage", "count",
                    "directory", "list", "summary", "availability", "remove_note", "help.title", "help.bind",
                    "help.bind_click", "help.bind_item", "help.access", "help.dimension", "help.chunk", "help.no_force_load",
                    "empty.title", "empty.hint.bind", "empty.hint.open", "status.ready", "status.unloaded",
                    "status.wrong_dimension", "status.missing", "status.denied", "status.incomplete")) {
                assertTrue(lang.has("gui.molecularmanipulator.building_terminal." + suffix), language + " " + suffix);
            }
            for (String suffix : List.of("bound", "already_bound", "full", "unavailable", "unsupported")) {
                assertTrue(lang.has("message.molecularmanipulator.building_terminal." + suffix), language + " " + suffix);
            }
        }
    }

    @Test
    void everyBuildingPanelSharesTerminalNavigationWithoutSyncIdCollisions() throws Exception {
        var sync = Pattern.compile("@GuiSync\\((\\d+)\\)");
        String base = Files.readString(JAVA.resolve("menu/BuildingMenu.java"));
        var inherited = sync.matcher(base);
        assertTrue(inherited.find());
        int inheritedId = Integer.parseInt(inherited.group(1));
        assertTrue(inheritedId <= Short.MAX_VALUE);
        for (String name : List.of("MolecularManipulatorMenu", "MolecularCenterMenu", "MolecularAutoCrafterMenu",
                "MolecularAutoCrafterConfigMenu", "MatterFabricationMenu", "MatterFabricationPortMenu",
                "MatterFabricationPatternAssemblyMenu", "StarComputationEngineMenu", "OmniComputationMenu", "SingularityMenu")) {
            String source = Files.readString(JAVA.resolve("menu/" + name + ".java"));
            assertTrue(source.contains("extends BuildingMenu"), name + " must retain terminal context");
            var own = sync.matcher(source);
            while (own.find()) assertNotEquals(inheritedId, Integer.parseInt(own.group(1)), name);
        }
        for (String name : List.of("MolecularAutoCrafterMenu", "MolecularAutoCrafterConfigMenu")) {
            assertTrue(Files.readString(JAVA.resolve("menu/" + name + ".java")).contains("buildingLocator()"));
        }
    }

    @Test
    void directoryReusesTheMachineSkinAndResourceDrivenPalette() throws Exception {
        String screen = Files.readString(JAVA.resolve("client/BuildingTerminalScreen.java"));
        assertTrue(screen.contains("new OsqUi(\"building_access_terminal\")"));
        assertTrue(screen.contains("ui.layout.renderSingleLine"));
        assertTrue(screen.contains("BuildingTerminal.MAX_BINDINGS"));
        assertTrue(screen.contains("entry.status() == Status.READY"), "Summary uses synchronized directory status");
        assertTrue(screen.contains("ui.button(\"remove.\" + i"), "Removal shares the real machine button skin");
        assertFalse(screen.contains("class DirectoryButton"));
        assertFalse(screen.contains("drawString"), "Do not fall back to unrelated vanilla text styling");
        assertFalse(Pattern.compile("0x[0-9A-Fa-f]{6,8}").matcher(screen).find(), "Skin colors belong to its resource layout");
        var terminal = JsonParser.parseString(Files.readString(ASSETS.resolve("osq_light/layout/building_access_terminal.json"))).getAsJsonObject();
        var entropy = JsonParser.parseString(Files.readString(ASSETS.resolve("osq_light/layout/star_computation_engine.json"))).getAsJsonObject();
        var colors = new HashMap<String, String>();
        for (var element : entropy.getAsJsonArray("draw")) {
            var entry = element.getAsJsonObject();
            if (entry.has("id") && entry.get("id").getAsString().startsWith("color.")) {
                colors.put(entry.get("id").getAsString(), entry.get("color").getAsString());
            }
        }
        for (var element : terminal.getAsJsonArray("draw")) {
            var entry = element.getAsJsonObject();
            if (!entry.has("id")) continue;
            String id = entry.get("id").getAsString();
            if (colors.containsKey(id)) assertEquals(colors.get(id), entry.get("color").getAsString(), id);
        }
    }

    @Test
    void statusIndicatorStaysNextToTheMeasuredLabelInBothLanguages() throws Exception {
        String source = Files.readString(JAVA.resolve("client/BuildingTerminalScreen.java"));
        assertTrue(source.contains("float statusWidth = OsqFonts.widthF(font, status.getString(),"),
                "Finite status labels use their full measured width, never an arbitrary truncation cap");
        assertTrue(source.contains("float statusX = remove.x - 6 - statusWidth"), "Keep the full status group left of Remove");
        assertTrue(source.contains("fill(g, statusX - 6, actionCenter - 1.5f, 2, 2, color(tint))"),
                "The two-pixel indicator must end four pixels before the label");
        assertTrue(source.contains("centered(g, status, statusX, actionCenter, statusWidth, tint, \"left\""));
        assertFalse(source.contains("fill(g, x + 172, y + 8"), "Do not restore the detached indicator column");
    }

    @Test
    void fiveRowOpenAndRemoveTargetsAreSeparateAndFitTheResponsivePanel() throws Exception {
        var layout = JsonParser.parseString(Files.readString(ASSETS.resolve("osq_light/layout/building_access_terminal.json"))).getAsJsonObject();
        assertEquals("[400,356]", layout.getAsJsonArray("size").toString());
        var rects = new HashMap<String, int[]>();
        for (var element : layout.getAsJsonArray("draw")) {
            var entry = element.getAsJsonObject();
            if (!entry.has("k") || !"ctl".equals(entry.get("k").getAsString())) continue;
            int[] rect = entry.getAsJsonArray("rect").asList().stream().mapToInt(value -> value.getAsInt()).toArray();
            assertTrue(rect[0] >= 0 && rect[1] >= 0 && rect[2] > 0 && rect[3] > 0);
            assertTrue(rect[0] + rect[2] <= 400 && rect[1] + rect[3] <= 356);
            assertNull(rects.put(entry.get("id").getAsString(), rect));
        }
        assertEquals(11, rects.size(), "Five open buttons, five remove buttons and a scrollbar");
        assertFalse(rects.containsKey("previous"));
        assertFalse(rects.containsKey("next"));
        int[] scrollbar = rects.get("scrollbar");
        assertNotNull(scrollbar);
        for (int i = 0; i < 5; i++) {
            int[] open = rects.get("row." + i), remove = rects.get("remove." + i);
            assertNotNull(open); assertNotNull(remove);
            assertEquals(47, open[3]);
            assertEquals(22, remove[3], "Use the same comfortable native buttons as the machines");
            assertTrue(remove[2] >= 40, "Both Remove and 移除 need their full labels");
            assertTrue(open[0] + open[2] <= remove[0], "Opening must not intercept removing row " + i);
            assertTrue(remove[1] >= open[1] && remove[1] + remove[3] <= open[1] + open[3]);
            assertTrue(remove[0] + remove[2] < scrollbar[0], "Scrollbar must not overlap a row action");
            if (i < 4) assertTrue(open[1] + open[3] < rects.get("row." + (i + 1))[1]);
        }
    }

    @Test
    void directoryScrollsByRowsAndKeepsActionsRelativeToTheScrollOffset() throws Exception {
        String source = Files.readString(JAVA.resolve("client/BuildingTerminalScreen.java"));
        assertFalse(source.contains("PAGE_SIZE"));
        assertFalse(source.contains("changePage"));
        assertFalse(source.contains("pageCount"));
        assertFalse(source.contains("tr(\"page\""));
        assertTrue(source.contains("OsqScrollList.scroll(scroll, scrollY"));
        assertTrue(source.contains("logicalMouseX(mouseX) - leftPos"));
        assertTrue(source.contains("logicalMouseY(mouseY) - topPos"));
        assertEquals(2, source.split("int index = firstRow\\(\\) \\+ row;", -1).length - 1,
                "Both opening and removal use the visible row's absolute directory index");
        assertTrue(source.contains("scroll = OsqScrollList.clamp(scroll, rows.size(), LIST_HEIGHT, ROW_HEIGHT)"),
                "Deletion, data updates and resize keep the offset within the current list");
        assertTrue(source.contains("scrollbar.active = maxScroll() > 0"));
        assertTrue(source.contains("class DirectoryScrollbar extends AbstractWidget"));
        assertTrue(source.contains("onDrag(double mouseX, double mouseY"));
        assertTrue(source.contains("isDragging() && getFocused() == scrollbar"),
                "Vanilla container drag handling must explicitly forward captured scrollbar drags");
    }

    @Test
    void guidesAndScreenStyleMatchTheNewItemAndDirectorySize() throws Exception {
        var style = JsonParser.parseString(Files.readString(RESOURCES.resolve("assets/ae2/screens/building_access_terminal.json"))).getAsJsonObject();
        assertEquals(400, style.getAsJsonObject("generatedBackground").get("width").getAsInt());
        assertEquals(356, style.getAsJsonObject("generatedBackground").get("height").getAsInt());
        assertTrue(style.getAsJsonObject("slots").isEmpty());
        for (String prefix : List.of("", "_zh_cn/")) {
            String guide = Files.readString(ASSETS.resolve("ae2guide/" + prefix + "items-blocks-machines/building_access_terminal.md"));
            assertTrue(guide.contains("- molecularmanipulator:building_access_terminal"));
            assertTrue(guide.contains("parent: omnisequence-index.md"));
            assertTrue(guide.contains("64"));
        }
    }
}
