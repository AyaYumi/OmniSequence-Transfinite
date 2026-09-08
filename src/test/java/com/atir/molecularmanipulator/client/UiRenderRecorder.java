package com.atir.molecularmanipulator.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix4f;

/** Executes real widget code without a Minecraft client, window or OpenGL context. */
public final class UiRenderRecorder extends GuiGraphics {
    static {
        System.setProperty("java.awt.headless", "true");
        try {
            // JUnit has no Forge event transformer. Supply the listener lists
            // normally created by that transformer before registry bootstrap.
            var lookup = net.minecraftforge.eventbus.api.EventListenerHelper.class
                    .getDeclaredMethod("getListenerListInternal", Class.class, boolean.class);
            lookup.setAccessible(true);
            initializeEvent(lookup, net.minecraftforge.network.NetworkEvent.class);
            for (var nested : net.minecraftforge.network.NetworkEvent.class.getDeclaredClasses()) {
                if (net.minecraftforge.eventbus.api.Event.class.isAssignableFrom(nested)) initializeEvent(lookup, nested);
            }
            net.minecraft.SharedConstants.tryDetectVersion();
            net.minecraft.server.Bootstrap.bootStrap();
        } catch (ReflectiveOperationException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private static void initializeEvent(java.lang.reflect.Method lookup, Class<?> event) throws ReflectiveOperationException {
        var parent = event.getSuperclass();
        if (parent != net.minecraftforge.eventbus.api.Event.class) initializeEvent(lookup, parent);
        lookup.invoke(null, event, true);
    }
    private static void ensureBootstrapped() { }
    public record Text(String value, int x, int y, int color, boolean shadow, Matrix4f transform) { }
    public record Rect(int left, int top, int right, int bottom, int color, boolean selection, Matrix4f transform) { }
    public record Item(net.minecraft.world.item.ItemStack stack, int x, int y, Matrix4f transform) { }
    public final List<Text> text = new ArrayList<>();
    public final List<Rect> rects = new ArrayList<>();
    public final List<Item> items = new ArrayList<>();
    public int clipDepth;
    public int clips;

    public UiRenderRecorder() { super(null, null); }

    @Override public void renderItem(net.minecraft.world.item.ItemStack stack, int x, int y) {
        items.add(new Item(stack, x, y, new Matrix4f(pose().last().pose())));
    }

    @Override public int drawString(Font font, String value, int x, int y, int color, boolean shadow) {
        if (value == null) return 0;
        text.add(new Text(value, x, y, color, shadow, new Matrix4f(pose().last().pose())));
        return x + font.width(value) + (shadow ? 1 : 0);
    }

    @Override public int drawString(Font font, FormattedCharSequence value, int x, int y, int color, boolean shadow) {
        int[] resolved = {color};
        value.accept((index, style, codePoint) -> {
            if (style.getColor() != null) resolved[0] = color & 0xFF000000 | style.getColor().getValue();
            return false;
        });
        return drawString(font, string(value), x, y, resolved[0], shadow);
    }

    @Override public int drawString(Font font, Component value, int x, int y, int color, boolean shadow) {
        return drawString(font, value.getVisualOrderText(), x, y, color, shadow);
    }

    @Override public void fill(int left, int top, int right, int bottom, int color) {
        rects.add(new Rect(left, top, right, bottom, color, false, new Matrix4f(pose().last().pose())));
    }

    @Override public void fill(RenderType type, int left, int top, int right, int bottom, int color) {
        rects.add(new Rect(left, top, right, bottom, color, true, new Matrix4f(pose().last().pose())));
    }

    @Override public void enableScissor(int left, int top, int right, int bottom) { clipDepth++; clips++; }
    @Override public void disableScissor() { clipDepth--; }

    public static String string(FormattedCharSequence sequence) {
        var result = new StringBuilder();
        sequence.accept((index, style, codePoint) -> { result.appendCodePoint(codePoint); return true; });
        return result.toString();
    }

    /** Deterministic glyph advances; tests verify drawing contracts, not a replacement game font. */
    public static final class MetricsFont extends Font {
        public MetricsFont() { super(id -> null, false); ensureBootstrapped(); }
        private static int advance(int codePoint) { return codePoint >= 0x2E80 ? 9 : 6; }
        @Override public int width(String value) { return value.codePoints().map(MetricsFont::advance).sum(); }
        @Override public int width(FormattedText value) { return width(value.getString()); }
        @Override public int width(FormattedCharSequence value) { return width(string(value)); }
        @Override public String plainSubstrByWidth(String value, int width) { return plainSubstrByWidth(value, width, false); }
        @Override public String plainSubstrByWidth(String value, int width, boolean reverse) {
            int length = 0, used = 0;
            if (reverse) {
                int start = value.length();
                while (start > 0) {
                    int cp = value.codePointBefore(start);
                    if (used + advance(cp) > width) break;
                    used += advance(cp); start -= Character.charCount(cp);
                }
                return value.substring(start);
            }
            while (length < value.length()) {
                int cp = value.codePointAt(length);
                if (used + advance(cp) > width) break;
                used += advance(cp); length += Character.charCount(cp);
            }
            return value.substring(0, length);
        }
    }
}
