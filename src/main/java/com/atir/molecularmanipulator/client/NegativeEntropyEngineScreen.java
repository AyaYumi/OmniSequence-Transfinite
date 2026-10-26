package com.atir.molecularmanipulator.client;

import appeng.api.client.AEKeyRendering;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.GenericStack;
import appeng.client.gui.style.ScreenStyle;
import com.atir.molecularmanipulator.client.gui.theme.OsqFonts;
import com.atir.molecularmanipulator.client.gui.theme.OsqLayout;
import com.atir.molecularmanipulator.entropy.NegativeEntropyRecipeCatalog.Entry;
import com.atir.molecularmanipulator.entropy.NegativeEntropyBenefits;
import com.atir.molecularmanipulator.menu.NegativeEntropyEngineMenu;
import com.atir.molecularmanipulator.menu.NegativeEntropyEngineMenu.ActionResult;
import com.atir.molecularmanipulator.menu.NegativeEntropyEngineMenu.RecipeNode;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/** OSQ geometry and typography over synchronized AE2 state, with no simulated payments. */
public final class NegativeEntropyEngineScreen extends ResponsiveContainerScreen<NegativeEntropyEngineMenu> {
    private static final String PREFIX = "gui.molecularmanipulator.entropy.";
    private static final NumberFormat INTEGER = NumberFormat.getIntegerInstance(Locale.ROOT);
    private static final int RECIPE_X = 213, RECIPE_Y = 170, RECIPE_STEP = 21, RECIPE_COLUMNS = 8;
    private static final ResourceLocation CONTENTS_FONT = OsqFonts.id("osq_entropy_600_5_4");
    private OsqUi ui;
    private boolean recipesPage;
    private int selectedNode;
    private Button overclock, unlock, refresh, prerequisites;
    private final List<Button> nodeButtons = new ArrayList<>();
    private int pendingVersion = -1;
    private int seenVersion;
    private long pendingSince;
    private long resultUntil;

    public NegativeEntropyEngineScreen(NegativeEntropyEngineMenu menu, Inventory inventory, Component title,
            ScreenStyle style) {
        super(menu, inventory, title, style);
    }

    @Override
    protected void init() {
        ui = new OsqUi("negative_entropy_engine");
        imageWidth = ui.layout.width;
        imageHeight = ui.layout.height;
        super.init();
        ui.inventory(menu);
        seenVersion = menu.resultVersion;
        nodeButtons.clear();
        addScreenWidget(new EngineButton("overview", () -> tr("overview"), () -> setPage(false)));
        addScreenWidget(new EngineButton("recipes", () -> tr("recipes"), () -> setPage(true)));
        overclock = addScreenWidget(new EngineButton("overclock", this::overclockLabel,
                () -> request(menu::requestToggleOverclock)));
        overclock.setTooltip(Tooltip.create(tr("overclock_help")));
        unlock = addScreenWidget(new EngineButton("unlock", this::unlockLabel,
                () -> request(() -> menu.requestUnlock(selectedNode))));
        refresh = addScreenWidget(new EngineButton("refresh", () -> tr("refresh"),
                () -> request(menu::requestRefresh)));
        prerequisites = addScreenWidget(new EngineButton("prerequisites", () -> tr("view_prerequisites"),
                this::selectPrerequisite));
        addScreenWidget(new EngineButton("guide", () -> tr("guide"), () -> OsqGuide.open("negative_entropy_engine")));
        for (var node : NegativeEntropyEngineMenu.NODES) {
            var button = new EngineButton("node." + node.index(), () -> nodeName(node), () -> {
                selectedNode = node.index();
                updateControls();
            });
            button.setTooltip(Tooltip.create(tr("node_tooltip", nodeName(node), number(node.entropy()),
                    number(node.informationCost()))));
            nodeButtons.add(addScreenWidget(button));
        }
        updateControls();
    }

    private void setPage(boolean recipes) {
        recipesPage = recipes;
        updateControls();
    }

    private RecipeNode selected() { return NegativeEntropyEngineMenu.NODES.get(selectedNode); }
    private static Component tr(String key, Object... args) { return Component.translatable(PREFIX + key, args); }
    private static Component nodeName(RecipeNode node) { return Component.translatable(node.translationKey()); }
    private static String number(long value) { return INTEGER.format(value); }
    private int color(String name) { return OsqLayout.argb(ui.layout.find("color." + name).color); }

