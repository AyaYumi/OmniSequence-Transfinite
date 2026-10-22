package com.atir.molecularmanipulator.client;

import appeng.api.client.AEKeyRendering;
import appeng.menu.SlotSemantics;
import appeng.client.gui.style.ScreenStyle;
import com.atir.molecularmanipulator.menu.MatterFabricationPortMenu;
import com.atir.molecularmanipulator.blockentity.MatterFabricationPortBlockEntity;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import java.util.*;

public final class MatterFabricationPortScreen extends ResponsiveContainerScreen<MatterFabricationPortMenu> {
    private OsqUi ui;
    private boolean directionsOpen;
    private final List<Button> sides=new ArrayList<>();
    private Button refund,auto;
    private final Component[] sideTooltips=new Component[6];
    public MatterFabricationPortScreen(MatterFabricationPortMenu menu,Inventory inventory,Component title,ScreenStyle style){super(menu,inventory,title,style);}
    @Override protected void init(){
        boolean fluid=menu.getPort().getPortType().isFluid();
        ui=new OsqUi(fluid?"fab_port_fluid":"fab_port_item");
        imageWidth=ui.layout.width;imageHeight=ui.layout.height;super.init();sides.clear();refund=auto=null;
        Arrays.fill(sideTooltips,null);
        var help=addScreenWidget(ui.button("tool0",leftPos,topPos,()->OsqUi.tr("gui.molecularmanipulator.auto_craft_help"),()->OsqGuide.open("matter_fabrication_ports")));
        help.setTooltip(Tooltip.create(OsqUi.tr("gui.molecularmanipulator.auto_craft_help")));
        ui.inventory(menu);ui.slots(menu.getSlots(SlotSemantics.MACHINE_INPUT),"cache");
        ui.slots(menu.getSlots(SlotSemantics.MACHINE_OUTPUT),"cache");
        ui.text(fluid?"block.molecularmanipulator.matter_fabrication_fluid_output":"block.molecularmanipulator.matter_fabrication_item_input",
                ()->menu.getPort().getBlockState().getBlock().getName());
        ui.text("gui.molecularmanipulator.fabrication.port.network_online",()->text(menu.networkOnline?"network_online":"network_offline"));
        if(menu.getPort().getPortType().isInput()) {
            ui.hide(e->e.x<0&&e.y>=56&&e.y<78);
            refund=addScreenWidget(fluid?ui.rect(96,101,80,17,leftPos,topPos,()->text("return_ae"),menu::returnInputs)
                    :ui.button("return",leftPos,topPos,()->text("return_ae"),menu::returnInputs));
            ui.outline(refund);
            refund.setTooltip(Tooltip.create(text("return_ae")));
        }else{
            auto=addScreenWidget(fluid?ui.button("auto",leftPos,topPos,()->text(menu.autoOutput?"auto_on":"auto_off"),menu::toggleAutoOutput)
                    :ui.button("return",leftPos,topPos,()->text(menu.autoOutput?"auto_on":"auto_off"),menu::toggleAutoOutput));
            ui.outline(auto);
            var direction=addScreenWidget(ui.button("tool1",leftPos,topPos,()->text("directions"),this::toggleDirections));
            direction.setTooltip(Tooltip.create(OsqUi.tr("gui.molecularmanipulator.auto_craft_output_directions_hint")));
            ui.state(direction,()->directionsOpen?"active":"normal");
            for(int i=0;i<6;i++){
                var dir=OsqNeighborDirections.SIDES[i];
                Button b=ui.button(OsqNeighborDirections.IDS[i],leftPos,topPos,()->text("side."+dir.getName()),()->menu.toggleOutputSide(dir.get3DDataValue()));
                ui.state(b,()->(menu.outputSides&(1<<dir.get3DDataValue()))!=0?"active":"normal");
                ui.itemIcon(b,()->OsqNeighborDirections.icon(menu.getPort().getLevel(),menu.getPort().getBlockPos(),dir));
                sides.add(addScreenWidget(b));
            }
        }
        ui.visible=e->{
            if("tip".equals(e.kind))return false;
            if(Arrays.asList(OsqNeighborDirections.IDS).contains(e.id))return directionsOpen;
            if("dirs".equals(e.id)
                    ||"gui.molecularmanipulator.auto_craft_output_directions".equals(e.lang)
                    ||"gui.molecularmanipulator.seq.s002".equals(e.lang))return directionsOpen;
            float y="text".equals(e.type)?e.baseline:e.y;
            return !directionsOpen||y<40||y>=124;
        };
        ui.color("gui.molecularmanipulator.fabrication.port.network_online",()->menu.networkOnline?ui.palette.ok:ui.palette.ink3);
        ui.color("gui.molecularmanipulator.seq.s002",()->ui.palette.ink3);
        ui.sprite(12,30,()->ui.sprite("dot_"+(menu.networkOnline?"ok":"mute")));
        ui.number(fluid?96:92,69,()->menu.occupied);
        ui.number(fluid?106.52f:102.77f,69,()->fluid?"/ 4":"/ 16");
        ui.number(142,66,()->OsqUi.compact(menu.totalAmount));
        ui.number(96,89,()->String.format(Locale.ROOT,"%,d",MatterFabricationPortBlockEntity.FLUID_CAPACITY));
        ui.omit("gui.molecularmanipulator.fab.s031");
        refresh();
    }
    private void toggleDirections(){directionsOpen=!directionsOpen;refresh();}
    private void refresh(){
        if(menu.getPort().getPortType().isInput())directionsOpen=false;
        if(refund!=null){refund.active=menu.networkOnline&&menu.totalAmount>0;refund.visible=!directionsOpen;}
        if(auto!=null)auto.visible=!directionsOpen;
        ui.slots(menu.getSlots(SlotSemantics.MACHINE_INPUT),"cache");
        ui.slots(menu.getSlots(SlotSemantics.MACHINE_OUTPUT),"cache");
        if(directionsOpen)for(var slot:menu.getSlots(SlotSemantics.MACHINE_OUTPUT)){slot.x=-1000;slot.y=-1000;}
        for(int i=0;i<sides.size();i++){
            var b=sides.get(i);b.visible=directionsOpen;if(!directionsOpen)continue;
            var hint=OsqNeighborDirections.tooltip(menu.getPort().getLevel(),menu.getPort().getBlockPos(),OsqNeighborDirections.SIDES[i],menu.outputSides);
            if(!hint.equals(sideTooltips[i])){b.setTooltip(Tooltip.create(hint));sideTooltips[i]=hint;}
        }
    }
    @Override public void containerTick(){super.containerTick();refresh();}
    boolean directionsOpen(){return directionsOpen;}
    @Override public boolean keyPressed(int key,int scan,int mods){if(key==256&&directionsOpen){directionsOpen=false;refresh();return true;}return super.keyPressed(key,scan,mods);}
    @Override public void drawBG(GuiGraphics g,int x,int y,int mx,int my,float pt){if(ui!=null)ui.background(g,font,x,y);}
    @Override public void drawFG(GuiGraphics g,int ox,int oy,int mx,int my){
        if(ui==null)return;ui.foreground(g,font);
        if(directionsOpen)return;
        if(menu.getPort().getPortType().isFluid())for(int i=0;i<4;i++){
            int x=tankX(i),y=tankY(i);var stack=menu.getTankStack(i);
            if(stack!=null)AEKeyRendering.drawInGui(minecraft,g,x+9,y+4,stack.what());
            ui.smallText(g,Component.literal(stack==null?"0":OsqUi.compact(stack.amount())),x+3,y+29,28);
        }
    }
    @Override public boolean mouseClicked(double x,double y,int button){
        if(!directionsOpen&&menu.getPort().getPortType().isFluid()&&(button==0||button==1)){
            int tank=tankAt(logicalMouseX(x)-leftPos,logicalMouseY(y)-topPos);
            if(tank>=0){if(button==1)menu.interactFluid(tank);else menu.requestFluidTransfer(tank);return true;}
        }return super.mouseClicked(x,y,button);
    }
    @Override public void render(GuiGraphics g,int mx,int my,float pt){super.render(g,mx,my,pt);
        if(directionsOpen||!menu.getPort().getPortType().isFluid())return;int i=tankAt(logicalMouseX(mx)-leftPos,logicalMouseY(my)-topPos);if(i<0)return;
        var s=menu.getTankStack(i);g.renderComponentTooltip(font,List.of(s==null?text("empty_tank"):s.what().getDisplayName(),
                Component.literal((s==null?0:s.amount())+" / "+MatterFabricationPortBlockEntity.FLUID_CAPACITY+" mB"),text("fluid_click")),mx,my);
    }
    private static int tankX(int i){return 14+i%2*38;} private static int tankY(int i){return 46+i/2*38;}
    private static int tankAt(double x,double y){for(int i=0;i<4;i++)if(x>=tankX(i)&&x<tankX(i)+34&&y>=tankY(i)&&y<tankY(i)+34)return i;return -1;}
    private static Component text(String key,Object...args){return OsqUi.tr("gui.molecularmanipulator.fabrication.port."+key,args);}
}
