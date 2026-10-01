package com.atir.molecularmanipulator.client.render;

import static org.junit.jupiter.api.Assertions.*;
import com.atir.molecularmanipulator.blockentity.TaixuMotionGeometry;
import com.mojang.blaze3d.vertex.PoseStack;
import org.junit.jupiter.api.Test;

class TaixuStructureEffectsTest {
    @Test void suspendedCrystalsFollowTowerLiftAndStayInsideTheRenderer() {
        for (boolean detailed : new boolean[]{false,true}) {
            var frame=TaixuStructureEffects.Frame.steady(240);
            var base=new RecordingColorConsumer(); var lifted=new RecordingColorConsumer();
            var poses=new PoseStack(); var original=new org.joml.Matrix4f(poses.last().pose());
            var motion=new TaixuMotionGeometry.Pose[11];motion[3]=new TaixuMotionGeometry.Pose(0,1.5);
            TaixuStructureEffects.renderCrystal(poses,base,frame,detailed,null,true);
            TaixuStructureEffects.renderCrystal(poses,lifted,frame,detailed,motion,true);
            int changed=0;
            for(int i=0;i<base.vertices().size();i++) {
                var a=base.vertices().get(i);var b=lifted.vertices().get(i);
                assertTrue(Math.abs(b.x())<54 && Math.abs(b.z())<54 && b.y()>-47 && b.y()<70);
                if(!a.equals(b)) {changed++;assertEquals(a.x(),b.x(),.00001);assertEquals(a.z(),b.z(),.00001);assertEquals(1.5,b.y()-a.y(),.00001);}
            }
            assertEquals(5*8*4,changed,"Exactly the five crystals of the raised tower must follow its body");
            var glow=new RecordingColorConsumer();TaixuStructureEffects.render(poses,glow,frame,detailed,motion,true);
            assertTrue(glow.vertices().size()+base.vertices().size()<(detailed?75000:28000));
            assertEquals(original,poses.last().pose());
        }
    }
    @Test
    void crystalAndStartupPassesStayBoundedAndRevealInOrder() {
        long first = 0, last = 0;
        for (float age : new float[]{0, 30, 60, 90, 120, 160}) {
            for (boolean detailed : new boolean[]{false, true}) {
                var poses = new PoseStack();
                var original = new org.joml.Matrix4f(poses.last().pose());
                var glow = new RecordingColorConsumer();
                var crystal = new RecordingColorConsumer();
                var frame = new TaixuStructureEffects.Frame(80, age, 1);
                TaixuStructureEffects.render(poses, glow, frame, detailed, null);
                TaixuStructureEffects.renderCrystal(poses, crystal, frame, detailed);
                assertEquals(original, poses.last().pose());
                assertTrue(glow.vertices().size() + crystal.vertices().size() < (detailed ? 75000 : 28000));
                for (var v : crystal.vertices()) {
                    assertTrue(Math.abs(v.x()) < 15 && Math.abs(v.z()) < 15 && Math.abs(v.y()) < 15);
                }
                if (detailed && age == 0) first = glow.vertices().stream().mapToLong(v -> v.alpha()).sum();
                if (detailed && age == 160) last = glow.vertices().stream().mapToLong(v -> v.alpha()).sum();
            }
        }
        assertTrue(last > first * 3, "The opening reveals towers, rings, and core rather than spawning at full brightness");
    }
    @Test
    void geometryStaysInsideRendererBoundsAndRestoresCallerPose() {
        for (boolean detailed : new boolean[]{false, true}) {
            for (float time : new float[]{0, 17.5F, 120, 320, 4000, 23999.5F}) {
                var motion = new TaixuMotionGeometry.Pose[11];
                for (int group = 0; group < motion.length; group++) motion[group] = TaixuMotionGeometry.runningPose(group, time);
                var poses = new PoseStack();
                var original = new org.joml.Matrix4f(poses.last().pose());
                var out = new RecordingColorConsumer();
                TaixuStructureEffects.render(poses, out, time, detailed, motion);
                assertEquals(original, poses.last().pose());
                assertEquals(0, out.vertices().size() % 4);
                assertTrue(out.vertices().size() < (detailed ? 75000 : 28000),
                        "Bound the full structure's per-frame vertex cost: " + out.vertices().size());
                for (var v : out.vertices()) {
                    assertTrue(Math.abs(v.x()) < 54 && Math.abs(v.z()) < 54 && v.y() > -47 && v.y() < 70,
                            "Renderer bounds must contain animated towers, trails, and beam: " + v);
                }
            }
        }
    }

    @Test
    void lowDetailCutsGeometryAndAmbientClockWrapIsStable() {
        var reduced = render(0, false, null);
        var full = render(0, true, null);
        System.out.println("TIANYI_VERTEX_COUNTS full=" + full.vertices().size() + " reduced=" + reduced.vertices().size());
        assertTrue(reduced.vertices().size() * 2 < full.vertices().size());
        assertEquals(full.vertices(), render(24000, true, null).vertices());
        assertNotEquals(full.vertices(), render(40, true, null).vertices(), "Ambient geometry must animate");
    }

    @Test
    void towerSealsFollowTheActualLiftWithoutMovingOtherEffects() {
        var baseline = render(20, false, null).vertices();
        var motion = new TaixuMotionGeometry.Pose[11];
        motion[3] = new TaixuMotionGeometry.Pose(0, 1.5);
        var lifted = render(20, false, motion).vertices();
        assertEquals(baseline.size(), lifted.size());
        int changed = 0;
        for (int i = 0; i < baseline.size(); i++) {
            var a = baseline.get(i); var b = lifted.get(i);
            if (a.equals(b)) continue;
            changed++;
            assertEquals(a.x(), b.x(), .00001);
            assertEquals(a.z(), b.z(), .00001);
            assertEquals(1.5, b.y() - a.y(), .00001);
            assertEquals(a.argb(), b.argb());
            assertTrue(Math.abs(a.x()) < 5.2 && Math.abs(a.z() - 41) < 5.2);
        }
        assertTrue(changed > 500, "Both the top and bottom seals should follow the tower");
    }

    @Test
    void ringSigilsUseThePhysicalAngle() {
        var baseline = render(0, false, null).vertices();
        var motion = new TaixuMotionGeometry.Pose[11];
        motion[0] = new TaixuMotionGeometry.Pose(37, 0);
        var rotated = render(0, false, motion).vertices();
        assertEquals(baseline.size(), rotated.size());
        double angle = Math.toRadians(37), cos = Math.cos(angle), sin = Math.sin(angle);
        int changed = 0;
        for (int i = 0; i < baseline.size(); i++) {
            var a = baseline.get(i); var b = rotated.get(i);
            if (a.equals(b)) continue;
            changed++;
            assertEquals(a.x() * cos - a.z() * sin, b.x(), .00002);
            assertEquals(a.z() * cos + a.x() * sin, b.z(), .00002);
            assertEquals(a.y(), b.y());
            assertEquals(a.argb(), b.argb());
        }
        assertTrue(changed > 500);
    }

    private static RecordingColorConsumer render(float time, boolean detailed, TaixuMotionGeometry.Pose[] motion) {
        var out = new RecordingColorConsumer();
        TaixuStructureEffects.render(new PoseStack(), out, time, detailed, motion);
        return out;
    }
}
