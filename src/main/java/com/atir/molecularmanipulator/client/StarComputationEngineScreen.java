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
import com.atir.molecularmanipulator.starcomputation.StarComputationUpgradeCatalog;
import com.atir.molecularmanipulator.starcomputation.StarComputationUpgradeCatalog.Row;
import com.atir.molecularmanipulator.menu.StarComputationEngineMenu;
import com.atir.molecularmanipulator.menu.StarComputationEngineMenu.ActionResult;
import com.atir.molecularmanipulator.starcomputation.StarResearchMenuNodes.Node;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

/** OSQ geometry and typography over synchronized AE2 state, with no simulated payments. */
public final class StarComputationEngineScreen extends ResponsiveContainerScreen<StarComputationEngineMenu> {
    private static final String PREFIX = "gui.molecularmanipulator.star_computation.";
    private static final NumberFormat INTEGER = NumberFormat.getIntegerInstance(Locale.ROOT);
    private static final int RECIPE_X = 213, RECIPE_Y = 170, RECIPE_STEP = 21, RECIPE_COLUMNS = 8;
    private static final int MAX_ANCHORS = java.util.stream.IntStream.range(0, StarOrreryLayout.RINGS)
            .map(StarOrreryLayout::slotCount).sum();
    private static final int RECIPE_COMPACT = 27;
    private static final int RESEARCH_ROWS = 5;
    private static final int BENEFIT_TOP = 135, BENEFIT_PITCH = 25;
    private static final int RING_CENTER_Y = 127;
    private static final ResourceLocation CONTENTS_FONT = Style.DEFAULT_FONT;
    private OsqUi ui;
    // Local recipe-page navigation, mutually exclusive with the upgrade page.
    private boolean recipesPage;
    private boolean upgradePage;
    private int selectedNode;
    private ResourceLocation selectedResearchId;
    private int researchScroll;
    private ResearchScrollbar researchScrollbar;
    private int selectedUpgradeLevel;
    private double upgradeScroll;
    private Button overclock, unlock, refresh, prerequisites, reclaim, guide, startUpgrade, pauseUpgrade;
    private UpgradeRequirements upgradeRequirements;
    private final List<Button> nodeButtons = new ArrayList<>();
    private final List<Button> levelButtons = new ArrayList<>();
    private int pendingVersion = -1;
    private int seenVersion;
    private long pendingSince;
    private long resultUntil;

    public StarComputationEngineScreen(StarComputationEngineMenu menu, Inventory inventory, Component title,
            ScreenStyle style) {
        super(menu, inventory, title, style);
        seenVersion = menu.resultVersion;
    }

    @Override
    protected void init() {
        ui = new OsqUi("star_computation_engine");
        ui.visible = this::pageVisible;
        ui.hide(entry -> "ctl".equals(entry.kind) || "edge".equals(entry.kind));
        configureRecipePage();
        imageWidth = ui.layout.width;
        imageHeight = ui.layout.height - (recipesPage ? RECIPE_COMPACT : 0);
        super.init();
        ui.inventory(menu);
        nodeButtons.clear();
        levelButtons.clear();
        addScreenWidget(ui.tab("overview",leftPos,topPos,()->tr("overview"),()->!recipesPage&&!upgradePage,()->setPage(false)));
        addScreenWidget(ui.tab("upgrade",leftPos,topPos,()->up("tab"),()->upgradePage,this::setUpgradePage));
        addScreenWidget(ui.tab("recipes",leftPos,topPos,()->tr("recipes"),()->recipesPage,()->setPage(true)));
        initControls();
        updateControls();
    }

    private void initControls() {
        overclock = addScreenWidget(new EngineButton("overclock", this::overclockLabel,
                () -> request(menu::requestToggleOverclock)));
        overclock.setTooltip(Tooltip.create(tr("overclock_help")));
        unlock = addScreenWidget(new EngineButton("unlock", this::unlockLabel,
                () -> request(() -> menu.requestUnlock(selected().id()))));
        refresh = addScreenWidget(new EngineButton("refresh", () -> tr("refresh"),
                () -> request(menu::requestRefresh)));
        reclaim = addScreenWidget(new EngineButton("reclaim", this::reclaimLabel,
                () -> request(menu::requestReclaim)));
        reclaim.setTooltip(Tooltip.create(tr("reclaim_help")));
        prerequisites = addScreenWidget(new EngineButton("prerequisites", () -> tr("view_prerequisites"),
                this::selectPrerequisite));
        guide = addScreenWidget(new EngineButton("guide", () -> tr("guide"), () -> OsqGuide.open("star_computation_engine")));
        startUpgrade = addScreenWidget(new EngineButton("upgrade_start", this::startUpgradeLabel,
                () -> { if (upgradeState().canStart()) request(() -> menu.requestStartUpgrade(selectedUpgradeLevel)); }));
        pauseUpgrade = addScreenWidget(new EngineButton("upgrade_pause", this::pauseUpgradeLabel,
                () -> { if (upgradeState().canPause()) request(menu::requestPauseUpgrade); }));
        upgradeRequirements = addScreenWidget(new UpgradeRequirements(ui.layout.findControl("up_requirements")));
        for (int level = 1; level <= 9; level++) {
            int target = level;
            var button = ui.button("up_level." + level, leftPos, topPos,
                    () -> Component.literal("L" + target), () -> selectUpgradeLevel(target));
            ui.state(button, () -> selectedUpgradeLevel == target ? "active" : "normal");
            levelButtons.add(addScreenWidget(button));
        }
        // Reuse the existing five visual rows as a viewport, not five hard-coded research definitions.
        for (int row = 0; row < RESEARCH_ROWS; row++) {
            int slot = row;
            var button = ui.button("node." + row, leftPos, topPos,
                    () -> researchScroll + slot < menu.nodes().size()
                            ? nodeName(menu.node(researchScroll + slot)) : Component.empty(),
                    () -> selectNode(researchScroll + slot));
            ui.state(button, () -> selected().index() == researchScroll + slot ? "active" : "normal");
            nodeButtons.add(addScreenWidget(button));
        }
        researchScrollbar = addScreenWidget(new ResearchScrollbar());
        ui.progress(24, 211, () -> (double) menu.effectiveEntropy / Math.max(1, menu.nodeThreshold(selected())));
        ui.progress(22, 232, () -> (double) menu.anchorCount / MAX_ANCHORS);
    }

    /** Close the former 33 px gap below unlock contents, moving the inventory and actions with the frame. */
    private void configureRecipePage() {
        if (!recipesPage) return;
        for (String id : List.of("window", "armillary")) {
            var surface = ui.layout.find(id);
            surface.h -= RECIPE_COMPACT;
        }
        // Shared frames resize directly, and reflection UVs use the moved layout coordinates.
        for (var entry : ui.layout.draw) if (entry.id != null && List.of("inv", "net", "unlock", "prerequisites", "guide").contains(entry.id)) {
            entry.y -= RECIPE_COMPACT;
        }
    }

    private boolean pageVisible(OsqLayout.Entry entry) {
        if (entry.id == null) return true;
        if (entry.id.startsWith("ov_")) return !recipesPage && !upgradePage;
        if (entry.id.startsWith("rc_")) return recipesPage;
        if (entry.id.startsWith("up_")) return upgradePage;
        return !entry.id.equals("net") || !upgradePage;
    }

    private void setPage(boolean recipes) {
        if (recipesPage == recipes && !upgradePage) return;
        recipesPage = recipes;
        upgradePage = false;
        rebuildWidgets();
    }

