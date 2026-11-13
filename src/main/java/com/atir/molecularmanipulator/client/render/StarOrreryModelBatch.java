package com.atir.molecularmanipulator.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.system.MemoryStack;

/**
 * Persistent, per-helper star_orrery model batch. A batch keeps one independent native output per
 * draw index across frames: the stable mesh template is copied once, and the light/color memo is
 * retained. Each frame captures commands on the render thread, transforms them on workers, and
 * submits them in the original draw order. Closing a frame only joins its jobs and drops capture
 * references; native outputs stay alive for the next frame. Only {@link #release()} frees outputs
 * and removes the batch from the registry.
 */
final class StarOrreryModelBatch implements AutoCloseable {
    /** Conservative ceiling for caller-owned native copies. A larger frame uses the old path. */
    private static final long MAX_CAPTURED_BYTES = 64L * 1024L * 1024L;
    private static final int MAX_CAPTURED_DRAWS = 512;
    private static final Object EXECUTOR_LOCK = new Object();
    private static final Object REGISTRY_LOCK = new Object();
    private static final ArrayList<StarOrreryModelBatch> ACTIVE = new ArrayList<>();
    private static ExecutorService workers;

    private final ArrayList<Draw> draws = new ArrayList<>();
    private long bytes;
    private int count;
    private State state = State.NEW;
    private boolean cursorIssued;
    private boolean released;

    StarOrreryModelBatch() {
        // Registered exactly once per instance; begin() reuses this instance without touching ACTIVE.
        synchronized (REGISTRY_LOCK) { ACTIVE.add(this); }
    }

    /**
     * Opens this frame's capture on the persistent instance. Returns null when model preparation
     * is unavailable or this batch is busy or already released; callers keep the old path.
     */
    StarOrreryModelBatch begin() {
        if (released || state == State.OPEN || state == State.SEALED) return null;
        if (Runtime.getRuntime().availableProcessors() < 4
                || !SodiumVertexBatch.available()) return null;
        state = State.OPEN;
        count = 0;
        bytes = 0;
        cursorIssued = false;
        return this;
    }

    /**
     * Render-thread capture. False means the conservative byte/draw ceiling was reached and the
     * whole capture should be discarded; no partially prepared output is ever submitted. Draw and
     * output storage is reused by index, and an output is only recreated when the mesh identity
     * changes (for example after a model reload).
     */
    boolean add(ResourceLocation model, RenderType type, List<BakedQuad> quads,
            StarOrreryModelCache.Mesh mesh, PoseStack.Pose pose,
            int light, float brightness, float alpha) {
        if (state != State.OPEN) throw new IllegalStateException("Model batch is not capturing");
        long cost = Math.max(1L, mesh.bytes());
        if (count >= MAX_CAPTURED_DRAWS || bytes > MAX_CAPTURED_BYTES - cost) return false;
        Draw draw;
        if (count < draws.size()) {
            draw = draws.get(count);
        } else {
            draw = new Draw();
            draws.add(draw);
        }
        draw.capture(model, type, quads, mesh, pose, light, brightness, alpha);
        bytes += cost;
        count++;
        return true;
    }

    int count() { return count; }

    boolean usable() { return state == State.SEALED && count > 0; }

    /** Trims unused tail outputs, then queues all transforms before the actual BER. */
    boolean seal() {
        if (state != State.OPEN) return false;
        state = State.SEALED;
        trimToCount();
        var executor = executor();
        for (int index = 0; index < count; index++) {
            var draw = draws.get(index);
            draw.job = CompletableFuture.runAsync(draw::transform, executor);
        }
        return true;
    }

    Cursor openCursor() {
        if (state != State.SEALED || cursorIssued) return null;
        cursorIssued = true;
        return new Cursor(this);
    }

