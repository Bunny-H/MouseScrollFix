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
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Gives the game window the desktop's own mouse pointer.
 *
 * <p>Minecraft never calls {@code glfwSetCursor}, so on Wayland the pointer is whatever GLFW picked
 * for itself - and GLFW resolves that theme from the {@code XCURSOR_THEME} environment variable,
 * which desktop environments do not export to applications (on X11 the theme comes from the X
 * resource database instead, so the same game looked correct there). The result is a generic
 * fallback arrow, whatever cursor theme the user configured.
 *
 * <p>Setting the environment variable from inside the game is not an option: Forge creates its early
 * loading window (through GLFW) before any mod is constructed, and GLFW loads the cursor theme
 * exactly once, at that moment. So instead this class reads the theme name from the same place the
 * desktop would (KDE's own configuration files, GTK's settings.ini, or the environment), loads the
 * arrow image out of that theme and hands it to GLFW directly.
 */
public final class CursorThemeFix {

    /** Arrow names, in the order themes are likely to provide them. */
    private static final String[] ARROW_NAMES = {"default", "left_ptr", "arrow", "top_left_arrow"};
    /** KDE's own default cursor size, in logical pixels. */
    private static final int DEFAULT_SIZE = 24;

    private static long cursor;
    private static boolean buildAttempted;
    private static String summary = "not applied yet";

    private CursorThemeFix() {
    }

    /** What the last attempt did, for the status command and the log. */
    public static synchronized String summary() {
        return summary;
    }

