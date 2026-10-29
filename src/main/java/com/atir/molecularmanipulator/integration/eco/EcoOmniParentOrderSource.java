package com.atir.molecularmanipulator.integration.eco;

import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.parts.IPartHost;
import appeng.parts.AEBasePart;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Keeps the original security source; reload never silently promotes a player to the CPU machine. */
public final class EcoOmniParentOrderSource {
    private final CompoundTag identity;
    private final IActionSource original;

    private EcoOmniParentOrderSource(CompoundTag identity, IActionSource original) {
        this.identity = identity.copy();
        this.original = original;
    }

    public static EcoOmniParentOrderSource capture(IActionSource source) {
        var tag = new CompoundTag();
        tag.putString("type", source.getClass().getName());
        tag.putBoolean("reconstructible", source.getClass() == appeng.me.helpers.BaseActionSource.class
                || source.getClass() == appeng.me.helpers.PlayerSource.class
                || source.getClass() == appeng.me.helpers.MachineSource.class);
        source.player().ifPresent(player -> tag.putUUID("player", player.getUUID()));
        source.machine().ifPresent(machine -> {
            tag.putBoolean("machine", true);
            BlockEntity block = machine instanceof BlockEntity be ? be
                    : machine instanceof AEBasePart part ? part.getBlockEntity() : null;
            if (block != null && block.getLevel() != null) {
                tag.putString("dimension", block.getLevel().dimension().location().toString());
                tag.putLong("position", block.getBlockPos().asLong());
                tag.putString("machineClass", machine.getClass().getName());
                if (machine instanceof AEBasePart part) {
                    tag.putBoolean("part", true);
                    tag.putInt("side", part.getSide() == null ? -1 : part.getSide().ordinal());
                }
            }
        });
        return new EcoOmniParentOrderSource(tag, source);
    }

    public IActionSource resolve(MinecraftServer server) {
        UUID playerId = identity.hasUUID("player") ? identity.getUUID("player") : null;
        if (original != null && playerId == null && !identity.getBoolean("machine")) return original;
        if (server == null) return null;
        var player = playerId == null ? null : server.getPlayerList().getPlayer(playerId);
        if (playerId != null && player == null) return null;
        // Retain custom action-source contexts while the original source remains live.
        if (original != null && (playerId == null || original.player().orElse(null) == player)) return original;
        if (!identity.getBoolean("reconstructible")) return null;
        IActionHost machine = null;
        if (identity.getBoolean("machine")) {
            if (!identity.contains("dimension")) return null;
            var dimension = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(identity.getString("dimension")));
            var level = server.getLevel(dimension);
            var position = BlockPos.of(identity.getLong("position"));
            if (level == null || !level.hasChunkAt(position)) return null;
            var block = level.getBlockEntity(position);
            Object candidate = block;
            if (identity.getBoolean("part")) {
                int side = identity.getInt("side");
                if (side < -1 || side >= Direction.values().length || !(block instanceof IPartHost host)) return null;
                candidate = host.getPart(side == -1 ? null : Direction.values()[side]);
            }
            if (!(candidate instanceof IActionHost host)
                    || !candidate.getClass().getName().equals(identity.getString("machineClass"))) return null;
            machine = host;
        }
        return player != null ? IActionSource.ofPlayer(player, machine)
                : machine != null ? IActionSource.ofMachine(machine) : IActionSource.empty();
    }

    public CompoundTag write() { return identity.copy(); }
    public static EcoOmniParentOrderSource read(CompoundTag tag) { return new EcoOmniParentOrderSource(tag, null); }
}
