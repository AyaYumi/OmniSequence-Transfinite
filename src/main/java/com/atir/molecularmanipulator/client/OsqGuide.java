package com.atir.molecularmanipulator.client;

import com.atir.molecularmanipulator.MolecularManipulator;
import guideme.PageAnchor;
import guideme.internal.GuideMEClient;
import guideme.internal.GuideRegistry;

/** Opens a machine guide without changing its menu settings. */
final class OsqGuide {
    private OsqGuide() {}

    static void open(String path) {
        var page=MolecularManipulator.id("items-blocks-machines/"+path+".md");
        GuideRegistry.getAll().stream().filter(guide->guide.pageExists(page)).findFirst()
                .ifPresent(guide->GuideMEClient.openGuideAtAnchor(guide,PageAnchor.page(page)));
    }
}
