package com.atir.molecularmanipulator.registry;

import com.atir.molecularmanipulator.MolecularManipulator;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.MissingMappingsEvent;

/** Removed ports have no usable replacement and are cleared when old worlds load. */
@Mod.EventBusSubscriber(modid = MolecularManipulator.MOD_ID)
public final class RetiredContentMappings {
    private RetiredContentMappings() { }
    @SubscribeEvent public static void remap(MissingMappingsEvent event) {
        for (var mapping : event.getMappings(Registries.BLOCK, MolecularManipulator.MOD_ID)) {
            if (mapping.getKey().getPath().equals("resource_confluence_port")) mapping.remap(Blocks.AIR);
        }
        for (var mapping : event.getMappings(Registries.ITEM, MolecularManipulator.MOD_ID)) {
            if (mapping.getKey().getPath().equals("resource_confluence_port")) mapping.remap(Items.AIR);
        }
    }
}
