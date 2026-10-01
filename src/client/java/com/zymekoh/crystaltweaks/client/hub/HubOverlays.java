package com.zymekoh.crystaltweaks.client.hub;

import com.zymekoh.crystaltweaks.client.CrystalAppearance;
import com.zymekoh.crystaltweaks.client.CrystalTheme;
import com.zymekoh.crystaltweaks.client.CrystalTweaksScreen;
import com.zymekoh.crystaltweaks.client.CrystalUi;
import com.zymekoh.crystaltweaks.client.PurpleCloseButton;
import com.zymekoh.crystaltweaks.client.hub.HubLayout.Rect;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

/**
 * Everything the hub draws over itself (the intro, the optimizer window, the change of menus) and
 * the two pages that fill the stage (KoHs and the enemy's Coming soon), behind one door, plus the
 * animated icons of the tabs and buttons.
 *
 * <p>Overlays answer input before anything under them: while one is up, {@link #blocking} is true
 * and the screen hands every click, scroll and key to it.</p>
 */
public final class HubOverlays {
    private static final Identifier MUSIC_DISC = HubDraw.vanilla("textures/item/music_disc_pigstep.png");
    private static final Identifier END_CRYSTAL = HubDraw.vanilla("textures/item/end_crystal.png");
    private static final Identifier PICKAXE = HubDraw.vanilla("textures/item/netherite_pickaxe.png");

