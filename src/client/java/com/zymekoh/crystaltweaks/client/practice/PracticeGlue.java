package com.zymekoh.crystaltweaks.client.practice;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;

/**
 * Where Minecraft keeps what {@link PracticeHost} switches between worlds, in this version. 26.2
 * moved the screen into the GUI and renamed the camera and the HUD, so 26.2 and 26.3 have their own
 * copy of this class under {@code src/gui}.
 */
final class PracticeGlue {
    private PracticeGlue() {
    }

    /** The screen field itself, with none of what opening or closing a screen does. */
    static Screen rawScreen(Minecraft minecraft) {
        return minecraft.screen;
    }

    static void setRawScreen(Minecraft minecraft, Screen screen) {
        minecraft.screen = screen;
    }

    /** Opens a screen as the game does. */
    static void setScreen(Minecraft minecraft, Screen screen) {
        minecraft.setScreen(screen);
    }

    static Camera camera(Minecraft minecraft) {
        return minecraft.gameRenderer.getMainCamera();
    }

    static BossHealthOverlay bossOverlay(Minecraft minecraft) {
        return minecraft.gui.getBossOverlay();
    }

    static void chat(Minecraft minecraft, Component message) {
        minecraft.gui.getChat().addClientSystemMessage(message);
    }

    /** 26.1's renderer looks the chunks up in the world itself when it takes a world back: nothing to report. */
    static void announceChunks(ClientLevel level, int centerX, int centerZ) {
    }
}
