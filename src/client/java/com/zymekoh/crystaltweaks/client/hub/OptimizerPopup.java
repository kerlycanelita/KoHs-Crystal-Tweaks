package com.zymekoh.crystaltweaks.client.hub;

import com.zymekoh.crystaltweaks.client.CrystalTheme;
import com.zymekoh.crystaltweaks.client.CrystalUi;
import com.zymekoh.crystaltweaks.client.hub.HubLayout.Rect;
import com.zymekoh.crystaltweaks.core.CrystalOptimizerGuard;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.FormattedCharSequence;

/**
 * The window that drops in the first time the Crystal Tweaks tab is opened in a session: any
 * crystal optimizer can be used alongside, and Crystal Tweaks steps aside for it on its own. Above
 * the words, two crystals stand for the two mods, with light running between them toward whichever
 * one is driving crystals right now; under them, what the compatibility check actually found.
 */
final class OptimizerPopup {
    enum Action { NONE, CLOSE, DISMISS }

    private static final long DROP_NANOS = 420_000_000L;
    private static final long LEAVE_NANOS = 220_000_000L;

    private final boolean spanish;
    private final OverlayButton dismiss;
    private final OverlayButton close;
    private final long shownAt = System.nanoTime();
    private long leavingAt = Long.MIN_VALUE;
    private Rect card = Rect.EMPTY;
    private final List<FormattedCharSequence> lines = new ArrayList<>();
    private List<FormattedCharSequence> status = List.of();
    private int textTop;

    OptimizerPopup(boolean spanish) {
        this.spanish = spanish;
        this.dismiss = new OverlayButton(spanish ? "No volver a mostrar" : "Don't show again", false);
        this.close = new OverlayButton(spanish ? "Entendido" : "Got it", true);
    }

    private String title() {
        return this.spanish ? "Funciona con cualquier optimizador" : "Works with any optimizer";
    }

    private String text() {
        return this.spanish
                ? "Puedes usar Crystal Tweaks junto con cualquier optimizador de cristales: el de Marlow, G1ax, Kind's y otros. Si hay uno instalado, Crystal Tweaks apaga sus propias optimizaciones solo, para que únicamente uno maneje tus cristales. Los colores, el brillo, los destellos y los sonidos siguen funcionando."
                : "You can use Crystal Tweaks together with any crystal optimizer: Marlow's, G1ax, Kind's and others. When one is installed, Crystal Tweaks turns its own optimizations off by itself, so only one of them drives your crystals. Colours, glow, flashes and sounds keep working.";
    }

    private String statusText() {
        return switch (CrystalOptimizerGuard.pauseReason()) {
            case CONFLICT -> this.spanish
                    ? "Ahora mismo: " + CrystalOptimizerGuard.conflictingModName() + " detectado. Las optimizaciones de Crystal Tweaks están apagadas."
                    : "Right now: " + CrystalOptimizerGuard.conflictingModName() + " detected. Crystal Tweaks' optimizations are off.";
            case FORCED_OFF -> this.spanish
                    ? "Ahora mismo: las apagaste tú con «Forzar apagado de optimizaciones»."
                    : "Right now: you turned them off with \"Force off optimizations\".";
            case CHECKING -> this.spanish ? "Ahora mismo: comprobando tus mods…" : "Right now: checking your mods…";
            case NONE -> this.spanish
                    ? "Ahora mismo: no hay otro optimizador. La optimización de Crystal Tweaks está activa."
                    : "Right now: no other optimizer found. Crystal Tweaks' optimization is on.";
        };
    }

