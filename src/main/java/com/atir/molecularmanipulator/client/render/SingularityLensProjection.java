package com.atir.molecularmanipulator.client.render;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

final class SingularityLensProjection {
    private SingularityLensProjection() {}

    /** Pixel bounds use framebuffer coordinates, with the origin at the bottom left. */
    record ScreenBounds(int x, int y, int width, int height) {
        boolean isEmpty() { return width == 0 || height == 0; }

        ScreenBounds expand(int padding, int screenWidth, int screenHeight) {
            if (isEmpty()) return this;
            int left = Math.max(0, x - padding);
            int bottom = Math.max(0, y - padding);
            int right = Math.min(screenWidth, x + width + padding);
            int top = Math.min(screenHeight, y + height + padding);
            return new ScreenBounds(left, bottom, right - left, top - bottom);
        }
    }

    static ScreenBounds screenBounds(Vector3f center, float radius, Matrix4f projection,
            int width, int height) {
        return screenBounds(new Matrix4f(projection).translate(center), radius, width, height);
    }

    /**
     * Project a cube enclosing the effect sphere. Its corners bound the projected
     * sphere as long as the cube is in front of the projection eye. When it
     * crosses the eye plane, retain the full screen so entering the field never
     * clips it. The shader can also shade fields closer than the near plane.
     */
    static ScreenBounds screenBounds(Matrix4f clipFromLocal, float radius, int width, int height) {
        float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY;
        int inFront = 0;
        var corner = new Vector4f();
        for (int index = 0; index < 8; index++) {
            corner.set((index & 1) == 0 ? -radius : radius,
                    (index & 2) == 0 ? -radius : radius,
                    (index & 4) == 0 ? -radius : radius, 1);
            clipFromLocal.transform(corner);
            if (corner.w <= 0) continue;
            inFront++;
            float x = corner.x / corner.w, y = corner.y / corner.w;
            minX = Math.min(minX, x); minY = Math.min(minY, y);
            maxX = Math.max(maxX, x); maxY = Math.max(maxY, y);
        }
        if (inFront == 0) return new ScreenBounds(0, 0, 0, 0);
        if (inFront < 8) return new ScreenBounds(0, 0, width, height);
        if (maxX < -1 || minX > 1 || maxY < -1 || minY > 1) {
            return new ScreenBounds(0, 0, 0, 0);
        }
        // Round outwards and keep a pixel for rasterization at the silhouette.
        int left = Math.clamp((int) Math.floor((minX * .5F + .5F) * width) - 1, 0, width);
        int bottom = Math.clamp((int) Math.floor((minY * .5F + .5F) * height) - 1, 0, height);
        int right = Math.clamp((int) Math.ceil((maxX * .5F + .5F) * width) + 1, 0, width);
        int top = Math.clamp((int) Math.ceil((maxY * .5F + .5F) * height) + 1, 0, height);
        return new ScreenBounds(left, bottom, right - left, top - bottom);
    }

    static Vector3f centerInViewSpace(Matrix4f pose, float localY, Matrix4f view) {
        var center = pose.transformPosition(0, localY, 0, new Vector3f());
        view.transformPosition(center);
        return center;
    }

    static Matrix4f inverseProjection(Matrix4f renderedProjection) {
        return new Matrix4f(renderedProjection).invert();
    }

    static Vector3f viewOrigin(Matrix4f inverseProjection) {
        // Vanilla folds walking/hurt transforms into the projection. Its perspective
        // center is then no longer (0,0,0) in the unbobbed view space used by CoreCenter.
        // The eye projects to (0,0,z,0); undo that homogeneous point, not a depth sample.
        var eye = inverseProjection.transform(new Vector4f(0, 0, -1, 0));
        return new Vector3f(eye.x, eye.y, eye.z).div(eye.w);
    }
}
