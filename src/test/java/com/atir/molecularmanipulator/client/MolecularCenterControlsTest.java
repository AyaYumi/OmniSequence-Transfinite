package com.atir.molecularmanipulator.client;

import static com.atir.molecularmanipulator.client.ResponsiveScreenTestFixture.set;
import static org.junit.jupiter.api.Assertions.*;

import com.atir.molecularmanipulator.blockentity.MachineEffectSettings;
import com.atir.molecularmanipulator.blockentity.MolecularAutoCrafter;
import com.atir.molecularmanipulator.blockentity.MolecularCenterBlockEntity;
import com.atir.molecularmanipulator.config.ModConfig;
import com.atir.molecularmanipulator.menu.MolecularCenterMenu;
import com.electronwill.nightconfig.core.CommentedConfig;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.resources.language.ClientLanguage;
import net.minecraft.core.BlockPos;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.joml.Vector3f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import sun.misc.Unsafe;

/** Runs the real controls/foreground without constructing a window, live menu or world. */
class MolecularCenterControlsTest {
    private static final String PREFIX = "gui.molecularmanipulator.";
    private UiRenderRecorder.MetricsFont font;
    private MolecularCenterMenu menu;
    private MolecularCenterScreen screen;
    private MolecularCenterControls controls;
    private Field minecraftInstance;
    private Minecraft savedMinecraft;
    private Language savedLanguage;
    private boolean suppliedClientConfig;
    private int panelWidth, panelHeight;

    @BeforeEach
    void constructRealControls() throws Exception {
        Class.forName(UiRenderRecorder.class.getName());
        savedLanguage = Language.getInstance();
        // MachineEffectButton reads the client config during construction. Never load
        // or write a user's config file, and preserve an already-loaded suite config.
        if (!ModConfig.CLIENT_SPEC.isLoaded()) {
            var config = CommentedConfig.inMemory();
            ModConfig.CLIENT_SPEC.correct(config);
            suppliedClientConfig = true;
            ModConfig.CLIENT_SPEC.setConfig(config);
        }
        var allocatorField = Unsafe.class.getDeclaredField("theUnsafe");
        allocatorField.setAccessible(true);
        var allocator = (Unsafe) allocatorField.get(null);
        font = new UiRenderRecorder.MetricsFont();

        // previewLabel consults Minecraft.getInstance(), not just screen.minecraft.
        // Supply a non-null center too; neither preview state nor a live client may leak.
        var minecraft = (Minecraft) allocator.allocateInstance(Minecraft.class);
        set(minecraft, "font", font);
        minecraftInstance = Minecraft.class.getDeclaredField("instance");
        minecraftInstance.setAccessible(true);
        savedMinecraft = (Minecraft) minecraftInstance.get(null);
        minecraftInstance.set(null, minecraft);
        var center = (MolecularCenterBlockEntity) allocator.allocateInstance(MolecularCenterBlockEntity.class);
        set(center, "worldPosition", BlockPos.ZERO);
        menu = (MolecularCenterMenu) allocator.allocateInstance(MolecularCenterMenu.class);
        set(menu, "center", center);
        menu.formed = menu.operational = menu.legacyStructure = true;
        menu.quantumLinkState = MolecularCenterBlockEntity.QuantumLinkState.EMPTY;
        menu.autoCraftSelectedSlot = -1;
        menu.autoCraftState = MolecularAutoCrafter.AutoCraftState.EMPTY;
        menu.dynamicEffectOverride = MachineEffectSettings.INHERIT;
        // Keep unrelated paging independent of server config and live inventories.
        menu.patternSearchActive = true;
        menu.patternSearchResultCount = MolecularCenterBlockEntity.PATTERNS_PER_PAGE;

        screen = (MolecularCenterScreen) allocator.allocateInstance(MolecularCenterScreen.class);
        set(screen, "menu", menu);
        set(screen, "minecraft", minecraft);
        set(screen, "font", font);
        set(screen, "playerInventoryTitle", Component.literal("Inventory"));
        set(screen, "patternSearchQuery", "");
        set(screen, "children", new ArrayList<>());
        set(screen, "renderables", new ArrayList<>());
        set(screen, "narratables", new ArrayList<>());
        set(screen, "screenWidgets", new ArrayList<>());
        set(screen, "screenContent", new ResponsiveScreenWidgets());
        var background = json("/assets/ae2/screens/molecular_center.json").getAsJsonObject("generatedBackground");
        panelWidth = background.get("width").getAsInt();
        panelHeight = background.get("height").getAsInt();
        set(screen, "imageWidth", panelWidth);
        set(screen, "imageHeight", panelHeight);
        // Nonzero origin also catches refresh accidentally writing local widget coordinates.
        set(screen, "leftPos", 37);
        set(screen, "topPos", 23);
        controls = new MolecularCenterControls(screen, menu);
    }

