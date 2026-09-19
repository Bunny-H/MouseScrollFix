package com.bunnyh.mousescrollfix;

import net.minecraft.client.Minecraft;

import java.util.Locale;

/**
 * Turns an arbitrary GLFW scroll delta into a plain +-1 "one notch" delta.
 *
 * <p>Vanilla {@code MouseHandler.onScroll} does
 * {@code accumulatedScroll += yOffset * sensitivity; int i = (int) accumulatedScroll;}
 * and only acts when {@code i != 0}. When the compositor scales a notch down to e.g. 0.75 the
 * accumulator needs two notches before reaching 1.0 (and the leftover fraction makes it overshoot
 * later); when it scales a notch up to 1.5 the accumulator jumps two slots at once. Collapsing
 * every event to exactly +-1 makes the accumulator consume itself completely every time, so there
 * is never a leftover and a notch is always exactly one step - which is what Windows does naturally,
 * because its wheel events are always +-1.
 */
public final class ScrollNormalizer {

    private static long lastEventNanos;
    private static int lastSign;
    private static long suppressedEvents;

    private ScrollNormalizer() {
    }

    public static double normalize(double yOffset) {
        if (yOffset == 0.0D) {
            // Horizontal-only event (or a macOS-style x-only scroll): nothing to normalize.
            return 0.0D;
        }
        if (!ScrollFixConfig.enabled) {
            return yOffset;
        }
        if (!ScrollFixConfig.affectScreens && !isInWorld()) {
            return yOffset;
        }

        int sign = yOffset > 0.0D ? 1 : -1;
        long now = System.nanoTime();

        int windowMs = ScrollFixConfig.dedupeWindowMs;
        if (windowMs > 0 && sign == lastSign) {
            long gapMs = (now - lastEventNanos) / 1_000_000L;
            if (gapMs < windowMs) {
                suppressedEvents++;
                log(yOffset, 0.0D, true, gapMs);
                return 0.0D;
            }
        }

        lastEventNanos = now;
        lastSign = sign;
        log(yOffset, (double) sign, false, -1L);
        return (double) sign;
    }

    /** True when vanilla would route the scroll to the hotbar rather than to a Screen. */
    private static boolean isInWorld() {
        Minecraft mc = Minecraft.getInstance();
        return mc.screen == null && mc.player != null && mc.getOverlay() == null;
    }

    public static long suppressedEvents() {
        return suppressedEvents;
    }

    private static void log(double raw, double normalized, boolean duplicate, long gapMs) {
        if (!ScrollDebugLog.isEnabled()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        String ctx = mc.screen != null ? "screen" : (mc.player != null ? "world" : "none");
        int slotBefore = mc.player != null ? mc.player.getInventory().selected : -1;
        String dup = duplicate ? " DUP-DROPPED" : "";
        ScrollDebugLog.write(String.format(Locale.ROOT,
                "raw=%+.4f norm=%+.4f gapMs=%d ctx=%-6s slotBefore=%d%s",
                raw, normalized, gapMs, ctx, slotBefore, dup));
    }
}
