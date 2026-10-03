package com.atir.molecularmanipulator.client;

import appeng.api.stacks.GenericStack;
import com.atir.molecularmanipulator.menu.MatterFabricationMenu;
import com.atir.molecularmanipulator.research.MatterResearchApi;
import com.atir.molecularmanipulator.research.MatterResearchRecipe;
import com.atir.molecularmanipulator.research.ResearchDepth;
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
import org.joml.Vector3f;

/** Paginated research navigation plus an independently scrolling material/unlock list. */
final class MatterResearchPanel {
    private static final int ROWS = 6;
    private static final int SCROLL_TOP = 140;
    private static final int SCROLL_BOTTOM = 237;
    private static final int SCROLL_HEIGHT = SCROLL_BOTTOM - SCROLL_TOP;
    private final MatterFabricationMenu menu;
    private final Font font;
    private final List<Button> rows = new ArrayList<>();
    private final Button previous, next, action, order, bookmark, orderDetails;
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
    private String lastState = "";
    private final Map<ResourceLocation, MatterResearchRecipe> taskTerms = new HashMap<>();
    private final Map<ResourceLocation, String> termSources = new HashMap<>();
    private MatterResearchRecipe costDefinition;
    private int costFirst, costLast;
    private List<MatterResearchRecipe.Cost> cachedCosts = List.of();
    private ResourceLocation selected;
    private int page;
    private double scroll;
    private int contentHeight;
    private boolean visible;
    private final List<QuantityTooltip> quantityTooltips = new ArrayList<>();

    MatterResearchPanel(MatterFabricationMenu menu, Font font, int left, int top, Consumer<Button> add) {
        this.menu = menu; this.font = font;
        for (int index = 0; index < ROWS; index++) {
            final int row = index;
            // AE's sprite includes a five-pixel bottom bevel; its three-pixel slice border repeats the bevel when stretched taller.
            var button = AeUiTheme.button(left + 18, top + 64 + index * 27, 96, 20, Component.empty(), clicked -> {
                int position = page * ROWS + row;
                if (position < definitions.size()) { selected = definitions.get(position).id(); scroll = 0; }
            });
            rows.add(button); add.accept(button);
        }
        previous = AeUiTheme.button(left + 18, top + 244, 28, 16, Component.literal("<"), clicked -> { page--; update(true); });
        next = AeUiTheme.button(left + 86, top + 244, 28, 16, Component.literal(">"), clicked -> { page++; update(true); });
        action = AeUiTheme.button(left + 136, top + 244, 84, 16, Component.empty(), clicked -> {
            if (selected != null) menu.toggleResearch(selected.toString());
        });
        action.setTooltip(Tooltip.create(text("action_hint")));
        order = AeUiTheme.button(left + 228, top + 244, 84, 16,
                text("order"), clicked -> {
                    if (selected != null) {
                        if (preparations.has(selected.toString())) menu.stopResearchPreparation(selected.toString());
                        else menu.requestResearchOrder(selected.toString(), Screen.hasShiftDown());
                        showOrderDetails = false;
                        scroll = 0;
                    }
                });
        order.setTooltip(Tooltip.create(text("order_hint")));
        orderDetails = AeUiTheme.button(left + 262, top + 121, 50, 14,
                text("order_details"), clicked -> { showOrderDetails = !showOrderDetails; scroll = 0; });
        orderDetails.setTooltip(Tooltip.create(text("order_details_hint")));
        bookmark = AeUiTheme.button(left + 292, top + 48, 20, 16, Component.literal("★"), clicked -> bookmarkSelected());
        bookmark.setTooltip(Tooltip.create(text("bookmark_hint")));
        add.accept(previous); add.accept(next); add.accept(action); add.accept(order);
        add.accept(bookmark); add.accept(orderDetails);
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
        page = Math.max(0, Math.min(page, Math.max(0, (definitions.size() - 1) / ROWS)));
        if (definitions.stream().noneMatch(holder -> holder.id().equals(selected))) {
            selected = definitions.isEmpty() ? null : definitions.getFirst().id();
            scroll = 0;
        }
        orderEntries = allOrderEntries.stream().filter(entry -> entry.owner() == null || entry.owner().equals(selected)).toList();
        orderQueued = (int) orderEntries.stream().filter(entry -> entry.queued() > 0).count();
        orderActive = (int) orderEntries.stream().filter(entry -> entry.active() > 0).count();
        orderStatus = orderEntries.isEmpty() ? "idle" : orderEntries.getFirst().status();
        if (visible && selected != null) menu.selectResearch(selected.toString());
        for (int row = 0; row < ROWS; row++) {
            int position = page * ROWS + row;
            var button = rows.get(row);
            button.visible = visible && position < definitions.size();
            if (position < definitions.size()) {
                var holder = definitions.get(position);
                var label = Component.translatable(holder.value().title());
                button.setMessage(shortLabel(label, 86));
                button.setTooltip(Tooltip.create(label.copy().append("\n").append(status(holder))));
                button.active = !holder.id().equals(selected);
            }
        }
        previous.visible = next.visible = visible;
        previous.active = page > 0;
        next.active = (page + 1) * ROWS < definitions.size();
        var holder = selected();
        action.visible = visible && holder != null;
        order.visible = visible && holder != null;
        orderDetails.visible = visible && holder != null
                && (showOrderDetails || !orderEntries.isEmpty() || !orderStatus.equals("idle"));
        orderDetails.setMessage(text(showOrderDetails ? "order_materials" : "order_details"));
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
        AeUiTheme.panel(graphics, x + 12, y + 44, x + 120, y + 264);
        AeUiTheme.panel(graphics, x + 128, y + 44, x + 320, y + 264);
    }

