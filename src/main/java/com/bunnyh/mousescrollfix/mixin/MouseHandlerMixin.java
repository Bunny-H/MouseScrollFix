package com.bunnyh.mousescrollfix.mixin;

import com.bunnyh.mousescrollfix.ScrollNormalizer;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(MouseHandler.class)
public class MouseHandlerMixin {

    /**
     * Replaces the raw {@code yOffset} argument with its sign before vanilla sees it.
     *
     * <p>Everything downstream (accumulator, direction-change reset, spectator flight speed,
     * ForgeHooksClient.onMouseScroll, swapPaint, Screen.mouseScrolled) is left untouched, so mods
     * that hook scrolling keep working - they simply receive +-1 per notch like they do on Windows.
     *
     * <p>{@code ordinal = 1} with {@code argsOnly = true} selects the second double argument, which
     * is {@code yOffset} (the first is {@code xOffset}, and {@code this}/{@code long} are filtered
     * out by type).
     */
    @ModifyVariable(method = "onScroll(JDD)V", at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private double mousescrollfix$normalizeScroll(double yOffset) {
        return ScrollNormalizer.normalize(yOffset);
    }
}
