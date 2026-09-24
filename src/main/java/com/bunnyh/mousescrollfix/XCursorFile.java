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

package com.bunnyh.mousescrollfix;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;

import org.lwjgl.system.MemoryUtil;

/**
 * Minimal reader for the Xcursor file format ("Xcur"), which is what every Linux cursor theme
 * stores its pointers in.
 *
 * <p>Layout: a 16-byte header, then {@code ntoc} table-of-contents entries of 12 bytes each, then
 * the chunks those entries point at. An image chunk is a 36-byte header followed by
 * {@code width * height} premultiplied ARGB pixels, top-to-bottom. Every entry in a theme file is
 * the same arrow at a different nominal size, so picking one is a matter of nearest size.
 */
final class XCursorFile {

    private static final int MAGIC = 0x72756358; // "Xcur" as a little-endian int
    private static final int IMAGE_TYPE = 0xFFFD0002;
    private static final int IMAGE_HEADER_BYTES = 36;
    private static final int MAX_DIMENSION = 512;

    /** The theme's own name for this size, e.g. 24 - not the pixel size of the image. */
    final int nominalSize;
    final int width;
    final int height;
    final int hotspotX;
    final int hotspotY;
    /**
     * Non-premultiplied RGBA, top-to-bottom: the layout {@code GLFWImage} expects. Direct buffer,
     * so the caller owns it and must release it with {@code MemoryUtil.memFree} once the cursor has
     * been created.
     */
    final ByteBuffer rgba;

    private XCursorFile(int nominalSize, int width, int height, int hotspotX, int hotspotY, ByteBuffer rgba) {
        this.nominalSize = nominalSize;
        this.width = width;
        this.height = height;
        this.hotspotX = hotspotX;
        this.hotspotY = hotspotY;
        this.rgba = rgba;
    }

    static XCursorFile load(Path file, int targetSize) throws IOException {
        byte[] data = Files.readAllBytes(file);
        if (data.length < 16) {
            throw new IOException("too short to be an Xcursor file");
        }
        ByteBuffer buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        if (buf.getInt(0) != MAGIC) {
            throw new IOException("not an Xcursor file (bad magic)");
        }
        int headerSize = buf.getInt(4);
        int entries = buf.getInt(12);
        if (headerSize < 16 || entries <= 0 || headerSize + entries * 12 > data.length) {
            throw new IOException("corrupt table of contents");
        }

        int bestOffset = -1;
        int bestNominal = -1;
        int bestDistance = Integer.MAX_VALUE;
        for (int i = 0; i < entries; i++) {
            int at = headerSize + i * 12;
            if (buf.getInt(at) != IMAGE_TYPE) {
                continue;
            }
            int nominal = buf.getInt(at + 4);
            int offset = buf.getInt(at + 8);
            int distance = Math.abs(nominal - targetSize);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestNominal = nominal;
                bestOffset = offset;
            }
        }
        if (bestOffset < 0) {
            throw new IOException("no image chunks");
        }

        int width = buf.getInt(bestOffset + 16);
        int height = buf.getInt(bestOffset + 20);
        int hotspotX = buf.getInt(bestOffset + 24);
        int hotspotY = buf.getInt(bestOffset + 28);
        if (width <= 0 || height <= 0 || width > MAX_DIMENSION || height > MAX_DIMENSION) {
            throw new IOException("implausible image size " + width + "x" + height);
        }
        long pixels = (long) width * height;
        long end = (long) bestOffset + IMAGE_HEADER_BYTES + pixels * 4L;
        if (end > data.length) {
            throw new IOException("image data runs past the end of the file");
        }

        ByteBuffer out = MemoryUtil.memAlloc((int) pixels * 4);
        try {
            for (long i = 0; i < pixels; i++) {
                int argb = buf.getInt((int) (bestOffset + IMAGE_HEADER_BYTES + i * 4));
                int a = (argb >>> 24) & 0xFF;
                if (a == 0) {
                    out.put((byte) 0).put((byte) 0).put((byte) 0).put((byte) 0);
                    continue;
                }
                int r = (argb >>> 16) & 0xFF;
                int g = (argb >>> 8) & 0xFF;
                int b = argb & 0xFF;
                if (a != 0xFF) {
                    // Xcursor stores premultiplied alpha; GLFW wants straight RGBA.
                    r = Math.min(255, r * 255 / a);
                    g = Math.min(255, g * 255 / a);
                    b = Math.min(255, b * 255 / a);
                }
                out.put((byte) r).put((byte) g).put((byte) b).put((byte) a);
            }
        } catch (Throwable t) {
            MemoryUtil.memFree(out);
            throw t;
        }
        out.flip();

        return new XCursorFile(bestNominal, width, height,
                Math.max(0, Math.min(width - 1, hotspotX)),
                Math.max(0, Math.min(height - 1, hotspotY)),
                out);
    }
}
