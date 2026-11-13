package com.atir.molecularmanipulator.registry;

import com.atir.molecularmanipulator.MolecularManipulator;
import com.atir.molecularmanipulator.block.SequenceWirelessHubBlock;
import com.atir.molecularmanipulator.blockentity.SequenceWirelessHubBlockEntity;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class WirelessContent {
    private static final DeferredRegister<DataComponentType<?>> COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, MolecularManipulator.MOD_ID);
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<CompoundTag>> HUB_BINDING =
            COMPONENTS.register("sequence_wireless_binding", () -> DataComponentType.<CompoundTag>builder()
                    .persistent(CompoundTag.CODEC).networkSynchronized(ByteBufCodecs.COMPOUND_TAG).build());
    public static final DeferredBlock<SequenceWirelessHubBlock> HUB =
            ModContent.BLOCKS.register("sequence_wireless_hub", SequenceWirelessHubBlock::new);
    public static final DeferredItem<BlockItem> HUB_ITEM = ModContent.ITEMS.register("sequence_wireless_hub",
            () -> new BlockItem(HUB.get(), new Item.Properties()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SequenceWirelessHubBlockEntity>> HUB_BE =
            ModContent.BLOCK_ENTITIES.register("sequence_wireless_hub", () -> BlockEntityType.Builder.of(
                    SequenceWirelessHubBlockEntity::new, HUB.get()).build(null));

    private WirelessContent() {}

    public static void register(IEventBus eventBus) {
        COMPONENTS.register(eventBus);
    }

    public static void bind() {
        HUB.get().setBlockEntity(SequenceWirelessHubBlockEntity.class, HUB_BE.get(), null, null);
    }
}
