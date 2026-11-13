package com.atir.molecularmanipulator.integration.ae2;

import com.atir.molecularmanipulator.blockentity.SequenceWirelessHubBlockEntity;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

/** An explicit hub identity and location, never a request to discover or load chunks. */
public record SequenceWirelessBinding(GlobalPos target, UUID hubId) {
    public CompoundTag save() {
        var tag = new CompoundTag();
        tag.putString("dimension", target.dimension().location().toString());
        tag.putLong("pos", target.pos().asLong());
        tag.putUUID("hub", hubId);
        return tag;
    }

    @Nullable
    public static SequenceWirelessBinding load(@Nullable CompoundTag tag) {
        if (tag == null || !tag.hasUUID("hub") || !tag.contains("pos", Tag.TAG_LONG)) return null;
        var dimension = ResourceLocation.tryParse(tag.getString("dimension"));
        if (dimension == null) return null;
        return new SequenceWirelessBinding(GlobalPos.of(ResourceKey.create(Registries.DIMENSION, dimension),
                BlockPos.of(tag.getLong("pos"))), tag.getUUID("hub"));
    }

    @Nullable
    public SequenceWirelessHubBlockEntity resolve(MinecraftServer server) {
        var level = server.getLevel(target.dimension());
        if (level == null || !level.hasChunkAt(target.pos())) return null;
        return level.getBlockEntity(target.pos()) instanceof SequenceWirelessHubBlockEntity hub
                && !hub.isRemoved() && hub.getHubId().equals(hubId) ? hub : null;
    }

    public String describe() {
        return target.dimension().location() + " @ " + target.pos().toShortString();
    }
}
