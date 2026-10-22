package com.atir.molecularmanipulator.client.gui.theme;

import com.atir.molecularmanipulator.MolecularManipulator;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.neoforge.event.AddPackFindersEvent;

/** Optional, visible pack embedded in the mod; light stays the default. */
public final class OsqResourcePacks {
    public static final String DARK_PATH = "resourcepacks/osq_dark";
    public static final String DARK_ID = "mod/molecularmanipulator:" + DARK_PATH;

    private OsqResourcePacks() {}

    public static void register(AddPackFindersEvent event) {
        event.addPackFinders(MolecularManipulator.id(DARK_PATH), PackType.CLIENT_RESOURCES,
                Component.translatable("resourcePack.molecularmanipulator.osq_dark"),
                PackSource.BUILT_IN, false, Pack.Position.TOP);
    }
}
