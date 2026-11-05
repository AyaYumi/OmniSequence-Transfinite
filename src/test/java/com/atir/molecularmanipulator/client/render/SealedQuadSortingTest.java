package com.atir.molecularmanipulator.client.render;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import com.mojang.blaze3d.vertex.VertexSorting;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryUtil;

/** Native-buffer tests; no window, RenderSystem initialization or GL context is needed. */
class SealedQuadSortingTest {
    private static final VertexFormat COMPACT = SingularityVertexLayout.COLOR_MATERIAL_FORMAT;

    @Test void formatScopeIsIdentityNotAnEquivalentForeignLayout() {
        var foreign = VertexFormat.builder().add("Position", VertexFormatElement.POSITION)
                .add("Color", VertexFormatElement.COLOR).add("UV1", VertexFormatElement.UV1)
                .add("UV2", VertexFormatElement.UV2).build();
        assertNotSame(COMPACT, foreign);
        assertEquals(24, COMPACT.getVertexSize());
        assertEquals(0, COMPACT.getOffset(VertexFormatElement.POSITION));
        assertTrue(GpuEffectSorting.owns(draw(COMPACT, 4, VertexFormat.Mode.QUADS, VertexFormat.IndexType.SHORT)));
        for (var format : List.of(foreign, DefaultVertexFormat.POSITION_COLOR, DefaultVertexFormat.NEW_ENTITY))
            assertFalse(GpuEffectSorting.owns(draw(format, 4, VertexFormat.Mode.QUADS, VertexFormat.IndexType.SHORT)));
        assertFalse(GpuEffectSorting.owns(draw(COMPACT, 4, VertexFormat.Mode.TRIANGLES, VertexFormat.IndexType.SHORT)));
    }

    @Test void diagonalSnapshotMatchesRealMeshDataIncludingFloatEdgesAndDoesNotTouchMaterials() {
        try (var source = mesh(8, 7); var sorted = new ByteBufferBuilder(128)) {
            long start = MemoryUtil.memAddress(source.mesh.vertexBuffer());
            float[] values = {0F, -0F, Float.MAX_VALUE, -Float.MAX_VALUE,
                    Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NaN, Float.MIN_VALUE};
            for (int quad = 0; quad < values.length; quad++) {
                long first = start + quad * 96L;
                MemoryUtil.memPutFloat(first, values[quad]);
                MemoryUtil.memPutFloat(first + 48, values[quad]);
                MemoryUtil.memPutFloat(first + 4, -values[quad]);
                MemoryUtil.memPutFloat(first + 52, values[quad]);
                // Corners one/three must NOT contribute to MeshData's diagonal midpoint.
                MemoryUtil.memPutFloat(first + 24, 123456F);
                MemoryUtil.memPutFloat(first + 72, -654321F);
            }
            byte[] before = bytes(source.mesh.vertexBuffer());
            var actual = SealedQuadSorting.capture(source.mesh.vertexBuffer(), source.mesh.drawState());
            var expected = source.mesh.sortQuads(sorted, VertexSorting.DISTANCE_TO_ORIGIN);
            assertCentroids(expected, actual);
            assertArrayEquals(before, bytes(source.mesh.vertexBuffer()), "Colors and per-vertex materials are untouched");
            assertSame(source.mesh.drawState().indexType(), actual.indexType());
        }
    }

    @Test void captureRejectsAnIncompleteOrForeignBufferBeforeReadingNativeMemory() {
        try (var storage = new ByteBufferBuilder(96)) {
            storage.reserve(95);
            try (var result = storage.build()) {
                assertThrows(IllegalArgumentException.class, () -> SealedQuadSorting.capture(result.byteBuffer(),
                        draw(COMPACT, 4, VertexFormat.Mode.QUADS, VertexFormat.IndexType.SHORT)));
                assertThrows(IllegalArgumentException.class, () -> SealedQuadSorting.capture(result.byteBuffer(),
                        draw(DefaultVertexFormat.POSITION_COLOR, 4, VertexFormat.Mode.QUADS, VertexFormat.IndexType.SHORT)));
            }
        }
    }

