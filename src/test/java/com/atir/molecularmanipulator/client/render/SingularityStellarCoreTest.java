package com.atir.molecularmanipulator.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SingularityStellarCoreTest {
    @Test void nestedCoreRestoresPoseStaysBoundedAndWrapsItsClock() {
        for (boolean detailed : new boolean[]{false, true}) {
            var poses = new PoseStack();
            var original = new org.joml.Matrix4f(poses.last().pose());
            var star = new RecordingColorConsumer(); var flux = new RecordingColorConsumer();
            var frame = SingularityStructureEffects.Frame.steady(120);
            SingularityStellarCore.renderStar(poses, star, frame, detailed);
            SingularityStellarCore.renderFlux(poses, flux, frame, detailed);
            assertEquals(original, poses.last().pose());
            assertEquals(0, (star.vertices().size() + flux.vertices().size()) % 4);
            for (var v : star.vertices()) assertTrue(Math.sqrt(v.x()*v.x()+(v.y()-12.5)*(v.y()-12.5)+v.z()*v.z()) < 8.1);
            for (var v : flux.vertices()) assertTrue(Math.abs(v.x()) < 8 && Math.abs(v.z()) < 8 && v.y() > 5 && v.y() < 20);
            assertNotEquals(render(0, detailed).vertices(), render(40, detailed).vertices());
            assertEquals(render(0, detailed).vertices(), render(24000, detailed).vertices());
        }
    }
    @Test void reducedDetailRetainsCollectorsWithoutJetsOrOpaqueCore() {
        var low = render(80, false); var full = render(80, true);
        assertTrue(low.vertices().size() < full.vertices().size() * .8);
        assertTrue(low.vertices().stream().allMatch(v -> v.y() < 22));
        assertTrue(low.vertices().stream().allMatch(v -> v.y() > 3));
        var star = new RecordingColorConsumer();
        SingularityStellarCore.renderStar(new PoseStack(), star, SingularityStructureEffects.Frame.steady(80), false);
        assertTrue(star.vertices().stream().allMatch(v -> v.alpha() == 255));
        assertTrue(star.vertices().stream().allMatch(v ->
                v.x()*v.x() + (v.y()-12.5)*(v.y()-12.5) + v.z()*v.z() > 4.5),
                "The horizon is composed after the world; no opaque star may hide the scenery snapshot");
    }
    private static RecordingColorConsumer render(float time, boolean detailed) {
        var out = new RecordingColorConsumer(); var poses = new PoseStack();
        SingularityStellarCore.renderStar(poses, out, SingularityStructureEffects.Frame.steady(time), detailed);
        SingularityStellarCore.renderFlux(poses, out, SingularityStructureEffects.Frame.steady(time), detailed);
        return out;
    }
}