    void drawForeground(GuiGraphics graphics) {
        quantityTooltips.clear();
        int stage = definitions.stream().filter(holder -> completed.contains(holder.id().toString()))
                .mapToInt(holder -> holder.value().stage()).max().orElse(0);
        fitted(graphics, Component.translatable("gui.molecularmanipulator.research.projects", stage), 20, 48, 92, AeUiTheme.PRIMARY_TEXT);
        var pageLabel = Component.literal((page + 1) + "/" + Math.max(1, (definitions.size() + ROWS - 1) / ROWS));
        int labelWidth = font.width(pageLabel);
        float labelScale = Math.min(1, 32.0F / Math.max(1, labelWidth));
        graphics.pose().pushPose();
        graphics.pose().translate(66 - labelWidth * labelScale / 2.0F,
                252 - font.lineHeight * labelScale / 2.0F, 0);
        graphics.pose().scale(labelScale, labelScale, 1);
        graphics.drawString(font, pageLabel, 0, 0, AeUiTheme.MUTED_TEXT, false);
        graphics.pose().popPose();
        var holder = selected();
        if (holder == null) { fitted(graphics, text("empty"), 136, 50, 176, AeUiTheme.MUTED_TEXT); return; }
        fitted(graphics, shortLabel(Component.translatable(holder.value().title()), 176), 136, 48, 176, AeUiTheme.PRIMARY_TEXT);
        fitted(graphics, status(holder).copy().append(" · ").append(Component.translatable("gui.molecularmanipulator.research.depth_count", count(holder), holder.value().depths().size())), 136, 63, 176,
                completed.contains(holder.id().toString()) ? AeUiTheme.SUCCESS : AeUiTheme.WARNING);
        var task = task(holder);
        var preparation=preparation(holder);
        var definition = terms(holder,task!=null?task:preparation);
        int count = count(holder), max = definition.depths().size();
        boolean maxed = task == null && count >= max;
        boolean batch=task!=null && task.has("ordered") && task.get("ordered").getAsBoolean()
                || preparation!=null || task==null && Screen.hasShiftDown();
        int firstRound=task!=null && task.has("first_round")?task.get("first_round").getAsInt()
                : preparation!=null?preparation.get("first_round").getAsInt():Math.min(count+1,max);
        int round = task != null ? task.get("round").getAsInt() : preparation!=null?preparation.get("round").getAsInt()
                : batch?max:Math.min(count+1,max);
        int progress = task == null ? count >= max ? definition.duration() : 0 : task.get("progress").getAsInt();
        var current = holder.value().depths().get(Math.max(0, Math.min(count - 1, holder.value().depths().size() - 1)));
        fitted(graphics, count == 0 ? text("no_benefit") : Component.translatable("gui.molecularmanipulator.research.current_benefit",
                number(current.parallel()), current.processingTicks() > 0 ? current.processingTicks() + " tick" : "×" + number(current.speedMultiplier())),
                136, 76, 176, AeUiTheme.PRIMARY_TEXT);
        if (count > 0) quantityTooltips.add(new QuantityTooltip(136, 76, 176, 11, benefitTooltip("current_quantity_title", current)));
        var benefit = definition.depths().get(Math.max(0, Math.min(round - 1, max - 1)));
        String speed = benefit.processingTicks() > 0 ? benefit.processingTicks() + " tick"
                : "×" + number(benefit.speedMultiplier());
        fitted(graphics, maxed ? text("max_hint") : Component.translatable("gui.molecularmanipulator.research.benefit", number(benefit.parallel()), speed),
                136, 88, 176, AeUiTheme.MUTED_TEXT);
        if (!maxed) quantityTooltips.add(new QuantityTooltip(136, 88, 176, 11, benefitTooltip("next_quantity_title", benefit)));
        AeUiTheme.progress(graphics, 136, 101, 176, 6, Math.min(1, progress / (float) definition.duration()), AeUiTheme.CYAN);
        fitted(graphics, Component.translatable("gui.molecularmanipulator.research.timing", progress / 20,
                definition.duration() / 20, DisplayNumbers.compact(definition.aePerTick())),
                136, 111, 176, AeUiTheme.MUTED_TEXT);
        if (!orderStatus.equals("idle")) {
            fitted(graphics, orderState(orderStatus), 136, 123, 120,
                    orderStatus.equals("submitted") ? AeUiTheme.SUCCESS : AeUiTheme.WARNING);
            quantityTooltips.add(new QuantityTooltip(136, 121, 120, 15, List.of(
                    orderState(orderStatus), Component.translatable("gui.molecularmanipulator.research.order_counts",
                            orderQueued, orderActive))));
        }
        quantityTooltips.add(new QuantityTooltip(136, 111, 176, 11, List.of(Component.translatable(
                "gui.molecularmanipulator.research.timing", progress / 20, definition.duration() / 20,
                DisplayNumbers.exact(definition.aePerTick())))));
        if (showOrderDetails) {
            drawOrderDetails(graphics);
            return;
        }
        List<MatterResearchRecipe.Cost> costs;
        try { costs = maxed ? List.of() : batch ? batchCosts(definition, firstRound, round) : definition.costsFor(round); } catch (ArithmeticException error) { costs = List.of(); }
        var level = Minecraft.getInstance().level;
        var recipeIndex = MatterRecipeIndex.get(level);
        var unlocks = recipeIndex.unlocksForResearch(holder).stream()
                .filter(id -> recipeIndex.fabrication(id) != null || level.getRecipeManager().byKey(id).isPresent()).toList();
        int prerequisiteHeight = holder.value().prerequisites().isEmpty() ? 0 : 15 + 16 * holder.value().prerequisites().size();
        contentHeight = prerequisiteHeight + 17 + costs.size() * 27 + 20 + unlocks.size() * 20;
        scroll = Math.max(0, Math.min(scroll, Math.max(0, contentHeight - SCROLL_HEIGHT)));
        // Minecraft 1.21's scissor coordinates are screen-space and do not inherit the pose stack.
        var matrix = graphics.pose().last().pose();
        var clipStart = matrix.transformPosition(new Vector3f(132, SCROLL_TOP, 0));
        var clipEnd = matrix.transformPosition(new Vector3f(317, SCROLL_BOTTOM, 0));
        graphics.enableScissor((int) Math.floor(clipStart.x), (int) Math.floor(clipStart.y),
                (int) Math.ceil(clipEnd.x), (int) Math.ceil(clipEnd.y));
        try {
            int y = SCROLL_TOP + 3 - (int) scroll;
            if (prerequisiteHeight > 0) {
                fitted(graphics, text("prerequisites"), 136, y, 176, AeUiTheme.PRIMARY_TEXT); y += 15;
                for (var id : holder.value().prerequisites()) {
                    var parent = definitions.stream().filter(value -> value.id().equals(id)).findFirst();
                    int required = parent.map(value -> MatterResearchApi.requiredPrerequisiteLevel(holder.value(), value)).orElse(1);
                    Component label = parent.map(value -> Component.translatable(value.value().title())
                            .append(" · " + Math.min(count(value), required) + "/" + required))
                            .orElse(Component.literal(id.toString()));
                    fitted(graphics, label, 140, y, 168,
                            parent.map(value -> count(value) >= required).orElse(false) ? AeUiTheme.SUCCESS : AeUiTheme.WARNING);
                    y += 16;
                }
            }
            fitted(graphics, text(maxed ? "max_hint" : batch ? "materials_max" : task == null ? "materials" : "materials_committed"), 136, y, 176, AeUiTheme.PRIMARY_TEXT); y += 17;
            for (int index = 0; index < costs.size(); index++) {
                var cost = costs.get(index);
                var examples = cost.ingredient().getItems();
                var stack = examples.length == 0 ? ItemStack.EMPTY : examples[0];
                long paid = task == null ? 0 : task.getAsJsonArray("paid").get(index).getAsLong();
                long required = cost.count() - paid;
                String availableField=batch && preparation==null && task==null ? "batch_available" : "available";
                long available=liveForSelected() && live.has(availableField) && index<live.getAsJsonArray(availableField).size()
                        ?live.getAsJsonArray(availableField).get(index).getAsLong():0;
                long reserved=preparation!=null && liveForSelected() && live.has("reserved") && index<live.getAsJsonArray("reserved").size()
                        ?live.getAsJsonArray("reserved").get(index).getAsLong():0;
                long supplied=available>Long.MAX_VALUE-reserved?Long.MAX_VALUE:available+reserved;
                if (y > 104 && y < 237) {
                    graphics.renderItem(stack, 136, y);
                    fitted(graphics, stack.getHoverName(), 156, y, 152, AeUiTheme.PRIMARY_TEXT);
                    fitted(graphics, Component.literal(number(supplied) + " / " + number(required)), 156, y + 11, 152,
                            supplied >= required ? AeUiTheme.SUCCESS : AeUiTheme.MUTED_TEXT);
                    var details = new ArrayList<Component>();
                    details.add(stack.getHoverName());
                    details.add(quantity("available", available));
                    details.add(quantity("required", cost.count()));
                    if(preparation!=null)details.add(quantity("reserved",reserved));
                    if (task != null) {
                        details.add(quantity("committed", paid));
                        details.add(quantity("remaining", required));
                    }
                    int top = Math.max(SCROLL_TOP, y), bottom = Math.min(SCROLL_BOTTOM, y + 24);
                    if (bottom > top) quantityTooltips.add(new QuantityTooltip(136, top, 176, bottom - top, List.copyOf(details)));
                }
                y += 27;
            }
            fitted(graphics, text("unlocks"), 136, y, 176, AeUiTheme.PRIMARY_TEXT); y += 20;
            for (var id : unlocks) {
                var recipe = level.getRecipeManager().byKey(id);
                var imported = recipeIndex.fabrication(id);
                var stack = recipe.map(value -> value.value().getResultItem(level.registryAccess())).orElse(ItemStack.EMPTY);
                if (stack.isEmpty() && imported != null) stack = imported.value().getResultItem(level.registryAccess());
                var fabrication = imported == null ? null : imported.value();
                if (fabrication == null && recipe.isPresent()
                        && recipe.get().value() instanceof com.atir.molecularmanipulator.crafting.MatterFabricationRecipe value) {
                    fabrication = value;
                }
                appeng.api.stacks.GenericStack resource = null;
                if (stack.isEmpty() && fabrication != null) {
                    if (!fabrication.fluidResult().isEmpty()) {
                        resource = new appeng.api.stacks.GenericStack(
                                appeng.api.stacks.AEFluidKey.of(fabrication.fluidResult()),
                                fabrication.fluidResult().getAmount());
                    } else if (!fabrication.aeOutputs().isEmpty()) {
                        resource = fabrication.aeOutputs().getFirst();
                    }
                }
                Component name = resource != null ? resource.what().getDisplayName()
                        : stack.isEmpty() ? Component.literal(id.toString()) : stack.getHoverName();
                if (imported != null) {
                    int branches = recipeIndex.branchCountForResearch(holder.id(), imported.value());
                    if (branches > 1) name = name.copy().append(" ").append(Component.translatable(
                            "gui.molecularmanipulator.research.branch_recipes", branches));
                }
                if (y > 104 && y < 237) {
                    if (resource == null) graphics.renderItem(stack, 136, y);
                    else AEStackIcon.draw(graphics, resource, 136, y);
                    fitted(graphics, name, 156, y + 3, 152, AeUiTheme.MUTED_TEXT);
                }
                y += 20;
            }
        } finally { graphics.disableScissor(); }
        if (contentHeight > SCROLL_HEIGHT) {
            int thumb = Math.max(12, SCROLL_HEIGHT * SCROLL_HEIGHT / contentHeight);
            int y = SCROLL_TOP + (int) ((SCROLL_HEIGHT - thumb) * scroll / (contentHeight - SCROLL_HEIGHT));
            graphics.fill(315, y, 317, y + thumb, AeUiTheme.ACCENT);
        }
    }

