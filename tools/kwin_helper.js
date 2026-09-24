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

// KWin helper: raise Minecraft, optionally move the cursor, and report geometry.
// console.info output goes to the user journal (journalctl --user -t kwin_wayland).
const wins = (typeof workspace.windowList === "function")
    ? workspace.windowList()
    : workspace.clientList();

for (const w of wins) {
    const cap = ((w.caption || "") + " " + (w.resourceClass || "")).toLowerCase();
    if (cap.indexOf("minecraft") >= 0) {
        w.keepAbove = true;
        w.minimized = false;
        workspace.activeWindow = w;
        const g = w.frameGeometry;
        console.info("MSF-WIN caption='" + w.caption + "' x=" + g.x + " y=" + g.y
            + " w=" + g.width + " h=" + g.height
            + " cx=" + (g.x + g.width / 2) + " cy=" + (g.y + g.height / 2));
    }
}
const p = workspace.cursorPos;
console.info("MSF-CURSOR x=" + p.x + " y=" + p.y);
