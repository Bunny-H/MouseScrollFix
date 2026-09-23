package com.bunnyh.mousescrollfix;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Where this desktop keeps its own configuration.
 *
 * <p>The obvious answer - {@code System.getProperty("user.home")} - is wrong on HMCL: it launches the
 * game with {@code -Duser.home=<its own data directory>}, so a mod that trusts that property looks
 * inside {@code ~/.config/hmcl/.config} and never finds the desktop's settings. Both features that
 * read the desktop's configuration (the X11 duplicate detection in {@link X11Scaling} and the cursor
 * theme in {@link CursorThemeFix}) need the <em>user's</em> home, so HOME comes first here and the
 * property is only a fallback - a Gradle-launched client sets no {@code -Duser.home} and both agree.
 *
 * <p>Every directory that could hold the file is offered, not just the first one: a launcher may set
 * {@code XDG_CONFIG_HOME}, a distribution may ship defaults in {@code /etc/xdg}, and checking all of
 * them is cheap while a single wrong guess means a feature that silently does nothing.
 */
public final class DesktopFiles {

    private DesktopFiles() {
    }

    /** The user's real home directory, or a best effort when even HOME is missing. */
    public static Path home() {
        String env = System.getenv("HOME");
        if (env != null && !env.isBlank()) {
            return Path.of(env);
        }
        return Path.of(System.getProperty("user.home", "."));
    }

    /** Directories that may hold the desktop's configuration, most trustworthy first. */
    public static List<Path> configHomes() {
        List<Path> dirs = new ArrayList<>();
        addIfSet(dirs, System.getenv("XDG_CONFIG_HOME"));
        dirs.add(home().resolve(".config"));
        dirs.addAll(aroundPropertyHome(".config"));
        return distinct(dirs);
    }

    /** Directories that may hold the desktop's data: icons, themes. */
    public static List<Path> dataHomes() {
        List<Path> dirs = new ArrayList<>();
        addIfSet(dirs, System.getenv("XDG_DATA_HOME"));
        dirs.add(home().resolve(".local/share"));
        dirs.addAll(aroundPropertyHome(".local/share"));
        return distinct(dirs);
    }

    /**
     * {@code -Duser.home} plus one subdirectory, and the same for its first two ancestors.
     *
     * <p>On HMCL that property is the launcher's data directory, usually {@code <home>/.config/hmcl},
     * so the ancestors are the directories a desktop would really keep its settings in - which keeps
     * the lookup working even in an environment where HOME was not passed on either.
     */
    private static List<Path> aroundPropertyHome(String subdirectory) {
        List<Path> dirs = new ArrayList<>();
        String property = System.getProperty("user.home");
        if (property == null || property.isBlank()) {
            return dirs;
        }
        Path start = Path.of(property);
        dirs.add(start.resolve(subdirectory));
        Path ancestor = start.getParent();
        for (int i = 0; i < 2 && ancestor != null && ancestor.getNameCount() >= 2; i++) {
            dirs.add(ancestor.resolve(subdirectory));
            ancestor = ancestor.getParent();
        }
        return dirs;
    }

    /** {@code $XDG_CONFIG_DIRS}, i.e. system-wide configuration. */
    public static List<Path> configDirs() {
        return splitPaths(System.getenv("XDG_CONFIG_DIRS"), "/etc/xdg");
    }

    /** {@code $XDG_DATA_DIRS}, i.e. system-wide data. */
    public static List<Path> dataDirs() {
        return splitPaths(System.getenv("XDG_DATA_DIRS"), "/usr/local/share:/usr/share");
    }

    private static void addIfSet(List<Path> dirs, String value) {
        if (value != null && !value.isBlank()) {
            dirs.add(Path.of(value));
        }
    }

    private static List<Path> distinct(List<Path> dirs) {
        return new ArrayList<>(new LinkedHashSet<>(dirs));
    }

    private static List<Path> splitPaths(String value, String fallback) {
        List<Path> dirs = new ArrayList<>();
        for (String part : (value == null || value.isBlank() ? fallback : value).split(":")) {
            if (!part.isBlank()) {
                dirs.add(Path.of(part));
            }
        }
        return dirs;
    }
}
