package com.atir.molecularmanipulator.client;

import appeng.client.gui.style.ScreenStyle;
import com.atir.molecularmanipulator.blockentity.MolecularCenterBlockEntity.QuantumLinkState;
import com.atir.molecularmanipulator.blockentity.SingularityBlockEntity;
import com.atir.molecularmanipulator.blockentity.SingularityDuplicationProcessor;
import com.atir.molecularmanipulator.blockentity.SingularityStructure;
import com.atir.molecularmanipulator.menu.SingularityMenu;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class SingularityScreen extends ResponsiveContainerScreen<SingularityMenu> {
    private Button build, preview, refresh, dismantle, cancel, overview, collectionTab, duplicationTab, embed, collectionToggle;
    private boolean collectionPage, duplicationPage;
    private long armedAt;
    private int collectionScroll;
    private OsqUi ui;

    public SingularityScreen(SingularityMenu menu, Inventory inventory, Component title, ScreenStyle style) {
        super(menu, inventory, title, style);
        collectionPage = menu.getMachine().isSingleBlock();
    }

    private static Component tr(String key, Object... args) {
        return Component.translatable("gui.molecularmanipulator.singularity." + key, args);
    }

    @Override
    protected void init() {
        boolean compact = menu.getMachine().isSingleBlock();
        if (compact && !duplicationPage) collectionPage = true;
        String name=collectionPage?"sing_collection":duplicationPage?"sing_duplication":menu.getMachine().isSingleBlock()?"sing_compact":"sing_overview";
        ui=new OsqUi(name);
        com.atir.molecularmanipulator.client.gui.theme.OsqSingularityPages.configure(ui.layout, compact);
        imageWidth=ui.layout.width;imageHeight=ui.layout.height;super.init();
        ui.inventory(menu);ui.slots(menu.getSlots(SingularityMenu.QUANTUM_INPUT),"qes");
        ui.slots(menu.getSlots(SingularityMenu.DUPLICATION_SAMPLE),"sample");ui.slots(menu.getSlots(SingularityMenu.BLACK_HOLE_INPUT),"hole");
        var tabs=ui.layout.draw.stream().filter(e->e.sprite!=null&&e.sprite.contains("/tab_")).toList();
        int tabIndex = 0;
        overview = null;
        if (!compact) {
            var t=tabs.get(tabIndex++);overview=addScreenWidget(ui.tab(t.x,t.y,t.w,t.h,leftPos,topPos,()->tr("overview"),bOverview()));
            ui.state(overview,()->!collectionPage&&!duplicationPage?"active":"normal");
        }
        var t=tabs.get(tabIndex++);collectionTab=addScreenWidget(ui.tab(t.x,t.y,t.w,t.h,leftPos,topPos,()->tr("collection"),this::showCollection));
        t=tabs.get(tabIndex);duplicationTab=addScreenWidget(ui.tab(t.x,t.y,t.w,t.h,leftPos,topPos,()->tr("duplication"),this::showDuplication));
        ui.state(collectionTab,()->collectionPage?"active":"normal");ui.state(duplicationTab,()->duplicationPage?"active":"normal");
        boolean secondary=collectionPage||duplicationPage;
        build=addScreenWidget(!secondary&&!menu.getMachine().isSingleBlock()?ui.button("build",leftPos,topPos,null,()->menu.request(menu.operation != SingularityBlockEntity.Operation.IDLE ? "pause" : "build"))
                :ui.rect(-1000,-1000,40,17,leftPos,topPos,()->tr("build"),()->menu.request("build")));
        preview=addScreenWidget(!secondary&&!menu.getMachine().isSingleBlock()?ui.button("preview",leftPos,topPos,()->tr("preview"),()->{SingularityGhostPreview.toggle(menu.getMachine());onClose();})
                :ui.rect(-1000,-1000,40,17,leftPos,topPos,()->tr("preview"),()->{}));
        refresh=addScreenWidget(!secondary&&!menu.getMachine().isSingleBlock()?ui.button("refresh",leftPos,topPos,()->tr("refresh"),()->menu.request("refresh"))
                :ui.rect(-1000,-1000,40,17,leftPos,topPos,()->tr("refresh"),()->menu.request("refresh")));
        dismantle=addScreenWidget(!secondary&&!menu.getMachine().isSingleBlock()?ui.button("dismantle",leftPos,topPos,()->tr(armedAt==0?"dismantle":"confirm"),()->{
                long now=Util.getMillis();if(armedAt!=0&&now-armedAt<250)return;menu.request("dismantle");armedAt=armedAt!=0&&now-armedAt<=3000?0:now;})
                :ui.rect(-1000,-1000,40,17,leftPos,topPos,()->tr("dismantle"),()->{}));
        ui.state(dismantle,()->armedAt==0?"danger":"confirm");
        cancel=addScreenWidget(ui.rect(173,142,68,17,leftPos,topPos,()->tr("cancel"),()->menu.request("cancel")));
        embed=addScreenWidget(ui.rect(246,142,87,17,leftPos,topPos,null,()->menu.request(menu.structureVersion==SingularityStructure.EMBEDDED_VERSION?"upgrade":"embed")));
        collectionToggle=addScreenWidget(collectionPage?ui.button("collection",leftPos,topPos,null,()->menu.request("collection"))
                :ui.rect(-1000,-1000,40,17,leftPos,topPos,()->tr("collection_start"),()->menu.request("collection")));
        if(collectionPage){
            addScreenWidget(ui.button("prev",leftPos,topPos,()->Component.literal("<"),()->collectionScroll=Math.max(0,collectionScroll-1)));
            addScreenWidget(ui.button("next",leftPos,topPos,()->Component.literal(">"),()->collectionScroll=Math.min(Math.max(0,(collectionResources().size()-1)/12),collectionScroll+1)));
            ui.text("gui.molecularmanipulator.page",()->OsqUi.tr("gui.molecularmanipulator.page",collectionScroll+1,Math.max(1,(collectionResources().size()+11)/12)));
            for(var e:ui.layout.draw)if("text".equals(e.type)&&e.baseline>=120&&e.baseline<209)ui.at(e.x,e.baseline,Component::empty);
            ui.number(55.34f,102,()->collectionResources().size());ui.number(108.94f,102,()->menu.collectionBatchSize);
            ui.number(88.42f,64,()->menu.quantumFrequency==0?"-":Long.toUnsignedString(menu.quantumFrequency,16).toUpperCase(Locale.ROOT));
            ui.width(114.97f,64,167);ui.width(17,64,48);
            ui.sprite(114.5f,47,()->ui.sprite("dot_"+(menu.collectionActive?"ok":"mute")));
            ui.at(17,102,()->tr("collection_resources",collectionResources().size(),menu.collectionBatchSize,menu.collectionIntervalTicks));
            ui.width(17,102,225);
            ui.number(55.34f,102,()->"");ui.number(108.94f,102,()->"");ui.at(64.5f,102,Component::empty);ui.at(125.8f,102,Component::empty);
        }
        ui.text("gui.molecularmanipulator.formed",()->tr(menu.formed?"formed":"incomplete"));
        ui.text("gui.molecularmanipulator.singularity.network_online",()->tr(menu.networkOnline?"network_online":"network_offline"));
        ui.text("gui.molecularmanipulator.singularity.status.running",this::overviewStatus);
        ui.text("gui.molecularmanipulator.singularity.collection_running",()->tr(menu.collectionStarting?"collection_starting_status":menu.collectionActive?"collection_running":"collection_stopped"));
        ui.text("gui.molecularmanipulator.singularity.collection_network",()->tr("collection_network",tr(menu.networkOnline?"online":"offline")));
        ui.text("gui.molecularmanipulator.sing.s001",()->OsqUi.tr("gui.molecularmanipulator.quantum_state."+quantumStateKey()));
        ui.number(222.02f,197.5f,()->menu.quantumFrequency==0?"-":Long.toUnsignedString(menu.quantumFrequency,16).toUpperCase(Locale.ROOT));
        ui.width(252.72f,195.8f,80);
        ui.width(16.5f,246,48);ui.width(67.02f,246,80);
        ui.sprite(243.7f,45,()->ui.sprite("dot_"+(menu.formed?"ok":"warn")));
        ui.sprite(173,60,()->ui.sprite("dot_"+(menu.networkOnline?"ok":"mute")));
        ui.progress(17,252,()->menu.correct/(double)Math.max(1,SingularityStructure.parts(menu.structureVersion).size()));
        ui.number(16.5f,246,()->menu.correct);ui.number(35.09f,246,()->"");ui.number(67.02f,246,()->"/ "+SingularityStructure.parts(menu.structureVersion).size());ui.number(79.59f,246,()->"");
        ui.number(172.5f,134,()->menu.missing);ui.number(225.5f,134,()->menu.conflicts);ui.number(278.5f,134,()->menu.unloaded);
        ui.width(172.5f,134,42);ui.width(225.5f,134,42);ui.width(278.5f,134,48);
        ui.color("gui.molecularmanipulator.singularity.network_online",()->menu.networkOnline?ui.palette.ok:ui.palette.ink3);
        ui.color("gui.molecularmanipulator.sing.s001",()->quantumConnected()?ui.palette.ok:ui.palette.ink3);
        ui.number(16,203,()->menu.blackHoleCount);ui.number(30.19f,203,()->"/ 64");ui.number(185.5f,181,()->OsqUi.compact(menu.duplicationMatterPerCycle));ui.number(199.36f,194,()->menu.duplicationIntervalTicks);
        ui.number(17,242,()->formatEnergy(menu.duplicationFePerCycle));ui.number(86,242,()->formatEnergy(menu.lastDuplicationFeConsumed));
        ui.number(17,272,()->formatEnergy(menu.duplicationAePerCycle));ui.number(86,272,()->formatEnergy(menu.lastDuplicationAeConsumed));
        ui.text("gui.molecularmanipulator.singularity.duplication_energy",()->tr("duplication_energy",menu.duplicationEnergyPriority));
        ui.text("gui.molecularmanipulator.singularity.duplication_cost",()->tr("duplication_cost",SingularityDuplicationProcessor.MATTER_PER_ITEM));
        ui.sprite(17,59,()->ui.sprite("dot_"+(menu.networkOnline?"ok":"mute")));
        ui.color("gui.molecularmanipulator.singularity.collection_network",()->menu.networkOnline?ui.palette.ok:ui.palette.ink3);
        ui.text("gui.molecularmanipulator.sing.s017",()->tr("duplication_black_hole_count",menu.blackHoleCount));
        ui.color("gui.molecularmanipulator.sing.s017",()->menu.blackHoleCount>0?ui.palette.ok:ui.palette.ink3);
        if (compact) {
            ui.text("block.molecularmanipulator.event_horizon_singularity_hub",
                    ()->menu.getMachine().getBlockState().getBlock().getName());
            ui.text("gui.molecularmanipulator.singularity.collection_hint",()->tr("compact_collection_hint"));
        }
        if(name.equals("sing_compact")) {
            var dot=ui.layout.find("network_dot");
            ui.sprite(dot.x,dot.y,()->ui.sprite("dot_"+(menu.networkOnline?"ok":"mute")));
        }
        updateWidgets();
    }

    private Runnable bOverview(){return this::showOverview;}
    void testPage(int page){if(page==1)showCollection();else if(page==2)showDuplication();else showOverview();}
    int detailPage(){return duplicationPage?2:collectionPage?1:0;}
    private void showOverview(){
        if (menu.getMachine().isSingleBlock()) { showCollection(); return; }
        collectionPage=false;duplicationPage=false;rebuildWidgets();
    }

    private void showCollection() {
        collectionPage=true;duplicationPage=false;rebuildWidgets();
    }

    private void showDuplication() {
        collectionPage=false;duplicationPage=true;rebuildWidgets();
    }

    private void updateWidgets() {
        boolean operationActive = menu.operation != SingularityBlockEntity.Operation.IDLE;
        boolean secondaryPage = collectionPage || duplicationPage;
        menu.setDuplicationSlotsActive(duplicationPage);
        menu.setQuantumSlotActive(!secondaryPage && !menu.getMachine().isSingleBlock());
        if(ui.layout.find("qes")==null)for(var slot:menu.getSlots(SingularityMenu.QUANTUM_INPUT)){slot.x=-1000;slot.y=-1000;}
        build.visible = !secondaryPage;
        preview.visible = refresh.visible = !secondaryPage;
        build.setMessage(tr(operationActive ? menu.paused ? "resume" : "pause" : "build"));
        build.active = !secondaryPage && !menu.embedRequested
                && (operationActive || !menu.formed && !menu.movingBodies);

        cancel.visible = menu.embedRequested || operationActive;
        cancel.active = cancel.visible;
        cancel.setMessage(tr("cancel"));

        dismantle.visible = !secondaryPage;
        dismantle.active = !secondaryPage && !menu.embedRequested && !operationActive
                && !menu.collectionActive && menu.correct > 1 && menu.motionMode != 3;
        embed.visible = !secondaryPage && menu.structureVersion < SingularityStructure.VERSION;
        embed.active = menu.formed && !operationActive && !menu.embedRequested && menu.motionMode != 3
                && !menu.collectionActive;
        String migration = menu.structureVersion == SingularityStructure.EMBEDDED_VERSION ? "upgrade" : "embed";
        embed.setMessage(tr(migration + (menu.embedRequested ? "_waiting" : "")));
        embed.setTooltip(Tooltip.create(tr(migration + "_hint")));

        if (armedAt != 0 && Util.getMillis() - armedAt > 3000) armedAt = 0;
        dismantle.setMessage(tr(armedAt == 0 ? "dismantle" : "confirm"));
        if (overview != null) overview.active = true;
        collectionTab.active = duplicationTab.active = true;

        collectionToggle.visible = collectionPage;
        collectionToggle.setMessage(tr(menu.collectionStarting ? "collection_starting"
                : menu.collectionActive ? "collection_stop" : "collection_start"));
        collectionToggle.active = collectionPage && menu.formed && !operationActive && !menu.embedRequested
                && menu.motionMode != 3 && (menu.collectionActive || menu.networkOnline);
        if (menu.getMachine().isSingleBlock()) {
            build.visible = preview.visible = refresh.visible = dismantle.visible = cancel.visible = embed.visible = false;
        }
    }

    @Override
    public void containerTick() {
        super.containerTick();
        updateWidgets();
    }

    @Override
    public void drawBG(GuiGraphics g, int ox, int oy, int mx, int my, float partialTick) {
        if(ui!=null)ui.background(g,font,ox,oy);
    }

    @Override
    public void drawFG(GuiGraphics g, int ox, int oy, int mx, int my) {
        if(ui==null)return;ui.foreground(g,font);
        if(collectionPage){var resources=collectionResources();for(int i=0;i<12;i++){int index=collectionScroll*12+i;if(index>=resources.size())break;
            int x=17+i%3*108,y=111+i/3*23;var item=resources.get(index);g.renderItem(item,x+1,y+1);ui.smallText(g,item.getHoverName(),x+23,y+12,76);}}

    }

    private Component overviewStatus() {
        if (!menu.formed) return tr("status.not_formed");
        if (menu.getMachine().isSingleBlock()) {
            if (!menu.networkOnline) return tr("network_offline");
            if (menu.status == SingularityBlockEntity.Status.STORAGE_FULL) return tr("status.storage_full");
            if (menu.collectionActive) return tr("status.running");
        }
        if (menu.collectionStarting) return tr("collection_starting_status");
        if (menu.collectionActive && menu.motionMode == 1) return tr("status.running");
        if (menu.collectionActive) return tr("status.collection_paused");
        return tr("status." + menu.status.name().toLowerCase(Locale.ROOT));
    }

    private List<ItemStack> collectionResources() {
        List<ItemStack> result = new ArrayList<>();
        for (String raw : menu.collectionItems.split(",")) {
            if (raw.isBlank()) continue;
            ResourceLocation id = ResourceLocation.tryParse(raw.trim());
            if (id == null) continue;
            var item = BuiltInRegistries.ITEM.get(id);
            ItemStack stack = new ItemStack(item);
            if (!stack.isEmpty()) result.add(stack);
        }
        return result;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        double x=logicalMouseX(mouseX)-leftPos,y=logicalMouseY(mouseY)-topPos;
        if(collectionPage&&x>=8&&x<342&&y>=110&&y<207&&scrollY!=0){collectionScroll=Math.clamp(collectionScroll+(scrollY<0?1:-1),0,Math.max(0,(collectionResources().size()-1)/12));return true;}
        return super.mouseScrolled(mouseX,mouseY,scrollX,scrollY);
    }

    private static String formatEnergy(long value) {
        return String.format(Locale.ROOT, "%,d", value);
    }
    private static String formatEnergy(double value) {
        return String.format(Locale.ROOT, "%,.3f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private boolean quantumConnected() {
        return menu.quantumLinkState == QuantumLinkState.CONNECTED
                || menu.quantumLinkState == QuantumLinkState.CONNECTED_BUILD_ONLY;
    }

    private String quantumStateKey() {
        return menu.quantumLinkState.name().toLowerCase(Locale.ROOT);
    }
}
