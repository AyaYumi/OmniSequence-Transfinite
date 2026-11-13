package com.atir.molecularmanipulator.menu;

import com.atir.molecularmanipulator.blockentity.MolecularCenterBlockEntity.QuantumLinkState;
import com.atir.molecularmanipulator.blockentity.SequenceWirelessHubBlockEntity;
import com.atir.molecularmanipulator.integration.ae2.SequenceWirelessAccess;
import com.atir.molecularmanipulator.integration.ae2.SequenceWirelessHost;
import com.atir.molecularmanipulator.integration.ae2.SequenceWirelessHubs;
import com.atir.molecularmanipulator.util.MathCompat;
import java.util.Comparator;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;

/** Actions resolve through the open menu and revalidate both ends, never trust a client position. */
final class SequenceWirelessMenu {
    static final String OPEN = "wireless_open", PAGE = "wireless_page";
    static final String CONNECT = "wireless_connect", DISCONNECT = "wireless_disconnect";
    private final BuildingMenu menu;
    private boolean open;
    private int page;
    private long nextSync;
    private String result = "";

    SequenceWirelessMenu(BuildingMenu menu) { this.menu = menu; }

    private SequenceWirelessHost host() { return SequenceWirelessAccess.host(menu.getBlockEntity()); }

    private boolean valid() {
        return menu.getPlayer() instanceof ServerPlayer player
                && player.containerMenu == menu && menu.isValidMenu() && menu.stillValid(player);
    }

    void open(boolean selected) {
        if (!valid()) return;
        open = selected;
        page = 0;
        result = "";
        nextSync = 0;
        if (!open) menu.wirelessState = SequenceWirelessMenuState.EMPTY;
        menu.broadcastChanges();
    }

    void page(int requested) {
        if (!valid() || !open) return;
        page = Math.max(0, requested);
        nextSync = 0;
        result = "";
        menu.broadcastChanges();
    }

    private boolean permitted(SequenceWirelessHost host) {
        return valid() && host != null && SequenceWirelessAccess.canConfigure((ServerPlayer) menu.getPlayer(), host);
    }

    void connect(String id) {
        if (!open) return;
        var host = host();
        if (!permitted(host)) { finish("denied"); return; }
        // Require a row from this menu's last authorized page, then recheck UUID, location and team.
        var entry = menu.wirelessState.entries().stream()
                .filter(candidate -> candidate.binding().hubId().toString().equals(id)).findFirst().orElse(null);
        var player = (ServerPlayer) menu.getPlayer();
        var hub = entry == null ? null : entry.binding().resolve(player.getServer());
        if (hub == null) { finish("missing"); return; }
        if (!hub.canManage(player) || !SequenceWirelessAccess.sameTeam(host, hub)) { finish("denied"); return; }
        host.getWirelessLink().bind(hub.binding());
        host.refreshRemoteConnection();
        finish("bound");
    }

    void disconnect() {
        if (!open) return;
        var host = host();
        if (!permitted(host)) { finish("denied"); return; }
        host.getWirelessLink().clear();
        host.refreshRemoteConnection();
        finish("unbound");
    }

    private void finish(String message) {
        result = message;
        nextSync = 0;
        menu.broadcastChanges();
    }

    void broadcast() {
        var host = host();
        menu.wirelessAvailable = host != null;
        if (!open || !(menu.getPlayer() instanceof ServerPlayer player)) return;
        long now = player.serverLevel().getGameTime();
        if (now < nextSync) return;
        nextSync = now + 20;
        if (!permitted(host)) {
            menu.wirelessState = new SequenceWirelessMenuState(0, 1, false, false, null,
                    QuantumLinkState.EMPTY, List.of(), "denied");
            return;
        }
        var hubs = SequenceWirelessHubs.loaded(player.getServer()).stream()
                .filter(hub -> hub.canManage(player) && SequenceWirelessAccess.sameTeam(host, hub))
                .sorted(Comparator.comparing((SequenceWirelessHubBlockEntity hub) -> hub.getName().getString())
                        .thenComparing(hub -> hub.binding().describe())).toList();
        int pages = Math.max(1, MathCompat.ceilDiv(hubs.size(), SequenceWirelessMenuState.PAGE_SIZE));
        page = Math.min(page, pages - 1);
        int start = page * SequenceWirelessMenuState.PAGE_SIZE;
        var entries = hubs.subList(start, Math.min(hubs.size(), start + SequenceWirelessMenuState.PAGE_SIZE))
                .stream().map(SequenceWirelessMenu::entry).toList();
        var link = host.getWirelessLink();
        var binding = link.binding();
        var current = binding == null ? null : binding.resolve(player.getServer());
        // A retained binding from another team stays disconnectable without exposing that team's hub.
        var visibleCurrent = current != null && current.canManage(player)
                && SequenceWirelessAccess.sameTeam(host, current) ? entry(current) : null;
        menu.wirelessState = new SequenceWirelessMenuState(page, pages, true, link.isBound(),
                visibleCurrent, link.state(), entries, result);
    }

    private static SequenceWirelessMenuState.Entry entry(SequenceWirelessHubBlockEntity hub) {
        return new SequenceWirelessMenuState.Entry(hub.binding(), hub.getName().copy(), hub.isOnline());
    }
}
