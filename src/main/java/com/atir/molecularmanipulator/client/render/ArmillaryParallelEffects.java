package com.atir.molecularmanipulator.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Prepares effects while rigid models are submitted. Start, submit and close on the render
 * thread; workers only read captured inputs and write their own reusable outputs.
 */
final class ArmillaryParallelEffects implements AutoCloseable {
    private static final CompletableFuture<Void> DONE = CompletableFuture.completedFuture(null);
    private ExecutorService workers;
    private final Frame frame = new Frame();
    private boolean busy;

    Frame start(PoseStack poses, int lit, int effects, Vec3 camera, float time,
            float flowTime, double distance, boolean detailed) {
        return start(poses, lit, effects, camera, time, flowTime, distance, detailed, true);
    }

    Frame start(PoseStack poses, int lit, int effects, Vec3 camera, float time,
            float flowTime, double distance, boolean detailed, boolean prepareRain) {
        if (!canStart(lit)) return null;
        return startCaptured(poses, lit, effects, camera, time, flowTime, distance, detailed,
                SingularityVertexLayout.captureLevel(), prepareRain);
    }

    /** Explicit render-thread capture; null selects ordinary XYZ/color preparation. */
    Frame startCaptured(PoseStack poses, int lit, int effects, Vec3 camera, float time,
            float flowTime, double distance, boolean detailed, SingularityVertexLayout.State state) {
        return startCaptured(poses, lit, effects, camera, time, flowTime, distance, detailed, state, true);
    }

    private Frame startCaptured(PoseStack poses, int lit, int effects, Vec3 camera, float time,
            float flowTime, double distance, boolean detailed, SingularityVertexLayout.State state, boolean prepareRain) {
        if (!canStart(lit)) return null;
        var input = new Input(new Matrix4f(poses.last().pose()), lit, effects, camera, time,
                flowTime, distance, detailed, state, prepareRain);
        if (workers == null) {
            var sequence = new AtomicInteger();
            workers = Executors.newFixedThreadPool(Math.clamp(Runtime.getRuntime().availableProcessors() / 4, 2, 4), task -> {
                var thread = new Thread(task, "Omni geometry-" + sequence.incrementAndGet());
                thread.setDaemon(true);
                return thread;
            });
        }
        frame.begin(prepareRain);
        busy = true;
        try {
            for (var task : frame.anchors) task.future = CompletableFuture.runAsync(() -> task.prepare(input), workers);
            frame.traces.future = CompletableFuture.runAsync(() -> frame.traces.prepare(input), workers);
        } catch (RuntimeException | Error failure) {
            // A scheduling failure may follow accepted tasks. Finish those before reuse/free.
            try { frame.close(); }
            catch (RuntimeException | Error joining) { failure.addSuppressed(joining); }
            throw failure;
        }
        return frame;
    }

    private boolean canStart(int lit) {
        return !busy && lit >= 6 && Runtime.getRuntime().availableProcessors() >= 4;
    }

    private record Input(Matrix4f pose, int lit, int effects, Vec3 camera, float time,
            float flowTime, double distance, boolean detailed, SingularityVertexLayout.State state, boolean prepareRain) {
        PoseStack poses() {
            var poses = new PoseStack();
            poses.last().pose().set(pose);
            return poses;
        }
    }

    final class Frame implements AutoCloseable {
        private final Anchors[] anchors = {new Anchors(0), new Anchors(3), new Anchors(6)};
        private final Traces traces = new Traces();
        private boolean ready, closed = true, appendFailed, rainPrepared;
        private int submitted;

        private void begin(boolean prepareRain) {
            rainPrepared = prepareRain;
            ready = false; closed = false; appendFailed = false; submitted = 0;
            for (var task : anchors) task.future = DONE;
            traces.future = DONE;
        }

        private void joinAll() {
            try {
                // allOf waits for every worker, including when an earlier task failed.
                CompletableFuture.allOf(anchors[0].future, anchors[1].future, anchors[2].future, traces.future).join();
            } catch (CompletionException failure) {
                // Never rethrow the same future exception from both submit and close:
                // try-with-resources must be able to suppress the latter safely.
                throw new CompletionException("Armillary effect preparation failed", failure);
            }
        }

