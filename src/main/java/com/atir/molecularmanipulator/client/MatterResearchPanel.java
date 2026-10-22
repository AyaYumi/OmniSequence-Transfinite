package com.atir.molecularmanipulator.client;

import appeng.api.stacks.GenericStack;
import com.atir.molecularmanipulator.menu.MatterFabricationMenu;
import com.atir.molecularmanipulator.research.MatterResearchApi;
import com.atir.molecularmanipulator.research.MatterResearchRecipe;
import com.atir.molecularmanipulator.research.ResearchBatch;
import net.minecraft.client.gui.screens.Screen;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.function.Consumer;
import com.atir.molecularmanipulator.integration.jei.ResearchJeiBookmarks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import com.atir.molecularmanipulator.crafting.MatterRecipeIndex;
import com.atir.molecularmanipulator.client.gui.theme.OsqScrollList;
import org.joml.Vector3f;

/** Independently scrolling research navigation and material/unlock/order details. */
final class MatterResearchPanel {
    private static final int ROWS = 5;
    private static final int PROJECT_TOP = 50, PROJECT_BOTTOM = 145, PROJECT_ROW_HEIGHT = 19;
    private static final int SCROLL_TOP = 157;
    private static final int SCROLL_BOTTOM = 219;
    private static final int SCROLL_HEIGHT = SCROLL_BOTTOM - SCROLL_TOP;
    private static final int ROW_HEIGHT = 20;
    private final MatterFabricationMenu menu;
    private final OsqUi ui;
    private final Font font;
    private final List<Button> rows = new ArrayList<>();
    private final Button action, order, bookmark, orderDetails, stop, unlocks;
    private List<RecipeHolder<MatterResearchRecipe>> definitions = List.of();
    private Set<String> completed = Set.of();
    private JsonObject tasks = new JsonObject(), preparations = new JsonObject();
    private JsonObject counts = new JsonObject(), live = new JsonObject();
    private String notice = "";
    private String orderStatus = "idle";
    private int orderQueued;
    private int orderActive;
    private List<OrderEntry> orderEntries = List.of(), allOrderEntries = List.of();
    private boolean showOrderDetails;
    private boolean showUnlocks;
    private MatterRecipeIndex unlockIndex;
    private ResourceLocation unlockSelection;
    private List<MatterResearchUnlocks.Entry> unlockEntries = List.of();
    private String lastState = "";
    private final Map<ResourceLocation, MatterResearchRecipe> taskTerms = new HashMap<>();
    private final Map<ResourceLocation, String> termSources = new HashMap<>();
    private MatterResearchRecipe costDefinition;
    private int costFirst, costLast;
    private List<MatterResearchRecipe.Cost> cachedCosts = List.of();
    private ResourceLocation selected;
    private int projectScroll;
    private double scroll;
    private int contentRows;
    private boolean visible;
    private final List<QuantityTooltip> quantityTooltips = new ArrayList<>();
    private final Set<com.atir.molecularmanipulator.client.gui.theme.OsqLayout.Entry> materialLayer = new HashSet<>(), detailLayer = new HashSet<>();

