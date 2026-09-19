// Query-only KWin helper: list Minecraft windows with geometry, pid and focus state.
// Does not move, raise or activate anything.
const wins = (typeof workspace.windowList === "function")
    ? workspace.windowList()
    : workspace.clientList();

const p = workspace.cursorPos;
console.info("MSF-CURSOR x=" + p.x + " y=" + p.y);

for (const w of wins) {
    const cap = ((w.caption || "") + " " + (w.resourceClass || "")).toLowerCase();
    if (cap.indexOf("minecraft") < 0 && cap.indexOf("forge") < 0) {
        continue;
    }
    const g = w.frameGeometry;
    let out = "?";
    try {
        const o = w.output;
        if (o) {
            out = o.name + " scale=" + o.scale + " geo=" + o.geometry.x + "," + o.geometry.y
                + " " + o.geometry.width + "x" + o.geometry.height;
        }
    } catch (e) {
        out = "output-unavailable";
    }
    console.info("MSF-WIN pid=" + w.pid
        + " active=" + (workspace.activeWindow === w)
        + " cap='" + w.caption + "'"
        + " frame=" + g.x + "," + g.y + " " + g.width + "x" + g.height
        + " output[" + out + "]");
}
