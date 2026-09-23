package com.bunnyh.mousescrollfix;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Locale;

@Mod.EventBusSubscriber(modid = MouseScrollFix.MODID, value = Dist.CLIENT)
public final class ScrollFixClientEvents {

    private static final long AUTO_TEST_DELAY_MS = 4000L;
    /** GLFW forgets the window cursor on grab/release, so re-apply it now and then. */
    private static final int CURSOR_REAPPLY_TICKS = 200;

    private static boolean autoTestArmed;
    private static boolean autoTestDone;
    private static long inWorldSince;
    private static int ticks;

    private ScrollFixClientEvents() {
    }

    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("mousescrollfix")
                        .then(Commands.literal("test").executes(ctx -> {
                            ScrollSelfTest.runAndReport();
                            return 1;
                        }))
                        .then(Commands.literal("cursor").executes(ctx -> {
                            CursorThemeFix.reset();
                            CursorThemeFix.apply();
                            say("[mousescrollfix] cursor: " + CursorThemeFix.summary());
                            return 1;
                        }))
                        .then(Commands.literal("status").executes(ctx -> {
                            status();
                            return 1;
                        }))
                        .then(Commands.literal("hint").executes(ctx -> {
                            BackendHint.show();
                            say("[mousescrollfix] backend: " + EnvInfo.windowBackend()
                                    + " (the main-menu notice is shown automatically under X11/XWayland)");
                            return 1;
                        }))
        );
    }

    private static void status() {
        String line = String.format(Locale.ROOT,
                "[mousescrollfix] enabled=%s affect_screens=%s x11_fix=%s x11_merge_ms=%d debug_log=%s"
                        + " | duplicates merged this session=%d",
                ScrollFixConfig.enabled, ScrollFixConfig.affectScreens, ScrollFixConfig.x11Fix,
                ScrollFixConfig.x11MergeMs, ScrollFixConfig.debugLog, ScrollNormalizer.mergedEvents());
        say(line);
        say(String.format(Locale.ROOT, "[mousescrollfix] GLFW %s | backend: %s",
                EnvInfo.glfwVersion(), EnvInfo.windowBackend()));
        say("[mousescrollfix] scroll fix here: " + ScrollNormalizer.describeActions());
        say("[mousescrollfix] X11 duplicate detection: " + X11Scaling.summary());
        say("[mousescrollfix] cursor: " + CursorThemeFix.summary());
    }

    private static void say(String line) {
        MouseScrollFix.LOGGER.info(line);
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.gui != null && mc.gui.getChat() != null) {
            mc.gui.getChat().addMessage(Component.literal(line));
        }
    }

    /** The earliest moment the GLFW window exists is the first screen the game opens. */
    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        CursorThemeFix.apply();
        BackendHint.onScreenShown(event.getScreen());
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (++ticks % CURSOR_REAPPLY_TICKS == 0) {
            CursorThemeFix.apply();
        }
        if (!ScrollFixConfig.selfTestOnJoin) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            autoTestArmed = false;
            autoTestDone = false;
            inWorldSince = 0L;
            return;
        }
        if (autoTestDone) {
            return;
        }
        if (!autoTestArmed) {
            autoTestArmed = true;
            inWorldSince = Util.getMillis();
            return;
        }
        // Wait for a moment with no GUI open, so the scroll really reaches the hotbar.
        if (mc.screen != null || mc.getOverlay() != null) {
            inWorldSince = Util.getMillis();
            return;
        }
        if (Util.getMillis() - inWorldSince < AUTO_TEST_DELAY_MS) {
            return;
        }
        autoTestDone = true;
        ScrollSelfTest.runAndReport();
    }
}
