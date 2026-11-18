package com.atir.molecularmanipulator.client;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NeighborPreviewTest {
    @Test void eachDirectionKeepsItsOwnThumbnailClearOfTheDirectionText() throws Exception {
        var graphics = new UiRenderRecorder();
        var ui = MolecularCenterControlsTest.layout("fab_port_item");
        var render = java.util.Arrays.stream(OsqUi.SkinButton.class.getDeclaredMethods())
                .filter(method -> method.getName().equals("renderContents")).findFirst().orElseThrow();
        render.setAccessible(true);
        var blocks = new net.minecraft.world.item.Item[]{Items.STONE, Items.GLASS, Items.FURNACE,
                Items.CRAFTING_TABLE, Items.BARREL, Items.OAK_PLANKS};
        for (int i = 0; i < blocks.length; i++) {
            var side = OsqNeighborDirections.OUTPUT_SIDES[i];
            var button = ui.button(OsqNeighborDirections.IDS[i], 0, 0, () -> OsqNeighborDirections.label(side), () -> {});
            var stack = new ItemStack(blocks[i]);
            ui.itemIcon(button, () -> stack);
            render.invoke(button, graphics, "button", "normal", button.getHeight()/2f, null);
            var thumbnail = graphics.items.get(i);
            assertEquals(blocks[i], thumbnail.stack().getItem());
            assertEquals(button.getX() + (button.getWidth()-16)/2, thumbnail.x());
            assertEquals(button.getY() + Math.round(button.getHeight()/2f-8), thumbnail.y());
        }
        assertEquals(6, graphics.items.size());
        assertTrue(graphics.text.isEmpty(), "An available thumbnail must not also show a placeholder");
    }

    @Test void missingThumbnailsKeepLocalizedDirectionsAndUnloadedNeighborHints() {
        new UiRenderRecorder();
        for (var side : OsqNeighborDirections.SIDES) {
            assertTrue(OsqNeighborDirections.icon(null, net.minecraft.core.BlockPos.ZERO, side).isEmpty());
            var label = (net.minecraft.network.chat.contents.TranslatableContents) OsqNeighborDirections.label(side).getContents();
            assertTrue(label.getKey().endsWith("side_short." + side.getName()));
            var hint = OsqNeighborDirections.tooltip(null, net.minecraft.core.BlockPos.ZERO, side, 0);
            var neighbor = (net.minecraft.network.chat.contents.TranslatableContents) hint.getSiblings().get(1).getContents();
            var status = (net.minecraft.network.chat.Component) neighbor.getArgs()[0];
            assertTrue(((net.minecraft.network.chat.contents.TranslatableContents) status.getContents()).getKey().endsWith("neighbor_unloaded"));
        }
    }
}
