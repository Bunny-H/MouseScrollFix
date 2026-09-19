package com.bunnyh.mousescrollfix;

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
    }
}
