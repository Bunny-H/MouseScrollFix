package com.bunnyh.mousescrollfix;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;

/**
 * Tells the player, on the main menu, what this mod can and cannot do on X11/XWayland.
 *
 * <p>Only a positively detected X11/XWayland backend triggers the notice. There the desktop cannot
 * scale the value of a wheel event, so it emits extra events instead - that half of the problem this
 * mod repairs (see {@link ScrollNormalizer}). A multiplier below 1 destroys notches before the game
 * sees them, and no client-side mod can bring those back, so the notice says so. On Windows and macOS
 * nothing is shown: there is no compositor scroll multiplier to fight, and the backend is not
 * detectable there anyway (so a false alarm is impossible by construction).
 *
 * <p>Shown at most once per game launch; {@code /mousescrollfix hint} shows it on demand.
 */
public final class BackendHint {

    private static final String TITLE_KEY = "mousescrollfix.backend_hint.title";
    private static final String MESSAGE_KEY = "mousescrollfix.backend_hint.message";

    private static boolean shown;

    private BackendHint() {
    }

    /** Called for every screen that finishes initialising; only the first main menu can trigger it. */
    public static void onScreenShown(Screen screen) {
        if (shown || !ScrollFixConfig.backendHint || !(screen instanceof TitleScreen)) {
            return;
        }
        if (EnvInfo.backend() != EnvInfo.Backend.X11) {
            return;
        }
        shown = true;
        show();
    }

    /** Shows the notice regardless of config and of whether it was already shown. */
    public static void show() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            return;
        }
        ToastComponent toasts = mc.getToasts();
        if (toasts == null) {
            return;
        }
        String backend = EnvInfo.windowBackend();
        toasts.addToast(SystemToast.multiline(mc, SystemToast.SystemToastIds.PERIODIC_NOTIFICATION,
                Component.translatable(TITLE_KEY),
                Component.translatable(MESSAGE_KEY)));
        MouseScrollFix.LOGGER.info(
                "[mousescrollfix] backend hint shown: the game is not on native Wayland (backend: {}, scroll fix: {})",
                backend, ScrollNormalizer.describeActions());
    }
}
