package com.atir.molecularmanipulator.client;

import appeng.api.client.AEKeyRendering;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.GenericStack;
import appeng.client.gui.style.ScreenStyle;
import com.atir.molecularmanipulator.client.gui.theme.OsqFonts;
import com.atir.molecularmanipulator.client.gui.theme.OsqLayout;
import com.atir.molecularmanipulator.starcomputation.StarOrreryLayout;
import com.atir.molecularmanipulator.starcomputation.StarComputationRecipeCatalog.Entry;
import com.atir.molecularmanipulator.starcomputation.StarComputationBenefits;
import com.atir.molecularmanipulator.menu.StarComputationEngineMenu;
import com.atir.molecularmanipulator.menu.StarComputationEngineMenu.ActionResult;
import com.atir.molecularmanipulator.menu.StarComputationEngineMenu.RecipeNode;
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
public final class StarComputationEngineScreen extends ResponsiveContainerScreen<StarComputationEngineMenu> {
    private static final String PREFIX = "gui.molecularmanipulator.star_computation.";
    private static final NumberFormat INTEGER = NumberFormat.getIntegerInstance(Locale.ROOT);
    private static final int RECIPE_X = 213, RECIPE_Y = 170, RECIPE_STEP = 21, RECIPE_COLUMNS = 8;
    private static final int BENEFIT_TOP = 138, BENEFIT_PITCH = 22;
    private static final ResourceLocation CONTENTS_FONT = OsqFonts.id("osq_entropy_600_7_5");
    private OsqUi ui;
    private boolean recipesPage;
    private int selectedNode;
    private Button overclock, unlock, refresh, prerequisites;
    private final List<Button> nodeButtons = new ArrayList<>();
    private int pendingVersion = -1;
    private int seenVersion;
    private long pendingSince;
    private long resultUntil;

    public StarComputationEngineScreen(StarComputationEngineMenu menu, Inventory inventory, Component title,
            ScreenStyle style) {
        super(menu, inventory, title, style);
    }

    @Override
    protected void init() {
        ui = new OsqUi("star_computation_engine");
        ui.visible = entry -> entry.id == null || !entry.id.startsWith(recipesPage ? "ov_" : "rc_");
        ui.hide(entry -> "ctl".equals(entry.kind) || "edge".equals(entry.kind));
        imageWidth = ui.layout.width;
        imageHeight = ui.layout.height;
        super.init();
        ui.inventory(menu);
        seenVersion = menu.resultVersion;
        nodeButtons.clear();
        addScreenWidget(ui.tab("overview",leftPos,topPos,()->tr("overview"),()->!recipesPage,()->setPage(false)));
        addScreenWidget(ui.tab("recipes",leftPos,topPos,()->tr("recipes"),()->recipesPage,()->setPage(true)));
        overclock = addScreenWidget(new EngineButton("overclock", this::overclockLabel,
                () -> request(menu::requestToggleOverclock)));
        overclock.setTooltip(Tooltip.create(tr("overclock_help")));
        unlock = addScreenWidget(new EngineButton("unlock", this::unlockLabel,
                () -> request(() -> menu.requestUnlock(selectedNode))));
        refresh = addScreenWidget(new EngineButton("refresh", () -> tr("refresh"),
                () -> request(menu::requestRefresh)));
        prerequisites = addScreenWidget(new EngineButton("prerequisites", () -> tr("view_prerequisites"),
                this::selectPrerequisite));
        addScreenWidget(new EngineButton("guide", () -> tr("guide"), () -> OsqGuide.open("star_computation_engine")));
        for (var node : StarComputationEngineMenu.NODES) {
            var button = new EngineButton("node." + node.index(), () -> nodeName(node), () -> {
                selectedNode = node.index();
                updateControls();
            });
            button.setTooltip(Tooltip.create(tr("node_tooltip", nodeName(node), number(menu.nodeThreshold(node)),
                    number(node.informationCost()))));
            nodeButtons.add(addScreenWidget(button));
        }
        updateControls();
    }

    private void setPage(boolean recipes) {
        recipesPage = recipes;
        updateControls();
    }

