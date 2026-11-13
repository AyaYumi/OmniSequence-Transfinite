package com.atir.molecularmanipulator.menu;

import appeng.blockentity.AEBaseBlockEntity;
import appeng.menu.AEBaseMenu;
import com.atir.molecularmanipulator.blockentity.MachineEffectHost;
import com.atir.molecularmanipulator.blockentity.MachineEffectSettings;
import com.atir.molecularmanipulator.terminal.BuildingTerminalLocator;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** Shared validation for AE2 menu actions; never accepts a client-selected controller position. */
public final class MachineEffectMenuAccess {
    public static final String ACTION = "set_machine_effect_level";

    private MachineEffectMenuAccess() {}

    public static boolean apply(AEBaseMenu menu, AEBaseBlockEntity machine, Integer requested) {
        if (requested == null || !MachineEffectSettings.isValidOverride(requested)
                || menu.isClientSide() || !(menu.getPlayer() instanceof ServerPlayer player)
                || player.containerMenu != menu || !menu.isValidMenu()
                || player.isSpectator() || !player.mayBuild()
                || !(machine instanceof MachineEffectHost host)) return false;
        var level = machine.getLevel();
        var pos = machine.getBlockPos();
        if (level == null || level.isClientSide() || machine.isRemoved() || player.level() != level
                || !level.hasChunkAt(pos) || level.getBlockEntity(pos) != machine
                || menu.getBlockEntity() != machine || !level.mayInteract(player, pos)) return false;
        if (menu.getLocator() instanceof BuildingTerminalLocator terminal) {
            // Remote access belongs to this menu's exact binding and inventory slot, not just any held terminal.
            if (!terminal.target().dimension().equals(level.dimension()) || !terminal.target().pos().equals(pos)
                    || !terminal.isValid(player)) return false;
        } else if (player.distanceToSqr(pos.getCenter()) > 64) return false;
        // Check liveness before AE2's menu validation, which can otherwise query an unloaded host chunk.
        if (!menu.stillValid(player) || !host.getMachineEffects().setOverride(requested)) return false;
        machine.saveChanges();
        // This reaches all chunk watchers, including players who never opened this menu.
        machine.markForUpdate();
        return true;
    }
}
