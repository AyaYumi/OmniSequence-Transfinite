package com.atir.molecularmanipulator.client.render;

import com.atir.molecularmanipulator.MolecularManipulator;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.ClientHooks;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/** Prepares previously visible machines earlier in the same main-view frame. Never draws early. */
@EventBusSubscriber(modid = MolecularManipulator.MOD_ID, value = Dist.CLIENT)
public final class EarlyFrameGeometry {
    public interface Snapshot extends AutoCloseable {
        @Override void close();
    }

    public interface Source<T extends BlockEntity, S extends Snapshot> {
        S capture(T machine, float partialTick, PoseStack base, ParallelEffectGeometry geometry);
        default void release(ParallelEffectGeometry geometry) {}
    }

    private static final MethodHandle SHADOW = shadowProbe();
    private static final ArrayList<Entry<?, ?>> ENTRIES = new ArrayList<>();
    private static boolean inFrame;
    private EarlyFrameGeometry() {}

    /** Called before the renderer mutates its incoming block-to-camera pose. */
    @SuppressWarnings("unchecked")
    public static <T extends BlockEntity, S extends Snapshot> S take(
            Source<T, S> source, T machine, float partialTick, PoseStack base) {
        if (shadowPass() || machine.getLevel() != Minecraft.getInstance().level) return null;
        for (var raw : ENTRIES) {
            if (raw.machine.get() == machine && raw.source == source) {
                var entry = (Entry<T, S>) raw;
                return entry.pending.take(inFrame, partialTick, base);
            }
        }
        ENTRIES.add(new Entry<>(source, machine));
        return null; // The first actual draw discovers a candidate for the next frame.
    }

    @SubscribeEvent
    public static void stage(RenderLevelStageEvent event) {
        if (shadowPass()) return;
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_SKY) {
            finish();
            inFrame = true;
            var client = Minecraft.getInstance();
            var iterator = ENTRIES.iterator();
            while (iterator.hasNext()) {
                var entry = iterator.next();
                var machine = entry.machine.get();
                if (machine == null || machine.isRemoved() || machine.getLevel() != client.level
                        || client.getBlockEntityRenderDispatcher().getRenderer(machine) != entry.source) {
                    entry.close();
                    iterator.remove();
                    continue;
                }
                if (!ParallelEffectGeometry.isAvailable()) continue;
                var renderer = client.getBlockEntityRenderDispatcher().getRenderer(machine);
                if (!machine.getType().isValid(machine.getBlockState())
                        || !renderer.shouldRender(machine, event.getCamera().getPosition())
                        || !ClientHooks.isBlockEntityRendererVisible(client.getBlockEntityRenderDispatcher(), machine, event.getFrustum())) continue;
                entry.prepare(event);
            }
        } else if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            finish();
        }
    }

    /** Pending output is claimed once, and only by the matching view/pose/partial tick. */
    static final class Pending<S extends Snapshot> {
        private S snapshot;
        private PoseStack.Pose base;
        private float partialTick;

        void set(S snapshot, float partialTick, PoseStack base) {
            close();
            this.snapshot = snapshot;
            this.partialTick = partialTick;
            this.base = base.last().copy();
        }

        S take(boolean inFrame, float partialTick, PoseStack current) {
            if (snapshot == null) return null;
            if (!inFrame || Float.floatToIntBits(this.partialTick) != Float.floatToIntBits(partialTick)
                    || !base.pose().equals(current.last().pose()) || !base.normal().equals(current.last().normal())) {
                close();
                return null;
            }
            var result = snapshot;
            snapshot = null;
            base = null;
            return result;
        }

        void close() {
            var value = snapshot;
            snapshot = null;
            base = null;
            if (value != null) value.close();
        }
    }

    private static final class Entry<T extends BlockEntity, S extends Snapshot> implements AutoCloseable {
        final Source<T, S> source;
        final WeakReference<T> machine;
        final ParallelEffectGeometry geometry = new ParallelEffectGeometry();
        final Pending<S> pending = new Pending<>();

        Entry(Source<T, S> source, T machine) {
            this.source = source;
            this.machine = new WeakReference<>(machine);
        }

        void prepare(RenderLevelStageEvent event) {
            var value = machine.get();
            if (value == null) return;
            var eye = event.getCamera().getPosition();
            var pos = value.getBlockPos();
            // Both vanilla and Sodium start BER with a fresh stack and this translation.
            // The event model-view matrix is applied separately by the world renderer.
            var base = new PoseStack();
            base.translate(pos.getX() - eye.x, pos.getY() - eye.y, pos.getZ() - eye.z);
            float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
            pending.set(source.capture(value, partialTick, base, geometry), partialTick, base);
        }

        @Override public void close() {
            try { pending.close(); }
            finally {
                try { source.release(geometry); }
                finally { geometry.close(); }
            }
        }
    }

    private static void finish() {
        inFrame = false;
        Throwable failure = null;
        for (var entry : ENTRIES) {
            try { entry.pending.close(); }
            catch (RuntimeException | Error error) {
                if (failure == null) failure = error;
                else if (error != failure) failure.addSuppressed(error);
            }
        }
        rethrow(failure);
    }

    public static void clear() {
        if (!RenderSystem.isOnRenderThread()) { RenderSystem.recordRenderCall(EarlyFrameGeometry::clear); return; }
        inFrame = false;
        Throwable failure = null;
        for (var entry : ENTRIES) {
            try { entry.close(); }
            catch (RuntimeException | Error error) {
                if (failure == null) failure = error;
                else if (error != failure) failure.addSuppressed(error);
            }
        }
        ENTRIES.clear();
        rethrow(failure);
    }

    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { clear(); }

    private static void rethrow(Throwable failure) {
        if (failure instanceof RuntimeException error) throw error;
        if (failure instanceof Error error) throw error;
    }

    private static MethodHandle shadowProbe() {
        try {
            return MethodHandles.publicLookup().unreflect(Class.forName("net.irisshaders.iris.shadows.ShadowRenderingState")
                    .getMethod("areShadowsCurrentlyBeingRendered"));
        } catch (ClassNotFoundException absent) { return null; }
        catch (ReflectiveOperationException failure) { throw new IllegalStateException("Cannot inspect Iris render view", failure); }
    }

    private static boolean shadowPass() {
        if (SHADOW == null) return false;
        try { return (boolean) SHADOW.invokeExact(); }
        catch (Throwable failure) { throw new IllegalStateException("Cannot inspect Iris render view", failure); }
    }
}
