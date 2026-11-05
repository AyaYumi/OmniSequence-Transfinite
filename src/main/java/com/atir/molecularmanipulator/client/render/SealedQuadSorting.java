package com.atir.molecularmanipulator.client.render;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
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

/**
 * CPU-only work for one already sealed sorting domain. No task retains a mesh, a live vertex
 * view, a caller's SortState, a shared ByteBufferBuilder, or rendering/GL state.
 */
public final class SealedQuadSorting {
    private SealedQuadSorting() {}

    /** Match MeshData's diagonal midpoint, including its float arithmetic (not a four-corner average). */
    static MeshData.SortState capture(ByteBuffer vertices, MeshData.DrawState draw) {
        if (draw.mode() != VertexFormat.Mode.QUADS || !GpuEffectSorting.owns(draw))
            throw new IllegalArgumentException("Expected the owned compact quad format");
        int stride = draw.format().getVertexSize();
        if (draw.vertexCount() < 0 || (long) draw.vertexCount() * stride > vertices.remaining())
            throw new IllegalArgumentException("Incomplete compact vertex buffer");
        int quads = draw.vertexCount() / 4;
        var centroids = new Vector3f[quads];
        long start = MemoryUtil.memAddress(vertices);
        for (int quad = 0; quad < quads; quad++) {
            long first = start + (long) quad * stride * 4;
            long opposite = first + stride * 2L;
            centroids[quad] = new Vector3f(
                    (MemoryUtil.memGetFloat(first) + MemoryUtil.memGetFloat(opposite)) / 2F,
                    (MemoryUtil.memGetFloat(first + 4) + MemoryUtil.memGetFloat(opposite + 4)) / 2F,
                    (MemoryUtil.memGetFloat(first + 8) + MemoryUtil.memGetFloat(opposite + 8)) / 2F);
        }
        return new MeshData.SortState(centroids, draw.indexType());
    }

    /** Same sorting call and index pattern as SortState, without MutableLong/IntConsumer per-index dispatch. */
    static ByteBufferBuilder.Result sortNow(MeshData.SortState state, ByteBufferBuilder destination, VertexSorting sorting) {
        return write(destination, sorting.sort(state.centroids()), state.indexType());
    }

    private static ByteBufferBuilder.Result write(ByteBufferBuilder destination, int[] order, VertexFormat.IndexType type) {
        long pointer = destination.reserve(indexBytes(order.length, type));
        if (type == VertexFormat.IndexType.SHORT) {
            for (int quad : order) {
                int first = quad * 4;
                MemoryUtil.memPutShort(pointer, (short) first);
                MemoryUtil.memPutShort(pointer + 2, (short) (first + 1));
                MemoryUtil.memPutShort(pointer + 4, (short) (first + 2));
                MemoryUtil.memPutShort(pointer + 6, (short) (first + 2));
                MemoryUtil.memPutShort(pointer + 8, (short) (first + 3));
                MemoryUtil.memPutShort(pointer + 10, (short) first);
                pointer += 12;
            }
        } else if (type == VertexFormat.IndexType.INT) {
            for (int quad : order) {
                int first = quad * 4;
                MemoryUtil.memPutInt(pointer, first);
                MemoryUtil.memPutInt(pointer + 4, first + 1);
                MemoryUtil.memPutInt(pointer + 8, first + 2);
                MemoryUtil.memPutInt(pointer + 12, first + 2);
                MemoryUtil.memPutInt(pointer + 16, first + 3);
                MemoryUtil.memPutInt(pointer + 20, first);
                pointer += 24;
            }
        } else throw new IllegalArgumentException("Unsupported quad index type: " + type);
        return destination.build();
    }

    private static int indexBytes(int quads, VertexFormat.IndexType type) {
        return Math.multiplyExact(Math.multiplyExact(quads, 6), type.bytes);
    }

    /** Conservative charge includes both centroid representations, sorting scratch and native indices. */
    static long reservationBytes(MeshData.SortState state) {
        return 4096L + state.centroids().length * (96L + 6L * state.indexType().bytes);
    }

    private record Snapshot(float[] positions, VertexFormat.IndexType type, VertexSorting sorting) {
        static Snapshot copy(MeshData.SortState state, VertexSorting sorting) {
            var source = state.centroids();
            float[] positions = new float[Math.multiplyExact(source.length, 3)];
            for (int index = 0; index < source.length; index++) {
                var value = source[index];
                positions[index * 3] = value.x;
                positions[index * 3 + 1] = value.y;
                positions[index * 3 + 2] = value.z;
            }
            return new Snapshot(positions, state.indexType(), sorting);
        }