    private void drawOrderDetails(GuiGraphics graphics) {
        contentHeight = orderEntries.isEmpty() ? SCROLL_HEIGHT : 18;
        for (var entry : orderEntries) contentHeight += 31 + entry.missing().size() * 22;
        scroll = Math.max(0, Math.min(scroll, Math.max(0, contentHeight - SCROLL_HEIGHT)));
        var matrix = graphics.pose().last().pose();
        var clipStart = matrix.transformPosition(new Vector3f(132, SCROLL_TOP, 0));
        var clipEnd = matrix.transformPosition(new Vector3f(317, SCROLL_BOTTOM, 0));
        graphics.enableScissor((int) Math.floor(clipStart.x), (int) Math.floor(clipStart.y),
                (int) Math.ceil(clipEnd.x), (int) Math.ceil(clipEnd.y));
        try {
            int y = SCROLL_TOP + 3 - (int) scroll;
            if (orderEntries.isEmpty()) {
                fitted(graphics, text("order_empty"), 136, y, 176, AeUiTheme.MUTED_TEXT);
            } else {
                int missingTypes = orderEntries.stream().mapToInt(entry -> entry.missing().size()).sum();
                fitted(graphics, Component.translatable("gui.molecularmanipulator.research.order_missing_count",
                        missingTypes), 136, y, 176, AeUiTheme.WARNING);
                y += 18;
                for (var entry : orderEntries) {
                    if (y + 27 > SCROLL_TOP && y < SCROLL_BOTTOM) {
                        AEStackIcon.draw(graphics, entry.target(), 136, y);
                        fitted(graphics, entry.target().what().getDisplayName(), 156, y, 156,
                                AeUiTheme.PRIMARY_TEXT);
                        fitted(graphics, Component.translatable("gui.molecularmanipulator.research.order_target_status",
                                DisplayNumbers.exact(entry.target().amount()), orderState(entry.status())),
                                156, y + 11, 156, entry.missing().isEmpty()
                                        && entry.status().equals("submitted") ? AeUiTheme.SUCCESS : AeUiTheme.WARNING);
                        int top = Math.max(SCROLL_TOP, y), bottom = Math.min(SCROLL_BOTTOM, y + 27);
                        if (bottom > top) quantityTooltips.add(new QuantityTooltip(136, top, 176, bottom - top,
                                List.of(entry.target().what().getDisplayName(),
                                        quantity("required", entry.target().amount()), orderState(entry.status()),
                                        Component.translatable("gui.molecularmanipulator.research.order_counts",
                                                entry.queued(), entry.active()))));
                    }
                    y += 31;
                    for (var missing : entry.missing()) {
                        if (y + 20 > SCROLL_TOP && y < SCROLL_BOTTOM) {
                            AEStackIcon.draw(graphics, missing, 144, y);
                            fitted(graphics, missing.what().getDisplayName(), 164, y, 148,
                                    AeUiTheme.WARNING);
                            fitted(graphics, Component.translatable(
                                    "gui.molecularmanipulator.research.order_missing_amount",
                                    DisplayNumbers.exact(missing.amount())), 164, y + 11, 148,
                                    AeUiTheme.MUTED_TEXT);
                            int top = Math.max(SCROLL_TOP, y), bottom = Math.min(SCROLL_BOTTOM, y + 20);
                            if (bottom > top) quantityTooltips.add(new QuantityTooltip(144, top, 168, bottom - top,
                                    List.of(missing.what().getDisplayName(), quantity("remaining", missing.amount()))));
                        }
                        y += 22;
                    }
                }
            }
        } finally { graphics.disableScissor(); }
        if (contentHeight > SCROLL_HEIGHT) {
            int thumb = Math.max(12, SCROLL_HEIGHT * SCROLL_HEIGHT / contentHeight);
            int y = SCROLL_TOP + (int) ((SCROLL_HEIGHT - thumb) * scroll / (contentHeight - SCROLL_HEIGHT));
            graphics.fill(315, y, 317, y + thumb, AeUiTheme.ACCENT);
        }
    }

