package com.atir.molecularmanipulator.client;

import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MachineUiLayoutTest {
    @Test void everyFrameMatchesTheRealAe2SlotAndScreenDefinitions() throws Exception {
        for (var layout : MachineUiLayout.values()) {
            String path = "/assets/ae2/screens/" + layout.styleFile + ".json";
            try (var stream = getClass().getResourceAsStream(path)) {
                assertNotNull(stream, path);
                var json = JsonParser.parseString(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
                var background = json.getAsJsonObject("generatedBackground");
                assertEquals(background.get("width").getAsInt(), layout.width, layout.name());
                assertEquals(background.get("height").getAsInt(), layout.height, layout.name());
                for (var frame : layout.slots) {
                    var slot = json.getAsJsonObject("slots").getAsJsonObject(frame.semantic());
                    assertNotNull(slot, layout + ": " + frame.semantic());
                    assertEquals(slot.get("left").getAsInt(), frame.x() + 1, frame.semantic());
                    assertEquals(slot.get("top").getAsInt(), frame.y() + 1, frame.semantic());
                }
            }
        }
    }

    @Test void allMachineRegionsAndSlotsFitAtSmallAndLargeGuiScales() {
        for (var layout : MachineUiLayout.values()) {
            for (int[] viewport : new int[][] {{320, 240}, {480, 270}, {854, 480}, {1280, 720}}) {
                float scale = Math.min(1F, Math.min((viewport[0] - 8F) / layout.width, (viewport[1] - 8F) / layout.height));
                var graphics = new UiRenderRecorder();
                graphics.pose().translate((viewport[0] - layout.width * scale) / 2F, (viewport[1] - layout.height * scale) / 2F, 0);
                graphics.pose().scale(scale, scale, 1);
                var expectedPose = new Matrix4f(graphics.pose().last().pose());
                layout.draw(graphics, 0, 0);
                assertEquals(expectedPose, graphics.pose().last().pose());
                for (var rectangle : graphics.rects) {
                    assertTrue(rectangle.left() >= 0 && rectangle.top() >= 0, layout.name());
                    assertTrue(rectangle.right() <= layout.width && rectangle.bottom() <= layout.height, layout.name());
                    var low = rectangle.transform().transformPosition(new Vector3f(rectangle.left(), rectangle.top(), 0));
                    var high = rectangle.transform().transformPosition(new Vector3f(rectangle.right(), rectangle.bottom(), 0));
                    assertTrue(low.x >= 3.9F && low.y >= 3.9F && high.x <= viewport[0] - 3.9F && high.y <= viewport[1] - 3.9F);
                }
            }
        }
    }

    @Test void computationCoreRestoresTransformAndKeepsConstantDrawingCost() {
        for (int radius : new int[] {29, 30, 31}) {
            var graphics = new UiRenderRecorder();
            var before = new Matrix4f(graphics.pose().last().pose());
            OmniUiTheme.computationCore(graphics, 82, 82, radius, true, true);
            assertEquals(before, graphics.pose().last().pose());
            assertEquals(4, graphics.rects.size());
        }
    }
}