    @Test void directWriterMatchesTheRealSortStateForShortIntDistancesAndEqualKeys() {
        var sorters = List.of(VertexSorting.DISTANCE_TO_ORIGIN, VertexSorting.ORTHOGRAPHIC_Z,
                VertexSorting.byDistance(13.25F, -27F, 91.5F),
                VertexSorting.byDistance(value -> value.x * 2F - value.y + value.z));
        for (int count : new int[]{0, 1, 2, 79, 80, 81, 1024, 16383, 16384})
            for (var type : VertexFormat.IndexType.values()) for (var sorter : sorters) {
                var state = centroids(count, type, 41);
                try (var output = new ByteBufferBuilder(64);
                     var result = SealedQuadSorting.sortNow(state, output, sorter)) {
                    assertArrayEquals(reference(state, sorter), bytes(result), count + " / " + type + " / " + sorter);
                }
            }
    }

    @Test void equalDistanceKeysKeepAuthoredOrderAndUseTheOriginalTrianglePattern() {
        var state = new MeshData.SortState(new Vector3f[]{new Vector3f(1, 0, 0), new Vector3f(-1, 0, 0),
                new Vector3f(0, 0, 2), new Vector3f(0, 1, 0)}, VertexFormat.IndexType.SHORT);
        assertArrayEquals(indices(VertexFormat.IndexType.SHORT, 2, 0, 1, 3), reference(state, VertexSorting.DISTANCE_TO_ORIGIN));
        try (var pool = pool(2, 8, 64L << 20); var job = pool.trySubmit(state, VertexSorting.DISTANCE_TO_ORIGIN)) {
            assertNotNull(job);
            assertArrayEquals(indices(VertexFormat.IndexType.SHORT, 2, 0, 1, 3), bytes(job.result()));
        }
    }

    @Test void indexTypeAndNarrowingAreNotRecomputedFromCountsOrChangedByTheWriter() {
        int[] order = {16383, 0, 16384, 65535, -1, 536870911};
        VertexSorting customOrder = unused -> order;
        for (var type : VertexFormat.IndexType.values()) {
            var state = centroids(6, type, 1);
            try (var output = new ByteBufferBuilder(64);
                 var result = SealedQuadSorting.sortNow(state, output, customOrder)) {
                assertArrayEquals(indices(type, order), bytes(result));
                assertArrayEquals(reference(state, customOrder), bytes(result));
                assertEquals(order.length * 6 * type.bytes, result.byteBuffer().remaining());
            }
        }
    }

    @Test void realMeshesOnEitherSideOfTheIndexTypeBoundaryRetainTheirTypeAndIndexBytes() {
        for (int quads : new int[]{16383, 16384, 16385}) {
            try (var source = mesh(quads, quads); var pool = pool(2, 8, 64L << 20)) {
                var state = SealedQuadSorting.capture(source.mesh.vertexBuffer(), source.mesh.drawState());
                assertSame(quads < 16384 ? VertexFormat.IndexType.SHORT : VertexFormat.IndexType.INT, state.indexType());
                try (var job = pool.trySubmit(state, VertexSorting.DISTANCE_TO_ORIGIN)) {
                    assertNotNull(job);
                    assertArrayEquals(reference(state, VertexSorting.DISTANCE_TO_ORIGIN), bytes(job.result()));
                }
            }
        }
    }

    @Test void onlyAuditedSortingObjectsCanRunAsync() {
        assertTrue(GpuEffectSorting.asyncSafe(VertexSorting.DISTANCE_TO_ORIGIN));
        assertTrue(GpuEffectSorting.asyncSafe(VertexSorting.ORTHOGRAPHIC_Z));
        assertFalse(GpuEffectSorting.asyncSafe(VertexSorting.byDistance(new Vector3f(1, 2, 3))));
        assertFalse(GpuEffectSorting.asyncSafe(VertexSorting.byDistance(value -> value.x)));
        assertFalse(GpuEffectSorting.asyncSafe(values -> VertexSorting.DISTANCE_TO_ORIGIN.sort(values)));
    }

