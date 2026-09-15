package com.atir.molecularmanipulator.client;

import static org.junit.jupiter.api.Assertions.*;

import com.atir.molecularmanipulator.client.render.RecordingColorConsumer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

/**
 * The dial steps once per real second and fills one mark at a time across a minute. The timing lives
 * in {@link FeatherResonanceEffects#clockState(double)} so it can be pinned without sampling geometry.
 */
class MolecularCenterClockEffectTest {
    private static final int FIELD = 0x123456;
    private static final int CORE = 0x234567;
    private static final int PRIMARY = 0x345678;
    private static final int SECONDARY = 0x456789;
    private static final int LATTICE = 0x56789A;

    @Test
    void dialStepsOnceEveryTwentyTicksAndWrapsEachMinute() {
        assertEquals(0, FeatherResonanceEffects.clockState(0).secondOfMinute());
        assertEquals(0, FeatherResonanceEffects.clockState(19.999).secondOfMinute());
        assertEquals(1, FeatherResonanceEffects.clockState(20).secondOfMinute());
        assertEquals(1, FeatherResonanceEffects.clockState(39.999).secondOfMinute());
        assertEquals(2, FeatherResonanceEffects.clockState(40).secondOfMinute());
        assertEquals(59, FeatherResonanceEffects.clockState(1199).secondOfMinute());
        assertEquals(0, FeatherResonanceEffects.clockState(1200).secondOfMinute(),
                "A full minute returns the dial to its first mark");
        assertEquals(1, FeatherResonanceEffects.clockState(1220).secondOfMinute());
        assertEquals(0, FeatherResonanceEffects.clockState(1199).minuteOfHour());
        assertEquals(1, FeatherResonanceEffects.clockState(1200).minuteOfHour(),
                "The minute hand advances with the minute");
        assertEquals(2, FeatherResonanceEffects.clockState(2400).minuteOfHour());
    }

    @Test
    void handsJumpSixDegreesPerStepInsteadOfSweeping() {
        for (int second = 0; second < 60; second++) {
            var start = FeatherResonanceEffects.clockState(second * 20.0);
            var rest = FeatherResonanceEffects.clockState(second * 20.0 + 19.999);
            assertEquals(second * 6.0, start.secondDegrees(), 1.7 + 1.0E-9,
                    "The second hand starts each second on its own mark with a small overshoot");
            assertEquals(second * 6.0, rest.secondDegrees(), 1.0E-6,
                    "The second hand comes to rest before the next step");
            assertTrue(start.secondDegrees() >= rest.secondDegrees(),
                    "The overshoot must settle forward, never drift backwards");
            assertEquals(second * 0.1, start.minuteDegrees(), 1.0E-9,
                    "The minute hand creeps one tenth of a degree per second");
        }
        assertEquals(6.0, FeatherResonanceEffects.clockState(40).secondDegrees()
                - FeatherResonanceEffects.clockState(20).secondDegrees(), 1.0E-9,
                "Consecutive marks are one sixtieth of a turn apart");
    }

    @Test
    void pulseSpikesAtEachStepAndDecaysWithinTheSecond() {
        assertEquals(1.0, FeatherResonanceEffects.clockState(20).pulse(), 1.0E-9);
        double previous = Double.MAX_VALUE;
        for (double fraction : new double[] {0, 0.05, 0.15, 0.3, 0.5, 0.75, 0.99}) {
            double pulse = FeatherResonanceEffects.clockState(20 + fraction * 20).pulse();
            assertTrue(pulse >= 0 && pulse <= 1, "Pulse stays normalised: " + pulse);
            assertTrue(pulse < previous, "Pulse must decay across the second");
            previous = pulse;
        }
        assertTrue(FeatherResonanceEffects.clockState(30).pulse() < 0.05,
                "The beat must be a short stutter, not a slow fade");
    }

