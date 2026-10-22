package com.atir.molecularmanipulator.client.gui.theme;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;

/**
 * 界面字体。assets/molecularmanipulator/font/osq_&lt;字体&gt;_&lt;字重&gt;_&lt;字号&gt;.json，每个定义 = 主字体 ttf + 思源黑体（同字重）回退 + minecraft:default。
 * 布局 JSON 的 text 项给出 font_id / x / baseline（GUI px）/ color（#AARRGGBB）/ letter_spacing（px）/ opacity。
 * 共 45 个定义。由 tools/finish.py 生成。
 */
public final class OsqFonts {
    private OsqFonts() {}

    /** Vanilla TTF providers have a seven-pixel baseline regardless of provider size. */
    public static float baselineFromTop(ResourceLocation fontId) { return 7; }
    @Deprecated public static float baselineFromTop(float size) { return 7; }

    private static boolean usesModernText(Font font) {
        // Check the active splitter, not merely the installed mod: users may disable its text engine.
        return font.getSplitter().getClass().getName().equals("icyllis.modernui.mc.text.ModernStringSplitter");
    }

    private static float renderingScale(Font font, ResourceLocation fontId) {
        if (!usesModernText(font) || !fontId.getNamespace().equals("molecularmanipulator")) return 1;
        float baseSize = ModernMetrics.baseSize();
        return OsqFontMetrics.scale(OsqFontMetrics.providerSize(fontId.getPath(), baseSize), baseSize);
    }

    /** Optional public Modern UI metrics; no compile/runtime dependency is added to the mod. */
    private static final class ModernMetrics {
        private static final java.lang.reflect.Field SIZE = field("TextLayoutProcessor", "sBaseFontSize");
        private static final java.lang.reflect.Field BASELINE = field("TextLayout", "sBaselineOffset");

        private static java.lang.reflect.Field field(String owner, String name) {
            try {
                return Class.forName("icyllis.modernui.mc.text." + owner, false,
                        OsqFonts.class.getClassLoader()).getField(name);
            } catch (ReflectiveOperationException | LinkageError ex) {
                com.mojang.logging.LogUtils.getLogger().warn("OSQ cannot read Modern UI metric {}.{}; using default", owner, name, ex);
                return null;
            }
        }

        private static float read(java.lang.reflect.Field field, float fallback) {
            if (field == null) return fallback;
            try { return field.getFloat(null); }
            catch (IllegalAccessException ex) { return fallback; }
        }

        static float baseSize() {
            float value = read(SIZE, 8);
            return Float.isFinite(value) && value > 0 ? value : 8;
        }
        static float baseline() { return OsqFontMetrics.baseline(read(BASELINE, 7)); }
    }

    private static final java.util.Map<ResourceLocation, java.util.List<java.awt.Font>> INK_FACES = new java.util.HashMap<>();
    private static final java.util.Map<ResourceLocation, java.awt.Font> INK_FILES = new java.util.HashMap<>();
    private static final java.util.Map<String, Float> INK_OFFSETS = new java.util.LinkedHashMap<>(512, .75f, true) {
        @Override protected boolean removeEldestEntry(java.util.Map.Entry<String, Float> entry) { return size() > 512; }
    };

    public static void clearInkMetrics() { OsqSlotGhosts.clear(); INK_FACES.clear(); INK_FILES.clear(); INK_OFFSETS.clear(); }

    public static float centeredBaselineOffset(Component text, ResourceLocation fontId, float size) {
        String key = fontId + ":" + size + ":" + text.getString();
        return INK_OFFSETS.computeIfAbsent(key, unused -> OsqFontInk.centeredBaselineOffset(
                INK_FACES.computeIfAbsent(fontId, OsqFonts::loadInkFaces), text.getString(), size));
    }