    @Test void workerOwnsItsSnapshotAndDoesNotRetainTheCompatibilityStateSourceOrSharedBuilder() throws Exception {
        var gate = new Gate();
        var taskThread = new AtomicReference<Thread>();
        VertexSorting captured = values -> {
            taskThread.set(Thread.currentThread());
            gate.block();
            return VertexSorting.DISTANCE_TO_ORIGIN.sort(values);
        };
        try (var pool = pool(1, 8, 64L << 20); var caller = Executors.newSingleThreadExecutor()) {
            try (var source = mesh(257, 13); var shared = new GuardedBuilder();
                 var sorting = new GpuEffectSorting.MeshState(null, pool::trySubmit)) {
                byte[] originalVertices = bytes(source.mesh.vertexBuffer());
                var expectedState = SealedQuadSorting.capture(source.mesh.vertexBuffer(), source.mesh.drawState());
                byte[] expected = reference(expectedState, VertexSorting.DISTANCE_TO_ORIGIN);
                var returned = sorting.sort(source.mesh.vertexBuffer(), source.mesh.drawState(), shared, captured);
                try {
                    assertTrue(gate.started.await(5, TimeUnit.SECONDS));
                    assertNull(sorting.currentResult(), "No placeholder index buffer is published during a pending sort");
                    assertNotSame(Thread.currentThread(), taskThread.get());
                    assertEquals(0, shared.calls);
                    assertCentroids(expectedState, returned);
                    assertArrayEquals(originalVertices, bytes(source.mesh.vertexBuffer()));
                    // SortState's public array AND the individual vectors can be changed by its caller.
                    returned.centroids()[0].set(999999, -999999, 12345);
                    Arrays.fill(returned.centroids(), new Vector3f());
                    source.close();
                    shared.close();
                    var waiting = caller.submit(() -> bytes(sorting.result()));
                    assertThrows(TimeoutException.class, () -> waiting.get(50, TimeUnit.MILLISECONDS));
                    gate.release.countDown();
                    assertArrayEquals(expected, waiting.get(5, TimeUnit.SECONDS));
                    assertArrayEquals(indices(returned.indexType(), range(257)), reference(returned, VertexSorting.DISTANCE_TO_ORIGIN));
                    assertEquals(0, shared.calls, "Workers must never reserve/build through the caller's builder");
                } finally { gate.release.countDown(); }
            }
            assertEquals(0, pool.activeJobs());
            assertEquals(0, pool.reservedBytes());
        }
    }

    @Test void originallySeparateDomainsMayCompleteInAnyOrderWithoutMergingOrReorderingTheirIndices() throws Exception {
        var gate = new Gate();
        VertexSorting slow = values -> { gate.block(); return VertexSorting.ORTHOGRAPHIC_Z.sort(values); };
        try (var pool = pool(2, 8, 64L << 20); var first = mesh(129, 61); var second = mesh(81, 29);
             var unused = new GuardedBuilder(); var a = new GpuEffectSorting.MeshState(null, pool::trySubmit);
             var b = new GpuEffectSorting.MeshState(null, pool::trySubmit)) {
            var aState = a.sort(first.mesh.vertexBuffer(), first.mesh.drawState(), unused, slow);
            try {
                assertTrue(gate.started.await(5, TimeUnit.SECONDS));
                var bState = b.sort(second.mesh.vertexBuffer(), second.mesh.drawState(), unused, VertexSorting.DISTANCE_TO_ORIGIN);
                assertArrayEquals(reference(bState, VertexSorting.DISTANCE_TO_ORIGIN), bytes(b.result()));
                gate.release.countDown();
                assertArrayEquals(reference(aState, VertexSorting.ORTHOGRAPHIC_Z), bytes(a.result()));
                assertEquals(129 * 6 * aState.indexType().bytes, a.result().byteBuffer().remaining());
                assertEquals(81 * 6 * bState.indexType().bytes, b.result().byteBuffer().remaining());
                assertEquals(0, unused.calls);
            } finally { gate.release.countDown(); }
        }
    }

    @Test void completedButUndrawnJobsStillCountAgainstBothBudgetsAndReleaseAllowsReuse() {
        var state = centroids(81, VertexFormat.IndexType.INT, 3);
        long charge = SealedQuadSorting.reservationBytes(state);
        try (var pool = pool(2, 2, charge * 2)) {
            var first = pool.trySubmit(state, VertexSorting.DISTANCE_TO_ORIGIN);
            var second = pool.trySubmit(state, VertexSorting.ORTHOGRAPHIC_Z);
            assertNotNull(first);
            assertNotNull(second);
            first.result(); second.result();
            assertEquals(2, pool.activeJobs());
            assertEquals(charge * 2, pool.reservedBytes());
            assertNull(pool.trySubmit(state, VertexSorting.DISTANCE_TO_ORIGIN));
            first.close(); first.close();
            assertEquals(charge, pool.reservedBytes());
            var third = pool.trySubmit(state, VertexSorting.DISTANCE_TO_ORIGIN);
            assertNotNull(third);
            assertArrayEquals(reference(state, VertexSorting.DISTANCE_TO_ORIGIN), bytes(third.result()));
            second.close(); third.close();
        }
        try (var pool = pool(1, 8, charge - 1)) {
            assertNull(pool.trySubmit(state, VertexSorting.DISTANCE_TO_ORIGIN));
            assertEquals(0, pool.activeJobs());
            assertEquals(0, pool.reservedBytes());
        }
    }