    private RecipeNode selected() { return StarComputationEngineMenu.NODES.get(selectedNode); }
    private static Component tr(String key, Object... args) { return Component.translatable(PREFIX + key, args); }
    private static Component nodeName(RecipeNode node) { return Component.translatable(node.translationKey()); }
    private static Component nodeLabel(RecipeNode node) {
        return tr("node_short." + node.translationKey().substring(node.translationKey().lastIndexOf('.') + 1));
    }
    private static String number(long value) { return INTEGER.format(value); }
    private static final String[] TIERS = {"bronze", "violet", "gold"};
    private static final String[] ANCHOR_TIERS = {"reference", "quantum", "terminal"};

    /** L0-L3 bronze, L4-L6 violet, L7-L9 gold; read every frame from the synchronized level. */
    private String tier() { return TIERS[menu.benefitLevel <= 3 ? 0 : menu.benefitLevel <= 6 ? 1 : 2]; }

    private int color(String name) {
        // A tiered key overrides its neutral base; optional themes may only ship the base keys.
        var entry = ui.layout.find("color." + name + "." + tier());
        if (entry == null) entry = ui.layout.find("color." + name);
        if (entry != null) return OsqLayout.argb(entry.color);
        return switch (name) {
            case "ring_full" -> color("accent");
            case "ring_locked" -> color("hair");
            default -> throw new IllegalArgumentException("Missing OSQ colour color." + name);
        };
    }

    /** Real star orreries use verified rings; ordinary engines use the supplied entropy-level diagram. */
    private int litRings() {
        return Math.clamp(menu.starOrrery ? menu.litRings : menu.benefitLevel, 0, StarOrreryLayout.RINGS);
    }

