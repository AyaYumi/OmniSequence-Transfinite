package com.atir.molecularmanipulator.client;

import com.atir.molecularmanipulator.MolecularManipulator;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.utils.XmlUtils;

final class LdUiXml {
    private LdUiXml() {
    }

    static UI load(String path) {
        var location = MolecularManipulator.id(path);
        var document = XmlUtils.loadXml(location);
        if (document == null) {
            throw new IllegalStateException("Unable to load LDLib2 UI XML: " + location);
        }
        return UI.of(document);
    }

    static <T> T require(UI ui, String id, Class<T> type) {
        return ui.selectId(id, type)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Missing LDLib2 UI element #" + id + " of type " + type.getSimpleName()));
    }
}