    MatterResearchPanel(MatterFabricationMenu menu, Font font, int left, int top, Consumer<Button> add) {
        this.menu=menu;this.font=font;ui=new OsqUi("fab_research");
        for(var e:ui.layout.draw)if(e.x>=204&&(("text".equals(e.type)&&e.baseline>=140&&e.baseline<219)
                ||(!"text".equals(e.type)&&e.y>=140&&e.y<219)))materialLayer.add(e);
        var detail=com.atir.molecularmanipulator.client.gui.theme.OsqLayout.forTheme("fab_research_detail",false);
        for(var e:detail.draw)if(e.x>=204&&!"tip".equals(e.kind)&&(("text".equals(e.type)&&e.baseline>=140&&e.baseline<219)
                ||(!"text".equals(e.type)&&e.y>=140&&e.y<219))) {detailLayer.add(e);ui.layout.draw.add(e);}
        ui.visible=e->!(showOrderDetails?materialLayer:detailLayer).contains(e);
        // The overview screen owns these live tabs. Neither research layer may repaint them.
        ui.hide(e -> e.sprite != null && e.sprite.contains("/tab_")
                || "text".equals(e.type) && e.x >= 339 && e.baseline < 25
                || e.x>=204 && e.y>=SCROLL_TOP-1 && e.y<SCROLL_BOTTOM
                        && !"text".equals(e.type) && !"card".equals(e.kind));
        for(int i=0;i<ROWS;i++){final int row=i;
            var button=ui.button("proj"+i,left,top,null,()->{int position=firstProject()+row;if(position<definitions.size()){selected=definitions.get(position).id();scroll=0;update(true);}});
            rows.add(button);add.accept(button);
            ui.state(button,()->firstProject()+row<definitions.size()&&definitions.get(firstProject()+row).id().equals(selected)?"active":"normal");
        }
        // Leave space at the right of each row for the navigation scrollbar.
        for (var row : rows) row.setWidth(row.getWidth()-6);
        action=ui.button("pause",left,top,null,()->{if(selected!=null)menu.toggleResearch(selected.toString());});
        order=ui.button("order",left,top,null,()->{if(selected!=null){if(preparations.has(selected.toString()))menu.stopResearchPreparation(selected.toString());else menu.requestResearchOrder(selected.toString(),Screen.hasShiftDown());showOrderDetails=false;showUnlocks=false;scroll=0;update(visible);}});
        orderDetails=ui.button("detail",left,top,null,()->{showOrderDetails=!showOrderDetails;showUnlocks=false;scroll=0;update(visible);});
        bookmark=ui.button("jei",left,top,()->Component.literal("?"),this::bookmarkSelected);
        unlocks=ui.rect(315,142,69,13,left,top,()->showUnlocks?text("show_materials")
                :Component.translatable("gui.molecularmanipulator.research.show_unlocks",unlockEntries.size()),
                ()->{showUnlocks=!showUnlocks;showOrderDetails=false;scroll=0;update(visible);});
        ui.state(unlocks,()->showUnlocks?"active":"normal");
        ui.centerLabel(unlocks);
        unlocks.setTooltip(Tooltip.create(text("unlock_on_completion")));
        add.accept(unlocks);add.accept(action);add.accept(order);add.accept(bookmark);add.accept(orderDetails);
        stop=ui.button("stop",left,top,()->text("stop_current"),()->{if(selected!=null)menu.stopResearchPreparation(selected.toString());});
        add.accept(stop);
        ui.state(stop,()->selected!=null&&preparations.has(selected.toString())?"danger":"disabled");
        for(var e:ui.layout.draw)if("text".equals(e.type)&&((e.x>=210&&e.baseline>=157&&e.baseline<219)||(e.x<195&&e.baseline>=49&&e.baseline<144)))ui.at(e.x,e.baseline,Component::empty);
        ui.text("gui.molecularmanipulator.fab.s050",()->selected()==null?text("empty"):Component.translatable(selected().value().title()));
        ui.number(42.36f,191,()->"");ui.number(106.81f,191,()->"");ui.at(47.23f,191,Component::empty);
        ui.text("gui.molecularmanipulator.research.order_state.submitted",()->orderState(orderStatus));
        ui.number(212.5f,76,()->selected()==null?0:count(selected()));
        ui.number(223.47f,76,()->selected()==null?"":"/ "+selected().value().depths().size());
        ui.number(299.5f,76,()->selected()==null?0:Math.round(100*progressRatio()));
        ui.number(213,110.5f,()->benefit(false));ui.number(300,110.5f,()->benefit(true));
        ui.number(222,124,()->selected()==null?"":progressTicks()/20+" / "+selected().value().duration()/20+" s");
        ui.number(265.11f,124,()->selected()==null?"":DisplayNumbers.compact(selected().value().aePerTick())+" AE/t");
        ui.text("gui.molecularmanipulator.research.materials",()->showUnlocks?text("unlocks"):text("materials"));
        ui.width(213,151,98);
        // Keep the missing-count sentence intact instead of binding its sample digits separately.
        ui.hide(e -> detailLayer.contains(e) && "text".equals(e.type) && e.baseline==151);
        for(int i=0;i<9;i++){final int index=i;ui.sprite(213+i*19,84,()->"molecularmanipulator:osq_light/fab/depth_seg_"
                +(selected()!=null&&index<count(selected())?"on":selected()!=null&&index==count(selected())&&task(selected())!=null?"cur":"off"));}
        ui.width(213,110.5f,80);ui.width(300,110.5f,84);ui.width(265.11f,124,90);
        ui.sprite(17,171.75f,()->ui.sprite("dot_"+(orderActive>0?"ok":orderStatus.equals("idle")?"mute":"warn")));
        ui.at(24,173.75f,Component::empty);ui.at(17,191,Component::empty);
        ui.state(orderDetails,()->showOrderDetails?"active":"normal");
        ui.visible=e->!(showOrderDetails?materialLayer:detailLayer).contains(e);
        // The unlock switch replaces its example heading/count in both detail modes.
        ui.hide(e -> "text".equals(e.type) && e.baseline==151 && e.x>=315);
        for(var e:detailLayer)if("text".equals(e.type)&&e.baseline>=157)ui.at(e.x,e.baseline,Component::empty);
        update(false);
    }

