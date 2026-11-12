package com.atir.molecularmanipulator.integration.jei;

import appeng.api.client.AEKeyRendering;
import com.atir.molecularmanipulator.ae2.StarSignalKey;
import java.util.List;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.registration.IModIngredientRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.TooltipFlag;
import org.jetbrains.annotations.Nullable;

/** Registers the mod's non-item AE resource independently of optional JEI converters. */
final class StarSignalJeiIngredient implements IIngredientHelper<StarSignalKey>, IIngredientRenderer<StarSignalKey> {
    static final IIngredientType<StarSignalKey> TYPE = () -> StarSignalKey.class;

    static void register(IModIngredientRegistration registration) {
        var ingredient = new StarSignalJeiIngredient();
        registration.register(TYPE, List.of(StarSignalKey.INSTANCE), ingredient, ingredient, StarSignalKey.CODEC);
    }

    @Override
    public IIngredientType<StarSignalKey> getIngredientType() {
        return TYPE;
    }

    @Override
    public String getDisplayName(StarSignalKey ingredient) {
        return ingredient.getDisplayName().getString();
    }

    @Override
    @SuppressWarnings("removal")
    public String getUniqueId(StarSignalKey ingredient, UidContext context) {
        return ingredient.getId().toString();
    }

    @Override
    public ResourceLocation getResourceLocation(StarSignalKey ingredient) {
        return ingredient.getId();
    }

    @Override
    public StarSignalKey copyIngredient(StarSignalKey ingredient) {
        return ingredient;
    }

    @Override
    public String getErrorInfo(@Nullable StarSignalKey ingredient) {
        return String.valueOf(ingredient);
    }

    @Override
    public void render(GuiGraphics graphics, StarSignalKey ingredient) {
        AEKeyRendering.drawInGui(Minecraft.getInstance(), graphics, 0, 0, ingredient);
    }

    @Override
    public List<Component> getTooltip(StarSignalKey ingredient, TooltipFlag flag) {
        return AEKeyRendering.getTooltip(ingredient);
    }
}
