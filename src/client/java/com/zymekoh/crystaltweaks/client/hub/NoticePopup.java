package com.zymekoh.crystaltweaks.client.hub;

import com.zymekoh.crystaltweaks.client.CrystalTheme;
import com.zymekoh.crystaltweaks.client.CrystalUi;
import com.zymekoh.crystaltweaks.client.hub.HubLayout.Rect;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.FormattedCharSequence;

/**
 * A notice that drops in over the hub as a leaf of the book: its title in gothic letters beside the
 * crystal's mark, the words, and a note in the margin for what matters right now. "Got it" closes
 * it; "Don't show again" closes it for good.
 */
final class NoticePopup {
    enum Action { NONE, CLOSE, DISMISS }

    private static final long DROP_NANOS = 420_000_000L;
    private static final long LEAVE_NANOS = 220_000_000L;
    /** The title's band, from the leaf's inner rule to the rule under it. */
    private static final int HEAD = 24;
    private static final HubButton.Icon MARK = GrimoireIcons.icon(GrimoireIcons.Sprite.END_CRYSTAL, 0.3F);

    private final OverlayButton dismiss;
    private final OverlayButton close;
    private final long shownAt = System.nanoTime();
    private final String title;
    private final String text;
    private final String statusText;
    private final List<FormattedCharSequence> lines = new ArrayList<>();
    private List<FormattedCharSequence> status = List.of();
    private long leavingAt = Long.MIN_VALUE;
    private Rect card = Rect.EMPTY;
    private int textTop;

    NoticePopup(boolean spanish, String title, String text, String status) {
        this.title = title;
        this.text = text;
        this.statusText = status;
        this.dismiss = new OverlayButton(spanish ? "No volver a mostrar" : "Don't show again", false);
        this.close = new OverlayButton(spanish ? "Entendido" : "Got it", true);
    }

    void layout(Font font, int width, int height) {
        int cardWidth = Math.min(320, width - 16);
        int textWidth = cardWidth - 24;
        this.lines.clear();
        this.lines.addAll(CrystalUi.wrap(font, this.text, textWidth));
        this.status = CrystalUi.wrap(font, this.statusText, textWidth - 6);
        int cardHeight = HEAD + 8 + this.lines.size() * GrimoireDraw.line(font) + 8 + this.status.size() * GrimoireDraw.line(font) + 12 + 30;
        cardHeight = Math.min(height - 12, cardHeight);
        this.card = new Rect((width - cardWidth) / 2, (height - cardHeight) / 2, cardWidth, cardHeight);
        this.textTop = this.card.y() + HEAD + 8;
        int buttonHeight = 18;
        int gap = 6;
        int buttonWidth = Math.max(60, Math.min(140, (cardWidth - 24 - gap) / 2));
        int buttonsX = this.card.x() + (cardWidth - buttonWidth * 2 - gap) / 2;
        int buttonsY = this.card.bottom() - buttonHeight - 9;
        this.dismiss.setRect(new Rect(buttonsX, buttonsY, buttonWidth, buttonHeight));
        this.close.setRect(new Rect(buttonsX + buttonWidth + gap, buttonsY, buttonWidth, buttonHeight));
    }

    void leave(long now) {
        if (this.leavingAt == Long.MIN_VALUE) {
            this.leavingAt = now;
        }
    }

    boolean gone(long now) {
        return this.leavingAt != Long.MIN_VALUE && now - this.leavingAt >= LEAVE_NANOS;
    }

    Action mouseClicked(double mouseX, double mouseY) {
        if (this.leavingAt != Long.MIN_VALUE || System.nanoTime() - this.shownAt < DROP_NANOS) {
            return Action.NONE;
        }
        if (this.close.contains(mouseX, mouseY)) {
            this.close.press();
            return Action.CLOSE;
        }
        if (this.dismiss.contains(mouseX, mouseY)) {
            this.dismiss.press();
            return Action.DISMISS;
        }
        return Action.NONE;
    }

