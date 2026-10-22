package com.atir.molecularmanipulator.client.gui.theme;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * 直角风格贴图，亮 / 暗两套，全部是原版 GUI 精灵（1.21.1 自动进 gui 图集）：
 *   亮：assets/molecularmanipulator/textures/gui/sprites/osq_light/&lt;family|common&gt;/&lt;name&gt;.png       id molecularmanipulator:osq_light/...
 *   暗：assets/molecularmanipulator/textures/gui/sprites/osq_dark/&lt;family|common&gt;/&lt;name&gt;.png  id molecularmanipulator:osq_dark/...
 * 两套贴图名完全相同，主题只换根目录：{@code resolve(OsqPalette.SING_DARK, "button_normal")}。
 * 4× 密度；九宫格写在 .png.mcmeta；阴影在控件矩形外，绘制矩形 = 布局矩形外扩 {@link #pad}。由 tools/gen_java.py 生成。
 */
public final class OsqSprites {
    private OsqSprites() {}
    public static final String NS = "molecularmanipulator";

    public static ResourceLocation id(OsqPalette p, String name) {
        return ResourceLocation.fromNamespaceAndPath(NS, p.root() + "/" + p.id + "/" + name);
    }
    public static ResourceLocation common(OsqPalette p, String name) {
        return ResourceLocation.fromNamespaceAndPath(NS, p.root() + "/common/" + name);
    }
    /** 先看是否放在 common/（各家族像素一致的贴图），否则用家族目录。 */
    public static ResourceLocation resolve(OsqPalette p, String name) {
        return (p.dark ? COMMON_DARK : COMMON_LIGHT).contains(name) ? common(p, name) : id(p, name);
    }
    static final java.util.Set<String> COMMON_LIGHT = java.util.Set.of("bar_fill_ok", "bar_fill_warn", "bar_track_block", "bar_track_dark", "button_confirm", "button_normal", "button_pressed", "card", "divider_dark", "dot_err", "dot_ok", "dot_warn", "edge_key", "field_error", "icons/lock_white_8", "icons/net_white_8", "icons/order_white_8", "icons/out_white_8", "icons/pause_white_8", "icons/shield_white_8", "icons/x_white_8", "key13_disabled", "key13_normal", "row_normal", "swatch_frame", "tab_normal");
    static final java.util.Set<String> COMMON_DARK = java.util.Set.of("bar_fill_ok", "bar_fill_warn", "bar_track_block", "bar_track_dark", "button_confirm", "dot_err", "dot_ok", "dot_warn", "icons/back_white_8", "icons/check_white_8", "icons/cog_white_8", "icons/down_white_8", "icons/lock_white_8", "icons/net_white_8", "icons/order_white_8", "icons/out_white_8", "icons/pause_white_8", "icons/play_white_8", "icons/right_white_8", "icons/shield_white_8", "icons/up_white_8", "icons/x_white_8", "swatch_frame");

    /** 外扩留白 [左, 上, 右, 下]（GUI px）；path = 精灵路径，如 "osq_dark/sing/card" */
    public static int[] pad(String path) {
        return switch (path) {
            case "osq_dark/fab/window", "osq_dark/omni/window", "osq_dark/seq/window", "osq_dark/sing/window" -> new int[]{0, 0, 3, 12};
            case "osq_light/fab/slot_selected", "osq_light/fab/window", "osq_light/omni/slot_selected", "osq_light/omni/window", "osq_light/seq/slot_selected", "osq_light/seq/window", "osq_light/sing/slot_selected", "osq_light/sing/window", "osq_dark/fab/slot_selected", "osq_dark/omni/slot_selected", "osq_dark/seq/slot_selected", "osq_dark/sing/slot_selected" -> new int[]{1, 1, 1, 1};
            case "osq_light/common/button_normal", "osq_light/common/card", "osq_light/common/edge_key", "osq_light/fab/button_hover", "osq_light/fab/card_dark", "osq_light/fab/edge_key_active", "osq_light/fab/popup", "osq_light/fab/tooltip", "osq_light/omni/button_hover", "osq_light/omni/card_dark", "osq_light/omni/edge_key_active", "osq_light/omni/popup", "osq_light/omni/tooltip", "osq_light/seq/button_hover", "osq_light/seq/card_dark", "osq_light/seq/edge_key_active", "osq_light/seq/popup", "osq_light/seq/tooltip", "osq_light/sing/button_hover", "osq_light/sing/card_dark", "osq_light/sing/edge_key_active", "osq_light/sing/popup", "osq_light/sing/tooltip", "osq_dark/fab/button_hover", "osq_dark/fab/button_normal", "osq_dark/fab/card", "osq_dark/fab/card_dark", "osq_dark/fab/edge_key", "osq_dark/fab/edge_key_active", "osq_dark/fab/popup", "osq_dark/fab/tooltip", "osq_dark/omni/button_hover", "osq_dark/omni/button_normal", "osq_dark/omni/card", "osq_dark/omni/card_dark", "osq_dark/omni/edge_key", "osq_dark/omni/edge_key_active", "osq_dark/omni/popup", "osq_dark/omni/tooltip", "osq_dark/seq/button_hover", "osq_dark/seq/button_normal", "osq_dark/seq/card", "osq_dark/seq/card_dark", "osq_dark/seq/edge_key", "osq_dark/seq/edge_key_active", "osq_dark/seq/popup", "osq_dark/seq/tooltip", "osq_dark/sing/button_hover", "osq_dark/sing/button_normal", "osq_dark/sing/card", "osq_dark/sing/card_dark", "osq_dark/sing/edge_key", "osq_dark/sing/edge_key_active", "osq_dark/sing/popup", "osq_dark/sing/tooltip" -> new int[]{5, 3, 5, 7};
            default -> new int[]{0, 0, 0, 0};
        };
    }

    public static void draw(GuiGraphics g, ResourceLocation sprite, int x, int y, int w, int h) {
        int[] p = pad(sprite.getPath());
        // 文字绘制会关掉混合；不开混合时阴影的低 alpha 像素会被画成实黑锯齿
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
        OsqSpriteRenderer.draw(g, sprite, x - p[0], y - p[1], w + p[0] + p[2], h + p[1] + p[3]);
    }
    public static void draw(GuiGraphics g, ResourceLocation sprite, float x, float y, float w, float h) {
        g.pose().pushPose();
        // 小数部分对齐到屏幕像素（GUI 缩放 3 时 0.5 GUI px = 1.5 屏幕 px，会采样到图集里相邻精灵的边）
        double sc = net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScale();
        g.pose().translate((float) (Math.round((x - (int) x) * sc) / sc), (float) (Math.round((y - (int) y) * sc) / sc), 0);
        draw(g, sprite, (int) x, (int) y, Math.round(w), Math.round(h));
        g.pose().popPose();
    }
    public static void draw(GuiGraphics g, OsqPalette p, String name, float x, float y, float w, float h) {
        draw(g, resolve(p, name), x, y, w, h);
    }

    /** CONFIRM = 二次点击确认（拆卸 / 回收：第一次点击后 3 秒内再点）。暗色里 DANGER 是浅底深字。 */
    public enum Button { NORMAL("button_normal"), HOVER("button_hover"), PRESSED("button_pressed"), DISABLED("button_disabled"),
        ACTIVE("button_active"), DANGER("button_danger"), CONFIRM("button_confirm");
        public final String sprite; Button(String s) { sprite = s; } }
    public enum Key13 { NORMAL("key13_normal"), HOVER("key13_hover"), ACTIVE("key13_active"), INK("key13_ink"), DISABLED("key13_disabled");
        public final String sprite; Key13(String s) { sprite = s; } }
    public enum Field { NORMAL("field_normal"), FOCUS("field_focus"), ERROR("field_error");
        public final String sprite; Field(String s) { sprite = s; } }
    public enum Slot { NORMAL("slot"), GHOST("slot_ghost"), SELECTED("slot_selected"), TANK("tank");
        public final String sprite; Slot(String s) { sprite = s; } }
    public static String toggle(boolean on) { return on ? "toggle_on" : "toggle_off"; }
    public static String tab(boolean on) { return on ? "tab_active" : "tab_normal"; }
    public static String row(boolean on) { return on ? "row_active" : "row_normal"; }
    public static String edgeKey(boolean on) { return on ? "edge_key_active" : "edge_key"; }
    public static String dot(String kind) { return "dot_" + kind; }
    /** 图标 icons/&lt;名&gt;_&lt;颜色&gt;_&lt;尺寸&gt;。暗色里画在反色底（危险键 / 小键 ink / 选中行）上的图标颜色名为 inv。 */
    public static String icon(String name, String color, int size) { return "icons/" + name + "_" + color + "_" + size; }

    public static void bar(GuiGraphics g, OsqPalette p, String track, String fill, float x, float y, float w, float h, float v) {
        draw(g, resolve(p, track), x, y, w, h);
        if (v > 0) draw(g, resolve(p, fill), x, y, w * Math.min(1f, v), h);
    }
}