    @Test
    void clockIsATotalFunctionOfTime() {
        assertEquals(FeatherResonanceEffects.clockState(-5), FeatherResonanceEffects.clockState(0),
                "Negative time clamps to the first mark");
        var nan = FeatherResonanceEffects.clockState(Double.NaN);
        assertEquals(0, nan.secondOfMinute());
        assertEquals(0, nan.pulse());
        assertTrue(Double.isFinite(FeatherResonanceEffects.clockState(Double.POSITIVE_INFINITY).secondDegrees()));
        assertTrue(Double.isFinite(FeatherResonanceEffects.clockState(Double.POSITIVE_INFINITY).minuteDegrees()));
    }

    @Test
    void dialFollowsTheClockDeterministically() {
        assertEquals(draw(true, 1000).vertices(), draw(true, 1000).vertices(),
                "The dial must be a pure function of its inputs");
        assertNotEquals(draw(true, 1000).vertices(), draw(true, 1600).vertices(),
                "Later in the minute the dial must have filled further");
        assertEquals(draw(true, 1000).vertices().size(), draw(true, 1600).vertices().size(),
                "Filling a mark changes brightness, not the vertex layout");
    }

    @Test
    void dialKeepsTheCompletionWaveLastAndTheBudgetIntact() {
        var glow = draw(true, 1000);
        var depth = draw(false, 1000);
        assertTrue(glow.vertices().stream().allMatch(vertex -> vertex.alpha() <= 34),
                "The glow pass may not exceed its alpha ceiling");
        assertTrue(glow.vertices().stream().allMatch(vertex -> Math.abs(vertex.x()) < 31
                && Math.abs(vertex.z()) < 31 && vertex.y() > -6 && vertex.y() < 15));
        assertTrue(depth.vertices().stream().noneMatch(vertex -> vertex.rgb() == FIELD));
        assertTrue(depth.vertices().size() + glow.vertices().size() < 100000);
        // Completion still owns the final vertices so the expanding wave stays on top.
        var early = draw(true, 1000, 0.9F);
        var late = draw(true, 1000, 0.2F);
        var first = early.vertices().getLast();
        var last = late.vertices().getLast();
        assertTrue(Math.hypot(last.x(), last.z()) > Math.hypot(first.x(), first.z()));
        assertTrue(last.alpha() < first.alpha());
    }

    @Test
    void clockNeverMasksTheStaticMainRail() {
        // The rail regression selects vertices by alpha 158 above a 3D radius of 10, so any dial
        // vertex reusing that alpha would be measured against the rail plane. Keep the pair rail-only.
        for (double clock : new double[] {0, 7.5, 20, 617, 1200, 2400}) {
            for (boolean glow : new boolean[] {false, true}) {
                for (var vertex : draw(glow, clock).vertices()) {
                    if (vertex.radius() > 10 && vertex.alpha() == 158) {
                        assertTrue(Math.abs(vertex.y() + 5.0) <= 0.03,
                                "Alpha 158 above radius 10 must sit on the rail plane, at clock=" + clock);
                        double horizontal = Math.hypot(vertex.x(), vertex.z());
                        assertTrue(horizontal >= 11.45 && horizontal <= 11.55,
                                "Alpha 158 above radius 10 belongs to the rail ring alone, at clock=" + clock);
                    }
                }
            }
        }
    }

    @Test
    void pulsingDividerAndSecondHandBrightenOnTheStepAndFadeWithinTheSecond() {
        // 250 is the top band of the dial palette; no other geometry in this effect reaches it, so
        // it isolates the beat: full pulse at the step, gone halfway through the second.
        long atStep = draw(false, 1000).vertices().stream().filter(v -> v.alpha() == 250).count();
        long midSecond = draw(false, 1010).vertices().stream().filter(v -> v.alpha() == 250).count();
        assertTrue(atStep > 0, "The leading divider and second hand must flash at each step");
        assertEquals(0, midSecond, "The beat must decay inside the second");
        assertEquals(0, draw(true, 1000).vertices().stream().filter(v -> v.alpha() > 34).count(),
                "The glow pass stays inside its own alpha ceiling");
    }