    void render(GuiGraphicsExtractor graphics, Font font, long now, float frameMillis, int mouseX, int mouseY, int screenWidth,
            int screenHeight) {
        float drop = HubMotion.easeOutBack(HubMotion.progress(now - this.shownAt, DROP_NANOS));
        float fade = HubMotion.clamp01(HubMotion.progress(now - this.shownAt, DROP_NANOS / 2));
        if (this.leavingAt != Long.MIN_VALUE) {
            fade *= 1.0F - HubMotion.progress(now - this.leavingAt, LEAVE_NANOS);
        }
        if (fade <= 0.02F) {
            return;
        }
        HubDraw.isolate(graphics);
        graphics.fill(0, 0, screenWidth, screenHeight, CrystalTheme.withAlpha(0x05020A, Math.round(150 * fade)));
        int offset = Math.round((1.0F - drop) * -40.0F);
        Rect card = new Rect(this.card.x(), this.card.y() + offset, this.card.width(), this.card.height());
        // A leaf of the book, a shadow under it.
        graphics.fill(card.x() + 3, card.y() + 4, card.right() + 3, card.bottom() + 4, CrystalTheme.withAlpha(0x000000, Math.round(110 * fade)));
        GrimoireDraw.page(graphics, card, GrimoireDraw.PAGE_DEEP, fade);
        int inset = GrimoireDraw.inset(card);
        int ruleY = card.y() + HEAD;
        int bandY = card.y() + inset + 1;
        int band = ruleY - bandY;
        int markSize = Math.min(14, band - 2);
        MARK.draw(graphics, card.x() + 10, bandY + (band - markSize) / 2, markSize, CrystalTheme.fade(0xFFFFFFFF, fade));
        int titleX = card.x() + 10 + markSize + 5;
        float scale = Math.min(GrimoireDraw.gothicFit(band, 1.0F),
                (card.right() - 10 - titleX) / (float) Math.max(1, GrimoireDraw.gothicWidth(font, this.title, 1.0F)));
        GrimoireDraw.gothic(graphics, font, this.title, titleX, GrimoireDraw.gothicY(bandY, band, scale), scale,
                CrystalTheme.fade(GrimoireDraw.TITLE, fade), 0.0F);
        graphics.fill(card.x() + 10, ruleY, card.right() - 10, ruleY + 1, CrystalTheme.fade(GrimoireDraw.RULE_TITLE, fade));
        int lineY = this.textTop + offset;
        for (FormattedCharSequence line : this.lines) {
            if (lineY + GrimoireDraw.line(font) > this.close.rect().y() + offset - 4) {
                break;
            }
            HubDraw.line(graphics, font, line, card.x() + 12, lineY, CrystalTheme.fade(GrimoireDraw.TEXT, fade));
            lineY += GrimoireDraw.line(font);
        }
        lineY += 6;
        // The note in the margin: a violet rule beside it, as a hand would mark a page.
        int noteHeight = this.status.size() * GrimoireDraw.line(font) + 4;
        if (!this.status.isEmpty() && lineY + noteHeight <= this.close.rect().y() + offset - 4) {
            graphics.fill(card.x() + 12, lineY - 2, card.right() - 12, lineY - 2 + noteHeight, CrystalTheme.fade(0x40221238, fade));
            graphics.fill(card.x() + 12, lineY - 2, card.x() + 14, lineY - 2 + noteHeight, CrystalTheme.fade(GrimoireDraw.ACCENT, fade));
            int noteY = lineY;
            for (FormattedCharSequence line : this.status) {
                HubDraw.line(graphics, font, line, card.x() + 19, noteY, CrystalTheme.fade(GrimoireDraw.VALUE, fade));
                noteY += GrimoireDraw.line(font);
            }
        }
        Rect dismissRect = this.dismiss.rect();
        Rect closeRect = this.close.rect();
        this.dismiss.setRect(new Rect(dismissRect.x(), dismissRect.y() + offset, dismissRect.width(), dismissRect.height()));
        this.close.setRect(new Rect(closeRect.x(), closeRect.y() + offset, closeRect.width(), closeRect.height()));
        this.dismiss.render(graphics, font, now, frameMillis, mouseX, mouseY, fade, this.leavingAt == Long.MIN_VALUE);
        this.close.render(graphics, font, now, frameMillis, mouseX, mouseY, fade, this.leavingAt == Long.MIN_VALUE);
        this.dismiss.setRect(dismissRect);
        this.close.setRect(closeRect);
        HubDraw.isolate(graphics);
    }
}
