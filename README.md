[English](README.md) | [简体中文](README.zh_cn.md)

# Mouse Scroll Fix — Minecraft 1.20.1 / Forge 47.4.21

<img src="src/main/resources/logo.png" width="140" alt="Mouse Scroll Fix logo">

A client-side mod for Minecraft 1.20.1 (Forge) on Linux. It does three things:

1. **Makes 1.20.1 run on native Wayland.** Vanilla either crashes outright or hangs on an
   error dialog because of GLFW's `0x1000C` — Wayland does not provide a window position and
   does not support setting the window icon.
2. **Makes the mouse cursor follow the desktop's cursor theme.** On native Wayland the pointer
   is decided by GLFW, and GLFW only honours the `XCURSOR_THEME` environment variable, which
   desktop environments do not export — so you end up with a generic set of arrows instead.
   **Off by default since v1.4.0** (see limitation 3 in section 6); the switch lives at
   Mods list → this mod → Config.
3. **Makes the wheel mean "one notch = one step"**, which is what vanilla's *Discrete Scrolling*
   option does — except this one is on by default, so players don't have to know it exists.

On that third point: the vanilla option is not enough on its own. It only takes the sign of each
event's **value**, while what actually goes wrong on Linux desktops is the **number** of events —
the notch count has already been rewritten by the desktop before the game ever sees it. See
section 1. Windows and macOS have no such layer, so apart from the first two points this mod
does nothing there.

- Artifact: `build/libs/mousescrollfix-1.4.0.jar`
- Client-side only; no effect on multiplayer (`clientSideOnly=true`)
- **Verified only in a clean environment (a dev client with no other mods), not in a large
  modpack** — see section 6.

---

## 1. What the problem actually is

It is not a Minecraft bug, and the wheel is not broken. It is what happens when the **desktop
environment and the game stack on top of each other**.

KDE lets you set a "scroll speed" per input device. It is stored per device in
`~/.config/kcminputrc` and looks like this:

```ini
[Libinput][9390][5138][your device name]
ScrollFactor=1.5
```

(The setting lives under System Settings → Mouse & Touchpad → Scroll speed. Other desktops have
similar mechanisms, differing in where the value is stored and how widely it applies; GNOME has
no such multiplier at all, so on GNOME this mod has no effect on scrolling.)

How that multiplier works is not what most people assume. Using a virtual mouse created through
`/dev/uinput` — so KWin treats it like a real mouse — and counting the wheel events the game
actually receives gives the following two sets of results.

### When the game runs on X11 / XWayland

| Device multiplier | 12 notches turned, game receives | Value of each event | Gap between events |
|---|---|---|---|
| 1.0 (default) | 12 events | all `+1.0` | 200 ms (= your turning rate) |
| **1.5** | **18 events** | all `+1.0` | alternating 200 ms and **0.8 ms** (a pair every 2 notches) |
| 0.75 | **9 events** | all `+1.0` | `200 200 400` repeating |
| 0.1 | **1 event** | `+1.0` | — |

The key insight: **the value is always ±1.0. What the desktop changes is the number of events.**

When the multiplier is **> 1**, the extra event arrives **0.8 ms behind the original**. It is not
"another notch turned" — nobody turns two notches within a millisecond, and even when spinning
hard, two real notches are several ms apart. It is **the same notch, duplicated**. Since v1.3.0
the mod recognises it: the criterion is "same direction, less than 2 ms apart", so 12 notches
stay 12 notches.

When the multiplier is **< 1** (0.75, 0.1), events are **swallowed**: turning 4 notches delivers
only 3 events, and the 4th is gone before it reaches the game. **Nothing can restore those**,
and this mod cannot either (neither can vanilla's *Discrete Scrolling* option or the
`mouseWheelSensitivity` slider — the events are already ±1 to begin with).

Vanilla Minecraft means "one event = one step", so before the fix:

- multiplier 1.5 → 1.5 steps per notch → **sometimes two steps at once, which looks like skipped items**
- multiplier 0.75 → 0.75 steps per notch → **one notch does nothing, two notches move one step, the next one skips**
- multiplier 0.1 → ten notches per step

### When the game runs on native Wayland

| Device multiplier | 12 notches turned, game receives | Value of each event |
|---|---|---|
| 1.5 | **12 events (one per notch)** | **`+1.5`** |
| 0.1 / 2.0 / anything else | still 12 events | equal to that multiplier |

