package com.atir.molecularmanipulator.integration;

import appeng.api.ids.AEComponents;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.security.IActionSource;
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
import java.util.UUID;
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
                .filter(assembly -> isDestination(assembly, player.server, team))
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
        var blanks = new MatterFabricationUploadTransfer(host.getLogic().getBlankPatternInv(),
                host.getInventory(), menu.getActionSource(), AEItems.BLANK_PATTERN.asItem(),
                stack -> player.getInventory().placeItemBackInInventory(stack, false));
        if (!blanks.hasBlank()) {
            message(player, "no_blank");
            return;
        }
        for (var assembly : destinations) {
            var inventory = assembly.getLogic().getPatternInv();
            for (int slot = 0; slot < inventory.size(); slot++) {
                if (!inventory.getStackInSlot(slot).isEmpty() || !inventory.insertItem(slot, pattern.copy(), true).isEmpty()) continue;
                boolean inserted;
                try {
                    inserted = blanks.insert(inventory, slot, pattern);
                } finally {
                    // A rejected insertion can return a network blank to the terminal slot.
                    host.getLogic().saveChanges();
                    menu.broadcastChanges();
                }
                if (!inserted) {
                    message(player, "failed");
                    return;
                }
                assembly.saveChanges();
                var pos = assembly.getBlockPos();
                message(player, "success", assembly.getDisplayName(),
                        assembly.getLevel().dimension().location().toString(), pos.getX(), pos.getY(), pos.getZ());
                return;
            }
        }
        message(player, "full");
    }

    /** Upload identity never depends on a custom name or a pattern terminal's display group. */
    private static boolean isDestination(MatterFabricationPatternAssemblyBlockEntity assembly,
            MinecraftServer server, UUID team) {
        if (assembly.isRemoved() || !(assembly.getLevel() instanceof ServerLevel level)
                || level.getServer() != server || !level.hasChunkAt(assembly.getBlockPos()) || team == null) return false;
        var controller = assembly.getController();
        return controller != null && team.equals(controller.getBoundTeam());
    }

    /** Local-only entry point retained for callers that already own a blank inventory. */
    public static boolean insertConsumingBlank(InternalInventory blanks, InternalInventory destination, int slot, ItemStack pattern) {
        return new MatterFabricationUploadTransfer(blanks, null, IActionSource.empty(), AEItems.BLANK_PATTERN.asItem(),
                stack -> { throw new IllegalStateException("Unexpected network blank recovery"); })
                .insert(destination, slot, pattern);
    }

    private static boolean containsPattern(InternalInventory inventory, ItemStack incoming) {
        var encoded = incoming.get(AEComponents.ENCODED_PROCESSING_PATTERN);
        for (var stored : inventory) {
            if (!stored.isEmpty() && stored.is(incoming.getItem())
                    && Objects.equals(encoded, stored.get(AEComponents.ENCODED_PROCESSING_PATTERN))) return true;
        }
        return false;
    }

    private static void message(ServerPlayer player, String key, Object... args) {
        player.sendSystemMessage(Component.translatable("message.molecularmanipulator.fabrication.upload." + key, args));
    }
}