    void update(boolean visible) {
        this.visible = visible;
        var level = Minecraft.getInstance().level;
        if (level != null) definitions = MatterResearchApi.definitions(level);
        if (!lastState.equals(menu.researchState.json())) {
            lastState = menu.researchState.json();
            var state = JsonParser.parseString(lastState).getAsJsonObject();
            var done = new HashSet<String>();
            for (var id : state.getAsJsonArray("completed")) done.add(id.getAsString());
            completed = done;
            tasks = state.getAsJsonObject("tasks");
            preparations=state.has("preparations")?state.getAsJsonObject("preparations"):new JsonObject();
            counts = state.has("counts") ? state.getAsJsonObject("counts") : new JsonObject();
            live = state.has("live") ? state.getAsJsonObject("live") : new JsonObject();
            notice = state.has("notice") ? state.get("notice").getAsString() : "";
            if (state.has("orders")) {
                var orders = state.getAsJsonObject("orders");
                orderStatus = orders.has("status") ? orders.get("status").getAsString() : "idle";
                orderQueued = orders.has("queued") ? orders.get("queued").getAsInt() : 0;
                orderActive = orders.has("active") ? orders.get("active").getAsInt() : 0;
                var parsed = new ArrayList<OrderEntry>();
                if (level != null && orders.has("items")) {
                    var ops = level.registryAccess().createSerializationContext(JsonOps.INSTANCE);
                    for (var element : orders.getAsJsonArray("items")) {
                        var entry = element.getAsJsonObject();
                        var target = GenericStack.CODEC.parse(ops, entry.get("target")).result();
                        if (target.isEmpty()) continue;
                        var missing = new ArrayList<GenericStack>();
                        for (var value : entry.getAsJsonArray("missing")) {
                            GenericStack.CODEC.parse(ops, value).result().ifPresent(missing::add);
                        }
                        parsed.add(new OrderEntry(target.get(), entry.get("queued").getAsLong(),
                                entry.get("active").getAsLong(), entry.get("status").getAsString(),
                                List.copyOf(missing), entry.has("owner") ? ResourceLocation.tryParse(entry.get("owner").getAsString()) : null));
                    }
                }
                allOrderEntries = List.copyOf(parsed);
            }
            var allTerms = new JsonObject();
            tasks.entrySet().forEach(entry -> allTerms.add(entry.getKey(), entry.getValue()));
            preparations.entrySet().forEach(entry -> allTerms.add(entry.getKey(), entry.getValue()));
            var retained = new HashSet<ResourceLocation>();
            if (level != null) allTerms.entrySet().forEach(entry -> {
                var id = ResourceLocation.parse(entry.getKey());
                retained.add(id);
                String source = entry.getValue().getAsJsonObject().get("terms").toString();
                if (!source.equals(termSources.get(id))) MatterResearchRecipe.CODEC.codec()
                        .parse(level.registryAccess().createSerializationContext(JsonOps.INSTANCE), JsonParser.parseString(source))
                        .result().ifPresent(value -> { taskTerms.put(id, value); termSources.put(id, source); });
            });
            taskTerms.keySet().retainAll(retained);
            termSources.keySet().retainAll(retained);
        }
        projectScroll = OsqScrollList.clamp(projectScroll,definitions.size(),PROJECT_BOTTOM-PROJECT_TOP,PROJECT_ROW_HEIGHT);
        if (definitions.stream().noneMatch(holder -> holder.id().equals(selected))) {
            selected = definitions.isEmpty() ? null : definitions.getFirst().id();
            scroll = 0;
        }
        if (level != null) {
            var index=MatterRecipeIndex.get(level);
            if (index!=unlockIndex || !java.util.Objects.equals(selected,unlockSelection)) {
                unlockEntries=MatterResearchUnlocks.entries(level,index,selected());
                unlockIndex=index;unlockSelection=selected;
            }
        } else {unlockEntries=List.of();unlockIndex=null;unlockSelection=null;}
        orderEntries = allOrderEntries.stream().filter(entry -> entry.owner() == null || entry.owner().equals(selected)).toList();
        orderQueued = (int) orderEntries.stream().filter(entry -> entry.queued() > 0).count();
        orderActive = (int) orderEntries.stream().filter(entry -> entry.active() > 0).count();
        orderStatus = orderEntries.isEmpty() ? "idle" : orderEntries.getFirst().status();
        if (visible && selected != null) menu.selectResearch(selected.toString());
        for (int row = 0; row < ROWS; row++) {
            int position = firstProject() + row;
            var button = rows.get(row);
            button.visible = visible && position < definitions.size();
            if (position < definitions.size()) {
                var holder = definitions.get(position);
                var label = Component.translatable(holder.value().title());
                button.setMessage(label.copy().append(" · "+count(holder)+"/"+holder.value().depths().size()));
                button.setTooltip(Tooltip.create(label.copy().append("\n").append(status(holder))));
                button.active = true;
            }
        }
        var holder = selected();
        if(showUnlocks)contentRows=unlockEntries.size();
        else if(showOrderDetails)contentRows=orderEntries.stream().mapToInt(e->1+e.missing().size()).sum();
        unlocks.visible=visible && holder!=null;
        unlocks.active=holder!=null;
        action.visible = visible && holder != null;
        order.visible = visible && holder != null;
        orderDetails.visible = visible && holder != null
                && (showOrderDetails || !orderEntries.isEmpty() || !orderStatus.equals("idle"));
        orderDetails.setMessage(text(showOrderDetails ? "order_materials" : "order_details"));
        stop.visible = visible && holder != null;
        stop.active = selected != null && preparations.has(selected.toString());
        bookmark.visible = visible && holder != null;
        if (holder != null) {
            var task=task(holder);
            boolean preparing=preparation(holder)!=null;
            boolean ordered=task!=null && task.has("ordered") && task.get("ordered").getAsBoolean();
            boolean paused = task != null && task.get("paused").getAsBoolean();
            boolean maxed = count(holder) >= holder.value().depths().size();
            action.setMessage(text(ordered ? "research_locked" : task == null ? maxed ? "maxed" : count(holder) == 0 ? "start" : "deepen" : paused ? "resume" : "pause"));
            action.setTooltip(Tooltip.create(text(!notice.isEmpty() ? notice : "action_hint")));
            action.active = !ordered && !preparing && (task != null || !maxed)
                    && prerequisitesMet(holder.value())
                    && (task != null && !paused || menu.formed && !menu.building && !menu.dismantling && !menu.updatingStructure
                        && liveForSelected() && live.get("online").getAsBoolean() && live.has("affordable") && live.get("affordable").getAsBoolean());
            order.setMessage(text(preparing ? "stop_current" : task!=null ? "research_locked" : Screen.hasShiftDown() ? "order_max" : "order"));
            order.setTooltip(Tooltip.create(text(preparing?"stop_current_hint":task!=null?"research_locked_hint":Screen.hasShiftDown()?"order_max_hint":"order_hint")));
            order.active = task==null && (preparing || !maxed && prerequisitesMet(holder.value()) && liveForSelected());
        }
    }