    /**
     * Drops the current cursor and forgets a previous failure, so the next {@link #apply()} picks up
     * the new settings. Called when the config is (re)loaded.
     */
    public static synchronized void reset() {
        buildAttempted = false;
        if (cursor == 0L) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) {
            long window = mc.getWindow().getWindow();
            if (window != 0L) {
                GLFW.glfwSetCursor(window, 0L);
            }
        }
        GLFW.glfwDestroyCursor(cursor);
        cursor = 0L;
    }

    /**
     * Gives the window the desktop's cursor, or re-applies it if it was already built. Idempotent
     * and cheap once the cursor exists, so it can be called on every screen open and from the tick
     * loop. The build itself is attempted at most once per config change, so a desktop without a
     * usable theme cannot spam the log.
     */
    public static synchronized void apply() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            return;
        }
        long window = mc.getWindow().getWindow();
        if (window == 0L) {
            return;
        }

        if (!ScrollFixConfig.fixCursorTheme) {
            if (cursor != 0L) {
                GLFW.glfwSetCursor(window, 0L);
                GLFW.glfwDestroyCursor(cursor);
                cursor = 0L;
                MouseScrollFix.LOGGER.info("[mousescrollfix] cursor fix disabled, restored GLFW's own pointer");
            }
            summary = "disabled by config";
            return;
        }
        if (cursor != 0L) {
            // GLFW forgets the window cursor whenever the game grabs or releases the mouse.
            GLFW.glfwSetCursor(window, cursor);
            return;
        }
        if (buildAttempted) {
            return;
        }
        buildAttempted = true;

        if (!isLinux()) {
            summary = "not Linux, skipped";
            return;
        }
        if (!EnvInfo.windowBackend().startsWith("wayland")) {
            summary = "not Wayland (X11 picks up the desktop theme itself), skipped";
            return;
        }

        try {
            build(window);
        } catch (Throwable t) {
            summary = "failed: " + t;
            MouseScrollFix.LOGGER.warn("[mousescrollfix] could not use the desktop cursor, "
                    + "keeping GLFW's default pointer", t);
        }
    }

    private static void build(long window) throws Exception {
        Setting themeSetting = firstSetting(
                isBlank(ScrollFixConfig.cursorTheme)
                        ? null
                        : new Setting(ScrollFixConfig.cursorTheme.trim(), "config cursor_theme"),
                envSetting("XCURSOR_THEME"),
                kdeSetting("cursorTheme"),
                gtkSetting("gtk-cursor-theme-name"));
        if (themeSetting == null) {
            summary = "no cursor theme configured on this desktop, keeping GLFW's default pointer";
            MouseScrollFix.LOGGER.info("[mousescrollfix] {}", summary);
            return;
        }
        String theme = themeSetting.value();

        int size = ScrollFixConfig.cursorSize;
        String sizeOrigin = "config cursor_size";
        if (size <= 0) {
            Setting sizeSetting = firstSetting(
                    envSetting("XCURSOR_SIZE"),
                    kdeSetting("cursorSize"),
                    gtkSetting("gtk-cursor-theme-size"));
            size = sizeSetting == null ? DEFAULT_SIZE : parseInt(sizeSetting.value());
            sizeOrigin = sizeSetting == null ? "default" : sizeSetting.origin();
            if (size <= 0) {
                size = DEFAULT_SIZE;
                sizeOrigin = "default";
            }
        }

        Path file = findArrowFile(theme);
        if (file == null) {
            summary = "cursor theme '" + theme + "' (" + themeSetting.origin()
                    + ") has no cursor files installed, keeping GLFW's default pointer";
            MouseScrollFix.LOGGER.warn("[mousescrollfix] {}", summary);
            return;
        }

        XCursorFile parsed = XCursorFile.load(file, size);
        long handle;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            GLFWImage.Buffer images = GLFWImage.malloc(1, stack);
            images.width(parsed.width).height(parsed.height).pixels(parsed.rgba);
            handle = GLFW.glfwCreateCursor(images.get(0), parsed.hotspotX, parsed.hotspotY);
        } finally {
            // glfwCreateCursor copies the pixels, so the buffer is ours to release right away.
            MemoryUtil.memFree(parsed.rgba);
        }
        if (handle == 0L) {
            int error = GLFW.glfwGetError(null);
            summary = "glfwCreateCursor failed (GLFW error 0x" + Integer.toHexString(error) + ")";
            MouseScrollFix.LOGGER.warn("[mousescrollfix] {}", summary);
            return;
        }

        cursor = handle;
        GLFW.glfwSetCursor(window, handle);
        summary = String.format(Locale.ROOT,
                "desktop cursor theme '%s' from %s: %s, %dpx image, nominal size %d, hotspot %d,%d "
                        + "(requested size %d from %s)",
                theme, themeSetting.origin(), file, parsed.width, parsed.nominalSize,
                parsed.hotspotX, parsed.hotspotY, size, sizeOrigin);
        MouseScrollFix.LOGGER.info("[mousescrollfix] {}", summary);
    }

    /** First {@code <icon dir>/<theme>/cursors/<arrow>} that exists. */
    private static Path findArrowFile(String theme) {
        for (Path iconDir : iconDirs()) {
            Path cursors = iconDir.resolve(theme).resolve("cursors");
            for (String name : ARROW_NAMES) {
                Path candidate = cursors.resolve(name);
                if (Files.isRegularFile(candidate)) {
                    return candidate;
                }
            }
        }
        return null;
    }

    private static List<Path> iconDirs() {
        List<Path> dirs = new ArrayList<>();
        for (Path dataHome : DesktopFiles.dataHomes()) {
            dirs.add(dataHome.resolve("icons"));
        }
        dirs.add(DesktopFiles.home().resolve(".icons"));
        for (Path dir : DesktopFiles.dataDirs()) {
            dirs.add(dir.resolve("icons"));
        }
        return dirs;
    }

    /**
     * The files KDE's config system would consult for {@code kcminputrc}, in the same order: the
     * user's own file, then system configs, then the {@code kdedefaults} that Plasma look-and-feel
     * themes install - the cursor theme usually lives in that last one, which is why a plain
     * {@code kcminputrc} lookup comes up empty on a stock install.
     *
     * <p>The directories come from {@link DesktopFiles} rather than from {@code user.home} directly:
     * launchers such as HMCL point {@code user.home} at their own data directory.
     */
    private static List<Path> kdeConfigFiles() {
        List<Path> files = new ArrayList<>();
        for (Path config : DesktopFiles.configHomes()) {
            files.add(config.resolve("kcminputrc"));
            files.add(config.resolve("kdedefaults/kcminputrc"));
        }
        for (Path dir : DesktopFiles.configDirs()) {
            files.add(dir.resolve("kcminputrc"));
            files.add(dir.resolve("kdedefaults/kcminputrc"));
        }
        for (Path dir : DesktopFiles.dataDirs()) {
            files.add(dir.resolve("kdedefaults/kcminputrc"));
        }
        return files;
    }

    private static Setting kdeSetting(String key) {
        for (Path file : kdeConfigFiles()) {
            String value = iniValue(file, "[Mouse]", key);
            if (!isBlank(value)) {
                return new Setting(value.trim(), file.toString());
            }
        }
        return null;
    }

    private static Setting gtkSetting(String key) {
        for (Path file : gtkSettingsFiles()) {
            String value = iniValue(file, null, key);
            if (!isBlank(value)) {
                return new Setting(value.trim(), file.toString());
            }
        }
        return null;
    }

    private static Setting envSetting(String name) {
        String value = System.getenv(name);
        return isBlank(value) ? null : new Setting(value.trim(), "$" + name);
    }

    private static Setting firstSetting(Setting... candidates) {
        for (Setting candidate : candidates) {
            if (candidate != null && !isBlank(candidate.value())) {
                return candidate;
            }
        }
        return null;
    }

    private static List<Path> gtkSettingsFiles() {
        List<Path> files = new ArrayList<>();
        for (Path config : DesktopFiles.configHomes()) {
            files.add(config.resolve("gtk-4.0/settings.ini"));
            files.add(config.resolve("gtk-3.0/settings.ini"));
        }
        return files;
    }

    /** Plain INI lookup; {@code section == null} matches the key in any section. */
    private static String iniValue(Path file, String section, String key) {
        if (file == null || !Files.isReadable(file)) {
            return null;
        }
        try {
            boolean inSection = section == null;
            for (String raw : Files.readAllLines(file)) {
                String line = raw.trim();
                if (line.isEmpty() || line.startsWith("#") || line.startsWith(";")) {
                    continue;
                }
                if (line.startsWith("[")) {
                    inSection = section != null && line.equalsIgnoreCase(section);
                    continue;
                }
                if (!inSection) {
                    continue;
                }
                int eq = line.indexOf('=');
                if (eq > 0 && line.substring(0, eq).trim().equals(key)) {
                    return line.substring(eq + 1).trim();
                }
            }
        } catch (Exception e) {
            MouseScrollFix.LOGGER.debug("[mousescrollfix] could not read {}: {}", file, e.toString());
        }
        return null;
    }

    private static int parseInt(String value) {
        if (isBlank(value)) {
            return 0;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static boolean isLinux() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("linux");
    }

    /** A config value together with the file or variable it came from, for logging. */
    private record Setting(String value, String origin) {
    }
}