Here the multiplier becomes the event's **value**, and the event count is back to 1:1. So
normalising the value to ±1 gives you a perfect "one notch = one step".

On native Wayland the mod is at its most thorough: whether the system multiplier is 0.1 or 2.0,
one notch is one step. That is also the recommended setup.

---

## 2. How to use it

### 1. Drop the jar into your `mods` folder

Just this one jar. **Try it in a 1.20.1 Forge instance with no other mods first**, and only then
consider putting it into a modpack.

### 2. Make Minecraft use native Wayland (recommended)

The GLFW that MC 1.20.1 ships (the copy bundled inside LWJGL 3.3.1) **still picks X11 in a Wayland
session even though it was built with the Wayland backend** — it is a 3.4 snapshot from before the
`XDG_SESSION_TYPE` selection logic existed. The GLFW your distribution ships (3.5.1 on Arch) does
pick Wayland, so hand that one to the game:

```bash
# First find where GLFW actually lives on your system
ldconfig -p | grep libglfw
# Arch:    /usr/lib/libglfw.so.3
# Debian/Ubuntu: /usr/lib/x86_64-linux-gnu/libglfw.so.3
```

Then add this JVM argument to the instance (with the path you just found):

```
-Dorg.lwjgl.glfw.libname=/usr/lib/libglfw.so.3
```

Where the argument goes differs per launcher; usually under instance settings → advanced → JVM
arguments. HMCL's "use system GLFW" toggle **achieves the same thing** — pick one or the other,
having both does no harm.

> **A too-old system GLFW closes this door.** GLFW 3.3.x has no Wayland backend at all, so on
> older LTS distributions such as Ubuntu/Debian you either build your own GLFW ≥ 3.4 or give up
> on native Wayland. What you need is not "the newest version" but a GLFW that **picks Wayland in
> a Wayland session**: version ≥ 3.4 with the `XDG_SESSION_TYPE` check built in (what
> distributions ship normally satisfies this).

**Switching is optional.** Since v1.3.0 the mod merges "the same notch, duplicated" under
X11 / XWayland, so the skipped-step problem at multiplier > 1 gets fixed there too. But multipliers
< 1 (swallowed events) can still only be solved by going native Wayland.

### 3. Confirm it is working

After the game starts, look at `logs/latest.log`. You should see:

```
[mousescrollfix] GLFW 3.5.1 Wayland X11 GLX Null EGL OSMesa monotonic shared | window backend: wayland (libX11 also present)
[mousescrollfix] desktop cursor theme 'breeze_cursors' from /home/<user>/.config/kdedefaults/kcminputrc: /usr/share/icons/breeze_cursors/cursors/default, 32px image, nominal size 24, hotspot 4,4
```

The first line is the backend. If it says `x11 / xwayland`, the JVM argument from step 2 did not
take effect — in that case the mod uses the X11 "merge duplicated notches" fix, which only covers
the "sped up" half (the log says so). The second line means the pointer has been set to your
desktop theme (theme name, source file and size are printed; change your theme and this line
changes too; it is absent when `fix_cursor_theme = false`).

There is also a line `[mousescrollfix] scroll fix in this session: ... | X11 detection: ...` which
says which fix applies to this run and whether a wheel multiplier was read from your system
configuration (and from which file). **When it seems to have no effect, look at that line first**:
if it says `no KDE wheel ScrollFactor found in [...], nothing to merge`, the files in brackets are
the ones it checked, which tells you whether this is a configuration-reading problem or simply a
system that never applied a multiplier.

### 4. If you want to see the evidence

Edit `config/mousescrollfix-client.toml`:

```toml
debug_log = true          # write every wheel event to logs/mousescrollfix-debug.log
self_test_on_join = true  # run a self-test 4 seconds after joining a world
```

Or type `/mousescrollfix test` once in a world. The log looks like this (native Wayland, system
multiplier 1.5):

```
t=1790187718166 raw=+1.5000 norm=+1.0000 gapUs=-1     ctx=world  slotBefore=7
t=1790187718366 raw=+1.5000 norm=+1.0000 gapUs=199763 ctx=world  slotBefore=6
t=1790187718566 raw=+1.5000 norm=+1.0000 gapUs=199909 ctx=world  slotBefore=5
```