    /** The reference skin's partial ring is entropy progress, not an invented physical anchor count. */
    private int currentRingFill() {
        int level = litRings();
        if (menu.starOrrery || level >= StarOrreryLayout.RINGS) return 0;
        long from = menu.levelThreshold(level), to = menu.levelThreshold(level + 1);
        long done = Math.clamp(menu.effectiveEntropy - from, 0, to - from);
        return (int) (done * StarOrreryLayout.slotCount(level) / Math.max(1, to - from));
    }

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
        for (int i = 0; i < nodeButtons.size(); i++) {
            var node = StarComputationEngineMenu.NODES.get(i);
            var button = nodeButtons.get(i);
            button.visible = recipesPage;
            button.setTooltip(Tooltip.create(tr("node_tooltip", nodeName(node), number(menu.nodeThreshold(node)),
                    number(node.informationCost()))));
        }
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
        for (var node : StarComputationEngineMenu.NODES) {
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
        if (menu.effectiveEntropy < menu.nodeThreshold(node)) return tr("result.insufficient_entropy");
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
            fill(g, 8, 212, 190, 30, color("soft"));
            treeConnections(g);
        } else {
            fill(g, 8, 197, 79, 45, color("ink2"));
            fill(g, 8, 241, 79, 1, color("hair"));
            drawRings(g);
            drawLevel(g);
            drawBenefitIcons(g);
        }
        g.pose().popPose();
    }

    private static void fill(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + h, color);
    }

    @Override
    public void drawFG(GuiGraphics g, int x, int y, int mx, int my) {
        if (ui == null) return;
        ui.foreground(g, font);
        text(g, Component.translatable("block.molecularmanipulator.star_computation_engine_core"),
                12, 23, 165, "osq_nsc_900_13", 13, "ink", "left");
        small(g, tr(!menu.formed ? "incomplete" : !menu.networkOnline ? "offline"
                : menu.overclocked ? "overclock_running" : "stable"), 185, 18, 86, "accent");
        if (recipesPage) drawRecipes(g); else drawOverview(g);
        boolean feedback = waiting() || Util.getMillis() < resultUntil && menu.actionResult != ActionResult.NONE;
        if (!feedback) {
            centeredText(g, tr(menu.networkOnline ? "network_online" : "network_offline"), 213, 258, 82,
                    "osq_nsc_600_7_5", 7.5f, menu.networkOnline ? "ink2" : "muted", "left");
            if (!recipesPage) centeredText(g, tr("consumption", number(menu.infoConsumption)), 299, 258, 84,
                    "osq_nsc_600_7_5", 7.5f, "muted", "right");
        }
        centeredText(g, tr("balance", number(menu.infoBalance)), 213, 271, 170,
                "osq_nsc_600_7_5", 7.5f, "ink2", "left");
        if (waiting()) centeredText(g, tr("waiting"), 213, 258, 170,
                "osq_nsc_600_7_5", 7.5f, "muted", "left");
        else if (feedback) {
            Component result = tr("result." + menu.actionResult.name().toLowerCase(Locale.ROOT));
            if (menu.resultNode >= 0 && menu.resultNode < StarComputationEngineMenu.NODES.size()) {
                result = tr("node_result", nodeLabel(StarComputationEngineMenu.NODES.get(menu.resultNode)), result);
            }
            boolean success = switch (menu.actionResult) {
                case UNLOCKED, ALREADY_UNLOCKED, OVERCLOCK_ENABLED, OVERCLOCK_DISABLED, STRUCTURE_REFRESHED -> true;
                default -> false;
            };
            centeredText(g, result, 213, 258, 170, "osq_nsc_600_7_5", 7.5f,
                    success ? "accent" : "warning", "left");
        }
    }

    private void drawOverview(GuiGraphics g) {
        label(g, tr("star_orrery"), 17, 45, 110, "ink2");
        small(g, menu.formed ? tr("tier." + tier()) : tr("incomplete"),
                140, 45, 54, menu.formed ? "accent" : "warning", "right");
        small(g, tr("star_orrery_hint", litRings(), number(menu.anchorCount)), 17, 56, 172, "muted");
        drawRingList(g);
        centeredText(g, tr("effective_anchors"), 17, 208, 63, "osq_nsc_600_7_5", 7.5f, "inverse", "left");
        centeredText(g, Component.literal(number(menu.anchorCount)), 15, 227, 49,
                "osq_intertight_700_26", 26, "inverse", "left");
        centeredText(g, tr("blocks"), 68, 227, 15, "osq_nsc_600_7_5", 7.5f, "inverse", "left");
        int[] anchors = {menu.anchorsReference, menu.anchorsQuantum, menu.anchorsTerminal};
        for (int t = 0; t < 3; t++) {
            centeredText(g, tr("anchor_" + ANCHOR_TIERS[t]), 96, 208 + t * 12, 40,
                    "osq_nsc_600_7_5", 7.5f, "ink2", "left");
            centeredText(g, Component.literal(number(anchors[t])), 140, 208 + t * 12, 51,
                    "osq_intertight_700_9", 9, "accent", "right");
        }

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
        small(g, Component.literal(number(menu.effectiveEntropy) + " / " + number(menu.levelThreshold(next))),
                286, 103, 97, "feature_ink", "right");

        label(g, tr("network_benefits"), 213, 132, 106, "ink");
        small(g, tr(benefit.infinite() ? "infinite_overclock" : benefit.overclocked() ? "overclock_double" : "base_benefits"),
                318, 132, 65, "accent", "right");
        var base = new StarComputationBenefits(benefit.level(), false);
        benefitRow(g, 0, "assembler_parallel", amount(benefit.assemblerParallel()),
                tr("assembler_detail", tr(benefit.virtualCpu() ? "virtual_parallel" : "physical_parallel"),
                        OsqUi.compact(base.assemblerParallel())));
        benefitRow(g, 1, "cpu_parallel", amount(benefit.cpuParallel()),
                tr("cpu_detail", tr(benefit.virtualCpu() ? "virtual_parallel" : "physical_parallel"),
                        OsqUi.compact(base.cpuParallel())));
        benefitRow(g, 2, "cpu_storage", storage(benefit.storageBytes()), tr("storage_detail", storage(base.storageBytes())));
        benefitRow(g, 3, "fabrication_speed", benefit.infinite() ? tr("one_tick")
                : Component.literal("×" + benefit.speedMultiplier()), tr("duration_detail", processingTime(benefit)));
        centeredText(g, tr("research_parallel_only"), 232, 234, 151,
                "osq_nsc_600_7_5", 7.5f, "muted", "left");
    }

    /** Same nine-row skin for both engine types, without inferring physical ring progress. */
    private void drawRingList(GuiGraphics g) {
        int lit = litRings();
        for (int ring = 0; ring < StarOrreryLayout.RINGS; ring++) {
            int y = 70 + (StarOrreryLayout.RINGS - 1 - ring) * 12;
            int slots = StarOrreryLayout.slotCount(ring);
            boolean full = ring < lit, current = !menu.starOrrery && ring == lit;
            small(g, tr("ring", ring + 1), 132, y, 22, full || current ? "ink2" : "muted");
            small(g, full ? Component.literal(slots + "/" + slots)
                    : current ? Component.literal(currentRingFill() + "/" + slots) : tr("ring_locked"),
                    150, y, 41, full ? "ink" : current ? "accent" : "muted", "right");
        }
    }

    private void benefitRow(GuiGraphics g, int row, String title, Component value, Component detail) {
        float center = BENEFIT_TOP + row * BENEFIT_PITCH + 6.5f;
        centeredText(g, tr(title), 232, center, 77, "osq_nsc_600_7_5", 7.5f, "ink2", "left");
        centeredText(g, value, 313, center, 70, "osq_intertight_700_9", 9, "accent", "right");
        centeredText(g, detail, 232, center + 11.5f, 151, "osq_nsc_500_7_5", 7.5f, "muted", "left");
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

    private static Component processingTime(StarComputationBenefits benefit) {
        int ticks = benefit.processingTicks(StarComputationEngineMenu.DEFAULT_PROCESSING_TICKS);
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
                / Math.max(1, menu.levelThreshold(next)), 0, 1);
        fill(g, 213, 107, (int) Math.round(170 * progress), 2, color("level"));
    }

    private void drawRecipes(GuiGraphics g) {
        label(g, tr("recipes"), 17, 45, 90, "ink");
        small(g, tr("permanent_unlock"), 137, 45, 52, "muted", "right");
        small(g, tr("tree_hint"), 17, 56, 172, "muted");
        small(g, tr("unlocked"), 17, 174, 47, "accent");
        small(g, tr("available"), 75, 174, 47, "accent");
        small(g, tr("locked"), 135, 174, 54, "muted");
        small(g, tr("entropy_total", number(menu.effectiveEntropy)), 17, 227, 102, "accent");
        small(g, tr("unlock_count", Integer.bitCount(menu.unlockMask & StarComputationEngineMenu.ALL_UNLOCKS),
                StarComputationEngineMenu.NODES.size()), 119, 227, 70, "ink2", "right");
        var node = selected();
        text(g, nodeName(node), 213, 48, 119, "osq_nsc_700_9", 9, "ink", "left");
        small(g, nodeState(node), 333, 46, 50, node.unlocked(menu.unlockMask) || menu.canUnlock(node) ? "accent" : "muted", "right");
        small(g, tr("unlock_hint"), 213, 60, 170, "muted");
        fill(g, 213, 66, 170, 1, color("hair"));
        small(g, tr("entropy_threshold"), 213, 79, 67, "ink2");
        small(g, Component.literal(number(menu.effectiveEntropy) + " / " + number(menu.nodeThreshold(node))), 280, 79, 103,
                menu.effectiveEntropy >= menu.nodeThreshold(node) ? "accent" : "warning", "right");
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
        for (var parent : StarComputationEngineMenu.NODES) if ((node.prerequisiteMask() & (1 << parent.index())) != 0) {
            if (!names.getString().isEmpty()) names.append(" · ");
            names.append(nodeLabel(parent));
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
        for (var node : StarComputationEngineMenu.NODES) {
            var target = ui.layout.findControl("node." + node.index());
            for (var parent : StarComputationEngineMenu.NODES) if ((node.prerequisiteMask() & (1 << parent.index())) != 0) {
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

    /** Benefit icons, 9x9 px with a 2 px margin inside the 13x13 backing: '#' = accent, '+' = holo_line, '.' = empty. Both keys follow the level band. */
    private static final String[][] BENEFIT_ICONS = {
            // Crafter parallel: three crafting units on one bus
            {".#######.", ".#+#+#+#.", ".#.#.#.#.", ".#+#+#+#.", ".#######.", "..#.#.#..", "..#.#.#..", "#########", "........."},
            // Calculation CPU parallel: chip with pins and parallel lanes
            {"..#.#.#..", ".#######.", "##.....##", ".#+++++#.", "##.....##", ".#+++++#.", "##.....##", ".#######.", "..#.#.#.."},
            // CPU crafting storage: three stacked storage bays with data dots
            {".........", "#########", "#+.+....#", "#########", "#+.+....#", "#########", "#+.+....#", "#########", ".#.....#."},
            // Construction well speed: fast-forward chevrons with motion trail
            {".........", "+...#....", "++..##...", "+++.###..", "++++####.", "+++.###..", "++..##...", "+...#....", "........."},
    };

    private void drawBenefitIcons(GuiGraphics g) {
        for (int row = 0; row < BENEFIT_ICONS.length; row++) {
            int y = BENEFIT_TOP + row * BENEFIT_PITCH, main = color("accent"), detail = color("holo_line");
            fill(g, 213, y, 13, 13, color("soft"));
            String[] icon = BENEFIT_ICONS[row];
            int left=9, top=9, right=0, bottom=0;
            for(int iy=0;iy<icon.length;iy++)for(int ix=0;ix<icon[iy].length();ix++)if(icon[iy].charAt(ix)!='.'){
                left=Math.min(left,ix);top=Math.min(top,iy);right=Math.max(right,ix+1);bottom=Math.max(bottom,iy+1);
            }
            g.pose().pushPose();
            g.pose().translate(219.5f-(left+right)/2f,y+6.5f-(top+bottom)/2f,0);
            for (int dy = 0; dy < icon.length; dy++) {
                for (int dx = 0; dx < icon[dy].length(); dx++) {
                    char c = icon[dy].charAt(dx);
                    if (c != '.') fill(g, dx, dy, 1, 1, c == '#' ? main : detail);
                }
            }
            g.pose().popPose();
        }
    }

    /** Supplied nine-ring artwork: completed rings, a pale current track, then locked dots. */
    private void drawRings(GuiGraphics g) {
        int lit = litRings();
        for (int ring = 0; ring < StarOrreryLayout.RINGS; ring++) {
            int radius = 10 + 5 * ring, slots = StarOrreryLayout.slotCount(ring);
            int steps = (int) (2 * Math.PI * radius);
            if (ring < lit) {
                for (int step = 0; step < steps; step++) ringDot(g, step, steps, radius, color("ring_full"));
            } else if (ring == lit) {
                for (int step = 0; step < steps; step++) ringDot(g, step, steps, radius, color("hair"));
                for (int slot = 0; slot < currentRingFill(); slot++) ringDot(g, slot, slots, radius, color("accent"));
            } else {
                for (int slot = 0; slot < slots; slot += 2) ringDot(g, slot, slots, radius, color("ring_locked"));
            }
        }
        fill(g, 66, 106, 3, 3, color("accent_fill"));
    }

    private static void ringDot(GuiGraphics g, int index, int count, int radius, int tint) {
        double angle = -Math.PI / 2 + 2 * Math.PI * index / count;
        fill(g, 67 + (int) Math.round(radius * Math.cos(angle)),
                107 + (int) Math.round(radius * Math.sin(angle)), 1, 1, tint);
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
        text(g, value, x, baseline, width, "osq_nsc_700_7_5", 7.5f, tint, "left");
    }
    private void small(GuiGraphics g, Component value, float x, float baseline, float width, String tint) {
        small(g, value, x, baseline, width, tint, "left");
    }
    private void small(GuiGraphics g, Component value, float x, float baseline, float width, String tint, String align) {
        text(g, value, x, baseline, width, "osq_nsc_600_7_5", 7.5f, tint, align);
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

    private void centeredText(GuiGraphics g, Component value, float x, float centerY, float width,
            String face, float size, String tint, String align) {
        var entry = new OsqLayout.Entry();
        entry.x=x;entry.centerY=centerY;entry.w=width;entry.size=size;
        entry.fontId="molecularmanipulator:"+face.replace("osq_nsc_","osq_entropy_");
        entry.color=String.format(Locale.ROOT,"#%08X",color(tint));entry.alignment=align;
        ui.layout.renderSingleLine(g,font,entry,value,0,0);
    }

    private final class EngineButton extends Button {
        private final String id;
        private final Supplier<Component> label;
        private final OsqLayout.Entry background = new OsqLayout.Entry();
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
            boolean node = id.startsWith("node.");
            boolean checked = node && Integer.parseInt(id.substring(5)) == selectedNode;
            String status = !active ? "disabled" : isHoveredOrFocused() ? "hover" : "normal";
            if (node) {
                var recipe = StarComputationEngineMenu.NODES.get(Integer.parseInt(id.substring(5)));
                String face = recipe.unlocked(menu.unlockMask) || menu.canUnlock(recipe) ? "soft" : "paper";
                fill(g, getX(), getY(), getWidth(), getHeight(), color(face));
                g.renderOutline(getX(), getY(), getWidth(), getHeight(), color(checked ? "ink" : "hair"));
            } else {
                background.sprite = ui.sprite("button_" + status);
                background.x = getX(); background.y = getY(); background.w = getWidth(); background.h = getHeight();
                OsqLayout.drawSprite(g, background, 0, 0);
            }
            if (checked) g.renderOutline(getX() + 1, getY() + 1, getWidth() - 2, getHeight() - 2, color("accent"));
            g.pose().pushPose();
            g.pose().translate(getX(), getY(), 0);
            if (node) {
                var recipe = StarComputationEngineMenu.NODES.get(Integer.parseInt(id.substring(5)));
                small(g, nodeLabel(recipe), 4, 10,
                        getWidth() - (recipe.unlocked(menu.unlockMask) && !ui.layout.english ? 16 : 8), "ink2");
                // The node tooltip gives the unit and exact threshold; the compact row contains the value in full.
                text(g, Component.literal(OsqUi.compact(menu.nodeThreshold(recipe))), 4, 23, getWidth() - 8,
                        "osq_nsc_500_7_5", 7.5f, recipe.unlocked(menu.unlockMask) || menu.canUnlock(recipe) ? "accent" : "muted", "left");
                if (recipe.unlocked(menu.unlockMask)) small(g, Component.literal("✓"), getWidth() - 9,
                        ui.layout.english ? 23 : 10, 6, "accent");
            } else {
                var entry = new OsqLayout.Entry();
                entry.x = 4; entry.w = getWidth() - (id.equals("overclock") ? 34 : 8);
                entry.centerY = OsqUi.controlCenter("button", status, getHeight(), false); entry.size = 7.5f;
                entry.fontId = "molecularmanipulator:osq_entropy_600_7_5";
                if(id.equals("overclock")){
                    entry.w=Math.min(getWidth()-41,OsqFonts.widthF(font,getMessage().getString(),OsqFonts.id(entry.fontId)));
                    entry.x=(getWidth()-entry.w-25)/2;
                }
                entry.alignment = "center";
                entry.color = String.format(Locale.ROOT, "#%08X", ui.palette.controlText("button", status, active));
                ui.layout.renderSingleLine(g, font, entry, getMessage(), 0, 0);
                if (id.equals("overclock")) {
                    int toggleY = Math.round(entry.centerY - 4.5f);
                    int toggleX = Math.round(entry.x+entry.w+8);
                    fill(g, toggleX, toggleY, 17, 9, color(menu.overclocked ? "level" : "hair"));
                    fill(g, toggleX+(menu.overclocked?11:2), toggleY + 1, 6, 7, color("card"));
                }
            }
            g.pose().popPose();
        }
    }
}
