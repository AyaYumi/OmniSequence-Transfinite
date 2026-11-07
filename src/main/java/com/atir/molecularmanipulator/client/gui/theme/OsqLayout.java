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
import java.util.Map;
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
        public String type, sprite, id, kind, text, lang, fontId, color, item, ownerId, aeIcon;
        public String tabPanel, tabTextColor;
        public Entry tabSurface, tabSingleSurface;
        public float x, y, w, h, baseline, size, letterSpacing, opacity = 1f;
        public boolean external, dynamic, langNew, wrap, requiresSelection;
        public Float centerY, drawX, drawY;
        public String alignment = "left";
        public JsonArray langArgs;
        public int[] pad = {0, 0, 0, 0};
        private String spriteSource, colorSource, componentText, componentLang;
        private ResourceLocation spriteId;
        private int parsedColor;
        private JsonArray componentArgs;
        private Component component;

        private ResourceLocation spriteId() {
            if (!java.util.Objects.equals(spriteSource, sprite)) {
                spriteId = ResourceLocation.parse(sprite); spriteSource = sprite;
            }
            return spriteId;
        }

        private int color() {
            if (!java.util.Objects.equals(colorSource, color)) {
                parsedColor = argb(color); colorSource = color;
            }
            return parsedColor;
        }

        /** Reused translatable components follow Language changes through vanilla's own cache. */
        public Component staticText() {
            if (component == null || !java.util.Objects.equals(componentText, text)
                    || !java.util.Objects.equals(componentLang, lang) || !java.util.Objects.equals(componentArgs, langArgs)) {
                Object[] args = langArgs == null ? new Object[0]
                        : langArgs.asList().stream().map(JsonElement::getAsString).toArray();
                component = lang == null ? Component.literal(text) : Component.translatable(lang, args);
                componentText = text; componentLang = lang;
                componentArgs = langArgs == null ? null : langArgs.deepCopy();
            }
            return component;
        }
    }
    public final String screen, family;
    public final boolean english;
    public final int width, height, leftRoom;
    public final List<Entry> draw = new ArrayList<>();

    private OsqLayout(JsonObject o) {
        screen = o.get("screen").getAsString(); family = o.get("family").getAsString();
        JsonArray s = o.getAsJsonArray("size"); width = s.get(0).getAsInt(); height = s.get(1).getAsInt();
        leftRoom = o.get("left_room").getAsInt();
        var client = Minecraft.getInstance();
        english = client != null && client.options != null && client.options.languageCode.startsWith("en_");
        for (JsonElement el : o.getAsJsonArray("draw")) {
            JsonObject d = el.getAsJsonObject(); Entry e = new Entry();
            e.type = d.get("type").getAsString();
            if (d.has("sprite") && !d.get("sprite").isJsonNull()) e.sprite = d.get("sprite").getAsString();
            if (d.has("id")) e.id = d.get("id").getAsString();
            if (d.has("owner")) e.ownerId = d.get("owner").getAsString();
            if (d.has("ae_icon")) e.aeIcon = d.get("ae_icon").getAsString();
            if (d.has("tab_panel")) e.tabPanel = d.get("tab_panel").getAsString();
            if (d.has("tab_text_color")) e.tabTextColor = d.get("tab_text_color").getAsString();
            if (d.has("tab_surface")) e.tabSurface = tabSurface(d.getAsJsonObject("tab_surface"));
            if (d.has("tab_single_surface")) e.tabSingleSurface = tabSurface(d.getAsJsonObject("tab_single_surface"));
            if (d.has("draw_x")) e.drawX = d.get("draw_x").getAsFloat();
            if (d.has("draw_y")) e.drawY = d.get("draw_y").getAsFloat();
            if (english && d.has("draw_x_en")) e.drawX = d.get("draw_x_en").getAsFloat();
            if (d.has("k")) e.kind = d.get("k").getAsString();
            if (d.has("rect")) { JsonArray r = d.getAsJsonArray("rect"); e.x = r.get(0).getAsFloat(); e.y = r.get(1).getAsFloat(); e.w = r.get(2).getAsFloat(); e.h = r.get(3).getAsFloat(); }
            if (d.has("pad")) for (int i = 0; i < 4; i++) e.pad[i] = d.getAsJsonArray("pad").get(i).getAsInt();
            e.requiresSelection = d.has("requires_selection") && d.get("requires_selection").getAsBoolean();
            e.external = d.has("external") && d.get("external").getAsBoolean();
            if ("text".equals(e.type)) {
                e.text = d.get("text").getAsString(); e.x = d.get("x").getAsFloat(); e.baseline = d.get("baseline").getAsFloat();
                e.w = d.get(english && d.has("width_en") ? "width_en" : "width").getAsFloat();
                e.size = d.get("size").getAsFloat(); e.fontId = d.get("font_id").getAsString();
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
            if ("ctl".equals(e.kind) || "edge".equals(e.kind)) e.pad = new int[4];
            draw.add(e);
        }
    }

    private static Entry tabSurface(JsonObject json) {
        var entry = new Entry();
        entry.type = "sprite";
        entry.sprite = json.get("sprite").getAsString();
        var rect = json.getAsJsonArray("rect");
        entry.x = rect.get(0).getAsFloat(); entry.y = rect.get(1).getAsFloat();
        entry.w = rect.get(2).getAsFloat(); entry.h = rect.get(3).getAsFloat();
        return entry;
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
        render(g, font, left, top, filter, Map.of());
    }

    /** Replace connected parent surfaces at their original source-order position. */
    public void render(GuiGraphics g, Font font, int left, int top, Predicate<Entry> filter,
            Map<Entry, Entry> surfaces) {
        try (var batch = OsqSpriteRenderer.batch()) {
        for (Entry e : draw) {
            if (e.external || !filter.test(e)) continue;
            if (!"sprite".equals(e.type) && !"holo".equals(e.type)) batch.flush();
            switch (e.type) {
                case "sprite", "holo" -> { if (e.sprite != null) drawSprite(g, surfaces.getOrDefault(e, e), left, top); }
                case "fill" -> g.fill(Math.round(left + e.x), Math.round(top + e.y), Math.round(left + e.x + e.w), Math.round(top + e.y + e.h), e.color());
                case "text" -> {
                    Component c = e.staticText();
                    ResourceLocation fid = OsqFonts.id(e.fontId);
                    int col = OsqPalette.withAlpha(e.color(), e.opacity);
                    // Only reserved paragraphs may wrap. A longer translation must
                    // not turn a single-row title/caption into neighboring rows.
                    if (e.wrap) {
                        renderText(g, font, e, c, left, top);
                    } else {
                        drawFittedText(g, font, c, fid, e, left, top, col);
                    }
                }
                default -> { }
            }
        }
        }
    }

    /** Use the loaded layout's padding, including padding overridden by a resource pack. */
    public static void drawSprite(GuiGraphics g, Entry e, int left, int top) {
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
        g.pose().pushPose();
        float x = left + (e.drawX == null ? e.x : e.drawX) - e.pad[0];
        float y = top + (e.drawY == null ? e.y : e.drawY) - e.pad[1];
        g.pose().translate(x, y, 0);
        int width = Math.round(e.w) + e.pad[0] + e.pad[2];
        int height = Math.round(e.h) + e.pad[1] + e.pad[3];
        if (width > 0 && height > 0) {
            g.pose().scale((e.w + e.pad[0] + e.pad[2]) / width,
                    (e.h + e.pad[1] + e.pad[3]) / height, 1);
            OsqSpriteRenderer.draw(g, e.spriteId(), 0, 0, width, height);
        }
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

    /** Floating content belongs to the last containing popup/tooltip before it. */
    public Entry layerOf(Entry entry) {
        // Runtime-only controls are base-layer controls, not content of a later tooltip.
        if (!draw.contains(entry)) return null;
        float x = "text".equals(entry.type) ? entry.x : entry.x + entry.w / 2;
        float y = "text".equals(entry.type) ? entry.baseline : entry.y + entry.h / 2;
        Entry layer = null;
        for (var panel : draw) {
            if (panel == entry) break;
            if (("pop".equals(panel.kind) || "tip".equals(panel.kind))
                    && x >= panel.x && x < panel.x + panel.w && y >= panel.y && y < panel.y + panel.h) layer = panel;
        }
        return layer;
    }

    /** Geometry alone cannot associate a popup's content with controls below it. */
    public boolean owns(Entry control, Entry entry) {
        if (entry == control) return true;
        int index = draw.indexOf(control);
        if (index >= 0 && draw.indexOf(entry) <= index || layerOf(control) != layerOf(entry)) return false;
        if (entry.ownerId != null) return entry.ownerId.equals(control.id);
        // Binding coordinates can be inside a different control after reflow.
        // Only an explicit owner may claim content that is now drawn elsewhere.
        float x = entry.drawX == null ? entry.x : entry.drawX;
        float y = "text".equals(entry.type) ? entry.centerY == null ? entry.baseline : entry.centerY
                : entry.drawY == null ? entry.y : entry.drawY;
        if ("text".equals(entry.type)) return x >= control.x && x < control.x + control.w
                && y >= control.y && y <= control.y + control.h;
        return "sprite".equals(entry.type) && (sameRect(entry, control)
                || entry.sprite != null && entry.sprite.contains("/icons/") && x >= control.x && y >= control.y
                && x + entry.w <= control.x + control.w + 1 && y + entry.h <= control.y + control.h + 1);
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
        int col = OsqPalette.withAlpha(e.color(), e.opacity);
        if (e.wrap && e.w > 0) {
            var lines = OsqFonts.wrap(font, value.getString(), fid, e.w + 0.5f);
            var row = new Entry();
            row.x = e.x; row.drawX = e.drawX; row.w = e.w; row.size = e.size; row.alignment = e.alignment;
            for (int i = 0; i < lines.size(); i++) {
                row.baseline = e.baseline + i * e.size * 1.45f;
                row.centerY = e.centerY == null ? null : e.centerY + (i - (lines.size() - 1) / 2f) * e.size * 1.45f;
                drawFittedText(g, font, Component.literal(lines.get(i)), fid, row, left, top, col);
            }
        } else {
            drawFittedText(g, font, value, fid, e, left, top, col);
        }
    }

    public void renderSingleLine(GuiGraphics g, Font font, Entry e, Component value, int left, int top) {
        if (e.wrap) { renderText(g, font, e, value, left, top); return; }
        drawFittedText(g, font, value, OsqFonts.id(e.fontId), e, left, top,
                OsqPalette.withAlpha(e.color(), e.opacity));
    }

    private static void drawFittedText(GuiGraphics g, Font font, Component text, ResourceLocation fontId,
            Entry entry, int left, int top, int color) {
        String plain = text.getString();
        float width = textWidth(font, plain, fontId, entry.letterSpacing);
        float minimumScale = entry.size > 7.5f ? 7.5f / entry.size : 1;
        if (entry.w > 0 && width * minimumScale > entry.w + .5f) {
            // Long live names must not turn the readable body font back into
            // microscopic text. Callers retain full values in native tooltips.
            String suffix = "…";
            float available = entry.w / minimumScale;
            int low = 0, high = plain.codePointCount(0, plain.length());
            while (low < high) {
                int middle = (low + high + 1) / 2;
                String candidate = plain.substring(0, plain.offsetByCodePoints(0, middle)) + suffix;
                if (textWidth(font, candidate, fontId, entry.letterSpacing) <= available) low = middle;
                else high = middle - 1;
            }
            plain = textWidth(font, suffix, fontId, entry.letterSpacing) > available ? ""
                    : plain.substring(0, plain.offsetByCodePoints(0, low)) + suffix;
            text = Component.literal(plain);
            width = textWidth(font, plain, fontId, entry.letterSpacing);
        }
        float scale = entry.w > 0 && width > entry.w + .5f ? Math.max(minimumScale, entry.w / width) : 1.0f;
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

    private static float textWidth(Font font, String text, ResourceLocation face, float tracking) {
        return Math.max(0, OsqFonts.widthF(font, text, face)
                + Math.max(0, text.codePointCount(0, text.length()) - 1) * tracking);
    }

    /** Align the measured live label, not the design's example string. */
    public static float textX(Entry entry, float renderedWidth) {
        float remaining = Math.max(0, entry.w - renderedWidth);
        return (entry.drawX == null ? entry.x : entry.drawX) + switch (entry.alignment) {
            case "center" -> remaining / 2;
            case "right" -> remaining;
            default -> 0;
        };
    }
}
