package com.atir.molecularmanipulator.client;

import appeng.client.gui.StackWithBounds;
import appeng.client.gui.style.ScreenStyle;
import com.atir.molecularmanipulator.blockentity.MolecularCenterBlockEntity.QuantumLinkState;
import com.atir.molecularmanipulator.menu.MatterFabricationMenu;
import net.minecraft.Util;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.Locale;
import java.util.List;

/** Fabrication telemetry, quantum construction link and structure-management controls. */
public final class MatterFabricationScreen extends ResponsiveContainerScreen<MatterFabricationMenu> {
    private static final int CYAN = AeUiTheme.CYAN;
    private static final long DISMANTLE_CONFIRM_MILLIS = 3_000L;
    private static final long DISMANTLE_DEBOUNCE_MILLIS = 250L;

    private Button buildButton;
    private Button updateButton;
    private Button dismantleButton;
    private Button previewButton;
    private Button refreshButton;
    private Button statusTab;
    private Button researchTab;
    private MatterResearchPanel researchPanel;
    private boolean researchPage;
    private long dismantleArmedAt;
    private OsqUi ui;
    private final StructureUpdateConfirmation updateConfirmation=new StructureUpdateConfirmation();

    public MatterFabricationScreen(MatterFabricationMenu menu, Inventory inventory, Component title,
            ScreenStyle style) {
        super(menu, inventory, title, style);
    }

    @Override
    protected void init() {
        var selection=researchPanel==null?null:researchPanel.selection();
        ui=new OsqUi("fab_overview");imageWidth=ui.layout.width;imageHeight=ui.layout.height+24;super.init();
        // A fitted footer keeps the effect control clear of both research tabs and native inventory slots.
        addScreenWidget(new MachineEffectButton(ui, ui.buttonBox(imageWidth - 128, ui.layout.height + 3, 120, 18), leftPos, topPos,
                () -> menu.dynamicEffectOverride, menu::requestDynamicEffectLevel));
        ui.inventory(menu);ui.slots(menu.getSlots(appeng.menu.SlotSemantics.MACHINE_INPUT),"qes");
        buildButton=addScreenWidget(ui.button("build",leftPos,topPos,()->Component.translatable("gui.molecularmanipulator.fabrication.build"),menu::requestBuild));
        updateButton=addScreenWidget(ui.button("build",leftPos,topPos,()->Component.translatable(updateConfirmation.isArmed()
                ?"gui.molecularmanipulator.structure_update_second_confirm":"gui.molecularmanipulator.fabrication.update"),()->{if(updateConfirmation.click())menu.requestStructureUpdate();}));
        previewButton=addScreenWidget(ui.button("preview",leftPos,topPos,()->Component.translatable("gui.molecularmanipulator.fabrication.preview"),()->{MatterFabricationGhostPreview.toggle(menu.getMachine());onClose();}));
        refreshButton=addScreenWidget(ui.button("refresh",leftPos,topPos,()->Component.translatable("gui.molecularmanipulator.fabrication.refresh"),menu::requestRefresh));
        dismantleButton=addScreenWidget(ui.button("dismantle",leftPos,topPos,()->Component.translatable(dismantleArmedAt>0
                ?"gui.molecularmanipulator.fabrication.dismantle_confirm":"gui.molecularmanipulator.fabrication.dismantle"),this::handleDismantleClick));
        ui.state(dismantleButton,()->dismantleArmedAt>0?"confirm":"danger");
        statusTab=addScreenWidget(ui.tab("tab0",leftPos,topPos,()->Component.translatable("gui.molecularmanipulator.fab.s014"),()->!researchPage,()->setResearchPage(false)));
        researchTab=addScreenWidget(ui.tab("tab1",leftPos,topPos,()->Component.translatable("gui.molecularmanipulator.research.research_tab"),()->researchPage,()->setResearchPage(true)));
        researchPanel=new MatterResearchPanel(menu,font,leftPos,topPos,button->addScreenWidget(button));researchPanel.restoreSelection(selection);
        ui.text("gui.molecularmanipulator.fabrication.state.running",this::stateLabel);
        ui.text("gui.molecularmanipulator.singularity.network_online",()->Component.translatable("gui.molecularmanipulator.fabrication."+(menu.networkOnline?"online":"offline")));
        ui.number(327.36f,63,()->DisplayNumbers.compact(menu.aePerTick));
        ui.number(213,86,()->menu.correctParts+" / "+menu.totalParts);ui.text("gui.molecularmanipulator.fab.s021",this::operationLabel);
        ui.number(334,86,()->"-");
        ui.number(16.5f,116,()->menu.correctParts);ui.number(29.03f,116,()->"");ui.number(59.23f,116,()->"/ "+menu.totalParts);ui.number(69.34f,116,()->"");
        ui.number(212.5f,129,()->menu.progress);ui.number(233.95f,129,()->"/ "+Math.max(0,menu.processingTime)+" t");
        ui.number(367.59f,126,()->Math.round(100*processRatio())+"%");
        ui.number(66.02f,168,()->menu.quantumFrequency==0?"-":Long.toUnsignedString(menu.quantumFrequency,16).toUpperCase(Locale.ROOT));
        ui.text("gui.molecularmanipulator.fab.s026",()->Component.translatable("gui.molecularmanipulator.quantum_state."+menu.quantumLinkState.name().toLowerCase(Locale.ROOT)));
        ui.number(226.36f,188,()->"512");
        ui.width(16.5f,116,40);ui.width(59.23f,116,65);
        ui.width(212.5f,129,20);ui.width(233.95f,129,78);
        ui.width(276,87,174);ui.width(327.36f,63,25);
        ui.color("gui.molecularmanipulator.fab.s026",()->menu.quantumFrequency==0?ui.palette.ink3:
                menu.quantumLinkState==QuantumLinkState.CONNECTED||menu.quantumLinkState==QuantumLinkState.CONNECTED_BUILD_ONLY?ui.palette.ok:ui.palette.warn);
        ui.sprite(285.63f,44,()->ui.sprite("dot_"+(menu.networkOnline?"ok":"mute")));
        ui.sprite(213,61,()->ui.sprite("dot_"+(menu.networkOnline?"ok":"mute")));
        ui.progress(17,122,()->menu.correctParts/(double)Math.max(1,menu.totalParts));
        ui.progress(213,151,this::processRatio);
        ui.at(238.27f,188,()->Component.translatable("gui.molecularmanipulator.fabrication.link_details"));
        setResearchPage(researchPage);
    }

