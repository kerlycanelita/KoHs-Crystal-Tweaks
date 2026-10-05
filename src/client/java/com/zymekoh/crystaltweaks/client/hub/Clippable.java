package com.zymekoh.crystaltweaks.client.hub;

import com.zymekoh.crystaltweaks.client.hub.HubLayout.Rect;

/**
 * A control drawn inside a scrolling list. A row that is half out of view is drawn half, clipped to
 * the list, and it takes clicks only on the half that shows: the hitbox is always what is drawn.
 */
public interface Clippable {
    /** The area the control may be clicked in; {@link Rect#EMPTY} for anywhere. */
    void clipTo(Rect area);

    /** Whether a point is inside the clip, for the control's own {@code isMouseOver}. */
    static boolean inside(Rect clip, double mouseX, double mouseY) {
        return clip == null || clip.isEmpty() || clip.contains(mouseX, mouseY);
    }
}
