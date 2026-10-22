package com.atir.molecularmanipulator.client;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.GenericStack;
import appeng.client.gui.Icon;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.AECheckbox;
import appeng.core.network.serverbound.InventoryActionPacket;
import appeng.helpers.InventoryAction;
import appeng.menu.slot.FakeSlot;
import com.atir.molecularmanipulator.blockentity.MolecularAutoCrafter;
import com.atir.molecularmanipulator.blockentity.MolecularCenterBlockEntity;
import com.atir.molecularmanipulator.menu.MolecularCenterMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Locale;

public final class MolecularCenterScreen extends ResponsiveContainerScreen<MolecularCenterMenu> {
    static final int TAB_OVERVIEW = 0;
    static final int TAB_AUTO_CRAFT = 1;
    static final int TAB_COLORS = 2;
    private static final int DISMANTLE_CONFIRM_TICKS = 60;
    private static final int DISMANTLE_CONFIRM_DELAY_TICKS = 6;
    private static final int SEARCH_DEBOUNCE_TICKS = 5;

    private EditBox patternSearch;
    private EditBox autoCraftOutputLimit;
    private final EditBox[] autoCraftInputReserves = new EditBox[MolecularAutoCrafter.MAX_INPUTS];
    private final List<CompactCogButton> autoCraftConfigButtons = new java.util.ArrayList<>();
    private final List<AECheckbox> autoCraftToggleButtons = new java.util.ArrayList<>();
    private final PatternSearchIndexState patternSearchIndex = new PatternSearchIndexState();
    private List<Integer> filteredPatternSlots = List.of();
    private String patternSearchQuery = "";
    private int patternSearchDebounce;
    private boolean patternSearchIndexPending;
    private int requestedPatternRevision = -1;
    private int indexedPatternRevision = -1;
    private int detailTab = TAB_OVERVIEW;
    private int dismantleConfirmTicks;
    private int displayedAutoCraftSlot = Integer.MIN_VALUE;
    private OsqUi ui;
    private ItemStack cachedPatternStack = ItemStack.EMPTY;
    private IPatternDetails cachedPatternDetails;
    private boolean uiRebuilding;
    private net.minecraft.client.gui.components.Button buildControl, dismantleControl;
    private final net.minecraft.client.gui.components.Button[] runControls=new net.minecraft.client.gui.components.Button[9], reserveControls=new net.minecraft.client.gui.components.Button[9];
    private net.minecraft.client.gui.components.Button limitControl;
    private final StructureUpdateConfirmation colorUpdateConfirmation=new StructureUpdateConfirmation();

    public MolecularCenterScreen(MolecularCenterMenu menu, Inventory playerInventory, Component title,
            ScreenStyle style) {
        super(menu, playerInventory, title, style);
    }