    @Test void rejectionFallsBackSynchronouslyWithoutLeakingReservationsOrTouchingForeignThreads() {
        var workers = Executors.newSingleThreadExecutor();
        workers.shutdown();
        try (var pool = new SealedQuadSorting.Pool(workers, 8, 64L << 20);
             var source = mesh(17, 31); var shared = new GuardedBuilder();
             var sorting = new GpuEffectSorting.MeshState(null, pool::trySubmit)) {
            var state = sorting.sort(source.mesh.vertexBuffer(), source.mesh.drawState(), shared, VertexSorting.ORTHOGRAPHIC_Z);
            assertTrue(shared.calls > 0);
            assertSame(sorting.currentResult(), sorting.result());
            assertArrayEquals(reference(state, VertexSorting.ORTHOGRAPHIC_Z), bytes(sorting.result()));
            assertEquals(0, pool.activeJobs());
            assertEquals(0, pool.reservedBytes());
        }
    }

    @Test void replacingASortDrainsAndReleasesThePreviousJobAndKeepsTheNewTypeAndBytes() {
        try (var pool = pool(2, 8, 64L << 20); var source = mesh(128, 97); var shared = new GuardedBuilder();
             var sorting = new GpuEffectSorting.MeshState(null, (state, sorter) ->
                     GpuEffectSorting.asyncSafe(sorter) ? pool.trySubmit(state, sorter) : null)) {
            var first = sorting.sort(source.mesh.vertexBuffer(), source.mesh.drawState(), shared, VertexSorting.ORTHOGRAPHIC_Z);
            var oldResult = sorting.result();
            assertArrayEquals(reference(first, VertexSorting.ORTHOGRAPHIC_Z), bytes(oldResult));
            VertexSorting synchronous = values -> VertexSorting.DISTANCE_TO_ORIGIN.sort(values);
            var next = sorting.sort(source.mesh.vertexBuffer(), source.mesh.drawState(), shared, synchronous);
            assertEquals(0, pool.activeJobs());
            assertEquals(0, pool.reservedBytes());
            assertThrows(IllegalStateException.class, oldResult::byteBuffer);
            assertArrayEquals(reference(next, synchronous), bytes(sorting.result()));
            var last = sorting.sort(source.mesh.vertexBuffer(), source.mesh.drawState(), shared, VertexSorting.DISTANCE_TO_ORIGIN);
            assertEquals(1, pool.activeJobs());
            assertArrayEquals(reference(last, VertexSorting.DISTANCE_TO_ORIGIN), bytes(sorting.result()));
        }
    }

    @Test void failingSynchronousReplacementLeavesThePreviousIndicesAlive() {
        try (var pool = pool(1, 8, 64L << 20); var source = mesh(31, 87); var shared = new GuardedBuilder();
             var sorting = new GpuEffectSorting.MeshState(null, (state, sorter) ->
                     GpuEffectSorting.asyncSafe(sorter) ? pool.trySubmit(state, sorter) : null)) {
            var first = sorting.sort(source.mesh.vertexBuffer(), source.mesh.drawState(), shared, VertexSorting.ORTHOGRAPHIC_Z);
            var previous = sorting.result();
            var failure = new IllegalArgumentException("Sort failed");
            assertSame(failure, assertThrows(IllegalArgumentException.class, () -> sorting.sort(source.mesh.vertexBuffer(),
                    source.mesh.drawState(), shared, ignored -> { throw failure; })));
            assertSame(previous, sorting.result());
            assertArrayEquals(reference(first, VertexSorting.ORTHOGRAPHIC_Z), bytes(sorting.result()));
            assertEquals(1, pool.activeJobs());
        }
    }

