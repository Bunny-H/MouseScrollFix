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

import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.glfw.GLFWVidMode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.system.MemoryUtil.NULL;

/**
 * Measures what GLFW hands to Minecraft for one physical wheel notch, under the real
 * KWin/Wayland session. Covers the whole screen so the pointer is definitely over it.
 */
public class ScrollProbe {

    public static void main(String[] args) throws IOException {
        GLFWErrorCallback.createPrint(System.err).set();

        if (!glfwInit()) {
            System.err.println("glfwInit FAILED");
            System.exit(1);
        }

        int[] major = new int[1], minor = new int[1], rev = new int[1];
        glfwGetVersion(major, minor, rev);
        System.out.println("GLFW compiled version : " + major[0] + "." + minor[0] + "." + rev[0]);
        System.out.println("GLFW version string   : " + glfwGetVersionString());
        System.out.println("WAYLAND_DISPLAY       : " + System.getenv("WAYLAND_DISPLAY"));
        System.out.println("DISPLAY               : " + System.getenv("DISPLAY"));

        long monitor = glfwGetPrimaryMonitor();
        GLFWVidMode mode = monitor == NULL ? null : glfwGetVideoMode(monitor);
        int w = mode != null ? mode.width() : 1280;
        int h = mode != null ? mode.height() : 720;
        System.out.println("monitor mode          : " + w + "x" + h);

        glfwWindowHint(GLFW_VISIBLE, GLFW_TRUE);
        glfwWindowHint(GLFW_CLIENT_API, GLFW_NO_API);
        glfwWindowHint(GLFW_FLOATING, GLFW_TRUE);
        // Undecorated + nearly full screen: on Wayland the compositor places the window,
        // so the only reliable way to guarantee the pointer is over it is to cover the screen.
        glfwWindowHint(GLFW_DECORATED, GLFW_FALSE);
        long window = glfwCreateWindow(w, h, "ScrollProbe", monitor, NULL);
        if (window == NULL) {
            System.err.println("glfwCreateWindow FAILED");
            System.exit(1);
        }
        glfwShowWindow(window);
        glfwFocusWindow(window);

        System.out.println("--- loaded window-system libs (from /proc/self/maps) ---");
        List<String> maps = Files.readAllLines(Path.of("/proc/self/maps"));
        System.out.println("  libwayland-client mapped : " + maps.stream().anyMatch(l -> l.contains("libwayland-client")));
        System.out.println("  libX11 mapped            : " + maps.stream().anyMatch(l -> l.contains("libX11.so")));
        System.out.println("--------------------------------------------------------");
        System.out.println("Each line = ONE GLFW scroll event. Ctrl-C in the terminal when done.");
        System.out.flush();

        // dt = microseconds since the previous event. Two events belonging to the SAME physical
        // notch (compositor replication) show up as dt of a few microseconds; separate notches are
        // milliseconds apart, however fast the wheel is spun.
        long[] prev = {0L};
        glfwSetScrollCallback(window, (win, xoffset, yoffset) -> {
            long t = System.nanoTime();
            long dtMicros = prev[0] == 0L ? -1L : (t - prev[0]) / 1_000L;
            prev[0] = t;
            System.out.printf("EVENT t=%d dtMicros=%d x=%+.6f y=%+.6f%n",
                    System.currentTimeMillis(), dtMicros, xoffset, yoffset);
        });

        while (!glfwWindowShouldClose(window)) {
            glfwWaitEventsTimeout(0.2);
        }
        glfwTerminate();
    }
}
