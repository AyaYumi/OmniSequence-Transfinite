package com.atir.molecularmanipulator.integration.jei;

import com.atir.molecularmanipulator.MolecularManipulator;
import com.atir.molecularmanipulator.client.OmniUiTheme;
import com.atir.molecularmanipulator.client.DisplayNumbers;
import com.atir.molecularmanipulator.crafting.MatterFabricationRecipe;
import com.atir.molecularmanipulator.registry.ModContent;
import com.atir.molecularmanipulator.research.MatterResearchApi;
import java.util.*;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Compact AE recipe sheet: research banner, centered material flow, base time/power footer. */
public final class MatterFabricationJeiCategory implements IRecipeCategory<MatterFabricationRecipe> {
    public static final RecipeType<MatterFabricationRecipe> TYPE = new RecipeType<>(
            MolecularManipulator.id("matter_fabrication_processing"), MatterFabricationRecipe.class);
    private final IDrawable icon, slot;
    private final Map<MatterFabricationRecipe, StageInfo> stages = new WeakHashMap<>();

    public MatterFabricationJeiCategory(IGuiHelper helper) {
        icon = helper.createDrawableItemStack(new ItemStack(ModContent.MATTER_FABRICATION_CONTROLLER_ITEM.get()));
        slot = ConsoleSlotBackground.INPUT;
    }
    @Override public RecipeType<MatterFabricationRecipe> getRecipeType() { return TYPE; }
    @Override public Component getTitle() { return Component.translatable("gui.molecularmanipulator.fabrication.jei_title"); }
    @Override public IDrawable getIcon() { return icon; }
    @Override public int getWidth() { return 184; }
    @Override public int getHeight() { return 100; }
    private static int inputColumns(int count) { return count == 4 ? 2 : Math.min(3, count); }
    public static int inputX(int count, int index) {
        int columns = inputColumns(count), rowItems = Math.min(columns, count - index / columns * columns);
        return 8 + (3 - rowItems) * 9 + index % columns * 18;
    }
    public static int inputY(int count, int index) {
        int columns = inputColumns(count), rows = (count + columns - 1) / columns;
        return 24 + (3 - rows) * 9 + index / columns * 18;
    }

    @Override public void setRecipe(IRecipeLayoutBuilder builder, MatterFabricationRecipe recipe, IFocusGroup focuses) {
        stages.put(recipe, resolveStage(recipe));
        for (int i = 0; i < recipe.ingredients().size(); i++) {
            var counted = recipe.ingredients().get(i);
            builder.addInputSlot(inputX(recipe.ingredients().size(), i), inputY(recipe.ingredients().size(), i))
                    .setBackground(slot, -1, -1)
                    .addItemStacks(Arrays.stream(counted.ingredient().getItems()).map(s -> s.copyWithCount(counted.count())).toList());
        }
        if (!recipe.fluidInput().isEmpty()) builder.addInputSlot(recipe.ingredients().isEmpty() ? 35 : 72, 42)
                .setBackground(slot, -1, -1).addFluidStack(recipe.fluidInput().getFluid(), recipe.fluidInput().getAmount())
                .setFluidRenderer(recipe.fluidInput().getAmount(), false, 16, 16);
        int outputs = recipe.results().size() + (recipe.fluidResult().isEmpty() ? 0 : 1);
        for (int i = 0; i < recipe.results().size(); i++) builder.addOutputSlot(outputX(outputs, i), outputY(outputs, i))
                .setBackground(ConsoleSlotBackground.OUTPUT, -1, -1).addItemStack(recipe.results().get(i));
        if (!recipe.fluidResult().isEmpty()) builder.addOutputSlot(outputX(outputs, outputs - 1), outputY(outputs, outputs - 1))
                .setBackground(ConsoleSlotBackground.OUTPUT, -1, -1).addFluidStack(recipe.fluidResult().getFluid(), recipe.fluidResult().getAmount())
                .setFluidRenderer(recipe.fluidResult().getAmount(), false, 16, 16);
    }
    private static int outputX(int count, int index) { return Math.min(2, count - index / 2 * 2) == 1 ? 146 : 136 + index % 2 * 20; }
    private static int outputY(int count, int index) { return count <= 2 ? 42 : 33 + index / 2 * 20; }

