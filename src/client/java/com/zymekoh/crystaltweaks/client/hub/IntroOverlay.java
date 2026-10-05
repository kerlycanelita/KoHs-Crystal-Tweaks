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
 * What the hub says first, when no crystal optimizer is installed: what Crystal Tweaks' local
 * optimization does, and that it is client-side and legitimate. The player's own crystal turns in
 * the middle of a ring of light beside it, in their colours and glow; "Continue" sends it to the
 * stage, and "Don't show again" does the same for good.
 *
 * <p>The wording only claims what the code does: the break prediction and the ghost crystals are
 * drawing (the entity stays in the world, every packet is Vanilla's), and nothing about them makes
 * the server act sooner. The interaction helpers that do filter a click are not part of it; their
 * own switches say so.</p>
 */
final class IntroOverlay {
    enum Action { NONE, CONTINUE, DISMISS }

    private static final long LEAVE_NANOS = 260_000_000L;

    private final boolean spanish;
    private final OverlayButton dismiss;
    private final OverlayButton proceed;
    private final long shownAt = System.nanoTime();
    private long leavingAt = Long.MIN_VALUE;
    private Rect crystal = Rect.EMPTY;
    private Rect card = Rect.EMPTY;
    private Rect body = Rect.EMPTY;
    private final List<FormattedCharSequence> lines = new ArrayList<>();
    private final List<Integer> paragraphStarts = new ArrayList<>();
    private int bodyScroll;
    private int bodyContentHeight;
    private String[] badges = new String[0];

    IntroOverlay(boolean spanish) {
        this.spanish = spanish;
        this.dismiss = new OverlayButton(spanish ? "No volver a mostrar" : "Don't show again", false);
        this.proceed = new OverlayButton(spanish ? "Continuar" : "Continue", true);
    }

    private String title() {
        return this.spanish ? "Optimización local" : "Local optimization";
    }

    private String[] paragraphs() {
        if (this.spanish) {
            return new String[] {
                    "Crystal Tweaks optimiza lo que ves y oyes. El cristal que golpeas desaparece y explota, con su sonido, en el momento del golpe; el que el servidor quita deja de dibujarse en cuanto llega su aviso; y, con los cristales fantasma activados, el que colocas aparece mientras la copia del servidor sigue en camino.",
                    "Es solo del cliente y legítima: solo cambia lo que se dibuja y se oye. No añade, cambia ni retrasa ningún paquete, el servidor sigue decidiendo cada golpe y cada colocación, y nadie más ve nada distinto.",
                    "No hace que nada llegue antes al servidor. Si instalas un optimizador de cristales como el de Marlow, Crystal Tweaks apaga su propia optimización solo, para que nunca se peleen."};
        }
        return new String[] {
                "Crystal Tweaks optimizes what you see and hear. A crystal you hit disappears and explodes, sound and all, the moment you hit it; one the server removes stops being drawn as soon as its word arrives; and, with Ghost crystals on, one you place appears while the server's copy is still on its way.",
                "It is client-side and legitimate: it only changes what is drawn and heard. No packet is added, changed or delayed, the server still decides every hit and placement, and nobody else sees anything different.",
                "It does not make anything reach the server sooner. With a crystal optimizer such as Marlow's installed, Crystal Tweaks turns its own optimization off by itself, so the two never fight."};
    }

    void layout(Font font, int width, int height) {
        this.badges = this.spanish
                ? new String[] {"Solo cliente", "Sin paquetes cambiados", "Decide el servidor"}
                : new String[] {"Client only", "No packets changed", "Server decides"};
        int margin = Math.max(6, Math.min(20, width / 30));
        boolean side = width >= 500 && height >= 220;
        int cardWidth = side ? Math.min(330, Math.round(width * 0.5F)) : Math.min(340, width - margin * 2);
        int textWidth = cardWidth - 24;
        this.lines.clear();
        this.paragraphStarts.clear();
        for (String paragraph : paragraphs()) {
            if (!this.lines.isEmpty()) {
                this.lines.add(FormattedCharSequence.EMPTY);
            }
            this.paragraphStarts.add(this.lines.size());
            this.lines.addAll(CrystalUi.wrap(font, paragraph, textWidth));
        }
        this.bodyContentHeight = this.lines.size() * (font.lineHeight + 1);
        int header = 34;
        // The buttons and, above them, the row of badges.
        int footer = 48;
        int chrome = header + 6 + footer;
        int cardHeight;
        if (side) {
            cardHeight = Math.min(height - margin * 2, chrome + this.bodyContentHeight);
            int crystalSize = Math.max(48, Math.min(Math.round(height * 0.6F), Math.min(Math.round(width * 0.3F), 230)));
            int gap = 22;
            int group = crystalSize + gap + cardWidth;
            int left = (width - group) / 2;
            this.crystal = new Rect(left, (height - crystalSize) / 2, crystalSize, crystalSize);
            this.card = new Rect(left + crystalSize + gap, (height - cardHeight) / 2, cardWidth, cardHeight);
        } else {
            int crystalSize = Math.max(0, Math.min(120, Math.min(width / 2, height - margin * 2 - chrome - 40 - 8)));
            cardHeight = Math.min(height - margin * 2 - crystalSize - (crystalSize > 0 ? 6 : 0), chrome + this.bodyContentHeight);
            int top = (height - crystalSize - (crystalSize > 0 ? 6 : 0) - cardHeight) / 2;
            this.crystal = crystalSize > 0 ? new Rect(width / 2 - crystalSize / 2, top, crystalSize, crystalSize) : Rect.EMPTY;
            this.card = new Rect((width - cardWidth) / 2, top + crystalSize + (crystalSize > 0 ? 6 : 0), cardWidth, cardHeight);
        }
        this.body = new Rect(this.card.x() + 12, this.card.y() + header, this.card.width() - 24,
                Math.max(10, this.card.height() - header - footer - 2));
        this.bodyScroll = Math.max(0, Math.min(this.bodyScroll, Math.max(0, this.bodyContentHeight - this.body.height())));
        int buttonHeight = 18;
        int gap = 6;
        int buttonWidth = Math.max(60, Math.min(150, (this.card.width() - 24 - gap) / 2));
        int buttonsX = this.card.x() + (this.card.width() - buttonWidth * 2 - gap) / 2;
        int buttonsY = this.card.bottom() - buttonHeight - 8;
        this.dismiss.setRect(new Rect(buttonsX, buttonsY, buttonWidth, buttonHeight));
        this.proceed.setRect(new Rect(buttonsX + buttonWidth + gap, buttonsY, buttonWidth, buttonHeight));
    }

    /** Where the crystal stands while the intro is up. */
    Rect crystalRect() {
        return this.crystal;
    }

    void leave(long now) {
        if (this.leavingAt == Long.MIN_VALUE) {
            this.leavingAt = now;
        }
    }

    boolean leaving() {
        return this.leavingAt != Long.MIN_VALUE;
    }

    boolean gone(long now) {
        return leaving() && now - this.leavingAt >= LEAVE_NANOS;
    }

    private float presence(long now) {
        float in = HubMotion.easeOutCubic(HubMotion.progress(now - this.shownAt, 420_000_000L));
        if (!leaving()) {
            return in;
        }
        return in * (1.0F - HubMotion.easeInCubic(HubMotion.progress(now - this.leavingAt, LEAVE_NANOS)));
    }

    Action mouseClicked(double mouseX, double mouseY) {
        if (leaving() || System.nanoTime() - this.shownAt < 300_000_000L) {
            return Action.NONE;
        }
        if (this.proceed.contains(mouseX, mouseY)) {
            this.proceed.press();
            return Action.CONTINUE;
        }
        if (this.dismiss.contains(mouseX, mouseY)) {
            this.dismiss.press();
            return Action.DISMISS;
        }
        return Action.NONE;
    }

    boolean mouseScrolled(double amount, int lineHeight) {
        int max = Math.max(0, this.bodyContentHeight - this.body.height());
        if (max <= 0) {
            return false;
        }
        this.bodyScroll = Math.max(0, Math.min(max, this.bodyScroll - (int) Math.signum(amount) * lineHeight * 2));
        return true;
    }

    /** The light behind the crystal: two rings turning opposite ways, rays and a slow pulse. */
    void renderBehind(GuiGraphicsExtractor graphics, long now, int halo, int crystalX, int crystalY, float crystalSize) {
        float alpha = presence(now);
        if (alpha <= 0.02F || crystalSize < 8.0F) {
            return;
        }
        double seconds = now / 1_000_000_000.0D;
        float radius = crystalSize * 0.48F;
        HubDraw.isolate(graphics);
        HubDraw.glowDisc(graphics, crystalX, crystalY, Math.round(radius * 1.05F), halo, 0.55F * alpha);
        float pulse = HubMotion.breathe(seconds, 2.6D);
        HubDraw.ring(graphics, crystalX, crystalY, radius * (0.98F + 0.04F * pulse), 1, CrystalTheme.withAlpha(halo, Math.round(150 * alpha)));
        HubDraw.ring(graphics, crystalX, crystalY, radius * 0.8F, 1, CrystalTheme.withAlpha(0xFFFFFF, Math.round(60 * alpha)));
        for (int ray = 0; ray < 12; ray++) {
            double angle = seconds * 0.25D + ray * Math.PI * 2.0D / 12.0D;
            float inner = radius * 0.86F;
            float outer = radius * (1.08F + (ray % 3) * 0.07F + 0.05F * pulse);
            HubDraw.line(graphics, crystalX + (float) Math.cos(angle) * inner, crystalY + (float) Math.sin(angle) * inner,
                    crystalX + (float) Math.cos(angle) * outer, crystalY + (float) Math.sin(angle) * outer, 1.0F,
                    CrystalTheme.withAlpha(halo, Math.round((ray % 2 == 0 ? 120 : 70) * alpha)));
        }
        HubDraw.isolate(graphics);
    }

    void renderFront(GuiGraphicsExtractor graphics, Font font, long now, float frameMillis, int mouseX, int mouseY) {
        float alpha = presence(now);
        if (alpha <= 0.02F) {
            return;
        }
        HubSkin skin = HubSkin.current();
        double seconds = now / 1_000_000_000.0D;
        int slide = Math.round((1.0F - alpha) * 14.0F);
        Rect card = new Rect(this.card.x() + slide, this.card.y(), this.card.width(), this.card.height());
        HubDraw.isolate(graphics);
        HubDraw.halo(graphics, card.x(), card.y(), card.width(), card.height(), skin.accent, 4, 0.3F * alpha);
        HubDraw.glass(graphics, card.x(), card.y(), card.width(), card.height(), 0xEE1B0928, 0xEE0A0310, skin.border, alpha);
        if (alpha > 0.98F) {
            CrystalUi.comets(graphics, card.x(), card.y(), card.width(), card.height(), seconds, skin.accentBright);
        }
        // Title, with the kicker above it.
        String kicker = this.spanish ? "◆ SOLO CLIENTE · LEGÍTIMA" : "◆ CLIENT-SIDE · LEGITIMATE";
        CrystalUi.label(graphics, font, kicker, card.x() + 12, card.y() + 7, CrystalTheme.fade(skin.hot, alpha));
        String title = title();
        CrystalUi.label(graphics, font, title, card.x() + 12, card.y() + 19, CrystalTheme.fade(skin.title, alpha), true);
        CrystalUi.glint(graphics, font, title, card.x() + 12, card.y() + 19, seconds + 0.7D, alpha);
        graphics.fill(card.x() + 10, card.y() + 30, card.right() - 10, card.y() + 31, CrystalTheme.fade(CrystalTheme.withAlpha(skin.border, 120), alpha));
        // The text, paragraph by paragraph.
        graphics.enableScissor(this.body.x() + slide - 2, this.body.y(), this.body.right() + slide + 2, this.body.bottom());
        int lineY = this.body.y() - this.bodyScroll;
        for (int index = 0; index < this.lines.size(); index++) {
            int paragraph = 0;
            for (int start = 0; start < this.paragraphStarts.size(); start++) {
                if (index >= this.paragraphStarts.get(start)) {
                    paragraph = start;
                }
            }
            float in = HubMotion.easeOutCubic(HubMotion.progress(now - this.shownAt - 150_000_000L - paragraph * 140_000_000L, 320_000_000L));
            HubDraw.line(graphics, font, this.lines.get(index), this.body.x() + slide + Math.round((1.0F - in) * 6.0F), lineY,
                    CrystalTheme.fade(paragraph == 1 ? skin.text : CrystalTheme.lerp(skin.text, skin.muted, 0.25F), alpha * in));
            lineY += font.lineHeight + 1;
        }
        graphics.disableScissor();
        // Three badges along the bottom of the text: what the claim rests on.
        int badgeY = this.dismiss.rect().y() - 16;
        int badgeX = card.x() + 12;
        for (int index = 0; index < this.badges.length; index++) {
            String badge = this.badges[index];
            int badgeWidth = font.width(badge) + 10;
            if (badgeX + badgeWidth > card.right() - 10) {
                break;
            }
            float in = HubMotion.easeOutCubic(HubMotion.progress(now - this.shownAt - 500_000_000L - index * 90_000_000L, 260_000_000L));
            HubDraw.glass(graphics, badgeX, badgeY, badgeWidth, 11, 0xC0261238, 0xC014081E,
                    CrystalTheme.withAlpha(index == 2 ? skin.hot : skin.accent, 170), alpha * in);
            CrystalUi.label(graphics, font, badge, badgeX + 5, badgeY + 2, CrystalTheme.fade(skin.text, alpha * in));
            badgeX += badgeWidth + 4;
        }
        float buttonsIn = HubMotion.progress(now - this.shownAt - 300_000_000L, 250_000_000L);
        this.dismiss.render(graphics, font, now, frameMillis, mouseX, mouseY, alpha * buttonsIn, !leaving());
        this.proceed.render(graphics, font, now, frameMillis, mouseX, mouseY, alpha * buttonsIn, !leaving());
        HubDraw.isolate(graphics);
    }
}
