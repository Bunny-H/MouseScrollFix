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

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.event.config.ModConfigEvent;

/**
 * Client config. Values are mirrored into plain static fields on load/reload so the hot path
 * (which runs on the render thread for every wheel event) never touches the config system.
 */
public final class ScrollFixConfig {

    public static final ForgeConfigSpec SPEC;

    /** What to do about the X11/XWayland path, where the desktop duplicates events instead of scaling them. */
    public enum X11Fix {
        /** Merge only when KDE's configuration says some device has a ScrollFactor above 1. */
        AUTO,
        /** Merge on any X11/XWayland session. */
        ON,
        /** Never merge. */
        OFF
    }

    private static final ForgeConfigSpec.BooleanValue ENABLED;
    private static final ForgeConfigSpec.BooleanValue AFFECT_SCREENS;
    private static final ForgeConfigSpec.EnumValue<X11Fix> X11_FIX;
    private static final ForgeConfigSpec.IntValue X11_MERGE_MS;
    private static final ForgeConfigSpec.BooleanValue DEBUG_LOG;
    private static final ForgeConfigSpec.BooleanValue SELF_TEST_ON_JOIN;
    private static final ForgeConfigSpec.BooleanValue BACKEND_HINT;
    private static final ForgeConfigSpec.BooleanValue FIX_CURSOR_THEME;
    private static final ForgeConfigSpec.ConfigValue<String> CURSOR_THEME;
    private static final ForgeConfigSpec.IntValue CURSOR_SIZE;

    /** Mirrors of the config values, pre-seeded with the spec defaults. */
    public static volatile boolean enabled = true;
    public static volatile boolean affectScreens = true;
    public static volatile X11Fix x11Fix = X11Fix.AUTO;
    public static volatile int x11MergeMs = 2;
    public static volatile boolean debugLog = false;
    public static volatile boolean selfTestOnJoin = false;
    public static volatile boolean backendHint = true;
    public static volatile boolean fixCursorTheme = false;
    public static volatile String cursorTheme = "";
    public static volatile int cursorSize = 0;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();

        b.comment(
                "Normalizes mouse wheel scroll so that ONE physical wheel notch is always ONE step.",
                "On Linux a desktop compositor (e.g. KWin's per-device 'scroll speed') multiplies the",
                "value that reaches the game, and vanilla Minecraft treats that multiplier as 'how many",
                "slots to scroll' - so one notch can move 0, 1 or several slots depending on a system",
                "setting. Windows never has this problem because its wheel events are always +-1."
        );
        ENABLED = b.comment("Master switch. false = completely vanilla behaviour.")
                .define("enabled", true);

        AFFECT_SCREENS = b.comment(
                        "true  = normalize everywhere: hotbar AND every scrollable GUI (inventory, JEI, creative tabs).",
                        "false = only normalize the in-world hotbar; GUIs keep following the system scroll speed.")
                .define("affect_screens", true);

        b.comment(
                "",
                "--- X11 / XWayland ---",
                "On X11 (including XWayland) a scroll-speed multiplier cannot scale the VALUE of a scroll",
                "event: the protocol has no fractional wheel deltas and GLFW always reports +-1.0 there.",
                "The desktop emits extra events instead. Measured with KWin and ScrollFactor 1.5: one",
                "physical notch arrives as two events 780-950 microseconds apart, so the game moves two",
                "slots for one notch. Two events that close cannot be two real notches (that would be over",
                "a thousand notches per second, and they always land in the same frame's event batch), so",
                "the duplicate is recognised and dropped.",
                "A multiplier BELOW 1 cannot be undone: there the desktop swallows notches before the game",
                "sees anything, and that information is gone."
        );
        X11_FIX = b.comment(
                        "auto = merge duplicates only when this desktop is known to duplicate wheel events,",
                        "       i.e. when KDE's kcminputrc contains a ScrollFactor above 1 (which also means",
                        "       the mod cannot get in the way of systems it has nothing to fix on),",
                        "on   = always merge on X11/XWayland,",
                        "off  = never merge; X11/XWayland then behaves as if this mod were not installed.")
                .defineEnum("x11_fix", X11Fix.AUTO);

        X11_MERGE_MS = b.comment(
                        "Two scroll events of the same direction closer together than this are one notch.",
                        "The duplicates measured on this machine are 0.8 ms apart while real notches are",
                        "tens of milliseconds apart even when the wheel is spun hard. In practice this",
                        "means at most one step per rendered frame.",
                        "A wheel with detents cannot clear two notches inside one frame, so real input is not",
                        "affected; a free-spinning wheel or a touchpad (which can report a click every 1-3 ms)",
                        "can reach the limit. 0 turns the merging off.")
                .defineInRange("x11_merge_ms", 2, 0, 100);

        DEBUG_LOG = b.comment(
                        "Write every scroll event (raw value, normalized value, resulting hotbar slot) to",
                        "logs/mousescrollfix-debug.log. For verifying the fix; leave off for normal play.")
                .define("debug_log", false);

        SELF_TEST_ON_JOIN = b.comment(
                        "Run the automated per-notch self test once, a few seconds after joining a world.",
                        "Results go to the log file and to chat. For diagnosing; leave off for normal play.")
                .define("self_test_on_join", false);