    @Test void closeWaitsForEveryWriterEvenAfterAnotherTaskFailedAndNeverCancelsQueuedWork() throws Exception {
        var gate = new Gate();
        var finished = new CountDownLatch(1);
        var expectedFailure = new IllegalArgumentException("Failed worker");
        var state = centroids(128, VertexFormat.IndexType.INT, 17);
        try (var pool = pool(2, 8, 64L << 20); var caller = Executors.newSingleThreadExecutor()) {
            var failed = pool.trySubmit(state, ignored -> { throw expectedFailure; });
            var blocked = pool.trySubmit(state, values -> {
                gate.block();
                try { return VertexSorting.DISTANCE_TO_ORIGIN.sort(values); }
                finally { finished.countDown(); }
            });
            assertNotNull(blocked);
            try {
                assertTrue(gate.started.await(5, TimeUnit.SECONDS));
                assertSame(expectedFailure, assertThrows(CompletionException.class, failed::result).getCause());
                var closing = caller.submit(pool::close);
                assertThrows(TimeoutException.class, () -> closing.get(50, TimeUnit.MILLISECONDS));
                assertEquals(1, finished.getCount());
                gate.release.countDown();
                closing.get(5, TimeUnit.SECONDS);
                assertEquals(0, finished.getCount());
                assertEquals(2, pool.activeJobs());
                assertNotNull(blocked.result());
                assertNull(pool.trySubmit(state, VertexSorting.DISTANCE_TO_ORIGIN));
                failed.close();
                blocked.close();
                assertEquals(0, pool.activeJobs());
                assertEquals(0, pool.reservedBytes());
            } finally { gate.release.countDown(); }
        }
    }

    @Test void poolShutdownPreservesUndrawnMeshIndicesUntilTheirOwnerCloses() {
        try (var pool = pool(1, 8, 64L << 20); var source = mesh(17, 67); var shared = new GuardedBuilder();
             var sorting = new GpuEffectSorting.MeshState(null, pool::trySubmit)) {
            sorting.sort(source.mesh.vertexBuffer(), source.mesh.drawState(), shared, VertexSorting.DISTANCE_TO_ORIGIN);
            var result = sorting.result();
            pool.close();
            assertArrayEquals(reference(SealedQuadSorting.capture(source.mesh.vertexBuffer(), source.mesh.drawState()),
                    VertexSorting.DISTANCE_TO_ORIGIN), bytes(result));
            assertSame(result, sorting.result());
            assertEquals(1, pool.activeJobs());
            sorting.close(); sorting.close();
            assertThrows(IllegalStateException.class, result::byteBuffer);
            assertThrows(IllegalStateException.class, sorting::currentResult);
            assertThrows(IllegalStateException.class, () -> sorting.sort(source.mesh.vertexBuffer(),
                    source.mesh.drawState(), shared, VertexSorting.DISTANCE_TO_ORIGIN));
            assertEquals(0, pool.activeJobs());
            assertEquals(0, pool.reservedBytes());
        }
    }

    @Test void failedAsyncResultIsReportedAndDisposalStillFreesTheReservation() {
        try (var pool = pool(1, 8, 64L << 20); var source = mesh(17, 71); var shared = new GuardedBuilder();
             var sorting = new GpuEffectSorting.MeshState(null, pool::trySubmit)) {
            sorting.sort(source.mesh.vertexBuffer(), source.mesh.drawState(), shared, ignored -> { throw new IllegalStateException("Failed"); });
            assertInstanceOf(IllegalStateException.class, assertThrows(CompletionException.class, sorting::result).getCause());
            sorting.close();
            assertEquals(0, pool.activeJobs());
            assertEquals(0, pool.reservedBytes());
            assertEquals(0, shared.calls);
        }
    }

