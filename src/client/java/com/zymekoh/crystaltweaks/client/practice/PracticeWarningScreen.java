package com.zymekoh.crystaltweaks.client.practice;

import com.zymekoh.crystaltweaks.client.CrystalTheme;
import com.zymekoh.crystaltweaks.client.CrystalUi;
import com.zymekoh.crystaltweaks.client.PurpleCloseButton;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

/**
 * The warning before Crystal Practice: it is still being tested, so errors and frame drops are
 * possible. Red, loud and moving on purpose, so nobody walks into it by accident.
 *
 * <p>Loud without being harmful: nothing fills the screen with flashes. The red wash breathes about
 * once a second, the brightest pulses stay inside the panel and below three per second, and the
 * glitch on the title is a small sideways offset. The continue button waits three seconds, long
 * enough to read the first line.</p>
 */
public final class PracticeWarningScreen extends Screen {
    private static final long READ_NANOS = 3_000_000_000L;
    private static final long ENTER_NANOS = 450_000_000L;

    private final Screen parent;
    private final boolean spanish;
    private long openedAt;
    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;
    private int textTop;
    private List<FormattedCharSequence> body = List.of();
    private PurpleCloseButton continueButton;

    public PracticeWarningScreen(Screen parent) {
        super(Component.literal("Crystal Practice"));
        this.parent = parent;
        this.spanish = CrystalUi.spanish();
    }

    @Override
    protected void init() {
        if (this.openedAt == 0L) {
            this.openedAt = System.nanoTime();
        }
        int margin = Mth.clamp(this.width / 20, 6, 24);
        this.panelWidth = Math.max(170, Math.min(380, this.width - margin * 2));
        int textWidth = this.panelWidth - 30;
        this.body = new ArrayList<>();
        for (String paragraph : paragraphs()) {
            if (!this.body.isEmpty()) {
                this.body.add(FormattedCharSequence.EMPTY);
            }
            this.body.addAll(this.font.split(Component.literal(paragraph), textWidth));
        }
        int header = 92;
        int footer = 34;
        int bodyHeight = this.body.size() * (this.font.lineHeight + 1);
        this.panelHeight = Math.max(150, Math.min(this.height - margin * 2, header + bodyHeight + footer));
        this.panelX = (this.width - this.panelWidth) / 2;
        this.panelY = (this.height - this.panelHeight) / 2;
        this.textTop = this.panelY + header;

        int buttonWidth = Math.max(60, Math.min(160, (this.panelWidth - 30) / 2));
        int buttonY = this.panelY + this.panelHeight - 26;
        int buttonsX = this.panelX + (this.panelWidth - buttonWidth * 2 - 6) / 2;
        addRenderableWidget(new PurpleCloseButton(buttonsX, buttonY, buttonWidth, 18,
                Component.literal(this.spanish ? "Volver" : "Back"), ignored -> onClose()));
        this.continueButton = addRenderableWidget(new PurpleCloseButton(buttonsX + buttonWidth + 6, buttonY, buttonWidth, 18,
                continueLabel(), ignored -> proceed()).icon(PurpleCloseButton.Icon.WARNING)
                .accent(PurpleCloseButton.Accent.EXPERIMENTAL));
        this.continueButton.active = readLongEnough();
    }

    private List<String> paragraphs() {
        if (this.spanish) {
            return List.of(
                    "Crystal Practice está en fase de pruebas. Pueden aparecer errores o caídas de FPS, sobre todo con el bot.",
                    "Al entrar saldrás del mundo o servidor actual y se abrirá un mundo de práctica aparte, en un jugador. Tu mundo se guarda antes de salir.",
                    "El bot y el equipo solo existen en ese mundo: no hacen nada en ningún servidor.");
        }
        return List.of(
                "Crystal Practice is still being tested. Errors or FPS drops can happen, especially with the bot.",
                "Entering leaves the world or server you are in and opens a separate practice world in singleplayer. Your world is saved before you leave.",
                "The bot and the gear exist only in that world: they do nothing on any server.");
    }