    // AE2 forwards wheel input only to its WidgetContainer, not native screen children.
    // Handle this list before that path; pointer scrolling must never depend on focus.
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        if (wirelessPanelOpen()) return super.mouseScrolled(mouseX, mouseY, scrollY);
        if (recipesPage && researchScrollbar != null && researchScrollbar.visible && scrollY != 0) {
            double x = logicalMouseX(mouseX), y = logicalMouseY(mouseY);
            var first = nodeButtons.get(0);
            if (x >= first.getX() && x < researchScrollbar.getX() + researchScrollbar.getWidth()
                    && y >= first.getY() && y < researchScrollbar.getY() + researchScrollbar.getHeight()) {
                scrollResearchTo(researchScroll + (scrollY > 0 ? -1 : 1));
                return true;
            }
        }
        if (upgradePage && upgradeRequirements != null && upgradeRequirements.mouseScrolled(
                logicalMouseX(mouseX), logicalMouseY(mouseY), scrollY)) return true;
        return super.mouseScrolled(mouseX, mouseY, scrollY);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (wirelessPanelOpen()) return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        // Container drag handling otherwise consumes native scrollbar drags as inventory input.
        if (recipesPage && researchScrollbar != null && button == 0 && isDragging()
                && researchScrollbar.draggingScrollbar) {
            return researchScrollbar.mouseDragged(logicalMouseX(mouseX), logicalMouseY(mouseY), button,
                    logicalMouseX(mouseX) - logicalMouseX(mouseX - dragX),
                    logicalMouseY(mouseY) - logicalMouseY(mouseY - dragY));
        }
        if (upgradePage && upgradeRequirements != null && button == 0 && isDragging()
                && getFocused() == upgradeRequirements) {
            return upgradeRequirements.mouseDragged(logicalMouseX(mouseX), logicalMouseY(mouseY), button,
                    logicalMouseX(mouseX) - logicalMouseX(mouseX - dragX),
                    logicalMouseY(mouseY) - logicalMouseY(mouseY - dragY));
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (researchScrollbar != null && button == 0 && researchScrollbar.draggingScrollbar) {
            setDragging(false);
            return researchScrollbar.mouseReleased(logicalMouseX(mouseX), logicalMouseY(mouseY), button);
        }
        if (upgradeRequirements != null && button == 0 && upgradeRequirements.draggingScrollbar) {
            setDragging(false);
            return upgradeRequirements.mouseReleased(logicalMouseX(mouseX), logicalMouseY(mouseY), button);
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    /** Local navigation only. Neither switching pages nor closing the screen changes calibration. */
    private void setUpgradePage() {
        if (upgradePage) return;
        recipesPage = false;
        upgradePage = true;
        if (selectedUpgradeLevel == 0) selectedUpgradeLevel = menu.upgradeTarget >= 4 ? menu.upgradeTarget
                : litRings() < 3 ? litRings() + 1 : Math.min(9, menu.certifiedLevel + 1);
        rebuildWidgets();
    }

    private void selectUpgradeLevel(int level) {
        if (selectedUpgradeLevel == level) return;
        selectedUpgradeLevel = com.atir.molecularmanipulator.util.MathCompat.clamp(level, 1, 9);
        upgradeScroll = 0;
        if (upgradeRequirements != null) upgradeRequirements.viewport.scroll = 0;
        updateControls();
    }

    private void selectNode(int index) {
        if (index < 0 || index >= menu.nodes().size()) return;
        selectedNode = index;
        selectedResearchId = menu.node(index).id();
        if (index < researchScroll) researchScroll = index;
        if (index >= researchScroll + RESEARCH_ROWS) researchScroll = index - RESEARCH_ROWS + 1;
        updateControls();
    }

    /** Follow the selected ID through reloads; removed nodes select the sole built-in rather than another old slot. */
    private Node selected() {
        if (selectedResearchId != null) {
            int current = menu.indexOf(selectedResearchId);
            selectedNode = current < 0 ? 0 : current;
        }
        selectedNode = com.atir.molecularmanipulator.util.MathCompat.clamp(selectedNode, 0, menu.nodes().size() - 1);
        var node = menu.node(selectedNode);
        selectedResearchId = node.id();
        return node;
    }

    private void scrollResearchTo(int offset) {
        researchScroll = com.atir.molecularmanipulator.util.MathCompat.clamp(offset, 0, Math.max(0, menu.nodes().size() - RESEARCH_ROWS));
        updateControls();
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (wirelessPanelOpen()) return super.keyPressed(key, scan, modifiers);
        if (recipesPage && (getFocused() == researchScrollbar || nodeButtons.contains(getFocused()))) {
            int index = selected().index();
            switch (key) {
                case GLFW.GLFW_KEY_UP -> index--;
                case GLFW.GLFW_KEY_DOWN -> index++;
                case GLFW.GLFW_KEY_PAGE_UP -> index -= RESEARCH_ROWS;
                case GLFW.GLFW_KEY_PAGE_DOWN -> index += RESEARCH_ROWS;
                case GLFW.GLFW_KEY_HOME -> index = 0;
                case GLFW.GLFW_KEY_END -> index = menu.nodes().size() - 1;
                default -> { return super.keyPressed(key, scan, modifiers); }
            }
            selectNode(com.atir.molecularmanipulator.util.MathCompat.clamp(index, 0, menu.nodes().size() - 1));
            setFocused(nodeButtons.get(selectedNode - researchScroll));
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }
    private static Component tr(String key, Object... args) { return Component.translatable(PREFIX + key, args); }
    private static Component up(String key, Object... args) { return tr("upgrade." + key, args); }
    private static Component nodeName(Node node) { return researchTitle(node.id(), node.title(), false); }
    private static Component nodeLabel(Node node) { return researchTitle(node.id(), node.title(), true); }
    private static Component researchTitle(ResourceLocation id, String title, boolean compact) {
        // Keep the original short labels only for unchanged built-in titles, never for a data-pack override.
        return compact && id.getNamespace().equals("molecularmanipulator") && title.equals(PREFIX + "node." + id.getPath())
                ? tr("node_short." + id.getPath()) : Component.translatable(title);
    }
    private static String number(long value) { return INTEGER.format(value); }
    private static final String[] TIERS = {"bronze", "violet", "gold"};
    private static final String[] ANCHOR_TIERS = {"reference", "quantum", "terminal"};

    /** L0-L3 bronze, L4-L6 violet, L7-L9 gold; read every frame from the synchronized level. */
    private String tier() { return TIERS[menu.benefitLevel <= 3 ? 0 : menu.benefitLevel <= 6 ? 1 : 2]; }

    private int color(String name) {
        // A tiered key overrides its neutral base; optional themes may only ship the base keys.
        var entry = upgradePage && name.equals("accent") ? ui.layout.find("color.upgrade_accent." + tier()) : null;
        if (entry == null) entry = ui.layout.find("color." + name + "." + tier());
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
        return com.atir.molecularmanipulator.util.MathCompat.clamp(menu.starOrrery ? menu.litRings : menu.benefitLevel, 0, StarOrreryLayout.RINGS);
    }

    /** The reference skin's partial ring is entropy progress, not an invented physical anchor count. */
    private int currentRingFill() {
        int level = litRings();
        if (menu.starOrrery || level >= StarOrreryLayout.RINGS) return 0;
        long from = menu.levelThreshold(level), to = menu.levelThreshold(level + 1);
        long done = com.atir.molecularmanipulator.util.MathCompat.clamp(menu.effectiveEntropy - from, 0, to - from);
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
        selected();
        researchScroll = com.atir.molecularmanipulator.util.MathCompat.clamp(researchScroll, 0, Math.max(0, menu.nodes().size() - RESEARCH_ROWS));
        boolean overviewPage = !recipesPage && !upgradePage;
        overclock.visible = overviewPage;
        overclock.active = !waiting() && menu.canToggleOverclock();
        refresh.visible = overviewPage;
        refresh.active = !waiting() && menu.canConfigure;
        reclaim.visible = overviewPage;
        reclaim.active = !waiting() && menu.canConfigure;
        unlock.visible = recipesPage;
        unlock.active = !waiting() && menu.canUnlock(selected());
        prerequisites.visible = recipesPage;
        prerequisites.active = selected().prerequisites().stream().anyMatch(parent -> menu.indexOf(parent.id()) >= 0);
        prerequisites.setTooltip(Tooltip.create(nodeTooltip(selected())));
        guide.visible = !upgradePage;
        var state = upgradeState();
        startUpgrade.visible = upgradePage && !state.automatic() && !state.certified();
        startUpgrade.active = state.canStart();
        startUpgrade.setTooltip(Tooltip.create(upgradeActionTooltip()));
        pauseUpgrade.visible = upgradePage && state.serverTarget() >= 4;
        pauseUpgrade.active = state.canPause();
        pauseUpgrade.setTooltip(Tooltip.create(up("pause_help", state.serverTarget())));
        upgradeRequirements.visible = upgradePage;
        if (upgradePage) upgradeRequirements.syncRows();
        for (int i = 0; i < levelButtons.size(); i++) {
            int level = i + 1;
            var button = levelButtons.get(i);
            button.visible = upgradePage;
            button.setTooltip(Tooltip.create(up("level_tooltip", level, number(menu.levelThreshold(level)),
                    levelState(level)).copy().append("\n").append(up("preview_help"))));
        }
        for (int row = 0; row < nodeButtons.size(); row++) {
            int index = researchScroll + row;
            var button = nodeButtons.get(row);
            button.visible = recipesPage && index < menu.nodes().size();
            button.active = button.visible;
            if (button.visible) button.setTooltip(Tooltip.create(nodeTooltip(menu.node(index))));
        }
        researchScrollbar.visible = recipesPage && menu.nodes().size() > RESEARCH_ROWS;
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
        int first = -1;
        for (var parent : selected().prerequisites()) {
            int index = menu.indexOf(parent.id());
            if (index < 0) continue;
            if (first < 0) first = index;
            if (!parent.unlocked()) { selectNode(index); return; }
        }
        if (first >= 0) selectNode(first);
    }

    private Component overclockLabel() {
        if (waiting()) return tr("waiting");
        return tr(menu.overclocked ? "overclock_on" : "overclock_off");
    }

    private Component reclaimLabel() {
        if (waiting()) return tr("waiting");
        return tr(menu.reclaimArmed ? "reclaim_confirm" : "reclaim");
    }

    private Component unlockLabel() {
        if (waiting()) return tr("waiting");
        var node = selected();
        var status = menu.unlockStatus(node);
        if (status == ActionResult.ALREADY_UNLOCKED) return tr("unlocked_permanent");
        if (status != ActionResult.NONE) return tr("result." + status.name().toLowerCase(Locale.ROOT));
        return tr("pay_unlock", number(node.informationCost()));
    }

    private Component nodeState(Node node) {
        return tr(node.unlocked() ? "unlocked" : menu.canUnlock(node) ? "available" : "locked");
    }

    @Override
    public void drawBG(GuiGraphics g, int x, int y, int mx, int my, float pt) {
        if (ui == null) return;
        ui.background(g, font, x, y);
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        if (!recipesPage && !upgradePage) {
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
                12, 23, 202, 13, "ink", "left");
        // Status belongs in the existing lower-right status strip, not between the title and tabs.
        if (upgradePage) { drawUpgrade(g); return; }
        if (recipesPage) drawRecipes(g); else drawOverview(g);
        boolean feedback = waiting() || Util.getMillis() < resultUntil && menu.actionResult != ActionResult.NONE;
        int networkOffset = recipesPage ? RECIPE_COMPACT : 0;
        if (!feedback) {
            centeredText(g, tr(menu.networkOnline ? "network_online" : "network_offline"), 213, 258 - networkOffset, 82,
                    7.5f, menu.networkOnline ? "ink2" : "muted", "left");
            if (!recipesPage) centeredText(g, tr("consumption", number(menu.infoConsumption)), 299, 258, 84,
                    7.5f, "muted", "right");
        }
        centeredText(g, tr("balance", DisplayNumbers.compact(menu.infoBalance)), 213, 271 - networkOffset, 170,
                7.5f, "ink2", "left");
        if (waiting()) centeredText(g, tr("waiting"), 213, 258 - networkOffset, 170,
                7.5f, "muted", "left");
        else if (feedback) {
            Component result = actionFeedback();
            if (menu.resultNode >= 0 && menu.resultNode < menu.nodes().size()) {
                result = tr("node_result", nodeLabel(menu.node(menu.resultNode)), result);
            }
            boolean success = switch (menu.actionResult) {
                case UNLOCKED, ALREADY_UNLOCKED, OVERCLOCK_ENABLED, OVERCLOCK_DISABLED, STRUCTURE_REFRESHED -> true;
                default -> false;
            };
            centeredText(g, result, 213, 258 - networkOffset, 170, 7.5f,
                    success ? "accent" : "warning", "left");
        }
    }

    private StarComputationUpgradeCatalog upgradeCatalog() {
        return menu.upgrades;
    }

    private StarComputationUpgradeCatalog.Entry selectedUpgrade() {
        return upgradeCatalog().entry(com.atir.molecularmanipulator.util.MathCompat.clamp(selectedUpgradeLevel, 1, 9));
    }

    private UpgradeUiState upgradeState() {
        var entry = selectedUpgrade();
        return new UpgradeUiState(entry.level(), menu.certifiedLevel, menu.upgradeTarget, menu.upgradeRunning,
                menu.formed, menu.networkOnline || entry.energy() == 0, litRings(), menu.canConfigure, waiting(), entry.configured(),
                entry.conditionsMet(), menu.upgradeStatus, entry.autoBuild());
    }

    /** UI eligibility only: all arbitrary rule evaluation comes from the synchronized catalog. */
    record UpgradeUiState(int selectedLevel, int certifiedLevel, int serverTarget, boolean running,
                          boolean formed, boolean online, int rings, boolean permitted, boolean waiting,
                          boolean configured, boolean conditionsMet, String status, boolean autoBuild) {
        UpgradeUiState(int selectedLevel, int certifiedLevel, int serverTarget, boolean running,
                boolean formed, boolean online, int rings, boolean permitted, boolean waiting,
                boolean configured, boolean conditionsMet, String status) {
            this(selectedLevel, certifiedLevel, serverTarget, running, formed, online, rings, permitted,
                    waiting, configured, conditionsMet, status, false);
        }
        boolean automatic() { return selectedLevel <= 3 && !autoBuild; }
        boolean certified() { return selectedLevel <= 3 ? autoBuild && rings >= selectedLevel : selectedLevel <= certifiedLevel; }
        boolean preview() { return selectedLevel <= 3 ? autoBuild && selectedLevel > rings + 1 : selectedLevel > certifiedLevel + 1; }
        boolean restoring() { return "restoring".equals(status) || "migration".equals(status); }
        boolean canStart() {
            boolean next = selectedLevel <= 3 ? autoBuild && rings >= selectedLevel - 1 && rings < selectedLevel
                    : selectedLevel == certifiedLevel + 1;
            return !automatic() && !certified() && !preview() && next
                    && (serverTarget == 0 || serverTarget == selectedLevel) && !running && !restoring()
                    && permitted && !waiting && formed && online && (autoBuild || rings >= selectedLevel)
                    && configured && conditionsMet;
        }
        // A lost channel, damaged structure or failed condition must never disable the user's pause command.
        boolean canPause() { return permitted && !waiting && running && serverTarget >= 4 && serverTarget <= 9; }
    }

    private Component levelState(int level) {
        if (level <= 3) {
            if (!upgradeCatalog().entry(level).autoBuild()) return up("automatic_short");
            if (litRings() >= level) return up("certified_short");
            return up(level == litRings() + 1 ? "next_short" : "preview_short");
        }
        if (level <= menu.certifiedLevel) return up("certified_short");
        if (level == menu.upgradeTarget) return up("task_short");
        return up(level == menu.certifiedLevel + 1 ? "next_short" : "preview_short");
    }

    private static String knownUpgradeStatus(String status) {
        if (status == null) return "idle";
        return switch (status) {
            case "idle", "running", "paused", "structure", "offline", "conditions", "rule_missing", "energy",
                    "restoring", "complete", "migration", "anchor_materials", "anchor_space", "anchor_chunks",
                    "anchor_permission", "anchor_failed" -> status;
            default -> "idle";
        };
    }

    private Component selectedUpgradeStatus() {
        var state = upgradeState();
        if (state.restoring()) return up("status." + knownUpgradeStatus(state.status()));
        if (state.automatic()) return up("status.automatic");
        if (state.certified()) return up(menu.formed && litRings() >= state.selectedLevel() ? "status.certified" : "status.repair");
        if (state.preview()) return up("status.preview", state.selectedLevel() <= 3 ? Math.min(3, litRings() + 1)
                : Math.min(9, menu.certifiedLevel + 1));
        if (state.serverTarget() != 0 && state.serverTarget() != state.selectedLevel()) return up("status.another_task", state.serverTarget());
        if (state.running()) return up("status." + knownUpgradeStatus(state.status()));
        if (!state.permitted()) return up("status.permission");
        if (!state.configured()) return up("status.rule_missing");
        if (!state.formed() || !state.autoBuild() && state.rings() < state.selectedLevel()) return up("status.structure");
        if (!state.online()) return up("status.offline");
        if (!state.conditionsMet()) {
            if (state.autoBuild() && knownUpgradeStatus(state.status()).startsWith("anchor_"))
                return up("status." + knownUpgradeStatus(state.status()));
            return up("status.conditions");
        }
        return up(state.serverTarget() == state.selectedLevel() ? "status.paused" : "status.idle");
    }

    private Component startUpgradeLabel() {
        if (waiting()) return tr("waiting");
        var state = upgradeState();
        if (state.preview()) return up("preview_button", state.selectedLevel());
        if (state.running() && state.serverTarget() == state.selectedLevel()) return up("running_button", state.selectedLevel());
        if (state.autoBuild() && selectedUpgrade().energy() == 0)
            return up(state.selectedLevel() <= 3 ? "build" : "upgrade", state.selectedLevel());
        return up(state.serverTarget() == state.selectedLevel() ? "resume" : "start", state.selectedLevel());
    }

    private Component pauseUpgradeLabel() { return waiting() ? tr("waiting") : up("pause", menu.upgradeTarget); }

    private Component upgradeActionTooltip() {
        var entry = selectedUpgrade();
        var tooltip = selectedUpgradeStatus().copy();
        if (entry.autoBuild()) tooltip.append("\n").append(up("auto_build_help"));
        if (entry.energy() > 0) tooltip.append("\n").append(up("cost_help", energy(entry.energy()), energy(entry.aePerTick())))
                .append("\n").append(up("action_help"));
        else tooltip.append("\n").append(up("no_energy"));
        return tooltip;
    }

    private Component actionFeedback() {
        // Upgrade actions use their synchronized status, avoiding a second client-side result vocabulary.
        if (menu.actionResult.name().startsWith("UPGRADE_")) return up("status." + knownUpgradeStatus(menu.upgradeStatus));
        return tr("result." + menu.actionResult.name().toLowerCase(Locale.ROOT));
    }

    private static String energy(double value) {
        if (!Double.isFinite(value)) return "0";
        return value == Math.floor(value) ? INTEGER.format(value) : java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    private static String shortEnergy(double value) {
        double magnitude = Math.abs(value);
        return magnitude >= 1.0e12 || magnitude > 0 && magnitude < 0.001
                ? String.format(Locale.ROOT, "%.3g", value) : energy(value);
    }

    private void drawUpgrade(GuiGraphics g) {
        int level = selectedUpgrade().level();
        label(g, up("route"), 17, 45, 104, "ink");
        small(g, Component.literal("L" + menu.benefitLevel + " → L" + level), 123, 45, 66, "accent", "right");
        for (int i = 1; i <= 9; i++) {
            boolean achieved = i <= 3 ? litRings() >= i : menu.certifiedLevel >= i;
            fill(g, 19 + (i - 1) * 19, 82, 14, 2, color(achieved ? "accent" : "hair"));
        }
        centeredText(g, up("route_help"), 17, 94, 172, 7.5f, "ink2", "left");
        centeredText(g, up("levels", menu.benefitLevel, menu.certifiedLevel), 17, 106, 172, 7.5f, "ink2", "left");
        fill(g, 17, 115, 172, 1, color("hair"));
        centeredText(g, up("benefits"), 17, 126, 102, 7.5f, "ink", "left");
        centeredText(g, Component.literal("L" + menu.benefitLevel + " → L" + level), 122, 126, 67, 7.5f, "accent", "right");
        var current = new StarComputationBenefits(com.atir.molecularmanipulator.util.MathCompat.clamp(menu.benefitLevel, 0, 9), false);
        var target = new StarComputationBenefits(level, false);
        for (int row = 0; row < 4; row++) {
            int center = 140 + row * 23;
            centeredText(g, tr(BENEFIT_KEYS[row]), 17, center, 172, 7.5f, "ink2", "left");
            centeredText(g, baseBenefit(current, row), 17, center + 10, 73, 7.5f, "ink2", "left");
            centeredText(g, Component.literal("→"), 91, center + 10, 12, 7.5f, "muted", "center");
            centeredText(g, baseBenefit(target, row), 105, center + 10, 84, 7.5f, "accent", "right");
        }
        centeredText(g, up("benefits_note"), 17, 234, 172, 7.5f, "ink2", "left");

        centeredText(g, up("target_power"), 213, 45, 133, 7.5f, "ink2", "left");
        centeredText(g, Component.literal(number(menu.levelThreshold(level))), 213, 65, 130, 17, "ink", "left");
        fill(g, 352, 51, 31, 25, color("accent_fill"));
        centeredText(g, Component.literal("L" + level), 353, 63, 30, 13, "inverse", "center");
        centeredText(g, levelState(level), 213, 83, 170, 7.5f, "accent", "left");
        centeredText(g, up(upgradeState().certified() ? "requirements_reference" : "requirements"),
                213, 108, 103, 7.5f, "ink", "left");
        centeredText(g, upgradeRequirements.rangeLabel(), 316, 108, 67, 7.5f, "ink2", "right");
        centeredText(g, up("hover_help"), 213, 233, 170, 6.5f, "ink2", "left");
        drawCalibration(g);
    }

    private static final String[] BENEFIT_KEYS = {"assembler_parallel", "cpu_parallel", "cpu_storage", "fabrication_speed"};

    private static Component baseBenefit(StarComputationBenefits benefit, int row) {
        return switch (row) {
            case 0 -> amount(benefit.assemblerParallel());
            case 1 -> amount(benefit.cpuParallel());
            case 2 -> storage(benefit.storageBytes());
            case 3 -> Component.literal("×" + benefit.speedMultiplier());
            default -> throw new IllegalArgumentException("Invalid benefit row " + row);
        };
    }

    private int calibrationLevel() { return menu.upgradeTarget >= 4 ? menu.upgradeTarget : selectedUpgrade().level(); }

    private void drawCalibration(GuiGraphics g) {
        int task = calibrationLevel();
        var entry = upgradeCatalog().entry(task);
        boolean activeTask = menu.upgradeTarget == task;
        boolean automatic = !activeTask && task <= 3 && !entry.autoBuild();
        boolean building = entry.autoBuild() && entry.energy() == 0;
        boolean certified = !activeTask && (task <= 3 ? litRings() >= task : task <= menu.certifiedLevel);
        double paid = activeTask ? Math.max(0, menu.upgradeEnergy) : 0;
        centeredText(g, up(certified ? "certified_title" : building ? "construction" : automatic ? "automatic_title" : "calibration", task),
                213, 257, 100, 7.5f, "ink", "left");
        if (!building) centeredText(g, up("actual_rate", shortEnergy(activeTask ? menu.upgradeRate : 0)), 315, 257, 68, 7.5f, "ink2", "right");
        fill(g, 213, 267, 170, 3, color("hair"));
        double progress = certified ? 1 : entry.energy() > 0 ? com.atir.molecularmanipulator.util.MathCompat.clamp(paid / entry.energy(), 0, 1) : 0;
        fill(g, 213, 267, (int) Math.round(170 * progress), 3, color("accent"));
        centeredText(g, certified ? up("no_repayment") : entry.energy() == 0 ? up("no_energy")
                : up("energy_progress", shortEnergy(paid), shortEnergy(entry.energy())), 213, 280, 170, 7.5f, "ink2", "left");
        boolean feedback = Util.getMillis() < resultUntil && menu.actionResult != ActionResult.NONE;
        Component status = activeTask ? up("status." + knownUpgradeStatus(menu.upgradeStatus)) : selectedUpgradeStatus();
        if (waiting()) status = tr("waiting");
        else if (feedback) status = actionFeedback();
        centeredText(g, status, 213, 296, 170, 7.5f, "ink", "left");
    }

    private List<Row> upgradeRows() {
        int level = selectedUpgrade().level();
        var rows = new ArrayList<Row>();
        rows.add(new Row(0, menu.formed, up("row.core"), List.of(
                up("core", tr(menu.formed ? "formed" : "incomplete")), up("help.core"))));
        var entry = selectedUpgrade();
        if (!entry.autoBuild()) rows.add(new Row(0, litRings() >= level, up("row.physical", level), List.of(
                up("physical", litRings(), level), up("help.physical"))));
        if (entry.energy() > 0) rows.add(new Row(0, menu.networkOnline, up("row.network"), List.of(
                up("network", tr(menu.networkOnline ? "network_online" : "network_offline")), up("help.network"))));
        if (level > 1 && level <= 3 && entry.autoBuild() && litRings() < level) rows.add(new Row(0, litRings() >= level - 1,
                up("row.previous", level - 1), List.of(up("help.previous"))));
        if (level >= 4) {
            if (level <= menu.certifiedLevel) rows.add(new Row(0, true, up("row.certification_kept"),
                    List.of(up("certification_kept"))));
            else rows.add(new Row(0, menu.certifiedLevel >= level - 1, up("row.previous", level - 1),
                    List.of(up("previous", menu.certifiedLevel, level - 1), up("help.previous"))));
        }
        // Every server row, in order, including unavailable targets and every nested branch. No four-condition cap.
        rows.addAll(selectedUpgrade().rows());
        return rows;
    }

    private List<Component> upgradeTooltip(double x, double y) {
        if (upgradeRequirements.isMouseOver(leftPos + x, topPos + y))
            return upgradeRequirements.tooltipAt(leftPos + x, topPos + y);
        if (x >= 17 && x < 190 && y >= 133 && y < 228) {
            int row = Math.min(3, (int) ((y - 133) / 23));
            var current = new StarComputationBenefits(com.atir.molecularmanipulator.util.MathCompat.clamp(menu.benefitLevel, 0, 9), false);
            var target = new StarComputationBenefits(selectedUpgrade().level(), false);
            return List.of(tr(BENEFIT_KEYS[row]), up("benefit_compare", menu.benefitLevel, baseBenefit(current, row),
                    target.level(), baseBenefit(target, row)), up("benefits_help"));
        }
        if (x >= 17 && x < 190 && y >= 86 && y < 116) return List.of(up("route_help_full"),
                up("levels_full", menu.benefitLevel, menu.certifiedLevel, litRings()));
        if (x >= 17 && x < 190 && y >= 228 && y < 242) return List.of(up("benefits_help"));
        if (x >= 204 && x < 392 && y >= 32 && y < 94) return List.of(up("target_help", selectedUpgrade().level(),
                number(menu.levelThreshold(selectedUpgrade().level()))), up("levels_full", menu.benefitLevel, menu.certifiedLevel, litRings()));
        if (x >= 204 && x < 392 && y >= 246 && y < 306) {
            int task = calibrationLevel();
            var entry = upgradeCatalog().entry(task);
            if (entry.autoBuild() && entry.energy() == 0) return List.of(up("construction", task), up("no_energy"),
                    up("auto_build_help"), selectedUpgradeStatus());
            return List.of(up("calibration", task), up("energy_progress", energy(menu.upgradeTarget == task ? menu.upgradeEnergy : 0),
                    energy(entry.energy())), up("rate_help", energy(menu.upgradeTarget == task ? menu.upgradeRate : 0), energy(entry.aePerTick())),
                    menu.upgradeTarget >= 4 ? up("status." + knownUpgradeStatus(menu.upgradeStatus)) : selectedUpgradeStatus(), up("retained_help"));
        }
        return List.of();
    }

    private void drawOverview(GuiGraphics g) {
        label(g, tr("star_orrery"), 17, 45, 110, "ink2");
        if (menu.formed) small(g, tr("tier." + tier()), 119, 45, 75, "accent", "right");
        else small(g, tr("incomplete"), 17, 68, 172, "warning");
        small(g, tr("star_orrery_hint", litRings(), number(menu.anchorCount)), 17, 56, 172, "muted");
        drawRingList(g);
        centeredText(g, tr("effective_anchors"), 22, 200, 65, 7.5f, "stat_muted", "left");
        centeredText(g, Component.literal(number(menu.anchorCount)), 22, 218, 53,
                26, "inverse", "left");
        centeredText(g, tr("blocks"), 77, 218, 12, 7.5f, "stat_muted", "left");
        int[] anchors = {menu.anchorsReference, menu.anchorsQuantum, menu.anchorsTerminal};
        for (int t = 0; t < 3; t++) {
            centeredText(g, tr("anchor_" + ANCHOR_TIERS[t]), 96, 208 + t * 12, 70,
                    7.5f, "ink2", "left");
            centeredText(g, Component.literal(number(anchors[t])), 140, 208 + t * 12, 51,
                    9, "accent", "right");
        }

        var benefit = menu.benefits();
        label(g, tr("effective_entropy"), 213, 46, 110, "feature_muted");
        text(g, Component.literal(number(menu.effectiveEntropy)), 212, 71, 129,
                26, "feature_ink", "left");
        text(g, Component.literal("L" + benefit.level()), 353, 62, 30,
                16, "inverse", "center");
        small(g, tr(benefit.level() == 9 ? "maximum_level" : "current_level", benefit.level()),
                213, 83, 167, "feature_muted");
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
        centeredText(g, tr("research_parallel_only"), 232, 236.5f, 151,
                7.5f, "muted", "left");
    }

    /** Same nine-row skin for both engine types, without inferring physical ring progress. */
    private void drawRingList(GuiGraphics g) {
        int lit = litRings();
        for (int ring = 0; ring < StarOrreryLayout.RINGS; ring++) {
            int center = RING_CENTER_Y + (StarOrreryLayout.RINGS / 2 - ring) * 12;
            int slots = StarOrreryLayout.slotCount(ring);
            boolean full = ring < lit, current = !menu.starOrrery && ring == lit;
            centeredText(g, tr("ring", ring + 1), 126, center, 30,
                    7.5f, full || current ? "ink2" : "muted", "left");
            centeredText(g, full ? Component.literal(slots + "/" + slots)
                    : current ? Component.literal(currentRingFill() + "/" + slots) : tr("ring_locked"),
                    150, center, 41, 7.5f,
                    full ? "ink" : current ? "accent" : "muted", "right");
        }
    }

    private void benefitRow(GuiGraphics g, int row, String title, Component value, Component detail) {
        float center = BENEFIT_TOP + row * BENEFIT_PITCH + 6.5f;
        centeredText(g, tr(title), 232, center, 84, 7.5f, "ink2", "left");
        centeredText(g, value, 313, center, 70, 9, "accent", "right");
        centeredText(g, detail, 232, center + 10, 151, 7.5f, "muted", "left");
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
        double progress = level == 9 ? 1 : com.atir.molecularmanipulator.util.MathCompat.clamp((double) menu.effectiveEntropy
                / Math.max(1, menu.levelThreshold(next)), 0, 1);
        fill(g, 213, 107, (int) Math.round(170 * progress), 2, color("level"));
    }

    private void drawRecipes(GuiGraphics g) {
        label(g, tr("recipes"), 17, 45, 90, "ink");
        small(g, tr("permanent_unlock"), 137, 45, 52, "muted", "right");
        centeredText(g, tr("effective_entropy"), 24, 182, 156, 7.5f, "stat_muted", "left");
        centeredText(g, Component.literal(number(menu.effectiveEntropy)), 24, 198.5f, 80, 17, "inverse", "left");
        centeredText(g, tr("unlock_count", menu.nodes().stream().filter(Node::unlocked).count(),
                menu.nodes().size()), 110, 198.5f, 70, 7.5f, "stat_muted", "right");
        var node = selected();
        text(g, nodeName(node), 213, 48, 119, 9, "ink", "left");
        small(g, nodeState(node), 333, 46, 50, node.unlocked() || menu.canUnlock(node) ? "accent" : "muted", "right");
        small(g, tr("unlock_hint"), 213, 60, 170, "muted");
        fill(g, 213, 66, 170, 1, color("hair"));
        small(g, tr("entropy_threshold"), 213, 79, 71, "ink2");
        small(g, Component.literal(number(menu.effectiveEntropy) + " / " + number(menu.nodeThreshold(node))
                + " · L" + node.requiredLevel()), 280, 79, 103,
                menu.benefitLevel >= node.requiredLevel() ? "accent" : "warning", "right");
        small(g, tr("prerequisites"), 213, 94, 59, "ink2");
        small(g, prerequisiteNames(node), 268, 94, 115, node.prerequisitesMet() ? "accent" : "warning", "right");
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
                AEKeyRendering.drawInGui(minecraft, g, x, RECIPE_Y, recipe.outputs().get(0).what());
            } else {
                small(g, Component.literal("?"), x, RECIPE_Y + 11, 16, "muted", "center");
            }
        }
        small(g, recipePreviews.isEmpty() ? catalogPlaceholder(node) : ellipsize(contents, 170), 213, 196, 170, "ink");
        small(g, tr("overclock_no_gate"), 213, 207, 170, "muted");
    }

    private Component catalogPlaceholder(Node node) {
        return tr(node.index() < menu.catalog.nodes().size() ? "catalog_empty" : "catalog_waiting");
    }

    private Component prerequisiteNames(Node node) {
        var names = Component.empty();
        for (var parent : node.prerequisites()) {
            if (!names.getString().isEmpty()) names.append(" · ");
            names.append(researchTitle(parent.id(), parent.title(), true));
        }
        return node.prerequisites().isEmpty() ? tr("no_prerequisites") : tr(node.prerequisitesMet()
                ? "prerequisites_done" : "prerequisites_needed", names);
    }

    private List<Component> researchTooltip(Node node) {
        var lines = new ArrayList<Component>();
        lines.add(nodeName(node));
        lines.add(tr("node_tooltip", nodeName(node), number(menu.nodeThreshold(node)), number(node.informationCost())));
        lines.add(Component.literal("L" + menu.benefitLevel + " / L" + node.requiredLevel()));
        if (!node.valid()) lines.add(tr("result.invalid_node").copy().append(": " + node.error()));
        if (node.prerequisites().isEmpty()) lines.add(tr("no_prerequisites"));
        for (var parent : node.prerequisites()) {
            var title = researchTitle(parent.id(), parent.title(), false).copy().append(" [" + parent.id() + "]");
            lines.add(tr(parent.unlocked() ? "prerequisites_done" : "prerequisites_needed", title));
            if (menu.indexOf(parent.id()) < 0) lines.add(Component.literal("/starresearch unlock " + parent.id()));
        }
        return lines;
    }

    private Component nodeTooltip(Node node) {
        var tooltip = nodeState(node).copy();
        for (var line : researchTooltip(node)) tooltip.append("\n").append(line);
        return tooltip;
    }

    private static Component recipeTitle(Entry recipe) {
        return !recipe.present() ? tr("recipe_missing") : recipe.outputs().isEmpty() ? tr("recipe_no_outputs")
                : recipe.outputs().get(0).what().getDisplayName();
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
        var recipePreviews = menu.catalog.recipes(selected().index());
        if (recipePreviews.isEmpty()) lines.add(catalogPlaceholder(selected()));
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
        if (wirelessPanelOpen() || ui == null) return;
        double x = logicalMouseX(mx) - leftPos, y = logicalMouseY(my) - topPos;
        if (upgradePage) {
            List<Component> tooltip = upgradeTooltip(x, y);
            if (!tooltip.isEmpty()) {
                var wrapped = tooltip.stream().flatMap(line -> font.split(line, Math.max(40, Math.min(320, width - 24))).stream()).toList();
                g.renderTooltip(font, wrapped, mx, my);
            }
            return;
        }
        int networkOffset = recipesPage ? RECIPE_COMPACT : 0;
        if (x >= 212 && x < 384 && y >= 263 - networkOffset && y < 279 - networkOffset) {
            g.renderComponentTooltip(font, List.of(tr("balance", DisplayNumbers.exact(menu.infoBalance))), mx, my);
            return;
        }
        if (!recipesPage) return;
        if (x >= 212 && x < 384 && y >= 68 && y < 118) {
            // Full IDs, per-prerequisite ownership and configuration errors need not fit the fixed card's one-line summary.
            var wrapped = researchTooltip(selected()).stream()
                    .flatMap(line -> font.split(line, Math.max(40, Math.min(320, width - 24))).stream()).toList();
            g.renderTooltip(font, wrapped, mx, my);
            return;
        }
        if (x < 212 || x >= 384 || y < 154 || y >= 200) return;
        var recipePreviews = menu.catalog.recipes(selected().index());
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
        fill(g, 66, RING_CENTER_Y - 1, 3, 3, color("accent_fill"));
    }

    private static void ringDot(GuiGraphics g, int index, int count, int radius, int tint) {
        double angle = -Math.PI / 2 + 2 * Math.PI * index / count;
        fill(g, 67 + (int) Math.round(radius * Math.cos(angle)),
                RING_CENTER_Y + (int) Math.round(radius * Math.sin(angle)), 1, 1, tint);
    }

    private void label(GuiGraphics g, Component value, float x, float baseline, float width, String tint) {
        text(g, value, x, baseline, width, 7.5f, tint, "left");
    }
    private void small(GuiGraphics g, Component value, float x, float baseline, float width, String tint) {
        small(g, value, x, baseline, width, tint, "left");
    }
    private void small(GuiGraphics g, Component value, float x, float baseline, float width, String tint, String align) {
        text(g, value, x, baseline, width, 7.5f, tint, align);
    }
    private void text(GuiGraphics g, Component value, float x, float baseline, float width,
            float size, String tint, String align) {
        var entry = new OsqLayout.Entry();
        entry.x = x; entry.baseline = baseline; entry.w = width; entry.size = size;
        entry.fontId = "minecraft:default";
        entry.color = String.format(Locale.ROOT, "#%08X", color(tint));
        entry.alignment = align;
        ui.layout.renderSingleLine(g, font, entry, value, 0, 0);
    }

    private void centeredText(GuiGraphics g, Component value, float x, float centerY, float width,
            float size, String tint, String align) {
        var entry = new OsqLayout.Entry();
        entry.x=x;entry.centerY=centerY;entry.w=width;entry.size=size;
        entry.fontId="minecraft:default";
        entry.color=String.format(Locale.ROOT,"#%08X",color(tint));entry.alignment=align;
        ui.layout.renderSingleLine(g,font,entry,value,0,0);
    }

    /** The list shows names only; progress and actionable instructions live in the hover tooltip. */
    static List<Component> requirementTooltip(Row row) {
        var lines = new ArrayList<Component>();
        lines.add(row.text());
        lines.add(up(row.met() ? "met" : "incomplete"));
        lines.addAll(row.details());
        return List.copyOf(lines);
    }

    /** Variable-height titles retain custom names and deeply nested branches without crowding the list. */
    record WrappedUpgradeRow(Row row, List<String> lines, int indent, int top, int height) {}

    static final class RequirementViewport {
        static final int LINE_HEIGHT = 10;
        List<WrappedUpgradeRow> rows = List.of();
        int contentHeight;
        double scroll;

        void rebuild(List<Row> source, int width, BiFunction<Component, Integer, List<String>> wrap) {
            var result = new ArrayList<WrappedUpgradeRow>(source.size());
            int top = 0;
            for (var row : source) {
                // Keep deep trees readable instead of consuming the whole text column with indentation.
                // Preserve the exact server depth for grouping, without exposing implementation terms to players.
                int indent = Math.min(row.depth(), 6) * 4;
                var lines = List.copyOf(wrap.apply(row.text(), Math.max(1, width - 22 - indent)));
                if (lines.isEmpty()) lines = List.of("");
                int height = lines.size() * LINE_HEIGHT + 6;
                result.add(new WrappedUpgradeRow(row, lines, indent, top, height));
                top += height;
            }
            rows = List.copyOf(result);
            contentHeight = top;
        }

        int maxScroll(int height) { return Math.max(0, contentHeight - height); }
        void scrollTo(double value, int height) { scroll = com.atir.molecularmanipulator.util.MathCompat.clamp(Double.isFinite(value) ? value : 0, 0, maxScroll(height)); }
        int rowAt(double contentY) {
            if (contentY < 0 || contentY >= contentHeight) return -1;
            int low = 0, high = rows.size() - 1;
            while (low <= high) {
                int middle = (low + high) >>> 1;
                var row = rows.get(middle);
                if (contentY < row.top()) high = middle - 1;
                else if (contentY >= row.top() + row.height()) low = middle + 1;
                else return middle;
            }
            return -1;
        }
    }

    /** Hover-to-scroll list with a draggable scrollbar; keyboard navigation is an optional alternative. */
    private final class UpgradeRequirements extends AbstractWidget {
        private final RequirementViewport viewport = new RequirementViewport();
        private List<Row> seenRows = List.of();
        private long metrics = Long.MIN_VALUE;
        private boolean draggingScrollbar;
        private double grabOffset;

        UpgradeRequirements(OsqLayout.Entry box) {
            super(leftPos + Math.round(box.x), topPos + Math.round(box.y), Math.round(box.w), Math.round(box.h), up("requirements"));
            viewport.scroll = upgradeScroll;
        }

        void syncRows() {
            var rows = upgradeRows();
            long generation = OsqFonts.metricsGeneration(font);
            if (!rows.equals(seenRows) || metrics != generation) {
                seenRows = List.copyOf(rows);
                metrics = generation;
                viewport.rebuild(rows, getWidth(), (text, width) -> OsqFonts.wrap(font, text.getString(), CONTENTS_FONT, 7.5f, width));
                scrollTo(viewport.scroll);
            }
        }

        Component rangeLabel() {
            int first = viewport.rowAt(viewport.scroll);
            int last = viewport.rowAt(Math.min(viewport.contentHeight - 1, viewport.scroll + getHeight() - 1));
            return up("row_range", Math.max(0, first + 1), Math.max(0, last + 1), viewport.rows.size());
        }

        private void scrollTo(double value) {
            viewport.scrollTo(value, getHeight());
            upgradeScroll = viewport.scroll;
        }

        private int thumbHeight() {
            return Math.min(getHeight(), Math.max(14, getHeight() * getHeight() / Math.max(getHeight(), viewport.contentHeight)));
        }

        private int thumbY() {
            return viewport.maxScroll(getHeight()) == 0 ? 0
                    : (int) Math.round((getHeight() - thumbHeight()) * viewport.scroll / viewport.maxScroll(getHeight()));
        }

        @Override protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
            syncRows();
            g.pose().pushPose();
            g.pose().translate(getX(), getY(), 0);
            var matrix = g.pose().last().pose();
            var from = matrix.transformPosition(new Vector3f(0, 0, 0));
            var to = matrix.transformPosition(new Vector3f(getWidth(), getHeight(), 0));
            g.flush();
            g.enableScissor((int) Math.floor(from.x), (int) Math.floor(from.y), (int) Math.ceil(to.x), (int) Math.ceil(to.y));
            int hovered = isMouseOver(mx, my) && mx < getX() + getWidth() - 9
                    ? viewport.rowAt(my - getY() + viewport.scroll) : -1;
            for (int index = 0; index < viewport.rows.size(); index++) {
                var row = viewport.rows.get(index);
                float top = row.top() - (float) viewport.scroll;
                if (top + row.height() <= 0) continue;
                if (top >= getHeight()) break;
                if (index == hovered) fill(g, 0, (int) top, getWidth() - 9, row.height(), color("soft"));
                int markerX = row.indent() + 1, markerY = Math.round(top + 4);
                if (row.row().depth() > 0) {
                    fill(g, markerX - 2, Math.round(top), 1, row.height(), color("hair"));
                    fill(g, markerX - 2, markerY + 3, 3, 1, color("hair"));
                }
                int ink = color(row.row().met() ? "accent" : "ink2");
                g.renderOutline(markerX, markerY, 6, 6, ink);
                if (row.row().met()) {
                    fill(g, markerX + 1, markerY + 3, 2, 1, ink);
                    fill(g, markerX + 2, markerY + 4, 1, 1, ink);
                    fill(g, markerX + 3, markerY + 2, 1, 2, ink);
                    fill(g, markerX + 4, markerY + 1, 1, 2, ink);
                } else fill(g, markerX + 2, markerY + 3, 2, 1, ink);
                for (int line = 0; line < row.lines().size(); line++)
                    centeredText(g, Component.literal(row.lines().get(line)), row.indent() + 12,
                            top + 7 + line * RequirementViewport.LINE_HEIGHT, getWidth() - 22 - row.indent(), 7.5f, "ink", "left");
            }
            if (viewport.maxScroll(getHeight()) > 0) {
                fill(g, getWidth() - 4, 0, 3, getHeight(), color("hair"));
                fill(g, getWidth() - 4, thumbY(), 3, thumbHeight(), color("accent"));
            }
            if (isFocused() && minecraft.getLastInputType().isKeyboard())
                g.renderOutline(0, 0, getWidth(), getHeight(), color("accent"));
            g.flush();
            g.disableScissor();
            g.pose().popPose();
        }

        List<Component> tooltipAt(double mouseX, double mouseY) {
            if (mouseX >= getX() + getWidth() - 9) return List.of(up("scroll_help"));
            int index = viewport.rowAt(mouseY - getY() + viewport.scroll);
            if (index < 0) return List.of();
            var row = viewport.rows.get(index).row();
            return requirementTooltip(row);
        }

        @Override public boolean mouseScrolled(double x, double y, double vertical) {
            if (!visible || !isMouseOver(x, y) || vertical == 0) return false;
            scrollTo(viewport.scroll - vertical * 24);
            return true;
        }

        @Override public void onClick(double x, double y) {
            draggingScrollbar = viewport.maxScroll(getHeight()) > 0 && x >= getX() + getWidth() - 9;
            if (draggingScrollbar) {
                double local = y - getY();
                grabOffset = local >= thumbY() && local < thumbY() + thumbHeight() ? local - thumbY() : thumbHeight() / 2.0;
                scrollFromMouse(y);
            }
        }

        @Override protected void onDrag(double x, double y, double dx, double dy) {
            if (draggingScrollbar) scrollFromMouse(y);
        }

        private void scrollFromMouse(double y) {
            scrollTo((y - getY() - grabOffset) * viewport.maxScroll(getHeight()) / Math.max(1, getHeight() - thumbHeight()));
        }

        @Override public void onRelease(double x, double y) {
            draggingScrollbar = false;
        }

        @Override public boolean keyPressed(int key, int scan, int modifiers) {
            if (!visible || !isFocused()) return false;
            switch (key) {
                case GLFW.GLFW_KEY_UP -> scrollTo(viewport.scroll - RequirementViewport.LINE_HEIGHT);
                case GLFW.GLFW_KEY_DOWN -> scrollTo(viewport.scroll + RequirementViewport.LINE_HEIGHT);
                case GLFW.GLFW_KEY_PAGE_UP -> scrollTo(viewport.scroll - getHeight() + RequirementViewport.LINE_HEIGHT);
                case GLFW.GLFW_KEY_PAGE_DOWN -> scrollTo(viewport.scroll + getHeight() - RequirementViewport.LINE_HEIGHT);
                case GLFW.GLFW_KEY_HOME -> scrollTo(0);
                case GLFW.GLFW_KEY_END -> scrollTo(viewport.maxScroll(getHeight()));
                default -> { return false; }
            }
            return true;
        }

        @Override protected void updateWidgetNarration(NarrationElementOutput output) {
            output.add(NarratedElementType.TITLE, getMessage());
            int index = viewport.rowAt(viewport.scroll);
            if (index >= 0) for (var line : requirementTooltip(viewport.rows.get(index).row()))
                output.add(NarratedElementType.TITLE, line);
            output.add(NarratedElementType.USAGE, up("scroll_help"));
        }
    }

    /** Native hit-tested scrollbar beside the existing research rows; the single default needs no scroll chrome. */
    private final class ResearchScrollbar extends AbstractWidget {
        private boolean draggingScrollbar;
        private double grabOffset;

        ResearchScrollbar() {
            super(nodeButtons.get(0).getX() + nodeButtons.get(0).getWidth() + 1, nodeButtons.get(0).getY(), 5,
                    nodeButtons.get(nodeButtons.size() - 1).getY() + nodeButtons.get(nodeButtons.size() - 1).getHeight()
                            - nodeButtons.get(0).getY(), tr("recipes"));
            setTooltip(Tooltip.create(tr("research_scroll_help")));
        }

        private int maximum() { return Math.max(0, menu.nodes().size() - RESEARCH_ROWS); }
        private int thumbHeight() { return Math.max(12, getHeight() * RESEARCH_ROWS / menu.nodes().size()); }
        private int thumbY() {
            return maximum() == 0 ? 0 : (getHeight() - thumbHeight()) * researchScroll / maximum();
        }

        @Override protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
            fill(g, getX() + 1, getY(), 3, getHeight(), color("hair"));
            fill(g, getX() + 1, getY() + thumbY(), 3, thumbHeight(), color("accent"));
        }

        @Override public void onClick(double x, double y) {
            draggingScrollbar = true;
            double local = y - getY();
            grabOffset = local >= thumbY() && local < thumbY() + thumbHeight()
                    ? local - thumbY() : thumbHeight() / 2.0;
            scrollFromMouse(y);
        }

        @Override protected void onDrag(double x, double y, double dx, double dy) {
            if (draggingScrollbar) scrollFromMouse(y);
        }

        private void scrollFromMouse(double y) {
            scrollResearchTo((int) Math.round((y - getY() - grabOffset) * maximum()
                    / Math.max(1, getHeight() - thumbHeight())));
        }

        @Override public void onRelease(double x, double y) { draggingScrollbar = false; }

        @Override protected void updateWidgetNarration(NarrationElementOutput output) {
            output.add(NarratedElementType.TITLE, tr("research_range", researchScroll + 1,
                    Math.min(menu.nodes().size(), researchScroll + RESEARCH_ROWS), menu.nodes().size()));
            output.add(NarratedElementType.USAGE, tr("research_scroll_help"));
        }
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
            String status = status();
            background.sprite = ui.sprite("button_" + status);
            background.x = getX(); background.y = getY(); background.w = getWidth(); background.h = getHeight();
            OsqLayout.drawSprite(g, background, 0, 0);
            renderContents(g, status);
        }
        private String status() {
            if (!active) return "disabled";
            if (id.equals("reclaim")) return menu.reclaimArmed ? "confirm" : "danger";
            return isHoveredOrFocused() ? "hover" : "normal";
        }
        private void renderContents(GuiGraphics g, String status) {
            setMessage(label.get());
            g.pose().pushPose();
            g.pose().translate(getX(), getY(), 0);
            var entry = new OsqLayout.Entry();
            entry.x = 4; entry.w = getWidth() - (id.equals("overclock") ? 34 : 8);
            entry.centerY = OsqUi.controlCenter("button", status, getHeight(), false); entry.size = 7.5f;
            entry.fontId = "minecraft:default";
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
            g.pose().popPose();
        }
    }
}