    void drawBackground(GuiGraphics graphics, int x, int y) {
        if(visible)ui.background(graphics,font,x,y);
    }

    void drawForeground(GuiGraphics graphics) {
        if(!visible)return;quantityTooltips.clear();ui.foreground(graphics,font);
        drawProjectScrollbar(graphics);
        ui.centeredText(graphics,orderState(orderStatus),24,173.75f,110,ui.palette.ink);
        ui.smallText(graphics,Component.translatable("gui.molecularmanipulator.research.order_summary",orderQueued,orderActive),17,191,173);
        var holder=selected();if(holder==null)return;
        if(showOrderDetails){drawOrderDetails(graphics);return;}
        if(showUnlocks){drawUnlocks(graphics);return;}
        var task=task(holder);var prep=preparation(holder);var definition=terms(holder,task!=null?task:prep);
        int count=count(holder),max=definition.depths().size();
        int first=task!=null&&task.has("first_round")?task.get("first_round").getAsInt():prep!=null?prep.get("first_round").getAsInt():Math.min(count+1,max);
        int round=task!=null?task.get("round").getAsInt():prep!=null?prep.get("round").getAsInt():Math.min(count+1,max);
        boolean batch=prep!=null||task!=null&&task.has("ordered")&&task.get("ordered").getAsBoolean()||task==null&&Screen.hasShiftDown();
        if(batch&&task==null&&prep==null)round=max;
        List<MatterResearchRecipe.Cost> costs;boolean costOverflow=false;
        try{costs=count>=max&&task==null?List.of():batch?batchCosts(definition,first,round):definition.costsFor(round);}
        catch(ArithmeticException error){costs=List.of();costOverflow=true;}
        contentRows=costs.size();scroll=OsqScrollList.clamp(scroll,contentRows,SCROLL_HEIGHT,ROW_HEIGHT);
        beginList(graphics);
        try{for(int i=(int)scroll/ROW_HEIGHT;i<Math.min(costs.size(),(int)scroll/ROW_HEIGHT+OsqScrollList.visibleRows(SCROLL_HEIGHT,ROW_HEIGHT));i++){
            var cost=costs.get(i);int y=158+i*ROW_HEIGHT-(int)scroll;var items=cost.ingredient().getItems();if(items.length==0)continue;
            var stack=items[0];ui.slotBackground(graphics,212,y);graphics.renderItem(stack,213,y+1);ui.smallText(graphics,stack.getHoverName(),235,y+8,143);
            long paid=task!=null&&task.has("paid")&&i<task.getAsJsonArray("paid").size()?task.getAsJsonArray("paid").get(i).getAsLong():0;
            String availableField=batch&&live.has("batch_available")?"batch_available":"available";
            long available=liveForSelected()&&live.has(availableField)&&i<live.getAsJsonArray(availableField).size()?live.getAsJsonArray(availableField).get(i).getAsLong():0;
            long covered=Math.min(cost.count(),Math.max(0,available));
            covered+=Math.min(cost.count()-covered,Math.max(0,paid));
            ui.smallText(graphics,Component.literal(DisplayNumbers.compact(covered)+" / "+DisplayNumbers.compact(cost.count())),235,y+17,143);
            quantityTooltips.add(new QuantityTooltip(212,y,166,ROW_HEIGHT,List.of(stack.getHoverName(),quantity("required",cost.count()),quantity("available",available))));
        }}finally{endList(graphics);}
        drawScrollbar(graphics);
        if(costs.isEmpty())ui.centeredText(graphics,text(costOverflow?"cost_overflow":count>=max?"max_hint":"no_materials"),
                213,187,170,costOverflow?ui.palette.warn:ui.palette.ink3);
    }

