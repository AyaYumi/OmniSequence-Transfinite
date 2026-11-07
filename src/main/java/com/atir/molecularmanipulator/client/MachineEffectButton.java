package com.atir.molecularmanipulator.client;

import com.atir.molecularmanipulator.blockentity.MachineEffectSettings;
import com.atir.molecularmanipulator.config.ModConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/** Small AE2 action button: three explicit tiers; Shift-activation clears the machine override. */
public final class MachineEffectButton extends ResponsiveAE2Button {
    private static final String PREFIX = "gui.molecularmanipulator.effects.";
    private final IntSupplier override;
    private final IntConsumer request;
    private int displayedOverride = Integer.MIN_VALUE;
    private int displayedLevel = Integer.MIN_VALUE;

    public MachineEffectButton(int x, int y, int width, IntSupplier override, IntConsumer request) {
        super(x, y, width, 18, Component.empty(), button -> {});
        this.override = override;
        this.request = request;
        refresh();
    }

    @Override
    public void onPress() {
        request.accept(Screen.hasShiftDown() ? MachineEffectSettings.INHERIT
                : MachineEffectSettings.nextLevel(override.getAsInt(), ModConfig.DYNAMIC_EFFECT_LEVEL.get()));
    }

    /** Reads the authoritative menu field; a rejected request never produces a false local state. */
    public void refresh() {
        int currentOverride = override.getAsInt();
        int currentLevel = MachineEffectSettings.resolve(currentOverride, ModConfig.DYNAMIC_EFFECT_LEVEL.get());
        if (currentOverride == displayedOverride && currentLevel == displayedLevel) return;
        displayedOverride = currentOverride;
        displayedLevel = currentLevel;
        var level = Component.translatable(PREFIX + "level." + currentLevel);
        setMessage(Component.translatable(PREFIX + "button", level));
        var source = Component.translatable(PREFIX + (currentOverride == MachineEffectSettings.INHERIT
                ? "inherited" : "overridden"));
        setTooltip(Tooltip.create(source.copy().append("\n").append(Component.translatable(PREFIX + "tooltip"))));
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        refresh();
        super.renderWidget(graphics, mouseX, mouseY, partialTick);
    }
}
