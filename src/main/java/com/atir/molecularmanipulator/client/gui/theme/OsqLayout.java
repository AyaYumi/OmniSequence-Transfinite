package com.atir.molecularmanipulator.client.gui.theme;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * 读取 assets/molecularmanipulator/osq_light/layout/&lt;screen&gt;.json 并按 z 顺序画“静态层”：窗口 / 卡片 / 槽位 / 控件底图 / 图标 / 全息线稿 / 固定文案。
 * 动态内容（数字、状态文字、物品、按钮实际状态）由界面代码按 id 画；布局里的演示值只用于对位。
 * 外部模组按键（external=true）不画。用法：
 *   static final OsqLayout L = OsqLayout.load(ResourceLocation.fromNamespaceAndPath("molecularmanipulator", "osq_light/layout/sing_overview.json"));
 *   L.render(g, font, leftPos, topPos, e -> !e.dynamic);
 */
public final class OsqLayout {
    public static final class Entry {
        public String type, sprite, id, kind, text, lang, fontId, color, item;
        public float x, y, w, h, baseline, size, letterSpacing, opacity = 1f;
        public boolean external, dynamic, langNew, wrap, requiresSelection;
        public Float centerY;
        public String alignment = "left";
        public JsonArray langArgs;
        public int[] pad = {0, 0, 0, 0};
    }
    public final String screen, family;
    public final int width, height, leftRoom;
    public final List<Entry> draw = new ArrayList<>();

    private OsqLayout(JsonObject o) {
        screen = o.get("screen").getAsString(); family = o.get("family").getAsString();
        JsonArray s = o.getAsJsonArray("size"); width = s.get(0).getAsInt(); height = s.get(1).getAsInt();
        leftRoom = o.get("left_room").getAsInt();
        for (JsonElement el : o.getAsJsonArray("draw")) {
            JsonObject d = el.getAsJsonObject(); Entry e = new Entry();
            e.type = d.get("type").getAsString();
            if (d.has("sprite") && !d.get("sprite").isJsonNull()) e.sprite = d.get("sprite").getAsString();
            if (d.has("id")) e.id = d.get("id").getAsString();
            if (d.has("k")) e.kind = d.get("k").getAsString();
            if (d.has("rect")) { JsonArray r = d.getAsJsonArray("rect"); e.x = r.get(0).getAsFloat(); e.y = r.get(1).getAsFloat(); e.w = r.get(2).getAsFloat(); e.h = r.get(3).getAsFloat(); }
            if (d.has("pad")) for (int i = 0; i < 4; i++) e.pad[i] = d.getAsJsonArray("pad").get(i).getAsInt();
            e.requiresSelection = d.has("requires_selection") && d.get("requires_selection").getAsBoolean();
            e.external = d.has("external") && d.get("external").getAsBoolean();
            if ("text".equals(e.type)) {
                e.text = d.get("text").getAsString(); e.x = d.get("x").getAsFloat(); e.baseline = d.get("baseline").getAsFloat();
                e.w = d.get("width").getAsFloat(); e.size = d.get("size").getAsFloat(); e.fontId = d.get("font_id").getAsString();
                e.color = d.get("color").getAsString(); e.letterSpacing = d.get("letter_spacing").getAsFloat(); e.opacity = d.get("opacity").getAsFloat();
                e.dynamic = d.get("dynamic").getAsBoolean();
                if (d.has("center_y")) e.centerY = d.get("center_y").getAsFloat();
                if (d.has("align")) e.alignment = d.get("align").getAsString();
                e.wrap = d.has("wrap") && d.get("wrap").getAsBoolean();
                if (d.has("lang")) e.lang = d.get("lang").getAsString();
                if (d.has("lang_args")) e.langArgs = d.getAsJsonArray("lang_args");
                e.langNew = d.has("lang_new");
            }
            if ("item".equals(e.type)) { e.item = d.get("item").getAsString(); e.x = d.get("x").getAsFloat(); e.y = d.get("y").getAsFloat(); }
            if ("fill".equals(e.type)) e.color = d.get("color").getAsString();
            draw.add(e);
        }
    }

    public static OsqLayout load(ResourceLocation file) {
        try (Reader r = Minecraft.getInstance().getResourceManager().openAsReader(file)) {
            return new OsqLayout(JsonParser.parseReader(r).getAsJsonObject());
        } catch (Exception ex) {
            throw new IllegalStateException("osq gui layout " + file, ex);
        }
    }

    /** Runtime IDs stay stable. The optional dark resource pack overrides osq_light. */
    public static OsqLayout forTheme(String screen, boolean dark) {
        return load(ResourceLocation.fromNamespaceAndPath("molecularmanipulator", "osq_light/layout/" + screen + ".json"));
    }

    public static int argb(String hex) { return (int) Long.parseLong(hex.substring(1), 16); }

