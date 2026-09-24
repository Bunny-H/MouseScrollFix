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

package com.bunnyh.mousescrollfix.mixin;

import com.bunnyh.mousescrollfix.MouseScrollFix;
import com.mojang.blaze3d.platform.GLX;
import com.mojang.blaze3d.platform.Window;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.function.BiConsumer;

/**
 * Lets Minecraft 1.20.1 start on native Wayland.
 *
 * <p>{@code GLX._initGlfw} polls the GLFW error queue before calling {@code glfwInit} and treats
 * <em>any</em> queued error as fatal. GLFW's Wayland backend legitimately reports
 * {@code GLFW_FEATURE_UNAVAILABLE (0x1000C)} "The platform does not provide the window position",
 * because Wayland deliberately does not let a client know where its window is. That single
 * non-fatal, purely informational error is what makes the vanilla client crash instantly:
 *
 * <pre>
 * java.lang.IllegalStateException: GLFW error before init: [0x1000C]Wayland: The platform does not
 * provide the window position
 *     at com.mojang.blaze3d.platform.GLX.lambda$_initGlfw$0(GLX.java:60)
 *     at com.mojang.blaze3d.platform.Window.checkGlfwError(Window.java:133)
 *     at com.mojang.blaze3d.platform.GLX._initGlfw(GLX.java:59)
 * </pre>
 *
 * <p>Only that one error code is tolerated; every other GLFW error still aborts startup exactly as
 * vanilla does, so real GLFW problems are not hidden.
 */
@Mixin(GLX.class)
public class GlxWaylandCompatMixin {

    private static final int GLFW_FEATURE_UNAVAILABLE = 0x0001000C;

    @Redirect(
            method = "_initGlfw",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/platform/Window;checkGlfwError(Ljava/util/function/BiConsumer;)V"
            )
    )
    private static void mousescrollfix$tolerateWaylandFeatureUnavailable(BiConsumer<Integer, String> fatal) {
        Window.checkGlfwError((code, description) -> {
            if (code != null && code == GLFW_FEATURE_UNAVAILABLE) {
                MouseScrollFix.LOGGER.warn(
                        "[mousescrollfix] ignored non-fatal GLFW error before init: [0x{}]{}",
                        Integer.toHexString(code), description);
                return;
            }
            fatal.accept(code, description);
        });
    }
}
