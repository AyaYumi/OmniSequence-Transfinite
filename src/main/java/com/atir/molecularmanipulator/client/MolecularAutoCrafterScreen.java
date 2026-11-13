package com.atir.molecularmanipulator.client;

import appeng.api.orientation.RelativeSide;
import appeng.client.gui.style.ScreenStyle;
import com.atir.molecularmanipulator.blockentity.MolecularAutoCrafterBlockEntity;
import com.atir.molecularmanipulator.menu.MolecularAutoCrafterMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.*;
import java.util.*;

public final class MolecularAutoCrafterScreen extends ResponsiveContainerScreen<MolecularAutoCrafterMenu> {
    private static final int DIRECTIONS_EXTRA_HEIGHT=14;
    private OsqUi ui;
    private boolean directionsOpen;
    private final List<Button> sideButtons=new ArrayList<>();
    private final Button[] configs=new Button[9],runs=new Button[9];
    private Button directionControl, outputModeControl;
    private Component outputModeTooltip, directionTooltip;
    private final Component[] sideTooltips=new Component[6];
    public MolecularAutoCrafterScreen(MolecularAutoCrafterMenu menu,Inventory inventory,Component title,ScreenStyle style){super(menu,inventory,title,style);}
    @Override protected void init(){
        outputModeTooltip=directionTooltip=null;
        Arrays.fill(sideTooltips,null);
        if(!adjacent())directionsOpen=false;
        ui=new OsqUi("seq_autocrafter");configureDirectionPage();super.init();
        ui.inventory(menu);ui.slots(menu.getPatternSlots(),"res");sideButtons.clear();
        ui.text("block.molecularmanipulator.molecular_auto_crafter",()->directionsOpen?tr("output_directions")
                :menu.getMachine().getBlockState().getBlock().getName());
        for(int i=0;i<9;i++){final int index=i;
            configs[i]=addScreenWidget(ui.button("cfg"+i,leftPos,topPos,()->tr("select_slot",index+1),()->menu.requestConfig(index)));
            runs[i]=addScreenWidget(ui.button("run"+i,leftPos,topPos,()->tr((menu.enabledMask&(1<<index))!=0?"stop_slot":"start_slot",index+1),()->menu.requestToggle(index)));
            ui.state(runs[i],()->(menu.enabledMask&(1<<index))!=0?"active":"normal");
            ui.icon(runs[i],()->(menu.enabledMask&(1<<index))!=0?"pause":"play");
        }
        var help=addScreenWidget(ui.button("tool0",leftPos,topPos,()->tr("help"),this::openGuide));
        help.setTooltip(Tooltip.create(tr("help")));
        outputModeControl=addScreenWidget(ui.button("tool1",leftPos,topPos,this::outputModeLabel,menu::requestOutputModeToggle));
        ui.icon(outputModeControl,()->adjacent()?"out":"net");
        var direction=addScreenWidget(ui.button("tool2",leftPos,topPos,()->tr("output_directions"),this::toggleDirections));
        ui.state(direction,()->directionsOpen?"active":"normal");
        directionControl=direction;
        for(int i=0;i<6;i++){var side=OsqNeighborDirections.OUTPUT_SIDES[i];var b=addScreenWidget(ui.button(OsqNeighborDirections.IDS[i],leftPos,topPos,()->OsqNeighborDirections.label(side),()->menu.requestOutputSideToggle(outputSide(side).get3DDataValue())));
            ui.state(b,()->(menu.outputSides&(1<<outputSide(side).get3DDataValue()))!=0?"active":"normal");
            ui.itemIcon(b,()->OsqNeighborDirections.icon(menu.getMachine().getLevel(),menu.getMachine().getBlockPos(),outputSide(side)));
            sideButtons.add(b);}
        ui.visible=e->{
            if("tip".equals(e.kind))return false;
            if("dirs".equals(e.id))return directionsOpen;
            return !("slots".equals(e.id)||"res".equals(e.id))||!directionsOpen;
        };
        refresh();
    }
    private Direction outputSide(RelativeSide side){return OsqNeighborDirections.worldSide(menu.getMachine().getBlockState(),side);}
    private boolean adjacent(){return menu.outputMode==MolecularAutoCrafterBlockEntity.OutputMode.ADJACENT;}
    private Component outputModeLabel(){return tr("output_mode",tr(adjacent()?"output_adjacent":"output_network"));}
    private void configureDirectionPage(){
        imageWidth=ui.layout.width;imageHeight=ui.layout.height;
        if(!directionsOpen)return;
        // The larger direction card needs its own space, not the inventory's top edge.
        imageHeight+=DIRECTIONS_EXTRA_HEIGHT;
        var window=ui.layout.find("window");
        window.h+=DIRECTIONS_EXTRA_HEIGHT;
        // Shared material samples follow the live geometry; no size-specific texture is required.
        for(var entry:ui.layout.draw)if("inv".equals(entry.id)||entry.external){
            entry.y+=DIRECTIONS_EXTRA_HEIGHT;
        }
    }
    private void toggleDirections(){if(adjacent()){directionsOpen=!directionsOpen;rebuildWidgets();}}
    private void openGuide(){
        OsqGuide.open("molecular_auto_crafter");
    }
    private void refresh(){boolean adjacent=adjacent();
        if(!adjacent&&directionsOpen){directionsOpen=false;rebuildWidgets();return;}
        directionControl.active=adjacent;
        var directionHint=tr(adjacent?"output_directions_hint":"directions_network_hint");
        if(!directionHint.equals(directionTooltip)){directionControl.setTooltip(Tooltip.create(directionHint));directionTooltip=directionHint;}
        var tooltip=outputModeLabel().copy().append("\n").append(tr("output_mode_hint"));
        if(!tooltip.equals(outputModeTooltip)){outputModeControl.setTooltip(Tooltip.create(tooltip));outputModeTooltip=tooltip;}
        for(int i=0;i<sideButtons.size();i++){
            var b=sideButtons.get(i);b.visible=directionsOpen;
            if(!directionsOpen)continue;
            var side=OsqNeighborDirections.OUTPUT_SIDES[i];
            var hint=OsqNeighborDirections.tooltip(menu.getMachine().getLevel(),menu.getMachine().getBlockPos(),side,outputSide(side),menu.outputSides);
            if(!hint.equals(sideTooltips[i])){b.setTooltip(Tooltip.create(hint));sideTooltips[i]=hint;}
        }
        for(int i=0;i<9;i++){boolean present=menu.getPatternSlots().get(i).hasItem();configs[i].active=runs[i].active=present;
            configs[i].visible=runs[i].visible=!directionsOpen;}
        ui.slots(menu.getPatternSlots(),"res");
        if(directionsOpen)for(var slot:menu.getPatternSlots()){slot.x=-1000;slot.y=-1000;}}
    @Override public void containerTick(){super.containerTick();refresh();}
    boolean directionsOpen(){return directionsOpen;}
    @Override public boolean keyPressed(int key,int scan,int mods){if(wirelessPanelOpen())return super.keyPressed(key,scan,mods);if(key==256&&directionsOpen){directionsOpen=false;rebuildWidgets();return true;}return super.keyPressed(key,scan,mods);}
    @Override protected void slotClicked(Slot slot,int id,int button,ClickType type){int i=menu.getPatternSlots().indexOf(slot);if(i>=0&&button==1&&type==ClickType.PICKUP){menu.requestConfig(i);return;}super.slotClicked(slot,id,button,type);}
    @Override public void drawBG(GuiGraphics g,int x,int y,int mx,int my,float pt){if(ui!=null)ui.background(g,font,x,y);}
    @Override public void drawFG(GuiGraphics g,int ox,int oy,int mx,int my){if(ui!=null)ui.foreground(g,font);}
    private static Component tr(String key,Object... args){return OsqUi.tr("gui.molecularmanipulator.auto_craft_"+key,args);}
}
