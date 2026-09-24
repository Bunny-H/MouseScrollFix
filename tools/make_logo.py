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

"""Draw the Mouse Scroll Fix logo.

The picture says one thing: the wheel drives exactly one hotbar slot. A mouse with a single lit
green wheel sits above a vanilla hotbar whose middle slot carries the white selection frame.

It is drawn on a 64x64 grid and scaled up with nearest-neighbour, so the icon stays crisp at any
size and the source of the logo is this file rather than a binary somebody has to re-edit.

Usage:
    make_logo.py [output.png] [--scale N] [--preview PATH]
"""

import argparse

from PIL import Image

GRID = 64

OUTLINE = (0x1F, 0x1F, 0x1F, 255)
BODY = (0x9E, 0x9E, 0x9E, 255)
BODY_LIGHT = (0xC2, 0xC2, 0xC2, 255)
DIVIDER = (0x3A, 0x3A, 0x3A, 255)
WHEEL = (0x55, 0xFF, 0x55, 255)          # Minecraft's green, one element only
WHEEL_KNURL = (0x2F, 0xA8, 0x2F, 255)

SLOT_FILL = (0x8B, 0x8B, 0x8B, 255)      # vanilla hotbar slot
SLOT_DARK = (0x2B, 0x2B, 0x2B, 255)
SLOT_LIGHT = (0xBC, 0xBC, 0xBC, 255)
SLOT_SHADE = (0x56, 0x56, 0x56, 255)
SELECTION = (0xFF, 0xFF, 0xFF, 255)      # vanilla selection frame

# Geometry, in grid cells.
MOUSE_CX, MOUSE_CY, MOUSE_A, MOUSE_B = 32.0, 18.0, 11.0, 15.5
WHEEL_HALF, WHEEL_TOP_OFFSET = 4, 7       # pill is 2*half wide, starts this far below the top edge
# The slots are spaced so the selection frame lands in the gaps instead of on its neighbours.
HOTBAR_TOP, SLOT_SIZE, SLOT_GAP, SLOT_COUNT, SELECTED = 42, 16, 4, 3, 1


class Canvas:
    def __init__(self):
        self.px = [[(0, 0, 0, 0)] * GRID for _ in range(GRID)]

    def set(self, x, y, color):
        if 0 <= x < GRID and 0 <= y < GRID:
            self.px[y][x] = color

    def rect(self, x0, y0, x1, y1, color):
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                self.set(x, y, color)

    def border(self, x0, y0, x1, y1, color, thickness=1):
        for i in range(thickness):
            self.rect(x0 + i, y0 + i, x1 - i, y0 + i, color)
            self.rect(x0 + i, y1 - i, x1 - i, y1 - i, color)
            self.rect(x0 + i, y0 + i, x0 + i, y1 - i, color)
            self.rect(x1 - i, y0 + i, x1 - i, y1 - i, color)

    def ellipse(self, cx, cy, a, b, color):
        for y in range(GRID):
            for x in range(GRID):
                dx, dy = (x + 0.5 - cx) / a, (y + 0.5 - cy) / b
                if dx * dx + dy * dy <= 1.0:
                    self.set(x, y, color)

    def to_image(self):
        img = Image.new("RGBA", (GRID, GRID))
        img.putdata([self.px[y][x] for y in range(GRID) for x in range(GRID)])
        return img


def draw_mouse(c):
    cx, cy, a, b = MOUSE_CX, MOUSE_CY, MOUSE_A, MOUSE_B
    c.ellipse(cx, cy, a + 1.0, b + 1.0, OUTLINE)
    c.ellipse(cx, cy, a, b, BODY)
    c.ellipse(cx - 1.2, cy - 1.4, a - 3.0, b - 3.6, BODY_LIGHT)

    top = int(cy - b) + 2
    wheel_y0 = int(cy - b) + WHEEL_TOP_OFFSET
    wheel_y1 = wheel_y0 + 10
    # Button split above the wheel, short dash below it: the classic mouse glyph.
    c.rect(int(cx) - 1, top, int(cx) - 1, wheel_y0 - 2, DIVIDER)
    c.rect(int(cx) - 1, wheel_y1 + 4, int(cx) - 1, wheel_y1 + 8, DIVIDER)
    return wheel_y0, wheel_y1


