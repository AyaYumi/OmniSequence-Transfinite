package com.atir.molecularmanipulator.mixin;

import com.atir.molecularmanipulator.building.BuildingPackage;
import net.minecraft.network.protocol.game.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keep the durable transfer receipt in its native inventory slot until completion. */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class BuildingReservationMixin {
    @Shadow public ServerPlayer player;
    @Unique private static final String FOLDING_THREAD = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V";
    @Unique private static boolean folding$reserved(ItemStack stack) { return BuildingPackage.reservation(stack) != null; }
    @Unique private void folding$reject(CallbackInfo callback) { player.containerMenu.sendAllDataToRemote(); callback.cancel(); }
    @Inject(method = "handleContainerClick", at = @At(value = "INVOKE", target = FOLDING_THREAD, shift = At.Shift.AFTER), cancellable = true)
    private void folding$container(ServerboundContainerClickPacket packet, CallbackInfo callback) {
        if (BuildingPackage.blocksClick(player, player.containerMenu, packet.getSlotNum(), packet.getButtonNum(), packet.getClickType())) folding$reject(callback);
    }
    @Inject(method = "handlePlayerAction", at = @At(value = "INVOKE", target = FOLDING_THREAD, shift = At.Shift.AFTER), cancellable = true)
    private void folding$drop(ServerboundPlayerActionPacket packet, CallbackInfo callback) {
        var action = packet.getAction();
        if ((action == ServerboundPlayerActionPacket.Action.DROP_ITEM || action == ServerboundPlayerActionPacket.Action.DROP_ALL_ITEMS
                || action == ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND) && folding$reserved(player.getMainHandItem())
                || action == ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND && folding$reserved(player.getOffhandItem())) folding$reject(callback);
    }
    @Inject(method = "handleSetCreativeModeSlot", at = @At(value = "INVOKE", target = FOLDING_THREAD, shift = At.Shift.AFTER), cancellable = true)
    private void folding$creative(ServerboundSetCreativeModeSlotPacket packet, CallbackInfo callback) {
        int id = packet.slotNum();
        if (folding$reserved(packet.itemStack()) || id >= 0 && id < player.inventoryMenu.slots.size()
                && folding$reserved(player.inventoryMenu.getSlot(id).getItem())) folding$reject(callback);
    }
    @Mixin(AbstractContainerMenu.class)
    public abstract static class Menu {
        @Inject(method = "clicked", at = @At("HEAD"), cancellable = true)
        private void folding$click(int id, int button, ClickType type, Player player, CallbackInfo callback) {
            if (BuildingPackage.blocksClick(player, (AbstractContainerMenu) (Object) this, id, button, type)) callback.cancel();
        }
    }
}
