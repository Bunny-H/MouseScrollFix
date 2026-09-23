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

    /** Which client library GLFW actually bound in this process. */
    public enum Backend {
        /** Native Wayland: the compositor scales a notch to a fraction, the only case this mod acts on. */
        WAYLAND,
        /** X11 or XWayland: every notch arrives as +-1.0, so there is nothing to normalize. */
        X11,
        /** Not determined (non-Linux, unreadable /proc, ...). Never guessed at; callers stay silent. */
        UNKNOWN
    }

    /** One read of /proc/self/maps, plus why it failed if it did. */
    private record Loaded(boolean wayland, boolean x11, String error) {
    }

    private EnvInfo() {
    }

    public static String glfwVersion() {
        try {
            return GLFW.glfwGetVersionString();
        } catch (Throwable t) {
            return "unavailable (" + t.getClass().getSimpleName() + ")";
        }
    }

    public static Backend backend() {
        Loaded loaded = load();
        if (loaded.wayland()) {
            return Backend.WAYLAND;
        }
        if (loaded.x11()) {
            return Backend.X11;
        }
        return Backend.UNKNOWN;
    }

    /** Wayland vs X11, decided by which client library GLFW actually bound in this process. */
    public static String windowBackend() {
        Loaded loaded = load();
        if (loaded.error() != null) {
            return "unknown (" + loaded.error() + ")";
        }
        if (loaded.wayland() && loaded.x11()) {
            return "wayland (libX11 also present)";
        }
        if (loaded.wayland()) {
            return "wayland";
        }
        if (loaded.x11()) {
            return "x11 / xwayland";
        }
        return "unknown";
    }

    /**
     * A loaded Wayland client library wins when both are mapped: /libX11.so can be pulled in by
     * anything, while GLFW only binds libwayland-client after it really opened a Wayland display.
     */
    private static Loaded load() {
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
            return new Loaded(false, false, e.getClass().getSimpleName());
        }
        return new Loaded(wayland, x11, null);
    }
}
