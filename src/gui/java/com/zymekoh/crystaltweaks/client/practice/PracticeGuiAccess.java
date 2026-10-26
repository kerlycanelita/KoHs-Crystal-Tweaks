package com.zymekoh.crystaltweaks.client.practice;

import net.minecraft.client.gui.screens.Screen;

/** The GUI's screen field, read and written without opening or closing anything (26.2 and 26.3). */
public interface PracticeGuiAccess {
    Screen crystalTweaks$screen();

    void crystalTweaks$setScreen(Screen screen);
}