`raw` is the value GLFW handed over, `norm` is what the fix passes on to vanilla, and `gapUs` is
the time since the previous event in microseconds (`t` is a millisecond timestamp). The snippet
above is "three notches → hotbar 7, 6, 5", one step at a time.

Under X11 / XWayland you additionally get `DUPLICATE-DROPPED` lines and much smaller `gapUs`:

```
t=1790187718366 raw=+1.0000 norm=+0.0000 gapUs=193    ctx=world  slotBefore=6 DUPLICATE-DROPPED
t=1790187718566 raw=+1.0000 norm=+1.0000 gapUs=199909 ctx=world  slotBefore=6
```

The first line is the duplicated second event of the same notch: it follows the previous event
within 193 microseconds and is dropped without counting as a step. The second line is the next
real notch.

---

## 3. Measured results

Test environment: KDE Plasma 6 / Wayland / KWin 6, mouse device with `ScrollFactor = 1.5`,
reproduced with a `/dev/uinput` virtual mouse. All values are measured.

| Environment | 12 notches turned, wheel events received | Value per event | Result |
|---|---|---|---|
| Vanilla + X11 | **18** | `+1.0` | 1.5 steps per notch, scrambled |
| Vanilla + native Wayland | 12 | `+1.5` | still 1.5 steps per notch (vanilla treats 1.5 as a count) |
| **This mod + native Wayland** | 12 | `+1.0` (after the mod) | **exactly one step per notch** |

### Under X11 / XWayland (the v1.3.0 merge fix)

Again "12 notches turned" (dev client running under XWayland, virtual mouse posing as a device
with ScrollFactor 1.5):

| Configuration | Events received | Judged duplicates | Hotbar steps actually taken |
|---|---|---|---|
| `x11_fix = "off"` (control group, as if the fix did not exist) | 18 | 0 | **18** ← skipping |
| `x11_fix = "auto"` (default; the mod reads kcminputrc itself) | 18 | 6 | **12** ← one per notch |
| Default device (multiplier 1.0, no multiplier applied by the system) | 12 | 0 | 12 (unaffected) |
| Multiplier 1.5, one notch every 20 ms (50/s, spinning hard) | 18 | 6 | **12** (fast scrolling is not eaten) |

Hotbar path for the default row: `8,7,6,5,4,3,2,1,0,8,7,6,5` — 12 notches are exactly 12 steps,
including a 9-slot wrap. The merged duplicates measured 138–197 microseconds apart, while the
detection window is 2 ms, more than 10× apart.

**Does it swallow genuine fast scrolling as duplicates?** Measured with 200 consecutive fast
notches (one every 50 ms): 300 events received = 200 × 1.5, all 100 pairs merged, exactly 200
steps in the end — nothing dropped, nothing extra. Together with another 42 pairs, all 142
duplicate pairs were merged correctly.

Self-test output after joining a world (`yOffset` is the raw value a "notch" was simulated as;
the numbers are how many slots that notch moved the hotbar):

```
yOffset   notch1 notch2 notch3 notch4
+0.10         -1     -1     -1     -1
+0.75         -1     -1     -1     -1
+1.00         -1     -1     -1     -1
+1.50         -1     -1     -1     -1
+2.00         -1     -1     -1     -1
RESULT: PASS - every simulated notch moved exactly 1 slot
```

Everything from 0.1 to 2.0 moves exactly one step — meaning **the mod stays correct if you switch
to a different mouse or change your desktop's scroll speed**.

---

## 4. Configuration

`config/mousescrollfix-client.toml` (generated on first launch)

