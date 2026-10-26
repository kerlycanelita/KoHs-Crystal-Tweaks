package com.zymekoh.crystaltweaks.client;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/**
 * The settings' screens are drawn at GUI scale 3 whatever the game's own is set to: the scale they
 * were made at, so they look the same for everyone. A window too small for it steps down to the
 * largest scale that still leaves them room. The player's own scale comes back as the screen goes.
 */
public final class FixedGuiScale {
    private static final int SCALE = 3;
    /** The least room, in GUI pixels, the settings are comfortable in. */
    private static final int MIN_WIDTH = 520;
    private static final int MIN_HEIGHT = 290;

    private FixedGuiScale() {
    }

    /**
     * Puts the window at the settings' scale. Call first thing in {@code init}: when the scale
     * changes, the screen takes the new size before laying anything out.
     */
    public static void apply(Screen screen, Minecraft minecraft) {
        Window window = minecraft.getWindow();
        int scale = SCALE;
        while (scale > 1 && (window.getWidth() / scale < MIN_WIDTH || window.getHeight() / scale < MIN_HEIGHT)) {
            scale--;
        }
        if (window.getGuiScale() != scale) {
            window.setGuiScale(scale);
        }
        screen.width = window.getGuiScaledWidth();
        screen.height = window.getGuiScaledHeight();
    }

    /** The player's own scale back, as the game itself sets it. Call from {@code removed}. */
    public static void restore(Minecraft minecraft) {
        Window window = minecraft.getWindow();
        window.setGuiScale(window.calculateScale(minecraft.options.guiScale().get(), minecraft.isEnforceUnicode()));
    }
}