    @Override public void draw(MatterFabricationRecipe recipe, IRecipeSlotsView slots, GuiGraphics graphics, double mouseX, double mouseY) {
        var font = Minecraft.getInstance().font;
        OmniUiTheme.panel(graphics, 0, 0, 184, 100);
        graphics.fill(1, 1, 183, 19, OmniUiTheme.PANEL_INSET);
        var info = stages.getOrDefault(recipe, new StageInfo(text("unassigned"), List.of(), OmniUiTheme.WARNING));
        graphics.fill(0, 0, 3, 19, info.color());
        var label = info.label();
        if (font.width(label) > 168) label = Component.literal(font.plainSubstrByWidth(label.getString(), 168 - font.width("…")) + "…");
        graphics.drawString(font, label, 8, 5, OmniUiTheme.PRIMARY_TEXT, false);
        graphics.fill(98, 49, 117, 52, OmniUiTheme.SHADOW);
        for (int i = 0; i < 4; i++) graphics.fill(115 + i, 46 + i, 116 + i, 55 - i, OmniUiTheme.SHADOW);
        int duration = Math.max(20, recipe.processingTime());
        int progress = (int) (18 * (Util.getMillis() / 50 % duration) / duration);
        graphics.fill(98, 50, 98 + progress, 51, OmniUiTheme.CYAN);
        graphics.fill(8, 81, 176, 82, OmniUiTheme.BORDER);
        OmniUiTheme.label(graphics, font, text("time", format(recipe.processingTime() / 20.0)), 8, 87, 74, OmniUiTheme.PRIMARY_TEXT);
        var power = Component.literal(format(recipe.aePerTick()) + " AE/t");
        OmniUiTheme.label(graphics, font, power, 176 - Math.min(90, font.width(power)), 87, 90, OmniUiTheme.PRIMARY_TEXT);
    }

    @Override public void getTooltip(ITooltipBuilder tooltip, MatterFabricationRecipe recipe, IRecipeSlotsView slots, double mouseX, double mouseY) {
        tooltip.addAll(tooltipLines(recipe, mouseY));
    }
    List<Component> tooltipLines(MatterFabricationRecipe recipe, double mouseY) {
        if (mouseY < 19) return stages.getOrDefault(recipe, resolveStage(recipe)).tooltip();
        if (mouseY >= 81) return List.of(text("base_stats"), text("time", DisplayNumbers.exact(recipe.processingTime() / 20.0)),
                Component.literal(DisplayNumbers.exact(recipe.aePerTick()) + " AE/t"));
        return List.of();
    }
    @Override public boolean needsRecipeBorder() { return false; }

    private static StageInfo resolveStage(MatterFabricationRecipe recipe) {
        var level = Minecraft.getInstance().level;
        if (level != null) {
            var ids = level.getRecipeManager().getAllRecipesFor(ModContent.MATTER_FABRICATION_RECIPE_TYPE.get()).stream()
                    .filter(holder -> holder.value() == recipe).map(holder -> holder.id()).toList();
            var owners = MatterResearchApi.definitions(level).stream()
                    .filter(holder -> holder.value().unlocks().stream().anyMatch(ids::contains)).toList();
            if (!owners.isEmpty()) {
                var label = Component.empty(); var tips = new ArrayList<Component>(); tips.add(text("unlock_heading"));
                for (var holder : owners) {
                    var name = Component.translatable(holder.value().title());
                    if (!label.getSiblings().isEmpty()) label.append(" / ");
                    label.append(name);
                    tips.add(text("stage_detail", holder.value().stage(), name));
                }
                tips.add(text("initial_unlock")); tips.add(text("base_stats"));
                return new StageInfo(label, List.copyOf(tips), OmniUiTheme.ACCENT);
            }
        }
        var label = text(recipe.requiresResearch() ? "unassigned" : "no_research");
        return new StageInfo(label, List.of(label), recipe.requiresResearch() ? OmniUiTheme.WARNING : OmniUiTheme.MUTED_TEXT);
    }
    private record StageInfo(Component label, List<Component> tooltip, int color) {}
    private static Component text(String key, Object... args) { return Component.translatable("gui.molecularmanipulator.fabrication.jei." + key, args); }
    private static String format(double value) {
        return DisplayNumbers.compact(value);
    }
}
