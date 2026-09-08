package com.atir.molecularmanipulator.client;

import java.util.List;
import net.minecraft.client.gui.GuiGraphics;

/** Shared, allocation-free screen geometry transcribed from the 1.21.1 branch. */
public enum MachineUiLayout {
    MOLECULAR_ARRAY("molecular_manipulator", 194, 274,
            List.of(panel(8, 35, 186, 130), panel(8, 134, 186, 170), panel(8, 176, 186, 267)),
            List.of(grid("ENCODED_PATTERN", 15, 51, 9, 4), grid("STORAGE", 15, 144, 9, 1),
                    inventory(15, 189), hotbar(15, 247))),
    MOLECULAR_CENTER("molecular_center", 430, 286,
            List.of(area(4, 28, 192, 153), area(4, 157, 192, 282)),
            List.of(grid("ENCODED_PATTERN", 16, 51, 9, 4), inventory(16, 187), hotbar(16, 245))),
    OMNI_COMPUTATION("omni_computation", 332, 364,
            List.of(area(10, 28, 154, 190), area(162, 28, 322, 190),
                    insetArea(10, 194, 322, 226), area(75, 260, 257, 360)),
            List.of(grid("MACHINE_INPUT", 292, 200, 1, 1), inventory(84, 281), hotbar(84, 339))),
    FABRICATION_STATUS("matter_fabrication", 332, 368,
            List.of(panel(12, 44, 160, 142), panel(168, 44, 320, 142), panel(12, 150, 320, 238)),
            List.of(inventory(84, 281), hotbar(84, 339))),
    FABRICATION_RESEARCH("matter_fabrication", 332, 368,
            List.of(panel(12, 44, 120, 264), panel(128, 44, 320, 264)),
            List.of(inventory(84, 281), hotbar(84, 339))),
    FABRICATION_PORT("matter_fabrication_port", 248, 284,
            List.of(panel(12, 44, 112, 178), panel(122, 44, 236, 178)),
            List.of(inventory(43, 197), hotbar(43, 255))),
    PATTERN_ASSEMBLY("matter_fabrication_pattern_assembly", 248, 325,
            List.of(panel(12, 42, 236, 77), panel(12, 84, 236, 218)),
            List.of(inventory(43, 237), hotbar(43, 295)));

    public final String styleFile;
    public final int width, height;
    public final List<Region> regions;
    public final List<SlotGrid> slots;

    MachineUiLayout(String styleFile, int width, int height, List<Region> regions, List<SlotGrid> slots) {
        this.styleFile = styleFile;
        this.width = width;
        this.height = height;
        this.regions = regions;
        this.slots = slots;
    }

    /** The owning container draws the outer frame and dynamic content separately. */
    public void draw(GuiGraphics graphics, int x, int y) {
        for (int i = 0; i < regions.size(); i++) regions.get(i).draw(graphics, x, y);
        for (int i = 0; i < slots.size(); i++) {
            var slot = slots.get(i);
            OmniUiTheme.slotGrid(graphics, x + slot.x, y + slot.y, slot.columns, slot.rows);
        }
    }

    public record Region(int left, int top, int right, int bottom, boolean inset, boolean lightBorder) {
        void draw(GuiGraphics graphics, int x, int y) {
            if (inset) {
                if (lightBorder) OmniUiTheme.insetArea(graphics, x + left, y + top, x + right, y + bottom);
                else OmniUiTheme.insetPanel(graphics, x + left, y + top, x + right, y + bottom);
            } else if (lightBorder) OmniUiTheme.area(graphics, x + left, y + top, x + right, y + bottom);
            else OmniUiTheme.panel(graphics, x + left, y + top, x + right, y + bottom);
        }
    }

    /** x/y describe the 18px frame; the AE2 item position is one pixel inside. */
    public record SlotGrid(String semantic, int x, int y, int columns, int rows) { }

    private static Region panel(int l, int t, int r, int b) { return new Region(l, t, r, b, false, false); }
    private static Region area(int l, int t, int r, int b) { return new Region(l, t, r, b, false, true); }
    private static Region insetArea(int l, int t, int r, int b) { return new Region(l, t, r, b, true, true); }
    private static SlotGrid grid(String semantic, int x, int y, int columns, int rows) {
        return new SlotGrid(semantic, x, y, columns, rows);
    }
    private static SlotGrid inventory(int x, int y) { return grid("PLAYER_INVENTORY", x, y, 9, 3); }
    private static SlotGrid hotbar(int x, int y) { return grid("PLAYER_HOTBAR", x, y, 9, 1); }
}