    private void drawOrderDetails(GuiGraphics graphics) {
        long missingCount=orderEntries.stream().flatMap(e->e.missing().stream()).map(GenericStack::what).distinct().count();
        ui.smallText(graphics,Component.translatable("gui.molecularmanipulator.research.order_missing_count",missingCount),213,151,98,
                missingCount>0?ui.palette.warn:ui.palette.ink3);
        contentRows=orderEntries.stream().mapToInt(e->1+e.missing().size()).sum();
        scroll=OsqScrollList.clamp(scroll,contentRows,SCROLL_HEIGHT,ROW_HEIGHT);
        beginList(graphics);
        try{int row=0;for(var e:orderEntries){
            drawOrderRow(graphics,e.target(),Component.literal(DisplayNumbers.compact(e.target().amount())+" · ").append(orderState(e.status())),row++,false);
            for(var missing:e.missing())drawOrderRow(graphics,missing,quantity("remaining",missing.amount()),row++,true);
        }}finally{endList(graphics);}
        drawScrollbar(graphics);
        if(orderEntries.isEmpty())ui.centeredText(graphics,text("order_empty"),213,187,170,ui.palette.ink3);
    }

    private void drawUnlocks(GuiGraphics graphics) {
        contentRows=unlockEntries.size();
        scroll=OsqScrollList.clamp(scroll,contentRows,SCROLL_HEIGHT,ROW_HEIGHT);
        int first=(int)scroll/ROW_HEIGHT;
        beginList(graphics);
        try {
            for(int i=first;i<Math.min(contentRows,first+OsqScrollList.visibleRows(SCROLL_HEIGHT,ROW_HEIGHT));i++) {
                var entry=unlockEntries.get(i);int y=158+(i-first)*ROW_HEIGHT;
                if(!entry.outputs().isEmpty()) {
                    ui.slotBackground(graphics,212,y);
                    appeng.api.client.AEKeyRendering.drawInGui(Minecraft.getInstance(),graphics,213,y+1,entry.outputs().getFirst().what());
                }
                ui.smallText(graphics,entry.title(),235,y+8,143);
                ui.smallText(graphics,entry.summary(),235,y+17,143);
                quantityTooltips.add(new QuantityTooltip(212,y,166,ROW_HEIGHT,entry.tooltip()));
            }
        } finally {endList(graphics);}
        drawScrollbar(graphics);
        if(unlockEntries.isEmpty())ui.centeredText(graphics,text("no_unlocks"),213,187,170,ui.palette.ink3);
    }