    private boolean waiting() { return pendingVersion >= 0; }
    private void request(Runnable action) {
        if (waiting()) return;
        pendingVersion = menu.resultVersion;
        pendingSince = Util.getMillis();
        action.run();
        updateControls();
    }

    private void updateControls() {
        if (overclock == null) return;
        overclock.visible = !recipesPage;
        overclock.active = !waiting() && menu.canToggleOverclock();
        refresh.visible = !recipesPage;
        refresh.active = !waiting() && menu.canConfigure;
        unlock.visible = recipesPage;
        unlock.active = !waiting() && menu.canUnlock(selected());
        prerequisites.visible = recipesPage;
        prerequisites.active = selected().prerequisiteMask() != 0;
        nodeButtons.forEach(button -> button.visible = recipesPage);
    }

    @Override
    public void containerTick() {
        super.containerTick();
        if (seenVersion != menu.resultVersion) {
            seenVersion = menu.resultVersion;
            pendingVersion = -1;
            resultUntil = Util.getMillis() + 6000;
        }
        // A lost response releases the input lock; it never assumes success or edits synchronized data.
        if (waiting() && Util.getMillis() - pendingSince > 5000) pendingVersion = -1;
        updateControls();
    }

    private void selectPrerequisite() {
        int mask = selected().prerequisiteMask();
        for (var node : NegativeEntropyEngineMenu.NODES) {
            if ((mask & (1 << node.index())) != 0 && !node.unlocked(menu.unlockMask)) {
                selectedNode = node.index(); updateControls(); return;
            }
        }
        if (mask != 0) selectedNode = Integer.numberOfTrailingZeros(mask);
        updateControls();
    }

    private Component overclockLabel() {
        if (waiting()) return tr("waiting");
        return tr(menu.overclocked ? "overclock_on" : "overclock_off");
    }

    private Component unlockLabel() {
        if (waiting()) return tr("waiting");
        var node = selected();
        if (node.unlocked(menu.unlockMask)) return tr("unlocked_permanent");
        if (!menu.canConfigure) return tr("result.not_permitted");
        if (!menu.formed) return tr("result.structure_incomplete");
        if (!menu.networkOnline) return tr("result.offline");
        if (menu.effectiveEntropy < node.entropy()) return tr("result.insufficient_entropy");
        if (!node.prerequisitesMet(menu.unlockMask)) return tr("result.missing_prerequisites");
        if (menu.infoBalance < node.informationCost()) return tr("result.insufficient_information");
        return tr("pay_unlock", number(node.informationCost()));
    }

    private Component nodeState(RecipeNode node) {
        return tr(node.unlocked(menu.unlockMask) ? "unlocked" : menu.canUnlock(node) ? "available" : "locked");
    }

