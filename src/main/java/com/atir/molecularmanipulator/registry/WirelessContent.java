package com.atir.molecularmanipulator.registry;

import com.atir.molecularmanipulator.block.SequenceWirelessHubBlock;
import com.atir.molecularmanipulator.blockentity.SequenceWirelessHubBlockEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.RegistryObject;

public final class WirelessContent {
    /** AE2 15 stores exported settings in the memory card's Data compound. */
    public static final String HUB_BINDING = "molecularmanipulator:sequence_wireless_binding";
    public static final RegistryObject<SequenceWirelessHubBlock> HUB =
            ModContent.BLOCKS.register("sequence_wireless_hub", SequenceWirelessHubBlock::new);
    public static final RegistryObject<BlockItem> HUB_ITEM = ModContent.ITEMS.register("sequence_wireless_hub",
            () -> new BlockItem(HUB.get(), new Item.Properties()));
    public static final RegistryObject<BlockEntityType<SequenceWirelessHubBlockEntity>> HUB_BE =
            ModContent.BLOCK_ENTITIES.register("sequence_wireless_hub", () -> BlockEntityType.Builder.of(
                    SequenceWirelessHubBlockEntity::new, HUB.get()).build(null));

    private WirelessContent() {}
    public static void init() {}

    public static void bind() {
        HUB.get().setBlockEntity(SequenceWirelessHubBlockEntity.class, HUB_BE.get(), null, null);
    }
}