def draw_wheel(c, cx, y0, y1):
    x0, x1 = int(cx) - WHEEL_HALF, int(cx) + WHEEL_HALF - 1
    c.rect(x0 - 1, y0 - 1, x1 + 1, y1 + 1, OUTLINE)
    c.rect(x0, y0, x1, y1, WHEEL)
    for corner in ((x0, y0), (x1, y0), (x0, y1), (x1, y1)):
        c.set(*corner, OUTLINE)
    c.rect(x0 + 1, y0 + 3, x1 - 1, y0 + 3, WHEEL_KNURL)
    c.rect(x0 + 1, y0 + 6, x1 - 1, y0 + 6, WHEEL_KNURL)


def draw_slot(c, x0, y0):
    x1, y1 = x0 + SLOT_SIZE - 1, y0 + SLOT_SIZE - 1
    c.rect(x0, y0, x1, y1, SLOT_DARK)
    c.rect(x0 + 1, y0 + 1, x1 - 1, y1 - 1, SLOT_FILL)
    c.rect(x0 + 1, y0 + 1, x1 - 1, y0 + 1, SLOT_LIGHT)
    c.rect(x0 + 1, y0 + 1, x0 + 1, y1 - 1, SLOT_LIGHT)
    c.rect(x0 + 1, y1 - 1, x1 - 1, y1 - 1, SLOT_SHADE)
    c.rect(x1 - 1, y0 + 1, x1 - 1, y1 - 1, SLOT_SHADE)


def draw_hotbar(c):
    span = SLOT_COUNT * SLOT_SIZE + (SLOT_COUNT - 1) * SLOT_GAP
    left = (GRID - span) // 2
    for i in range(SLOT_COUNT):
        draw_slot(c, left + i * (SLOT_SIZE + SLOT_GAP), HOTBAR_TOP)
    marked = left + SELECTED * (SLOT_SIZE + SLOT_GAP)
    c.border(marked - 2, HOTBAR_TOP - 2,
             marked + SLOT_SIZE + 1, HOTBAR_TOP + SLOT_SIZE + 1, SELECTION, 2)


def render():
    c = Canvas()
    draw_wheel(c, MOUSE_CX, *draw_mouse(c))
    draw_hotbar(c)
    return c.to_image()


def preview(art, path):
    """Same icon at the sizes it actually gets shown at, on a dark and a light background."""
    sizes = [128, 96, 64, 48, 32, 16]
    row_h = 168
    width = 16 + sum(s + 24 for s in sizes)
    sheet = Image.new("RGBA", (width, row_h * 2))
    for index, background in enumerate(((24, 24, 24, 255), (238, 238, 238, 255))):
        row = Image.new("RGBA", (width, row_h), background)
        x = 16
        for size in sizes:
            row.alpha_composite(art.resize((size, size), Image.NEAREST), (x, (row_h - size) // 2))
            x += size + 24
        sheet.paste(row, (0, index * row_h))
    sheet.save(path)


def main():
    parser = argparse.ArgumentParser(description="Render the Mouse Scroll Fix logo.")
    parser.add_argument("output", nargs="?", default="logo.png")
    parser.add_argument("--scale", type=int, default=8, help="pixels per grid cell (8 -> 512x512)")
    parser.add_argument("--preview", default=None, help="also write a size/background sheet here")
    args = parser.parse_args()

    art = render()
    art.resize((GRID * args.scale, GRID * args.scale), Image.NEAREST).save(args.output)
    print(f"wrote {args.output} ({GRID * args.scale}x{GRID * args.scale})")
    if args.preview:
        preview(art, args.preview)
        print(f"wrote {args.preview}")


if __name__ == "__main__":
    main()
