package com.atir.molecularmanipulator.item;

import appeng.api.implementations.menuobjects.IMenuItem;
import appeng.api.implementations.menuobjects.ItemMenuHost;
import appeng.menu.MenuOpener;
import appeng.menu.locator.ItemMenuHostLocator;
import appeng.menu.locator.MenuLocators;
import com.atir.molecularmanipulator.menu.BuildingTerminalMenu;
import com.atir.molecularmanipulator.terminal.BuildingTerminal;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/** One handheld directory for all of this mod's building control panels. */
public final class BuildingTerminalItem extends Item implements IMenuItem {
    public BuildingTerminalItem(Properties properties) { super(properties); }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide()) MenuOpener.open(BuildingTerminalMenu.TYPE, player, MenuLocators.forHand(player, hand));
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide());
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        var player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;
        use(context.getLevel(), player, context.getHand());
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide());
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        var player = context.getPlayer();
        if (player == null || !player.isShiftKeyDown()) return InteractionResult.PASS;
        var level = context.getLevel();
        // Run before block activation, so the controller cannot consume the binding click by opening its own GUI.
        if (!level.isClientSide()) {
            var machine = BuildingTerminal.findTarget(level, context.getClickedPos());
            String result;
            Object[] args = {};
            if (machine == null) result = "unsupported";
            else if (!level.mayInteract(player, context.getClickedPos()) || !level.mayInteract(player, machine.getBlockPos())) {
                result = "unavailable";
            } else {
                var bindings = new ArrayList<>(BuildingTerminal.bindings(stack));
                var binding = BuildingTerminal.binding(machine);
                if (bindings.contains(binding)) result = "already_bound";
                else if (bindings.size() >= BuildingTerminal.MAX_BINDINGS) result = "full";
                else {
                    // Rebinding a replaced controller at the same coordinates refreshes that entry.
                    bindings.removeIf(old -> old.target().equals(binding.target()));
                    bindings.add(binding);
                    BuildingTerminal.saveBindings(stack, bindings);
                    player.getInventory().setChanged();
                    result = "bound";
                    args = new Object[]{machine.getBlockState().getBlock().getName(), bindings.size()};
                }
            }
            player.displayClientMessage(Component.translatable("message.molecularmanipulator.building_terminal." + result, args), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    public ItemMenuHost<?> getMenuHost(Player player, ItemMenuHostLocator locator, @Nullable BlockHitResult hitResult) {
        return new Host(this, player, locator);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.molecularmanipulator.building_terminal.bind").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.molecularmanipulator.building_terminal.open").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.molecularmanipulator.building_terminal.count", BuildingTerminal.bindings(stack).size())
                .withStyle(ChatFormatting.AQUA));
    }

    public static final class Host extends ItemMenuHost<BuildingTerminalItem> {
        public Host(BuildingTerminalItem item, Player player, ItemMenuHostLocator locator) { super(item, player, locator); }
    }
}