    /**
     * Consumes the next prepared command if it exactly matches this draw. A mismatch or an
     * unsupported target leaves the caller free to append synchronously before any mutation. A
     * failed transform is an actual bug and is surfaced as a fresh exception instead of silently
     * replaying the CPU path every frame. On success the native output is kept for reuse.
     */
    private boolean submit(Cursor cursor, ResourceLocation model, RenderType type,
            List<BakedQuad> quads, int light, float brightness, float alpha,
            MultiBufferSource buffers) {
        if (state != State.SEALED || cursor.batch != this || cursor.closed
                || cursor.index >= count) return false;
        var draw = draws.get(cursor.index++);
        if (!draw.matches(model, type, quads, light, brightness, alpha)) {
            discard(draw);
            return false;
        }
        draw.await();
        var output = draw.output;
        var mesh = draw.mesh;
        if (output == null || mesh == null) {
            throw new IllegalStateException("Prepared star_orrery model draw has no native output");
        }
        var consumer = buffers.getBuffer(type);
        var writer = SodiumVertexBatch.of(consumer);
        if (writer == null || writer.format() != mesh.format) {
            // Nothing was appended, so the ordinary submit path may still take this draw.
            return false;
        }
        try (var stack = MemoryStack.stackPush()) {
            writer.push(stack, output.address(), mesh.count);
        } catch (RuntimeException | Error failure) {
            // The append may already have written vertices. Never fall back/replay this draw.
            throw new IllegalStateException("Cannot append prepared star_orrery model " + model, failure);
        }
        // Keep the output: the next frame transforms it in place and reuses its memo.
        return true;
    }

    private static void discard(Draw draw) {
        // Join only. The native output stays allocated for the next frame.
        draw.await();
    }

    /**
     * Joins every outstanding transform, drops capture references, and returns the instance to the
     * idle state. Mesh templates and native outputs deliberately survive so the next frame copies
     * nothing. The batch stays in the registry until {@link #release()}.
     */
    @Override public void close() {
        boolean capturing = state == State.OPEN || state == State.SEALED;
        Throwable failure = joinAll();
        // Only an in-flight capture owns unused tail slots. An idle batch keeps its outputs.
        if (capturing) trimToCount();
        state = State.CLOSED;
        count = 0;
        cursorIssued = false;
        for (var draw : draws) draw.clearCapture();
        rethrow(failure);
    }

    /** Frees all persistent outputs and removes the batch from the registry. Idempotent. */
    void release() {
        synchronized (REGISTRY_LOCK) {
            if (released) return;
            released = true;
            ACTIVE.remove(this);
        }
        Throwable failure = null;
        try { close(); }
        catch (RuntimeException | Error error) { failure = error; }
        for (var draw : draws) {
            draw.clearCapture();
            try { draw.freeOutput(); }
            catch (RuntimeException | Error error) {
                if (failure == null) failure = error;
                else if (error != failure) failure.addSuppressed(error);
            }
        }
        draws.clear();
        rethrow(failure);
    }

    /** Called on resource reload/logout after the renderer released its helper-keyed batches. */
    static void clear() {
        ArrayList<StarOrreryModelBatch> active;
        synchronized (REGISTRY_LOCK) {
            active = new ArrayList<>(ACTIVE);
            ACTIVE.clear();
        }
        Throwable failure = null;
        for (var batch : active) {
            try { batch.release(); }
            catch (RuntimeException | Error error) {
                if (failure == null) failure = error;
                else if (error != failure) failure.addSuppressed(error);
            }
        }
        ExecutorService stale;
        synchronized (EXECUTOR_LOCK) {
            stale = workers;
            workers = null;
        }
        // Every release above joined its own jobs; a graceful shutdown is enough.
        if (stale != null) stale.shutdown();
        rethrow(failure);
    }

    private void trimToCount() {
        while (draws.size() > count) {
            var draw = draws.remove(draws.size() - 1);
            draw.freeOutput();
        }
    }

