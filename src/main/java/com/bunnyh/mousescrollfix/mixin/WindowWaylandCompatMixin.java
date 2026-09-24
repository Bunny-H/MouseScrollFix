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
import com.mojang.blaze3d.platform.Window;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static org.lwjgl.system.MemoryUtil.memUTF8Safe;

/**
 * The second half of "let Minecraft 1.20.1 run on native Wayland".
 *
 * <p>Wayland deliberately cannot do some window-manager things, and GLFW reports each of them as
 * {@code GLFW_FEATURE_UNAVAILABLE (0x1000C)} through the currently installed error callback:
 *
 * <ul>
 *   <li>"Wayland: The platform does not support setting the window icon" - raised by
 *       {@code glfwSetWindowIcon}, which {@code Window.setIcon} calls while the boot splash is up,
 *       so {@code Window.bootCrash} pops a modal "please update your drivers" dialog and the game
 *       never gets past "Forge loading".</li>
 *   <li>"Wayland: The platform does not provide the window position" - raised by
 *       {@code glfwGetWindowPos}/{@code glfwSetWindowPos}.</li>
 * </ul>
 *
 * <p>Neither is an actual failure: the icon simply is not set, and the position is whatever the
 * compositor chose. Both callbacks are therefore made to skip only that single error code, and to
 * log it instead. Every other GLFW error still takes the vanilla path.
 */
@OnlyIn(Dist.CLIENT)
@Mixin(Window.class)
public class WindowWaylandCompatMixin {

    private static final int GLFW_FEATURE_UNAVAILABLE = 0x0001000C;

    @Inject(method = "bootCrash(IJ)V", at = @At("HEAD"), cancellable = true)
    private static void mousescrollfix$tolerateBoot(int error, long description, CallbackInfo ci) {
        if (error == GLFW_FEATURE_UNAVAILABLE) {
            MouseScrollFix.LOGGER.warn("[mousescrollfix] ignored non-fatal GLFW error during boot: {}",
                    memUTF8Safe(description));
            ci.cancel();
        }
    }

    @Inject(method = "defaultErrorCallback(IJ)V", at = @At("HEAD"), cancellable = true)
    private void mousescrollfix$tolerateRuntime(int error, long description, CallbackInfo ci) {
        if (error == GLFW_FEATURE_UNAVAILABLE) {
            MouseScrollFix.LOGGER.warn("[mousescrollfix] ignored non-fatal GLFW error: {}",
                    memUTF8Safe(description));
            ci.cancel();
        }
    }
}
