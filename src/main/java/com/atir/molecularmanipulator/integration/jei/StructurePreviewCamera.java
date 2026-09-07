package com.atir.molecularmanipulator.integration.jei;

import net.minecraft.client.gui.navigation.ScreenRectangle;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Recipe-local orbit camera. Pan is measured in GUI pixels, independently of zoom. */
final class StructurePreviewCamera {
    double yaw = 45;
    double pitch = 25;
    double zoom = 1;
    double panX;
    double panY;

    void rotate(double dx, double dy) {
        yaw = wrap(yaw + dx * 0.6);
        pitch = wrap(pitch + dy * 0.6);
    }

    void pan(double dx, double dy) {
        panX += dx;
        panY += dy;
    }

    void zoomAt(double scroll, double xFromCenter, double yFromCenter) {
        double next = Math.clamp(zoom * Math.pow(1.15, Math.clamp(scroll, -10, 10)), 0.2, 12.0);
        double ratio = next / zoom;
        panX = xFromCenter + (panX - xFromCenter) * ratio;
        panY = yFromCenter + (panY - yFromCenter) * ratio;
        zoom = next;
    }

    void reset() {
        yaw = 45;
        pitch = 25;
        zoom = 1;
        panX = panY = 0;
    }

    private static double wrap(double degrees) {
        return degrees - Math.floor(degrees / 360.0) * 360.0;
    }

    static float fitScale(double width, double height, double depth, int viewWidth, int viewHeight) {
        double yaw = Math.toRadians(45);
        double pitch = Math.toRadians(25);
        double projectedWidth = width * Math.cos(yaw) + depth * Math.sin(yaw);
        double projectedHeight = height * Math.cos(pitch)
                + (width * Math.sin(yaw) + depth * Math.cos(yaw)) * Math.sin(pitch);
        return (float) Math.min((viewWidth - 14) / Math.max(1, projectedWidth),
                (viewHeight - 14) / Math.max(1, projectedHeight));
    }

    static ScreenRectangle screenBounds(Matrix4f pose, ScreenRectangle local) {
        // GuiGraphics scissor coordinates in 1.21.1 are absolute, unlike its drawing coordinates.
        float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY;
        for (int corner = 0; corner < 4; corner++) {
            var point = pose.transformPosition(new Vector3f(
                    (corner & 1) == 0 ? local.left() : local.right(),
                    (corner & 2) == 0 ? local.top() : local.bottom(), 0));
            minX = Math.min(minX, point.x);
            minY = Math.min(minY, point.y);
            maxX = Math.max(maxX, point.x);
            maxY = Math.max(maxY, point.y);
        }
        int left = (int) Math.floor(minX), top = (int) Math.floor(minY);
        return new ScreenRectangle(left, top, (int) Math.ceil(maxX) - left, (int) Math.ceil(maxY) - top);
    }
}
