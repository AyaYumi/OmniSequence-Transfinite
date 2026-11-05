package com.atir.molecularmanipulator.mixin;

import com.atir.molecularmanipulator.world.LanternInventoryLock;
import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundPickItemPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class LanternServerInventoryMixin {
    @Shadow public ServerPlayer player;

    // Validate after vanilla transfers packet handling to the server thread, before mutation.
    @Unique
    private static final String LANTERN_THREAD_CHECK =
            "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread("
                    + "Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;"
                    + "Lnet/minecraft/server/level/ServerLevel;)V";

    @Inject(method = "handleSetCarriedItem", at = @At(value = "INVOKE",
            target = LANTERN_THREAD_CHECK, shift = At.Shift.AFTER), cancellable = true)
    private void lantern$select(ServerboundSetCarriedItemPacket packet, CallbackInfo ci) {
        if (LanternInventoryLock.blocksSelection(player, packet.getSlot())) {
            lantern$reject();
            ci.cancel();
        }
    }

    @Inject(method = "handlePlayerAction", at = @At(value = "INVOKE",
            target = LANTERN_THREAD_CHECK, shift = At.Shift.AFTER), cancellable = true)
    private void lantern$drop(ServerboundPlayerActionPacket packet, CallbackInfo ci) {
        ServerboundPlayerActionPacket.Action action = packet.getAction();
        if ((action == ServerboundPlayerActionPacket.Action.DROP_ITEM
                || action == ServerboundPlayerActionPacket.Action.DROP_ALL_ITEMS)
                && LanternInventoryLock.locksMainHand(player)) {
            lantern$reject();
            ci.cancel();
        }
        // SWAP_ITEM_WITH_OFFHAND deliberately proceeds through vanilla's F handling.
    }

    @Inject(method = "handlePickItem", at = @At(value = "INVOKE",
            target = LANTERN_THREAD_CHECK, shift = At.Shift.AFTER), cancellable = true)
    private void lantern$pick(ServerboundPickItemPacket packet, CallbackInfo ci) {
        if (LanternInventoryLock.locksMainHand(player)) {
            lantern$reject();
            ci.cancel();
        }
    }

    @Inject(method = "handleContainerClick", at = @At(value = "INVOKE",
            target = LANTERN_THREAD_CHECK, shift = At.Shift.AFTER), cancellable = true)
    private void lantern$click(ServerboundContainerClickPacket packet, CallbackInfo ci) {
        if (player.containerMenu.containerId == packet.getContainerId()
                && LanternInventoryLock.blocksClick(player, player.containerMenu,
                        packet.getSlotNum(), packet.getButtonNum(), packet.getClickType())) {
            // Reject before vanilla accepts the client's predicted slots and cursor.
            lantern$reject();
            ci.cancel();
        }
    }

    @Inject(method = "handleSetCreativeModeSlot", at = @At(value = "INVOKE",
            target = LANTERN_THREAD_CHECK, shift = At.Shift.AFTER), cancellable = true)
    private void lantern$creative(ServerboundSetCreativeModeSlotPacket packet, CallbackInfo ci) {
        if (player.gameMode.isCreative()
                && LanternInventoryLock.blocksCreativeChange(player, packet.slotNum(), packet.itemStack())) {
            lantern$reject();
            ci.cancel();
        }
    }

    @Unique
    private void lantern$reject() {
        LanternInventoryLock.message(player);
        player.containerMenu.sendAllDataToRemote();
        if (player.containerMenu != player.inventoryMenu) {
            player.inventoryMenu.sendAllDataToRemote();
        }
        player.connection.send(new ClientboundSetCarriedItemPacket(player.getInventory().selected));
    }
}
