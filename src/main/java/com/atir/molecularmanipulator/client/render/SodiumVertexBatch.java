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

/** Optional Embeddium/Xenon native submission through the caller's original consumer. */
final class SodiumVertexBatch {
    private static final Api API = Api.load();
    private static final MethodHandle MARK_SPRITE_ACTIVE = spriteActivation();

    private SodiumVertexBatch() {}

    static boolean available() { return API != null; }

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
        if (API == null || !(target instanceof BufferBuilder builder)) return null;
        try {
            var format = ((ForgeRenderSupport.BuilderAccess) builder).omni$vertexFormat();
            Object writer = (Object) API.writer.invokeExact(target);
            Object description = (Object) API.description.invokeExact(format);
            return writer == null ? null : new Writer(writer, format, description);
        } catch (Throwable failure) {
            throw new IllegalStateException("Cannot obtain native vertex writer", failure);
        }
    }

    record Writer(Object target, VertexFormat format, Object description) {
        void push(MemoryStack stack, long address, int vertices) {
            try {
                // 1.20.1 callers end each immediate vertex explicitly. The native writer's
                // Oculus hook accounts for complete quads appended by this batch.
                API.push.invokeExact(target, stack, address, vertices, description);
            } catch (Throwable failure) {
                throw new IllegalStateException("Cannot append native vertex batch", failure);
            }
        }
    }

    private static MethodHandle spriteActivation() {
        try {
            return MethodHandles.lookup().unreflect(Class.forName("me.jellysquid.mods.sodium.client.render.texture.SpriteUtil")
                    .getMethod("markSpriteActive", TextureAtlasSprite.class));
        } catch (ClassNotFoundException absent) { return null; }
        catch (ReflectiveOperationException incompatible) {
            MolecularManipulator.LOGGER.warn("Embeddium sprite activation is unavailable; retaining ordinary model submission", incompatible);
            return null;
        }
    }

    private record Api(MethodHandle writer, MethodHandle push, MethodHandle description) {
        static Api load() {
            try {
                var type = Class.forName("net.caffeinemc.mods.sodium.api.vertex.buffer.VertexBufferWriter");
                var description = Class.forName("net.caffeinemc.mods.sodium.api.vertex.format.VertexFormatDescription");
                var registry = Class.forName("net.caffeinemc.mods.sodium.api.vertex.format.VertexFormatRegistry");
                var lookup = MethodHandles.lookup();
                return new Api(
                        lookup.unreflect(type.getMethod("tryOf", VertexConsumer.class))
                                .asType(MethodType.methodType(Object.class, VertexConsumer.class)),
                        lookup.unreflect(type.getMethod("push", MemoryStack.class, long.class, int.class, description))
                                .asType(MethodType.methodType(void.class, Object.class, MemoryStack.class, long.class, int.class, Object.class)),
                        lookup.unreflect(registry.getMethod("get", VertexFormat.class)).bindTo(registry.getField("INSTANCE").get(null))
                                .asType(MethodType.methodType(Object.class, VertexFormat.class)));
            } catch (ClassNotFoundException absent) { return null; }
            catch (ReflectiveOperationException incompatible) {
                MolecularManipulator.LOGGER.warn("Embeddium native vertex submission is unavailable; using ordinary vertices", incompatible);
                return null;
            }
        }
    }
}