    /** 画静态层；filter 决定画哪些项（例如 e -> !e.dynamic && !"ctl".equals(e.kind) 只画底板）。 */
    public void render(GuiGraphics g, Font font, int left, int top, Predicate<Entry> filter) {
        for (Entry e : draw) {
            if (e.external || !filter.test(e)) continue;
            switch (e.type) {
                case "sprite", "holo" -> { if (e.sprite != null) drawSprite(g, e, left, top); }
                case "fill" -> g.fill(Math.round(left + e.x), Math.round(top + e.y), Math.round(left + e.x + e.w), Math.round(top + e.y + e.h), argb(e.color));
                case "text" -> {
                    Object[] args = e.langArgs == null
                            ? new Object[0]
                            : e.langArgs.asList().stream().map(JsonElement::getAsString).toArray();
                    Component c = e.lang == null
                            ? Component.literal(e.text)
                            : Component.translatable(e.lang, args);
                    ResourceLocation fid = OsqFonts.id(e.fontId);
                    int col = OsqPalette.withAlpha(argb(e.color), e.opacity);
                    // 设计稿里多行文字（提示框等）在布局里是一项、width = 框宽：按 width 逐字折行，行高 1.45×字号
                    if (e.w > 0 && Math.abs(e.letterSpacing) < 0.05f && (e.wrap || OsqFonts.widthF(font, c.getString(), fid) > e.w * 1.2f)) { // 只折真正的多行文字；单行文字因字形度量差 1~2 px 不折
                        renderText(g, font, e, c, left, top);
                    } else {
                        drawFittedText(g, font, c, fid, e, left, top, col);
                    }
                }
                default -> { }
            }
        }
    }

    /** Use the loaded layout's padding, including padding overridden by a resource pack. */
    public static void drawSprite(GuiGraphics g, Entry e, int left, int top) {
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
        g.pose().pushPose();
        float x = left + e.x - e.pad[0], y = top + e.y - e.pad[1];
        g.pose().translate(x, y, 0);
        OsqSpriteRenderer.draw(g, ResourceLocation.parse(e.sprite), 0, 0,
                Math.round(e.w) + e.pad[0] + e.pad[2], Math.round(e.h) + e.pad[1] + e.pad[3]);
        g.pose().popPose();
    }

    /** 按 data-id 找控件矩形（按钮 / 槽位 / 卡片），给界面代码做点击区域与动态绘制。 */
    public Entry find(String id) {
        for (Entry e : draw) if (id.equals(e.id)) return e;
        return null;
    }

    /** IDs can be shared by a panel and a button (notably research "order"). */
    public Entry findControl(String id) {
        return draw.stream().filter(e -> id.equals(e.id)
                && ("ctl".equals(e.kind) || "edge".equals(e.kind))).findFirst()
                .orElseThrow(() -> new IllegalArgumentException(screen + ": no control " + id));
    }

    /** Generated controls must replace their source background, not paint it twice. */
    public static boolean sameRect(Entry first, Entry second) {
        return !"text".equals(first.type) && !"item".equals(first.type)
                && Math.abs(first.x-second.x)<.8f && Math.abs(first.y-second.y)<.8f
                && Math.abs(first.w-second.w)<.8f && Math.abs(first.h-second.h)<.8f;
    }

    public Entry findText(String lang) {
        for (Entry e : draw) if ("text".equals(e.type) && lang.equals(e.lang)) return e;
        return null;
    }

    public List<Entry> dynamicTexts() {
        return draw.stream().filter(e -> "text".equals(e.type) && e.dynamic).toList();
    }

    public void renderText(GuiGraphics g, Font font, Entry e, Component value, int left, int top) {
        ResourceLocation fid = OsqFonts.id(e.fontId);
        int col = OsqPalette.withAlpha(argb(e.color), e.opacity);
        if (e.w > 0 && Math.abs(e.letterSpacing) < 0.05f
                && (e.wrap || OsqFonts.widthF(font, value.getString(), fid) > e.w * 1.2f)) {
            var lines = OsqFonts.wrap(font, value.getString(), fid, e.w + 0.5f);
            float baseline = top + (e.centerY == null ? e.baseline : e.centerY
                    - (lines.size() - 1) * e.size * 1.45f / 2
                    + OsqFonts.centeredBaselineOffset(value, fid, e.size));
            for (String line : lines) {
                OsqFonts.draw(g, font, Component.literal(line), fid, e.size,
                        left + textX(e, OsqFonts.widthF(font, line, fid)), baseline, col, 0f);
                baseline += e.size * 1.45f;
            }
        } else {
            drawFittedText(g, font, value, fid, e, left, top, col);
        }
    }

    public void renderSingleLine(GuiGraphics g, Font font, Entry e, Component value, int left, int top) {
        if (e.wrap) { renderText(g, font, e, value, left, top); return; }
        drawFittedText(g, font, value, OsqFonts.id(e.fontId), e, left, top,
                OsqPalette.withAlpha(argb(e.color), e.opacity));
    }

    private static void drawFittedText(GuiGraphics g, Font font, Component text, ResourceLocation fontId,
            Entry entry, int left, int top, int color) {
        float width = OsqFonts.widthF(font, text.getString(), fontId);
        int characters = text.getString().codePointCount(0, text.getString().length());
        width = Math.max(0, width + Math.max(0, characters - 1) * entry.letterSpacing);
        float scale = entry.w > 0 && width > entry.w ? entry.w / width : 1.0f;
        float x = textX(entry, width * scale);
        float baseline = entry.centerY == null ? entry.baseline
                : entry.centerY + OsqFonts.centeredBaselineOffset(text, fontId, entry.size) * scale;
        if (scale < 1.0f) {
            g.pose().pushPose();
            g.pose().translate(left + x, top + baseline, 0);
            g.pose().scale(scale, scale, 1);
            OsqFonts.draw(g, font, text, fontId, entry.size, 0, 0, color, entry.letterSpacing);
            g.pose().popPose();
        } else {
            OsqFonts.draw(g, font, text, fontId, entry.size,
                    left + x, top + baseline, color, entry.letterSpacing);
        }
    }

    /** Align the measured live label, not the design's example string. */
    public static float textX(Entry entry, float renderedWidth) {
        float remaining = Math.max(0, entry.w - renderedWidth);
        return entry.x + switch (entry.alignment) {
            case "center" -> remaining / 2;
            case "right" -> remaining;
            default -> 0;
        };
    }
}