        private void beforeSubmit(int pass) {
            if (closed) throw new IllegalStateException("Armillary effect frame is closed");
            if (appendFailed || (submitted & pass) != 0)
                throw new IllegalStateException("Prepared armillary effects cannot be appended again");
            if (!ready) {
                joinAll(); // Validate completion before any output can change the target.
                ready = true;
            }
            submitted |= pass;
        }

        void crystal(VertexConsumer out) {
            beforeSubmit(1);
            try {
                for (var task : anchors) task.depth.submit(out);
                traces.depth.submit(out);
            } catch (RuntimeException | Error failure) { appendFailed = true; throw failure; }
        }

        void rain(VertexConsumer out, boolean glow) {
            if (!rainPrepared) throw new IllegalStateException("This frame delegates rain generation to the GPU");
            beforeSubmit(glow ? 8 : 2);
            try { (glow ? traces.rainGlow : traces.rain).submit(out); }
            catch (RuntimeException | Error failure) { appendFailed = true; throw failure; }
        }

        void glow(VertexConsumer out) {
            beforeSubmit(4);
            try {
                traces.glow.submit(out);
                for (var task : anchors) task.glow.submit(out);
            } catch (RuntimeException | Error failure) { appendFailed = true; throw failure; }
        }

        @Override public void close() {
            if (closed) return;
            try { joinAll(); }
            finally { closed = true; busy = false; }
        }
    }

    /** One worker owns both representations; only the captured representation is begun/replayed. */
    private static final class Output implements AutoCloseable {
        private final PreparedColorVertices colors = new PreparedColorVertices();
        private final PreparedEffectVertices attributes = new PreparedEffectVertices();
        private boolean fullAttributes;

        VertexConsumer begin(SingularityVertexLayout.State state) {
            fullAttributes = state != null;
            return fullAttributes ? attributes.begin(state) : colors.begin();
        }

        void submit(VertexConsumer out) {
            if (fullAttributes) attributes.submit(out);
            else colors.submit(out);
        }

        @Override public void close() { colors.close(); attributes.close(); }
    }

    private static final class Anchors implements AutoCloseable {
        final int from;
        final Output depth = new Output(), glow = new Output();
        CompletableFuture<Void> future;
        Anchors(int from) { this.from = from; }
        void prepare(Input input) {
            var poses = input.poses();
            EntropyArmillaryAnchorEffects.renderRange(poses, depth.begin(input.state), from,
                    Math.min(from + 3, input.lit), input.effects, input.camera, input.time, false);
            EntropyArmillaryAnchorEffects.renderRange(poses, glow.begin(input.state), from,
                    Math.min(from + 3, input.lit), input.effects, input.camera, input.time, true);
        }
        @Override public void close() { depth.close(); glow.close(); }
    }

    private static final class Traces implements AutoCloseable {
        final Output depth = new Output(), glow = new Output(), rain = new Output(), rainGlow = new Output();
        CompletableFuture<Void> future;
        void prepare(Input input) {
            var poses = input.poses();
            int rings = EntropyArmillaryEffects.visibleRings(input.lit, input.effects, input.distance);
            EntropyArmillaryEffects.render(poses, depth.begin(input.state), rings, input.time, input.detailed, false);
            EntropyArmillaryEffects.render(poses, glow.begin(input.state), rings, input.time, input.detailed, true);
            if (input.prepareRain) {
                rain(input, poses, rain.begin(input.state), false);
                rain(input, poses, rainGlow.begin(input.state), true);
            }
        }
        private static void rain(Input input, PoseStack poses, VertexConsumer out, boolean glow) {
            if (!EntropyArmillaryEffects.effectsVisible(input.effects, input.distance)) return;
            EntropyArmillaryCoreEffects.renderRain(poses, out, input.lit, input.effects, input.distance, input.flowTime, glow);
            EntropyArmillaryCoreEffects.renderBuildingRain(poses, out, input.lit, input.effects,
                    input.distance, input.flowTime, input.time, glow);
        }
        @Override public void close() { depth.close(); glow.close(); rain.close(); rainGlow.close(); }
    }

    @Override public void close() {
        try {
            if (busy) frame.close();
        } finally {
            // Failed preparation still joins all tasks before releasing their native storage.
            for (var task : frame.anchors) task.close();
            frame.traces.close();
            if (workers != null) workers.shutdown();
            workers = null;
        }
    }
}
