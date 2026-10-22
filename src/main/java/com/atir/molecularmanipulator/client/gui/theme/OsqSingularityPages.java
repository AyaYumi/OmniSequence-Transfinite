package com.atir.molecularmanipulator.client.gui.theme;

/** Single-block hubs have no structure overview or paired quantum-link controls. */
public final class OsqSingularityPages {
    private static final String PREFIX = "gui.molecularmanipulator.singularity.";
    private OsqSingularityPages() {}

    public static void configure(OsqLayout layout, boolean singleBlock) {
        if (!singleBlock) return;
        var tabs = layout.draw.stream().filter(e -> e.sprite != null && e.sprite.contains("/tab_")).toList();
        var overview = layout.findText(PREFIX + "overview");
        if (overview == null || tabs.size() != 3)
            throw new IllegalStateException("Expected three source singularity tabs: " + layout.screen);
        var overviewTab = tabs.stream().filter(e -> overview.x >= e.x && overview.x < e.x + e.w)
                .findFirst().orElseThrow();
        float left = tabs.getFirst().x;
        float right = tabs.getLast().x + tabs.getLast().w;
        layout.draw.removeIf(e -> e == overview || e == overviewTab || "collection_quantum_status".equals(e.id));
        var remaining = tabs.stream().filter(e -> e != overviewTab).toList();
        float width = (right - left) / remaining.size();
        for (int i = 0; i < remaining.size(); i++) {
            var tab = remaining.get(i);
            tab.x = left + i * width;
            tab.w = width;
            var label = layout.findText(PREFIX + (i == 0 ? "collection" : "duplication"));
            label.x = tab.x + 5;
            label.w = tab.w - 10;
            label.centerY = tab.y + tab.h / 2;
            label.alignment = "center";
        }
    }
}
