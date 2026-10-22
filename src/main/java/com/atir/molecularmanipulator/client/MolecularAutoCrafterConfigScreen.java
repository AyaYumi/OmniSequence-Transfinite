package com.atir.molecularmanipulator.client;

import appeng.api.crafting.*;
import appeng.api.stacks.GenericStack;
import appeng.client.gui.style.ScreenStyle;
import appeng.menu.SlotSemantics;
import com.atir.molecularmanipulator.blockentity.MolecularAutoCrafter;
import com.atir.molecularmanipulator.menu.MolecularAutoCrafterConfigMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import java.util.Locale;
import java.util.stream.IntStream;

public final class MolecularAutoCrafterConfigScreen extends ResponsiveContainerScreen<MolecularAutoCrafterConfigMenu> {
    private static final int PAGE_SIZE=5;
    private OsqUi ui;
    private final AutoCrafterAmountDraft limitDraft=new AutoCrafterAmountDraft();
    private final AutoCrafterAmountDraft[] drafts=IntStream.range(0,MolecularAutoCrafter.MAX_INPUTS).mapToObj(i->new AutoCrafterAmountDraft()).toArray(AutoCrafterAmountDraft[]::new);
    private EditBox limit;
    private final EditBox[] fields=new EditBox[MolecularAutoCrafter.MAX_INPUTS];
    private Button limitApply,save;
    private final Button[] apply=new Button[MolecularAutoCrafter.MAX_INPUTS];
    private int page;
    private ItemStack decoded=ItemStack.EMPTY;
    private IPatternDetails pattern;
    public MolecularAutoCrafterConfigScreen(MolecularAutoCrafterConfigMenu menu,Inventory inventory,Component title,ScreenStyle style){super(menu,inventory,title,style);}
    @Override protected void init(){
        ui=new OsqUi("seq_autocrafter_config");imageWidth=ui.layout.width;imageHeight=ui.layout.height;super.init();sync();
        menu.getSlots(SlotSemantics.ENCODED_PATTERN).forEach(slot->{slot.x=-1000;slot.y=-1000;});
        limit=addScreenWidget(amountField("limit",limitDraft));
        limitApply=addScreenWidget(ui.button("limit_set",leftPos,topPos,()->Component.literal("+"),this::applyLimit));
        var target=addScreenWidget(ui.button("src_target",leftPos,topPos,()->tr("limit_source_destination"),()->setMode(true)));
        var network=addScreenWidget(ui.button("src_me",leftPos,topPos,()->tr("limit_source_network"),()->setMode(false)));
        ui.state(target,()->menu.outputLimitMode==MolecularAutoCrafter.OutputLimitMode.DESTINATION?"active":"normal");
        ui.state(network,()->menu.outputLimitMode==MolecularAutoCrafter.OutputLimitMode.NETWORK?"active":"normal");
        for(int i=0;i<fields.length;i++){final int input=i;
            fields[i]=addScreenWidget(amountField("rsv"+(i%PAGE_SIZE),drafts[i]));
            apply[i]=addScreenWidget(ui.button("ok"+(i%PAGE_SIZE),leftPos,topPos,()->Component.literal("+"),()->applyReserve(input)));
        }
        addScreenWidget(ui.button("back",leftPos,topPos,()->Component.translatable("gui.back"),menu::requestBack));
        save=addScreenWidget(ui.button("save",leftPos,topPos,()->tr("save_all"),this::applyAll));
        addScreenWidget(ui.button("prev",leftPos,topPos,()->Component.literal("<"),()->changePage(-1)));
        addScreenWidget(ui.button("next",leftPos,topPos,()->Component.literal(">"),()->changePage(1)));
        ui.text("gui.molecularmanipulator.page",()->OsqUi.tr("gui.molecularmanipulator.page",page+1,pageCount()));
        for(var e:ui.layout.draw)if("text".equals(e.type)&&((e.baseline>=124&&e.baseline<218)||(e.x>=39&&e.x<158&&e.baseline<55)))ui.at(e.x,e.baseline,Component::empty);
        ui.omit("gui.molecularmanipulator.auto_craft_amount_range");
        ui.text("gui.molecularmanipulator.auto_craft_unsaved",()->tr(!valid()?"invalid_amount":dirty()?"unsaved":"saved"));
        ui.number(110,22,()->"#"+(menu.selectedSlot+1));
        refresh();
    }
    private EditBox amountField(String id,AutoCrafterAmountDraft draft){
        var field=ui.field(id,leftPos,topPos,font,tr("amount_range"));field.setMaxLength(19);
        field.setFilter(AutoCrafterAmountDraft::accepts);field.setValue(draft.text());
        field.setResponder(value->draft.edit(value));field.setTooltip(Tooltip.create(tr("amount_range")));return field;
    }
    private void setMode(boolean target){if(target!=(menu.outputLimitMode==MolecularAutoCrafter.OutputLimitMode.DESTINATION))menu.requestOutputLimitModeToggle();}
    private void sync(){limitDraft.sync(menu.outputLimit);for(int i=0;i<drafts.length;i++)drafts[i].sync(menu.getReserve(i));}
    private int count(){return Math.clamp(menu.inputCount,0,fields.length);}
    private int pageCount(){return Math.max(1,Math.ceilDiv(count(),PAGE_SIZE));}
    private void changePage(int amount){page=Math.clamp(page+amount,0,pageCount()-1);setFocused(null);refresh();}
    private boolean valid(){if(limitDraft.value().isEmpty())return false;for(int i=0;i<count();i++)if(drafts[i].value().isEmpty())return false;return true;}
    private boolean dirty(){if(limitDraft.isDirty())return true;for(int i=0;i<count();i++)if(drafts[i].isDirty())return true;return false;}
    private void applyLimit(){if(details()!=null)limitDraft.value().ifPresent(menu::requestLimit);}
    private void applyReserve(int i){if(details()!=null&&i<count())drafts[i].value().ifPresent(value->menu.requestReserve(i,value));}
    private void applyAll(){if(!valid()||details()==null)return;if(limitDraft.isDirty())applyLimit();for(int i=0;i<count();i++)if(drafts[i].isDirty())applyReserve(i);}
    private void refresh(){if(limit==null)return;page=Math.min(page,pageCount()-1);boolean ready=details()!=null;
        limit.active=ready;limitApply.active=ready&&limitDraft.value().isPresent()&&limitDraft.isDirty();
        if(!limit.getValue().equals(limitDraft.text()))limit.setValue(limitDraft.text());limit.setTextColor(limitDraft.value().isPresent()?ui.palette.ink:ui.palette.warn);
        for(int i=0;i<fields.length;i++){boolean visible=i<count()&&i/PAGE_SIZE==page;fields[i].visible=apply[i].visible=visible;fields[i].active=ready&&visible;
            apply[i].active=ready&&visible&&drafts[i].value().isPresent()&&drafts[i].isDirty();
            if(!fields[i].getValue().equals(drafts[i].text()))fields[i].setValue(drafts[i].text());fields[i].setTextColor(drafts[i].value().isPresent()?ui.palette.ink:ui.palette.warn);}
        save.active=ready&&valid()&&dirty();
    }
    @Override public void containerTick(){super.containerTick();sync();refresh();}
    @Override public boolean keyPressed(int key,int scan,int mods){if(key==257||key==335){if(limit.isFocused()){applyLimit();return true;}for(int i=0;i<count();i++)if(fields[i].visible&&fields[i].isFocused()){applyReserve(i);return true;}}return super.keyPressed(key,scan,mods);}
    private IPatternDetails details(){if(minecraft==null||minecraft.level==null)return null;var stack=menu.getSelectedPattern();if(!ItemStack.matches(decoded,stack)){
        decoded=stack.copy();try{pattern=stack.isEmpty()?null:PatternDetailsHelper.decodePattern(stack,minecraft.level);}catch(RuntimeException error){pattern=null;}}
        return pattern;
    }
    @Override public void drawBG(GuiGraphics g,int x,int y,int mx,int my,float pt){if(ui!=null)ui.background(g,font,x,y);}
    @Override public void drawFG(GuiGraphics g,int ox,int oy,int mx,int my){if(ui==null)return;ui.foreground(g,font);var d=details();if(d==null)return;
        AEStackIcon.draw(g,d.getPrimaryOutput(),17,39);ui.smallText(g,d.getPrimaryOutput().what().getDisplayName(),39,45,112);
        ui.smallText(g,tr("state."+menu.state.name().toLowerCase(Locale.ROOT)),46,54,106);
        for(int i=page*PAGE_SIZE;i<Math.min(count(),(page+1)*PAGE_SIZE);i++){if(i>=d.getInputs().length)break;var options=d.getInputs()[i].getPossibleInputs();if(options.length==0)continue;
            GenericStack s=options[0];int y=125+(i%PAGE_SIZE)*18;AEStackIcon.draw(g,s,17,y);ui.smallText(g,s.what().getDisplayName(),39,y+10,112);}
    }
    private static Component tr(String key){return OsqUi.tr("gui.molecularmanipulator.auto_craft_"+key);}
}
