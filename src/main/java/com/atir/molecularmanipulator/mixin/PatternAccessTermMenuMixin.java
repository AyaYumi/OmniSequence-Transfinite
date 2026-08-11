package com.atir.molecularmanipulator.mixin;

import appeng.api.networking.IGrid;
import appeng.helpers.patternprovider.PatternContainer;
import appeng.menu.implementations.PatternAccessTermMenu;
import com.atir.molecularmanipulator.integration.ae2.SegmentedPatternContainerHost;
import com.atir.molecularmanipulator.integration.ae2.SegmentedPatternContainers;
import com.glodblock.github.extendedae.network.EPPNetworkHandler;
import com.glodblock.github.extendedae.network.packet.SExPatternInfo;
import com.glodblock.github.extendedae.util.Ae2Reflect;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Expands large OmniSequence pattern inventories into terminal-only segments.
 * The physical grid node and crafting provider remain unchanged.
 */
@Mixin(value = PatternAccessTermMenu.class, priority = 900, remap = false)
public abstract class PatternAccessTermMenuMixin {
    @Shadow
    private Map<PatternContainer, ?> diList;

    @ModifyExpressionValue(
            method = { "visitPatternProviderHosts", "sendFullUpdate" },
            at = @At(value = "INVOKE",
                    target = "Lappeng/api/networking/IGrid;getActiveMachines(Ljava/lang/Class;)Ljava/util/Set;"),
            require = 2)
    @SuppressWarnings({ "rawtypes", "unchecked" })
    private Set molecularmanipulator$expandSegmentedPatternContainers(Set machines) {
        boolean hasSegmentedHost = false;
        for (Object machine : machines) {
            if (machine instanceof SegmentedPatternContainerHost) {
                hasSegmentedHost = true;
                break;
            }
        }
        if (!hasSegmentedHost) {
            return machines;
        }

        var expanded = new LinkedHashSet();
        for (Object machine : machines) {
            if (machine instanceof SegmentedPatternContainerHost segmentedHost) {
                expanded.addAll(segmentedHost.molecularmanipulator$getTerminalPatternContainers());
            } else {
                expanded.add(machine);
            }
        }
        return expanded;
    }

    @Inject(method = "sendFullUpdate", at = @At("TAIL"))
    private void molecularmanipulator$sendSegmentLocationsToExtendedAe(IGrid grid,
            CallbackInfo callback) {
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
