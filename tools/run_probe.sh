#!/usr/bin/env bash
# Copyright (C) 2026 BunnyH
#
# This file is part of Mouse Scroll Fix (mousescrollfix), a Minecraft mod.
#
# Mouse Scroll Fix is free software: you can redistribute it and/or modify it under the terms
# of the GNU Lesser General Public License as published by the Free Software Foundation,
# version 3 of the License.
#
# Mouse Scroll Fix is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
# without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
# See the GNU Lesser General Public License for more details.
#
# You should have received a copy of the GNU Lesser General Public License along with
# Mouse Scroll Fix. If not, see <https://www.gnu.org/licenses/>.

# Compile and run ScrollProbe against the very same LWJGL/GLFW (3.3.1 / GLFW 3.4.0) that
# Minecraft 1.20.1 uses, so the probe sees exactly what the game sees.
#
#   tools/run_probe.sh              # => X11 / XWayland (bundled GLFW picks X11)
#   tools/run_probe.sh --wayland    # => native Wayland (distro GLFW 3.5.1)
#
# Needs JDK 17 (LWJGL 3.3.1 predates newer class file versions); the system javac
# on this machine is Java 26, which would produce classes the probe cannot load.
set -euo pipefail

here="$(cd "$(dirname "$0")" && pwd)"
out=/tmp/msf-probe
mkdir -p "$out"

lwjgl=$(find "$HOME/.gradle/caches/modules-2/files-2.1/org.lwjgl" \
        -name 'lwjgl-3.3.1.jar' -o -name 'lwjgl-glfw-3.3.1.jar' \
        -o -name 'lwjgl-3.3.1-natives-linux.jar' -o -name 'lwjgl-glfw-3.3.1-natives-linux.jar' \
        | sort | tr '\n' ':')

jdks=${MSF_JDK:-/usr/lib/jvm/java-17-openjdk}

rm -rf "$out"
mkdir -p "$out"
"$jdks/bin/javac" -cp "$lwjgl" -d "$out" "$here/ScrollProbe.java"

if [ "${1:-}" = "--wayland" ]; then
    exec "$jdks/bin/java" -cp "$out:$lwjgl" \
        -Dorg.lwjgl.glfw.libname=/usr/lib/libglfw.so.3 ScrollProbe
fi
exec "$jdks/bin/java" -cp "$out:$lwjgl" ScrollProbe
