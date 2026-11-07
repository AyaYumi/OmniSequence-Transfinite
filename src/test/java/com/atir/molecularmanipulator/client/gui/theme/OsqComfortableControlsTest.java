package com.atir.molecularmanipulator.client.gui.theme;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class OsqComfortableControlsTest {
    private static final List<String> ROOTS = List.of("src/main/resources", "resourcepacks/OmniSequence Dark");
    private static final String ASSET = "assets/molecularmanipulator/";

    private static OsqLayout load(String root, String name) throws Exception {
        var constructor = OsqLayout.class.getDeclaredConstructor(JsonObject.class);
        constructor.setAccessible(true);
        return constructor.newInstance(JsonParser.parseString(Files.readString(
                Path.of(root, ASSET, "osq_light/layout/" + name + ".json"))).getAsJsonObject());
    }

    @Test void everyPageHasPaddedFieldsAndComfortableTextButtons() throws Exception {
        for (String root : ROOTS) try (var files = Files.list(Path.of(root, ASSET, "osq_light/layout"))) {
            for (var file : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                var page = load(root, file.getFileName().toString().replace(".json", ""));
                for (var button : page.draw) {
                    if (!"ctl".equals(button.kind) || "scrollbar".equals(button.id)) continue;
                    assertTrue(button.h >= 16, page.screen + "/" + button.id);
                    if (button.sprite != null && button.sprite.contains("/field_"))
                        assertTrue(button.h >= 18, page.screen + "/" + button.id);
                    if (button.sprite != null && button.sprite.contains("/button_")) {
                        float minimum = button.id != null && button.id.matches("c[0-4][RGB]") ? 20 : 22;
                        assertTrue(button.h >= minimum, page.screen + "/" + button.id);
                    }
                    if (button.x >= 0) {
                        assertTrue(button.x + button.w <= page.width, page.screen + "/" + button.id);
                        assertTrue(button.y >= 0 && button.y + button.h <= page.height,
                                page.screen + "/" + button.id + " stays inside its enlarged window");
                    }
                }
            }
        }
    }

    @Test void allOwnedButtonIconsPreserveTheirNativeAspectAndStayInsideTheControl() throws Exception {
        for (String root : ROOTS) try (var files = Files.list(Path.of(root, ASSET, "osq_light/layout"))) {
            for (var file : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                var page = load(root, file.getFileName().toString().replace(".json", ""));
                for (var icon : page.draw) {
                    if (icon.ownerId == null || icon.sprite == null || !icon.sprite.contains("/icons/")) continue;
                    var button = page.findControl(icon.ownerId);
                    var image = ImageIO.read(Path.of(root, ASSET, "textures/gui/sprites/" + icon.sprite.split(":", 2)[1] + ".png").toFile());
                    assertEquals(image.getWidth() / (float)image.getHeight(), icon.w / icon.h, .001f, page.screen + "/" + icon.sprite);
                    assertTrue(icon.x >= button.x - .01f && icon.x + icon.w <= button.x + button.w + .01f, icon.sprite);
                    assertTrue(icon.y >= button.y - .01f && icon.y + icon.h <= button.y + button.h + .01f, icon.sprite);
                    boolean iconOnly = page.draw.stream().noneMatch(e -> "text".equals(e.type) && page.owns(button, e));
                    if (iconOnly) {
                        var center = OsqIconInk.measure(image);
                        assertEquals(button.x + button.w / 2, icon.x + icon.w * center.x(), .01f, page.screen + "/" + button.id);
                    }
                }
            }
        }
    }

    @Test void projectRowsAndResearchActionsGainHeightWithoutChangingOldTextAddresses() throws Exception {
        for (String root : ROOTS) for (String name : List.of("fab_research", "fab_research_detail")) {
            var page = load(root, name);
            for (int i = 0; i < 5; i++) {
                String rowId = "proj" + i;
                var row = page.findControl(rowId);
                assertEquals(21, row.h);
                assertEquals(54 + i * 25, row.y);
                var oldLabel = page.draw.stream().filter(e -> "text".equals(e.type)
                        && rowId.equals(e.ownerId)).findFirst().orElseThrow();
                assertTrue(page.owns(row, oldLabel));
            }
            var moved = page.findControl("proj4");
            var label = page.draw.stream().filter(e -> "proj4".equals(e.ownerId) && "text".equals(e.type)).findFirst().orElseThrow();
            assertEquals(135.5f, label.baseline);
            assertTrue(label.baseline < moved.y, "Explicit ownership must survive moving away from binding addresses");
            assertEquals(22, page.findControl("unlocks").h);
            assertTrue(page.findControl("stop").y + page.findControl("stop").h <= page.height - 6);
        }
    }

    @Test void fluidActionReplacesItsWholeOriginalControlIncludingTheMockToggle() throws Exception {
        for (String root : ROOTS) {
            var page = load(root, "fab_port_fluid");
            var action = page.findControl("auto");
            assertEquals(22, action.h);
            var toggle = page.draw.stream().filter(e -> e.sprite != null && e.sprite.contains("/toggle_")).findFirst().orElseThrow();
            assertTrue(page.owns(action, toggle), "The shared live widget must hide the static toggle");
            var capacity = page.draw.stream().filter(e -> "text".equals(e.type) && e.x == 96 && e.baseline == 89).findFirst().orElseThrow();
            assertTrue(capacity.centerY + capacity.size / 2 <= action.y - 6);
            var help = page.findControl("tool0");
            assertEquals("HELP", help.aeIcon, "Use AE2's real '?' icon, not a generated 'i'");
        }
        var screen = Files.readString(Path.of("src/main/java/com/atir/molecularmanipulator/client/MatterFabricationPortScreen.java"));
        assertTrue(screen.contains("fluid?ui.button(\"auto\""), "Input and output variants replace the same layout target");
        assertFalse(screen.contains("ui.rect(96,101"), "A mismatched runtime rectangle must never leave a duplicate source button");
    }

    @Test void autoCraftSettingsAndRunButtonsRemainSquareAndClearOfThePatternSlots() throws Exception {
        for (String root : ROOTS) for (String name : List.of("seq_autocrafter", "seq_center_autocraft")) {
            var page = load(root, name);
            float size = name.equals("seq_autocrafter") ? 16 : 18;
            for (int i = 0; i < 9; i++) for (String prefix : List.of("cfg", "run")) {
                var button = page.findControl(prefix + i);
                assertEquals(size, button.w);assertEquals(size, button.h);
                if (prefix.equals("cfg")) {
                    assertEquals("COG", button.aeIcon, "Native AE gear is scaled uniformly in both axes");
                    assertEquals(appeng.client.gui.Icon.COG.width, appeng.client.gui.Icon.COG.height);
                } else {
                    assertTrue(page.draw.stream().anyMatch(e -> e.sprite != null && e.sprite.contains("/icons/") && page.owns(button, e)));
                }
                for (var slot : page.draw) if ("slot".equals(slot.kind))
                    assertFalse(button.x < slot.x + slot.w && button.x + button.w > slot.x
                            && button.y < slot.y + slot.h && button.y + button.h > slot.y,
                            name + "/" + button.id + " overlaps an inventory/pattern slot");
            }
        }
    }

    @Test void helpControlsUseTheNativeAeQuestionMarkInBothThemes() throws Exception {
        for (String root : ROOTS) for (String name : List.of("fab_port_item", "fab_port_fluid", "seq_autocrafter")) {
            var page = load(root, name);
            var help = page.findControl("tool0");
            assertEquals("HELP", help.aeIcon);
            assertTrue(page.draw.stream().noneMatch(e -> page.owns(help, e) && e.sprite != null && e.sprite.contains("/icons/")),
                    "No old help artwork may be drawn over the native AE icon");
            assertFalse(Files.exists(Path.of(root, ASSET, "textures/gui/sprites/osq_light/common/icons/info_ink_16.png")));
        }
    }

    @Test void reserveSlotsAndLabelsFollowTheirTallerFieldsInsteadOfTheOldEighteenPixelPitch() throws Exception {
        for (String root : ROOTS) {
            var page = load(root, "seq_autocrafter_config");
            var slots = page.draw.stream().filter(e -> "slot".equals(e.kind) && "rsv".equals(e.id)).toList();
            assertEquals(5, slots.size());
            for (int i = 0; i < 5; i++) {
                int row = i;
                var field = page.findControl("rsv" + i);
                var apply = page.findControl("ok" + i);
                var label = page.draw.stream().filter(e -> "text".equals(e.type) && e.x == 39 && e.baseline == 135 + row*18).findFirst().orElseThrow();
                assertEquals(130 + i*20, field.y);
                assertEquals(field.y, slots.get(i).y);
                assertEquals(field.y + field.h/2, apply.y + apply.h/2);
                assertEquals(field.y + field.h/2, label.centerY);
                if (i > 0) assertTrue(field.y - slots.get(i-1).y - slots.get(i-1).h >= 2);
            }
            var main = load(root, "seq_center_autocraft");
            var caption = main.draw.stream().filter(e -> "text".equals(e.type) && e.x == 319 && e.baseline == 132).findFirst().orElseThrow();
            assertTrue(main.findControl("limit").y - caption.centerY - caption.size/2 >= 4);
            for (var marker : page.draw) if (marker.sprite != null && marker.sprite.contains("/dot_"))
                assertEquals(marker.x == 39 ? 56 : 252, marker.y + marker.h/2, .01f);
            for (var marker : main.draw) if (marker.sprite != null && marker.sprite.contains("/dot_") && (marker.x == 39 || marker.x == 223))
                assertEquals(marker.x == 39 ? 169 : 147.5f, marker.y + marker.h/2, .01f);
        }
    }

    @Test void enlargedHeaderTabsHaveSpaceBetweenTheirFrames() throws Exception {
        for (String root : ROOTS) for (String name : List.of("fab_overview", "fab_research", "seq_center_overview", "sing_overview")) {
            var page = load(root, name);
            var tabs = page.draw.stream().filter(e -> e.sprite != null && e.sprite.contains("/tab_")).toList();
            for (int i = 0; i < tabs.size(); i++) {
                assertEquals(22, tabs.get(i).h);
                if (i > 0) assertTrue(tabs.get(i).x - tabs.get(i - 1).x - tabs.get(i - 1).w >= 3.99f);
            }
        }
    }
}
