package com.atir.molecularmanipulator.client.render;

import com.atir.molecularmanipulator.MolecularManipulator;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.List;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.lwjgl.system.MemoryStack;

/** Optional native submission through the original consumer, preserving its render layer. */
final class SodiumVertexBatch {
    private static final Api API = Api.load();
    private static final MethodHandle MARK_SPRITE_ACTIVE = spriteActivation();

    private SodiumVertexBatch() {}

    static boolean available() { return API != null; }

    /** Cached uploads bypass putBulkData's sprite tracking; false keeps the ordinary draw path. */
    static boolean markSpritesActive(List<TextureAtlasSprite> sprites) {
        if (MARK_SPRITE_ACTIVE == null) return false;
        try {
            for (var sprite : sprites) MARK_SPRITE_ACTIVE.invokeExact(sprite);
            return true;
        } catch (Throwable failure) {
            throw new IllegalStateException("Cannot activate cached model sprites", failure);
        }
    }

    static Writer of(VertexConsumer target) {
        // A wrapper may change attributes. Only a direct builder also lets us complete
        // an immediate vertex before Iris marks the appended batch as complete.
        if (API == null || !(target instanceof BufferBuilder builder)) return null;
        try {
            var format = (VertexFormat) API.format.invokeExact(builder);
            Object writer = (Object) API.writer.invokeExact(target, format);
            return writer == null ? null : new Writer(builder, writer, format);
        } catch (Throwable failure) {
            throw new IllegalStateException("Cannot obtain native vertex writer", failure);
        }
    }

    record Writer(BufferBuilder builder, Object target, VertexFormat format) {
        void push(MemoryStack stack, long address, int vertices) {
            try {
                // Iris finishes the preceding quad when the following vertex starts.
                // Its Sodium push hook then suppresses completion of this batch once.
                API.finishVertex.invokeExact(builder);
                API.push.invokeExact(target, stack, address, vertices, format);
            } catch (Throwable failure) {
                // A failed append may already have written vertices; never replay it.
                throw new IllegalStateException("Cannot append native vertex batch", failure);
            }
        }
    }

    private static MethodHandle spriteActivation() {
        try {
            var type = Class.forName("net.caffeinemc.mods.sodium.api.texture.SpriteUtil");
            return MethodHandles.lookup().unreflect(type.getMethod("markSpriteActive", TextureAtlasSprite.class))
                    .bindTo(type.getField("INSTANCE").get(null));
        } catch (ClassNotFoundException absent) {
            return null;
        } catch (ReflectiveOperationException incompatible) {
            MolecularManipulator.LOGGER.warn("Sodium sprite activation is unavailable; animated mainframes will use ordinary vertices", incompatible);
            return null;
        }
    }

    private record Api(MethodHandle writer, MethodHandle push, MethodHandle finishVertex, MethodHandle format) {
        static Api load() {
            try {
                var type = Class.forName("net.caffeinemc.mods.sodium.api.vertex.buffer.VertexBufferWriter");
                var lookup = MethodHandles.lookup();
                var finish = BufferBuilder.class.getDeclaredMethod("endLastVertex");
                finish.setAccessible(true);
                var format = BufferBuilder.class.getDeclaredField("format");
                format.setAccessible(true);
                return new Api(
                        lookup.unreflect(type.getMethod("tryOf", VertexConsumer.class, VertexFormat.class))
                                .asType(MethodType.methodType(Object.class, VertexConsumer.class, VertexFormat.class)),
                        lookup.unreflect(type.getMethod("push", MemoryStack.class, long.class, int.class, VertexFormat.class))
                                .asType(MethodType.methodType(void.class, Object.class, MemoryStack.class, long.class, int.class, VertexFormat.class)),
                        lookup.unreflect(finish), lookup.unreflectGetter(format));
            } catch (ClassNotFoundException absent) {
                return null;
            } catch (ReflectiveOperationException incompatible) {
                MolecularManipulator.LOGGER.warn("Sodium native vertex submission is unavailable; using ordinary vertices", incompatible);
                return null;
            }
        }
    }
}
