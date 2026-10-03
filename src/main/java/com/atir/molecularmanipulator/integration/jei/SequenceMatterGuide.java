package com.atir.molecularmanipulator.integration.jei;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.GenericStack;
import com.atir.molecularmanipulator.MolecularManipulator;
import com.atir.molecularmanipulator.registry.ModFluids;
import com.mojang.blaze3d.platform.InputConstants;
import guideme.PageAnchor;
import guideme.internal.GuideMEClient;
import guideme.internal.GuideRegistry;
import guideme.internal.hotkey.OpenGuideHotkey;
import guideme.internal.screen.GuideScreen;
import mezz.jei.api.forge.ForgeTypes;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fluids.FluidStack;

/** Adds the existing guide hotkey to native JEI fluids and AE fluid display stacks. */
public final class SequenceMatterGuide {
    private static IJeiRuntime runtime;
    private static boolean registered;
    private static int heldTicks;
    private SequenceMatterGuide() {}

    public static void setRuntime(IJeiRuntime value) {
        runtime = value;
        if (!registered && ModList.get().isLoaded("guideme")) {
            MinecraftForge.EVENT_BUS.addListener(SequenceMatterGuide::tick);
            registered = true;
        }
    }

    private static boolean matches(FluidStack stack) {
        return stack != null && !stack.isEmpty() && stack.getFluid().isSame(ModFluids.SEQUENCE_MATTER.get());
    }

    private static boolean hovered(Minecraft mc) {
        if (matches(runtime.getIngredientListOverlay().getIngredientUnderMouse(ForgeTypes.FLUID_STACK))
                || matches(runtime.getBookmarkOverlay().getIngredientUnderMouse(ForgeTypes.FLUID_STACK))
                || runtime.getRecipesGui().getIngredientUnderMouse(ForgeTypes.FLUID_STACK)
                        .filter(SequenceMatterGuide::matches).isPresent()) return true;
        if (mc.screen instanceof AbstractContainerScreen<?> container) {
            var slot = container.getSlotUnderMouse();
            var stack = slot == null ? null : GenericStack.unwrapItemStack(slot.getItem());
            if (stack != null && stack.what() instanceof AEFluidKey key
                    && key.getFluid().isSame(ModFluids.SEQUENCE_MATTER.get())) return true;
        }
        double x = mc.mouseHandler.xpos() * mc.getWindow().getGuiScaledWidth() / mc.getWindow().getScreenWidth();
        double y = mc.mouseHandler.ypos() * mc.getWindow().getGuiScaledHeight() / mc.getWindow().getScreenHeight();
        return runtime.getScreenHelper().getClickableIngredientUnderMouse(mc.screen, x, y)
                .anyMatch(ingredient -> ingredient.getTypedIngredient().getIngredient(ForgeTypes.FLUID_STACK)
                        .filter(SequenceMatterGuide::matches).isPresent());
    }

    private static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var mc = Minecraft.getInstance();
        var key = OpenGuideHotkey.getHotkey().getKey();
        updateHotkey(mc, key.getType() == InputConstants.Type.KEYSYM
                && InputConstants.isKeyDown(mc.getWindow().getWindow(), key.getValue()));
    }

    private static void updateHotkey(Minecraft mc, boolean pressed) {
        if (runtime == null || mc.screen == null || mc.screen instanceof GuideScreen
                || !pressed
                || !hovered(mc)) {
            heldTicks = 0;
            return;
        }
        if (++heldTicks == 10) open();
    }

    private static boolean open() {
        var page = MolecularManipulator.id("items-blocks-machines/singularity_matter_duplication.md");
        var guide = GuideRegistry.getAll().stream().filter(value -> value.pageExists(page)).findFirst();
        return guide.isPresent() && GuideMEClient.openGuideAtAnchor(guide.get(), PageAnchor.page(page));
    }
}
