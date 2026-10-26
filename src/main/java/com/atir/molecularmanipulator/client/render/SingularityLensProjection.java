package com.atir.molecularmanipulator.client.render;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

final class SingularityLensProjection {
    private SingularityLensProjection() {}

    static Vector3f centerInViewSpace(Matrix4f pose, float localY, Matrix4f view) {
        var center = pose.transformPosition(0, localY, 0, new Vector3f());
        view.transformPosition(center);
        return center;
    }

    static Matrix4f inverseProjection(Matrix4f renderedProjection) {
        return new Matrix4f(renderedProjection).invert();
    }

    static Vector3f rayOriginInViewSpace(Matrix4f inverseProjection) {
        // View bobbing is folded into the rendered projection, including its translation.
        // The perspective eye maps to (0, 0, z, 0), not an ordinary NDC point (w = 1).
        var eye = inverseProjection.transform(new Vector4f(0, 0, 1, 0));
        return new Vector3f(eye.x, eye.y, eye.z).div(eye.w);
    }
}
