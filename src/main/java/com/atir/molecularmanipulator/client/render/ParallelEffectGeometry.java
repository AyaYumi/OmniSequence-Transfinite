package com.atir.molecularmanipulator.client.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Reusable, coarse color-geometry tasks on one shared pool. Capture all inputs on the render
 * thread; tasks may only emit geometry into their own consumer. Submit and close on the render
 * thread, finishing a pass before requesting the next render buffer.
 */
public final class ParallelEffectGeometry implements AutoCloseable {
    private static final Object LIFECYCLE = new Object();
    private static final Set<ParallelEffectGeometry> INSTANCES =
            Collections.newSetFromMap(new IdentityHashMap<>());
    private static ExecutorService workers;

    private final ArrayList<PreparedColorVertices> outputs = new ArrayList<>();
    private Frame active;

    public static boolean isAvailable() {
        return Runtime.getRuntime().availableProcessors() >= 3;
    }

    /**
     * Returns null for unavailable workers, fewer than two tasks, or a busy helper.
     * Callers should render synchronously in that case. Each task retains its own reusable
     * buffer; no builder, shader state, or Minecraft state is accessed during preparation.
     */
    @SafeVarargs
    public final Frame prepare(Consumer<VertexConsumer>... tasks) {
        return launch(null, null, tasks);
    }

    /**
     * Groups consecutive worker outputs into the caller's original passes. Ends are exclusive;
     * submit(pass, target) still emits exactly one pass and one batch, regardless of task count.
     */
    @SafeVarargs
    public final Frame prepareGroups(int[] groupEnds, Consumer<VertexConsumer>... tasks) {
        return prepareGroups(groupEnds, null, tasks);
    }

    /** Optional launch order affects scheduling only; output still follows the original task order. */
    @SafeVarargs
    public final Frame prepareGroups(int[] groupEnds, int[] launchOrder,
            Consumer<VertexConsumer>... tasks) {
        Objects.requireNonNull(groupEnds, "group ends");
        Objects.requireNonNull(tasks, "tasks");
        int previous = 0;
        for (int end : groupEnds) {
            if (end <= previous || end > tasks.length) throw new IllegalArgumentException("Invalid geometry group end");
            previous = end;
        }
        if (previous != tasks.length) throw new IllegalArgumentException("Groups must cover every geometry task");
        if (launchOrder != null) {
            if (launchOrder.length != tasks.length) throw new IllegalArgumentException("Launch order must cover every task");
            var seen = new boolean[tasks.length];
            for (int index : launchOrder) {
                Objects.checkIndex(index, tasks.length);
                if (seen[index]) throw new IllegalArgumentException("Duplicate geometry task in launch order");
                seen[index] = true;
            }
        }
        return launch(groupEnds.clone(), launchOrder == null ? null : launchOrder.clone(), tasks);
    }

    private Frame launch(int[] groupEnds, int[] launchOrder, Consumer<VertexConsumer>[] tasks) {
        Objects.requireNonNull(tasks, "tasks");
        for (var task : tasks) Objects.requireNonNull(task, "task");
        synchronized (LIFECYCLE) {
            if (tasks.length < 2 || active != null || !isAvailable()) return null;
            if (workers == null) {
                var sequence = new AtomicInteger();
                try {
                    workers = Executors.newFixedThreadPool(
                            com.atir.molecularmanipulator.util.MathCompat.clamp(Runtime.getRuntime().availableProcessors() / 2, 2, 4), task -> {
                                var thread = new Thread(task, "Omni effect geometry-" + sequence.incrementAndGet());
                                thread.setDaemon(true);
                                return thread;
                            });
                } catch (SecurityException unavailable) {
                    return null;
                }
            }
            while (outputs.size() < tasks.length) outputs.add(new PreparedColorVertices());
            var frame = new Frame(tasks.length, groupEnds);
            active = frame;
            INSTANCES.add(this);
            try {
                for (int scheduled = 0; scheduled < tasks.length; scheduled++) {
                    int index = launchOrder == null ? scheduled : launchOrder[scheduled];
                    var task = tasks[index];
                    var output = outputs.get(index);
                    frame.jobs[index] = CompletableFuture.runAsync(() -> task.accept(output.begin()), workers);
                }
            } catch (RejectedExecutionException | SecurityException unavailable) {
                frame.close(); // Join even the tasks accepted before a scheduling failure.
                return null;
            } catch (RuntimeException | Error failure) {
                try { frame.close(); }
                catch (RuntimeException | Error joining) { failure.addSuppressed(joining); }
                throw failure;
            }
            return frame;
        }
    }

