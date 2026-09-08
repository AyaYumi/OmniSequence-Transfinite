package com.atir.molecularmanipulator.mixin;

import appeng.api.networking.IGrid;
import appeng.helpers.patternprovider.PatternContainer;
import appeng.menu.implementations.PatternAccessTermMenu;
import com.atir.molecularmanipulator.integration.ae2.SegmentedPatternContainers;
import com.glodblock.github.extendedae.network.EPPNetworkHandler;
import com.glodblock.github.extendedae.network.packet.SExPatternInfo;
import com.glodblock.github.extendedae.util.Ae2Reflect;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

/**
 * Supplies ExtendedAE location metadata for terminal-only pattern segments.
 */
@Mixin(value = PatternAccessTermMenu.class, priority = 900, remap = false)
public abstract class PatternAccessTermMenuMixin {
    @Shadow
    private Map<PatternContainer, ?> diList;

    @Inject(method = "sendFullUpdate", at = @At("TAIL"))
    private void molecularmanipulator$sendSegmentLocationsToExtendedAe(IGrid grid, CallbackInfo callback) {
        var player = ((PatternAccessTermMenu) (Object) this).getPlayer();
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }

        for (Object tracker : diList.values()) {
            PatternContainer container = Ae2Reflect.getContainer(tracker);
            PatternContainer physicalHost = SegmentedPatternContainers.unwrap(container);
            if (physicalHost == container || !(physicalHost instanceof BlockEntity blockEntity)) {
                continue;
            }
            var level = blockEntity.getLevel();
            if (level != null) {
                EPPNetworkHandler.INSTANCE.sendTo(new SExPatternInfo(
                        Ae2Reflect.getContainerID(tracker), blockEntity.getBlockPos(), level.dimension()),
                        serverPlayer);
            }
        }
    }
}
