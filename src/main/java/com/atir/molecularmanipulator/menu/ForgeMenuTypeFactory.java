package com.atir.molecularmanipulator.menu;

import appeng.menu.AEBaseMenu;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuLocator;
import appeng.menu.locator.MenuLocators;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Nameable;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.network.NetworkHooks;

import java.util.Objects;
import java.util.function.Function;

/**
 * Forge 1.20.1 equivalent of AE2's newer buildUnregistered menu builder.
 *
 * <p>AE2 15's {@code MenuTypeBuilder.build} always queues the menu in AE2's
 * namespace. Addon menus must instead be registered by their own mod while
 * retaining AE2's locator-based opening protocol.</p>
 */
public final class ForgeMenuTypeFactory {
    @FunctionalInterface
    public interface MenuFactory<M extends AEBaseMenu, H> {
        M create(int id, Inventory inventory, H host);
    }

    private ForgeMenuTypeFactory() {
    }

    public static <M extends AEBaseMenu, H> MenuType<M> create(
            ResourceLocation id, MenuFactory<M, H> factory, Class<H> hostClass) {
        return create(id, factory, hostClass, ForgeMenuTypeFactory::getTitle);
    }

    public static <M extends AEBaseMenu, H> MenuType<M> create(
            ResourceLocation id, MenuFactory<M, H> factory, Class<H> hostClass, Function<H, Component> title) {
        MenuType<M> menuType = IForgeMenuType.create((containerId, inventory, buffer) -> {
            var locator = MenuLocators.readFromPacket(buffer);
            var host = locator.locate(inventory.player, hostClass);
            if (host == null) {
                throw new IllegalStateException("Could not locate menu host for " + id + " using " + locator);
            }

            var menu = factory.create(containerId, inventory, host);
            menu.setLocator(locator);
            menu.setReturnedFromSubScreen(buffer.readBoolean());
            return menu;
        });

        MenuOpener.addOpener(menuType,
                (player, locator, returnedFromSubScreen) -> open(
                        factory, hostClass, title, player, locator, returnedFromSubScreen));
        return menuType;
    }

    private static <M extends AEBaseMenu, H> boolean open(
            MenuFactory<M, H> factory,
            Class<H> hostClass,
            Function<H, Component> title,
            Player player,
            MenuLocator locator,
            boolean returnedFromSubScreen) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return false;
        }

        var host = locator.locate(player, hostClass);
        if (host == null) {
            return false;
        }

        var provider = new SimpleMenuProvider((containerId, inventory, ignored) -> {
            var menu = factory.create(containerId, inventory, host);
            menu.setLocator(locator);
            return menu;
        }, host instanceof Nameable nameable && nameable.hasCustomName() ? getTitle(host) : title.apply(host));

        NetworkHooks.openScreen(serverPlayer, provider, buffer -> {
            MenuLocators.writeToPacket(buffer, locator);
            buffer.writeBoolean(returnedFromSubScreen);
        });
        return true;
    }

    private static Component getTitle(Object host) {
        if (host instanceof Nameable nameable && nameable.hasCustomName()) {
            return Objects.requireNonNullElse(nameable.getCustomName(), Component.empty());
        }
        return Component.empty();
    }
}