    private int firstProject() {return projectScroll/PROJECT_ROW_HEIGHT;}

    private void drawProjectScrollbar(GuiGraphics graphics) {
        int height=PROJECT_BOTTOM-PROJECT_TOP;
        int max=OsqScrollList.maxScroll(definitions.size(),height,PROJECT_ROW_HEIGHT);
        if(max<=0)return;
        int thumb=Math.max(8,height*ROWS/definitions.size());
        int y=PROJECT_TOP+Math.round((height-thumb)*(float)projectScroll/max);
        graphics.fill(187,PROJECT_TOP,189,PROJECT_BOTTOM,ui.palette.hair);
        graphics.fill(187,y,189,y+thumb,ui.palette.acc);
    }

    private void drawOrderRow(GuiGraphics graphics, GenericStack stack, Component amount, int row, boolean missing) {
        int first=(int)scroll/ROW_HEIGHT;
        if(row<first||row>=first+OsqScrollList.visibleRows(SCROLL_HEIGHT,ROW_HEIGHT))return;
        int x=missing?221:213,y=158+(row-first)*ROW_HEIGHT;
        ui.slotBackground(graphics,x-1,y);
        appeng.api.client.AEKeyRendering.drawInGui(Minecraft.getInstance(),graphics,x,y+1,stack.what());
        ui.smallText(graphics,stack.what().getDisplayName(),x+22,y+8,378-x-22);
        ui.smallText(graphics,amount,x+22,y+17,378-x-22);
        quantityTooltips.add(new QuantityTooltip(x-1,y,379-x,ROW_HEIGHT,
                List.of(stack.what().getDisplayName(),quantity(missing?"remaining":"required",stack.amount()),amount)));
    }

