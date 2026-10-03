package com.atir.molecularmanipulator.verification;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.blockentity.storage.ChestBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.menu.locator.MenuLocators;
import appeng.menu.me.crafting.CraftAmountMenu;
import appeng.menu.me.crafting.CraftConfirmMenu;
import com.appliedenhancements.Config;
import com.appliedenhancements.CraftingOrderMode;
import com.appliedenhancements.ae2.LongNumberEntryWidgetBridge;
import com.appliedenhancements.network.NetworkHandler;
import com.appliedenhancements.network.ServerConfigSyncPayload;
import java.lang.reflect.Field;
import java.math.BigInteger;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Items;

/** Real Next clicks and AE back actions, across both network directions in a disposable world. */
public final class ForgeCraftingPacketVerification {
    private static final String[] AMOUNTS = {"64", "2147483648", "9223372036854775808"};
    private static final BlockPos POS = new BlockPos(6, 100, 20);
    private static final AEItemKey OUTPUT = AEItemKey.of(Items.DIAMOND);
    private static int stage, wait, ticks, sample;
    private static volatile boolean created;
    private static volatile Throwable serverFailure;
    private static CraftingOrderMode previousMode;
    private static IManagedGridNode providerNode;

    private ForgeCraftingPacketVerification() {}

    public static boolean tick(Minecraft mc) throws Exception {
        if (++ticks > 1800) throw new IllegalStateException("Crafting packet verification timed out at " + stage);
        if (serverFailure != null) throw new IllegalStateException("Server packet fixture failed", serverFailure);
        if (mc.player == null || mc.level == null || mc.getConnection() == null)
            throw new IllegalStateException("Crafting request disconnected the client");
        if (wait-- > 0) return false;
        switch (stage) {
            case 0 -> {
                var uuid = mc.player.getUUID();
                mc.getSingleplayerServer().execute(() -> {
                    try {
                        var player = mc.getSingleplayerServer().getPlayerList().getPlayer(uuid);
                        var level = player.serverLevel();
                        previousMode = Config.MAX_CRAFTING_ORDER_AMOUNT.get();
                        player.teleportTo(4.5, 100, 20.5);
                        player.setNoGravity(true);
                        level.setBlockAndUpdate(POS, AEBlocks.CHEST.block().defaultBlockState());
                        level.setBlockAndUpdate(POS.east(), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
                        ICraftingProvider provider = new ICraftingProvider() {
                            public List<IPatternDetails> getAvailablePatterns() { return List.of(); }
                            public Set<AEKey> getEmitableItems() { return Set.of(OUTPUT); }
                            public boolean pushPattern(IPatternDetails pattern, KeyCounter[] inputs) { return false; }
                            public boolean isBusy() { return false; }
                        };
                        providerNode = GridHelper.createManagedNode(provider, (owner, node) -> {})
                                .setInWorldNode(false).setIdlePowerUsage(0).addService(ICraftingProvider.class, provider);
                        providerNode.create(level, POS);
                        created = true;
                    } catch (Throwable failure) { serverFailure = failure; }
                });
                stage++; wait = 100;
            }
            case 1 -> {
                if (!created || !(mc.level.getBlockEntity(POS) instanceof ChestBlockEntity)) return false;
                server(mc, player -> {
                    var chest = (ChestBlockEntity) player.serverLevel().getBlockEntity(POS);
                    GridHelper.createConnection(providerNode.getNode(), chest.getMainNode().getNode());
                    ICraftingProvider.requestUpdate(providerNode);
                });
                stage++; wait = 60;
            }
            case 2 -> {
                server(mc, player -> {
                    Config.MAX_CRAFTING_ORDER_AMOUNT.set(sample < 2 ? CraftingOrderMode.LONG_MAX : CraftingOrderMode.BIG_INTEGER);
                    NetworkHandler.sendToPlayer(player, ServerConfigSyncPayload.currentServerValues());
                    CraftAmountMenu.open(player, MenuLocators.forBlockEntity(player.serverLevel().getBlockEntity(POS)), OUTPUT, 1);
                });
                stage++; wait = 30;
            }
            case 3 -> {
                if (!(mc.player.containerMenu instanceof CraftAmountMenu) || mc.screen == null) return false;
                var widget = (LongNumberEntryWidgetBridge) field(mc.screen, "amountToCraft");
                widget.appliedenhancements$setExactValue(new BigInteger(AMOUNTS[sample]));
                stage++; wait = 5;
            }
            case 4 -> {
                var next = (Button) field(mc.screen, "next");
                if (!next.active) throw new IllegalStateException("Next disabled for " + AMOUNTS[sample]);
                if (!mc.screen.mouseClicked(next.getX() + next.getWidth() / 2.0,
                        next.getY() + next.getHeight() / 2.0, 0))
                    throw new IllegalStateException("Next click was not accepted");
                System.out.println("FORGE_CRAFTING_PACKET_NEXT=" + AMOUNTS[sample]);
                stage++; wait = 20;
            }
            case 5 -> {
                if (!(mc.player.containerMenu instanceof CraftConfirmMenu confirm) || confirm.getPlan() == null) return false;
                var plan = confirm.getPlan();
                long projection = new BigInteger(AMOUNTS[sample]).min(BigInteger.valueOf(Long.MAX_VALUE)).longValueExact();
                if (plan.getEntries().stream().noneMatch(entry -> entry.getWhat().equals(OUTPUT)
                        && entry.getCraftAmount() == projection))
                    throw new IllegalStateException("Plan did not retain the requested amount " + AMOUNTS[sample]);
                Screenshot.grab(mc.gameDirectory, "crafting-confirm-" + sample + ".png", mc.getMainRenderTarget(), message -> {});
                confirm.goBack(); // AE's real client action sends the back packet.
                stage++; wait = 30;
            }
            case 6 -> {
                if (!(mc.player.containerMenu instanceof CraftAmountMenu) || mc.screen == null) return false;
                var widget = (LongNumberEntryWidgetBridge) field(mc.screen, "amountToCraft");
                if (!widget.appliedenhancements$getExactValue().orElseThrow().equals(new BigInteger(AMOUNTS[sample])))
                    throw new IllegalStateException("Return lost exact quantity " + AMOUNTS[sample]);
                Screenshot.grab(mc.gameDirectory, "crafting-restored-" + sample + ".png", mc.getMainRenderTarget(), message -> {});
                System.out.println("FORGE_CRAFTING_PACKET_ROUNDTRIP_PASS=" + AMOUNTS[sample]);
                if (++sample < AMOUNTS.length) { stage = 2; wait = 10; }
                else {
                    server(mc, player -> {
                        player.closeContainer();
                        providerNode.destroy();
                        Config.MAX_CRAFTING_ORDER_AMOUNT.set(previousMode);
                    });
                    stage++; wait = 20;
                }
            }
            case 7 -> {
                System.out.println("FORGE_CRAFTING_PACKET_ALL_PASS normal=true long=true bigInteger=true next=true back=true connected=true");
                return true;
            }
            default -> throw new IllegalStateException("Unexpected packet verification stage " + stage);
        }
        return false;
    }

    private static Object field(Object target, String name) throws Exception {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException ignored) {}
        }
        throw new NoSuchFieldException(name);
    }

    private static void server(Minecraft mc, java.util.function.Consumer<net.minecraft.server.level.ServerPlayer> task) {
        var uuid = mc.player.getUUID();
        mc.getSingleplayerServer().execute(() -> {
            try { task.accept(mc.getSingleplayerServer().getPlayerList().getPlayer(uuid)); }
            catch (Throwable failure) { serverFailure = failure; }
        });
    }
}
