package com.atir.molecularmanipulator.client.render;

import org.joml.Matrix4f;
import org.joml.Vector3f;

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
}
