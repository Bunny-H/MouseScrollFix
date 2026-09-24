/*
 * Copyright (C) 2026 BunnyH
 *
 * This file is part of Mouse Scroll Fix (mousescrollfix), a Minecraft mod.
 *
 * Mouse Scroll Fix is free software: you can redistribute it and/or modify it under the terms
 * of the GNU Lesser General Public License as published by the Free Software Foundation,
 * version 3 of the License.
 *
 * Mouse Scroll Fix is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License along with
 * Mouse Scroll Fix. If not, see <https://www.gnu.org/licenses/>.
 */

package com.bunnyh.mousescrollfix;

import net.minecraft.client.Minecraft;

import java.util.Locale;

/**
 * Turns what GLFW hands over into exactly one step per physical wheel notch.
 *
 * <p>Two different things go wrong on Linux, one per window-system backend:
 *
 * <ul>
 *   <li><b>Native Wayland</b>: the compositor multiplies the notch and GLFW delivers the product, so
 *       one notch arrives as a single event with a fractional value (0.1, 0.75, 1.5, ...). Vanilla
 *       {@code MouseHandler.onScroll} does
 *       {@code accumulatedScroll += yOffset * sensitivity; int i = (int) accumulatedScroll;} and only
 *       acts when {@code i != 0}, so the fraction is either swallowed (needs several notches) or
 *       counted twice (1.5 leaves 0.5 behind). Collapsing the value to +-1 makes the accumulator
 *       consume itself completely every time.</li>
 *   <li><b>X11 / XWayland</b>: the multiplier cannot change the value - GLFW always reports +-1.0
 *       there - so the desktop emits extra events instead. With ScrollFactor 1.5 one notch becomes
 *       two events 780-950 microseconds apart, and the game steps twice for that one notch. Two
 *       events that close together cannot be two real notches (that would be over a thousand notches
 *       per second), so the duplicate is dropped ({@link #mergingActive()}, {@link X11Scaling}).</li>
 * </ul>
 *
 * <p>The multiplier cannot be undone when it is <em>below</em> 1: there the desktop swallows notches
 * before the game ever sees them, and no client-side mod can invent them back.
 */
public final class ScrollNormalizer {

    /** Time of the last event that was let through, for the duplicate window. */
    private static long lastAcceptedNanos;
    /** Time of the last event written to the debug log, for its gap column. */
    private static long lastLoggedNanos;
    private static int lastSign;
    private static long mergedEvents;
    /** Lets the self test drive values back to back without them being merged as duplicates. */
    private static volatile boolean mergeSuspended;
    /** null = not decided yet; re-decided on every config (re)load. */
    private static volatile Boolean merging;

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
        long windowMicros = ScrollFixConfig.x11MergeMs * 1000L;
        if (windowMicros > 0L && mergingActive() && !mergeSuspended && sign == lastSign) {
            long gapMicros = (System.nanoTime() - lastAcceptedNanos) / 1_000L;
            if (gapMicros < windowMicros) {
                mergedEvents++;
                log(yOffset, 0.0D, true);
                return 0.0D;
            }
        }

        lastAcceptedNanos = System.nanoTime();
        lastSign = sign;
        log(yOffset, (double) sign, false);
        return (double) sign;
    }

    /**
     * True when events that arrive together are one notch, i.e. this is an X11/XWayland session and
     * the desktop is known to duplicate wheel events (or the user forced it with {@code x11_fix}).
     * Never true on other backends: there a burst of events is always real input.
     */
    public static boolean mergingActive() {
        Boolean decided = merging;
        if (decided == null) {
            decided = decideMerging();
            merging = decided;
        }
        return decided;
    }

    private static boolean decideMerging() {
        if (ScrollFixConfig.x11MergeMs <= 0) {
            return false;
        }
        if (EnvInfo.backend() != EnvInfo.Backend.X11) {
            return false;
        }
        return switch (ScrollFixConfig.x11Fix) {
            case ON -> true;
            case OFF -> false;
            case AUTO -> X11Scaling.duplicating();
        };
    }

    /** Re-decides on the next event; called when the config is (re)loaded. */
    public static void onConfigChanged() {
        merging = null;
        X11Scaling.reset();
    }

    /** For the self test, which feeds notches instead of a real event stream. */
    public static void setMergeSuspended(boolean suspended) {
        mergeSuspended = suspended;
    }

    /** Forgets the previous event, so the next one cannot be merged with something older. */
    public static void resetTiming() {
        lastAcceptedNanos = 0L;
        lastLoggedNanos = 0L;
        lastSign = 0;
    }

    /** What this session's fix amounts to, for the status command. */
    public static String describeActions() {
        if (!ScrollFixConfig.enabled) {
            return "disabled by config";
        }
        if (EnvInfo.backend() == EnvInfo.Backend.WAYLAND) {
            return "value normalisation (one notch = one step)";
        }
        if (mergingActive()) {
            return "duplicate merging (one notch = one step, at most one per frame)";
        }
        return "nothing (no scaler detected on this backend, or merging turned off)";
    }

    public static long mergedEvents() {
        return mergedEvents;
    }

    /** True when vanilla would route the scroll to the hotbar rather than to a Screen. */
    private static boolean isInWorld() {
        Minecraft mc = Minecraft.getInstance();
        return mc.screen == null && mc.player != null && mc.getOverlay() == null;
    }

    private static void log(double raw, double normalized, boolean duplicate) {
        if (!ScrollDebugLog.isEnabled()) {
            return;
        }
        long now = System.nanoTime();
        long gapMicros = lastLoggedNanos == 0L ? -1L : (now - lastLoggedNanos) / 1_000L;
        lastLoggedNanos = now;
        Minecraft mc = Minecraft.getInstance();
        String ctx = mc.screen != null ? "screen" : (mc.player != null ? "world" : "none");
        int slotBefore = mc.player != null ? mc.player.getInventory().selected : -1;
        String dup = duplicate ? " DUPLICATE-DROPPED" : "";
        ScrollDebugLog.write(String.format(Locale.ROOT,
                "t=%d raw=%+.4f norm=%+.4f gapUs=%d ctx=%-6s slotBefore=%d%s",
                System.currentTimeMillis(), raw, normalized, gapMicros, ctx, slotBefore, dup));
    }
}
