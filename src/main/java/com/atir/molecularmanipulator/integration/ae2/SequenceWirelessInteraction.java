package com.atir.molecularmanipulator.integration.ae2;

import appeng.api.implementations.items.IMemoryCard;
import com.atir.molecularmanipulator.MolecularManipulator;
import com.atir.molecularmanipulator.blockentity.SequenceWirelessHubBlockEntity;
import com.atir.molecularmanipulator.building.BuildingTransfer;
import com.atir.molecularmanipulator.registry.WirelessContent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

/** Reuses AE2's memory card as a binding tool, never as an installed access card. */
@EventBusSubscriber(modid = MolecularManipulator.MOD_ID)
public final class SequenceWirelessInteraction {
    private SequenceWirelessInteraction() {}

    @SubscribeEvent
    public static void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        var stack = event.getItemStack();
        if (!(stack.getItem() instanceof IMemoryCard card)) return;
        var level = event.getLevel();
        var entity = level.getBlockEntity(event.getPos());
        var data = card.getData(stack);
        var selected = data.contains(WirelessContent.HUB_BINDING, Tag.TAG_COMPOUND)
                ? data.getCompound(WirelessContent.HUB_BINDING) : null;
        boolean selectingHub = entity instanceof SequenceWirelessHubBlockEntity;
        var host = SequenceWirelessAccess.host(entity);
        boolean applyingBinding = selected != null && host != null;
        if (!selectingHub && !applyingBinding) return;
        // Consume on both sides before AE2 imports unrelated settings or clears a sneak-clicked card.
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
                var bindingData = new CompoundTag();
                bindingData.put(WirelessContent.HUB_BINDING, hub.binding().save());
                bindingData.putString("tooltip", "exported_setting.molecularmanipulator.sequence_wireless_binding");
                card.setMemoryCardContents(stack, hub.getBlockState().getBlock().getDescriptionId(), bindingData);
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