    boolean scroll(double x, double y, double amount) {
        if (!visible || x < 128 || x >= 320 || y < SCROLL_TOP || y >= SCROLL_BOTTOM) return false;
        scroll = Math.max(0, Math.min(scroll - amount * 27, Math.max(0, contentHeight - SCROLL_HEIGHT)));
        return true;
    }

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

    record Selection(ResourceLocation id, int page, double scroll) {}
    Selection selection() { return new Selection(selected, page, scroll); }
    void restoreSelection(Selection selection) {
        if (selection != null) { selected = selection.id(); page = selection.page(); scroll = selection.scroll(); }
    }
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
    private static String number(long amount) {
        return DisplayNumbers.compact(amount);
    }
    private static Component quantity(String key, long amount) {
        return Component.translatable("gui.molecularmanipulator.research.quantity_" + key, DisplayNumbers.exact(amount));
    }
    private static List<Component> benefitTooltip(String title, ResearchDepth depth) {
        return List.of(text(title), quantity("parallel", depth.parallel()), depth.processingTicks() > 0
                ? quantity("ticks", depth.processingTicks()) : quantity("speed", depth.speedMultiplier()));
    }
    List<Component> tooltipAt(double x, double y) {
        if (!visible) return List.of();
        return quantityTooltips.stream().filter(tip -> x >= tip.x() && x < tip.x() + tip.width()
                && y >= tip.y() && y < tip.y() + tip.height()).findFirst().map(QuantityTooltip::lines).orElse(List.of());
    }
    private record QuantityTooltip(int x, int y, int width, int height, List<Component> lines) {}
    private Component shortLabel(Component label, int width) {
        return font.width(label) <= width ? label : Component.literal(font.plainSubstrByWidth(label.getString(), width - font.width("…")) + "…");
    }
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
    private void fitted(GuiGraphics graphics, Component text, int x, int y, int width, int color) {
        float scale = Math.min(1, width / (float) Math.max(1, font.width(text)));
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(scale, scale, 1);
        graphics.drawString(font, text, 0, 0, color, false);
        graphics.pose().popPose();
    }

}
