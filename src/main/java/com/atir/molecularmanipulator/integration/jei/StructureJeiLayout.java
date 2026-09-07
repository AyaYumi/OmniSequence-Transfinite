package com.atir.molecularmanipulator.integration.jei;

import com.atir.molecularmanipulator.client.AeUiTheme;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.constants.VanillaTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import java.util.List;

/** Material counts need more horizontal room than a tightly packed inventory grid. */
final class StructureJeiLayout {
    private StructureJeiLayout() { }

    static int previewHeight() { return previewHeight(Minecraft.getInstance().getWindow().getGuiScaledHeight()); }
    static int previewHeight(int screenHeight) { return Math.clamp(screenHeight - 142, 70, 168); }
    static int height() { return previewHeight() + 58; }

    static void setRecipe(IRecipeLayoutBuilder builder, List<ItemStack> materials, ItemStack controller) {
        int index = 0;
        for (var material : materials) {
            if (material.isEmpty()) continue;
            builder.addInputSlot(materialX(index), materialY(index))
                    .setStandardSlotBackground().setCustomRenderer(VanillaTypes.ITEM_STACK, StructureMaterialRenderer.INSTANCE)
                    .addItemStack(material);
            index++;
        }
        builder.addOutputSlot(219, previewHeight() + 28).setOutputSlotBackground().addItemStack(controller);
    }

    static int materialX(int index) { return 12 + index % 7 * 27; }
    static int materialY(int index) { return previewHeight() + 18 + index / 7 * 20; }

    static void drawMaterials(GuiGraphics graphics) {
        int top = previewHeight();
        AeUiTheme.panel(graphics, 0, top, InteractiveStructurePreviewWidget.WIDTH, top + 58);
        AeUiTheme.insetPanel(graphics, 3, top + 2, 195, top + 57);
        AeUiTheme.insetPanel(graphics, 199, top + 2, 245, top + 57);
        InteractiveStructurePreviewWidget.text(graphics,
                Component.translatable("gui.molecularmanipulator.jei_materials"),
                8, top + 6, 181, AeUiTheme.PRIMARY_TEXT, false);
        InteractiveStructurePreviewWidget.text(graphics,
                Component.translatable("gui.molecularmanipulator.jei_structure_controller"),
                201, top + 6, 42, AeUiTheme.PRIMARY_TEXT, true);
    }
}
