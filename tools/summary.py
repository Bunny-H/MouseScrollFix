#!/usr/bin/env python3
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

"""Summarise logs/mousescrollfix-debug.log: events received, duplicates merged, slots moved.

Usage: summary.py [path-to-log]   (default: run/logs/mousescrollfix-debug.log)
"""
import re
import sys

path = sys.argv[1] if len(sys.argv) > 1 else "run/logs/mousescrollfix-debug.log"

line_re = re.compile(
    r"^t=(\d+) raw=([+-][\d.]+) norm=([+-][\d.]+) gapUs=(-?\d+) ctx=(\S+)\s+slotBefore=(-?\d+)(.*)$")

events = []
for raw in open(path, encoding="utf-8", errors="replace"):
    m = line_re.match(raw.strip())
    if m:
        events.append({
            "t": int(m.group(1)),
            "raw": float(m.group(2)),
            "gap": int(m.group(4)),
            "ctx": m.group(5),
            "slot": int(m.group(6)),
            "dup": "DUPLICATE-DROPPED" in m.group(7),
        })

if not events:
    raise SystemExit(f"no scroll events in {path}")

dups = [e for e in events if e["dup"]]
accepted = [e for e in events if not e["dup"]]

print(f"events received  : {len(events)}   (of which duplicates merged: {len(dups)})")
print(f"scroll steps     : {len(accepted)}")
gaps = sorted(e["gap"] for e in dups)
if gaps:
    print(f"duplicate gap    : {gaps[0]} us .. {gaps[-1]} us")

# A step is one hotbar slot; the slot path shows whether every notch moved exactly one.
path_slots = [e["slot"] for e in accepted] + [(accepted[-1]["slot"] - 1) % 9]
deltas = []
for a, b in zip(path_slots, path_slots[1:]):
    d = b - a
    deltas.append(d + 9 if d < -4 else (d - 9 if d > 4 else d))
print(f"slot path        : {path_slots}")
print(f"steps per event  : {deltas}")
print("RESULT           : " + ("every step moved exactly one slot" if all(d == -1 for d in deltas)
                               else "SOME EVENTS MOVED MORE THAN ONE SLOT"))
