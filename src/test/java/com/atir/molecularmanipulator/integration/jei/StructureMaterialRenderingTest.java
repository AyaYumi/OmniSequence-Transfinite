package com.atir.molecularmanipulator.integration.jei;

import com.atir.molecularmanipulator.client.OmniUiTheme;
import com.atir.molecularmanipulator.client.UiRenderRecorder;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StructureMaterialRenderingTest {
    @Test void quantitiesHaveOpaqueBadgesBelowTheModelAndFitTheirIngredientBounds() {
        var font = new UiRenderRecorder.MetricsFont();
        for (int count : new int[] {1, 32, 267, 817, 9999, Integer.MAX_VALUE}) {
            var graphics = new UiRenderRecorder();
            StructureMaterialRenderer.drawQuantity(graphics, font, count);
            assertEquals(1, graphics.rects.size());
            var badge = graphics.rects.get(0);
            assertEquals(OmniUiTheme.QUANTITY_BACKGROUND, badge.color());
            assertTrue(badge.top() >= 17, "Keep the quantity clear of the 16-pixel model");
            assertEquals(1, graphics.text.size());
            var text = graphics.text.get(0);
            assertEquals(Integer.toString(count), text.value());
            assertFalse(text.shadow());
            assertEquals(OmniUiTheme.HIGHLIGHT, text.color());
            var left = text.transform().transformPosition(new Vector3f(text.x(), text.y(), 0));
            var right = text.transform().transformPosition(new Vector3f(text.x() + font.width(text.value()), text.y() + 8, 0));
            assertTrue(left.x >= badge.left() && right.x <= badge.right());
            assertTrue(left.y >= badge.top() && right.y <= badge.bottom());
            assertEquals(new Matrix4f(), graphics.pose().last().pose(), "Quantity rendering must restore the pose");
        }
    }

    @Test void ingredientHoverBoundsAndBadgesDoNotOverlapAdjacentMaterials() {
        var renderer = StructureMaterialRenderer.INSTANCE;
        assertTrue(renderer.getWidth() < StructureJeiLayout.materialX(1) - StructureJeiLayout.materialX(0));
        assertTrue(renderer.getHeight() < StructureJeiLayout.MATERIAL_ROW_HEIGHT);
        for (int index = 0; index < 14; index++) {
            assertTrue(StructureJeiLayout.materialX(index) >= 3);
            assertTrue(StructureJeiLayout.materialX(index) + renderer.getWidth() <= 195);
        }
    }
}