    public final class Frame implements AutoCloseable {
        private final CompletableFuture<?>[] jobs;
        private final boolean[] submitted;
        private final int[] groupEnds;
        private boolean ready, closed, appendFailed;

        private Frame(int tasks, int[] groupEnds) {
            jobs = new CompletableFuture<?>[tasks];
            submitted = new boolean[tasks];
            this.groupEnds = groupEnds;
        }

        private void joinAll() {
            CompletionException failure = null;
            for (var job : jobs) {
                if (job == null) continue;
                try { job.join(); }
                catch (CompletionException error) {
                    // A future can rethrow the same exception object on every join. Use a
                    // fresh aggregate so try-with-resources can safely suppress close failures.
                    if (failure == null) failure = new CompletionException("Effect geometry preparation failed", error);
                    else failure.addSuppressed(error);
                }
            }
            if (failure != null) throw failure;
        }

        /** Joins and validates every task before the first append, then replays in caller order. */
        public void submit(int passIndex, VertexConsumer target) {
            if (groupEnds == null) submitRange(passIndex, passIndex + 1, target);
            else {
                Objects.checkIndex(passIndex, groupEnds.length);
                submitRange(passIndex == 0 ? 0 : groupEnds[passIndex - 1], groupEnds[passIndex], target);
            }
        }

        /** Concatenates disjoint task outputs in index order within one original pass batch. */
        public void submitRange(int fromInclusive, int toExclusive, VertexConsumer target) {
            Objects.requireNonNull(target, "target");
            synchronized (LIFECYCLE) {
                if (closed || active != this) throw new IllegalStateException("Geometry frame is closed");
                Objects.checkFromToIndex(fromInclusive, toExclusive, jobs.length);
                if (fromInclusive == toExclusive) throw new IllegalArgumentException("Empty geometry submission range");
                if (appendFailed) throw new IllegalStateException("Prepared geometry cannot be appended again");
                for (int index = fromInclusive; index < toExclusive; index++) {
                    if (submitted[index]) throw new IllegalStateException("Prepared geometry cannot be appended again");
                }
                if (!ready) {
                    joinAll();
                    for (int index = 0; index < jobs.length; index++) {
                        if ((outputs.get(index).count() & 3) != 0) {
                            throw new IllegalStateException("Incomplete prepared color quad in task " + index);
                        }
                    }
                    ready = true;
                }
                // A failed append may already have changed the builder. Never retry or replay it.
                for (int index = fromInclusive; index < toExclusive; index++) submitted[index] = true;
                try (var batch = SingularityShaderVertices.batch(target)) {
                    for (int index = fromInclusive; index < toExclusive; index++) outputs.get(index).submit(target);
                } catch (RuntimeException | Error failure) {
                    appendFailed = true;
                    throw failure;
                }
            }
        }

        @Override public void close() {
            synchronized (LIFECYCLE) {
                if (closed) return;
                try { joinAll(); }
                finally {
                    closed = true;
                    if (active == this) active = null;
                }
            }
        }
    }

    /** Releases one helper; it can be used again after closing. */
    @Override public void close() {
        synchronized (LIFECYCLE) {
            try {
                if (active != null) active.close();
            } finally {
                for (var output : outputs) output.close();
                outputs.clear();
                INSTANCES.remove(this);
            }
        }
    }

    /** Resource reload/logout hook. Joins all work before freeing buffers and resetting the pool. */
    public static void clear() {
        synchronized (LIFECYCLE) {
            Throwable failure = null;
            for (var instance : new ArrayList<>(INSTANCES)) {
                try { instance.close(); }
                catch (RuntimeException | Error error) {
                    if (failure == null) failure = error;
                    else if (failure != error) failure.addSuppressed(error);
                }
            }
            if (workers != null) workers.shutdown();
            workers = null;
            if (failure instanceof RuntimeException error) throw error;
            if (failure instanceof Error error) throw error;
        }
    }
}