| Option | Default | Meaning |
|---|---|---|
| `enabled` | `true` | Master switch |
| `affect_screens` | `true` | Also normalise GUI screens (inventory / JEI / creative inventory); set to `false` to fix the hotbar only |
| `x11_fix` | `"auto"` | X11 / XWayland only: whether to merge "the same notch, duplicated". `auto` = enable only when a **ScrollFactor > 1** is found in KDE's `kcminputrc` (only such a machine produces duplicate events, so the mod does not touch systems it cannot help); `on` = merge in any X11 session; `off` = never merge (equivalent to not having this fix) |
| `x11_merge_ms` | `2` | Two events in the same direction closer together than this many milliseconds count as one notch. Measured duplicates are 0.15–0.95 ms apart and two real notches are at least several ms apart, so 2 is safe; effectively "at most one step per frame". `0` disables |
| `debug_log` | `false` | Write every wheel event to `logs/mousescrollfix-debug.log` |
| `self_test_on_join` | `false` | Run a self-test after joining a world (includes the X11 merge self-test) |
| `backend_hint` | `true` | If the game is not on native Wayland (X11 / XWayland), show a one-time hint on the main menu explaining that "the sped-up half is fixed there, the slowed-down half is not". Only shown when X11/XWayland is **actually detected** (on Windows/macOS the backend cannot be detected, so it never fires) |
| `fix_cursor_theme` | `false` | Replace the mouse pointer with the desktop's cursor theme (native Wayland only). **Off by default since v1.4.0** — see limitation 3 in section 6. Toggle it in game at Mods list → this mod → Config; changes take effect immediately and are written back to this file. Set back to `false` to restore GLFW's default arrows |
| `cursor_theme` | empty | Only relevant when `fix_cursor_theme = true`. Empty = read the desktop setting; only fill this in when the mod cannot recognise your desktop environment (a directory name under `/usr/share/icons`) |
| `cursor_size` | `0` | Only relevant when `fix_cursor_theme = true`. Cursor size in logical pixels; `0` = use the desktop's configured size (24 when unreadable) |

In-game commands: `/mousescrollfix test` (run the self-test), `/mousescrollfix status` (current
state: backend, which fix applies here, X11 detection result), `/mousescrollfix cursor` (re-apply
and print which theme and file the pointer came from), `/mousescrollfix hint` (show that
environment hint manually, useful for seeing it while on native Wayland).

---

## 5. What the mod actually does

| Class | Role |
|---|---|
| `MouseHandlerMixin` | **Core**: passes each wheel event's `yOffset` through `ScrollNormalizer` before handing it to the rest of vanilla logic. Not a single line of vanilla is rewritten, so spectator speed control, creative flight and `ForgeHooksClient.onMouseScroll` (other mods' wheel features) all behave exactly as before |
| `ScrollNormalizer` | Both fixes live here: normalise the value to ±1 on native Wayland; classify "the second event in the same instant, same direction" as a duplicate and drop it under X11/XWayland |
| `X11Scaling` | Reads KDE's `kcminputrc` looking for any device with `ScrollFactor > 1` — only then is the system really duplicating wheel events, and `x11_fix = "auto"` decides on that basis |
| `GlxWaylandCompatMixin` | Lets MC 1.20.1 start on Wayland at all: vanilla treats GLFW's `0x1000C` ("Wayland does not provide the window position") as fatal and crashes |
| `WindowWaylandCompatMixin` | Same story for the next one: `0x1000C` ("Wayland does not support setting the window icon") makes vanilla show a "please update your graphics driver" dialog and hang. Both mixins only let that single error code through and log it; every other GLFW error is handled by vanilla as usual |
| `CursorThemeFix` | **Off by default since v1.4.0**: on Wayland the pointer over the window is decided by GLFW, and GLFW only honours `XCURSOR_THEME` (which desktops do not export), so you get generic arrows. This class reads the desktop configuration itself (KDE `kcminputrc` / GTK `settings.ini` / environment variables) and hands the theme's arrow to GLFW when a screen opens. GLFW forgets the window cursor when the mouse is grabbed and released, so it is re-applied every 10 seconds and whenever a screen opens |
| `ScrollFixConfigScreen` | The config screen in the Mods list (Forge 1.20.1 has no config UI of its own; without registering one the settings would only exist in the toml file). Currently a single cursor switch whose tooltip explains the risk of the desktop-theme cursor; flipping it updates the config, saves it, and rebuilds or frees the cursor immediately, no restart needed |
| `XCursorFile` | Parses the Xcursor file format (`.cursor` / `cursors/*` inside a theme), picks the closest available arrow size, and converts premultiplied ARGB to the straight RGBA GLFW wants |
| `EnvInfo` | Determines whether the game is really on native Wayland or X11/XWayland (reads `/proc/self/maps` to see whether `libwayland-client` or `/libX11.so` is loaded); if neither is found it reports "unknown" and does nothing |
| `BackendHint` | Shows a one-time hint on the main menu (at most once per launch) when X11/XWayland is detected, explaining that "the sped-up half is fixed, the slowed-down half cannot be". On Windows/macOS the backend cannot be detected, so by construction it never misfires. The text comes from the lang files, one for `en_us` and one for `zh_cn` |

