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
    private static long lastFlush;

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
                writer.write("# raw     = yOffset exactly as GLFW delivered it (already scaled by the compositor)\n");
                writer.write("# norm    = value after normalization (what vanilla MouseHandler.onScroll now sees)\n");
                writer.write("# ctx     = world = hotbar path, screen = a GUI consumed it, none = overlay/no player\n");
                writer.write("# slotBefore = hotbar slot index before this event was applied\n");
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

    /** Always written, even when {@code debug_log} is off (used by the explicit self test). */
    public static synchronized void writeForced(String line) {
        BufferedWriter w = writer();
        if (w == null) {
            return;
        }
        try {
            w.write(line);
            w.write('\n');
            long now = System.currentTimeMillis();
            if (now - lastFlush > 500L) {
                lastFlush = now;
                w.flush();
            }
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