    private boolean readLongEnough() {
        return System.nanoTime() - this.openedAt >= READ_NANOS;
    }

    private Component continueLabel() {
        long left = (READ_NANOS - (System.nanoTime() - this.openedAt) + 999_999_999L) / 1_000_000_000L;
        String label = this.spanish ? "Entiendo, continuar" : "I understand, continue";
        return Component.literal(left > 0 ? label + " (" + left + ")" : label);
    }

    private void proceed() {
        if (readLongEnough()) {
            this.minecraft.setScreen(new PracticeSetupScreen(this.parent));
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        long now = System.nanoTime();
        float breathe = 0.5F + 0.5F * (float) Math.sin(now / 1_000_000_000.0D * 2.0D * Math.PI * 0.9D);
        int top = CrystalTheme.withAlpha(0x3A0008, Math.round(150 + 50 * breathe));
        int bottom = CrystalTheme.withAlpha(0x120003, 215);
        graphics.fillGradient(0, 0, this.width, this.height, top, bottom);
        this.minecraft.gui.extractDeferredSubtitles();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        long now = System.nanoTime();
        double seconds = now / 1_000_000_000.0D;
        float enter = CrystalTheme.easeOutCubic(Mth.clamp((now - this.openedAt) / (float) ENTER_NANOS, 0.0F, 1.0F));
        sparks(graphics, seconds);

        // A slight shake on arrival, then still: the panel itself never moves under the buttons.
        int shake = enter < 1.0F ? Math.round((float) Math.sin(seconds * 70.0D) * 3.0F * (1.0F - enter)) : 0;
        int x = this.panelX + shake;
        int y = this.panelY;
        float alarm = 0.5F + 0.5F * (float) Math.sin(seconds * 2.0D * Math.PI * 1.6D);
        CrystalUi.panel(graphics, x, y, this.panelWidth, this.panelHeight,
                CrystalTheme.fade(0xF0240308, enter), CrystalTheme.fade(0xF0100104, enter));
        int border = CrystalTheme.fade(CrystalTheme.lerp(0xFFB0101E, CrystalTheme.DANGER_BRIGHT, alarm * 0.7F), enter);
        CrystalUi.roundedOutline(graphics, x, y, this.panelWidth, this.panelHeight, border);
        CrystalUi.roundedOutline(graphics, x - 1, y - 1, this.panelWidth + 2, this.panelHeight + 2,
                CrystalTheme.withAlpha(0xFF3B4E, Math.round(60 + 80 * alarm * enter)));
        tape(graphics, x + 4, y + 4, this.panelWidth - 8, now, enter);
        tape(graphics, x + 4, y + this.panelHeight - 9, this.panelWidth - 8, now + 400_000_000L, enter);
        if (enter > 0.98F) {
            CrystalUi.comets(graphics, x, y, this.panelWidth, this.panelHeight, seconds, 0xFFFF6A7A);
        }
        scanline(graphics, x, y, now);

        brackets(graphics, x, y, seconds, enter);
        int centerX = x + this.panelWidth / 2;
        triangle(graphics, centerX, y + 14, seconds, enter);
        glitchTitle(graphics, centerX, y + 58, seconds, enter);

        int lineY = this.textTop;
        int textColor = CrystalTheme.fade(0xFFFFE4E8, enter);
        if (((textColor >>> 24) & 255) >= 8) {
            for (FormattedCharSequence line : this.body) {
                graphics.text(this.font, line, x + 15, lineY, textColor, false);
                lineY += this.font.lineHeight + 1;
            }
        }
        boolean ready = readLongEnough();
        this.continueButton.active = ready;
        this.continueButton.setMessage(continueLabel());
        if (!ready) {
            // The wait, as a bar filling under the button.
            float waited = Mth.clamp((now - this.openedAt) / (float) READ_NANOS, 0.0F, 1.0F);
            int barX = this.continueButton.getX();
            int barY = this.continueButton.getY() + this.continueButton.getHeight() + 2;
            graphics.fill(barX, barY, barX + this.continueButton.getWidth(), barY + 2, 0x66400A12);
            graphics.fill(barX, barY, barX + Math.round(this.continueButton.getWidth() * waited), barY + 2, 0xFFFF3B4E);
        }
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    /** Red embers rising across the whole screen. */
    private void sparks(GuiGraphicsExtractor graphics, double seconds) {
        int count = Mth.clamp(this.width * this.height / 3_200, 40, 120);
        int span = Math.max(1, this.height + 20);
        for (int index = 0; index < count; index++) {
            double phase = index * 0.7548776662D;
            double speed = 14.0D + index % 9 * 4.0D;
            int x = Math.floorMod(index * 97 + 13, Math.max(1, this.width))
                    + (int) Math.round(Math.sin(seconds * 1.7D + phase * 6.0D) * (3 + index % 5));
            int y = this.height + 10 - (int) ((seconds * speed + phase * span) % span);
            int size = index % 7 == 0 ? 2 : 1;
            int alpha = 90 + index % 5 * 30;
            int color = index % 4 == 0 ? 0xFFD27A : 0xFF3B4E;
            graphics.fill(x - 1, y - 1, x + size + 1, y + size + 1, CrystalTheme.withAlpha(color, alpha / 4));
            graphics.fill(x, y, x + size, y + size, CrystalTheme.withAlpha(color, alpha));
        }
    }

    /** Hazard tape, red and black stripes sliding sideways. */
    private static void tape(GuiGraphicsExtractor graphics, int x, int y, int width, long now, float alpha) {
        graphics.fill(x, y, x + width, y + 5, CrystalTheme.fade(0xE0120104, alpha));
        int offset = (int) ((now / 30_000_000L) % 10L);
        for (int stripe = -10; stripe < width + 10; stripe += 10) {
            for (int row = 0; row < 5; row++) {
                int left = Math.max(x, x + stripe + offset + row);
                int right = Math.min(x + width, x + stripe + offset + row + 5);
                if (left < right) {
                    graphics.fill(left, y + row, right, y + row + 1, CrystalTheme.fade(0xF0FF2A40, alpha));
                }
            }
        }
    }

    /** A bright band sweeping down the panel, like an alarm light. */
    private void scanline(GuiGraphicsExtractor graphics, int x, int y, long now) {
        int travel = Math.max(1, this.panelHeight - 20);
        int lineY = y + 10 + (int) ((now / 12_000_000L) % travel);
        graphics.fillGradient(x + 2, Math.max(y + 2, lineY - 10), x + this.panelWidth - 2, lineY, 0x00FF3B4E, 0x30FF3B4E);
        graphics.fill(x + 2, lineY, x + this.panelWidth - 2, lineY + 1, 0x70FF7A88);
    }

    /**
     * The warning sign: a large triangle breathing inside rings of red light that spread out from
     * it, with its mark flickering like a failing lamp every couple of seconds.
     */
    private static void triangle(GuiGraphicsExtractor graphics, int centerX, int top, double seconds, float alpha) {
        float pulse = 0.5F + 0.5F * (float) Math.sin(seconds * 2.0D * Math.PI * 1.2D);
        int size = 30;
        int middle = top + size / 2 + 2;
        for (int ring = 0; ring < 3; ring++) {
            double phase = (seconds * 0.8D + ring / 3.0D) % 1.0D;
            int radius = 14 + (int) Math.round(phase * 22.0D);
            int ringAlpha = Math.round((float) (1.0D - phase) * 120.0F * alpha);
            CrystalUi.ellipseRim(graphics, centerX, middle, radius + 8, Math.max(4, radius / 2 + 4),
                    CrystalTheme.withAlpha(0xFF3B4E, ringAlpha));
        }
        int glow = CrystalTheme.withAlpha(0xFF3B4E, Math.round((40 + 70 * pulse) * alpha));
        for (int row = -3; row <= size + 3; row++) {
            int span = Math.max(0, (row + 3) * 17 / 30);
            graphics.fill(centerX - span - 3, top + row, centerX + span + 4, top + row + 1, glow);
        }
        int fill = CrystalTheme.fade(CrystalTheme.lerp(0xFFE0182E, 0xFFFFB0B8, pulse * 0.55F), alpha);
        int edge = CrystalTheme.fade(0xFFFFE4E8, alpha);
        for (int row = 0; row <= size; row++) {
            int span = row * 17 / 30;
            graphics.fill(centerX - span, top + row, centerX + span + 1, top + row + 1, fill);
            graphics.fill(centerX - span, top + row, centerX - span + 1, top + row + 1, edge);
            graphics.fill(centerX + span, top + row, centerX + span + 1, top + row + 1, edge);
        }
        graphics.fill(centerX - 17, top + size, centerX + 18, top + size + 1, edge);
        boolean flicker = (seconds % 2.3D) < 0.12D && ((int) (seconds * 30.0D) & 1) == 0;
        int mark = CrystalTheme.fade(flicker ? 0xFF6A0A14 : 0xFF1A0206, alpha);
        graphics.fill(centerX - 1, top + 9, centerX + 2, top + 21, mark);
        graphics.fill(centerX - 1, top + 23, centerX + 2, top + 26, mark);
    }

    /** Corner brackets that close in on the panel and open out again, like a target lock. */
    private void brackets(GuiGraphicsExtractor graphics, int x, int y, double seconds, float alpha) {
        int inset = 2 + (int) Math.round((0.5D + 0.5D * Math.sin(seconds * 2.0D * Math.PI * 0.8D)) * 5.0D);
        int length = 14;
        int color = CrystalTheme.fade(0xFFFF6A7A, alpha);
        int left = x - 7 + inset;
        int top = y - 7 + inset;
        int right = x + this.panelWidth + 6 - inset;
        int bottom = y + this.panelHeight + 6 - inset;
        graphics.fill(left, top, left + length, top + 2, color);
        graphics.fill(left, top, left + 2, top + length, color);
        graphics.fill(right - length + 1, top, right + 1, top + 2, color);
        graphics.fill(right - 1, top, right + 1, top + length, color);
        graphics.fill(left, bottom - 1, left + length, bottom + 1, color);
        graphics.fill(left, bottom - length + 1, left + 2, bottom + 1, color);
        graphics.fill(right - length + 1, bottom - 1, right + 1, bottom + 1, color);
        graphics.fill(right - 1, bottom - length + 1, right + 1, bottom + 1, color);
    }

    /** The title twice as large, with a red and a cyan ghost that jump apart now and then. */
    private void glitchTitle(GuiGraphicsExtractor graphics, int centerX, int y, double seconds, float alpha) {
        String title = this.spanish ? "⚠ FASE DE PRUEBAS ⚠" : "⚠ EXPERIMENTAL ⚠";
        // Twice the size where it fits; a narrow window shrinks it rather than cutting it off.
        float scale = Math.max(1.0F, Math.min(2.0F, (this.panelWidth - 24) / (float) this.font.width(title)));
        int width = Math.round(this.font.width(title) * scale);
        boolean glitch = (seconds % 1.7D) < 0.18D;
        int jitter = glitch ? (int) Math.round(Math.sin(seconds * 97.0D) * 3.0D) : 0;
        int left = centerX - width / 2;
        graphics.pose().pushMatrix();
        graphics.pose().translate(left, y);
        graphics.pose().scale(scale, scale);
        graphics.text(this.font, title, -1 + jitter, 0, CrystalTheme.fade(0xB0FF2A40, alpha), false);
        graphics.text(this.font, title, 1 - jitter, 0, CrystalTheme.fade(0x9060F0FF, alpha), false);
        graphics.text(this.font, title, 0, 0, CrystalTheme.fade(0xFFFFF1F3, alpha), false);
        graphics.pose().popMatrix();
        String subtitle = this.spanish ? "Crystal Practice · puede fallar" : "Crystal Practice · may break";
        CrystalUi.centered(graphics, this.font, subtitle, centerX, y + 20, CrystalTheme.fade(0xFFFF8A96, alpha));
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }
}
