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

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.fml.ModLoadingContext;

/**
 * The settings screen behind the "Config" button in the Mods list.
 *
 * <p>Forge 1.20.1 ships no screen for {@code ModConfigSpec} configs, so without this the options
 * would only exist in the toml file. Only the pointer switch is on it: it is the one setting that
 * makes the mod call {@code glfwSetCursor}, which is where the game has been seen to crash (see
 * FINDINGS.md), so it has to be reachable and it has to explain itself.
 */
public final class ScrollFixConfigScreen extends Screen {

    private static final int WIDTH = 310;

    private final Screen parent;

    private ScrollFixConfigScreen(Screen parent) {
        super(Component.translatable("mousescrollfix.config.title"));
        this.parent = parent;
    }

    /** Registers the screen for the Mods list. Client-only code; never runs on a dedicated server. */
    public static void register() {
        ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory(
                        (mc, parent) -> new ScrollFixConfigScreen(parent)));
    }

    @Override
    protected void init() {
        int center = this.width / 2;
        int top = this.height / 2 - 24;

        addRenderableWidget(Button.builder(cursorLabel(), button -> {
                    ScrollFixConfig.setFixCursorTheme(!ScrollFixConfig.fixCursorTheme);
                    button.setMessage(cursorLabel());
                })
                .bounds(center - WIDTH / 2, top, WIDTH, 20)
                .tooltip(Tooltip.create(Component.translatable("mousescrollfix.config.cursor.tooltip")))
                .build());

        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
                .bounds(center - WIDTH / 2, top + 48, WIDTH, 20)
                .build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 20, 0xFFFFFF);
        graphics.drawCenteredString(this.font, Component.translatable("mousescrollfix.config.cursor.hint"),
                this.width / 2, this.height / 2 + 2, 0xA0A0A0);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }

    /** Button text: which pointer the game is currently set to use. */
    private static Component cursorLabel() {
        return Component.translatable("mousescrollfix.config.cursor",
                Component.translatable(ScrollFixConfig.fixCursorTheme
                        ? "mousescrollfix.config.cursor.theme"
                        : "mousescrollfix.config.cursor.default"));
    }
}
