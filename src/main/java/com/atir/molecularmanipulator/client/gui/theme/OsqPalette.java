package com.atir.molecularmanipulator.client.gui.theme;

/**
 * 直角风格配色（ARGB），亮色与暗色两套。常量 = 家族 + 主题：SEQ / OMNI / SING / FAB 为亮色，*_DARK 为暗色。
 * 家族：seq = 构序阵列 / 构序操纵器 / 构序自动合成器；omni = 万物演算核心；sing = 视界奇点天枢（含单方块）；fab = 物质构筑井（含端口、样板总成）。
 * 角色：paper 窗口 · card 卡片 · raise 按钮/页签/小键 · sunk 输入框/按下 · feat 重点卡 · blk 计数块 · inv/invT 反色（危险键、选中页签、提示框）
 * · accB 主色按钮底 · disT 禁用文字 · dis 禁用底。
 */
public enum OsqPalette {
    SEQ("seq", false),
    OMNI("omni", false),
    SING("sing", false),
    FAB("fab", false),
    SEQ_DARK("seq", true, 0xFF1B1518, 0xFF251E22, 0xFF31282D, 0xFF141013, 0xFF3D2632, 0xFFF4EAEE, 0xFFD3C2C9, 0xFFA8939C, 0xFF6F5F66, 0xFF8D7A82, 0xFF382E33, 0xFFE57DA0, 0xFFB83D66, 0xFF3A2430, 0xFFDCC08A, 0xFF352C20, 0xFFF08257, 0xFF5CC996, 0xFF43393E, 0xFFA8355E, 0xFFF1E4E9, 0xFF1B1518, 0xFF211A1E),
    OMNI_DARK("omni", true, 0xFF14121C, 0xFF1E1B29, 0xFF2A2638, 0xFF0F0D16, 0xFF2B2350, 0xFFEEEBF7, 0xFFC7C2DA, 0xFF9C97B4, 0xFF615C78, 0xFF7F7A98, 0xFF2E2A3D, 0xFFA795FF, 0xFF6A52E6, 0xFF2A2448, 0xFFD6B677, 0xFF2F2A1F, 0xFFF08257, 0xFF5CC996, 0xFF3A3550, 0xFF5236CF, 0xFFECE9F7, 0xFF14121C, 0xFF1C1926),
    SING_DARK("sing", true, 0xFF12161D, 0xFF1A2029, 0xFF252D39, 0xFF0D1117, 0xFF1D2D45, 0xFFE9EFF6, 0xFFC2CDDA, 0xFF95A3B4, 0xFF5B6778, 0xFF7D8999, 0xFF2A3340, 0xFF9FC6F2, 0xFF3367C2, 0xFF1E2D42, 0xFFDABB72, 0xFF2E2919, 0xFFF08257, 0xFF5CC996, 0xFF333E4F, 0xFF2F5DB5, 0xFFE6EEF7, 0xFF12161D, 0xFF171C24),
    FAB_DARK("fab", true, 0xFF18150F, 0xFF221E16, 0xFF2E2920, 0xFF110E09, 0xFF382C17, 0xFFF3ECDD, 0xFFD3C8B2, 0xFFA79B84, 0xFF6B6252, 0xFF8E8470, 0xFF352F24, 0xFFE3BA5C, 0xFF8A6313, 0xFF33290F, 0xFFD2B26B, 0xFF2F2714, 0xFFF08257, 0xFF5CC996, 0xFF433C2F, 0xFF7A570F, 0xFFF2EBDC, 0xFF18150F, 0xFF1D1912);

    public final String id;
    public final boolean dark;
    public final int paper, card, raise, sunk, feat, ink, ink2, ink3, ink4, disT, hair, acc, accB, accT, gold, goldT, warn, ok, slot, blk, inv, invT, dis;

    /** The approved light glass uses one gray-purple palette across families. */
    OsqPalette(String id, boolean dark) {
        this(id, dark, 0xFFCBCCD4, 0xFFCBCCD4, 0xFF9A9FB4, 0xFFB3B7C7, 0xFF4D4D67,
                0xFF2E2C40, 0xFF4D4D67, 0xFF5D637D, 0xFF9A9FB4, 0xFF413F54, 0xFF9A9FB4,
                0xFF7CA0D2, 0xFF696D88, 0xFFE0E5F2, 0xFFB9A478, 0xFFDFD8C8,
                0xFFCE654F, 0xFF65AD91, 0xFFADB0C4, 0xFF4D4D67, 0xFFF2F2F2, 0xFFF2F2F2, 0xFF878FA5);
    }

    OsqPalette(String id, boolean dark, int paper, int card, int raise, int sunk, int feat, int ink, int ink2, int ink3, int ink4, int disT, int hair, int acc, int accB, int accT, int gold, int goldT, int warn, int ok, int slot, int blk, int inv, int invT, int dis) {
        this.id = id; this.dark = dark;
        this.paper = paper;
        this.card = card;
        this.raise = raise;
        this.sunk = sunk;
        this.feat = feat;
        this.ink = ink;
        this.ink2 = ink2;
        this.ink3 = ink3;
        this.ink4 = ink4;
        this.disT = disT;
        this.hair = hair;
        this.acc = acc;
        this.accB = accB;
        this.accT = accT;
        this.gold = gold;
        this.goldT = goldT;
        this.warn = warn;
        this.ok = ok;
        this.slot = slot;
        this.blk = blk;
        this.inv = inv;
        this.invT = invT;
        this.dis = dis;
    }

    /** 贴图根目录：osq_light（亮）或 osq_dark（暗）。 */
    public String root() { return dark ? "osq_dark" : "osq_light"; }
    /** 同家族切换主题。 */
    public OsqPalette withTheme(boolean dark) {
        for (OsqPalette p : values()) if (p.id.equals(id) && p.dark == dark) return p;
        return this;
    }
    public static OsqPalette of(String family, boolean dark) {
        for (OsqPalette p : values()) if (p.id.equals(family) && p.dark == dark) return p;
        throw new IllegalArgumentException(family);
    }
    /** Light AE2 controls use pale labels, blue hover text and dark disabled text. */
    public int controlText(String kind, String status, boolean enabled) {
        if (!enabled || status.equals("disabled")) return disT;
        if (!dark) return status.equals("hover") ? 0xFF517497 : 0xFFF2F2F2;
        if (status.equals("danger") || (kind.equals("tab") || kind.equals("row")) && status.equals("active")) return invT;
        return status.equals("active") || status.equals("confirm") ? 0xFFFFFFFF : ink;
    }

    public static int withAlpha(int argb, float alpha) {
        return (Math.round(((argb >>> 24) & 0xFF) * alpha) << 24) | (argb & 0xFFFFFF);
    }
}
