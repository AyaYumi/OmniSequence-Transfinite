package com.atir.molecularmanipulator.integration.jei;

import com.atir.molecularmanipulator.building.BuildingType;
import java.util.List;

/** Uses the same visible, unformed blueprint as package construction, including the real foundation. */
final class StarOrreryStructurePreviewWidget extends InteractiveStructurePreviewWidget {
    StarOrreryStructurePreviewWidget(BuildingType type) {
        this(type, StructureJeiLayout.previewHeight());
    }

    StarOrreryStructurePreviewWidget(BuildingType type, int height) {
        this(renderParts(type), height);
    }

    private StarOrreryStructurePreviewWidget(List<RenderPart> parts, int height) {
        // Keep every template member in both the overview and the individual layers.
        // In particular, negative foundation Y coordinates must not be flattened or omitted.
        super(parts, parts, minY(parts), maxY(parts) - minY(parts) + 1, height);
    }

    static List<RenderPart> renderParts(BuildingType type) {
        if (!type.isStarOrrery()) {
            throw new IllegalArgumentException("A star orrery building type is required");
        }
        return type.template().stream()
                .filter(entry -> !entry.state().isAir())
                .map(entry -> new RenderPart(entry.offset(), entry.state()))
                .toList();
    }

    private static int minY(List<RenderPart> parts) {
        return parts.stream().mapToInt(RenderPart::y).min().orElse(0);
    }

    private static int maxY(List<RenderPart> parts) {
        return parts.stream().mapToInt(RenderPart::y).max().orElse(0);
    }
}