    @Override
    protected void slotClicked(@Nullable Slot slot, int slotId, int mouseButton, ClickType clickType) {
        cancelDismantleConfirmation();
        int autoCraftPatternSlot = menu.getAutoCraftPatternSlots().indexOf(slot);
        if (detailTab == TAB_AUTO_CRAFT && autoCraftPatternSlot >= 0 && mouseButton == 1) {
            menu.requestSelectAutoCraftSlot(autoCraftPatternSlot);
            return;
        }
        if (slot instanceof FakeSlot) {
            var action = mouseButton == 1
                    ? InventoryAction.SPLIT_OR_PLACE_SINGLE
                    : InventoryAction.PICKUP_OR_SET_DOWN;
            PacketDistributor.sendToServer(new InventoryActionPacket(action, slotId, 0));
            return;
        }
        super.slotClicked(slot, slotId, mouseButton, clickType);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        double logicalX = logicalMouseX(mouseX);
        double logicalY = logicalMouseY(mouseY);
        boolean clickedDismantle = button == 0
                && logicalX >= leftPos + 350 && logicalX < leftPos + 424
                && logicalY >= topPos + 287 && logicalY < topPos + 304;
        if (!clickedDismantle) {
            cancelDismantleConfirmation();
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void init() {
        uiRebuilding=true;
        ui=new OsqUi(detailTab==TAB_AUTO_CRAFT?"seq_center_autocraft":detailTab==TAB_COLORS?"seq_center_colors":"seq_center_overview");
        imageWidth=ui.layout.width;imageHeight=ui.layout.height;super.init();
        menu.setPatternSearchIndexListener(this::acceptPatternSearchIndexChunk);
        ui.inventory(menu);ui.slots(menu.getPatternSlots(),"pat");
        ui.slot(menu.getQuantumSlot(),"qes");ui.slot(menu.getMatrixUploadCoreSlot(),"core");
        patternSearch=addScreenWidget(ui.field("search",leftPos,topPos,font,OsqUi.tr("gui.molecularmanipulator.pattern_search")));
        patternSearch.setValue(patternSearchQuery);patternSearch.setResponder(this::patternSearchChanged);
        if(!patternSearchQuery.isBlank())patternSearchDebounce=1;
        addScreenWidget(ui.button("prev",leftPos,topPos,()->Component.literal("<"),()->changePatternPage(-1)));
        addScreenWidget(ui.button("next",leftPos,topPos,()->Component.literal(">"),()->changePatternPage(1)));
        ui.omit("gui.molecularmanipulator.pattern_search");
        ui.text("gui.molecularmanipulator.page",()->OsqUi.tr("gui.molecularmanipulator.page",menu.getPage()+1,menu.getPageCount()));
        String[] tabs={"tab_overview","tab_auto_craft","tab_colors"};
        for(int i=0;i<3;i++){final int tab=i;var b=addScreenWidget(ui.tab(308.75f+i*38.25f,9,38.75f,15,leftPos,topPos,
                ()->OsqUi.tr("gui.molecularmanipulator."+tabs[tab]),()->selectTab(tab)));ui.state(b,()->detailTab==tab?"active":"normal");}
        addScreenWidget(ui.button("projection",leftPos,topPos,this::previewLabel,()->{MolecularCenterGhostPreview.toggle(menu.getCenter());menu.requestPreview();}));
        buildControl=addScreenWidget(ui.button("build",leftPos,topPos,()->OsqUi.tr("gui.molecularmanipulator.build"),menu::requestBuild));
        var dismantle=addScreenWidget(ui.button("dismantle",leftPos,topPos,this::dismantleLabel,this::dismantleClicked));
        dismantleControl=dismantle;
        ui.state(dismantle,()->dismantleConfirming()?"confirm":"danger");
        ui.text("gui.molecularmanipulator.formed",()->OsqUi.tr(menu.formed?"gui.molecularmanipulator.formed":"gui.molecularmanipulator.incomplete"));
        ui.text("gui.molecularmanipulator.overview_online",()->OsqUi.tr("gui.molecularmanipulator.overview_"+(menu.operational?"online":"offline")));
        ui.text("gui.molecularmanipulator.quantum_state.connected",()->OsqUi.tr("gui.molecularmanipulator.quantum_state."+menu.quantumLinkState.name().toLowerCase(Locale.ROOT)));
        ui.number(65.39f,179.5f,()->formatFrequency(menu.quantumFrequency));ui.width(46,165.89f,101);
        ui.text("gui.molecularmanipulator.overview_quantum_requirements",()->OsqUi.tr("gui.molecularmanipulator.overview_quantum_requirements",512));
        ui.sprite(201,42.5f,()->ui.sprite("dot_"+(menu.formed?"ok":"warn")));
        ui.sprite(411,42.5f,()->ui.sprite("dot_"+(menu.operational?"ok":"mute")));
        ui.sprite(39,163.89f,()->ui.sprite("dot_"+(menu.quantumFrequency==0?"mute":"ok")));
        ui.progress(201,209,()->menu.usedPatternSlots/(double)Math.max(1,menu.patternCapacity));
        ui.color("gui.molecularmanipulator.formed",()->menu.formed?ui.palette.ok:ui.palette.warn);
        ui.color("gui.molecularmanipulator.overview_online",()->menu.operational?ui.palette.ok:ui.palette.ink3);
        ui.number(200.5f,203,()->menu.usedPatternSlots);ui.width(200.5f,203,38);ui.number(209.63f,203,()->"");ui.number(241.58f,203,()->"/ "+menu.patternCapacity);
        ui.number(200.5f,253,()->Integer.bitCount(menu.autoCraftEnabledMask));ui.number(211.31f,253,()->"/ 9");
        ui.number(254,249,()->menu.activePipelineRecipes);ui.number(301.09f,249,()->OsqUi.compact(menu.activePipelineCrafts));ui.number(352,249,()->OsqUi.compact(menu.pendingOutputAmount));
        ui.width(254,249,30);ui.width(301.09f,249,40);ui.width(352,249,60);
        autoCraftConfigButtons.clear();autoCraftToggleButtons.clear();
        autoCraftOutputLimit=addScreenWidget(detailTab==TAB_AUTO_CRAFT?ui.field("limit",leftPos,topPos,font,Component.empty())
                :new EditBox(font,-1000,-1000,40,10,Component.empty()));
        configureLongField(autoCraftOutputLimit,menu.autoCraftOutputLimit,"gui.molecularmanipulator.auto_craft_output_limit_tooltip");
        for(int i=0;i<9;i++){final int input=i;
            autoCraftInputReserves[i]=addScreenWidget(detailTab==TAB_AUTO_CRAFT?ui.field("rsv_f"+i,leftPos,topPos,font,Component.empty())
                    :new EditBox(font,-1000,-1000,40,10,Component.empty()));
            configureLongField(autoCraftInputReserves[i],menu.getAutoCraftInputReserve(i),"gui.molecularmanipulator.auto_craft_input_reserve_tooltip");
            if(detailTab==TAB_AUTO_CRAFT){
                ui.slot(menu.getAutoCraftPatternSlots().get(i),"res"+i);
                addScreenWidget(ui.button("cfg"+i,leftPos,topPos,()->Component.literal("+"),()->menu.requestSelectAutoCraftSlot(input)));
                var b=addScreenWidget(ui.button("run"+i,leftPos,topPos,()->Component.literal("+"),()->menu.requestToggleAutoCraft(input)));
                runControls[i]=b;
                ui.state(b,()->(menu.autoCraftEnabledMask&(1<<input))!=0?"active":"normal");
                ui.icon(b,()->(menu.autoCraftEnabledMask&(1<<input))!=0?"pause":"play");
                var apply=addScreenWidget(ui.button("rsv_ok"+i,leftPos,topPos,()->Component.literal("+"),()->menu.requestSetAutoCraftInputReserve(input,autoCraftInputReserveInput(input))));
                reserveControls[i]=apply;
                ui.state(apply,()->input<menu.autoCraftInputCount?"normal":"disabled");
            }
        }
        if(detailTab==TAB_AUTO_CRAFT){ui.number(226.36f,45,()->Integer.bitCount(menu.autoCraftEnabledMask));ui.number(231.22f,45,()->"/ 9");
            ui.number(230,143.89f,()->menu.autoCraftSelectedSlot<0?"": "#"+(menu.autoCraftSelectedSlot+1)+" · "+OsqUi.tr("gui.molecularmanipulator.auto_craft_state."+menu.autoCraftState.name().toLowerCase(Locale.ROOT)).getString());
            ui.width(230,143.89f,82);
            ui.at(223,137,()->{var d=selectedAutoCraftPattern();return d==null?OsqUi.tr("gui.molecularmanipulator.auto_craft_select_hint"):d.getPrimaryOutput().what().getDisplayName();});
            ui.width(223,137,88);
        }
        if(detailTab==TAB_AUTO_CRAFT)limitControl=addScreenWidget(ui.button("limit_set",leftPos,topPos,()->Component.literal("+"),()->menu.requestSetAutoCraftOutputLimit(autoCraftOutputLimitInput())));
        if(detailTab==TAB_COLORS){
            for(int target=0;target<5;target++)for(int channel=0;channel<3;channel++){final int t=target,c=channel;
                var b=addScreenWidget(ui.button("c"+target+"RGB".charAt(channel),leftPos,topPos,
                        ()->Component.literal("RGB".substring(c,c+1)),
                        ()->menu.requestAdjustVisualColor(t,c,(net.minecraft.client.gui.screens.Screen.hasShiftDown()?-1:1)*(net.minecraft.client.gui.screens.Screen.hasControlDown()?1:17))));
            }
            addScreenWidget(ui.button("reset",leftPos,topPos,()->OsqUi.tr("gui.molecularmanipulator.visual_color_reset"),menu::requestResetVisualColors));
            if(menu.legacyStructure){
                final var confirm=colorUpdateConfirmation;
                addScreenWidget(ui.button("update",leftPos,topPos,()->OsqUi.tr("gui.molecularmanipulator.structure_update_confirm"),()->{if(confirm.click())menu.requestStructureUpdate();}));
                addScreenWidget(ui.button("keep",leftPos,topPos,()->OsqUi.tr("gui.molecularmanipulator.structure_update_keep_legacy"),menu::requestKeepLegacyStructure));
            }
        }
        ui.visible=e->{
            if("tip".equals(e.kind))return false;
            if(detailTab==TAB_AUTO_CRAFT&&e.requiresSelection&&!hasSelectedAutoCraftDetails())return false;
            if(detailTab==TAB_AUTO_CRAFT&&"selection_hint".equals(e.kind))return !hasSelectedAutoCraftDetails();
            if(detailTab==TAB_COLORS&&!menu.legacyStructure&&e.x>=192&&e.y>=192&&e.y<282)return false;
            if(detailTab==TAB_COLORS&&!menu.legacyStructure&&"text".equals(e.type)&&e.x>=192&&e.baseline>=192&&e.baseline<282)return false;
            if(detailTab==TAB_AUTO_CRAFT&&e.x>=200&&e.y>=125&&e.y<260&&e.id!=null&&e.id.startsWith("rsv")) {
                int input="slot".equals(e.kind)?Math.round((e.y-172)/18)*2+(e.x>300?1:0):Integer.parseInt(e.id.replaceAll("\\D",""));
                return hasSelectedAutoCraftDetails()&&input<menu.autoCraftInputCount;
            }
            return true;
        };
        uiRebuilding=false;selectTab(detailTab);
    }

    void changePatternPage(int offset) {
        if (patternSearchQuery.isBlank()) {
            menu.requestPage(menu.getPage() + offset);
        } else {
            showPatternSearchPage(menu.getPage() + offset);
        }
    }

    private void patternSearchChanged(String query) {
        patternSearchQuery = query == null ? "" : query.strip();
        patternSearchDebounce = SEARCH_DEBOUNCE_TICKS;
        if (patternSearchQuery.isBlank()) {
            filteredPatternSlots = List.of();
            patternSearchIndexPending = false;
            menu.clearPatternSearch();
        }
    }

    private void applyPatternSearchQuery() {
        if (patternSearchQuery.isBlank()) {
            return;
        }
        if (patternSearchIndex.complete() && indexedPatternRevision == menu.patternRevision) {
            rebuildPatternSearchResults();
        } else {
            requestPatternSearchIndex();
        }
    }

    private void requestPatternSearchIndex() {
        if (patternSearchIndexPending) {
            return;
        }
        patternSearchIndexPending = true;
        requestedPatternRevision = menu.patternRevision;
        menu.requestPatternSearchIndex();
    }

    private void acceptPatternSearchIndexChunk(
            com.atir.molecularmanipulator.network.PatternSearchIndexChunk chunk) {
        if (!patternSearchIndex.accept(chunk) || !patternSearchIndex.complete()) {
            return;
        }
        patternSearchIndexPending = false;
        indexedPatternRevision = requestedPatternRevision;
        if (patternSearchQuery.isBlank()) {
            return;
        }
        if (indexedPatternRevision != menu.patternRevision) {
            requestPatternSearchIndex();
        } else {
            rebuildPatternSearchResults();
        }
    }

    private void rebuildPatternSearchResults() {
        filteredPatternSlots = patternSearchIndex.entries().stream()
                .filter(entry -> ClientSearchMatcher.matchesPattern(entry, patternSearchQuery))
                .map(entry -> entry.sourceSlot())
                .toList();
        showPatternSearchPage(0);
    }

    private void showPatternSearchPage(int requestedPage) {
        int resultCount = filteredPatternSlots.size();
        int pageCount = Math.max(1, (resultCount + MolecularCenterBlockEntity.PATTERNS_PER_PAGE - 1)
                / MolecularCenterBlockEntity.PATTERNS_PER_PAGE);
        int page = Math.max(0, Math.min(pageCount - 1, requestedPage));
        int first = page * MolecularCenterBlockEntity.PATTERNS_PER_PAGE;
        int last = Math.min(resultCount, first + MolecularCenterBlockEntity.PATTERNS_PER_PAGE);
        int[] sourceSlots = new int[Math.max(0, last - first)];
        for (int index = first; index < last; index++) {
            sourceSlots[index - first] = filteredPatternSlots.get(index);
        }
        menu.requestPatternSearchPage(page, resultCount, sourceSlots);
    }

    void selectTab(int tab) {
        int nextTab=Math.max(TAB_OVERVIEW,Math.min(TAB_COLORS,tab));
        if(nextTab!=detailTab&&!uiRebuilding){detailTab=nextTab;rebuildWidgets();return;}
        detailTab=nextTab;
        boolean autoCraftVisible = detailTab == TAB_AUTO_CRAFT;
        boolean autoCraftSelected = hasSelectedAutoCraftDetails();
        autoCraftOutputLimit.visible = autoCraftVisible && autoCraftSelected;
        for (int input = 0; input < autoCraftInputReserves.length; input++) {
            autoCraftInputReserves[input].visible = autoCraftVisible
                    && input < menu.autoCraftInputCount;
        }
        for (var slot : menu.getAutoCraftPatternSlots()) {
            slot.setActive(autoCraftVisible);
        }
        menu.getQuantumSlot().setActive(ui.layout.find("qes") != null);
        if (menu.getMatrixUploadCoreSlot() != null) {
            menu.getMatrixUploadCoreSlot().setActive(ui.layout.find("core") != null);
        }
        refreshAutoCraftFields();
        refreshAutoCraftIconButtons();
        if(buildControl!=null)buildControl.active=!menu.formed&&!menu.building&&!menu.dismantling;
        if(dismantleControl!=null)dismantleControl.active=!menu.building&&!menu.dismantling&&menu.formed;
    }

    @Override
    public void containerTick() {
        super.containerTick();
        colorUpdateConfirmation.tick(menu.legacyStructure&&!menu.building&&!menu.dismantling);
        if (patternSearchDebounce > 0 && --patternSearchDebounce == 0) {
            applyPatternSearchQuery();
        }
        if (!patternSearchQuery.isBlank()
                && indexedPatternRevision != menu.patternRevision
                && !patternSearchIndexPending
                && patternSearchDebounce == 0) {
            requestPatternSearchIndex();
        }
        if (dismantleConfirmTicks > 0) {
            --dismantleConfirmTicks;
        }
        refreshAutoCraftFields();
        refreshAutoCraftIconButtons();

        if(buildControl!=null)buildControl.active=!menu.formed&&!menu.building&&!menu.dismantling;
        if(dismantleControl!=null)dismantleControl.active=!menu.building&&!menu.dismantling&&menu.formed;
    }

    @Override
    public void removed() {
        menu.setPatternSearchIndexListener(null);

        super.removed();
    }

    void dismantleClicked() {
        if (dismantleConfirmTicks == 0) {
            dismantleConfirmTicks = DISMANTLE_CONFIRM_TICKS;
            return;
        }
        if (dismantleConfirmTicks <= DISMANTLE_CONFIRM_TICKS - DISMANTLE_CONFIRM_DELAY_TICKS) {
            cancelDismantleConfirmation();
            menu.requestDismantle();
        }
    }

    private void cancelDismantleConfirmation() {
        if (dismantleConfirmTicks == 0) {
            return;
        }
        dismantleConfirmTicks = 0;
    }

    Component dismantleLabel() {
        return Component.translatable(dismantleConfirmTicks > 0
                ? "gui.molecularmanipulator.dismantle_confirm"
                : "gui.molecularmanipulator.dismantle");
    }

    Component dismantleTooltip() {
        return Component.translatable(dismantleConfirmTicks > 0
                ? "gui.molecularmanipulator.dismantle_confirm_tooltip"
                : "gui.molecularmanipulator.dismantle_tooltip");
    }

    boolean dismantleConfirming() {
        return dismantleConfirmTicks > 0;
    }

    int detailTab() {
        return detailTab;
    }

    boolean patternSearchWaiting() {
        return !patternSearchQuery.isBlank() && patternSearchIndexPending;
    }

    long autoCraftOutputLimitInput() {
        return parseNonNegativeLong(autoCraftOutputLimit);
    }

    long autoCraftInputReserveInput(int inputIndex) {
        return inputIndex < 0 || inputIndex >= autoCraftInputReserves.length
                ? 0
                : parseNonNegativeLong(autoCraftInputReserves[inputIndex]);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (detailTab == TAB_AUTO_CRAFT
                && (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)) {
            if (autoCraftOutputLimit.isFocused() && autoCraftOutputLimit.active) {
                menu.requestSetAutoCraftOutputLimit(autoCraftOutputLimitInput());
                autoCraftOutputLimit.setFocused(false);
                return true;
            }
            for (int input = 0; input < autoCraftInputReserves.length; input++) {
                var reserve = autoCraftInputReserves[input];
                if (reserve.isFocused() && reserve.active && reserve.visible) {
                    menu.requestSetAutoCraftInputReserve(input,
                            autoCraftInputReserveInput(input));
                    reserve.setFocused(false);
                    return true;
                }
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void drawBG(GuiGraphics graphics, int x, int y, int mouseX, int mouseY,
            float partialTick) {
        if(ui!=null)ui.background(graphics,font,x,y);
    }

    @Override
    public void drawFG(GuiGraphics graphics, int offsetX, int offsetY, int mouseX, int mouseY) {
        if(ui==null)return;ui.foreground(graphics,font);
        if(detailTab==TAB_AUTO_CRAFT){
            var details=hasSelectedAutoCraftDetails()?selectedAutoCraftPattern():null;if(details!=null){AEStackIcon.draw(graphics,details.getPrimaryOutput(),201,131);
                for(int i=0;i<Math.min(menu.autoCraftInputCount,9);i++){var stack=selectedAutoCraftInput(details,i);if(stack!=null)AEStackIcon.draw(graphics,stack,i%2==0?201:312,173+i/2*18);}}
        }
        if(detailTab==TAB_COLORS)for(int i=0;i<5;i++){
            graphics.fill(245,63+i*21,279,74+i*21,0xFF000000|visualColor(i));
            for(int c=0;c<3;c++)ui.smallText(graphics,Component.literal(Integer.toString((visualColor(i)>>(16-c*8))&255)),310+c*44,70.3f+i*21,16);
        }
        if(detailTab==TAB_OVERVIEW){ui.smallText(graphics,Component.literal("-"),344,195,10);ui.smallText(graphics,Component.literal("-"),372,195,10);ui.smallText(graphics,Component.literal("-"),409,195,8);}
    }

    private void configureLongField(EditBox field, long value, String tooltipKey) {
        field.setMaxLength(19);
        field.setFilter(MolecularCenterScreen::isTargetText);
        field.setValue(Long.toString(Math.max(0, value)));
        field.setTooltip(Tooltip.create(Component.translatable(tooltipKey)));
    }

    private void refreshAutoCraftFields() {
        if (autoCraftOutputLimit == null || autoCraftInputReserves[0] == null) {
            return;
        }
        boolean autoCraftVisible = detailTab == TAB_AUTO_CRAFT;
        boolean selected = hasSelectedAutoCraftDetails();
        autoCraftOutputLimit.visible = autoCraftVisible && selected;
        autoCraftOutputLimit.active = selected;
        if(detailTab==TAB_AUTO_CRAFT){
            limitControl.visible=selected;limitControl.active=selected;
            for(int i=0;i<9;i++){
                reserveControls[i].visible=selected&&i<menu.autoCraftInputCount;reserveControls[i].active=selected&&i<menu.autoCraftInputCount;
                runControls[i].active=menu.getAutoCraftPatternSlots().get(i).hasItem();
            }
        }

        boolean selectedSlotChanged = displayedAutoCraftSlot != menu.autoCraftSelectedSlot;
        if (selectedSlotChanged) {
            displayedAutoCraftSlot = menu.autoCraftSelectedSlot;
            autoCraftOutputLimit.setFocused(false);
            autoCraftOutputLimit.setValue(Long.toString(Math.max(0, menu.autoCraftOutputLimit)));
        }
        syncLongField(autoCraftOutputLimit, menu.autoCraftOutputLimit);
        for (int input = 0; input < autoCraftInputReserves.length; input++) {
            var reserve = autoCraftInputReserves[input];
            boolean inputVisible = autoCraftVisible && selected
                    && input < menu.autoCraftInputCount;
            reserve.visible = inputVisible;
            reserve.active = selected && input < menu.autoCraftInputCount;
            long syncedReserve = menu.getAutoCraftInputReserve(input);
            if (selectedSlotChanged) {
                reserve.setFocused(false);
                reserve.setValue(Long.toString(Math.max(0, syncedReserve)));
            } else {
                syncLongField(reserve, syncedReserve);
            }
        }
    }

    private void refreshAutoCraftIconButtons() {
        if (autoCraftConfigButtons.size() != MolecularAutoCrafter.PATTERN_SLOTS
                || autoCraftToggleButtons.size() != MolecularAutoCrafter.PATTERN_SLOTS) {
            return;
        }
        boolean visible = detailTab == TAB_AUTO_CRAFT;
        for (int slot = 0; slot < MolecularAutoCrafter.PATTERN_SLOTS; slot++) {
            var config = autoCraftConfigButtons.get(slot);
            config.visible = visible;
            config.active = visible;
            config.setSelected(visible && slot == menu.autoCraftSelectedSlot);

            boolean occupied = !menu.getAutoCraftPatternSlots().get(slot).getItem().isEmpty();
            boolean enabled = (menu.autoCraftEnabledMask & (1 << slot)) != 0;
            var toggle = autoCraftToggleButtons.get(slot);
            toggle.visible = visible;
            toggle.active = visible && occupied;
            toggle.setSelected(enabled);
            toggle.setTooltip(Tooltip.create(Component.translatable(
                    enabled
                            ? "gui.molecularmanipulator.auto_craft_stop_slot"
                            : "gui.molecularmanipulator.auto_craft_start_slot",
                    slot + 1)));
        }
    }

    boolean hasSelectedAutoCraftDetails() {
        return menu.autoCraftSelectedSlot >= 0
                && menu.autoCraftState != MolecularAutoCrafter.AutoCraftState.EMPTY
                && selectedAutoCraftPattern() != null;
    }

    private IPatternDetails selectedAutoCraftPattern() {
        ItemStack pattern = menu.getSelectedAutoCraftPatternStack();
        if (pattern.isEmpty() || minecraft == null || minecraft.level == null) {
            return null;
        }
        if (!ItemStack.matches(pattern, cachedPatternStack)) {
            cachedPatternStack = pattern.copy();
            try { cachedPatternDetails = PatternDetailsHelper.decodePattern(pattern, minecraft.level); }
            catch (RuntimeException | LinkageError ignored) { cachedPatternDetails = null; }
        }
        return cachedPatternDetails;
    }

    private static GenericStack selectedAutoCraftInput(IPatternDetails details, int inputIndex) {
        if (details == null || inputIndex < 0 || inputIndex >= details.getInputs().length) {
            return null;
        }
        var input = details.getInputs()[inputIndex];
        if (input == null || input.getPossibleInputs().length == 0) {
            return null;
        }
        return input.getPossibleInputs()[0];
    }

    private static boolean isTargetText(String value) {
        if (value.isEmpty()) {
            return true;
        }
        for (int index = 0; index < value.length(); index++) {
            if (!Character.isDigit(value.charAt(index))) {
                return false;
            }
        }
        return true;
    }

    private static long parseNonNegativeLong(EditBox field) {
        if (field.getValue().isBlank()) {
            return 0;
        }
        try {
            return Math.max(0, Long.parseLong(field.getValue()));
        } catch (NumberFormatException ignored) {
            return Long.MAX_VALUE;
        }
    }

    private static void syncLongField(EditBox field, long value) {
        if (!field.isFocused() && parseNonNegativeLong(field) != value) {
            field.setValue(Long.toString(Math.max(0, value)));
        }
    }

    Component previewLabel() {
        return Component.translatable(MolecularCenterGhostPreview.isShowing(menu.getCenter())
                ? "gui.molecularmanipulator.preview_hide"
                : "gui.molecularmanipulator.preview");
    }


    private static String formatFrequency(long frequency) {
        return Long.toUnsignedString(frequency, 16).toUpperCase(Locale.ROOT);
    }

    int visualColor(int target) {
        return switch (target) {
            case 0 -> menu.fieldColor;
            case 1 -> menu.coreColor;
            case 2 -> menu.primaryRingColor;
            case 3 -> menu.secondaryRingColor;
            case 4 -> menu.latticeColor;
            default -> 0xFFFFFF;
        };
    }

    private static final class CompactCogButton extends AbstractButton {
        private static final int BUTTON_SIZE = 14;
        private static final int ICON_SIZE = 12;

        private final Runnable action;
        private boolean selected;

        private CompactCogButton(int x, int y, Runnable action) {
            super(x, y, BUTTON_SIZE, BUTTON_SIZE, Component.empty());
            this.action = action;
        }

        @Override
        public void onPress() {
            action.run();
        }

        void setSelected(boolean selected) {
            this.selected = selected;
        }

        @Override
        public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY,
                float partialTick) {
            Icon background = isHovered()
                    ? Icon.TOOLBAR_BUTTON_BACKGROUND_HOVER
                    : selected || isFocused()
                            ? Icon.TOOLBAR_BUTTON_BACKGROUND_FOCUS
                            : Icon.TOOLBAR_BUTTON_BACKGROUND;
            float opacity = active ? 1.0F : 0.5F;
            background.getBlitter()
                    .dest(getX(), getY(), BUTTON_SIZE, BUTTON_SIZE)
                    .opacity(opacity)
                    .zOffset(2)
                    .blit(graphics);
            Icon.COG.getBlitter()
                    .dest(getX() + 1, getY() + 1, ICON_SIZE, ICON_SIZE)
                    .opacity(opacity)
                    .zOffset(3)
                    .blit(graphics);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
            defaultButtonNarrationText(narrationElementOutput);
        }
    }
}
