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
        var overviewTab = tabs.stream().filter(e -> layout.owns(e, overview))
                .findFirst().orElseThrow();
        float left = tabs.get(0).x;
        float right = tabs.get(tabs.size() - 1).x + tabs.get(tabs.size() - 1).w;
        layout.draw.removeIf(e -> e == overview || e == overviewTab || "collection_quantum_status".equals(e.id));
        var remaining = tabs.stream().filter(e -> e != overviewTab).toList();
        float width = (right - left) / remaining.size();
        for (int i = 0; i < remaining.size(); i++) {
            var tab = remaining.get(i);
            tab.x = left + i * width;
            tab.w = width;
            if (tab.tabSingleSurface != null) tab.tabSurface = tab.tabSingleSurface;
            var label = layout.findText(PREFIX + (i == 0 ? "collection" : "duplication"));
            label.x = tab.x + 5;
            label.drawX = label.x;
            label.w = tab.w - 10;
            label.centerY = tab.y + OsqTabs.textCenter(tab.h);
            label.alignment = "center";
        }
    }
}
