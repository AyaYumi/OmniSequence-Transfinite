package com.atir.molecularmanipulator.verification;

import com.atir.molecularmanipulator.block.GhostMatterBlock;
import com.atir.molecularmanipulator.blockentity.GhostMatterBlockEntity;
import com.atir.molecularmanipulator.client.render.GhostMatterExposureRenderer;
import com.atir.molecularmanipulator.client.render.OmniShaders;
import com.atir.molecularmanipulator.registry.ModContent;
import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;
import java.nio.file.*;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Real server damage packets, natural flowing water, rendered exposure and support-loss sync. */
@EventBusSubscriber(modid = "molecularmanipulator", value = Dist.CLIENT)
public final class GhostMatterClientChecks {
    private static final BlockPos GHOST = new BlockPos(4, 150, 0);
    private static int stage, ticks, watchdog, hurtSounds;
    private static CompletableFuture<?> operation;

    @SubscribeEvent public static void sound(PlaySoundEvent event) {
        if (Boolean.getBoolean("omnisequence.ghost_client_check") && stage >= 2 && stage != 6 && stage != 7
                && event.getName().startsWith("entity.player.hurt")) hurtSounds++;
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) throws Exception {
        if (!Boolean.getBoolean("omnisequence.ghost_client_check")) return;
        var mc = Minecraft.getInstance();
        if (++watchdog > 2400) throw new IllegalStateException("Ghost client check timeout, stage=" + stage);
        if (stage == 0) {
            if (mc.screen instanceof AccessibilityOnboardingScreen) {
                mc.options.onboardingAccessibilityFinished(); mc.setScreen(new TitleScreen());
            }
            if (!(mc.screen instanceof TitleScreen)) return;
            mc.options.pauseOnLostFocus = false;
            mc.options.renderDistance().set(4);
            mc.getWindow().setWindowed(1280, 720);
            mc.options.screenEffectScale().set(1.0);
            stage = 1;
            mc.createWorldOpenFlows().createFreshLevel("ghost-check-" + System.currentTimeMillis(),
                    new LevelSettings("Ghost Check", GameType.SURVIVAL, false, Difficulty.NORMAL, true,
                            new GameRules(), WorldDataConfiguration.DEFAULT), new WorldOptions(8327461, false, false),
                    registry -> registry.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT)
                            .value().createWorldDimensions(), null);
            return;
        }
        if (mc.player == null || mc.level == null || mc.getSingleplayerServer() == null) return;
        if (operation != null) {
            if (!operation.isDone()) return;
            operation.join(); operation = null;
        }
        if (stage == 1) {
            mc.setScreen(null); mc.options.setCameraType(CameraType.FIRST_PERSON);
            mc.options.hideGui = true;
            var id = mc.player.getUUID();
            operation = mc.getSingleplayerServer().submit(() -> {
                var player = mc.getSingleplayerServer().getPlayerList().getPlayer(id);
                var level = player.serverLevel();
                for (int cx = -1; cx <= 1; cx++) for (int cz = -1; cz <= 1; cz++) level.setChunkForced(cx, cz, true);
                for (int x = -2; x <= 10; x++) for (int z = -5; z <= 5; z++) {
                    level.setBlock(new BlockPos(x, 149, z), Blocks.STONE.defaultBlockState(), 3);
                    for (int y = 150; y <= 154; y++) level.setBlock(new BlockPos(x, y, z),
                            (z == -4 || y == 150 && (x == -2 || x == 10 || Math.abs(z) == 5)
                                    ? Blocks.DEEPSLATE : Blocks.AIR).defaultBlockState(), 3);
                }
                for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++)
                    level.setBlock(GHOST.offset(x, 0, z), ModContent.GHOST_MATTER_BLOCK.get().defaultBlockState(), 3);
                player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);
                player.setHealth(200);
                level.getGameRules().getRule(GameRules.RULE_NATURAL_REGENERATION).set(false, level.getServer());
                player.setNoGravity(true);
                player.setGameMode(GameType.SURVIVAL);
                player.setDeltaMovement(Vec3.ZERO);
                player.connection.teleport(4.5, 150, .7, 180, 12);
            });
            stage = 2; ticks = 0;
            return;
        }
        if (stage == 2) {
            // The real ServerPlayer has a 60-tick login protection window.
            if (++ticks < 80) return;
            check(OmniShaders.ghostExposure() != null, "Exposure shader must compile on the actual GPU");
            check(GhostMatterExposureRenderer.intensity() > .99F, "Exposure must reach full intensity inside real matter");
            LogUtils.getLogger().info("GHOST_CLIENT_EXPOSURE health={} maxHealth={} position={} intensity={} hurtTime={} hurtSounds={}",
                    mc.player.getHealth(), mc.player.getMaxHealth(), mc.player.position(), GhostMatterExposureRenderer.intensity(), mc.player.hurtTime, hurtSounds);
            check(mc.player.getHealth() < 200 && mc.player.getHealth() > 0, "Real server packets must reduce health continuously: health=" + mc.player.getHealth());
            check(mc.player.hurtTime == 0 && hurtSounds == 0, "Exposure packets must produce neither flinch nor hurt sounds");
            check(Math.abs(mc.player.getDeltaMovement().x) < .001 && Math.abs(mc.player.getDeltaMovement().z) < .001,
                    "Exposure must not impart horizontal knockback");
            screenshot(mc, "exposure.png");
            stage = 9; ticks = 0;
            return;
        }
        if (stage == 9) {
            if (++ticks < 14) return;
            screenshot(mc, "exposure-motion.png");
            mc.options.screenEffectScale().set(0.0);
            stage = 10; ticks = 0;
            return;
        }
        if (stage == 10) {
            if (++ticks < 3) return;
            screenshot(mc, "exposure-disabled.png");
            mc.options.screenEffectScale().set(1.0);
            mc.options.hideGui = false;
            stage = 11; ticks = 0;
            return;
        }
        if (stage == 11) {
            if (++ticks < 3) return;
            screenshot(mc, "exposure-hud.png");
            mc.options.hideGui = true;
            operation = mc.getSingleplayerServer().submit(() -> {
                var level = mc.getSingleplayerServer().overworld();
                level.setBlock(GHOST.offset(-2, 0, 0), Blocks.WATER.defaultBlockState(), 3);
            });
            stage = 3; ticks = 0;
            return;
        }
        if (stage == 3) {
            if (++ticks < 45) return;
            check(!mc.level.getFluidState(GHOST).isEmpty() && !mc.level.getFluidState(GHOST).isSource(),
                    "Natural water must flow over the actual crystal deposit");
            check(GhostMatterExposureRenderer.intensity() == 0, "Water must extinguish the exposure overlay");
            mc.player.setXRot(60);
            screenshot(mc, "water-flow.png");
            operation = mc.getSingleplayerServer().submit(() -> {
                var level = mc.getSingleplayerServer().overworld();
                level.setBlock(GHOST.offset(-2, 0, 0), Blocks.AIR.defaultBlockState(), 3);
            });
            stage = 4; ticks = 0;
            return;
        }
        if (stage == 4) {
            if (++ticks < 170) return;
            check(mc.level.getFluidState(GHOST).isEmpty() && mc.level.getBlockEntity(GHOST) instanceof GhostMatterBlockEntity,
                    "Draining natural flow must preserve the deposit");
            // Vanilla flowing water can carry the player away; return to the now-dry deposit.
            operation = mc.getSingleplayerServer().submit(() -> {
                var player = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                player.setDeltaMovement(Vec3.ZERO);
                player.connection.teleport(4.5, 150, .7, 180, 28);
            });
            stage = 8; ticks = 0;
            return;
        }
        if (stage == 8) {
            if (++ticks < 15) return;
            check(GhostMatterExposureRenderer.intensity() > .99F, "Dry matter must restore the screen effect after returning");
            operation = mc.getSingleplayerServer().submit(() -> {
                var level = mc.getSingleplayerServer().overworld();
                level.destroyBlock(GHOST.below(), true);
            });
            stage = 5; ticks = 0;
            return;
        }
        if (stage == 5) {
            if (++ticks < 12) return;
            check(mc.level.getBlockState(GHOST).getValue(GhostMatterBlock.DISPERSED), "Support-loss state must reach the rendered client");
            var matter = (GhostMatterBlockEntity) mc.level.getBlockEntity(GHOST);
            check(matter.expiresAt() > mc.level.getGameTime() + 1100, "Client must receive the server's one-minute mist deadline");
            check(mc.player.hurtTime == 0 && hurtSounds == 0, "Dry and dispersed exposure must stay silent");
            screenshot(mc, "dispersed-exposure.png");
            operation = mc.getSingleplayerServer().submit(() -> {
                var player = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                player.connection.teleport(8.5, 152, 3.5, 135, 45);
            });
            stage = 6; ticks = 0;
            return;
        }
        if (stage == 6) {
            if (++ticks < 18) return;
            check(GhostMatterExposureRenderer.intensity() == 0, "Leaving the hazard must clear its overlay");
            var probe = new RemotePlayer(mc.level, new GameProfile(UUID.randomUUID(), "DamageProbe"));
            var ghostSource = new DamageSource(mc.level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                    .getHolderOrThrow(GhostMatterBlockEntity.DAMAGE_TYPE));
            probe.handleDamageEvent(ghostSource);
            check(probe.hurtTime == 0, "Ghost damage must not start client hurt animation");
            probe.handleDamageEvent(probe.damageSources().generic());
            check(probe.hurtTime > 0, "Ordinary damage must retain its normal hurt response");
            float originalHealth = mc.player.getHealth();
            mc.player.hurtTime = 0;
            mc.player.handleDamageEvent(ghostSource);
            mc.player.hurtTo(originalHealth - 1);
            check(mc.player.hurtTime == 0, "Local health sync must remain quiet for ghost exposure");
            mc.player.handleDamageEvent(mc.player.damageSources().generic());
            mc.player.hurtTo(originalHealth - 2);
            check(mc.player.hurtTime > 0, "Local health sync must retain the physical-hit response");
            mc.player.hurtTime = 4;
            mc.player.handleDamageEvent(ghostSource);
            mc.player.hurtTo(originalHealth - 3);
            check(mc.player.hurtTime == 4, "Exposure must preserve a preceding physical-hit animation");
            mc.player.setHealth(originalHealth);
            mc.player.hurtTime = 0;
            screenshot(mc, "outside.png");
            LogUtils.getLogger().info("GHOST_MATTER_CLIENT_PASS shader=true healthPackets=true overlapCapped=true noHurtSound=true noFlinch=true noKnockback=true naturalWater=true drain=true supportShatter=true mistDeadlineSync=true exitFade=true ordinaryDamage=true");
            stage = 7; mc.stop();
        }
    }

    private static void check(boolean ok, String message) { if (!ok) throw new IllegalStateException(message); }
    private static void screenshot(Minecraft mc, String name) throws Exception {
        var directory = Path.of(mc.gameDirectory.getAbsolutePath()).toAbsolutePath().normalize().getParent().resolve("ghost-matter-verification");
        Files.createDirectories(directory);
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) { image.writeToFile(directory.resolve(name)); }
    }
}