    @AfterEach
    void restoreClientGlobals() throws Exception {
        try {
            if (controls != null) controls.close();
        } finally {
            if (savedLanguage != null) Language.inject(savedLanguage);
            if (minecraftInstance != null) minecraftInstance.set(null, savedMinecraft);
            if (suppliedClientConfig) ModConfig.CLIENT_SPEC.setConfig(null);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"en_us", "zh_cn"})
    void migrationAndRelatedActionKeysExistInBothLanguages(String locale) throws Exception {
        var translations = useLanguage(locale);
        assertEquals(PREFIX + "pattern_migration.button", key(button("migrate").getMessage()));
        assertEquals(PREFIX + "pattern_migration.tooltip", key(tooltip(button("migrate"))));
        assertEquals(PREFIX + "pattern_upload.off", key(button("upload").getMessage()));
        for (String name : List.of("migrate", "upload", "update", "keep")) {
            var button = button(name);
            assertDefined(button.getMessage(), translations, locale);
            assertNotNull(button.getTooltip());
            assertDefined(tooltip(button), translations, locale);
        }
        menu.automaticPatternUpload = true;
        controls.tick();
        assertEquals(PREFIX + "pattern_upload.on", key(button("upload").getMessage()));
        assertDefined(button("upload").getMessage(), translations, locale);
        assertEquals(translations.get(PREFIX + "pattern_migration.button"), button("migrate").getMessage().getString());
        button("update").onPress(); // First confirmation only: no menu/network request.
        assertEquals(PREFIX + "structure_update_second_confirm", key(button("update").getMessage()));
        assertDefined(button("update").getMessage(), translations, locale);
    }

    @ParameterizedTest
    @ValueSource(strings = {"en_us", "zh_cn"})
    void overviewActionsAndExplanationsDoNotOverlapForEitherStructure(String locale) throws Exception {
        useLanguage(locale);
        assertEquals(286, panelHeight, "Use the real screen frame, not an enlarged test-only layout");
        for (boolean legacy : new boolean[] {true, false}) {
            menu.legacyStructure = legacy;
            show(MolecularCenterScreen.TAB_OVERVIEW);
            assertRectangle("upload", 306, 198, 108, 18);
            assertRectangle("migrate", 306, 219, 108, 18);
            assertRectangle("update", 210, 264, 100, 18);
            assertRectangle("keep", 314, 264, 100, 18);
            assertTrue(button("upload").visible);
            assertTrue(button("migrate").visible);
            assertEquals(legacy, button("update").visible);
            assertEquals(legacy, button("keep").visible);
            assertFalse(button("effects").visible);

            var graphics = foreground();
            assertTextY(graphics, "overview_quantum_requirements", 242,
                    Long.toString((long) MolecularCenterBlockEntity.QUANTUM_LINK_POWER));
            assertTextY(graphics, "spawn_protection", legacy ? 252 : 256);
            if (legacy) {
                assertFalse(graphics.text.stream().anyMatch(run -> run.value().equals(
                        Component.translatable(PREFIX + "spawn_protection_area").getString())));
            } else {
                assertTextY(graphics, "spawn_protection_area", 270);
            }
            assertNonOverlappingLayout(graphics, locale + ": overview, legacy=" + legacy);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"en_us", "zh_cn"})
    void colorsAndReturningOverviewRefreshTheLegacyActionRow(String locale) throws Exception {
        useLanguage(locale);
        for (boolean legacy : new boolean[] {true, false}) {
            menu.legacyStructure = legacy;
            show(MolecularCenterScreen.TAB_COLORS);
            assertFalse(button("upload").visible);
            assertFalse(button("migrate").visible);
            assertTrue(button("reset").visible);
            assertTrue(button("effects").visible);
            assertEquals(15, allButtons().stream().filter(button -> button.visible
                    && List.of("R", "G", "B").contains(button.getMessage().getString())).count());
            assertEquals(legacy, button("update").visible);
            assertEquals(legacy, button("keep").visible);
            assertRectangle("update", 210, 233, 100, 18);
            assertRectangle("keep", 314, 233, 100, 18);
            assertNonOverlappingLayout(foreground(), locale + ": colors, legacy=" + legacy);

            show(MolecularCenterScreen.TAB_OVERVIEW);
            assertRectangle("update", 210, 264, 100, 18);
            assertRectangle("keep", 314, 264, 100, 18);
            assertFalse(button("reset").visible);
            assertTrue(button("migrate").visible);
            assertNonOverlappingLayout(foreground(), locale + ": returned overview, legacy=" + legacy);
        }
    }

