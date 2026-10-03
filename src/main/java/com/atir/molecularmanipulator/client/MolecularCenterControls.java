package com.atir.molecularmanipulator.client;

import com.atir.molecularmanipulator.blockentity.MolecularAutoCrafter;
import com.atir.molecularmanipulator.menu.MolecularCenterMenu;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.ArrayList;
import java.util.List;

/** Native Forge controls for the current overview, passive crafting and color pages. */
final class MolecularCenterControls {
    private final MolecularCenterScreen screen;
    private final MolecularCenterMenu menu;
    private final List<OmniButton> colors = new ArrayList<>(), reserves = new ArrayList<>();
    private final StructureUpdateConfirmation confirmation = new StructureUpdateConfirmation();
    private final OmniButton preview, build, previous, next, dismantle, reset, update, keep, apply;
    private final OmniButton[] tabs = new OmniButton[3];

    MolecularCenterControls(MolecularCenterScreen screen, MolecularCenterMenu menu) {
        this.screen = screen; this.menu = menu;
        preview = button(8, 4, 46, 18, screen.previewLabel(), text("preview"), () -> {
            if (MolecularCenterGhostPreview.toggle(menu.getCenter())) menu.requestPreview();
        });
        build = button(58, 4, 46, 18, text("build"), text("build"), menu::requestBuild);
        previous = button(108, 4, 20, 18, Component.literal("<"), text("previous_page"), () -> screen.changePatternPage(-1));
        next = button(130, 4, 20, 18, Component.literal(">"), text("next_page"), () -> screen.changePatternPage(1));
        dismantle = button(154, 4, 58, 18, screen.dismantleLabel(), screen.dismantleTooltip(), screen::dismantleClicked);
        String[] keys = {"tab_overview", "tab_auto_craft", "tab_colors"};
        for (int i=0;i<3;i++) {
            int tab=i; tabs[i]=button(216+i*68,4,i==2?68:66,18,text(keys[i]),text(keys[i]),()->screen.selectTab(tab));
        }
        apply=button(371,90,43,16,text("auto_craft_apply"),text("auto_craft_output_limit_tooltip"),
                ()->menu.requestSetAutoCraftOutputLimit(screen.autoCraftOutputLimitInput()));
        for(int i=0;i<MolecularAutoCrafter.MAX_INPUTS;i++) {
            int input=i; reserves.add(button(i%2==0?277:383,123+i/2*22,32,16,text("auto_craft_apply"),
                    text("auto_craft_input_reserve_tooltip"),()->menu.requestSetAutoCraftInputReserve(input,screen.autoCraftInputReserveInput(input))));
        }
        String[] channels={"R","G","B"};
        for(int i=0;i<5;i++) for(int j=0;j<3;j++) {
            int target=i,channel=j; colors.add(button(323+j*30,43+i*32,27,18,Component.literal(channels[j]),
                    text("visual_color_adjust_tooltip"),()->menu.requestAdjustVisualColor(target,channel,
                    (Screen.hasShiftDown()?-1:1)*(Screen.hasControlDown()?1:17))));
        }
        reset=button(286,198,128,18,text("visual_color_reset"),text("visual_color_reset"),menu::requestResetVisualColors);
        update=button(210,233,100,18,text("structure_update_confirm"),text("structure_update_confirm_tooltip"),()->{
            if(confirmation.click())menu.requestStructureUpdate();
        });
        keep=button(314,233,100,18,text("structure_update_keep_legacy"),text("structure_update_keep_legacy_tooltip"),()->{
            confirmation.cancel();menu.requestKeepLegacyStructure();
        });
        refresh();
    }
    void tick(){confirmation.tick(menu.legacyStructure&&!menu.building&&!menu.dismantling);refresh();}
    void close(){confirmation.cancel();}
    private void refresh(){
        int tab=screen.detailTab();boolean auto=tab==MolecularCenterScreen.TAB_AUTO_CRAFT;
        boolean color=tab==MolecularCenterScreen.TAB_COLORS,busy=menu.building||menu.dismantling;
        for(int i=0;i<tabs.length;i++)tabs[i].setSelected(i==tab);
        colors.forEach(button->button.visible=color);reset.visible=color;
        update.visible=menu.legacyStructure&&!auto;
        keep.visible=update.visible&&!menu.legacyStructureUpdateDismissed;
        update.active=keep.active=!busy;build.active=!busy;
        previous.active=!screen.patternSearchWaiting()&&menu.getPage()>0;
        next.active=!screen.patternSearchWaiting()&&menu.getPage()+1<menu.getPageCount();
        boolean valid=menu.autoCraftSelectedSlot>=0&&menu.autoCraftState!=MolecularAutoCrafter.AutoCraftState.EMPTY;
        apply.visible=auto&&valid;apply.active=valid;
        for(int i=0;i<reserves.size();i++){reserves.get(i).visible=auto&&i<menu.autoCraftInputCount;reserves.get(i).active=valid;}
        update.setMessage(text(confirmation.isArmed()?"structure_update_second_confirm":"structure_update_confirm"));
        update.setDanger(confirmation.isArmed());
        preview.setMessage(screen.previewLabel());dismantle.setMessage(screen.dismantleLabel());
        dismantle.setTooltip(Tooltip.create(screen.dismantleTooltip()));dismantle.setDanger(screen.dismantleConfirming());
    }
    private OmniButton button(int x,int y,int width,int height,Component label,Component tooltip,Runnable action){
        var button=AeUiTheme.button(screen.getGuiLeft()+x,screen.getGuiTop()+y,width,height,label,b->{action.run();refresh();});
        button.setTooltip(Tooltip.create(tooltip));return screen.addScreenWidget(button);
    }
    private static Component text(String key){return Component.translatable("gui.molecularmanipulator."+key);}
}
