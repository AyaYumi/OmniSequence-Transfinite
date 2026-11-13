package com.atir.molecularmanipulator.building;

import com.atir.molecularmanipulator.registry.ModContent;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/** The complete synchronized package data: identity, template version and a small summary only. */
public final class BuildingPackage {
    private static final String KEY = "folded_building";
    private BuildingPackage() {}
    public static CompoundTag data(ItemStack stack) {
        if (!stack.is(ModContent.FOLDED_BUILDING_PACKAGE.get())) return new CompoundTag();
        return stack.hasTag() ? stack.getTag().getCompound(KEY).copy() : new CompoundTag();
    }
    public static BuildingType type(ItemStack stack) {
        try { return BuildingType.byId(data(stack).getString("type")); } catch (IllegalArgumentException ignored) { return null; }
    }
    public static UUID archive(ItemStack stack) {
        var tag = data(stack); return tag.hasUUID("archive") ? tag.getUUID("archive") : null;
    }
    public static UUID reservation(ItemStack stack) {
        var tag = data(stack); return tag.hasUUID("reservation") ? tag.getUUID("reservation") : null;
    }
    public static boolean blocksClick(net.minecraft.world.entity.player.Player player, net.minecraft.world.inventory.AbstractContainerMenu menu,
            int id, int button, net.minecraft.world.inventory.ClickType click) {
        if (id >= 0 && id < menu.slots.size() && reservation(menu.getSlot(id).getItem()) != null) return true;
        return reservation(menu.getCarried()) != null || click == net.minecraft.world.inventory.ClickType.SWAP
                && button >= 0 && button < player.getInventory().getContainerSize() && reservation(player.getInventory().getItem(button)) != null;
    }
    public static ItemStack fresh(BuildingType type) {
        var stack = new ItemStack(ModContent.FOLDED_BUILDING_PACKAGE.get());
        var tag = new CompoundTag(); tag.putString("type", type.id); tag.putInt("template", BuildingType.TEMPLATE_VERSION);
        set(stack, tag); return stack;
    }
    public static ItemStack sealed(BuildingType type, UUID id, int blocks) {
        var stack = fresh(type); var tag = data(stack);
        tag.putUUID("archive", id); tag.putInt("blocks", blocks); set(stack, tag); return stack;
    }
    public static void reserve(ItemStack stack, UUID id) {
        var tag = data(stack); tag.putUUID("reservation", id); set(stack, tag);
    }
    public static void clearReservation(ItemStack stack) {
        var tag = data(stack); tag.remove("reservation"); set(stack, tag);
    }
    private static void set(ItemStack stack, CompoundTag data) {
        stack.getOrCreateTag().put(KEY, data);
    }
}