    private final CrystalTweaksScreen screen;
    private final boolean spanish;
    private final KohsPanel kohs;
    private final ComingSoonPanel comingSoon = new ComingSoonPanel();
    private IntroOverlay intro;
    private OptimizerPopup popup;
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
        if (this.intro != null) {
            this.intro.layout(layoutFont, screenWidth, screenHeight);
        }
        if (this.popup != null) {
            this.popup.layout(layoutFont, screenWidth, screenHeight);
        }
    }

    // ------------------------------------------------------------------------------------------
    // Intro
    // ------------------------------------------------------------------------------------------

    public void showIntro() {
        this.intro = new IntroOverlay(this.spanish);
        if (this.font != null) {
            this.intro.layout(this.font, this.width, this.height);
        }
    }

    public Rect introCrystal() {
        return this.intro == null ? Rect.EMPTY : this.intro.crystalRect();
    }

    public boolean introGone(long now) {
        return this.intro != null && this.intro.gone(now);
    }

    public void clearIntro() {
        this.intro = null;
    }

    public void renderIntroBehind(GuiGraphicsExtractor graphics, long now, int halo, int crystalX, int crystalY, float crystalSize) {
        if (this.intro != null) {
            this.intro.renderBehind(graphics, now, halo, crystalX, crystalY, crystalSize);
        }
    }

    // ------------------------------------------------------------------------------------------
    // Optimizer window
    // ------------------------------------------------------------------------------------------

    public void showOptimizerPopup() {
        this.popup = new OptimizerPopup(this.spanish);
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
        return (this.intro != null && !this.intro.leaving()) || this.popup != null || this.transition != null;
    }

    public void mouseClicked(double mouseX, double mouseY) {
        long now = System.nanoTime();
        if (this.popup != null) {
            OptimizerPopup.Action action = this.popup.mouseClicked(mouseX, mouseY);
            if (action != OptimizerPopup.Action.NONE) {
                this.popup.leave(now);
                this.screen.popupClosed(action == OptimizerPopup.Action.DISMISS);
            }
            return;
        }
        if (this.intro != null && !this.intro.leaving()) {
            IntroOverlay.Action action = this.intro.mouseClicked(mouseX, mouseY);
            if (action != IntroOverlay.Action.NONE) {
                this.intro.leave(now);
                this.screen.introContinued(action == IntroOverlay.Action.DISMISS);
            }
        }
    }

    public void mouseScrolled(double amount, int lineHeight) {
        if (this.intro != null && this.popup == null) {
            this.intro.mouseScrolled(amount, lineHeight);
        }
    }

    /** Escape or Enter on an overlay: the window closes, the intro continues. */
    public boolean closeTopmost() {
        long now = System.nanoTime();
        if (this.popup != null) {
            this.popup.leave(now);
            this.screen.popupClosed(false);
            return true;
        }
        if (this.intro != null && !this.intro.leaving()) {
            this.intro.leave(now);
            this.screen.introContinued(false);
            return true;
        }
        return false;
    }

    public void render(GuiGraphicsExtractor graphics, Font drawFont, long now, float frameMillis, int mouseX, int mouseY,
            int screenWidth, int screenHeight) {
        if (this.transition != null) {
            this.transition.render(graphics, now, screenWidth, screenHeight);
        }
        if (this.intro != null) {
            this.intro.renderFront(graphics, drawFont, now, frameMillis, mouseX, mouseY);
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
        } else if (id.equals("advanced")) {
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

    // ------------------------------------------------------------------------------------------
    // Icons: the game's own items, moving
    // ------------------------------------------------------------------------------------------

    private static int whiteWith(int color) {
        return (color & 0xFF000000) | 0xFFFFFF;
    }

    /** A music disc turning, with a note rising off it now and then. */
    public static void soundIcon(GuiGraphicsExtractor graphics, int x, int y, int size, int color) {
        double seconds = System.nanoTime() / 1_000_000_000.0D;
        HubDraw.icon(graphics, MUSIC_DISC, x + size / 2.0F, y + size / 2.0F, size, (float) (seconds * 2.2D), whiteWith(color));
        double rise = (seconds * 0.8D) % 1.0D;
        int noteX = x + size - 2;
        int noteY = y + 1 - (int) Math.round(rise * 4.0D);
        int alpha = Math.round(255 * (1.0F - (float) rise) * ((color >>> 24) / 255.0F));
        noteIcon(graphics, noteX - 2, noteY - 2, 5, CrystalTheme.withAlpha(0xFF8AD8, alpha));
    }

    /** A small eighth note in pixels. */
    public static void noteIcon(GuiGraphicsExtractor graphics, int x, int y, int size, int color) {
        int s = Math.max(4, size);
        int stemX = x + s * 3 / 5;
        graphics.fill(stemX, y, stemX + 1, y + s - 1, color);
        graphics.fill(stemX, y, stemX + Math.max(2, s / 3), y + 1, color);
        graphics.fill(x + 1, y + s - 3, stemX + 1, y + s, color);
    }

    /** The End Crystal item, bobbing. */
    public static void tweaksIcon(GuiGraphicsExtractor graphics, int x, int y, int size, int color) {
        double seconds = System.nanoTime() / 1_000_000_000.0D;
        float bob = (float) Math.sin(seconds * 3.0D) * 0.8F;
        HubDraw.icon(graphics, END_CRYSTAL, x + size / 2.0F, y + size / 2.0F + bob, size, 0.0F, whiteWith(color));
    }

    public static void kohsIcon(GuiGraphicsExtractor graphics, int x, int y, int size, int color) {
        HubDraw.texture(graphics, KohsPanel.mark(), 96, 96, x + size / 2.0F, y + size / 2.0F, size, size, 0.0F, whiteWith(color));
    }

    /** The End Crystal item in red: someone else's. */
    public static void profileIcon(GuiGraphicsExtractor graphics, int x, int y, int size, int color) {
        HubDraw.icon(graphics, END_CRYSTAL, x + size / 2.0F, y + size / 2.0F, size, 0.0F, (color & 0xFF000000) | 0xFF8A8A);
    }

    /** A netherite pickaxe, rocking as if about to swing. */
    public static void advancedIcon(GuiGraphicsExtractor graphics, int x, int y, int size, int color) {
        double seconds = System.nanoTime() / 1_000_000_000.0D;
        float rock = (float) Math.sin(seconds * 4.0D) * 0.25F;
        HubDraw.icon(graphics, PICKAXE, x + size / 2.0F, y + size / 2.0F, size, rock, whiteWith(color));
    }

    public static void enemyCrystalIcon(GuiGraphicsExtractor graphics, int x, int y, int size, int color) {
        crystalGlyph(graphics, x, y, size, 0xFFFF4A5E, 0xFFFFC2C8, color);
    }

    public static void ownCrystalIcon(GuiGraphicsExtractor graphics, int x, int y, int size, int color) {
        crystalGlyph(graphics, x, y, size, 0xFFB064F0, 0xFF86ECFF, color);
    }

    private static void crystalGlyph(GuiGraphicsExtractor graphics, int x, int y, int size, int outer, int inner, int color) {
        float alpha = (color >>> 24) / 255.0F;
        double seconds = System.nanoTime() / 1_000_000_000.0D;
        float pulse = HubMotion.breathe(seconds, 1.4D);
        int half = Math.max(2, size / 2);
        int centerX = x + half;
        int centerY = y + half;
        int body = CrystalTheme.fade(CrystalTheme.lerp(outer, 0xFFFFFFFF, 0.15F * pulse), alpha);
        for (int row = -half; row <= half; row++) {
            int span = half - Math.abs(row);
            graphics.fill(centerX - span, centerY + row, centerX + span + 1, centerY + row + 1, body);
        }
        int core = Math.max(1, half / 2);
        int coreColor = CrystalTheme.fade(inner, alpha);
        for (int row = -core; row <= core; row++) {
            int span = core - Math.abs(row);
            graphics.fill(centerX - span, centerY + row, centerX + span + 1, centerY + row + 1, coreColor);
        }
    }

    public static void chartIcon(GuiGraphicsExtractor graphics, int x, int y, int size, int color) {
        CrystalUi.icon(graphics, PurpleCloseButton.Icon.CHART, x + (size - 7) / 2, y + (size - 7) / 2, color);
    }

    public static void gearIcon(GuiGraphicsExtractor graphics, int x, int y, int size, int color) {
        CrystalUi.icon(graphics, PurpleCloseButton.Icon.GEAR, x + (size - 7) / 2, y + (size - 7) / 2, color);
    }
}
