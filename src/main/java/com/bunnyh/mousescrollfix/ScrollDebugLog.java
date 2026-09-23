package com.bunnyh.mousescrollfix;

import net.minecraftforge.fml.loading.FMLPaths;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;

/** Appends scroll traces to {@code logs/mousescrollfix-debug.log}. */
public final class ScrollDebugLog {

    private static BufferedWriter writer;
    private static boolean disabled;

    private ScrollDebugLog() {
    }

    public static boolean isEnabled() {
        return ScrollFixConfig.debugLog && !disabled;
    }

    private static synchronized BufferedWriter writer() {
        if (writer != null || disabled) {
            return writer;
        }
        try {
            Path logs = FMLPaths.GAMEDIR.get().resolve("logs");
            Files.createDirectories(logs);
            Path file = logs.resolve("mousescrollfix-debug.log");
            boolean fresh = !Files.exists(file);
            writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            if (fresh) {
                writer.write("# mousescrollfix debug log\n");
                writer.write("# t       = milliseconds since the epoch, so a line can be matched against\n");
                writer.write("#           the timestamps printed by tools/wheel.py\n");
                writer.write("# raw     = yOffset exactly as GLFW delivered it (already scaled by the compositor)\n");
                writer.write("# norm    = value after the fix (what vanilla MouseHandler.onScroll now sees)\n");
                writer.write("# gapUs   = microseconds since the previous event (-1 = first one in this session)\n");
                writer.write("# ctx     = world = hotbar path, screen = a GUI consumed it, none = overlay/no player\n");
                writer.write("# slotBefore = hotbar slot index before this event was applied\n");
                writer.write("# DUPLICATE-DROPPED = the second half of a duplicated notch (X11/XWayland), not a step\n");
            }
            writer.write("# ---- session started " + LocalDateTime.now() + " ----\n");
            writer.flush();
        } catch (IOException e) {
            disabled = true;
            MouseScrollFix.LOGGER.warn("[mousescrollfix] cannot open debug log, disabling it", e);
        }
        return writer;
    }

    /** One line per scroll event. No-op unless {@code debug_log} is on. */
    public static void write(String line) {
        if (!isEnabled()) {
            return;
        }
        writeForced(line);
    }

    /**
     * Every line is flushed immediately: the log is read while the game is still running (that is the
     * whole point of it), and a buffered tail shows up as missing events.
     */
    public static synchronized void writeForced(String line) {
        BufferedWriter w = writer();
        if (w == null) {
            return;
        }
        try {
            w.write(line);
            w.write('\n');
            w.flush();
        } catch (IOException e) {
            disabled = true;
        }
    }

    public static synchronized void flush() {
        if (writer == null) {
            return;
        }
        try {
            writer.flush();
        } catch (IOException e) {
            disabled = true;
        }
    }
}
