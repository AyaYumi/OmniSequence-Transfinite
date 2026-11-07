package com.atir.molecularmanipulator.menu;

import appeng.blockentity.AEBaseBlockEntity;
import appeng.menu.AEBaseMenu;
import com.atir.molecularmanipulator.blockentity.MachineEffectHost;
import com.atir.molecularmanipulator.blockentity.MachineEffectSettings;
import net.minecraft.server.level.ServerPlayer;

/** Shared validation for AE2 menu actions; never accepts a client-selected controller position. */
public final class MachineEffectMenuAccess {
    public static final String ACTION = "set_machine_effect_level";

    private MachineEffectMenuAccess() {}

    public static boolean apply(AEBaseMenu menu, AEBaseBlockEntity machine, Integer requested) {
        if (requested == null || !MachineEffectSettings.isValidOverride(requested)
                || menu.isClientSide() || !(menu.getPlayer() instanceof ServerPlayer player)
                || player.containerMenu != menu || !menu.isValidMenu() || !menu.stillValid(player)
                || player.isSpectator() || !player.mayBuild()
                || !(machine instanceof MachineEffectHost host)) return false;
        var level = machine.getLevel();
        var pos = machine.getBlockPos();
        if (level == null || level.isClientSide() || machine.isRemoved() || player.level() != level
                || !level.hasChunkAt(pos) || level.getBlockEntity(pos) != machine
                || player.distanceToSqr(pos.getCenter()) > 64 || !level.mayInteract(player, pos)) return false;
        if (!host.getMachineEffects().setOverride(requested)) return false;
        machine.saveChanges();
        // This reaches all chunk watchers, including players who never opened this menu.
        machine.markForClientUpdate();
        return true;
    }
}