---

## 6. Known limitations and risks (please read before using)

1. **Verified in a clean environment only.** The dev client used for testing had no other mods
   installed. Switching the graphics backend can conflict with rendering, screenshot, input and
   screen-recording mods, **so try it in an empty instance first and do not drop it straight into
   a modpack**.
2. Moving to native Wayland changes the whole window/input/scaling backend, and the side effects
   go beyond the wheel:
   - the window icon is never set (you will see the ignored warning in the log)
   - Wayland does not provide a window position, so the game may not remember where its window was
   - fractional scaling (e.g. 1.25x) can change how the picture and cursor behave

3. **The pointer can be switched to your desktop theme — but it is off by default since v1.4.0.**
   The background: on Wayland the arrow over the window comes entirely from GLFW (Minecraft itself
   never calls `glfwSetCursor` — the whole client only uses `glfwSetCursorPos` and a couple of
   callbacks), and GLFW's Wayland backend has neither the compositor-side cursor-shape protocol
   (searching `/usr/lib/libglfw.so.3` finds no `wp_cursor_shape`) nor any way to learn the theme
   name other than the `XCURSOR_THEME` / `XCURSOR_SIZE` environment variables — while KDE writes
   the theme into the X resource database on the X11 side (`xrdb -query` shows
   `Xcursor.theme: breeze_cursors`) and exports nothing on the Wayland side. GLFW therefore falls
   back to a theme called `default`, and `/usr/share/icons/default/index.theme` contains a single
   line, `Inherits=Adwaita` → you get a generic set of Adwaita arrows.

   Fixing it with environment variables is **a dead end**: Forge's early loading window
   (`Loading ImmediateWindowProvider fmlearlywindow`) initialises GLFW before mods are loaded, and
   GLFW reads the cursor theme from the environment exactly once, at that moment. So the mod reads
   the desktop configuration itself instead (KDE's `kcminputrc`, the `kdedefaults` default file
   next to it, GTK's `settings.ini`, and only then the environment variables), parses the arrow
   images out of the theme and hands them to GLFW directly. Change your theme and it follows;
   under X11 the feature is skipped automatically (nothing is broken there).

   Measured at 1.25x fractional scaling: pixel-by-pixel template matching against the original
   arrows of both themes gives a mean colour difference of 33.6 for Breeze versus 130.2 for
   Adwaita (main menu); repeating it after pressing ESC in a world (a full grab → release cycle)
   gives 38.4 / 142.4; with the feature turned off the numbers flip to 45.2 for Adwaita and 113.9
   for Breeze — i.e. before the fix you were looking at Adwaita. `/mousescrollfix cursor` shows
   which theme and which file it is currently using.

   **Why it is off by default**: this is the only place in the mod that calls `glfwSetCursor`, and
   on 2026-09-27 a large modpack run crashed — the crash was in GLFW tearing down its "mouse
   locked" objects when it hit an already-freed Wayland object pointer. That is a threading race
   between Ixeris (which moves GLFW event polling onto another thread) and the Wayland backend of
   the system GLFW 3.5.1; the root cause is outside this mod, but this mod's call touches that
   state, so it is left alone by default. The full chain of evidence is section 9 of
   [`FINDINGS.md`](FINDINGS.md) (written in Chinese). If you want the desktop-theme cursor, turn
   it on in the config screen — at your own risk.

4. **Under X11 / XWayland only half of it can be fixed**: the multiplier > 1 half (duplicated
   events) has been handled since v1.3.0, but the multiplier < 1 half (swallowed events) cannot be —
   those notches are gone before they reach the game, and no client-side mod can bring them back.
   The main menu shows a hint about this (`backend_hint = false` turns it off). Two further known
   edge cases: **free-spinning wheels** (no detents, can keep spinning) and **touchpads** can report
   hundreds of clicks per second under X11, and consecutive ones in the same frame get merged as
   duplicates; a mouse with detents physically cannot do that (it would need ≥ 125 notches/second),
   so normal wheels are unaffected.
5. **The wheel part does nothing on GNOME**: GNOME provides no per-device scroll multiplier (there
   is no `kcminputrc` equivalent), the events are ±1 to begin with, and there is nothing to fix.
   Native Wayland and the cursor theme still work normally there.
6. If native Wayland causes too much trouble in your modpack, **the simplest alternative** is to set
   that device's KDE scroll speed back to the default (1.0) — measured, X11 then gives 12 notches =
   12 events = 12 steps, exactly like Windows, with no mod needed at all. The price is that the
   device scrolls faster in every other program on your desktop too.
7. The self-test calls vanilla's `MouseHandler.onScroll` directly (bypassing the physical wheel),
   taking the **exact same** code path — it just does not require actually turning a wheel.

## 7. Game window larger than the screen (unrelated to this mod, but worth explaining)

Launchers pass the window size to the game as an argument. If the launcher has stored the
**physical** resolution while Wayland computes window sizes in **logical** pixels, that window
becomes "physical resolution × scale" — larger than the screen itself, and it will not fit no
matter where you place it. For example, storing 2560×1440 at a scale of 1.25 opens a window of
3200×1800 physical pixels on a 2560×1440 screen.

Logical size = physical resolution ÷ scale:

| Screen | Physical | Scale | Logical (largest usable window) |
|---|---|---|---|
| 2560×1440 | 2560×1440 | 1.25 | **2048×1152** |
| 2560×1600 | 2560×1600 | 1.4 | **1829×1143** |

Fix: launcher → instance settings / global game settings → set "window width/height" to something
no larger than the logical size in the table. `1920×1080` is a good choice (2400×1350 physical,
leaving a margin); `1600×900` is smaller still. Restart the game for it to take effect. To fill the
screen, press F11 for fullscreen in game — fullscreen is not affected by any of this.

## 8. Uninstalling

Delete `mods/mousescrollfix-1.4.0.jar` and remove the `-Dorg.lwjgl.glfw.libname` JVM argument
(removing it puts the game back on X11 and everything returns to how it was — your system settings
were never modified).

## 9. For developers

```bash
# Build (needs JDK 17; uses the bundled wrapper, pinned to Gradle 8.8)
JAVA_HOME=/usr/lib/jvm/java-17-openjdk ./gradlew build

# Normal dev client (X11)
JAVA_HOME=/usr/lib/jvm/java-17-openjdk ./gradlew runClient

# Native Wayland dev client (equivalent to the JVM argument from step 2 above)
JAVA_HOME=/usr/lib/jvm/java-17-openjdk ./gradlew runClient -Pwayland

# Jump straight into a world (skips the GUI)
JAVA_HOME=/usr/lib/jvm/java-17-openjdk ./gradlew runClient -Pwayland -Pquickplay=<world name>
```

`tools/` holds the verification scripts (Linux/KDE oriented, needs `/dev/uinput` access):
`wheel.py` / `aim_and_wheel.py` create a virtual mouse through `/dev/uinput` and make it pose as a
real device (so its desktop scroll multiplier applies), `run_probe.sh` starts a probe with the
same GLFW the game uses and prints the gap between wheel events, `summary.py` summarises the debug
log into "events received / duplicates merged / steps actually taken", `keys.py` sends key presses
via uinput (XTest key events never reach native Wayland clients), `kwin_query.js` queries window
geometry, and `click.py` sends real left clicks.

`make_logo.py` is the mod icon's **generator** (not a binary asset; needs Pillow): pixel art on a
64×64 grid. Change the colours and coordinates in the script and re-render with
`python3 tools/make_logo.py src/main/resources/logo.png --preview /tmp/x.png`; `--preview` also
writes a comparison sheet at various sizes on light and dark backgrounds, which is how you check
legibility when small.

Measured data and reproduction steps are in [`FINDINGS.md`](FINDINGS.md) (section 8 covers the
X11 / XWayland set; the document is in Chinese).

## 10. License

**GNU Lesser General Public License v3.0** (SPDX: `LGPL-3.0-only`).

- The full text is in [`LICENSE`](LICENSE); because LGPL-3.0 refers to GPL-3.0 in its terms,
  [`LICENSE.GPL-3.0`](LICENSE.GPL-3.0) is included as well. Both are packaged inside the jar too —
  just unpack it.
- **You are free to use, modify and redistribute this mod**: putting it in a modpack, recording
  videos with it, or using it on a server triggers no open-source obligation whatsoever, and you do
  not need to ask the author for permission.
- The one condition: **if you copy code from it into your own mod and publish that, your mod must
  also be released under an LGPL-3.0-compatible license**, keeping the original copyright notice
  and a description of your changes — this mod does not accept being copied into closed source.
