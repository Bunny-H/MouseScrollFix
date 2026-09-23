package com.bunnyh.mousescrollfix;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.event.config.ModConfigEvent;

/**
 * Client config. Values are mirrored into plain static fields on load/reload so the hot path
 * (which runs on the render thread for every wheel event) never touches the config system.
 */
public final class ScrollFixConfig {

    public static final ForgeConfigSpec SPEC;

    private static final ForgeConfigSpec.BooleanValue ENABLED;
    private static final ForgeConfigSpec.BooleanValue AFFECT_SCREENS;
    private static final ForgeConfigSpec.IntValue DEDUPE_WINDOW_MS;
    private static final ForgeConfigSpec.BooleanValue DEBUG_LOG;
    private static final ForgeConfigSpec.BooleanValue SELF_TEST_ON_JOIN;
    private static final ForgeConfigSpec.BooleanValue BACKEND_HINT;
    private static final ForgeConfigSpec.BooleanValue FIX_CURSOR_THEME;
    private static final ForgeConfigSpec.ConfigValue<String> CURSOR_THEME;
    private static final ForgeConfigSpec.IntValue CURSOR_SIZE;

    /** Mirrors of the config values, pre-seeded with the spec defaults. */
    public static volatile boolean enabled = true;
    public static volatile boolean affectScreens = true;
    public static volatile int dedupeWindowMs = 0;
    public static volatile boolean debugLog = false;
    public static volatile boolean selfTestOnJoin = false;
    public static volatile boolean backendHint = true;
    public static volatile boolean fixCursorTheme = true;
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

        DEDUPE_WINDOW_MS = b.comment(
                        "Duplicate-event guard. If one physical wheel notch is reported as two GLFW scroll",
                        "events within this many milliseconds, the second one is dropped. 0 disables the guard.",
                        "Measured on this machine: GLFW 3.4 emits exactly one event per notch, so 0 is correct",
                        "and is the default. Only raise this if one notch still moves two slots.")
                .defineInRange("dedupe_window_ms", 0, 0, 500);

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
                        "short notice on the main menu saying the fix does not work well there.",
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
                "no environment variables; whatever theme the desktop uses is what you get."
        );
        FIX_CURSOR_THEME = b.comment(
                        "Take the mouse pointer from the desktop's cursor theme on Wayland.",
                        "Has no effect on X11 or on non-Linux systems, where the desktop theme already applies.")
                .define("fix_cursor_theme", true);

        CURSOR_THEME = b.comment(
                        "Leave empty to use whatever cursor theme the desktop is configured with.",
                        "Set a theme name (a directory under /usr/share/icons) only to override it, e.g. when",
                        "the desktop is not one this mod knows how to read the setting from.")
                .define("cursor_theme", "");

        CURSOR_SIZE = b.comment(
                        "Cursor size in logical pixels; 0 = take the desktop's configured size (24 if unset).",
                        "This is the same number the desktop's own cursor settings use.")
                .defineInRange("cursor_size", 0, 0, 256);

        SPEC = b.build();
    }

    private ScrollFixConfig() {
    }

    public static void onConfigEvent(ModConfigEvent event) {
        if (event.getConfig().getSpec() != SPEC) {
            return;
        }
        try {
            enabled = ENABLED.get();
            affectScreens = AFFECT_SCREENS.get();
            dedupeWindowMs = DEDUPE_WINDOW_MS.get();
            debugLog = DEBUG_LOG.get();
            selfTestOnJoin = SELF_TEST_ON_JOIN.get();
            backendHint = BACKEND_HINT.get();
            fixCursorTheme = FIX_CURSOR_THEME.get();
            cursorTheme = CURSOR_THEME.get();
            cursorSize = CURSOR_SIZE.get();
            // A config change may have turned the cursor fix back on, or changed the theme name.
            CursorThemeFix.reset();
            MouseScrollFix.LOGGER.info(
                    "[mousescrollfix] config loaded: enabled={}, affect_screens={}, dedupe_window_ms={}, debug_log={}",
                    enabled, affectScreens, dedupeWindowMs, debugLog);
            MouseScrollFix.LOGGER.info("[mousescrollfix] GLFW {} | window backend: {}",
                    EnvInfo.glfwVersion(), EnvInfo.windowBackend());
            ScrollDebugLog.writeForced("# GLFW " + EnvInfo.glfwVersion()
                    + " | window backend: " + EnvInfo.windowBackend());
        } catch (IllegalStateException e) {
            // Config not loaded yet - the static defaults above are the spec defaults anyway.
            MouseScrollFix.LOGGER.warn("[mousescrollfix] config not loaded yet, using defaults");
        }
    }
}