    @Test void optionalRealSodiumSortersMatchTheirActualSortStateBytesAcrossRadixAndIndexBoundaries() throws Exception {
        Class<?> sodium = sodium();
        var origin = (VertexSorting) sodium.getMethod("distance", float.class, float.class, float.class).invoke(null, 0F, 0F, 0F);
        var point = (VertexSorting) sodium.getMethod("distance", float.class, float.class, float.class).invoke(null, 13F, -27F, 91F);
        var ortho = (VertexSorting) sodium.getMethod("orthographicZ").invoke(null);
        for (var sorter : List.of(origin, point, ortho)) {
            assertTrue(GpuEffectSorting.asyncSafe(sorter));
            try (var pool = pool(2, 8, 64L << 20)) {
                for (int count : new int[]{0, 1, 79, 80, 81, 1024, 16383, 16384})
                    for (var type : VertexFormat.IndexType.values()) {
                        var state = centroids(count, type, 73);
                        try (var job = pool.trySubmit(state, sorter)) {
                            assertNotNull(job);
                            assertArrayEquals(reference(state, sorter), bytes(job.result()), count + " / " + type + " / " + sorter);
                        }
                    }
            }
        }
    }

    @Test void optionalRealSodiumFallbackHasMutableScratchAndIsNeverAcceptedForAsync() throws Exception {
        Class<?> sodium = sodium();
        var renderThread = Thread.currentThread();
        VertexSorting.DistanceFunction callback = value -> {
            assertSame(renderThread, Thread.currentThread(), "Fallback callbacks must run on the caller thread");
            return value.z;
        };
        var fallback = (VertexSorting) sodium.getMethod("fallback", VertexSorting.DistanceFunction.class).invoke(null, callback);
        assertEquals(Vector3f.class, fallback.getClass().getDeclaredField("scratch").getType());
        assertFalse(GpuEffectSorting.asyncSafe(fallback));
        try (var pool = pool(1, 8, 64L << 20); var source = mesh(81, 73); var shared = new GuardedBuilder();
             var sorting = new GpuEffectSorting.MeshState(null, (state, sorter) ->
                     GpuEffectSorting.asyncSafe(sorter) ? pool.trySubmit(state, sorter) : null)) {
            var state = sorting.sort(source.mesh.vertexBuffer(), source.mesh.drawState(), shared, fallback);
            assertArrayEquals(reference(state, fallback), bytes(sorting.result()));
            assertEquals(0, pool.activeJobs());
            assertTrue(shared.calls > 0);
        }
    }

    @Test void optionalRealSodiumFixedPointSorterCanBeSharedAcrossIndependentDomains() throws Exception {
        var point = (VertexSorting) sodium().getMethod("distance", float.class, float.class, float.class).invoke(null, 13F, -27F, 91F);
        try (var pool = pool(4, 8, 64L << 20)) {
            var expected = new ArrayList<byte[]>();
            var jobs = new ArrayList<SealedQuadSorting.Job>();
            try {
                for (int domain = 0; domain < 8; domain++) {
                    var state = centroids(32768, VertexFormat.IndexType.INT, domain * 11L);
                    expected.add(reference(state, point));
                    jobs.add(pool.trySubmit(state, point));
                }
                for (int domain = jobs.size() - 1; domain >= 0; domain--) {
                    assertNotNull(jobs.get(domain));
                    assertArrayEquals(expected.get(domain), bytes(jobs.get(domain).result()));
                }
            } finally { for (var job : jobs) if (job != null) job.close(); }
        }
    }

    private static Class<?> sodium() throws ClassNotFoundException {
        try { return Class.forName("net.caffeinemc.mods.sodium.client.util.sorting.VertexSorters"); }
        catch (ClassNotFoundException absent) {
            assumeTrue(false, "Add the existing Sodium runtime JAR to testRuntimeOnly for the optional real-Sodium suite");
            throw absent;
        }
    }

    private static SealedQuadSorting.Pool pool(int threads, int maxJobs, long maxBytes) {
        return new SealedQuadSorting.Pool(Executors.newFixedThreadPool(threads), maxJobs, maxBytes);
    }

    private static MeshData.SortState centroids(int count, VertexFormat.IndexType type, long seed) {
        var random = new Random(seed);
        var positions = new Vector3f[count];
        for (int index = 0; index < count; index++) {
            // Quantization deliberately creates many equal keys and repeated centroids.
            positions[index] = new Vector3f(random.nextInt(17) - 8, random.nextInt(17) - 8, random.nextInt(17) - 8);
        }
        if (count > 80) {
            positions[3] = new Vector3f(-0F, 0F, -0F);
            positions[17] = new Vector3f(Float.NaN, 0F, 1F);
            positions[43] = new Vector3f(Float.POSITIVE_INFINITY, -1F, Float.NEGATIVE_INFINITY);
        }
        return new MeshData.SortState(positions, type);
    }

