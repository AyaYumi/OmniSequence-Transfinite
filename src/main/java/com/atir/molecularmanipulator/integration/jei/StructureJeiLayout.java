package com.atir.molecularmanipulator.integration.jei;

import com.atir.molecularmanipulator.client.OmniUiTheme;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.constants.VanillaTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import java.util.List;

/** Material counts need more horizontal room than a tightly packed inventory grid. */
final class StructureJeiLayout {
    static final int MATERIAL_HEIGHT = 76;
    static final int MATERIAL_ROW_HEIGHT = 28;
    private StructureJeiLayout() { }

    static int previewHeight() { return previewHeight(Minecraft.getInstance().getWindow().getGuiScaledHeight()); }
    static int previewHeight(int screenHeight) { return com.atir.molecularmanipulator.util.MathCompat.clamp(screenHeight - 160, 70, 168); }
    static int height() { return previewHeight() + MATERIAL_HEIGHT; }

    static void setRecipe(IRecipeLayoutBuilder builder, List<ItemStack> materials, ItemStack controller) {
        int index = 0;
        for (var material : materials) {
            if (material.isEmpty()) continue;
            builder.addInputSlot(materialX(index), materialY(index))
                    .setBackground(ConsoleSlotBackground.INPUT, 3, -1).setCustomRenderer(VanillaTypes.ITEM_STACK, StructureMaterialRenderer.INSTANCE)
                    .addItemStack(material);
            index++;
        }
        builder.addOutputSlot(219, previewHeight() + 36).setBackground(ConsoleSlotBackground.OUTPUT, -1, -1).addItemStack(controller);
    }

    static int materialX(int index) { return 8 + index % 7 * 27; }
    static int materialY(int index) { return materialY(index, previewHeight()); }
    static int materialY(int index, int previewHeight) { return previewHeight + 18 + index / 7 * MATERIAL_ROW_HEIGHT; }

    static void drawMaterials(GuiGraphics graphics) {
        int top = previewHeight();
        OmniUiTheme.panel(graphics, 0, top, InteractiveStructurePreviewWidget.WIDTH, top + MATERIAL_HEIGHT);
        OmniUiTheme.insetPanel(graphics, 3, top + 2, 195, top + MATERIAL_HEIGHT - 1);
        OmniUiTheme.insetPanel(graphics, 199, top + 2, 245, top + MATERIAL_HEIGHT - 1);
        InteractiveStructurePreviewWidget.text(graphics,
                Component.translatable("gui.molecularmanipulator.jei_materials"),
                8, top + 6, 181, OmniUiTheme.PRIMARY_TEXT, false);
        InteractiveStructurePreviewWidget.text(graphics,
                Component.translatable("gui.molecularmanipulator.jei_structure_controller"),
                201, top + 6, 42, OmniUiTheme.PRIMARY_TEXT, true);
    }
}
