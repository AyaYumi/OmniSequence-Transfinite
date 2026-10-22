package com.atir.molecularmanipulator.client;

import appeng.client.gui.style.ScreenStyle;
import appeng.menu.SlotSemantics;
import com.atir.molecularmanipulator.menu.OmniComputationMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import java.util.Locale;

/** Supplied light/dark layout, live AE2 data and server-authoritative actions. */
public final class OmniComputationScreen extends ResponsiveContainerScreen<OmniComputationMenu> {
    private OsqUi ui;
    private boolean legacyLayout;
    private Button build, dismantle, update, keep;
    private final StructureUpdateConfirmation confirmation = new StructureUpdateConfirmation();
    private long dismantleArmedAt;
    public OmniComputationScreen(OmniComputationMenu menu, Inventory inventory, Component title, ScreenStyle style) {
        super(menu, inventory, title, style);
    }
    @Override protected void init() {
        legacyLayout=menu.legacyStructure;
        ui=new OsqUi(legacyLayout ? "omni_core_legacy" : "omni_core");
        imageWidth=ui.layout.width; imageHeight=ui.layout.height;
        super.init(); ui.inventory(menu); ui.slots(menu.getSlots(SlotSemantics.MACHINE_INPUT),"qes");
        addScreenWidget(ui.button("projection",leftPos,topPos,
                () -> tr("projection"), () -> { confirmation.cancel(); OmniComputationGhostPreview.toggle(menu.getCore()); }));
        addScreenWidget(ui.button("refresh",leftPos,topPos,() -> tr("refresh"),menu::requestRefresh));
        if(legacyLayout) {
            update=addScreenWidget(ui.button("update",leftPos,topPos,() -> OsqUi.tr(confirmation.isArmed()
                    ? "gui.molecularmanipulator.structure_update_second_confirm" : "gui.molecularmanipulator.structure_update_confirm"),
                    () -> {if(confirmation.click()) menu.requestStructureUpdate();}));
            keep=addScreenWidget(ui.button("keep",leftPos,topPos,
                    () -> OsqUi.tr("gui.molecularmanipulator.structure_update_keep_legacy"),
                    () -> { confirmation.cancel();menu.requestKeepLegacyStructure(); }));
        } else {
            build=addScreenWidget(ui.button("build",leftPos,topPos,() -> tr(menu.building?"building":"build"),menu::requestBuild));
            dismantle=addScreenWidget(ui.button("dismantle",leftPos,topPos,
                    () -> dismantleArmedAt>0 ? OsqUi.tr("gui.molecularmanipulator.fabrication.dismantle_confirm") : tr("dismantle"), () -> {
                long now=net.minecraft.Util.getMillis();
                if(dismantleArmedAt==0){dismantleArmedAt=now;return;}
                if(now-dismantleArmedAt>=250){menu.requestDismantle();dismantleArmedAt=0;}
            }));
            ui.state(dismantle,()->dismantleArmedAt>0?"confirm":"danger");
        }
        ui.text("gui.molecularmanipulator.omni.formed",()->tr(menu.formed?"formed":"incomplete"));
        ui.text("gui.molecularmanipulator.omni.online",()->tr(menu.networkOnline?"online":"offline"));
        ui.text("gui.molecularmanipulator.omni.calculating",()->menu.activeMaterialCalculations>0
                ?tr("calculating",menu.activeMaterialCalculations):tr("calculation_idle"));
        ui.text("gui.molecularmanipulator.omni.calculation_stats",()->tr("calculation_stats",menu.completedMaterialCalculations,menu.lastMaterialCalculationMillis));
        ui.text("gui.molecularmanipulator.quantum_state.connected",()->OsqUi.tr("gui.molecularmanipulator.quantum_state."+menu.quantumLinkState.name().toLowerCase(Locale.ROOT)));
        ui.number(252.39f,238.5f,()->menu.quantumFrequency==0?"-":Long.toUnsignedString(menu.quantumFrequency,16).toUpperCase(Locale.ROOT));
        ui.number(252,147,()->menu.activeJobs);ui.number(314,147,()->menu.cpuLanes);
        ui.number(17,57,()->legacyLayout?"31×31×39 · 1.3.9":"65×35×65 · "+menu.missingParts);
        ui.number(16.5f,187,()->menu.correctParts);
        if(legacyLayout){ui.number(24.63f,187,()->"/ "+menu.totalParts);ui.number(34.64f,187,()->"");
            ui.layout.draw.stream().filter(e->"text".equals(e.type)&&Math.abs(e.x-24.63f)<.8f&&Math.abs(e.baseline-187)<.8f)
                    .findFirst().ifPresent(e->{e.x=76;e.w=54;});}
        else {ui.number(28.48f,187,()->"");ui.number(60.05f,187,()->"/ "+menu.totalParts);ui.number(70.06f,187,()->"");}
        ui.number(177.53f,185.5f,()->"-");
        ui.width(16.5f,187,legacyLayout?56:41);
        ui.width(60.05f,187,30);
        ui.width(233,224.89f,135);
        ui.sprite(17,43,()->ui.sprite("dot_"+(menu.formed?"ok":"warn")));
        ui.sprite(296.83f,56,()->ui.sprite("dot_"+(menu.networkOnline?"ok":"mute")));
        ui.sprite(226,222.89f,()->ui.sprite("dot_"+(menu.quantumFrequency==0?"mute":"ok")));
        ui.color("gui.molecularmanipulator.quantum_state.connected",()->menu.quantumFrequency==0?ui.palette.ink3:ui.palette.ok);
        if(legacyLayout)ui.number(230,221,()->"");
        refresh();
    }
    private Component tr(String key,Object...args){return OsqUi.tr("gui.molecularmanipulator.omni."+key,args);}
    private void refresh(){
        boolean busy=menu.building||menu.dismantling;
        confirmation.tick(legacyLayout&&!busy);
        if(update!=null)update.active=!busy;
        if(keep!=null)keep.active=!busy;
        if(build!=null)build.active=!busy&&!menu.formed&&menu.conflictParts==0;
        if(dismantle!=null)dismantle.active=!busy&&menu.dismantlableBlocks>0;
        if(dismantleArmedAt>0&&net.minecraft.Util.getMillis()-dismantleArmedAt>3000)dismantleArmedAt=0;
    }
    @Override public void containerTick(){super.containerTick();if(legacyLayout!=menu.legacyStructure){rebuildWidgets();return;}refresh();}
    @Override public void drawBG(GuiGraphics g,int x,int y,int mx,int my,float pt){if(ui!=null)ui.background(g,font,x,y);}
    @Override public void drawFG(GuiGraphics g,int x,int y,int mx,int my){if(ui!=null)ui.foreground(g,font);}
}
