package com.zymekoh.crystaltweaks.client.hub;

import com.zymekoh.crystaltweaks.client.CrystalAppearance;
import com.zymekoh.crystaltweaks.client.CrystalTweaksScreen;
import com.zymekoh.crystaltweaks.client.hub.HubLayout.Rect;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Everything the hub draws over itself (a notice, the change of menus) and the two pages that fill
 * the body (KoHs and the enemy's Coming soon), behind one door.
 *
 * <p>Overlays answer input before anything under them: while one is up, {@link #blocking} is true
 * and the screen hands every click, scroll and key to it.</p>
 */
public final class HubOverlays {
    private final CrystalTweaksScreen screen;
    private final boolean spanish;
    private final KohsPanel kohs;
    private final ComingSoonPanel comingSoon = new ComingSoonPanel();
    private NoticePopup popup;
    private ModeTransition transition;
    private boolean landedReported;
    private Font font;
    private int width;
    private int height;

    public HubOverlays(CrystalTweaksScreen screen, boolean spanish) {
        this.screen = screen;
        this.spanish = spanish;
        this.kohs = new KohsPanel(screen, spanish);
    }

    public void layout(Font layoutFont, int screenWidth, int screenHeight) {
        this.font = layoutFont;
        this.width = screenWidth;
        this.height = screenHeight;
        if (this.popup != null) {
            this.popup.layout(layoutFont, screenWidth, screenHeight);
        }
    }

    // ------------------------------------------------------------------------------------------
    // Notice
    // ------------------------------------------------------------------------------------------

    public void showNotice(String title, String text, String status) {
        this.popup = new NoticePopup(this.spanish, title, text, status);
        if (this.font != null) {
            this.popup.layout(this.font, this.width, this.height);
        }
    }

    // ------------------------------------------------------------------------------------------
    // Change of menus
    // ------------------------------------------------------------------------------------------

    public void startTransition(long now, List<Rect> outgoing, int impactX, int impactY, int oldTop, int oldBottom, int oldBorder,
            int oldHalo, List<Rect> incoming, int newTop, int newBottom, int newBorder, int newAccent) {
        this.transition = new ModeTransition(now, outgoing, impactX, impactY, oldTop, oldBottom, oldBorder, oldHalo, incoming,
                newTop, newBottom, newBorder, newAccent, this.width, this.height);
        this.landedReported = false;
    }

    /** Reports the landing and the end of the change to the screen, once each. */
    public void tickTransition(long now) {
        if (this.transition == null) {
            return;
        }
        if (!this.landedReported && this.transition.landed(now)) {
            this.landedReported = true;
            this.screen.transitionLanded();
        }
        if (this.transition.finished(now)) {
            this.transition = null;
            this.screen.transitionFinished();
        }
    }

    public boolean transitionLanded(long now) {
        return this.transition != null && this.transition.landed(now);
    }

    /** Drops a change still in progress: the window was resized in the middle of it. */
    public void endTransition() {
        if (this.transition != null && !this.landedReported) {
            this.landedReported = true;
            this.screen.transitionLanded();
        }
        this.transition = null;
    }

    // ------------------------------------------------------------------------------------------
    // Input and drawing
    // ------------------------------------------------------------------------------------------

    public boolean blocking() {
        return this.popup != null || this.transition != null;
    }

    public void mouseClicked(double mouseX, double mouseY) {
        long now = System.nanoTime();
        if (this.popup != null) {
            NoticePopup.Action action = this.popup.mouseClicked(mouseX, mouseY);
            if (action != NoticePopup.Action.NONE) {
                this.popup.leave(now);
                this.screen.popupClosed(action == NoticePopup.Action.DISMISS);
            }
        }
    }

    public void mouseScrolled(double amount, int lineHeight) {
    }

    /** Escape or Enter on an overlay: the notice closes. */
    public boolean closeTopmost() {
        long now = System.nanoTime();
        if (this.popup != null) {
            this.popup.leave(now);
            this.screen.popupClosed(false);
            return true;
        }
        return false;
    }

    public void render(GuiGraphicsExtractor graphics, Font drawFont, long now, float frameMillis, int mouseX, int mouseY,
            int screenWidth, int screenHeight) {
        if (this.transition != null) {
            this.transition.render(graphics, now, screenWidth, screenHeight);
        }
        if (this.popup != null) {
            this.popup.render(graphics, drawFont, now, frameMillis, mouseX, mouseY, screenWidth, screenHeight);
            if (this.popup.gone(now)) {
                this.popup = null;
            }
        }
    }

    // ------------------------------------------------------------------------------------------
    // The full-stage pages
    // ------------------------------------------------------------------------------------------

    public void drawerOpened(String id) {
        if (id.equals("kohs")) {
            this.kohs.enter();
        } else if (id.equals("soon")) {
            this.comingSoon.enter();
        }
    }

    public void renderKohs(GuiGraphicsExtractor graphics, Font drawFont, Rect area, int mouseX, int mouseY, float alpha, float frameMillis) {
        this.kohs.render(graphics, drawFont, area, mouseX, mouseY, alpha, frameMillis);
    }

    public boolean kohsClicked(Font clickFont, Rect area, double mouseX, double mouseY) {
        return this.kohs.mouseClicked(clickFont, area, mouseX, mouseY);
    }

    public void renderComingSoon(GuiGraphicsExtractor graphics, Font drawFont, Rect area, long now, int mouseX, int mouseY,
            CrystalAppearance look, float alpha) {
        this.comingSoon.render(graphics, drawFont, area, now, mouseX, mouseY, look, this.spanish, alpha);
    }

    public boolean comingSoonClicked(double mouseX, double mouseY) {
        return this.comingSoon.mouseClicked(mouseX, mouseY);
    }
}
