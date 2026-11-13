package com.atir.molecularmanipulator.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import java.nio.ByteBuffer;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.joml.Vector3f;

/** Defers only the private compact layer's original Forge buffer-sealing sort. */
public final class GpuEffectSorting {
    private static final int MIN_ASYNC_QUADS = 1024;
    private static final int MAX_DOMAINS = 8;
    private static final long MAX_BYTES = 64L * 1024 * 1024;
    private static final Map<BufferBuilder.RenderedBuffer, SealedQuadSorting.Job> JOBS = new IdentityHashMap<>();
    private static SealedQuadSorting.Pool pool;

    private GpuEffectSorting() {}

    public static boolean owns(VertexFormat format, VertexFormat.Mode mode) {
        return format == SingularityVertexLayout.COLOR_MATERIAL_FORMAT && mode == VertexFormat.Mode.QUADS;
    }

    public static SealedQuadSorting.Job seal(VertexFormat format, VertexFormat.Mode mode,
            Vector3f[] centroids, VertexSorting sorting, VertexFormat.IndexType type) {
        if (!owns(format, mode) || centroids == null || centroids.length < MIN_ASYNC_QUADS
                || !RenderSystem.isOnRenderThread() || Runtime.getRuntime().availableProcessors() < 2) return null;
        // These platform constants own their distance functions. Unknown callbacks and the older
        // Embeddium sorter (which holds a mutable Vector3f origin) stay on the caller thread.
        if (sorting != VertexSorting.DISTANCE_TO_ORIGIN && sorting != VertexSorting.ORTHOGRAPHIC_Z) return null;
        if (pool != null && pool.isClosed() && pool.activeJobs() != 0) return null;
        if (pool == null || pool.isClosed()) {
            int threads = Math.min(2, Runtime.getRuntime().availableProcessors() - 1);
            var workers = new ThreadPoolExecutor(threads, threads, 0L, TimeUnit.MILLISECONDS,
                    new ArrayBlockingQueue<>(MAX_DOMAINS), task -> {
                        var thread = new Thread(task, "omni-sealed-effect-sort");
                        thread.setDaemon(true);
                        return thread;
                    });
            pool = new SealedQuadSorting.Pool(workers, MAX_DOMAINS, MAX_BYTES);
        }
        return pool.trySubmit(centroids, type, sorting);
    }

    public static synchronized void attach(BufferBuilder.RenderedBuffer buffer, SealedQuadSorting.Job job) {
        if (job != null) JOBS.put(buffer, job);
    }

    /** Every consumer, including ordinary VertexBuffer upload, observes the completed sorted indices. */
    public static synchronized ByteBuffer indices(BufferBuilder.RenderedBuffer buffer) {
        var job = JOBS.get(buffer);
        return job == null ? null : job.result();
    }

    public static synchronized void release(BufferBuilder.RenderedBuffer buffer) {
        var job = JOBS.remove(buffer);
        if (job != null) job.close();
    }

    public static void clear() {
        RenderSystem.assertOnRenderThread();
        // Join writers without freeing indices still owned by an undrawn RenderedBuffer.
        if (pool != null) pool.close();
    }
}
