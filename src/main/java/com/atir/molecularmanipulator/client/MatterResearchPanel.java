package com.atir.molecularmanipulator.client;

import com.atir.molecularmanipulator.menu.MatterFabricationMenu;
import com.atir.molecularmanipulator.research.MatterResearchApi;
import com.atir.molecularmanipulator.research.MatterResearchRecipe;
import com.atir.molecularmanipulator.research.ResearchDepth;
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
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.joml.Vector3f;

/** Paginated research navigation plus an independently scrolling material/unlock list. */
final class MatterResearchPanel {
    private static final int ROWS = 6;
    private final MatterFabricationMenu menu;
    private final Font font;
    private final List<Button> rows = new ArrayList<>();
    private final Button previous, next, action;
    private List<RecipeHolder<MatterResearchRecipe>> definitions = List.of();
    private Set<String> completed = Set.of();
    private JsonObject tasks = new JsonObject();
    private JsonObject counts = new JsonObject(), live = new JsonObject();
    private String notice = "";
    private String lastState = "";
    private final Map<ResourceLocation, MatterResearchRecipe> taskTerms = new HashMap<>();
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
        action = AeUiTheme.button(left + 136, top + 244, 176, 16, Component.empty(), clicked -> {
            if (selected != null) menu.toggleResearch(selected.toString());
        });
        action.setTooltip(Tooltip.create(text("action_hint")));
        add.accept(previous); add.accept(next); add.accept(action);
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
            counts = state.has("counts") ? state.getAsJsonObject("counts") : new JsonObject();
            live = state.has("live") ? state.getAsJsonObject("live") : new JsonObject();
            notice = state.has("notice") ? state.get("notice").getAsString() : "";
            taskTerms.clear();
            if (level != null) tasks.entrySet().forEach(entry -> MatterResearchRecipe.CODEC.codec()
                    .parse(level.registryAccess().createSerializationContext(JsonOps.INSTANCE), entry.getValue().getAsJsonObject().get("terms"))
                    .result().ifPresent(value -> taskTerms.put(ResourceLocation.parse(entry.getKey()), value)));
        }
        page = Math.max(0, Math.min(page, Math.max(0, (definitions.size() - 1) / ROWS)));
        if (definitions.stream().noneMatch(holder -> holder.id().equals(selected))) {
            selected = definitions.isEmpty() ? null : definitions.getFirst().id();
            scroll = 0;
        }
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
        if (holder != null) {
            var task = task(holder);
            boolean paused = task != null && task.get("paused").getAsBoolean();
            boolean maxed = count(holder) >= holder.value().depths().size();
            action.setMessage(text(task == null ? maxed ? "maxed" : count(holder) == 0 ? "start" : "deepen" : paused ? "resume" : "pause"));
            action.setTooltip(Tooltip.create(text(!notice.isEmpty() ? notice : "action_hint")));
            action.active = (task != null || !maxed)
                    && prerequisitesMet(holder.value())
                    && (task != null && !paused || menu.formed && !menu.building && !menu.dismantling && !menu.updatingStructure
                        && liveForSelected() && live.get("online").getAsBoolean() && live.has("affordable") && live.get("affordable").getAsBoolean());
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
        var definition = terms(holder, task);
        int count = count(holder), max = definition.depths().size();
        boolean maxed = task == null && count >= max;
        int round = task != null ? task.get("round").getAsInt() : Math.min(count + 1, max);
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
        quantityTooltips.add(new QuantityTooltip(136, 111, 176, 11, List.of(Component.translatable(
                "gui.molecularmanipulator.research.timing", progress / 20, definition.duration() / 20,
                DisplayNumbers.exact(definition.aePerTick())))));
        List<MatterResearchRecipe.Cost> costs;
        try { costs = maxed ? List.of() : definition.costsFor(round); } catch (ArithmeticException error) { costs = List.of(); }
        var level = Minecraft.getInstance().level;
        var unlocks = definition.unlocks().stream().filter(id -> level.getRecipeManager().byKey(id).isPresent()).toList();
        int prerequisiteHeight = holder.value().prerequisites().isEmpty() ? 0 : 15 + 16 * holder.value().prerequisites().size();
        contentHeight = prerequisiteHeight + 17 + costs.size() * 27 + 20 + unlocks.size() * 20;
        scroll = Math.max(0, Math.min(scroll, Math.max(0, contentHeight - 111)));
        // Minecraft 1.21's scissor coordinates are screen-space and do not inherit the pose stack.
        var matrix = graphics.pose().last().pose();
        var clipStart = matrix.transformPosition(new Vector3f(132, 126, 0));
        var clipEnd = matrix.transformPosition(new Vector3f(317, 237, 0));
        graphics.enableScissor((int) Math.floor(clipStart.x), (int) Math.floor(clipStart.y),
                (int) Math.ceil(clipEnd.x), (int) Math.ceil(clipEnd.y));
        try {
            int y = 129 - (int) scroll;
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
            fitted(graphics, text(maxed ? "max_hint" : task == null ? "materials" : "materials_committed"), 136, y, 176, AeUiTheme.PRIMARY_TEXT); y += 17;
            for (int index = 0; index < costs.size(); index++) {
                var cost = costs.get(index);
                var examples = cost.ingredient().getItems();
                var stack = examples.length == 0 ? ItemStack.EMPTY : examples[0];
                long paid = task == null ? 0 : task.getAsJsonArray("paid").get(index).getAsLong();
                long required = cost.count() - paid;
                long available = liveForSelected() && live.has("available") && index < live.getAsJsonArray("available").size()
                        ? live.getAsJsonArray("available").get(index).getAsLong() : 0;
                if (y > 104 && y < 237) {
                    graphics.renderItem(stack, 136, y);
                    fitted(graphics, stack.getHoverName(), 156, y, 152, AeUiTheme.PRIMARY_TEXT);
                    fitted(graphics, Component.literal(number(available) + " / " + number(required)), 156, y + 11, 152,
                            available >= required ? AeUiTheme.SUCCESS : AeUiTheme.MUTED_TEXT);
                    var details = new ArrayList<Component>();
                    details.add(stack.getHoverName());
                    details.add(quantity("available", available));
                    details.add(quantity("required", cost.count()));
                    if (task != null) {
                        details.add(quantity("committed", paid));
                        details.add(quantity("remaining", required));
                    }
                    int top = Math.max(126, y), bottom = Math.min(237, y + 24);
                    if (bottom > top) quantityTooltips.add(new QuantityTooltip(136, top, 176, bottom - top, List.copyOf(details)));
                }
                y += 27;
            }
            fitted(graphics, text("unlocks"), 136, y, 176, AeUiTheme.PRIMARY_TEXT); y += 20;
            for (var id : unlocks) {
                var recipe = level.getRecipeManager().byKey(id);
                var stack = recipe.map(value -> value.value().getResultItem(level.registryAccess())).orElse(ItemStack.EMPTY);
                Component name = stack.isEmpty() ? Component.literal(id.toString()) : stack.getHoverName();
                if (stack.isEmpty() && recipe.isPresent()
                        && recipe.get().value() instanceof com.atir.molecularmanipulator.crafting.MatterFabricationRecipe fabrication
                        && !fabrication.fluidResult().isEmpty()) {
                    var fluid = appeng.api.stacks.AEFluidKey.of(fabrication.fluidResult());
                    name = fluid.getDisplayName();
                    stack = appeng.api.stacks.GenericStack.wrapInItemStack(new appeng.api.stacks.GenericStack(fluid, fabrication.fluidResult().getAmount()));
                }
                if (y > 104 && y < 237) {
                    graphics.renderItem(stack, 136, y);
                    fitted(graphics, name, 156, y + 3, 152, AeUiTheme.MUTED_TEXT);
                }
                y += 20;
            }
        } finally { graphics.disableScissor(); }
        if (contentHeight > 111) {
            int thumb = Math.max(12, 111 * 111 / contentHeight);
            int y = 126 + (int) ((111 - thumb) * scroll / (contentHeight - 111));
            graphics.fill(315, y, 317, y + thumb, AeUiTheme.ACCENT);
        }
    }

    boolean scroll(double x, double y, double amount) {
        if (!visible || x < 128 || x >= 320 || y < 126 || y >= 237) return false;
        scroll = Math.max(0, Math.min(scroll - amount * 27, Math.max(0, contentHeight - 111)));
        return true;
    }

    private RecipeHolder<MatterResearchRecipe> selected() {
        return definitions.stream().filter(holder -> holder.id().equals(selected)).findFirst().orElse(null);
    }
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
    private void fitted(GuiGraphics graphics, Component text, int x, int y, int width, int color) {
        float scale = Math.min(1, width / (float) Math.max(1, font.width(text)));
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(scale, scale, 1);
        graphics.drawString(font, text, 0, 0, color, false);
        graphics.pose().popPose();
    }

}
