package com.atir.molecularmanipulator.client;

import appeng.api.client.AEKeyRendering;
import appeng.api.stacks.AEFluidKey;
import appeng.client.gui.style.ScreenStyle;
import appeng.menu.SlotSemantics;
import com.atir.molecularmanipulator.menu.MatterFabricationPatternAssemblyMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import java.util.List;

/** Supplied layout with rename drafts, server-backed tab changes and cache paging. */
public final class MatterFabricationPatternAssemblyScreen extends ResponsiveContainerScreen<MatterFabricationPatternAssemblyMenu> {
    private OsqUi ui;
    private EditBox nameField;
    private int displayedView=-1;
    private boolean initializedName;
    private Button previous,next,refund;
    public MatterFabricationPatternAssemblyScreen(MatterFabricationPatternAssemblyMenu menu,Inventory inventory,Component title,ScreenStyle style){super(menu,inventory,title,style);}
    @Override protected void init(){
        String draft=nameField==null?null:nameField.getValue();displayedView=menu.view;
        ui=new OsqUi(menu.view==3?"fab_assembly_catalyst":menu.view==1||menu.view==2?"fab_assembly_input":"fab_assembly_patterns");
        imageWidth=ui.layout.width;imageHeight=ui.layout.height;super.init();
        ui.inventory(menu);ui.slots(menu.getSlots(SlotSemantics.ENCODED_PATTERN),"pat");
        ui.slots(menu.getSlots(SlotSemantics.MACHINE_INPUT),menu.view==3?"cat":"pat");ui.slots(menu.getSlots(SlotSemantics.ENCODED_PATTERN),"pat");
        nameField=addScreenWidget(ui.field("name",leftPos,topPos,font,text("name_hint")));
        if(draft!=null)nameField.setValue(draft);
        else if(!menu.assemblyName.isBlank()){nameField.setValue(menu.assemblyName);initializedName=true;}
        addScreenWidget(ui.button("save",leftPos,topPos,()->text("rename"),this::saveName));
        String[] keys={"patterns","input_tab","output_tab","catalysts"};
        for(int i=0;i<4;i++){final int selected=i;
            var b=addScreenWidget(ui.tab(71.81f+i*25.88f,62,26.38f,15,leftPos,topPos,()->buffer(keys[selected]),()->menu.requestView(selected)));
            ui.state(b,()->menu.view==selected?"active":"normal");
        }
        if(menu.view==1||menu.view==2){
            previous=addScreenWidget(ui.button("prev",leftPos,topPos,()->Component.literal("<"),()->menu.requestPage(menu.bufferState.page()-1)));
            next=addScreenWidget(ui.button("next",leftPos,topPos,()->Component.literal(">"),()->menu.requestPage(menu.bufferState.page()+1)));
            ui.text("gui.molecularmanipulator.page",()->OsqUi.tr("gui.molecularmanipulator.page",menu.bufferState.page()+1,menu.bufferState.pages()));
            for(var e:ui.layout.draw)if("text".equals(e.type)&&e.baseline>=98&&e.baseline<180)ui.at(e.x,e.baseline,Component::empty);
            ui.hide(e -> "slot".equals(e.kind) && "buf".equals(e.id));
            // The source's per-row extract controls have no corresponding server operation.
            // Do not fabricate one or attach a bulk refund to a single-item button.
            ui.visible=e->!("ctl".equals(e.kind)&&e.id!=null&&e.id.startsWith("take"))
                    &&!(e.sprite!=null&&e.sprite.contains("/icons/")&&e.x>=160&&e.y>=95&&e.y<178)
                    && !("tip".equals(e.kind));
        }
        if(menu.view!=3)refund=addScreenWidget(ui.button("return",leftPos,topPos,()->buffer("refund"),menu::requestRefund));
        ui.omit("gui.molecularmanipulator.fab.s001");
        ui.text("gui.molecularmanipulator.fabrication.buffer.network_online",()->buffer(menu.networkOnline?"network_online":"network_offline"));
        ui.number(160.42f,22,()->menu.occupied);ui.number(170.19f,22,()->"/ 36");
        ui.text("gui.molecularmanipulator.fabrication.buffer.queued",
                ()->OsqUi.tr("gui.molecularmanipulator.fabrication.buffer.queued",menu.bufferState.queuedPatterns()));
        ui.text("gui.molecularmanipulator.fabrication.buffer.catalyst_types",
                ()->OsqUi.tr("gui.molecularmanipulator.fabrication.buffer.catalyst_types",menu.sharedCatalystTypes));
        ui.text("gui.molecularmanipulator.fab.s010",()->buffer(menu.view==2?"output_tab":"input_tab"));
        refresh();
    }
    private void refresh(){menu.updatePatternSlots();
        if(previous!=null){previous.active=menu.bufferState.page()>0;next.active=menu.bufferState.page()+1<menu.bufferState.pages();}
        if(refund!=null)refund.active=menu.canRefund;
    }
    private void saveName(){menu.requestRename(nameField.getValue());nameField.setFocused(false);}
    @Override public void containerTick(){super.containerTick();if(displayedView!=menu.view){previous=next=refund=null;rebuildWidgets();return;}
        if(!initializedName&&!menu.assemblyName.isBlank()&&!nameField.isFocused()){nameField.setValue(menu.assemblyName);initializedName=true;}refresh();}
    @Override public boolean keyPressed(int key,int scan,int mods){if(nameField.isFocused()&&(key==257||key==335)){saveName();return true;}return super.keyPressed(key,scan,mods);}
    @Override public void drawBG(GuiGraphics g,int x,int y,int mx,int my,float pt){if(ui==null)return;
        centerMetadata("assembly_capacity", "assembly_capacity_icon", buffer("capacity"));
        centerMetadata("assembly_queue", "assembly_queue_icon",
                OsqUi.tr("gui.molecularmanipulator.fabrication.buffer.queued",menu.bufferState.queuedPatterns()));
        centerMetadata("assembly_catalyst_count", "assembly_catalyst_count_icon",
                OsqUi.tr("gui.molecularmanipulator.fabrication.buffer.catalyst_types",menu.sharedCatalystTypes));
        ui.background(g,font,x,y);
    }
    @Override public void drawFG(GuiGraphics g,int ox,int oy,int mx,int my){if(ui==null)return;ui.foreground(g,font);
        if(menu.view!=1&&menu.view!=2)return;
        var state=menu.bufferState;
        if(state.unavailable()||state.contents().isEmpty()){
            ui.centeredText(g,buffer(state.unavailable()?"unavailable":"empty"),14,135,162,ui.palette.ink3);
            return;
        }
        for(int i=0;i<Math.min(com.atir.molecularmanipulator.menu.MatterPatternBufferMenuState.PAGE_SIZE,state.contents().size());i++){
            var stack=state.contents().get(i);float y=98+i*19.5f;
            ui.slotBackground(g,14,y);
            AEKeyRendering.drawInGui(minecraft,g,15,Math.round(y)+1,stack.what());
            ui.smallText(g,stack.what().getDisplayName(),37,y+8,139);
            ui.smallText(g,Component.literal(OsqUi.compact(stack.amount())+(stack.what() instanceof AEFluidKey?" mB":"")),37,y+16.5f,139);
        }
    }
    /** Center each live icon/label group inside the left metadata column, not its example string. */
    private void centerMetadata(String textId, String iconId, Component value) {
        var label=ui.layout.find(textId);var icon=ui.layout.find(iconId);
        if(label==null||icon==null)return;
        float textWidth=Math.min(101,com.atir.molecularmanipulator.client.gui.theme.OsqFonts.widthF(
                font,value.getString(),com.atir.molecularmanipulator.client.gui.theme.OsqFonts.id(label.fontId)));
        float groupWidth=icon.w+3+textWidth;
        icon.x=8+(110-groupWidth)/2;
        icon.y=label.centerY-icon.h/2;
        label.x=icon.x+icon.w+3;
        label.w=textWidth;
    }
    @Override public boolean mouseScrolled(double mx,double my,double sx,double sy){
        double x=logicalMouseX(mx)-leftPos,y=logicalMouseY(my)-topPos;
        if((menu.view==1||menu.view==2)&&x>=8&&x<182&&y>=98&&y<178&&sy!=0){menu.requestPage(menu.bufferState.page()+(sy<0?1:-1));return true;}
        return super.mouseScrolled(mx,my,sx,sy);
    }
    @Override public void render(GuiGraphics g,int mx,int my,float pt){super.render(g,mx,my,pt);
        double x=logicalMouseX(mx)-leftPos,y=logicalMouseY(my)-topPos;
        if((menu.view==1||menu.view==2)&&!menu.bufferState.unavailable()&&x>=14&&x<176&&y>=98&&y<176){int row=(int)((y-98)/19.5);
            if(row<menu.bufferState.contents().size()){var s=menu.bufferState.contents().get(row);g.renderComponentTooltip(font,List.of(s.what().getDisplayName(),Component.literal(DisplayNumbers.exact(s.amount()))),mx,my);}}
    }
    private static Component buffer(String key){return OsqUi.tr("gui.molecularmanipulator.fabrication.buffer."+key);}
    private static Component text(String key){return OsqUi.tr("gui.molecularmanipulator.fabrication.pattern."+key);}
}