        OwnedIndices sort() {
            // The compatibility SortState is public and mutable. Even its Vector3f objects must
            // not alias these task-owned vectors. Construct the second representation off-thread.
            var centroids = new Vector3f[positions.length / 3];
            for (int index = 0; index < centroids.length; index++)
                centroids[index] = new Vector3f(positions[index * 3], positions[index * 3 + 1], positions[index * 3 + 2]);
            int[] order = sorting.sort(centroids); // Call the captured implementation, not a reconstructed comparator.
            var storage = new ByteBufferBuilder(Math.max(1, indexBytes(order.length, type)));
            try {
                return new OwnedIndices(storage, write(storage, order, type));
            } catch (RuntimeException | Error failure) {
                storage.close();
                throw failure;
            }
        }
    }

    private record OwnedIndices(ByteBufferBuilder storage, ByteBufferBuilder.Result result) implements AutoCloseable {
        @Override public void close() {
            try { if (result != null) result.close(); }
            finally { storage.close(); }
        }
    }

    /**
     * Non-cancellable task ownership. Access/close happen on the mesh owner thread, never the worker.
     * A returned Result is valid until close, just like an ordinary MeshData's index view.
     */
    public static final class Job implements AutoCloseable {
        private final Pool owner;
        private final long bytes;
        private final CompletableFuture<OwnedIndices> completion = new CompletableFuture<>();
        private boolean closed;

        private Job(Pool owner, long bytes) { this.owner = owner; this.bytes = bytes; }

        private void run(Snapshot snapshot) {
            try { completion.complete(snapshot.sort()); }
            catch (Throwable failure) { completion.completeExceptionally(failure); }
        }

        public synchronized ByteBufferBuilder.Result result() {
            if (closed) throw new IllegalStateException("Sealed quad sorting has been released");
            return completion.join().result();
        }

        private synchronized void awaitCompletion() {
            if (closed) return;
            try { completion.join(); }
            catch (CompletionException failed) { /* The mesh reader reports failure; shutdown still drains every job. */ }
        }

        /** The mesh retains its result across pool shutdown, and releases it only on replacement/close. */
        @Override public synchronized void close() {
            if (closed) return;
            try {
                OwnedIndices output;
                try { output = completion.join(); }
                catch (CompletionException failed) { return; } // result() reports failures; disposal still drains every job.
                output.close();
            } finally {
                closed = true;
                owner.release(this);
            }
        }
    }

    /** Bounded by both domain count and retained bytes, including already completed but undrawn jobs. */
    static final class Pool implements AutoCloseable {
        private final ExecutorService workers;
        private final int maxJobs;
        private final long maxBytes;
        private final Set<Job> active = new HashSet<>();
        private long reservedBytes;
        private boolean closed;

        Pool(ExecutorService workers, int maxJobs, long maxBytes) {
            this.workers = workers;
            this.maxJobs = maxJobs;
            this.maxBytes = maxBytes;
        }

        synchronized Job trySubmit(MeshData.SortState state, VertexSorting sorting) {
            long bytes = reservationBytes(state);
            if (closed || active.size() >= maxJobs || bytes > maxBytes - reservedBytes) return null;
            // Copy before publishing the SortState or scheduling. The executor cannot observe live caller memory.
            var snapshot = Snapshot.copy(state, sorting);
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

        private synchronized void release(Job job) {
            if (active.remove(job)) reservedBytes -= job.bytes;
        }

        synchronized boolean isClosed() { return closed; }
        synchronized int activeJobs() { return active.size(); }
        synchronized long reservedBytes() { return reservedBytes; }

        @Override public void close() {
            ArrayList<Job> jobs;
            synchronized (this) {
                closed = true;
                jobs = new ArrayList<>(active);
            }
            // Join writers without invalidating index storage still owned by sealed, undrawn meshes.
            Throwable failure = null;
            try {
                for (var job : jobs) {
                    try { job.awaitCompletion(); }
                    catch (RuntimeException | Error error) {
                        if (failure == null) failure = error;
                        else if (error != failure) failure.addSuppressed(error);
                    }
                }
            } finally { workers.shutdown(); }
            if (failure instanceof RuntimeException error) throw error;
            if (failure instanceof Error error) throw error;
        }
    }
}
