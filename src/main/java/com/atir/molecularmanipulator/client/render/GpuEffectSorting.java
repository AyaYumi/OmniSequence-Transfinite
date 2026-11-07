package com.atir.molecularmanipulator.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import java.lang.reflect.Modifier;
import java.nio.ByteBuffer;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;

/** Optional MeshData bridge, exclusively for GpuEffectLayers' private compact format identity. */
public final class GpuEffectSorting {
    private static final int MIN_ASYNC_QUADS = 1024;
    private static final int MAX_DOMAINS = 8;
    private static final long MAX_BYTES = 64L * 1024 * 1024;
    private static SealedQuadSorting.Pool pool;

    private GpuEffectSorting() {}

    public static boolean owns(MeshData.DrawState draw) {
        // Do not match by layout equality: other renderers/mods may use an equivalent format.
        return draw.format() == SingularityVertexLayout.COLOR_MATERIAL_FORMAT
                && draw.mode() == VertexFormat.Mode.QUADS;
    }

    /** Only these audited implementations may be shared with workers. No arbitrary callbacks. */
    static boolean asyncSafe(VertexSorting sorting) {
        if (SafeSodium.CLASSES.contains(sorting.getClass())) return true;
        // Vanilla's two constants capture only internally owned origin/orthographic functions;
        // byDistance(Vector3f) and user DistanceFunction closures can capture mutable state.
        return (sorting == VertexSorting.DISTANCE_TO_ORIGIN || sorting == VertexSorting.ORTHOGRAPHIC_Z)
                && sorting.getClass().isSynthetic() && sorting.getClass().getNestHost() == VertexSorting.class;
    }

    private static SealedQuadSorting.Pool selectPool(MeshData.DrawState draw, VertexSorting sorting) {
        if (draw.vertexCount() / 4 < MIN_ASYNC_QUADS || !asyncSafe(sorting) || !RenderSystem.isOnRenderThread()
                || Runtime.getRuntime().availableProcessors() < 2) return null;
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
        return pool;
    }

    /** Render-thread resource reload/logout boundary. Drains abandoned as well as drawable domains. */
    public static void clear() {
        RenderSystem.assertOnRenderThread();
        // Retain the drained generation while meshes still own outputs: its reservations continue
        // to bound memory, and a replacement pool is created only after those owners release them.
        if (pool != null) pool.close();
    }

    /**
     * Mesh-owned state used by the optional mixin. Kept separate from the mixin so its actual
     * replace/read/close lifecycle is also exercised by native-buffer tests without a game launch.
     */
    public static final class MeshState implements AutoCloseable {
        private final BiFunction<MeshData.DrawState, VertexSorting, SealedQuadSorting.Pool> poolSelector;
        private SealedQuadSorting.Job pending;
        private ByteBufferBuilder.Result indices;
        private boolean closed;

        public MeshState(ByteBufferBuilder.Result indices) { this(indices, GpuEffectSorting::selectPool); }

        MeshState(ByteBufferBuilder.Result indices,
                BiFunction<MeshData.DrawState, VertexSorting, SealedQuadSorting.Pool> poolSelector) {
            this.indices = indices;
            this.poolSelector = poolSelector;
        }

        public MeshData.SortState sort(ByteBuffer vertices, MeshData.DrawState draw,
                ByteBufferBuilder destination, VertexSorting sorting) {
            checkOpen();
            var selected = poolSelector.apply(draw, sorting);
            var captured = selected == null ? null : selected.tryCapture(vertices, draw, sorting);
            var state = captured == null ? SealedQuadSorting.capture(vertices, draw) : captured.state();
            var next = captured == null ? null : captured.job();
            // Unknown sorters, tiny domains and a full pool use the same sorting object on the
            // caller thread. The destination is NEVER passed to a task.
            var result = next == null ? SealedQuadSorting.sortNow(state, destination, sorting) : null;
            var previous = pending;
            var previousIndices = indices;
            pending = next;
            indices = result;
            if (previous != null) previous.close();
            else if (previousIndices != null) previousIndices.close();
            return state; // A real, independently mutable compatibility SortState, never a placeholder.
        }

        /** Non-blocking field update after sort(); the async result is published only by result(). */
        public ByteBufferBuilder.Result currentResult() { checkOpen(); return indices; }

        /** Called at the original Layer.draw's index upload (and for any other indexBuffer reader). */
        public ByteBufferBuilder.Result result() {
            checkOpen();
            if (pending != null) indices = pending.result();
            return indices;
        }

        private void checkOpen() {
            if (closed) throw new IllegalStateException("Compact mesh sorting has been closed");
        }

        @Override public void close() {
            if (closed) return;
            try {
                if (pending != null) pending.close();
                else if (indices != null) indices.close();
            } finally {
                closed = true;
                pending = null;
                indices = null;
            }
        }
    }

    private static final class SafeSodium {
        static final Set<Class<?>> CLASSES = load();

        private static Set<Class<?>> load() {
            /* Audited Sodium 0.8.12 VertexSorters:
             * AbstractSorter.sort(Vector3f[]) allocates keys/order locally and invokes
             * RadixSort.sortIndirect(..., true), whose histogram/temp are also invocation-local.
             * Origin and OrthographicZ have no instance fields; Point has three final floats.
             * SortByFallback is deliberately NOT allowed: applyMetric uses its mutable
             * Vector3f scratch, and its DistanceFunction may itself read arbitrary live state.
             * Do not substitute VertexSorters.sort(ByteBuffer,...): that is a different path,
             * reads Sodium options and may use closest-point rather than centroid semantics.
             */
            try {
                String prefix = "net.caffeinemc.mods.sodium.client.util.sorting.VertexSorters$";
                ClassLoader loader = GpuEffectSorting.class.getClassLoader();
                var origin = Class.forName(prefix + "SortByDistanceToOrigin", false, loader);
                var point = Class.forName(prefix + "SortByDistanceToPoint", false, loader);
                var ortho = Class.forName(prefix + "SortByOrthographicZ", false, loader);
                var base = origin.getSuperclass();
                if (base != point.getSuperclass() || base != ortho.getSuperclass()
                        || !base.getName().equals(prefix + "AbstractSorter")
                        || !Modifier.isFinal(base.getDeclaredMethod("sort", org.joml.Vector3f[].class).getModifiers()))
                    return Set.of();
                for (var type : new Class<?>[]{origin, point, ortho, base}) {
                    for (var field : type.getDeclaredFields()) {
                        if (Modifier.isStatic(field.getModifiers())) continue;
                        if (type != point || field.getType() != float.class || !Modifier.isFinal(field.getModifiers()))
                            return Set.of();
                    }
                }
                return Set.of(origin, point, ortho);
            } catch (ReflectiveOperationException | LinkageError | SecurityException absentOrChanged) {
                return Set.of();
            }
        }
    }
}
