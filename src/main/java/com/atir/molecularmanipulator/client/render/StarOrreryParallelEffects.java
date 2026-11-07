package com.atir.molecularmanipulator.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.concurrent.CancellationException;
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
final class StarOrreryParallelEffects implements AutoCloseable {
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
        // Only observations of real submission targets select compact preparation. Do not
        // request an Iris buffer here: that could flush/reorder the preceding model passes.
        var state = frame.needsAttributes(prepareRain) ? SingularityVertexLayout.captureLevel() : null;
        return startCaptured(poses, lit, effects, camera, time, flowTime, distance, detailed, state, prepareRain);
    }

    /** Explicit render-thread fallback capture; observed compact targets still use XYZ/color. */
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

        private boolean needsAttributes(boolean prepareRain) {
            for (var task : anchors) if (!task.depth.preferColors || !task.glow.preferColors) return true;
            return !traces.depth.preferColors || !traces.glow.preferColors
                    || prepareRain && (!traces.rain.preferColors || !traces.rainGlow.preferColors);
        }

        private void joinAll() {
            CompletionException failure = null;
            // Do not short-circuit on failure: every writer must finish before submission/free.
            // Direct joins avoid allocating an allOf tree on both submit and close.
            for (var task : anchors) failure = join(task.future, failure);
            failure = join(traces.future, failure);
            if (failure != null) throw failure;
        }

        private CompletionException join(CompletableFuture<Void> future, CompletionException failure) {
            try { future.join(); }
            catch (CompletionException | CancellationException error) {
                // Match allOf's first-failure contract. A fresh wrapper lets try-with-resources
                // suppress a later close failure, while joinAll still waits for every writer.
                if (failure == null) failure = new CompletionException("StarOrrery effect preparation failed", error);
            }
            return failure;
        }

        private void beforeSubmit(int pass) {
            if (closed) throw new IllegalStateException("StarOrrery effect frame is closed");
            if (appendFailed || (submitted & pass) != 0)
                throw new IllegalStateException("Prepared star_orrery effects cannot be appended again");
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
            try { if (!ready) joinAll(); }
            finally { closed = true; busy = false; }
        }
    }

    /** One worker owns both representations; only actual native targets train the compact hint. */
    private static final class Output implements AutoCloseable {
        private final PreparedColorVertices colors = new PreparedColorVertices();
        private final PreparedEffectVertices attributes = new PreparedEffectVertices();
        private boolean fullAttributes, preferColors;

        VertexConsumer begin(SingularityVertexLayout.State state) {
            fullAttributes = state != null && !preferColors;
            return fullAttributes ? attributes.begin(state) : colors.begin();
        }

        void submit(VertexConsumer out) {
            if (fullAttributes) {
                attributes.submit(out);
                // Seed the existing adaptive stride/material path even on the first full→compact
                // submission, so the following worker can write the target's exact 24-byte input.
                colors.captureTarget(out);
            } else colors.submit(out);
            // A shader/capability/buffer switch is allowed between start and submit. The current
            // submit validates its target; a CPU target restores full preparation next frame.
            preferColors = out instanceof SingularityShaderVertices adapter && adapter.acceptsPreparedColor();
        }

        @Override public void close() {
            colors.close(); attributes.close();
            fullAttributes = preferColors = false;
        }
    }

    private static final class Anchors implements AutoCloseable {
        final int from;
        final Output depth = new Output(), glow = new Output();
        CompletableFuture<Void> future;
        Anchors(int from) { this.from = from; }
        void prepare(Input input) {
            var poses = input.poses();
            StarOrreryAnchorEffects.renderRange(poses, depth.begin(input.state), from,
                    Math.min(from + 3, input.lit), input.effects, input.camera, input.time, false);
            StarOrreryAnchorEffects.renderRange(poses, glow.begin(input.state), from,
                    Math.min(from + 3, input.lit), input.effects, input.camera, input.time, true);
        }
        @Override public void close() { depth.close(); glow.close(); }
    }

    private static final class Traces implements AutoCloseable {
        final Output depth = new Output(), glow = new Output(), rain = new Output(), rainGlow = new Output();
        CompletableFuture<Void> future;
        void prepare(Input input) {
            var poses = input.poses();
            int rings = StarOrreryEffects.visibleRings(input.lit, input.effects, input.distance);
            StarOrreryEffects.render(poses, depth.begin(input.state), rings, input.time, input.detailed, false);
            StarOrreryEffects.render(poses, glow.begin(input.state), rings, input.time, input.detailed, true);
            if (input.prepareRain) {
                rain(input, poses, rain.begin(input.state), false);
                rain(input, poses, rainGlow.begin(input.state), true);
            }
        }
        private static void rain(Input input, PoseStack poses, VertexConsumer out, boolean glow) {
            if (!StarOrreryEffects.effectsVisible(input.effects, input.distance)) return;
            StarOrreryCoreEffects.renderRain(poses, out, input.lit, input.effects, input.distance, input.flowTime, glow);
            StarOrreryCoreEffects.renderBuildingRain(poses, out, input.lit, input.effects,
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