        BACKEND_HINT = b.comment(
                        "If the game is NOT running on native Wayland (i.e. under X11 or XWayland), show a",
                        "short notice on the main menu saying which half of the problem can be fixed there.",
                        "Only a positively detected Linux X11/XWayland backend triggers it; on Windows and",
                        "macOS the backend cannot be detected, so nothing is ever shown there.")
                .define("backend_hint", true);

        b.comment(
                "",
                "--- mouse pointer ---",
                "On native Wayland the pointer over the window is chosen by GLFW, which reads the theme",
                "name from the XCURSOR_THEME environment variable - something desktop environments do not",
                "export to applications. The game therefore shows a generic fallback arrow instead of the",
                "cursor theme you configured.",
                "Minecraft itself never sets a cursor, so this section lets the mod do it: the desktop's own",
                "cursor theme is read from KDE's kcminputrc / GTK's settings.ini, the matching arrow image is",
                "loaded from that theme's Xcursor files and handed to GLFW directly. No launcher settings,",
                "no environment variables; whatever theme the desktop uses is what you get.",
                "OFF by default: this is the only thing in the game that calls glfwSetCursor, and on a",
                "Wayland session driven by mods that move GLFW's event polling onto another thread (Ixeris)",
                "that call has been observed to crash the game - see FINDINGS.md, section 9.",
                "Everything else this mod does (the scroll fix) works with the default pointer."
        );
        FIX_CURSOR_THEME = b.comment(
                        "false (default) = leave GLFW's own pointer alone.",
                        "true = take the mouse pointer from the desktop's cursor theme on Wayland.",
                        "Has no effect on X11 or on non-Linux systems, where the desktop theme already applies.")
                .define("fix_cursor_theme", false);

        CURSOR_THEME = b.comment(
                        "Only used when fix_cursor_theme = true.",
                        "Leave empty to use whatever cursor theme the desktop is configured with.",
                        "Set a theme name (a directory under /usr/share/icons) only to override it, e.g. when",
                        "the desktop is not one this mod knows how to read the setting from.")
                .define("cursor_theme", "");

        CURSOR_SIZE = b.comment(
                        "Only used when fix_cursor_theme = true.",
                        "Cursor size in logical pixels; 0 = take the desktop's configured size (24 if unset).",
                        "This is the same number the desktop's own cursor settings use.")
                .defineInRange("cursor_size", 0, 0, 256);

        SPEC = b.build();
    }

    private ScrollFixConfig() {
    }

    /**
     * Changes the pointer switch from the settings screen: writes the value, mirrors it and saves the
     * file, then drops the cursor so the change shows up without restarting the game.
     */
    public static void setFixCursorTheme(boolean value) {
        FIX_CURSOR_THEME.set(value);
        fixCursorTheme = value;
        SPEC.save();
        CursorThemeFix.reset();
        MouseScrollFix.LOGGER.info("[mousescrollfix] fix_cursor_theme set to {}", value);
    }

    public static void onConfigEvent(ModConfigEvent event) {
        if (event.getConfig().getSpec() != SPEC) {
            return;
        }
        try {
            enabled = ENABLED.get();
            affectScreens = AFFECT_SCREENS.get();
            x11Fix = X11_FIX.get();
            x11MergeMs = X11_MERGE_MS.get();
            debugLog = DEBUG_LOG.get();
            selfTestOnJoin = SELF_TEST_ON_JOIN.get();
            backendHint = BACKEND_HINT.get();
            fixCursorTheme = FIX_CURSOR_THEME.get();
            cursorTheme = CURSOR_THEME.get();
            cursorSize = CURSOR_SIZE.get();
            // The merge decision depends on the config, so it has to be taken again.
            ScrollNormalizer.onConfigChanged();
            // A config change may have turned the cursor fix back on, or changed the theme name.
            CursorThemeFix.reset();
            MouseScrollFix.LOGGER.info(
                    "[mousescrollfix] config loaded: enabled={}, affect_screens={}, x11_fix={}, x11_merge_ms={}, debug_log={}",
                    enabled, affectScreens, x11Fix, x11MergeMs, debugLog);
            MouseScrollFix.LOGGER.info("[mousescrollfix] GLFW {} | window backend: {}",
                    EnvInfo.glfwVersion(), EnvInfo.windowBackend());
            MouseScrollFix.LOGGER.info("[mousescrollfix] scroll fix in this session: {} | X11 detection: {}",
                    ScrollNormalizer.describeActions(), X11Scaling.summary());
            ScrollDebugLog.writeForced("# GLFW " + EnvInfo.glfwVersion()
                    + " | window backend: " + EnvInfo.windowBackend()
                    + " | scroll fix: " + ScrollNormalizer.describeActions()
                    + " | X11 detection: " + X11Scaling.summary());
        } catch (IllegalStateException e) {
            // Config not loaded yet - the static defaults above are the spec defaults anyway.
            MouseScrollFix.LOGGER.warn("[mousescrollfix] config not loaded yet, using defaults");
        }
    }
}