    private void beginList(GuiGraphics graphics) {
        // GuiGraphics scissors are screen-space; flush before/after so batched text keeps this clip.
        graphics.flush();
        var matrix=graphics.pose().last().pose();
        var a=matrix.transformPosition(new Vector3f(211,SCROLL_TOP,0));
        var b=matrix.transformPosition(new Vector3f(379,SCROLL_BOTTOM,0));
        graphics.enableScissor((int)Math.floor(a.x),(int)Math.floor(a.y),(int)Math.ceil(b.x),(int)Math.ceil(b.y));
    }

    private static void endList(GuiGraphics graphics) { graphics.flush();graphics.disableScissor(); }

    private void drawScrollbar(GuiGraphics graphics) {
        int max=OsqScrollList.maxScroll(contentRows,SCROLL_HEIGHT,ROW_HEIGHT);
        if(max<=0)return;
        int height=Math.max(8,SCROLL_HEIGHT*OsqScrollList.visibleRows(SCROLL_HEIGHT,ROW_HEIGHT)/contentRows);
        int y=SCROLL_TOP+Math.round((SCROLL_HEIGHT-height)*(float)scroll/max);
        graphics.fill(383,SCROLL_TOP,385,SCROLL_BOTTOM,ui.palette.hair);
        graphics.fill(383,y,385,y+height,ui.palette.acc);
    }

    boolean scroll(double x, double y, double amount) {
        if (!visible || amount==0) return false;
        if(x>=8&&x<198&&y>=32&&y<146) {
            projectScroll=OsqScrollList.scroll(projectScroll,amount,definitions.size(),PROJECT_BOTTOM-PROJECT_TOP,PROJECT_ROW_HEIGHT);
            update(visible);return true;
        }
        if (x < 204 || x >= 392 || y < SCROLL_TOP || y >= SCROLL_BOTTOM) return false;
        scroll = OsqScrollList.scroll(scroll,amount,contentRows,SCROLL_HEIGHT,ROW_HEIGHT);
        return true;
    }

    private int progressTicks(){var holder=selected();if(holder==null)return 0;var task=task(holder);return task==null?0:task.get("progress").getAsInt();}
    private float progressRatio(){var holder=selected();return holder==null?0:Math.min(1,progressTicks()/(float)Math.max(1,holder.value().duration()));}
    private String benefit(boolean next){var h=selected();if(h==null)return "";int n=count(h);if(!next&&n==0)return text("no_benefit").getString();var d=h.value().depths().get(Math.clamp(next?n:n-1,0,h.value().depths().size()-1));return DisplayNumbers.compact(d.parallel())+" 并行 · ×"+d.speedMultiplier();}

    private RecipeHolder<MatterResearchRecipe> selected() {
        return definitions.stream().filter(holder -> holder.id().equals(selected)).findFirst().orElse(null);
    }

