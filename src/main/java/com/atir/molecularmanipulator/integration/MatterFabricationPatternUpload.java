package com.atir.molecularmanipulator.integration;

import appeng.api.inventories.InternalInventory;
import appeng.core.definitions.AEItems;
import appeng.helpers.IPatternTerminalMenuHost;
import appeng.menu.AEBaseMenu;
import com.atir.molecularmanipulator.blockentity.MatterFabricationPatternAssemblyBlockEntity;
import com.atir.molecularmanipulator.crafting.MatterFabricationPatternEncoding;
import com.atir.molecularmanipulator.crafting.MatterRecipeIndex;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Server-thread-only upload service. Formed wells already maintain their structure chunks. */
public final class MatterFabricationPatternUpload {
    private static final Map<MinecraftServer, Set<MatterFabricationPatternAssemblyBlockEntity>> LOADED = new WeakHashMap<>();

    private MatterFabricationPatternUpload() {}

    public static void register(MatterFabricationPatternAssemblyBlockEntity assembly) {
        if (assembly.getLevel() instanceof ServerLevel level) {
            LOADED.computeIfAbsent(level.getServer(), ignored -> Collections.newSetFromMap(new WeakHashMap<>())).add(assembly);
        }
    }

    public static void unregister(MatterFabricationPatternAssemblyBlockEntity assembly) {
        if (assembly.getLevel() instanceof ServerLevel level) {
            var assemblies = LOADED.get(level.getServer());
            if (assemblies != null) assemblies.remove(assembly);
        }
    }

    public static void upload(ServerPlayer player, int containerId, ResourceLocation recipeId, List<ItemStack> selections) {
        if (!(player.containerMenu instanceof AEBaseMenu menu) || menu.containerId != containerId
                || !MatterFabricationPatternEncoding.isEncodingMenu(menu) || menu.getPlayer() != player
                || !menu.isValidMenu() || !menu.stillValid(player) || player.isSpectator()) return;
        var recipe = MatterRecipeIndex.get(player.level()).fabrication(recipeId);
        if (recipe == null) {
            message(player, "invalid_recipe");
            return;
        }
        ItemStack pattern;
        try {
            pattern = MatterFabricationPatternEncoding.encode(recipe.value(), selections);
        } catch (IllegalArgumentException | ArithmeticException exception) {
            message(player, "invalid_recipe");
            return;
        }
        var team = FtbTeamOwnership.forPlayer(player);
        var destinations = LOADED.getOrDefault(player.server, Set.of()).stream()
                .filter(assembly -> !assembly.isRemoved() && assembly.getLevel() instanceof ServerLevel level
                        && level.getServer() == player.server && level.hasChunkAt(assembly.getBlockPos()))
                .filter(assembly -> assembly.getController() != null && team != null
                        && team.equals(assembly.getController().getBoundTeam()))
                .sorted(Comparator.<MatterFabricationPatternAssemblyBlockEntity>comparingInt(
                                assembly -> assembly.getLevel() == player.level() ? 0 : 1)
                        .thenComparingDouble(assembly -> assembly.getBlockPos().distToCenterSqr(player.position()))
                        .thenComparing(assembly -> assembly.getLevel().dimension().location().toString())
                        .thenComparingLong(assembly -> assembly.getBlockPos().asLong()))
                .toList();
        if (destinations.isEmpty()) {
            message(player, "no_assembly");
            return;
        }
        for (var assembly : destinations) {
            if (containsPattern(assembly.getLogic().getPatternInv(), pattern)) {
                message(player, "duplicate");
                return;
            }
        }
        var host = (IPatternTerminalMenuHost) menu.getTarget();
        var blanks = host.getLogic().getBlankPatternInv();
        if (blankSlot(blanks) < 0) {
            message(player, "no_blank");
            return;
        }
        for (var assembly : destinations) {
            var inventory = assembly.getLogic().getPatternInv();
            for (int slot = 0; slot < inventory.size(); slot++) {
                if (!inventory.getStackInSlot(slot).isEmpty() || !inventory.insertItem(slot, pattern.copy(), true).isEmpty()) continue;
                if (!insertConsumingBlank(blanks, inventory, slot, pattern)) {
                    message(player, "failed");
                    return;
                }
                assembly.saveChanges();
                host.getLogic().saveChanges();
                menu.broadcastChanges();
                var pos = assembly.getBlockPos();
                message(player, "success", assembly.getDisplayName(),
                        assembly.getLevel().dimension().location().toString(), pos.getX(), pos.getY(), pos.getZ());
                return;
            }
        }
        message(player, "full");
    }

    /** Both inventories belong to this server tick; failures restore the unchanged blank stack. */
    public static boolean insertConsumingBlank(InternalInventory blanks, InternalInventory destination, int slot, ItemStack pattern) {
        int sourceSlot = blankSlot(blanks);
        if (sourceSlot < 0 || pattern.isEmpty() || pattern.getCount() != 1 || slot < 0 || slot >= destination.size()
                || !destination.getStackInSlot(slot).isEmpty()
                || !destination.insertItem(slot, pattern.copy(), true).isEmpty()) return false;
        var original = blanks.getStackInSlot(sourceSlot).copy();
        var consumed = blanks.extractItem(sourceSlot, 1, false);
        if (consumed.isEmpty() || !consumed.is(AEItems.BLANK_PATTERN.asItem())) {
            blanks.setItemDirect(sourceSlot, original);
            return false;
        }
        if (!destination.insertItem(slot, pattern.copy(), false).isEmpty()) {
            blanks.setItemDirect(sourceSlot, original);
            return false;
        }
        return true;
    }

    private static int blankSlot(InternalInventory inventory) {
        for (int slot = 0; slot < inventory.size(); slot++) {
            if (!inventory.getStackInSlot(slot).isEmpty() && inventory.getStackInSlot(slot).is(AEItems.BLANK_PATTERN.asItem())) return slot;
        }
        return -1;
    }

    private static boolean containsPattern(InternalInventory inventory, ItemStack incoming) {
        var encoded = incoming.getTag();
        for (var stored : inventory) {
            if (!stored.isEmpty() && stored.is(incoming.getItem())
                    && Objects.equals(encoded, stored.getTag())) return true;
        }
        return false;
    }

    private static void message(ServerPlayer player, String key, Object... args) {
        player.sendSystemMessage(Component.translatable("message.molecularmanipulator.fabrication.upload." + key, args));
    }
}