    @Test
    void dialShowsLocalWallClockTimeAndHonoursTheZoneOffset() {
        long midnightUtc = 1_728_000_000_000L;   // an exact UTC midnight
        var start = FeatherResonanceEffects.clockState(FeatherResonanceEffects.wallClockTicks(midnightUtc, 0));
        assertEquals(0, start.secondOfMinute());
        assertEquals(0, start.minuteOfHour());
        assertEquals(0, start.hourOfHalfDay());
        assertEquals(0.0, start.hourDegrees(), 1.0E-9);

        long at1235 = midnightUtc + (12 * 3600 + 35 * 60 + 7) * 1000L;
        var state = FeatherResonanceEffects.clockState(FeatherResonanceEffects.wallClockTicks(at1235, 0));
        assertEquals(7, state.secondOfMinute());
        assertEquals(35, state.minuteOfHour());
        assertEquals(0, state.hourOfHalfDay(), "Twelve o'clock wraps to the top of the face");
        assertEquals(17.5, state.hourDegrees(), 1.0E-6, "The hour hand has moved half a degree per minute");

        // The same instant in UTC+8 is 20:35, i.e. eight on the twelve hour face.
        var shifted = FeatherResonanceEffects.clockState(
                FeatherResonanceEffects.wallClockTicks(at1235, 8 * 3600 * 1000));
        assertEquals(8, shifted.hourOfHalfDay());
        assertEquals(35, shifted.minuteOfHour());
        assertEquals(7, shifted.secondOfMinute());

        // Fourteen o'clock lands two on the face, and a whole day later nothing has changed.
        long at1435 = midnightUtc + (14 * 3600 + 35 * 60 + 7) * 1000L;
        var afternoon = FeatherResonanceEffects.clockState(FeatherResonanceEffects.wallClockTicks(at1435, 0));
        assertEquals(2, afternoon.hourOfHalfDay());
        assertEquals(77.5, afternoon.hourDegrees(), 1.0E-6);
        assertEquals(FeatherResonanceEffects.wallClockTicks(at1435, 0),
                FeatherResonanceEffects.wallClockTicks(at1435 + 86_400_000L, 0), 1.0E-9);
    }

    @Test
    void hourHandAdvancesHalfADegreePerMinute() {
        // Noon plus n minutes: the hour hand stays on XII and creeps half a degree per minute.
        for (int minute = 0; minute < 60; minute++) {
            var state = FeatherResonanceEffects.clockState(43200 * 20.0 + minute * 60 * 20.0);
            assertEquals(minute * 0.5, state.hourDegrees(), 1.0E-9);
            assertEquals(0, state.hourOfHalfDay());
        }
        assertEquals(30.0, FeatherResonanceEffects.clockState(46800 * 20.0).hourDegrees(), 1.0E-9,
                "One hour advances the hand to the next numeral");
    }

    @Test
    void clockPassesPreserveTheCallersPose() {
        var pose = new PoseStack();
        pose.translate(4, -2, 9);
        pose.mulPose(Axis.YP.rotationDegrees(73));
        var before = new Matrix4f(pose.last().pose());
        for (boolean glow : new boolean[] {false, true}) {
            MolecularCenterRenderer.renderFeatherPass(pose, new RecordingColorConsumer(),
                    50, 1, 0, 2, glow, FIELD, CORE, PRIMARY, SECONDARY, LATTICE, 1234);
            assertEquals(before, pose.last().pose());
        }
    }

    private static RecordingColorConsumer draw(boolean glow, double clockTicks) {
        return draw(glow, clockTicks, 0);
    }

    private static RecordingColorConsumer draw(boolean glow, double clockTicks, float completion) {
        var recorder = new RecordingColorConsumer();
        MolecularCenterRenderer.renderFeatherPass(new PoseStack(), recorder,
                50, 1, completion, 2, glow, FIELD, CORE, PRIMARY, SECONDARY, LATTICE, clockTicks);
        return recorder;
    }
}
