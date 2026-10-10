package com.atir.molecularmanipulator.integration.jei;

import com.atir.molecularmanipulator.MolecularManipulator;
import com.atir.molecularmanipulator.crafting.MatterFabricationPatternEncoding;
import com.atir.molecularmanipulator.crafting.MatterFabricationRecipe;
import com.atir.molecularmanipulator.crafting.MatterRecipeIndex;
import com.atir.molecularmanipulator.network.MatterFabricationUploadPayload;
import java.util.ArrayList;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.buttons.IButtonState;
import mezz.jei.api.gui.buttons.IIconButtonController;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.inputs.IJeiUserInput;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.advanced.IRecipeButtonControllerFactory;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.PacketDistributor;

/** JEI places custom side buttons above transfer/bookmark buttons, matching the recipe sheet. */
public final class MatterFabricationUploadButton implements IRecipeButtonControllerFactory {
    private static IJeiRuntime runtime;
    private final IDrawable icon;

    public MatterFabricationUploadButton(IGuiHelper helper) {
        icon = helper.drawableBuilder(MolecularManipulator.id("textures/gui/fabrication_pattern_upload.png"), 0, 0, 10, 10)
                .setTextureSize(10, 10).build();
    }

    public static void setRuntime(IJeiRuntime value) { runtime = value; }

    @Override
    public <T> IIconButtonController createButtonController(IRecipeLayoutDrawable<T> layout) {
        if (!layout.getRecipeCategory().getRecipeType().equals(MatterFabricationJeiCategory.TYPE)
                || !(layout.getRecipe() instanceof MatterFabricationRecipe recipe)) return null;
        return new IIconButtonController() {
            @Override public void initState(IButtonState state) {
                state.setIcon(icon);
                updateState(state);
            }

            @Override public void updateState(IButtonState state) {
                boolean encoding = encodingParent() != null;
                state.setVisible(encoding);
                state.setActive(encoding);
            }

            @Override public void getTooltips(ITooltipBuilder tooltip) {
                tooltip.add(Component.translatable("gui.molecularmanipulator.fabrication.upload"));
                tooltip.add(Component.translatable("gui.molecularmanipulator.fabrication.upload_hint"));
            }

            @Override public boolean onPress(IJeiUserInput input) {
                var screen = encodingParent();
                var level = Minecraft.getInstance().level;
                if (screen == null || level == null) return false;
                if (input.isSimulate()) return true;
                var holder = MatterRecipeIndex.get(level).fabrication().stream()
                        .filter(value -> value.value() == recipe).findFirst().orElse(null);
                if (holder == null) return false;
                var selections = new ArrayList<ItemStack>();
                for (int i = 0; i < recipe.ingredients().size(); i++) {
                    var slot = layout.getRecipeSlotsView().findSlotByName("fabrication_input_" + i).orElse(null);
                    var selected = slot == null ? ItemStack.EMPTY : slot.getDisplayedItemStack().orElse(ItemStack.EMPTY);
                    if (selected.isEmpty()) return false;
                    selections.add(selected.copyWithCount(1));
                }
                MatterFabricationUploadPayload.sendToServer(new MatterFabricationUploadPayload(screen.getMenu().containerId, holder.id(), selections));
                return true;
            }
        };
    }

    private static AbstractContainerScreen<?> encodingParent() {
        var player = Minecraft.getInstance().player;
        if (runtime == null || player == null) return null;
        var parent = runtime.getRecipesGui().getParentScreen().orElse(null);
        if (parent instanceof AbstractContainerScreen<?> screen && screen.getMenu() == player.containerMenu
                && MatterFabricationPatternEncoding.isEncodingMenu(screen.getMenu())) return screen;
        return null;
    }
}