    @Override
    public void drawBG(GuiGraphics g, int x, int y, int mx, int my, float pt) {
        if (ui == null) return;
        ui.background(g, font, x, y);
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        if (recipesPage) {
            card(g, 204, 32, 188, 112, "card");
            card(g, 204, 150, 188, 65, "card");
            fill(g, 8, 181, 190, 19, color("soft"));
            treeConnections(g);
        } else {
            card(g, 204, 32, 188, 82, "feature");
            card(g, 204, 120, 188, 95, "card");
            fill(g, 8, 159, 79, 41, color("accent_fill"));
            drawHologram(g);
            drawLevel(g);
            drawBenefitIcons(g);
        }
        card(g, 204, 221, 188, 26, "card");
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++) slot(g, 22 + col * 18, 212 + row * 18);
        for (int col = 0; col < 9; col++) slot(g, 22 + col * 18, 270);
        g.pose().popPose();
    }

    private void slot(GuiGraphics g, int x, int y) {
        fill(g, x, y, 18, 18, color("slot"));
        g.renderOutline(x, y, 18, 18, color("hair"));
    }

    private static void fill(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + h, color);
    }

    private void card(GuiGraphics g, int x, int y, int w, int h, String face) {
        fill(g, x, y + 1, w, h + 1, color("shadow"));
        fill(g, x, y, w, h, color(face));
    }

    @Override
    public void drawFG(GuiGraphics g, int x, int y, int mx, int my) {
        if (ui == null) return;
        ui.foreground(g, font);
        text(g, Component.translatable("block.molecularmanipulator.negative_entropy_engine_core"),
                12, 23, 112, "osq_nsc_900_13", 13, "ink", "left");
        small(g, tr("subtitle"), 127, 21, 110, "muted");
        small(g, tr(!menu.formed ? "incomplete" : !menu.networkOnline ? "offline"
                : menu.overclocked ? "overclock_running" : "stable"), 251, 18, 61, "accent");
        if (recipesPage) drawRecipes(g); else drawOverview(g);
        boolean feedback = waiting() || Util.getMillis() < resultUntil && menu.actionResult != ActionResult.NONE;
        if (!feedback) {
            small(g, tr(menu.networkOnline ? "network_online" : "network_offline"), 213, 232, 82,
                    menu.networkOnline ? "ink2" : "muted");
            if (!recipesPage) small(g, tr("consumption", number(menu.infoConsumption)), 299, 232, 84, "muted", "right");
        }
        small(g, tr("balance", number(menu.infoBalance)), 213, 242, 170, "ink2");
        if (waiting()) small(g, tr("waiting"), 213, 232, 170, "muted");
        else if (feedback) {
            Component result = tr("result." + menu.actionResult.name().toLowerCase(Locale.ROOT));
            if (menu.resultNode >= 0 && menu.resultNode < NegativeEntropyEngineMenu.NODES.size()) {
                result = tr("node_result", nodeName(NegativeEntropyEngineMenu.NODES.get(menu.resultNode)), result);
            }
            boolean success = switch (menu.actionResult) {
                case UNLOCKED, ALREADY_UNLOCKED, OVERCLOCK_ENABLED, OVERCLOCK_DISABLED, STRUCTURE_REFRESHED -> true;
                default -> false;
            };
            small(g, result, 213, 232, 170, success ? "accent" : "warning");
        }
    }

    private void drawOverview(GuiGraphics g) {
        label(g, tr("hologram"), 17, 45, 127, "ink2");
        small(g, tr(menu.formed ? "formed" : "incomplete"), 154, 45, 35, menu.formed ? "accent" : "warning", "right");
        small(g, tr("hologram_hint"), 17, 56, 168, "muted");
        small(g, tr("engine_body"), 145, 77, 43, "ink2");
        small(g, tr("anchor_schematic"), 17, 143, 54, "muted");
        small(g, tr("effective_anchors"), 17, 170, 63, "inverse");
        text(g, Component.literal(number(menu.anchorCount)), 15, 192, 49,
                "osq_intertight_700_26", 26, "inverse", "left");
        small(g, tr("blocks"), 65, 190, 15, "inverse");

        var benefit = menu.benefits();
        label(g, tr("effective_entropy"), 213, 46, 110, "feature_muted");
        text(g, Component.literal(number(menu.effectiveEntropy)), 212, 71, 129,
                "osq_intertight_700_26", 26, "feature_ink", "left");
        text(g, Component.literal("L" + benefit.level()), 353, 62, 30,
                "osq_intertight_700_16", 16, "inverse", "center");
        small(g, tr(benefit.level() == 9 ? "maximum_level" : "current_level", benefit.level()),
                213, 82, 167, "feature_muted");
        int next = Math.min(9, benefit.level() + 1);
        small(g, tr(benefit.level() == 9 ? "maximum_level" : "next_level", next), 213, 103, 73, "feature_muted");
        small(g, Component.literal(number(menu.effectiveEntropy) + " / " + number(NegativeEntropyBenefits.threshold(next))),
                286, 103, 97, "feature_ink", "right");

        label(g, tr("network_benefits"), 213, 132, 106, "ink");
        small(g, tr(benefit.infinite() ? "infinite_overclock" : benefit.overclocked() ? "overclock_double" : "base_benefits"),
                318, 132, 65, "accent", "right");
        var base = new NegativeEntropyBenefits(benefit.level(), false);
        benefitRow(g, 137, "assembler_parallel", amount(benefit.assemblerParallel()),
                tr("assembler_detail", tr(benefit.virtualCpu() ? "virtual_parallel" : "physical_parallel"),
                        OsqUi.compact(base.assemblerParallel())));
        benefitRow(g, 155, "cpu_parallel", amount(benefit.cpuParallel()),
                tr("cpu_detail", tr(benefit.virtualCpu() ? "virtual_parallel" : "physical_parallel"),
                        OsqUi.compact(base.cpuParallel())));
        benefitRow(g, 173, "cpu_storage", storage(benefit.storageBytes()), tr("storage_detail", storage(base.storageBytes())));
        benefitRow(g, 191, "fabrication_speed", benefit.infinite() ? tr("one_tick")
                : Component.literal("×" + benefit.speedMultiplier()), tr("duration_detail", processingTime(benefit)));
        small(g, tr("research_parallel_only"), 232, 212, 151, "muted");
    }

    private void benefitRow(GuiGraphics g, int y, String title, Component value, Component detail) {
        small(g, tr(title), 232, y + 7, 77, "ink2");
        text(g, value, 310, y + 9, 73, "osq_intertight_700_9", 9, "accent", "right");
        text(g, detail, 232, y + 15, 151, "osq_nsc_500_5", 5, "muted", "left");
    }

    private static Component amount(long value) {
        return value == Long.MAX_VALUE ? tr("infinite") : Component.literal(number(value));
    }

    private static Component storage(long bytes) {
        if (bytes == Long.MAX_VALUE) return tr("infinite");
        String[] units = {"B", "KiB", "MiB", "GiB", "TiB"};
        double value = bytes;
        int unit = 0;
        while (value >= 1024 && unit < units.length - 1) { value /= 1024; unit++; }
        return Component.literal(String.format(Locale.ROOT, "%s %s", decimal(value), units[unit]));
    }

    private static String decimal(double value) {
        return value == Math.floor(value) ? Long.toString((long) value) : String.format(Locale.ROOT, "%.2f", value);
    }

    private static Component processingTime(NegativeEntropyBenefits benefit) {
        int ticks = benefit.processingTicks(NegativeEntropyEngineMenu.DEFAULT_PROCESSING_TICKS);
        return ticks == 1 ? tr("one_tick") : Component.literal(decimal(ticks / 20.0) + " s");
    }

    private void drawLevel(GuiGraphics g) {
        fill(g, 352, 43, 31, 25, color("accent_fill"));
        int level = menu.benefitLevel;
        for (int i = 0; i < 9; i++) fill(g, 213 + i * 19, 88, 17, 3,
                color(i < level ? "level" : "feature_track"));
        fill(g, 213, 107, 170, 2, color("feature_track"));
        int next = Math.min(9, level + 1);
        double progress = level == 9 ? 1 : Math.clamp((double) menu.effectiveEntropy
                / Math.max(1, NegativeEntropyBenefits.threshold(next)), 0, 1);
        fill(g, 213, 107, (int) Math.round(170 * progress), 2, color("level"));
    }

    private void drawRecipes(GuiGraphics g) {
        label(g, tr("recipes"), 17, 45, 90, "ink");
        small(g, tr("permanent_unlock"), 137, 45, 52, "muted", "right");
        small(g, tr("tree_hint"), 17, 56, 172, "muted");
        small(g, tr("unlocked"), 17, 174, 47, "accent");
        small(g, tr("available"), 75, 174, 47, "accent");
        small(g, tr("locked"), 135, 174, 54, "muted");
        small(g, tr("entropy_total", number(menu.effectiveEntropy)), 17, 193, 102, "accent");
        small(g, tr("unlock_count", Integer.bitCount(menu.unlockMask & NegativeEntropyEngineMenu.ALL_UNLOCKS),
                NegativeEntropyEngineMenu.NODES.size()), 119, 193, 70, "ink2", "right");
        var node = selected();
        text(g, nodeName(node), 213, 48, 119, "osq_nsc_700_9", 9, "ink", "left");
        small(g, nodeState(node), 333, 46, 50, node.unlocked(menu.unlockMask) || menu.canUnlock(node) ? "accent" : "muted", "right");
        small(g, tr("unlock_hint"), 213, 60, 170, "muted");
        fill(g, 213, 66, 170, 1, color("hair"));
        small(g, tr("entropy_threshold"), 213, 79, 67, "ink2");
        small(g, Component.literal(number(menu.effectiveEntropy) + " / " + number(node.entropy())), 280, 79, 103,
                menu.effectiveEntropy >= node.entropy() ? "accent" : "warning", "right");
        small(g, tr("prerequisites"), 213, 94, 54, "ink2");
        small(g, prerequisiteNames(node), 268, 94, 115, node.prerequisitesMet(menu.unlockMask) ? "accent" : "warning", "right");
        small(g, tr("information_cost"), 213, 109, 73, "ink2");
        small(g, Component.literal(number(node.informationCost()) + " bit"), 286, 109, 97,
                menu.infoBalance >= node.informationCost() ? "accent" : "warning", "right");
        small(g, tr("entropy_not_spent"), 213, 133, 170, "muted");
        label(g, tr("unlock_contents"), 213, 163, 140, "ink");
        // Already decoded once by GuiSync; never consult the client's optional recipe registry.
        var recipePreviews = menu.catalog.recipes(node.index());
        small(g, Component.literal("×" + recipePreviews.size()), 357, 163, 26, "muted", "right");
        var contents = Component.empty();
        for (int i = 0; i < recipePreviews.size(); i++) {
            var recipe = recipePreviews.get(i);
            if (i > 0) contents.append(" · ");
            contents.append(recipeTitle(recipe));
            if (i >= RECIPE_COLUMNS) continue;
            int x = RECIPE_X + i * RECIPE_STEP;
            fill(g, x - 1, RECIPE_Y - 1, 18, 18, color("soft"));
            if (!recipe.outputs().isEmpty()) {
                AEKeyRendering.drawInGui(minecraft, g, x, RECIPE_Y, recipe.outputs().getFirst().what());
            } else {
                small(g, Component.literal("?"), x, RECIPE_Y + 11, 16, "muted", "center");
            }
        }
        small(g, recipePreviews.isEmpty() ? tr("catalog_waiting") : ellipsize(contents, 170), 213, 196, 170, "ink");
        small(g, tr("overclock_no_gate"), 213, 207, 170, "muted");
    }

    private Component prerequisiteNames(RecipeNode node) {
        var names = Component.empty();
        for (var parent : NegativeEntropyEngineMenu.NODES) if ((node.prerequisiteMask() & (1 << parent.index())) != 0) {
            if (!names.getString().isEmpty()) names.append(" · ");
            names.append(nodeName(parent));
        }
        return node.prerequisiteMask() == 0 ? tr("no_prerequisites") : tr(node.prerequisitesMet(menu.unlockMask)
                ? "prerequisites_done" : "prerequisites_needed", names);
    }

    private static Component recipeTitle(Entry recipe) {
        return !recipe.present() ? tr("recipe_missing") : recipe.outputs().isEmpty() ? tr("recipe_no_outputs")
                : recipe.outputs().getFirst().what().getDisplayName();
    }

    private static Component outputName(GenericStack output) {
        return output.what().getDisplayName().copy().append(" ×" + number(output.amount())
                + (output.what() instanceof AEFluidKey ? " mB" : ""));
    }

    private List<Component> recipeTooltip(Entry recipe) {
        var lines = new ArrayList<Component>();
        lines.add(recipeTitle(recipe));
        lines.add(Component.literal(recipe.id().toString()));
        lines.add(nodeState(selected()));
        if (recipe.outputs().isEmpty()) {
            lines.add(tr(recipe.present() ? "recipe_no_outputs" : "recipe_missing"));
        } else {
            lines.add(Component.translatable("gui.molecularmanipulator.research.recipe_outputs"));
            for (var output : recipe.outputs()) lines.add(outputName(output));
        }
        lines.add(tr("recipes_permanent"));
        return lines;
    }

    private List<Component> contentsTooltip() {
        var lines = new ArrayList<Component>();
        lines.add(nodeName(selected()));
        lines.add(tr("unlock_contents"));
        var recipePreviews = menu.catalog.recipes(selectedNode);
        if (recipePreviews.isEmpty()) lines.add(tr("catalog_waiting"));
        for (var recipe : recipePreviews) {
            if (recipe.outputs().isEmpty()) {
                lines.add(Component.literal(recipe.id() + " · ").append(recipeTitle(recipe)));
            } else {
                for (var output : recipe.outputs()) lines.add(outputName(output));
            }
        }
        lines.add(tr("recipes_permanent"));
        return lines;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        if (ui == null || !recipesPage) return;
        double x = logicalMouseX(mx) - leftPos, y = logicalMouseY(my) - topPos;
        if (x < 212 || x >= 384 || y < 154 || y >= 200) return;
        var recipePreviews = menu.catalog.recipes(selectedNode);
        int column = (int) ((x - (RECIPE_X - 1)) / RECIPE_STEP);
        boolean icon = y >= RECIPE_Y - 1 && y < RECIPE_Y + 17
                && column < Math.min(RECIPE_COLUMNS, recipePreviews.size())
                && x < RECIPE_X - 1 + column * RECIPE_STEP + 18;
        g.renderComponentTooltip(font, icon ? recipeTooltip(recipePreviews.get(column)) : contentsTooltip(), mx, my);
    }

    /** Keep the output summary legible; hovering the card exposes every full name. */
    private Component ellipsize(Component value, float width) {
        String text = value.getString();
        if (OsqFonts.widthF(font, text, CONTENTS_FONT) <= width) return value;
        int low = 0, high = text.codePointCount(0, text.length());
        while (low < high) {
            int middle = (low + high + 1) / 2;
            String candidate = text.substring(0, text.offsetByCodePoints(0, middle)) + "…";
            if (OsqFonts.widthF(font, candidate, CONTENTS_FONT) <= width) low = middle;
            else high = middle - 1;
        }
        return Component.literal(text.substring(0, text.offsetByCodePoints(0, low)) + "…");
    }

    private void treeConnections(GuiGraphics g) {
        for (var node : NegativeEntropyEngineMenu.NODES) {
            var target = ui.layout.findControl("node." + node.index());
            for (var parent : NegativeEntropyEngineMenu.NODES) if ((node.prerequisiteMask() & (1 << parent.index())) != 0) {
                var source = ui.layout.findControl("node." + parent.index());
                int tint = color(parent.unlocked(menu.unlockMask) ? "accent" : "hair");
                int sx = Math.round(source.x + source.w), sy = Math.round(source.y + source.h / 2);
                int tx = Math.round(target.x), ty = Math.round(target.y + target.h / 2);
                if (source.x == target.x) {
                    int cx = Math.round(source.x + source.w / 2);
                    line(g, cx, Math.round(source.y + source.h), cx, Math.round(target.y), tint);
                } else {
                    int mid = (sx + tx) / 2;
                    line(g, sx, sy, mid, sy, tint);
                    line(g, mid, sy, mid, ty, tint);
                    line(g, mid, ty, tx, ty, tint);
                }
            }
        }
    }

    private void drawBenefitIcons(GuiGraphics g) {
        for (int row = 0; row < 4; row++) {
            int y = 138 + row * 18, tint = color("accent");
            fill(g, 213, y, 13, 13, color("soft"));
            if (row == 0) {
                for (int a = 0; a < 2; a++) for (int b = 0; b < 2; b++) g.renderOutline(216 + a * 4, y + 3 + b * 4, 3, 3, tint);
            } else if (row == 1) {
                g.renderOutline(217, y + 4, 5, 5, tint);
                for (int i = 0; i < 3; i++) {
                    line(g, 215, y + 4 + i * 2, 224, y + 4 + i * 2, tint);
                    line(g, 217 + i * 2, y + 2, 217 + i * 2, y + 11, tint);
                }
            } else if (row == 2) {
                g.renderOutline(216, y + 3, 7, 3, tint);
                g.renderOutline(216, y + 8, 7, 3, tint);
            } else {
                line(g, 216, y + 3, 220, y + 6, tint); line(g, 220, y + 6, 216, y + 10, tint);
                line(g, 220, y + 3, 224, y + 6, tint); line(g, 224, y + 6, 220, y + 10, tint);
            }
        }
    }

    /** Four arms are a schematic; only the aggregate anchor count is supplied by the core. */
    private void drawHologram(GuiGraphics g) {
        int grid = color("holo_grid"), wire = color(menu.formed ? "holo_line" : "muted");
        for (int i = -7; i <= 7; i++) {
            isoLine(g, -7, 0, i, 7, 0, i, grid);
            isoLine(g, i, 0, -7, i, 0, 7, grid);
        }
        int anchor = color(menu.anchorCount > 0 ? "holo_line" : "hair");
        isoBox(g, -1.5, 0, -6, 3, 3, 4.5, anchor);
        isoBox(g, -6, 0, -1.5, 4.5, 3, 3, anchor);
        isoBox(g, -1.5, 0, -1.5, 3, 3, 3, wire);
        isoBox(g, 1.5, 0, -1.5, 4.5, 3, 3, anchor);
        isoBox(g, -1.5, 0, 1.5, 3, 3, 4.5, anchor);
        for (int i = 0; i < 3; i++) {
            double inset = .3 + i * .3;
            double a = -1.5 + inset, b = 1.5 - inset;
            isoLine(g, a, inset, 1.51, b, inset, 1.51, color("accent"));
            isoLine(g, b, inset, 1.51, b, 3 - inset, 1.51, color("accent"));
            isoLine(g, b, 3 - inset, 1.51, a, 3 - inset, 1.51, color("accent"));
            isoLine(g, a, 3 - inset, 1.51, a, inset, 1.51, color("accent"));
        }
        if (menu.formed && menu.networkOnline) {
            double scan = (Util.getMillis() % 3000) / 1000.0;
            isoLine(g, -1.5, scan, -1.5, 1.5, scan, -1.5, color("accent"));
            isoLine(g, 1.5, scan, -1.5, 1.5, scan, 1.5, color("accent"));
            isoLine(g, 1.5, scan, 1.5, -1.5, scan, 1.5, color("accent"));
        }
    }

    private void isoBox(GuiGraphics g, double x, double y, double z, double w, double h, double d, int tint) {
        for (int side = 0; side < 2; side++) {
            double yy = y + h * side;
            isoLine(g, x, yy, z, x + w, yy, z, tint);
            isoLine(g, x + w, yy, z, x + w, yy, z + d, tint);
            isoLine(g, x + w, yy, z + d, x, yy, z + d, tint);
            isoLine(g, x, yy, z + d, x, yy, z, tint);
        }
        for (int a = 0; a < 2; a++) for (int b = 0; b < 2; b++)
            isoLine(g, x + w * a, y, z + d * b, x + w * a, y + h, z + d * b, tint);
        for (int i = 1; i < w; i++) isoLine(g, x + i, y + h, z, x + i, y + h, z + d, tint);
        for (int i = 1; i < d; i++) isoLine(g, x, y + h, z + i, x + w, y + h, z + i, tint);
    }

    private static void isoLine(GuiGraphics g, double x, double y, double z,
            double xx, double yy, double zz, int tint) {
        line(g, (int) Math.round(103 + (x - z) * 6), (int) Math.round(120 + (x + z) * 2.3 - y * 8),
                (int) Math.round(103 + (xx - zz) * 6), (int) Math.round(120 + (xx + zz) * 2.3 - yy * 8), tint);
    }

    private static void line(GuiGraphics g, int x, int y, int xx, int yy, int tint) {
        int dx = Math.abs(xx - x), dy = -Math.abs(yy - y), sx = x < xx ? 1 : -1, sy = y < yy ? 1 : -1;
        int error = dx + dy;
        while (true) {
            fill(g, x, y, 1, 1, tint);
            if (x == xx && y == yy) return;
            int twice = error * 2;
            if (twice >= dy) { error += dy; x += sx; }
            if (twice <= dx) { error += dx; y += sy; }
        }
    }

    private void label(GuiGraphics g, Component value, float x, float baseline, float width, String tint) {
        text(g, value, x, baseline, width, "osq_nsc_700_6_6", 6.6f, tint, "left");
    }
    private void small(GuiGraphics g, Component value, float x, float baseline, float width, String tint) {
        small(g, value, x, baseline, width, tint, "left");
    }
    private void small(GuiGraphics g, Component value, float x, float baseline, float width, String tint, String align) {
        text(g, value, x, baseline, width, "osq_nsc_600_5_4", 5.4f, tint, align);
    }
    private void text(GuiGraphics g, Component value, float x, float baseline, float width,
            String face, float size, String tint, String align) {
        var entry = new OsqLayout.Entry();
        entry.x = x; entry.baseline = baseline; entry.w = width; entry.size = size;
        // Local provider chains add the missing 熵 glyph at the same weight, size and baseline.
        entry.fontId = "molecularmanipulator:" + face.replace("osq_nsc_", "osq_entropy_");
        entry.color = String.format(Locale.ROOT, "#%08X", color(tint));
        entry.alignment = align;
        ui.layout.renderSingleLine(g, font, entry, value, 0, 0);
    }

    private final class EngineButton extends Button {
        private final String id;
        private final Supplier<Component> label;
        EngineButton(String id, Supplier<Component> label, Runnable action) {
            this(id, ui.layout.findControl(id), label, action);
        }
        private EngineButton(String id, OsqLayout.Entry box, Supplier<Component> label, Runnable action) {
            super(leftPos + Math.round(box.x), topPos + Math.round(box.y), Math.round(box.w), Math.round(box.h),
                    label.get(), button -> action.run(), DEFAULT_NARRATION);
            this.id = id;
            this.label = label;
        }
        @Override
        protected void renderWidget(GuiGraphics g, int mx, int my, float partialTick) {
            setMessage(label.get());
            boolean tab = id.equals("overview") || id.equals("recipes");
            boolean selectedTab = tab && (id.equals("recipes") == recipesPage);
            boolean node = id.startsWith("node.");
            boolean primary = id.equals("unlock") || id.equals("overclock");
            boolean checked = node && Integer.parseInt(id.substring(5)) == selectedNode;
            String face = !active ? "disabled" : selectedTab ? "ink" : primary ? "accent_fill"
                    : checked || isHoveredOrFocused() ? "soft" : "card";
            if (node) {
                var recipe = NegativeEntropyEngineMenu.NODES.get(Integer.parseInt(id.substring(5)));
                face = recipe.unlocked(menu.unlockMask) || menu.canUnlock(recipe) ? "soft" : "paper";
            }
            fill(g, getX(), getY(), getWidth(), getHeight(), color(face));
            if (node || isHoveredOrFocused()) g.renderOutline(getX(), getY(), getWidth(), getHeight(),
                    color(checked || isHoveredOrFocused() ? "accent" : "hair"));
            if (checked) g.renderOutline(getX() + 1, getY() + 1, getWidth() - 2, getHeight() - 2, color("accent"));
            g.pose().pushPose();
            g.pose().translate(getX(), getY(), 0);
            if (node) {
                var recipe = NegativeEntropyEngineMenu.NODES.get(Integer.parseInt(id.substring(5)));
                small(g, getMessage(), 4, 10, getWidth() - (recipe.unlocked(menu.unlockMask) ? 16 : 8), "ink2");
                text(g, tr("node_threshold", number(recipe.entropy())), 4, 23, getWidth() - 8,
                        "osq_nsc_500_5", 5, recipe.unlocked(menu.unlockMask) || menu.canUnlock(recipe) ? "accent" : "muted", "left");
                if (recipe.unlocked(menu.unlockMask)) small(g, Component.literal("✓"), getWidth() - 9, 10, 6, "accent");
            } else {
                var entry = new OsqLayout.Entry();
                entry.x = 4; entry.w = getWidth() - (id.equals("overclock") ? 34 : 8);
                entry.centerY = getHeight() / 2f; entry.size = 6;
                entry.fontId = "molecularmanipulator:osq_entropy_600_6";
                entry.alignment = "center";
                entry.color = String.format(Locale.ROOT, "#%08X", color(!active ? "muted"
                        : selectedTab ? "inverse_tab" : primary ? "inverse" : "ink2"));
                ui.layout.renderSingleLine(g, font, entry, getMessage(), 0, 0);
                if (id.equals("overclock")) {
                    fill(g, getWidth() - 25, 5, 17, 9, color(menu.overclocked ? "level" : "hair"));
                    fill(g, getWidth() - (menu.overclocked ? 14 : 23), 6, 6, 7, color("card"));
                }
            }
            g.pose().popPose();
        }
    }
}
