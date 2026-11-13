package com.atir.molecularmanipulator.client;

import com.atir.molecularmanipulator.MolecularManipulator;
import com.atir.molecularmanipulator.network.LanternPayloads;
import com.atir.molecularmanipulator.world.LanternSimulation;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

/** Configurable controls and local eyelids; the server acknowledges every simulation transition. */
@EventBusSubscriber(modid = MolecularManipulator.MOD_ID, value = Dist.CLIENT)
public final class LanternClient {
    private static final String CATEGORY = "key.categories.molecularmanipulator.lantern";
    public static final KeyMapping CLOSE = key("close_eyes", GLFW.GLFW_KEY_G);
    public static final KeyMapping PLACE = key("place_lantern", GLFW.GLFW_KEY_V);
    public static final KeyMapping FIRE = key("lantern_fire", GLFW.GLFW_KEY_B);
    private static Player trackedPlayer;
    private static Level trackedLevel;
    private static int heldTicks;
    private static boolean closing, awaitingState;
    private static float previousEye, eye;
    private LanternClient() {}
    private static KeyMapping key(String name, int code) {
        return new KeyMapping("key.molecularmanipulator." + name, KeyConflictContext.IN_GAME,
                InputConstants.Type.KEYSYM, code, CATEGORY);
    }
    public static void receive(LanternPayloads.State state) {
        var mc = Minecraft.getInstance();
        if (mc.player == null) return;
        trackedPlayer = mc.player;
        trackedLevel = mc.level;
        boolean changed = LanternSimulation.isActive(mc.player) != state.active();
        LanternSimulation.setClientActive(mc.player, state.active());
        LanternParentRockModels.setActive(state.active());
        awaitingState = false;
        if (changed && mc.level != null) mc.levelRenderer.allChanged();
    }
    private static void send(int action) { LanternPayloads.sendToServer(new LanternPayloads.Action(action)); }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var mc = Minecraft.getInstance();
        if (trackedPlayer != mc.player || trackedLevel != mc.level) {
            clear();
            trackedPlayer = mc.player;
            trackedLevel = mc.level;
        }
        previousEye = eye;
        if (mc.player == null || mc.level == null) return;
        if (mc.screen == null && !mc.isPaused()) {
            while (PLACE.consumeClick()) send(LanternPayloads.PLACE);
            while (FIRE.consumeClick()) send(LanternPayloads.FIRE);
        } else {
            while (PLACE.consumeClick()) { }
            while (FIRE.consumeClick()) { }
        }
        boolean mayClose = mc.screen == null && !mc.isPaused() && mc.player.isAlive()
                && !LanternSimulation.isActive(mc.player) && LanternSimulation.heldHand(mc.player, true) != null;
        boolean down = mayClose && CLOSE.isDown();
        if (down && !closing && !awaitingState) {
            closing = true;
            heldTicks = 0;
            send(LanternPayloads.CLOSE);
        }
        if (closing && down) heldTicks++;
        if (closing && !down) {
            closing = false;
            if (mayClose && heldTicks >= LanternSimulation.CLOSE_TICKS) {
                awaitingState = true;
                send(LanternPayloads.OPEN);
            } else send(LanternPayloads.CANCEL);
            heldTicks = 0;
        }
        float target = closing || awaitingState ? 1 : 0;
        eye += com.atir.molecularmanipulator.util.MathCompat.clamp(target - eye, -.16F, .16F);
    }
    @SubscribeEvent public static void renderEyes(RenderGuiEvent.Post event) {
        float partial = event.getPartialTick();
        float amount = previousEye + (eye - previousEye) * partial;
        if (amount <= .001F) return;
        var graphics = event.getGuiGraphics();
        int width = graphics.guiWidth(), height = graphics.guiHeight();
        int lid = (int) Math.ceil(height * .5F * amount);
        graphics.fill(0, 0, width, lid, 0xFF020504);
        graphics.fill(0, height - lid, width, height, 0xFF020504);
        if (amount < .99F) {
            graphics.fillGradient(0, lid, width, lid + 10, 0xE0020504, 0x00020504);
            graphics.fillGradient(0, height - lid - 10, width, height - lid, 0x00020504, 0xE0020504);
        }
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { clear(); }
    private static void clear() {
        if (trackedPlayer != null) LanternSimulation.setClientActive(trackedPlayer, false);
        LanternParentRockModels.setActive(false);
        trackedPlayer = null;
        trackedLevel = null;
        heldTicks = 0;
        closing = awaitingState = false;
        previousEye = eye = 0;
    }
    @EventBusSubscriber(modid = MolecularManipulator.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        private Registration() {}
        @SubscribeEvent public static void keys(RegisterKeyMappingsEvent event) {
            event.register(CLOSE); event.register(PLACE); event.register(FIRE);
        }
    }
}
