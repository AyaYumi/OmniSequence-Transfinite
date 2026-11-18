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
import net.minecraft.client.gui.components.Button;
import com.atir.molecularmanipulator.client.gui.theme.OsqLayout;
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
    private OsqUi ui;
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
        set(screen, "colorUpdateConfirmation", new StructureUpdateConfirmation());
        show(MolecularCenterScreen.TAB_OVERVIEW);
    }

    @AfterEach
    void restoreClientGlobals() throws Exception {
        if (savedLanguage != null) Language.inject(savedLanguage);
        if (minecraftInstance != null) minecraftInstance.set(null, savedMinecraft);
        if (suppliedClientConfig) ModConfig.CLIENT_SPEC.setConfig(null);
    }

    @ParameterizedTest
    @ValueSource(strings = {"en_us", "zh_cn"})
    void migrationAndRelatedActionKeysExistInBothLanguages(String locale) throws Exception {
        var translations = useLanguage(locale);
        assertEquals(PREFIX + "pattern_migration.button", key(button("migrate").getMessage()));
        assertEquals(PREFIX + "pattern_migration.tooltip", key(tooltip(button("migrate"))));
        assertEquals(PREFIX + "pattern_upload.off", key(button("upload").getMessage()));
        for (String name : List.of("migrate", "upload")) {
            assertDefined(button(name).getMessage(), translations, locale);
            assertDefined(tooltip(button(name)), translations, locale);
        }
        menu.automaticPatternUpload = true;
        show(MolecularCenterScreen.TAB_COLORS);
        assertEquals(PREFIX + "pattern_upload.on", key(button("upload").getMessage()));
        for (String name : List.of("update", "keep")) assertDefined(button(name).getMessage(), translations, locale);
        button("update").onPress();
        assertEquals(translations.get(PREFIX + "pattern_migration.button"), button("migrate").getMessage().getString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"en_us", "zh_cn"})
    void overviewActionsAndExplanationsDoNotOverlapForEitherStructure(String locale) throws Exception {
        useLanguage(locale);
        for (boolean legacy : new boolean[]{true, false}) {
            menu.legacyStructure = legacy;
            show(MolecularCenterScreen.TAB_OVERVIEW);
            var upload = bounds(button("upload"));
            var migrate = bounds(button("migrate"));
            assertFalse(upload.intersects(migrate));
            for (String key : List.of("overview_quantum_requirements", "spawn_protection", "spawn_protection_area")) {
                var entry = ui.layout.findText(PREFIX + key);
                assertNotNull(entry, key);
                float left = entry.drawX == null ? entry.x : entry.drawX;
                var label = new Bounds(left, entry.centerY - entry.size / 2, left + entry.w, entry.centerY + entry.size / 2);
                assertFalse(upload.intersects(label), key); assertFalse(migrate.intersects(label), key);
            }
            assertNull(button("update")); assertNull(button("keep"));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"en_us", "zh_cn"})
    void colorsAndReturningOverviewRefreshTheLegacyActionRow(String locale) throws Exception {
        useLanguage(locale);
        for (boolean legacy : new boolean[]{true, false}) {
            menu.legacyStructure = legacy;
            show(MolecularCenterScreen.TAB_COLORS);
            assertEquals(legacy, button("update") != null); assertEquals(legacy, button("keep") != null);
            assertNotNull(button("reset")); assertNotNull(button("effects"));
            assertEquals(15, allButtons().stream().filter(button -> button.getMessage().getString().matches("[RGB] [0-9]+" )).count());
            assertNonOverlappingLayout();
            show(MolecularCenterScreen.TAB_OVERVIEW);
            assertNull(button("update")); assertNull(button("reset")); assertNotNull(button("migrate"));
            assertNonOverlappingLayout();
        }
    }

    @Test void migrationRequiresOperationalIdleCenterButUploadPreferenceRemainsAccessible() throws Exception {
        for (boolean operational : new boolean[]{false, true}) for (boolean building : new boolean[]{false, true}) {
            for (boolean dismantling : new boolean[]{false, true}) {
                menu.operational = operational; menu.building = building; menu.dismantling = dismantling;
                invoke("refreshPatternTransferControls");
                var migrate = button("migrate");
                assertEquals(operational && !building && !dismantling, migrate.active);
                if (!migrate.active) assertFalse(migrate.mouseClicked(migrate.getX()+1, migrate.getY()+1, 0));
                assertTrue(button("upload").active);
            }
        }
    }

    @Test void dismissalAndAutoCraftTabDoNotLeaveStaleStructureOrPatternActions() throws Exception {
        menu.legacyStructureUpdateDismissed = true;
        show(MolecularCenterScreen.TAB_COLORS);
        assertNotNull(button("update"));
        for (int tab : new int[]{MolecularCenterScreen.TAB_AUTO_CRAFT, MolecularCenterScreen.TAB_OVERVIEW}) {
            show(tab);
            assertNull(button("update")); assertNull(button("keep")); assertNull(button("reset"));
            assertNotNull(button("upload")); assertNotNull(button("migrate"));
        }
    }

    static OsqUi layout(String name) throws Exception {
        var ctor = OsqLayout.class.getDeclaredConstructor(JsonObject.class);
        ctor.setAccessible(true);
        return new OsqUi(ctor.newInstance(json("/assets/molecularmanipulator/osq_light/layout/" + name + ".json")));
    }

    private void invoke(String name) throws Exception {
        var method = MolecularCenterScreen.class.getDeclaredMethod(name);
        method.setAccessible(true); method.invoke(screen);
    }

    private void show(int tab) throws Exception {
        for (String field : List.of("children", "renderables", "narratables", "screenWidgets")) set(screen, field, new ArrayList<>());
        ui = layout(tab == MolecularCenterScreen.TAB_COLORS ? "seq_center_colors" : tab == MolecularCenterScreen.TAB_AUTO_CRAFT ? "seq_center_autocraft" : "seq_center_overview");
        panelWidth = ui.layout.width; panelHeight = ui.layout.height;
        set(screen, "ui", ui); set(screen, "detailTab", tab);
        set(screen, "leftPos", 37); set(screen, "topPos", 23);
        invoke("initColorControls"); invoke("initPatternTransferControls");
        button("effects").visible = tab == MolecularCenterScreen.TAB_COLORS;
    }

    private Button button(String name) {
        String id = switch(name) { case "upload" -> "pattern_upload"; case "migrate" -> "migrate_patterns"; default -> name; };
        var box = ui.layout.find(id);
        return allButtons().stream().filter(button -> name.equals("effects") ? button instanceof MachineEffectButton
                : box != null && button.getX() == screen.getGuiLeft() + Math.round(box.x)
                && button.getY() == screen.getGuiTop() + Math.round(box.y)).findFirst().orElse(null);
    }

    private List<Button> allButtons() {
        return screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast).toList();
    }

    private static Component tooltip(Button button) throws Exception {
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

    private Bounds bounds(Button button) {
        float left = button.getX() - screen.getGuiLeft(), top = button.getY() - screen.getGuiTop();
        return new Bounds(left, top, left + button.getWidth(), top + button.getHeight());
    }

    private void assertNonOverlappingLayout() {
        var visible = allButtons().stream().filter(button -> button.visible).toList();
        for (int first = 0; first < visible.size(); first++) {
            var area = bounds(visible.get(first));
            assertTrue(area.left >= 0 && area.top >= 0 && area.right <= panelWidth && area.bottom <= panelHeight);
            for (int other = first + 1; other < visible.size(); other++) assertFalse(area.intersects(bounds(visible.get(other))));
        }
    }

    private record Bounds(float left, float top, float right, float bottom) {
        boolean intersects(Bounds other) {
            return left < other.right && other.left < right && top < other.bottom && other.top < bottom;
        }
    }
}