    @Test
    void migrationRequiresOperationalAndBusyDisablesBothPatternActions() throws Exception {
        show(MolecularCenterScreen.TAB_OVERVIEW);
        for (boolean operational : new boolean[] {false, true}) {
            for (boolean building : new boolean[] {false, true}) {
                for (boolean dismantling : new boolean[] {false, true}) {
                    menu.operational = operational;
                    menu.building = building;
                    menu.dismantling = dismantling;
                    controls.tick();
                    boolean busy = building || dismantling;
                    String state = "operational=" + operational + ", building=" + building + ", dismantling=" + dismantling;
                    assertTrue(button("upload").visible, state);
                    assertTrue(button("migrate").visible, state);
                    assertEquals(!busy, button("upload").active, state);
                    assertEquals(operational && !busy, button("migrate").active, state);
                    for (String name : List.of("build", "update", "keep")) {
                        assertEquals(!busy, button(name).active, name + ": " + state);
                    }
                    // Exercise native click rejection, without firing any network actions.
                    for (String name : List.of("upload", "migrate", "build", "update", "keep")) {
                        var button = button(name);
                        if (!button.active) assertFalse(button.mouseClicked(
                                button.getX() + button.getWidth() / 2.0,
                                button.getY() + button.getHeight() / 2.0, 0), name + ": " + state);
                    }
                }
            }
        }
        menu.building = menu.dismantling = false;
        controls.tick();
        assertTrue(button("migrate").active, "An operational center must recover after being busy");
        assertTrue(button("upload").active);
    }

    @Test
    void dismissalAndAutoCraftTabDoNotLeaveStaleStructureOrPatternActions() throws Exception {
        menu.legacyStructureUpdateDismissed = true;
        for (int tab : new int[] {MolecularCenterScreen.TAB_OVERVIEW, MolecularCenterScreen.TAB_COLORS}) {
            show(tab);
            assertTrue(button("update").visible, "Dismissing legacy advice must not remove the update action");
            assertFalse(button("keep").visible);
        }
        show(MolecularCenterScreen.TAB_AUTO_CRAFT);
        for (String name : List.of("update", "keep", "upload", "migrate", "reset", "effects")) {
            var button = button(name);
            assertFalse(button.visible, name);
            assertFalse(button.mouseClicked(button.getX() + 1, button.getY() + 1, 0), name);
        }
        menu.legacyStructureUpdateDismissed = false;
        show(MolecularCenterScreen.TAB_OVERVIEW);
        assertTrue(button("keep").visible);
        assertTrue(button("upload").visible);
        assertTrue(button("migrate").visible);
        assertRectangle("keep", 314, 264, 100, 18);
    }

    private void show(int tab) throws Exception {
        // Bypass unrelated edit-box/slot initialization, but execute the real tick/refresh.
        set(screen, "detailTab", tab);
        controls.tick();
    }

    private OmniButton button(String name) throws Exception {
        var field = MolecularCenterControls.class.getDeclaredField(name);
        field.setAccessible(true);
        return (OmniButton) field.get(controls);
    }

    private List<OmniButton> allButtons() {
        return screen.children().stream().filter(OmniButton.class::isInstance).map(OmniButton.class::cast).toList();
    }

    private static Component tooltip(OmniButton button) throws Exception {
        var field = Tooltip.class.getDeclaredField("message");
        field.setAccessible(true);
        return (Component) field.get(button.getTooltip());
    }

    private static String key(Component component) {
        return assertInstanceOf(TranslatableContents.class, component.getContents()).getKey();
    }

