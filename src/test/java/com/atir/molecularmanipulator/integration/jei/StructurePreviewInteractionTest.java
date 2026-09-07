package com.atir.molecularmanipulator.integration.jei;

import net.minecraft.client.gui.navigation.ScreenRectangle;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class StructurePreviewInteractionTest {
    @Test
    void previewLeavesRoomForTheJeiHeaderAndBothMaterialRowsOnSmallScreens() {
        for (int screenHeight : new int[] {240, 270, 300, 360, 480}) {
            int preview = StructureJeiLayout.previewHeight(screenHeight);
            assertTrue(preview >= 70 && preview <= 168);
            assertTrue(preview + 58 <= screenHeight - 84);
            assertTrue(preview + 38 + 16 < preview + 58, "Second material row must fit");
        }
    }

    @Test
    void scissorFollowsTheJeiPageOffsetAndGuiTransform() {
        var pose = new Matrix4f().translation(137, 63, 0).scale(1.5F, 1.5F, 1);
        var screen = StructurePreviewCamera.screenBounds(pose, new ScreenRectangle(5, 26, 238, 125));
        assertEquals(new ScreenRectangle(144, 102, 358, 188), screen);
        var view = widget().view;
        assertEquals(view, StructurePreviewCamera.screenBounds(new Matrix4f(), view));
    }

    @Test
    void dragCaptureIsContinuousAndReleaseDoesNotActivateAnotherControl() {
        var widget = widget();
        assertFalse(widget.mouseDragged(80, 80, 0, 20, 10));
        assertTrue(widget.mouseClicked(80, 80, 0));
        assertTrue(widget.mouseDragged(81, 80.5, 0, 1, 0.5));
        assertEquals(45.6, widget.camera.yaw, 1.0E-8);
        assertEquals(25.3, widget.camera.pitch, 1.0E-8);
        assertTrue(widget.mouseDragged(400, 300, 0, 50, -30));
        assertTrue(widget.mouseReleased(180, 10, 0));
        assertNotEquals(45, widget.camera.yaw);
        assertFalse(widget.mouseDragged(80, 80, 0, 2, 2));
        assertFalse(widget.mouseClicked(20, 196, 0), "Material slots must remain owned by JEI");
    }

    @Test
    void rightAndMiddleDragPanWhileWheelZoomsWithoutChangingLayer() {
        var widget = widget();
        for (int button : new int[] {1, 2}) {
            assertTrue(widget.mouseClicked(90, 70, button));
            assertTrue(widget.mouseDragged(100, 90, button, 10, 20));
            assertTrue(widget.mouseReleased(100, 90, button));
        }
        assertEquals(20, widget.camera.panX);
        assertEquals(40, widget.camera.panY);
        assertEquals(45, widget.camera.yaw);
        assertTrue(widget.mouseScrolled(100, 90, 0, 2));
        assertEquals(1.3225, widget.camera.zoom, 1.0E-8);
        assertEquals(-1, widget.layer);
        assertFalse(widget.mouseScrolled(100, 200, 0, 1));
    }

    @Test
    void zoomKeepsThePointUnderTheCursorAndResetRecoversAnOffscreenModel() {
        var camera = new StructurePreviewCamera();
        camera.pan(13, -9);
        double beforeX = (52 - camera.panX) / camera.zoom;
        double beforeY = (-23 - camera.panY) / camera.zoom;
        camera.zoomAt(4, 52, -23);
        assertEquals(beforeX, (52 - camera.panX) / camera.zoom, 1.0E-8);
        assertEquals(beforeY, (-23 - camera.panY) / camera.zoom, 1.0E-8);
        for (int i = 0; i < 30; i++) camera.zoomAt(10, 0, 0);
        assertEquals(12, camera.zoom);
        for (int i = 0; i < 30; i++) camera.zoomAt(-10, 0, 0);
        assertEquals(0.2, camera.zoom);
        camera.pan(10000, -10000);
        camera.rotate(20000, -30000);
        camera.reset();
        assertEquals(1, camera.zoom);
        assertEquals(0, camera.panX);
        assertEquals(0, camera.panY);
        assertEquals(45, camera.yaw);
        assertEquals(25, camera.pitch);
    }

    @Test
    void layerButtonsWrapAndCancelledClicksDoNothing() {
        var widget = widget();
        assertTrue(widget.mouseClicked(10, 10, 0));
        assertTrue(widget.mouseReleased(10, 10, 0));
        assertEquals(34, widget.layer);
        assertTrue(widget.mouseClicked(110, 10, 0));
        assertTrue(widget.mouseReleased(110, 10, 0));
        assertEquals(-1, widget.layer);
        widget.mouseClicked(110, 10, 0);
        widget.mouseReleased(80, 80, 0);
        assertEquals(-1, widget.layer);
        widget.mouseClicked(110, 10, 0);
        widget.mouseReleased(110, 10, 0);
        assertEquals(0, widget.layer);
        widget.mouseClicked(40, 10, 0);
        widget.mouseReleased(40, 10, 0);
        assertEquals(-1, widget.layer);
    }

    private static InteractiveStructurePreviewWidget widget() {
        var parts = List.of(new InteractiveStructurePreviewWidget.RenderPart(-32, -4, -32, null),
                new InteractiveStructurePreviewWidget.RenderPart(32, 30, 32, null));
        return new InteractiveStructurePreviewWidget(parts, parts, -4, 35, 168) { };
    }
}