    private void bookmarkSelected() {
        var holder = selected();
        if (holder == null || Minecraft.getInstance().level == null) return;
        int completedCount = count(holder);
        int round = Math.min(completedCount + 1, holder.value().depths().size());
        List<ItemStack> stacks = new ArrayList<>();
        for (var cost : holder.value().costsFor(round)) {
            var examples = cost.ingredient().getItems();
            if (examples.length > 0) stacks.add(examples[0]);
        }
        ResearchJeiBookmarks.addItems(stacks);
    }
    private JsonObject preparation(RecipeHolder<MatterResearchRecipe> holder) {return preparations.getAsJsonObject(holder.id().toString());}
    private JsonObject task(RecipeHolder<MatterResearchRecipe> holder) { return tasks.getAsJsonObject(holder.id().toString()); }
    private MatterResearchRecipe terms(RecipeHolder<MatterResearchRecipe> holder, JsonObject task) {
        if (task == null) return holder.value();
        return taskTerms.getOrDefault(holder.id(), holder.value());
    }

    record Selection(ResourceLocation id, int projectScroll, double scroll, boolean unlocks, boolean orders) {
        Selection(ResourceLocation id,int projectScroll,double scroll) {this(id,projectScroll,scroll,false,false);}
    }
    Selection selection() { return new Selection(selected, projectScroll, scroll, showUnlocks, showOrderDetails); }
    void restoreSelection(Selection selection) {
        if (selection != null) { selected = selection.id(); projectScroll = selection.projectScroll(); scroll = selection.scroll();
            showOrderDetails=selection.orders();showUnlocks=selection.unlocks()&&!showOrderDetails;update(visible); }
    }
    void showDetails(boolean value) {showOrderDetails=value;showUnlocks=false;scroll=0;update(visible);}
    private Component status(RecipeHolder<MatterResearchRecipe> holder) {
        var task = task(holder);
        if (task != null) return text(task.get("status").getAsString());
        if(preparation(holder)!=null)return text("preparing");
        if (count(holder) >= holder.value().depths().size()) return text("maxed");
        if (!prerequisitesMet(holder.value())) return text("prerequisite");
        if (holder.id().equals(selected) && liveForSelected()) {
            if (live.has("error")) return text(live.get("error").getAsString());
            if (!live.get("online").getAsBoolean()) return text("network");
            if (live.has("affordable") && !live.get("affordable").getAsBoolean()) return text("insufficient");
        }
        return text(count(holder) > 0 ? "ready_depth" : "not_started");
    }
    private int count(RecipeHolder<MatterResearchRecipe> holder) {
        return counts.has(holder.id().toString()) ? counts.get(holder.id().toString()).getAsInt()
                : completed.contains(holder.id().toString()) ? 1 : 0;
    }
    private boolean prerequisitesMet(MatterResearchRecipe research) {
        return MatterResearchApi.prerequisitesMet(research, definitions, id -> counts.has(id.toString())
                ? counts.get(id.toString()).getAsInt() : completed.contains(id.toString()) ? 1 : 0);
    }
    private boolean liveForSelected() { return selected != null && live.has("id") && selected.toString().equals(live.get("id").getAsString()); }
    private static Component quantity(String key, long amount) {
        return Component.translatable("gui.molecularmanipulator.research.quantity_" + key, DisplayNumbers.exact(amount));
    }
    List<Component> tooltipAt(double x, double y) {
        if (!visible) return List.of();
        return quantityTooltips.stream().filter(tip -> x >= tip.x() && x < tip.x() + tip.width()
                && y >= tip.y() && y < tip.y() + tip.height()).findFirst().map(QuantityTooltip::lines).orElse(List.of());
    }
    private record QuantityTooltip(int x, int y, int width, int height, List<Component> lines) {}
    private static Component text(String key) { return Component.translatable("gui.molecularmanipulator.research." + key); }
    private static Component orderState(String key) {
        return Component.translatable("gui.molecularmanipulator.research.order_state." + key);
    }
    private List<MatterResearchRecipe.Cost> batchCosts(MatterResearchRecipe definition, int first, int last) {
        if (definition != costDefinition || first != costFirst || last != costLast) {
            cachedCosts = ResearchBatch.costs(definition, first, last);
            costDefinition = definition; costFirst = first; costLast = last;
        }
        return cachedCosts;
    }

    private record OrderEntry(GenericStack target, long queued, long active, String status,
                              List<GenericStack> missing, ResourceLocation owner) { }

}
