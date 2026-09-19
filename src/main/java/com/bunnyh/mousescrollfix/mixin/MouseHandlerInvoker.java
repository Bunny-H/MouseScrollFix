package com.bunnyh.mousescrollfix.mixin;

import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Exposes the private {@code MouseHandler.onScroll} so the debug self test can drive the real
 * vanilla code path with synthetic per-notch values - no physical mouse required.
 */
@Mixin(MouseHandler.class)
public interface MouseHandlerInvoker {

    @Invoker("onScroll")
    void mousescrollfix$invokeOnScroll(long window, double xOffset, double yOffset);
}