    void layout(Font font, int width, int height) {
        int cardWidth = Math.min(320, width - 16);
        int textWidth = cardWidth - 24;
        this.lines.clear();
        this.lines.addAll(CrystalUi.wrap(font, text(), textWidth));
        this.status = CrystalUi.wrap(font, statusText(), textWidth - 6);
        int art = height >= 230 ? 38 : 0;
        int cardHeight = 26 + art + this.lines.size() * (font.lineHeight + 1) + 8 + this.status.size() * (font.lineHeight + 1) + 10 + 28;
        cardHeight = Math.min(height - 12, cardHeight);
        this.card = new Rect((width - cardWidth) / 2, (height - cardHeight) / 2, cardWidth, cardHeight);
        this.textTop = this.card.y() + 26 + art;
        int buttonHeight = 18;
        int gap = 6;
        int buttonWidth = Math.max(60, Math.min(140, (cardWidth - 24 - gap) / 2));
        int buttonsX = this.card.x() + (cardWidth - buttonWidth * 2 - gap) / 2;
        int buttonsY = this.card.bottom() - buttonHeight - 7;
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
        HubSkin skin = HubSkin.current();
        float drop = HubMotion.easeOutBack(HubMotion.progress(now - this.shownAt, DROP_NANOS));
        float fade = HubMotion.clamp01(HubMotion.progress(now - this.shownAt, DROP_NANOS / 2));
        if (this.leavingAt != Long.MIN_VALUE) {
            fade *= 1.0F - HubMotion.progress(now - this.leavingAt, LEAVE_NANOS);
        }
        if (fade <= 0.02F) {
            return;
        }
        double seconds = now / 1_000_000_000.0D;
        HubDraw.isolate(graphics);
        graphics.fill(0, 0, screenWidth, screenHeight, CrystalTheme.withAlpha(0x05020A, Math.round(150 * fade)));
        int offset = Math.round((1.0F - drop) * -40.0F);
        Rect card = new Rect(this.card.x(), this.card.y() + offset, this.card.width(), this.card.height());
        HubDraw.halo(graphics, card.x(), card.y(), card.width(), card.height(), skin.accent, 4, 0.35F * fade);
        HubDraw.glass(graphics, card.x(), card.y(), card.width(), card.height(), 0xF01B0928, 0xF00A0310, skin.border, fade);
        CrystalUi.comets(graphics, card.x(), card.y(), card.width(), card.height(), seconds, skin.accentBright);
        CrystalUi.crystalIcon(graphics, card.x() + 14, card.y() + 12, 9, seconds, fade);
        CrystalUi.label(graphics, font, HubDraw.fit(font, title(), card.width() - 34), card.x() + 24, card.y() + 8,
                CrystalTheme.fade(skin.title, fade), true);
        graphics.fill(card.x() + 10, card.y() + 21, card.right() - 10, card.y() + 22, CrystalTheme.fade(CrystalTheme.withAlpha(skin.border, 120), fade));
        int textTop = this.textTop + offset;
        if (textTop - card.y() > 30) {
            drawHandOver(graphics, font, card, card.y() + 24, seconds, fade, skin);
        }
        int lineY = textTop;
        for (FormattedCharSequence line : this.lines) {
            if (lineY + font.lineHeight > this.close.rect().y() + offset - 4) {
                break;
            }
            HubDraw.line(graphics, font, line, card.x() + 12, lineY, CrystalTheme.fade(skin.text, fade));
            lineY += font.lineHeight + 1;
        }
        lineY += 6;
        // What the check found, on a strip with a scan of light running along it.
        int stripHeight = this.status.size() * (font.lineHeight + 1) + 4;
        if (lineY + stripHeight <= this.close.rect().y() + offset - 2) {
            boolean paused = CrystalOptimizerGuard.pauseReason() != CrystalOptimizerGuard.PauseReason.NONE;
            int tone = paused ? 0xFFFFC48A : 0xFF6BE39A;
            graphics.fill(card.x() + 10, lineY - 2, card.right() - 10, lineY - 2 + stripHeight, CrystalTheme.fade(0x60140820, fade));
            graphics.fill(card.x() + 10, lineY - 2, card.x() + 12, lineY - 2 + stripHeight, CrystalTheme.fade(tone, fade));
            int scanX = card.x() + 12 + (int) Math.round(((seconds * 0.5D) % 1.0D) * (card.width() - 24));
            graphics.fill(scanX, lineY - 2, scanX + 2, lineY - 2 + stripHeight, CrystalTheme.withAlpha(tone, Math.round(50 * fade)));
            int statusY = lineY;
            for (FormattedCharSequence line : this.status) {
                HubDraw.line(graphics, font, line, card.x() + 16, statusY, CrystalTheme.fade(CrystalTheme.lerp(tone, 0xFFFFFFFF, 0.4F), fade));
                statusY += font.lineHeight + 1;
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

    /** Two crystals, Crystal Tweaks and "an optimizer", with light running to whichever drives. */
    private void drawHandOver(GuiGraphicsExtractor graphics, Font font, Rect card, int top, double seconds, float fade, HubSkin skin) {
        boolean other = CrystalOptimizerGuard.conflictDetected();
        int leftX = card.x() + card.width() / 2 - 60;
        int rightX = card.x() + card.width() / 2 + 60;
        int y = top + 12;
        CrystalUi.crystalIcon(graphics, leftX, y, 13, seconds, fade * (other ? 0.45F : 1.0F));
        CrystalUi.crystalIcon(graphics, rightX, y, 13, seconds + 1.1D, fade * (other ? 1.0F : 0.45F));
        for (int dot = 0; dot < 6; dot++) {
            double travel = (seconds * 0.8D + dot / 6.0D) % 1.0D;
            if (!other) {
                travel = 1.0D - travel;
            }
            int x = leftX + 9 + (int) Math.round(travel * (rightX - leftX - 18));
            int alpha = Math.round(220 * fade * (float) Math.sin(travel * Math.PI));
            graphics.fill(x, y, x + 2, y + 2, CrystalTheme.withAlpha(skin.accentBright, alpha));
        }
        String ours = "Crystal Tweaks";
        String theirs = other ? CrystalOptimizerGuard.conflictingModName() : (this.spanish ? "Optimizador" : "Optimizer");
        theirs = HubDraw.fit(font, theirs, 110);
        CrystalUi.centered(graphics, font, ours, leftX, y + 14, CrystalTheme.fade(other ? skin.muted : skin.title, fade));
        CrystalUi.centered(graphics, font, theirs, rightX, y + 14, CrystalTheme.fade(other ? skin.title : skin.muted, fade));
    }
}
