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