    private static MeshData.DrawState draw(VertexFormat format, int vertices, VertexFormat.Mode mode, VertexFormat.IndexType type) {
        return new MeshData.DrawState(format, vertices, mode.indexCount(vertices), mode, type);
    }

    private static CompactMesh mesh(int quads, long seed) {
        var storage = new ByteBufferBuilder(Math.max(1, quads * 96));
        try {
            long start = storage.reserve(quads * 96);
            var random = new Random(seed);
            for (int vertex = 0; vertex < quads * 4; vertex++) {
                long pointer = start + vertex * 24L;
                MemoryUtil.memPutFloat(pointer, random.nextFloat() * 32F - 16F);
                MemoryUtil.memPutFloat(pointer + 4, random.nextFloat() * 32F - 16F);
                MemoryUtil.memPutFloat(pointer + 8, random.nextFloat() * 32F - 16F);
                MemoryUtil.memPutInt(pointer + 12, random.nextInt());
                MemoryUtil.memPutLong(pointer + 16, random.nextLong());
            }
            return new CompactMesh(storage, new MeshData(storage.build(),
                    draw(COMPACT, quads * 4, VertexFormat.Mode.QUADS, VertexFormat.IndexType.least(quads * 4))));
        } catch (RuntimeException | Error failure) {
            storage.close();
            throw failure;
        }
    }

    private record CompactMesh(ByteBufferBuilder storage, MeshData mesh) implements AutoCloseable {
        @Override public void close() {
            try { mesh.close(); }
            finally { storage.close(); }
        }
    }

    private static byte[] reference(MeshData.SortState state, VertexSorting sorter) {
        try (var storage = new ByteBufferBuilder(64); var result = state.buildSortedIndexBuffer(storage, sorter)) {
            return bytes(result);
        }
    }

    private static byte[] bytes(ByteBufferBuilder.Result result) {
        return result == null ? new byte[0] : bytes(result.byteBuffer());
    }

    private static byte[] bytes(ByteBuffer source) {
        var result = new byte[source.remaining()];
        source.duplicate().get(result);
        return result;
    }

    private static byte[] indices(VertexFormat.IndexType type, int... quads) {
        var result = ByteBuffer.allocate(quads.length * 6 * type.bytes).order(ByteOrder.nativeOrder());
        for (int quad : quads) for (int corner : new int[]{0, 1, 2, 2, 3, 0}) {
            if (type == VertexFormat.IndexType.SHORT) result.putShort((short) (quad * 4 + corner));
            else result.putInt(quad * 4 + corner);
        }
        return result.array();
    }

    private static int[] range(int count) {
        int[] result = new int[count];
        Arrays.setAll(result, index -> index);
        return result;
    }

    private static void assertCentroids(MeshData.SortState expected, MeshData.SortState actual) {
        assertNotNull(expected);
        assertSame(expected.indexType(), actual.indexType());
        assertEquals(expected.centroids().length, actual.centroids().length);
        for (int index = 0; index < actual.centroids().length; index++) {
            var a = expected.centroids()[index];
            var b = actual.centroids()[index];
            assertEquals(Float.floatToRawIntBits(a.x), Float.floatToRawIntBits(b.x));
            assertEquals(Float.floatToRawIntBits(a.y), Float.floatToRawIntBits(b.y));
            assertEquals(Float.floatToRawIntBits(a.z), Float.floatToRawIntBits(b.z));
        }
    }

    private static final class GuardedBuilder extends ByteBufferBuilder {
        private final Thread caller = Thread.currentThread();
        int calls;
        GuardedBuilder() { super(64); }
        @Override public long reserve(int bytes) {
            assertSame(caller, Thread.currentThread(), "Live shared builder used by a worker");
            calls++;
            return super.reserve(bytes);
        }
        @Override public Result build() {
            assertSame(caller, Thread.currentThread(), "Live shared builder built by a worker");
            calls++;
            return super.build();
        }
    }

    private static final class Gate {
        final CountDownLatch started = new CountDownLatch(1), release = new CountDownLatch(1);
        void block() {
            started.countDown();
            try { assertTrue(release.await(10, TimeUnit.SECONDS), "Test worker was never released"); }
            catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new AssertionError(interrupted);
            }
        }
    }
}
