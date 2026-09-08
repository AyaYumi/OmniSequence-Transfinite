package com.atir.molecularmanipulator.client;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NeighborPreviewTest {
    @Test void eachDirectionKeepsItsOwnThumbnailClearOfTheDirectionText() {
        var graphics = new UiRenderRecorder();
        var font = new UiRenderRecorder.MetricsFont();
        var blocks = new net.minecraft.world.item.Item[]{Items.STONE, Items.GLASS, Items.FURNACE,
                Items.CRAFTING_TABLE, Items.BARREL, Items.OAK_PLANKS};
        for (int i = 0; i < blocks.length; i++) {
            int x = 130 + i % 2 * 52, y = 94 + i / 2 * 24;
            MatterFabricationPortScreen.drawNeighborIcon(graphics, font, new ItemStack(blocks[i]), null, true, x, y);
            var thumbnail = graphics.items.get(i);
            assertEquals(blocks[i], thumbnail.stack().getItem());
            assertEquals(x + 3, thumbnail.x());
            assertEquals(y + 1, thumbnail.y());
            assertTrue(thumbnail.x() + 16 < x + 22, "Leave a gap before the direction label");
            assertTrue(thumbnail.y() + 16 < y + 20);
        }
        assertEquals(6, graphics.items.size());
        assertTrue(graphics.text.isEmpty(), "An available thumbnail must not also show a placeholder");
    }

    @Test void missingThumbnailsUseShadowlessPlaceholdersWithoutDrawingEmptyStacks() {
        var graphics = new UiRenderRecorder();
        var font = new UiRenderRecorder.MetricsFont();
        MatterFabricationPortScreen.drawNeighborIcon(graphics, font, ItemStack.EMPTY, null, false, 0, 0);
        MatterFabricationPortScreen.drawNeighborIcon(graphics, font, ItemStack.EMPTY, null, true, 0, 24);
        assertTrue(graphics.items.isEmpty());
        assertEquals("–", graphics.text.get(0).value());
        assertEquals("?", graphics.text.get(1).value());
        assertTrue(graphics.text.stream().noneMatch(UiRenderRecorder.Text::shadow));
    }
}
