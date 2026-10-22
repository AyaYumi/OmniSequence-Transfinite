package com.atir.molecularmanipulator.client;

import appeng.client.gui.style.ScreenStyle;
import com.atir.molecularmanipulator.blockentity.MatterFabricationBlockEntity;
import com.atir.molecularmanipulator.blockentity.MolecularCenterBlockEntity.QuantumLinkState;
import com.atir.molecularmanipulator.menu.MatterFabricationMenu;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.Locale;
import java.util.List;

/** Fabrication telemetry, quantum construction link and structure-management controls. */
public final class MatterFabricationScreen extends ResponsiveContainerScreen<MatterFabricationMenu> {
    private static final int PURPLE = AeUiTheme.ACCENT;
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
        ui=new OsqUi("fab_overview");imageWidth=ui.layout.width;imageHeight=ui.layout.height;super.init();
        ui.inventory(menu);ui.slots(menu.getSlots(appeng.menu.SlotSemantics.MACHINE_INPUT),"qes");
        buildButton=addScreenWidget(ui.button("build",leftPos,topPos,()->Component.translatable("gui.molecularmanipulator.fabrication.build"),menu::requestBuild));
        updateButton=addScreenWidget(ui.button("build",leftPos,topPos,()->Component.translatable(updateConfirmation.isArmed()
                ?"gui.molecularmanipulator.structure_update_second_confirm":"gui.molecularmanipulator.fabrication.update"),()->{if(updateConfirmation.click())menu.requestStructureUpdate();}));
        previewButton=addScreenWidget(ui.button("preview",leftPos,topPos,()->Component.translatable("gui.molecularmanipulator.fabrication.preview"),()->{MatterFabricationGhostPreview.toggle(menu.getMachine());onClose();}));
        refreshButton=addScreenWidget(ui.button("refresh",leftPos,topPos,()->Component.translatable("gui.molecularmanipulator.fabrication.refresh"),menu::requestRefresh));
        dismantleButton=addScreenWidget(ui.button("dismantle",leftPos,topPos,()->Component.translatable(dismantleArmedAt>0
                ?"gui.molecularmanipulator.fabrication.dismantle_confirm":"gui.molecularmanipulator.fabrication.dismantle"),this::handleDismantleClick));
        ui.state(dismantleButton,()->dismantleArmedAt>0?"confirm":"danger");
        statusTab=addScreenWidget(ui.tab(339.75f,9,26.38f,15,leftPos,topPos,()->Component.translatable("gui.molecularmanipulator.fab.s014"),()->setResearchPage(false)));
        researchTab=addScreenWidget(ui.tab(365.63f,9,26.38f,15,leftPos,topPos,()->Component.translatable("gui.molecularmanipulator.research.research_tab"),()->setResearchPage(true)));
        ui.state(statusTab,()->researchPage?"normal":"active");ui.state(researchTab,()->researchPage?"active":"normal");
        researchPanel=new MatterResearchPanel(menu,font,leftPos,topPos,button->addScreenWidget(button));researchPanel.restoreSelection(selection);
        ui.text("gui.molecularmanipulator.fabrication.state.running",this::stateLabel);
        ui.text("gui.molecularmanipulator.singularity.network_online",()->Component.translatable("gui.molecularmanipulator.fabrication."+(menu.networkOnline?"online":"offline")));
        ui.number(327.36f,63,()->DisplayNumbers.compact(menu.aePerTick));
        ui.number(213,86,()->menu.correctParts+" / "+menu.totalParts);ui.text("gui.molecularmanipulator.fab.s021",this::operationLabel);
        ui.number(334,86,()->"-");
        ui.number(16.5f,188,()->menu.correctParts);ui.number(29.03f,188,()->"");ui.number(59.23f,188,()->"/ "+menu.totalParts);ui.number(69.34f,188,()->"");
        ui.number(212.5f,129,()->menu.progress);ui.number(233.95f,129,()->"/ "+Math.max(0,menu.processingTime)+" t");
        ui.number(367.59f,126,()->Math.round(100*processRatio())+"%");
        ui.number(262.02f,188,()->menu.quantumFrequency==0?"-":Long.toUnsignedString(menu.quantumFrequency,16).toUpperCase(Locale.ROOT));
        ui.text("gui.molecularmanipulator.fab.s026",()->Component.translatable("gui.molecularmanipulator.quantum_state."+menu.quantumLinkState.name().toLowerCase(Locale.ROOT)));
        ui.number(217.36f,244,()->"512");
        ui.width(16.5f,188,40);ui.width(59.23f,188,65);
        ui.width(212.5f,129,20);ui.width(233.95f,129,78);
        ui.width(276,87,55);ui.width(327.36f,63,25);
        ui.color("gui.molecularmanipulator.fab.s026",()->menu.quantumFrequency==0?ui.palette.ink3:
                menu.quantumLinkState==QuantumLinkState.CONNECTED||menu.quantumLinkState==QuantumLinkState.CONNECTED_BUILD_ONLY?ui.palette.ok:ui.palette.warn);
        ui.sprite(285.63f,44,()->ui.sprite("dot_"+(menu.networkOnline?"ok":"mute")));
        ui.sprite(213,59,()->ui.sprite("dot_"+(menu.networkOnline?"ok":"mute")));
        ui.progress(17,194,()->menu.correctParts/(double)Math.max(1,menu.totalParts));
        ui.progress(213,137,this::processRatio);
        ui.at(229.27f,244,()->Component.literal("AE/t · 1 频道 · 仅抽取搭建材料"));
        setResearchPage(researchPage);
    }

    private void setResearchPage(boolean visible) {
        researchPage = visible;
        menu.showResearchPage(visible);
        updatePageWidgets();
    }
    void testPage(boolean research){setResearchPage(research);}
    void testDetails(){setResearchPage(true);researchPanel.showDetails(true);}

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

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        double x = logicalMouseX(mouseX) - leftPos, y = logicalMouseY(mouseY) - topPos;
        List<Component> tooltip = researchPage ? researchPanel.tooltipAt(x, y)
                : x >= 235 && x < 384 && y >= 178 && y < 194
                        ? List.of(Component.translatable("gui.molecularmanipulator.fab.s025").append(": ")
                                .append(menu.quantumFrequency==0?"-":Long.toUnsignedString(menu.quantumFrequency,16).toUpperCase(Locale.ROOT)))
                : x >= 213 && x < 384 && y >= 198 && y < 220
                        ? List.of(Component.translatable("gui.molecularmanipulator.quantum_state."+menu.quantumLinkState.name().toLowerCase(Locale.ROOT)))
                : x >= 20 && x < 152 && y >= 90 && y < 102
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

    private void drawFittedText(GuiGraphics graphics, Component text, int x, int y, int maxWidth, int color) {
        float scale = Math.min(1.0F, maxWidth / (float) Math.max(1, font.width(text)));
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawString(font, text, 0, 0, color, false);
        graphics.pose().popPose();
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

    private int stateColor() {
        return switch (menu.state) {
            case RUNNING -> AeUiTheme.SUCCESS;
            case STRUCTURE_INCOMPLETE, NETWORK_OFFLINE, OUTPUT_BLOCKED,
                    WAITING_POWER, BUILDING, DISMANTLING -> AeUiTheme.WARNING;
            default -> AeUiTheme.MUTED_TEXT;
        };
    }
}
