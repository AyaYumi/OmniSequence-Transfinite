package com.atir.molecularmanipulator.world;

import com.atir.molecularmanipulator.MolecularManipulator;
import com.atir.molecularmanipulator.block.SimulationLanternBlock;
import com.atir.molecularmanipulator.network.LanternPayloads;
import com.atir.molecularmanipulator.registry.SimulationLanternContent;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Per online player only; neither projection nor eye state is written into player/world saves. */
@EventBusSubscriber(modid = MolecularManipulator.MOD_ID)
public final class LanternSimulation {
    public static final int CLOSE_TICKS = 20;
    private static final Map<Player, Session> SESSIONS = new WeakHashMap<>();
    private static volatile Player clientPlayer;
    private static volatile boolean clientActive;
    private static final class Session { boolean active; long closedAt = -1; }
    private LanternSimulation() {}
    public static synchronized boolean isActive(Player player) {
        if (player == null) return false;
        if (player.level().isClientSide) return clientPlayer == player && clientActive;
        Session session = SESSIONS.get(player);
        return session != null && session.active;
    }
    private static synchronized Session session(Player player) {
        return SESSIONS.computeIfAbsent(player, key -> new Session());
    }
    public static void setClientActive(Player player, boolean active) {
        // Client/server player entities share an ID (and equality) in an integrated server.
        // Never put the client's acknowledgement into the server session map.
        if (active) {
            clientPlayer = player;
            clientActive = true;
        } else if (clientPlayer == player) {
            clientActive = false;
            clientPlayer = null;
        }
    }
    private static void sync(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new LanternPayloads.State(isActive(player)));
    }
    public static void exit(ServerPlayer player) {
        synchronized (LanternSimulation.class) { SESSIONS.remove(player); }
        sync(player);
    }
    public static InteractionHand heldHand(Player player, boolean litOnly) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (SimulationLanternContent.isLantern(stack) && (!litOnly || SimulationLanternContent.isLit(stack))) return hand;
        }
        return null;
    }
    public static void action(ServerPlayer player, int action) {
        if (!player.isAlive() || player.isSpectator()) return;
        Session session = session(player);
        switch (action) {
            case LanternPayloads.CLOSE -> {
                if (!session.active && heldHand(player, true) != null && !player.isInWater()) {
                    session.closedAt = player.serverLevel().getGameTime();
                }
            }
            case LanternPayloads.OPEN -> {
                long now = player.serverLevel().getGameTime();
                if (!session.active && session.closedAt >= 0 && now - session.closedAt >= CLOSE_TICKS
                        && heldHand(player, true) != null && !player.isInWater()) {
                    session.active = true;
                    player.playNotifySound(SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, .35F, .65F);
                }
                session.closedAt = -1;
                sync(player);
            }
            case LanternPayloads.CANCEL -> session.closedAt = -1;
            case LanternPayloads.PLACE -> place(player);
            case LanternPayloads.FIRE -> fire(player);
            default -> { }
        }
    }
    public static boolean place(ServerPlayer player) {
        InteractionHand hand = heldHand(player, false);
        if (hand == null) return false;
        HitResult target = player.pick(player.blockInteractionRange(), 1, false);
        if (!(target instanceof BlockHitResult hit) || target.getType() != HitResult.Type.BLOCK) return false;
        ItemStack stack = player.getItemInHand(hand);
        BlockPlaceContext context = new BlockPlaceContext(player.serverLevel(), player, hand, stack, hit);
        if (!player.mayBuild() || !player.serverLevel().mayInteract(player, context.getClickedPos())) return false;
        boolean wasLit = SimulationLanternContent.isLit(stack);
        boolean water = player.serverLevel().getFluidState(context.getClickedPos()).is(net.minecraft.tags.FluidTags.WATER);
        if (water) SimulationLanternContent.setLit(stack, false);
        var result = SimulationLanternContent.ITEM.get().place(context);
        if (!result.consumesAction()) {
            if (water) SimulationLanternContent.setLit(stack, wasLit);
            player.displayClientMessage(Component.translatable("message.molecularmanipulator.lantern_floor"), true);
            return false;
        }
        // Even creative placement transfers the carried lantern rather than leaving a locked copy.
        if (player.getAbilities().instabuild) stack.shrink(1);
        if (stack.isEmpty()) player.setItemInHand(hand, ItemStack.EMPTY);
        player.containerMenu.broadcastChanges();
        return true;
    }
    public static boolean fire(ServerPlayer player) {
        InteractionHand hand = isActive(player) ? heldHand(player, true) : null;
        if (hand == null) hand = heldHand(player, false);
        boolean lit;
        if (hand != null) {
            ItemStack stack = player.getItemInHand(hand);
            lit = SimulationLanternContent.isLit(stack);
            if (!lit && player.isInWater()) return false;
            SimulationLanternContent.setLit(stack, !lit);
            player.containerMenu.broadcastChanges();
        } else {
            HitResult target = player.pick(player.blockInteractionRange(), 1, false);
            if (!(target instanceof BlockHitResult hit) || target.getType() != HitResult.Type.BLOCK) return false;
            var level = player.serverLevel();
            var pos = hit.getBlockPos();
            var state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof SimulationLanternBlock) || !player.mayBuild() || !level.mayInteract(player, pos)) return false;
            lit = state.getValue(SimulationLanternBlock.LIT);
            if (!lit && state.getValue(SimulationLanternBlock.WATERLOGGED)) return false;
            level.setBlockAndUpdate(pos, state.setValue(SimulationLanternBlock.LIT, !lit));
        }
        player.serverLevel().playSound(null, player.blockPosition(), lit ? SoundEvents.FIRE_EXTINGUISH
                : SoundEvents.FLINTANDSTEEL_USE, SoundSource.PLAYERS, .5F, lit ? 1.25F : .8F);
        if (lit) exit(player);
        return true;
    }
    @SubscribeEvent public static void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Session session;
        synchronized (LanternSimulation.class) { session = SESSIONS.get(player); }
        if (session != null && session.closedAt >= 0 && (heldHand(player, true) == null || player.isInWater())) session.closedAt = -1;
        boolean extinguished = false;
        if (player.isInWater()) {
            for (InteractionHand hand : InteractionHand.values()) {
                ItemStack stack = player.getItemInHand(hand);
                if (SimulationLanternContent.isLit(stack)) {
                    SimulationLanternContent.setLit(stack, false);
                    extinguished = true;
                }
            }
        }
        if (extinguished) {
            player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, .5F, 1.3F);
            player.containerMenu.broadcastChanges();
            exit(player);
        }
    }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) exit(player);
    }
    @SubscribeEvent public static synchronized void logout(PlayerEvent.PlayerLoggedOutEvent event) { SESSIONS.remove(event.getEntity()); }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) exit(player);
    }
    @SubscribeEvent public static void death(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) exit(player);
    }
    @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) exit(player);
    }
}
