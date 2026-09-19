package com.bunnyh.mousescrollfix;

import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Reports which GLFW build and which window-system backend the running game actually uses.
 *
 * <p>This matters because the fix is only meaningful on the native-Wayland path: there the
 * compositor scales one wheel notch to a fractional delta (e.g. 0.75) and vanilla Minecraft
 * misreads that fraction as "how much to scroll". Under X11/XWayland GLFW always reports +-1.0 per
 * notch, so fractional deltas cannot occur and the raw values logged by this mod would show it.
 */
public final class EnvInfo {

    private EnvInfo() {
    }

    public static String glfwVersion() {
        try {
            return GLFW.glfwGetVersionString();
        } catch (Throwable t) {
            return "unavailable (" + t.getClass().getSimpleName() + ")";
        }
    }

    /** Wayland vs X11, decided by which client library GLFW actually bound in this process. */
    public static String windowBackend() {
        boolean wayland = false;
        boolean x11 = false;
        try {
            List<String> maps = Files.readAllLines(Path.of("/proc/self/maps"));
            for (String line : maps) {
                if (line.contains("libwayland-client")) {
                    wayland = true;
                } else if (line.contains("/libX11.so")) {
                    x11 = true;
                }
            }
        } catch (Exception e) {
            return "unknown (" + e.getClass().getSimpleName() + ")";
        }
        if (wayland && x11) {
            return "wayland (libX11 also present)";
        }
        if (wayland) {
            return "wayland";
        }
        if (x11) {
            return "x11 / xwayland";
        }
        return "unknown";
    }
}
