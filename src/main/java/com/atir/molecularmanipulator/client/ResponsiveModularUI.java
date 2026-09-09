package com.atir.molecularmanipulator.client;

import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import java.util.List;
import net.minecraft.client.renderer.Rect2i;

/** LDLib's cached layout rectangles stay logical; recipe viewers receive a converted copy. */
final class ResponsiveModularUI extends ModularUI {
    ResponsiveModularUI(UI ui) { super(ui); }

    @Override
    public List<Rect2i> getGuiExtraAreas() {
        var areas = super.getGuiExtraAreas();
        return getScreen() instanceof ResponsiveContainerScreen<?> screen
                ? areas.stream().map(screen::responsiveArea).toList() : areas;
    }
}
