package com.zymekoh.crystaltweaks.client.hub;

import com.zymekoh.crystaltweaks.client.hub.HubLayout.Rect;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/** The game's text box, clipped to the list it scrolls in (see {@link Clippable}). */
public final class HubEditBox extends EditBox implements Clippable {
    private Rect clip = Rect.EMPTY;

    public HubEditBox(Font font, int x, int y, int width, int height, Component message) {
        super(font, x, y, width, height, message);
    }

    @Override
    public void clipTo(Rect area) {
        this.clip = area;
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return super.isMouseOver(mouseX, mouseY) && Clippable.inside(this.clip, mouseX, mouseY);
    }
}
