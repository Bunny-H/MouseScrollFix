package com.bunnyh.mousescrollfix;

import com.bunnyh.mousescrollfix.mixin.MouseHandlerInvoker;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Drives the real vanilla {@code MouseHandler.onScroll} with synthetic per-notch values.
 *
 * <p>This is the part of the verification that needs no physical mouse: whatever GLFW delivers for
 * one wheel notch (0.1, 0.75, 1.5, ... depending on the compositor's scroll-speed multiplier) is fed
 * straight into the vanilla method, and the number of hotbar slots it moved is recorded. With the
 * fix active every single value must move exactly one slot.
 */
public final class ScrollSelfTest {

    private static final double[] NOTCH_VALUES = {0.1D, 0.75D, 1.0D, 1.5D, 2.0D};
    private static final int REPEATS = 4;

    private ScrollSelfTest() {
    }

    public static List<String> run() {
        List<String> out = new ArrayList<>();
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) {
            out.add("[mousescrollfix] self test skipped: not in a world");
            return out;
        }
        if (mc.screen != null) {
            out.add("[mousescrollfix] self test skipped: close the open screen first");
            return out;
        }

        MouseHandlerInvoker invoker = (MouseHandlerInvoker) mc.mouseHandler;
        long window = mc.getWindow().getWindow();

        StringBuilder header = new StringBuilder(String.format(Locale.ROOT, "%-9s", "yOffset"));
        for (int i = 1; i <= REPEATS; i++) {
            header.append(String.format(Locale.ROOT, "%7s", "notch" + i));
        }

        out.add("=== mousescrollfix self test ===");
        out.add("GLFW             : " + EnvInfo.glfwVersion());
        out.add("window backend   : " + EnvInfo.windowBackend());
        out.add(String.format(Locale.ROOT,
                "fix=%s  affect_screens=%s  dedupe_window_ms=%d",
                ScrollFixConfig.enabled, ScrollFixConfig.affectScreens, ScrollFixConfig.dedupeWindowMs));
        out.add("each cell = hotbar slots moved by ONE synthetic scroll event of that yOffset");
        out.add(header.toString());

        boolean allExactlyOne = true;
        for (double value : NOTCH_VALUES) {
            StringBuilder row = new StringBuilder(String.format(Locale.ROOT, "%+-9.2f", value));
            for (int i = 0; i < REPEATS; i++) {
                int before = mc.player.getInventory().selected;
                invoker.mousescrollfix$invokeOnScroll(window, 0.0D, value);
                int after = mc.player.getInventory().selected;
                int moved = wrapDelta(before, after);
                if (moved != -1) {
                    allExactlyOne = false;
                }
                row.append(String.format(Locale.ROOT, "%7d", moved));
            }
            out.add(row.toString());
        }

        out.add("(positive yOffset always walks towards lower slot indices, so -1 == one slot)");
        if (ScrollFixConfig.enabled) {
            out.add(allExactlyOne
                    ? "RESULT: PASS - every simulated notch moved exactly 1 slot"
                    : "RESULT: FAIL - some notches moved 0 or more than 1 slot");
        } else {
            out.add("RESULT: fix is DISABLED - the numbers above are the broken behaviour (0 = notch swallowed)");
        }
        return out;
    }

    /** Emits the report to the debug log, the game log and chat. */
    public static void runAndReport() {
        List<String> lines = run();
        for (String line : lines) {
            ScrollDebugLog.writeForced(line);
            MouseScrollFix.LOGGER.info("[mousescrollfix] {}", line);
        }
        ScrollDebugLog.flush();

        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.gui != null && mc.gui.getChat() != null) {
            for (String line : lines) {
                mc.gui.getChat().addMessage(Component.literal(line));
            }
        }
    }

    /** Signed number of slots moved, taking the 9-slot hotbar wrap into account. */
    private static int wrapDelta(int before, int after) {
        int d = after - before;
        if (d > 4) {
            d -= 9;
        } else if (d < -4) {
            d += 9;
        }
        return d;
    }
}
