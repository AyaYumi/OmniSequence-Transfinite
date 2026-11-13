package com.atir.molecularmanipulator.integration.ae2;

import appeng.api.ids.AEComponents;
import appeng.api.implementations.items.IMemoryCard;
import appeng.items.tools.MemoryCardItem;
import com.atir.molecularmanipulator.MolecularManipulator;
import com.atir.molecularmanipulator.blockentity.SequenceWirelessHubBlockEntity;
import com.atir.molecularmanipulator.building.BuildingTransfer;
import com.atir.molecularmanipulator.registry.WirelessContent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** Reuses AE2's memory card as a binding tool, never as an installed access card. */
@EventBusSubscriber(modid = MolecularManipulator.MOD_ID)
public final class SequenceWirelessInteraction {
    private SequenceWirelessInteraction() {}

    @SubscribeEvent
    public static void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        var stack = event.getItemStack();
        if (!(stack.getItem() instanceof IMemoryCard)) return;
        var level = event.getLevel();
        var entity = level.getBlockEntity(event.getPos());
        var selected = stack.get(WirelessContent.HUB_BINDING.get());
        boolean selectingHub = entity instanceof SequenceWirelessHubBlockEntity;
        var host = SequenceWirelessAccess.host(entity);
        boolean applyingBinding = selected != null && host != null;
        if (!selectingHub && !applyingBinding) return;
        // Consume the interaction on both sides before AE2 imports unrelated machine settings
        // or the memory card clears itself on a sneak-click.
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide()));
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!player.mayBuild() || player.isSpectator() || !level.mayInteract(player, event.getPos())
                || BuildingTransfer.locked(level, event.getPos())) {
            message(player, "unavailable");
            return;
        }
        if (entity instanceof SequenceWirelessHubBlockEntity hub) {
            if (!hub.canManage(player)) {
                message(player, "unavailable");
            } else if (!player.isShiftKeyDown()) {
                message(player, "usage");
            } else {
                MemoryCardItem.clearCard(stack);
                stack.set(WirelessContent.HUB_BINDING.get(), hub.binding().save());
                stack.set(AEComponents.EXPORTED_SETTINGS_SOURCE, hub.getName());
                player.getInventory().setChanged();
                message(player, "selected", hub.binding().describe());
            }
            return;
        }
        if (host == null || !SequenceWirelessAccess.canConfigure(player, host)) {
            message(player, "unavailable");
            return;
        }
        if (player.isShiftKeyDown()) {
            host.getWirelessLink().clear();
            host.refreshRemoteConnection();
            message(player, "unbound");
            return;
        }
        var binding = SequenceWirelessBinding.load(selected);
        var hub = binding == null ? null : binding.resolve(player.getServer());
        if (hub == null) {
            message(player, "missing");
        } else if (!hub.canManage(player) || !SequenceWirelessAccess.sameTeam(host, hub)) {
            message(player, "unavailable");
        } else {
            host.getWirelessLink().bind(binding);
            host.refreshRemoteConnection();
            message(player, "bound", binding.describe());
        }
    }

    private static void message(ServerPlayer player, String key, Object... args) {
        player.displayClientMessage(Component.translatable("message.molecularmanipulator.wireless_hub." + key, args), true);
    }
}