    private static java.util.List<java.awt.Font> loadInkFaces(ResourceLocation fontId) {
        var result = new java.util.ArrayList<java.awt.Font>();
        var resources = net.minecraft.client.Minecraft.getInstance().getResourceManager();
        try (var reader = resources.openAsReader(ResourceLocation.fromNamespaceAndPath(fontId.getNamespace(), "font/" + fontId.getPath() + ".json"))) {
            var definition = com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
            for (var item : definition.getAsJsonArray("providers")) {
                var provider = item.getAsJsonObject();
                if (!provider.get("type").getAsString().endsWith("ttf")) continue;
                var file = ResourceLocation.parse(provider.get("file").getAsString()).withPrefix("font/");
                var face = INK_FILES.get(file);
                if (face == null) {
                    try (var stream = resources.open(file)) { face = java.awt.Font.createFont(java.awt.Font.TRUETYPE_FONT, stream); }
                    INK_FILES.put(file, face);
                }
                result.add(face);
            }
        } catch (Exception ex) {
            com.mojang.logging.LogUtils.getLogger().warn("OSQ cannot measure font {}; using proportional centering", fontId, ex);
        }
        return java.util.List.copyOf(result);
    }

    public static ResourceLocation id(String fontId) {
        int i = fontId.indexOf(':');
        return i < 0 ? ResourceLocation.fromNamespaceAndPath("molecularmanipulator", fontId) : ResourceLocation.parse(fontId);
    }

    public static Component styled(Component text, ResourceLocation font) {
        return text.copy().withStyle(Style.EMPTY.withFont(font));
    }

    /** 画一段文字：x = 左边，baseline = 基线（GUI px，可为小数），letterSpacing 非 0 时逐字绘制。 */
    public static void draw(GuiGraphics g, Font font, Component text, ResourceLocation fontId, float size,
                            float x, float baseline, int argb, float letterSpacing) {
        float scale = renderingScale(font, fontId);
        float nativeBaseline = usesModernText(font) ? ModernMetrics.baseline() : baselineFromTop(fontId);
        g.pose().pushPose();
        g.pose().translate(x, baseline - nativeBaseline * scale, 0);
        g.pose().scale(scale, scale, 1);
        if (Math.abs(letterSpacing) < 0.05f) {
            g.drawString(font, styled(text, fontId), 0, 0, argb, false);
        } else {
            String s = text.getString(); float cx = 0;
            for (int i = 0; i < s.length(); ) {
                int cp = s.codePointAt(i); String ch = new String(Character.toChars(cp)); i += Character.charCount(cp);
                Component c = styled(Component.literal(ch), fontId);
                g.pose().pushPose(); g.pose().translate(cx, 0, 0); g.drawString(font, c, 0, 0, argb, false); g.pose().popPose();
                cx += font.getSplitter().stringWidth(c) + letterSpacing / scale; // 用浮点字宽：font.width() 逐字取整会累积误差
            }
        }
        g.pose().popPose();
    }

    public static int width(Font font, Component text, ResourceLocation fontId) {
        return (int) Math.ceil(font.getSplitter().stringWidth(styled(text, fontId)) * renderingScale(font, fontId));
    }
    /** 浮点字宽（GUI px）。 */
    public static float widthF(Font font, String text, ResourceLocation fontId) { return font.getSplitter().stringWidth(styled(Component.literal(text), fontId)) * renderingScale(font, fontId); }

    /** 按 maxWidth 逐字折行（中文没有空格，原版 splitLines 只在空格处断，会把“旧版”单独留一行）；遇到空格优先在空格处断。 */
    public static java.util.List<String> wrap(Font font, String text, ResourceLocation fontId, float maxWidth) {
        java.util.List<String> out = new java.util.ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i); i += Character.charCount(cp);
            String next = line.toString() + new String(Character.toChars(cp));
            if (line.length() > 0 && widthF(font, next, fontId) > maxWidth) {
                int sp = line.lastIndexOf(" ");
                boolean latin = cp < 0x2E80 && cp != ' ';
                if (latin && sp > 0 && sp > line.length() - 24) {   // 不拆开英文单词 / 数字
                    out.add(line.substring(0, sp)); line = new StringBuilder(line.substring(sp + 1));
                } else { out.add(line.toString()); line = new StringBuilder(); }
                if (cp == ' ') continue;
            }
            line.appendCodePoint(cp);
        }
        if (line.length() > 0) out.add(line.toString());
        return out;
    }
}