    private Throwable joinAll() {
        Throwable failure = null;
        for (var draw : draws) {
            try { draw.await(); }
            catch (RuntimeException | Error error) {
                if (failure == null) failure = error;
                else if (error != failure) failure.addSuppressed(error);
            }
        }
        return failure;
    }

    private static void rethrow(Throwable failure) {
        if (failure instanceof RuntimeException error) throw error;
        if (failure instanceof Error error) throw error;
    }

    private static ExecutorService executor() {
        synchronized (EXECUTOR_LOCK) {
            if (workers == null || workers.isShutdown()) {
                var sequence = new AtomicInteger();
                int threads = com.atir.molecularmanipulator.util.MathCompat.clamp(Runtime.getRuntime().availableProcessors() / 4, 2, 4);
                workers = Executors.newFixedThreadPool(threads, task -> {
                    var thread = new Thread(task, "Omni model geometry-" + sequence.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                });
            }
            return workers;
        }
    }

    private enum State { NEW, OPEN, SEALED, CLOSED }

    static final class Cursor implements AutoCloseable {
        private final StarOrreryModelBatch batch;
        private int index;
        private boolean closed;

        private Cursor(StarOrreryModelBatch batch) { this.batch = batch; }

        boolean submit(ResourceLocation model, RenderType type, List<BakedQuad> quads,
                int light, float brightness, float alpha, MultiBufferSource buffers) {
            return batch.submit(this, model, type, quads, light, brightness, alpha, buffers);
        }

        @Override public void close() { closed = true; }
    }

    private static final class Draw {
        private ResourceLocation model;
        private RenderType type;
        private List<BakedQuad> quads;
        private StarOrreryModelCache.Mesh mesh;
        private PoseStack.Pose pose;
        private int light;
        private float brightness, alpha;
        private StarOrreryModelCache.Mesh.Output output;
        private CompletableFuture<Void> job;

        private void capture(ResourceLocation model, RenderType type, List<BakedQuad> quads,
                StarOrreryModelCache.Mesh mesh, PoseStack.Pose pose,
                int light, float brightness, float alpha) {
            if (this.mesh != mesh) {
                // A different mesh template was baked (for example after a reload): copy it once.
                var replacement = mesh.newOutput();
                var previous = output;
                output = replacement;
                this.mesh = mesh;
                if (previous != null) previous.close();
            }
            this.model = model;
            this.type = type;
            this.quads = quads;
            if (this.pose == null) {
                this.pose = ForgeRenderSupport.copyPose(pose);
            } else {
                // Reuse the Pose container; only its values change each frame.
                this.pose.pose().set(pose.pose());
                this.pose.normal().set(pose.normal());
            }
            this.light = light;
            this.brightness = brightness;
            this.alpha = alpha;
            this.job = null;
        }

        private boolean matches(ResourceLocation model, RenderType type, List<BakedQuad> quads,
                int light, float brightness, float alpha) {
            return this.model.equals(model) && this.type == type && this.quads == quads
                    && this.light == light
                    && Float.floatToIntBits(this.brightness) == Float.floatToIntBits(brightness)
                    && Float.floatToIntBits(this.alpha) == Float.floatToIntBits(alpha);
        }

        private void transform() { mesh.transform(output, pose, light, brightness, alpha); }

        /** Joins this draw's job and clears the future reference. Failures become fresh exceptions. */
        private void await() {
            var pending = job;
            job = null;
            if (pending == null) return;
            try {
                pending.join();
            } catch (CompletionException | CancellationException failure) {
                throw new IllegalStateException("Cannot transform prepared star_orrery model draw", failure);
            }
        }

        private void clearCapture() {
            model = null;
            type = null;
            quads = null;
            pose = null;
            job = null;
            light = 0;
            brightness = 1F;
            alpha = 1F;
        }

        private void freeOutput() {
            var value = output;
            output = null;
            mesh = null;
            if (value != null) value.close();
        }
    }
}
