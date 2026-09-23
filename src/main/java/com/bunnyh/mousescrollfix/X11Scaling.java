package com.bunnyh.mousescrollfix;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Looks for evidence that this desktop duplicates wheel events, which is what makes the
 * X11/XWayland repair in {@link ScrollNormalizer} a repair rather than a guess.
 *
 * <p>Under X11 (including XWayland) a scroll-speed multiplier cannot scale the <em>value</em> of a
 * scroll event - the protocol has no fractional wheel delta and GLFW always reports +-1.0 there - so
 * the desktop can only emit <em>more or fewer</em> events. KWin does exactly that: with a per-device
 * {@code ScrollFactor} of 1.5 one physical notch arrives as two button-4 presses (measured 780-950
 * microseconds apart, see FINDINGS.md) and the game, which counts one event as one slot, jumps two
 * slots on every other notch.
 *
 * <p>KWin keeps those factors in {@code kcminputrc}, so the file itself is the evidence this class
 * reads. Only a factor above 1 duplicates notches; when no such factor is configured nothing is
 * merged and the mod stays out of the way of systems it cannot help.
 *
 * <p>The configuration files are located through {@link DesktopFiles}, because the launcher's idea of
 * "home" is not necessarily the desktop's: HMCL runs the game with {@code -Duser.home} pointing at its
 * own data directory, and looking there is how this detection silently found nothing once.
 */
public final class X11Scaling {

    private static Boolean duplicating;
    private static String summary = "not checked yet";

    private X11Scaling() {
    }

    /** True when KDE's input configuration scales some device's wheel up (ScrollFactor above 1). */
    public static boolean duplicating() {
        synchronized (X11Scaling.class) {
            if (duplicating == null) {
                detect();
            }
            return duplicating;
        }
    }

    /** What the detection found, for the log and the status command. */
    public static String summary() {
        synchronized (X11Scaling.class) {
            if (duplicating == null) {
                detect();
            }
            return summary;
        }
    }

    /** Forgets the previous answer; called when the config is (re)loaded. */
    public static synchronized void reset() {
        duplicating = null;
        summary = "not checked yet";
    }

    private static void detect() {
        double highest = 0.0D;
        String origin = null;
        List<Path> files = kdeInputConfigFiles();
        for (Path file : files) {
            if (!Files.isReadable(file)) {
                continue;
            }
            try {
                String section = "";
                for (String raw : Files.readAllLines(file)) {
                    String line = raw.trim();
                    if (line.isEmpty() || line.startsWith("#") || line.startsWith(";")) {
                        continue;
                    }
                    if (line.startsWith("[") && line.endsWith("]")) {
                        section = line.substring(1, line.length() - 1);
                        continue;
                    }
                    int eq = line.indexOf('=');
                    if (eq <= 0 || !line.substring(0, eq).trim().equals("ScrollFactor")) {
                        continue;
                    }
                    double value = parseDouble(line.substring(eq + 1));
                    if (value > highest) {
                        highest = value;
                        origin = file + " (" + section + ")";
                    }
                }
            } catch (Exception e) {
                MouseScrollFix.LOGGER.debug("[mousescrollfix] could not read {}: {}", file, e.toString());
            }
        }

        duplicating = highest > 1.0D;
        if (duplicating) {
            summary = String.format(Locale.ROOT,
                    "this desktop duplicates wheel events (ScrollFactor %.2f in %s)", highest, origin);
        } else if (highest > 0.0D) {
            summary = String.format(Locale.ROOT,
                    "no ScrollFactor above 1 configured (highest %.2f), nothing to merge", highest);
        } else {
            // Naming the files that were checked is what turns "no effect" into a diagnosable report.
            summary = "no KDE wheel ScrollFactor found in " + files + ", nothing to merge";
        }
    }

    /**
     * Every file that could hold a per-device scroll factor: the user's own {@code kcminputrc} in each
     * configuration directory, plus the {@code kdedefaults} copies Plasma themes and distributions
     * install.
     */
    private static List<Path> kdeInputConfigFiles() {
        List<Path> dirs = new ArrayList<>();
        dirs.addAll(DesktopFiles.configHomes());
        dirs.addAll(DesktopFiles.configDirs());
        dirs.addAll(DesktopFiles.dataDirs());
        List<Path> files = new ArrayList<>();
        for (Path dir : dirs) {
            files.add(dir.resolve("kcminputrc"));
            files.add(dir.resolve("kdedefaults/kcminputrc"));
        }
        return files;
    }

    private static double parseDouble(String value) {
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return 0.0D;
        }
    }
}