    private static void assertDefined(Component component, Map<String, String> translations, String locale) {
        if (component.getContents() instanceof TranslatableContents text) {
            assertTrue(translations.containsKey(text.getKey()), locale + ": undefined " + text.getKey());
            assertFalse(translations.get(text.getKey()).isBlank(), locale + ": empty " + text.getKey());
            for (var argument : text.getArgs()) {
                if (argument instanceof Component nested) assertDefined(nested, translations, locale);
            }
        }
        for (var sibling : component.getSiblings()) assertDefined(sibling, translations, locale);
    }

    private Map<String, String> useLanguage(String locale) throws Exception {
        var translations = new HashMap<String, String>();
        json("/assets/molecularmanipulator/lang/" + locale + ".json").entrySet()
                .forEach(entry -> translations.put(entry.getKey(), entry.getValue().getAsString()));
        var constructor = ClientLanguage.class.getDeclaredConstructor(Map.class, boolean.class);
        constructor.setAccessible(true);
        Language.inject(constructor.newInstance(translations, false));
        return translations;
    }

    private static JsonObject json(String path) throws Exception {
        try (var stream = MolecularCenterControlsTest.class.getResourceAsStream(path)) {
            assertNotNull(stream, path);
            try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                return JsonParser.parseReader(reader).getAsJsonObject();
            }
        }
    }

    private UiRenderRecorder foreground() {
        var graphics = new UiRenderRecorder();
        // AE2's foreground is panel-local; widget hitboxes include the screen origin.
        screen.drawFG(graphics, 0, 0, -1, -1);
        return graphics;
    }

    private Bounds bounds(OmniButton button) {
        float x = button.getX() - screen.getGuiLeft(), y = button.getY() - screen.getGuiTop();
        return new Bounds(x, y, x + button.getWidth(), y + button.getHeight());
    }

    private Bounds bounds(UiRenderRecorder.Text text) {
        var low = text.transform().transformPosition(new Vector3f(text.x(), text.y(), 0));
        var high = text.transform().transformPosition(new Vector3f(
                text.x() + font.width(text.value()), text.y() + font.lineHeight, 0));
        return new Bounds(low.x, low.y, high.x, high.y);
    }

    private void assertRectangle(String name, int x, int y, int width, int height) throws Exception {
        assertEquals(new Bounds(x, y, x + width, y + height), bounds(button(name)), name);
    }

    private void assertTextY(UiRenderRecorder graphics, String key, int y, Object... args) {
        String expected = Component.translatable(PREFIX + key, args).getString();
        var matches = graphics.text.stream().filter(text -> text.value().equals(expected)).toList();
        assertEquals(1, matches.size(), key);
        assertEquals(y, bounds(matches.get(0)).top(), 0.001F, key);
    }

    private void assertNonOverlappingLayout(UiRenderRecorder graphics, String state) {
        var visible = allButtons().stream().filter(button -> button.visible).toList();
        assertFalse(visible.isEmpty(), state);
        assertFalse(graphics.text.isEmpty(), state);
        for (int i = 0; i < visible.size(); i++) {
            var button = visible.get(i);
            var area = bounds(button);
            assertInsideFrame(area, state + ": " + button.getMessage().getString());
            for (int j = i + 1; j < visible.size(); j++) {
                var other = visible.get(j);
                assertFalse(area.intersects(bounds(other)), state + ": overlapping buttons "
                        + button.getMessage().getString() + " / " + other.getMessage().getString());
            }
            for (var text : graphics.text) {
                if (!text.value().isEmpty()) assertFalse(area.intersects(bounds(text)), state
                        + ": button " + button.getMessage().getString() + " overlaps text " + text.value());
            }
        }
        var textRuns = graphics.text.stream().filter(text -> !text.value().isEmpty()).toList();
        for (int i = 0; i < textRuns.size(); i++) {
            var text = textRuns.get(i);
            var area = bounds(text);
            assertInsideFrame(area, state + ": " + text.value());
            for (int j = i + 1; j < textRuns.size(); j++) {
                var other = textRuns.get(j);
                assertFalse(area.intersects(bounds(other)), state + ": overlapping text "
                        + text.value() + " / " + other.value());
            }
        }
    }

    private void assertInsideFrame(Bounds area, String message) {
        assertTrue(area.left >= -0.001F && area.top >= -0.001F
                && area.right <= panelWidth + 0.001F && area.bottom <= panelHeight + 0.001F,
                message + ": outside actual frame: " + area);
    }

    private record Bounds(float left, float top, float right, float bottom) {
        boolean intersects(Bounds other) {
            return left < other.right && other.left < right && top < other.bottom && other.top < bottom;
        }
    }
}
