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

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Forge 1.20.1's @Mod has no dist() member; client-only loading is enforced by
// clientSideOnly=true in META-INF/mods.toml instead.
@Mod(MouseScrollFix.MODID)
public class MouseScrollFix {

    public static final String MODID = "mousescrollfix";
    public static final Logger LOGGER = LoggerFactory.getLogger("MouseScrollFix");

    public MouseScrollFix() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, ScrollFixConfig.SPEC);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(ScrollFixConfig::onConfigEvent);
        // Without a registered screen the "Config" button in the Mods list does not exist at all
        // in 1.20.1, and the pointer switch would only be reachable by editing the file.
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> ScrollFixConfigScreen::register);
    }
}