    private void setResearchPage(boolean visible) {
        researchPage = visible;
        menu.showResearchPage(visible);
        updatePageWidgets();
    }
    private void updatePageWidgets() {
        buildButton.visible = !researchPage && !menu.legacyStructure;
        updateButton.visible = !researchPage && menu.legacyStructure;
        previewButton.visible = refreshButton.visible = dismantleButton.visible = !researchPage;
        statusTab.active = researchTab.active = true;
        researchPanel.update(researchPage);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (researchPanel != null && researchPanel.scroll(logicalMouseX(mouseX) - leftPos,
                logicalMouseY(mouseY) - topPos, scrollY)) return true;
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    /** Research rows are drawn outside native slots; expose their visible screen area to JEI. */
    public StackWithBounds researchIngredientAt(double mouseX,double mouseY) {
        if(!researchPage||researchPanel==null)return null;
        var ingredient=researchPanel.ingredientAt(logicalMouseX(mouseX)-leftPos,logicalMouseY(mouseY)-topPos);
        if(ingredient==null)return null;
        var area=ingredient.bounds();
        return new StackWithBounds(ingredient.stack(),responsiveArea(
                new Rect2i(leftPos+area.getX(),topPos+area.getY(),area.getWidth(),area.getHeight())));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        double x = logicalMouseX(mouseX) - leftPos, y = logicalMouseY(mouseY) - topPos;
        List<Component> tooltip = researchPage ? researchPanel.tooltipAt(x, y)
                : x >= 39 && x < 190 && y >= 196 && y < 209
                        ? List.of(Component.translatable("gui.molecularmanipulator.fab.s025").append(": ")
                                .append(menu.quantumFrequency==0?"-":Long.toUnsignedString(menu.quantumFrequency,16).toUpperCase(Locale.ROOT)))
                : x >= 17 && x < 190 && y >= 220 && y < 238
                        ? List.of(Component.translatable("gui.molecularmanipulator.quantum_state."+menu.quantumLinkState.name().toLowerCase(Locale.ROOT)))
                : x >= 213 && x < 384 && y >= 55 && y < 71
                        ? List.of(Component.translatable("gui.molecularmanipulator.fabrication.power", DisplayNumbers.exact(menu.aePerTick)))
                        : List.of();
        if (!tooltip.isEmpty()) graphics.renderComponentTooltip(font, tooltip, mouseX, mouseY);
    }

    @Override
    public void containerTick() {
        super.containerTick();
        boolean operationActive = menu.building || menu.dismantling;
        updateConfirmation.tick(menu.legacyStructure&&!operationActive);
        if (buildButton != null) {
            buildButton.active = !operationActive && !menu.updatingStructure;
        }
        if (updateButton != null) {
            updateButton.active = !operationActive;
        }
        if (dismantleButton != null) {
            dismantleButton.active = !operationActive && !menu.updatingStructure;
        }
        if (dismantleArmedAt > 0
                && Util.getMillis() - dismantleArmedAt > DISMANTLE_CONFIRM_MILLIS) {
            disarmDismantle();
        }
        updatePageWidgets();
    }

    private void handleDismantleClick() {
        long now = Util.getMillis();
        if (dismantleArmedAt == 0
                || now - dismantleArmedAt > DISMANTLE_CONFIRM_MILLIS) {
            dismantleArmedAt = now;
            dismantleButton.setMessage(Component.translatable(
                    "gui.molecularmanipulator.fabrication.dismantle_confirm"));
            return;
        }
        if (now - dismantleArmedAt >= DISMANTLE_DEBOUNCE_MILLIS) {
            menu.requestDismantle();
            disarmDismantle();
        }
    }

    private void disarmDismantle() {
        dismantleArmedAt = 0;
        if (dismantleButton != null) {
            dismantleButton.setMessage(Component.translatable(
                    "gui.molecularmanipulator.fabrication.dismantle"));
        }
    }

    @Override
    public void drawBG(GuiGraphics graphics, int x, int y, int mouseX, int mouseY,
            float partialTick) {
        if(ui==null)return;
        if(researchPage)researchPanel.drawBackground(graphics,x,y);else ui.background(graphics,font,x,y);
    }

    @Override
    public void drawFG(GuiGraphics graphics, int offsetX, int offsetY, int mouseX, int mouseY) {
        if(ui==null)return;
        if(researchPage){researchPanel.drawForeground(graphics);return;}
        ui.foreground(graphics,font);
    }

    private float processRatio(){return menu.processingTime<=0?0:Math.min(1,menu.progress/(float)menu.processingTime);}

    private Component operationLabel() {
        if (menu.updatingStructure) {
            return Component.translatable(
                    "gui.molecularmanipulator.fabrication.operation_update",
                    menu.operationProgress, menu.operationTotal);
        }
        if (menu.building) {
            return Component.translatable("gui.molecularmanipulator.fabrication.operation_build",
                    menu.operationProgress, menu.operationTotal);
        }
        if (menu.dismantling) {
            return Component.translatable("gui.molecularmanipulator.fabrication.operation_dismantle",
                    menu.operationProgress, menu.operationTotal);
        }
        return Component.translatable("gui.molecularmanipulator.fabrication.operation_idle");
    }

    private Component stateLabel() {
        return Component.translatable("gui.molecularmanipulator.fabrication.state."
                + menu.state.name().toLowerCase(Locale.ROOT));
    }
}
