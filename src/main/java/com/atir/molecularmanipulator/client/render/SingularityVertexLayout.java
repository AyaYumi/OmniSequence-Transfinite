package com.atir.molecularmanipulator.client.render;

import com.atir.molecularmanipulator.MolecularManipulator;
import com.google.common.collect.ImmutableMap;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;

/** Layout and captured material state for the two entity formats used by effect layers. */
final class SingularityVertexLayout {
    static final VertexFormat COLOR_MATERIAL_FORMAT = new VertexFormat(ImmutableMap.of(
            "Position", DefaultVertexFormat.ELEMENT_POSITION, "Color", DefaultVertexFormat.ELEMENT_COLOR,
            "UV1", DefaultVertexFormat.ELEMENT_UV1, "UV2", DefaultVertexFormat.ELEMENT_UV2));
    private static final SingularityVertexLayout COLOR_MATERIAL = new SingularityVertexLayout(COLOR_MATERIAL_FORMAT, false);
    private static final SingularityVertexLayout COLOR = new SingularityVertexLayout(DefaultVertexFormat.POSITION_COLOR, false);
    private static final SingularityVertexLayout VANILLA = new SingularityVertexLayout(DefaultVertexFormat.NEW_ENTITY, false);
    final VertexFormat format;
    final int stride, position, color, uv, overlay, light, normal, entities, midUv, tangent;

    private SingularityVertexLayout(VertexFormat format, boolean extended) {
        this.format = format;
        stride = format.getVertexSize();
        position = ForgeRenderSupport.offset(format, DefaultVertexFormat.ELEMENT_POSITION);
        color = ForgeRenderSupport.offset(format, DefaultVertexFormat.ELEMENT_COLOR);
        uv = ForgeRenderSupport.offset(format, DefaultVertexFormat.ELEMENT_UV0);
        overlay = ForgeRenderSupport.offset(format, DefaultVertexFormat.ELEMENT_UV1);
        light = ForgeRenderSupport.offset(format, DefaultVertexFormat.ELEMENT_UV2);
        normal = ForgeRenderSupport.offset(format, DefaultVertexFormat.ELEMENT_NORMAL);
        entities = extended ? offset(format, "iris_Entity") : -1;
        midUv = extended ? offset(format, "mc_midTexCoord") : -1;
        tangent = extended ? offset(format, "at_tangent") : -1;
    }

    static SingularityVertexLayout of(VertexFormat format) {
        if (format == COLOR_MATERIAL_FORMAT) return COLOR_MATERIAL;
        if (format == DefaultVertexFormat.POSITION_COLOR) return COLOR;
        if (format == DefaultVertexFormat.NEW_ENTITY) return VANILLA;
        var iris = IrisHolder.STATE;
        return iris != null && format == iris.format ? iris.layout : null;
    }

    record State(SingularityVertexLayout layout, long entityIds, boolean recalculateNormals) {}

    /** Capture on the render thread; workers must never read mutable Iris rendering state. */
    static State captureLevel() {
        if (!SodiumVertexBatch.available() || !SingularityEffectLayers.shadersActive()) return null;
        var iris = IrisHolder.STATE;
        if (iris == null || !iris.layout.recalculateNormals()) return null;
        return new State(iris.layout, iris.layout.entityIds(), true);
    }

    long entityIds() {
        if (entities < 0) return 0;
        var iris = IrisHolder.STATE;
        try {
            int entity = (int) iris.entity.invokeExact();
            int blockEntity = (int) iris.blockEntity.invokeExact();
            int item = (int) iris.item.invokeExact();
            return (entity & 65535L) | (blockEntity & 65535L) << 16 | (item & 65535L) << 32;
        } catch (Throwable failure) {
            throw new IllegalStateException("Cannot capture Iris effect materials", failure);
        }
    }

    boolean recalculateNormals() {
        if (entities < 0) return false;
        try { return (boolean) IrisHolder.STATE.renderingLevel.invokeExact(); }
        catch (Throwable failure) { throw new IllegalStateException("Cannot read Iris rendering phase", failure); }
    }

    private static int offset(VertexFormat format, String name) {
        int offset = ForgeRenderSupport.offset(format, name);
        if (offset < 0) throw new IllegalStateException("Missing Oculus entity attribute " + name);
        return offset;
    }

    private static final class IrisHolder {
        static final IrisState STATE = IrisState.load();
    }

    private record IrisState(VertexFormat format, SingularityVertexLayout layout, MethodHandle entity,
            MethodHandle blockEntity, MethodHandle item, MethodHandle renderingLevel) {
        static IrisState load() {
            try {
                var format = (VertexFormat) Class.forName("net.irisshaders.iris.vertices.IrisVertexFormats")
                        .getField("ENTITY").get(null);
                var state = Class.forName("net.irisshaders.iris.uniforms.CapturedRenderingState");
                var instance = state.getField("INSTANCE").get(null);
                var immediate = Class.forName("net.irisshaders.iris.vertices.ImmediateState");
                var lookup = MethodHandles.lookup();
                return new IrisState(format, new SingularityVertexLayout(format, true),
                        lookup.unreflect(state.getMethod("getCurrentRenderedEntity")).bindTo(instance),
                        lookup.unreflect(state.getMethod("getCurrentRenderedBlockEntity")).bindTo(instance),
                        lookup.unreflect(state.getMethod("getCurrentRenderedItem")).bindTo(instance),
                        lookup.unreflectGetter(immediate.getField("isRenderingLevel")));
            } catch (ClassNotFoundException absent) {
                return null;
            } catch (ReflectiveOperationException incompatible) {
                MolecularManipulator.LOGGER.warn("Iris native effect attributes are unavailable; using ordinary vertices", incompatible);
                return null;
            }
        }
    }
}
