package com.atir.molecularmanipulator.client.render;

import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryUtil;

/** CPU work for a sealed Forge quad domain, with private centroid and native index storage. */
public final class SealedQuadSorting {
    private SealedQuadSorting() {}

    private record Snapshot(float[] positions, VertexFormat.IndexType type, VertexSorting sorting) {
        static Snapshot copy(Vector3f[] source, VertexFormat.IndexType type, VertexSorting sorting) {
            var positions = new float[Math.multiplyExact(source.length, 3)];
            for (int index = 0; index < source.length; index++) {
                positions[index * 3] = source[index].x;
                positions[index * 3 + 1] = source[index].y;
                positions[index * 3 + 2] = source[index].z;
            }
            return new Snapshot(positions, type, sorting);
        }

        ByteBuffer sort() {
            var centroids = new Vector3f[positions.length / 3];
            for (int index = 0; index < centroids.length; index++)
                centroids[index] = new Vector3f(positions[index * 3], positions[index * 3 + 1], positions[index * 3 + 2]);
            int[] order = sorting.sort(centroids);
            var indices = MemoryUtil.memAlloc(Math.multiplyExact(Math.multiplyExact(order.length, 6), type.bytes));
            try {
                long pointer = MemoryUtil.memAddress(indices);
                for (int quad : order) {
                    int first = quad * 4;
                    if (type == VertexFormat.IndexType.SHORT) {
                        MemoryUtil.memPutShort(pointer, (short) first);
                        MemoryUtil.memPutShort(pointer + 2, (short) (first + 1));
                        MemoryUtil.memPutShort(pointer + 4, (short) (first + 2));
                        MemoryUtil.memPutShort(pointer + 6, (short) (first + 2));
                        MemoryUtil.memPutShort(pointer + 8, (short) (first + 3));
                        MemoryUtil.memPutShort(pointer + 10, (short) first);
                        pointer += 12;
                    } else {
                        MemoryUtil.memPutInt(pointer, first);
                        MemoryUtil.memPutInt(pointer + 4, first + 1);
                        MemoryUtil.memPutInt(pointer + 8, first + 2);
                        MemoryUtil.memPutInt(pointer + 12, first + 2);
                        MemoryUtil.memPutInt(pointer + 16, first + 3);
                        MemoryUtil.memPutInt(pointer + 20, first);
                        pointer += 24;
                    }
                }
                return indices;
            } catch (RuntimeException | Error failure) {
                MemoryUtil.memFree(indices);
                throw failure;
            }
        }
    }

    /** Owned until the corresponding RenderedBuffer is released, including after pool shutdown. */
    public static final class Job implements AutoCloseable {
        private final Pool owner;
        private final long bytes;
        private final CompletableFuture<ByteBuffer> completion = new CompletableFuture<>();
        private boolean closed;

        private Job(Pool owner, long bytes) { this.owner = owner; this.bytes = bytes; }
        private void run(Snapshot snapshot) {
            try { completion.complete(snapshot.sort()); }
            catch (Throwable failure) { completion.completeExceptionally(failure); }
        }
        public synchronized ByteBuffer result() {
            if (closed) throw new IllegalStateException("Sealed quad sorting has been released");
            return completion.join().duplicate().order(java.nio.ByteOrder.nativeOrder());
        }
        private synchronized void awaitCompletion() {
            if (closed) return;
            try { completion.join(); }
            catch (CompletionException failed) { /* The index reader reports the failed sort. */ }
        }
        @Override public synchronized void close() {
            if (closed) return;
            try {
                try { MemoryUtil.memFree(completion.join()); }
                catch (CompletionException failed) { /* Disposal still releases its reservation. */ }
            } finally {
                closed = true;
                owner.release(this);
            }
        }
    }

    /** Bounds both queued work and completed outputs retained by deferred draws. */
    static final class Pool implements AutoCloseable {
        private final ExecutorService workers;
        private final int maxJobs;
        private final long maxBytes;
        private final Set<Job> active = new HashSet<>();
        private long reservedBytes;
        private boolean closed;

        Pool(ExecutorService workers, int maxJobs, long maxBytes) {
            this.workers = workers; this.maxJobs = maxJobs; this.maxBytes = maxBytes;
        }
        synchronized Job trySubmit(Vector3f[] centroids, VertexFormat.IndexType type, VertexSorting sorting) {
            long bytes = 4096L + centroids.length * (96L + 6L * type.bytes);
            if (closed || active.size() >= maxJobs || bytes > maxBytes - reservedBytes) return null;
            // BufferBuilder.getSortState may expose its centroid objects. Never share them with a worker.
            var snapshot = Snapshot.copy(centroids, type, sorting);
            var job = new Job(this, bytes);
            active.add(job);
            reservedBytes += bytes;
            try {
                workers.execute(() -> job.run(snapshot));
                return job;
            } catch (RejectedExecutionException | SecurityException unavailable) {
                job.completion.completeExceptionally(unavailable);
                job.close();
                return null;
            } catch (RuntimeException | Error failure) {
                job.completion.completeExceptionally(failure);
                job.close();
                throw failure;
            }
        }
        private synchronized void release(Job job) { if (active.remove(job)) reservedBytes -= job.bytes; }
        synchronized boolean isClosed() { return closed; }
        synchronized int activeJobs() { return active.size(); }
        @Override public void close() {
            ArrayList<Job> jobs;
            synchronized (this) { closed = true; jobs = new ArrayList<>(active); }
            try { for (var job : jobs) job.awaitCompletion(); }
            finally { workers.shutdown(); }
        }
    }
}
