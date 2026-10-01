package com.zymekoh.crystaltweaks.client.practice;

import com.zymekoh.crystaltweaks.client.CrystalAppearance;
import com.zymekoh.crystaltweaks.client.CrystalTheme;
import com.zymekoh.crystaltweaks.client.CrystalUi;
import com.zymekoh.crystaltweaks.client.CrystalVisualConfig;
import com.zymekoh.crystaltweaks.client.PurpleCloseButton;
import com.zymekoh.crystaltweaks.client.hub.HubDraw;
import com.zymekoh.crystaltweaks.client.hub.HubMotion;
import com.zymekoh.crystaltweaks.client.hub.MiniCrystal;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

/**
 * The gate to Crystal Practice. Obsidian rises out of the ground on both sides, your own crystals
 * drop onto it in your colours and glow, and the warning comes up between them: practice is still
 * being tested, so errors and frame drops are possible.
 *
 * <p>Continuing is the show: the crystals charge and send their light into the warning, blow up one
 * after another, the obsidian and the panel burst apart, and a rift of light opens in the middle and
 * swallows the screen as the practice setup takes its place.</p>
 *
 * <p>Loud without being harmful: no strobing. The red wash breathes about once a second, every flash
 * is local and fades in a fraction of a second, and the light that fills the screen at the end rises
 * and stays rather than blinking. The continue button waits three seconds, enough to read the first
 * line. Nothing moves under a button while it can be clicked.</p>
 */
public final class PracticeWarningScreen extends Screen {
    private static final long READ_NANOS = 3_000_000_000L;
    private static final long ENTER_NANOS = 450_000_000L;
    private static final long CHARGE_NANOS = 420_000_000L;
    private static final long BURST_NANOS = 480_000_000L;
    private static final long RIFT_NANOS = 640_000_000L;
    private static final Identifier OBSIDIAN = HubDraw.vanilla("textures/block/obsidian.png");

    /**
     * One obsidian pillar and the crystal on it. {@code block} is a cube's vertical edge on screen:
     * stacked cubes sit one edge apart, so each covers the top face of the one under it.
     */
    private record Pillar(float x, float baseY, int blocks, float block, float delay, MiniCrystal crystal) {
        /** The middle of the top cube's upper face, where the crystal stands. */
        float topY() {
            return this.baseY - this.blocks * this.block - this.block * 0.5F;
        }

        float centerOf(int level) {
            return this.baseY - this.block - level * this.block;
        }
    }

    private final Screen parent;
    private final boolean spanish;
    private final List<Pillar> pillars = new ArrayList<>();
    private long openedAt;
    private long ascendAt = Long.MIN_VALUE;
    private boolean opened;
    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;
    private int textTop;
    private List<FormattedCharSequence> body = List.of();
    private PurpleCloseButton backButton;
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
        layoutPillars();

        int buttonWidth = Math.max(60, Math.min(160, (this.panelWidth - 30) / 2));
        int buttonY = this.panelY + this.panelHeight - 26;
        int buttonsX = this.panelX + (this.panelWidth - buttonWidth * 2 - 6) / 2;
        this.backButton = addRenderableWidget(new PurpleCloseButton(buttonsX, buttonY, buttonWidth, 18,
                Component.literal(this.spanish ? "Volver" : "Back"), ignored -> onClose()));
        this.continueButton = addRenderableWidget(new PurpleCloseButton(buttonsX + buttonWidth + 6, buttonY, buttonWidth, 18,
                continueLabel(), ignored -> ascend()).icon(PurpleCloseButton.Icon.WARNING)
                .accent(PurpleCloseButton.Accent.EXPERIMENTAL));
        this.continueButton.active = readLongEnough();
        if (ascending()) {
            this.backButton.visible = false;
            this.continueButton.visible = false;
        }
    }

    /** Two pillars on each side of the warning when there is room for them, none when there is not. */
    private void layoutPillars() {
        this.pillars.clear();
        int side = this.panelX - 8;
        if (side < 44 || this.panelHeight < 120) {
            return;
        }
        float block = Mth.clamp(side * 0.14F, 8.0F, 18.0F);
        float baseY = this.panelY + this.panelHeight - block * 0.4F;
        int[] heights = {3, 2};
        for (int index = 0; index < 2; index++) {
            float inner = this.panelX - block * (2.6F + index * 4.2F);
            float outer = this.panelX + this.panelWidth + block * (2.6F + index * 4.2F);
            this.pillars.add(new Pillar(inner, baseY - index * block * 0.8F, heights[index], block, index * 0.12F,
                    new MiniCrystal(index * 0.37F)));
            this.pillars.add(new Pillar(outer, baseY - index * block * 0.8F, heights[index], block, index * 0.12F + 0.06F,
                    new MiniCrystal(index * 0.37F + 0.2F)));
        }
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

    private boolean ascending() {
        return this.ascendAt != Long.MIN_VALUE;
    }

    private Component continueLabel() {
        long left = (READ_NANOS - (System.nanoTime() - this.openedAt) + 999_999_999L) / 1_000_000_000L;
        String label = this.spanish ? "Entiendo, continuar" : "I understand, continue";
        return Component.literal(left > 0 ? label + " (" + left + ")" : label);
    }

    /** The continue: the show plays, and the practice setup opens at its end. */
    private void ascend() {
        if (!readLongEnough() || ascending()) {
            return;
        }
        this.ascendAt = System.nanoTime();
        this.backButton.visible = false;
        this.continueButton.visible = false;
        this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BEACON_ACTIVATE, 1.4F, 0.6F));
    }

    @Override
    public void tick() {
        if (ascending() && !this.opened && System.nanoTime() - this.ascendAt >= CHARGE_NANOS + BURST_NANOS + RIFT_NANOS) {
            this.opened = true;
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
        long ascended = ascending() ? now - this.ascendAt : -1L;
        CrystalAppearance look = CrystalVisualConfig.visuals(false).copy();
        HubDraw.isolate(graphics);
        sparks(graphics, seconds);
        HubDraw.isolate(graphics);

        float panelPresence = enter;
        if (ascended >= CHARGE_NANOS) {
            panelPresence = 0.0F;
        }
        if (panelPresence > 0.0F) {
            drawPanel(graphics, now, seconds, enter, ascended);
        } else if (ascended >= CHARGE_NANOS && ascended < CHARGE_NANOS + BURST_NANOS) {
            drawPanelShards(graphics, (ascended - CHARGE_NANOS) / 1_000_000_000.0F);
        }
        drawPillars(graphics, now, look, ascended);
        if (!ascending()) {
            boolean ready = readLongEnough();
            this.continueButton.active = ready;
            this.continueButton.setMessage(continueLabel());
            if (!ready) {
                float waited = Mth.clamp((now - this.openedAt) / (float) READ_NANOS, 0.0F, 1.0F);
                int barX = this.continueButton.getX();
                int barY = this.continueButton.getY() + this.continueButton.getHeight() + 2;
                graphics.fill(barX, barY, barX + this.continueButton.getWidth(), barY + 2, 0x66400A12);
                graphics.fill(barX, barY, barX + Math.round(this.continueButton.getWidth() * waited), barY + 2, 0xFFFF3B4E);
            }
        }
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        if (ascending()) {
            HubDraw.isolate(graphics);
            drawAscension(graphics, ascended, look);
            HubDraw.isolate(graphics);
        }
    }

    private void drawPanel(GuiGraphicsExtractor graphics, long now, double seconds, float enter, long ascended) {
        int shake = enter < 1.0F ? Math.round((float) Math.sin(seconds * 70.0D) * 3.0F * (1.0F - enter)) : 0;
        int x = this.panelX + shake;
        int y = this.panelY;
        float alarm = 0.5F + 0.5F * (float) Math.sin(seconds * 2.0D * Math.PI * 1.6D);
        float charge = ascended >= 0L ? HubMotion.progress(ascended, CHARGE_NANOS) : 0.0F;
        CrystalUi.panel(graphics, x, y, this.panelWidth, this.panelHeight,
                CrystalTheme.fade(0xF0240308, enter), CrystalTheme.fade(0xF0100104, enter));
        int border = CrystalTheme.fade(CrystalTheme.lerp(CrystalTheme.lerp(0xFFB0101E, CrystalTheme.DANGER_BRIGHT, alarm * 0.7F),
                0xFFFFFFFF, charge), enter);
        CrystalUi.roundedOutline(graphics, x, y, this.panelWidth, this.panelHeight, border);
        CrystalUi.roundedOutline(graphics, x - 1, y - 1, this.panelWidth + 2, this.panelHeight + 2,
                CrystalTheme.withAlpha(0xFF3B4E, Math.round(60 + 80 * alarm * enter + 100 * charge)));
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
        if (charge > 0.0F) {
            // The crystals' light gathering in the warning.
            graphics.fill(x + 1, y + 1, x + this.panelWidth - 1, y + this.panelHeight - 1,
                    CrystalTheme.withAlpha(0xFFD6F0, Math.round(70 * charge * charge)));
        }
    }

    /** The obsidian rising, the crystals dropping onto it, and their part in the finale. */
    private void drawPillars(GuiGraphicsExtractor graphics, long now, CrystalAppearance look, long ascended) {
        if (this.pillars.isEmpty()) {
            return;
        }
        float since = (now - this.openedAt) / 1_000_000_000.0F;
        HubDraw.isolate(graphics);
        for (Pillar pillar : this.pillars) {
            float rise = HubMotion.easeOutBack(HubMotion.clamp01((since - pillar.delay()) / 0.5F));
            if (rise <= 0.01F) {
                continue;
            }
            boolean burst = ascended >= CHARGE_NANOS + Math.round(pillar.delay() * 300_000_000L);
            float burstTime = burst ? (ascended - CHARGE_NANOS - Math.round(pillar.delay() * 300_000_000L)) / 1_000_000_000.0F : 0.0F;
            if (!burst) {
                for (int level = 0; level < pillar.blocks(); level++) {
                    float levelRise = HubMotion.easeOutBack(HubMotion.clamp01((since - pillar.delay() - level * 0.08F) / 0.45F));
                    float y = pillar.centerOf(level) + (1.0F - levelRise) * pillar.block() * 6.0F;
                    HubDraw.isoBlock(graphics, OBSIDIAN, OBSIDIAN, pillar.x(), y, pillar.block(), -1, HubMotion.clamp01(levelRise));
                    if (level == pillar.blocks() - 1) {
                        // The top face catches the crystal's light.
                        CrystalUi.ellipse(graphics, Math.round(pillar.x()), Math.round(y - pillar.block() * 0.5F),
                                Math.round(pillar.block() * 0.7F), Math.max(1, Math.round(pillar.block() * 0.3F)),
                                CrystalTheme.withAlpha(look.haloColor(), Math.round(70 * HubMotion.clamp01(levelRise))));
                    }
                }
            } else if (burstTime < 0.6F) {
                drawRubble(graphics, pillar, burstTime);
            }
        }
        HubDraw.isolate(graphics);
        for (Pillar pillar : this.pillars) {
            float drop = HubMotion.easeOutQuart(HubMotion.clamp01((since - 0.55F - pillar.delay()) / 0.45F));
            if (drop <= 0.01F) {
                continue;
            }
            long detonateAt = CHARGE_NANOS + Math.round(pillar.delay() * 300_000_000L);
            if (ascended >= detonateAt) {
                float blast = (ascended - detonateAt) / 1_000_000_000.0F;
                drawBlast(graphics, pillar.x(), pillar.topY() - pillar.block() * 2.0F, pillar.block() * 2.0F, look.haloColor(), blast);
                continue;
            }
            float charge = ascended >= 0L ? HubMotion.progress(ascended, CHARGE_NANOS) : 0.0F;
            int size = Math.round(pillar.block() * (5.0F + 1.4F * charge));
            float lift = (1.0F - drop) * -this.height * 0.4F - charge * pillar.block();
            int x = Math.round(pillar.x() - size / 2.0F);
            int y = Math.round(pillar.topY() - size * 0.78F + lift);
            pillar.crystal().draw(graphics, x, y, size, look, now, 0.18F, 1.1F);
            if (charge > 0.0F) {
                // The beam into the warning.
                float centerX = this.panelX + this.panelWidth / 2.0F;
                float centerY = this.panelY + this.panelHeight / 2.0F;
                HubDraw.line(graphics, pillar.x(), y + size / 2.0F, centerX, centerY, 1.0F + 2.0F * charge,
                        CrystalTheme.withAlpha(look.haloColor(), Math.round(160 * charge)));
                HubDraw.line(graphics, pillar.x(), y + size / 2.0F, centerX, centerY, 1.0F,
                        CrystalTheme.withAlpha(0xFFFFFF, Math.round(200 * charge)));
            }
        }
    }

    private static void drawRubble(GuiGraphicsExtractor graphics, Pillar pillar, float time) {
        float fade = 1.0F - HubMotion.clamp01(time / 0.6F);
        for (int index = 0; index < 14; index++) {
            float angle = HubMotion.hash(index * 53L + Math.round(pillar.x())) * (float) Math.PI * 2.0F;
            float speed = pillar.block() * (3.0F + HubMotion.hash(index * 7L + Math.round(pillar.x())) * 5.0F);
            float x = pillar.x() + (float) Math.cos(angle) * speed * time;
            float y = pillar.baseY() - pillar.blocks() * pillar.block() + (float) Math.sin(angle) * speed * time * 0.7F
                    + pillar.block() * 18.0F * time * time;
            float size = pillar.block() * 0.7F;
            HubDraw.shard(graphics, x, y, size, size, angle + time * 8.0F,
                    CrystalTheme.withAlpha(index % 3 == 0 ? 0x6A3F9A : 0x1E0F30, Math.round(255 * fade)));
        }
    }

    /** A crystal going off: a white core, a ring in its glow colour and sparks, all gone in half a second. */
    private static void drawBlast(GuiGraphicsExtractor graphics, float x, float y, float block, int halo, float time) {
        float t = HubMotion.clamp01(time / 0.5F);
        if (t >= 1.0F) {
            return;
        }
        float fade = 1.0F - t;
        float radius = block * (1.0F + 5.0F * HubMotion.easeOutCubic(t));
        HubDraw.glowDisc(graphics, Math.round(x), Math.round(y), Math.round(block * 1.6F * (1.0F - t * 0.5F)), halo, 0.7F * fade);
        CrystalUi.ellipse(graphics, Math.round(x), Math.round(y), Math.max(1, Math.round(block * 0.5F * (1.0F - t))),
                Math.max(1, Math.round(block * 0.5F * (1.0F - t))), CrystalTheme.withAlpha(0xFFFFFF, Math.round(230 * fade)));
        HubDraw.ring(graphics, x, y, radius, 2, CrystalTheme.withAlpha(halo, Math.round(220 * fade)));
        HubDraw.ring(graphics, x, y, radius * 0.7F, 1, CrystalTheme.withAlpha(0xFFFFFF, Math.round(160 * fade)));
        for (int index = 0; index < 16; index++) {
            double angle = index * Math.PI * 2.0D / 16.0D + index * 0.37D;
            float distance = radius * (0.7F + 0.5F * HubMotion.hash(index * 3L));
            int sparkX = Math.round(x + (float) Math.cos(angle) * distance);
            int sparkY = Math.round(y + (float) Math.sin(angle) * distance);
            HubDraw.sparkle(graphics, sparkX, sparkY, 2, CrystalTheme.withAlpha(index % 2 == 0 ? 0xFFFFFF : halo, Math.round(230 * fade)));
        }
    }

    /** The warning itself breaking apart as the crystals go off. */
    private void drawPanelShards(GuiGraphicsExtractor graphics, float time) {
        int columns = Math.max(3, this.panelWidth / 30);
        int rows = Math.max(3, this.panelHeight / 30);
        float cellWidth = this.panelWidth / (float) columns;
        float cellHeight = this.panelHeight / (float) rows;
        float centerX = this.panelX + this.panelWidth / 2.0F;
        float centerY = this.panelY + this.panelHeight / 2.0F;
        float fade = 1.0F - HubMotion.clamp01(time / (BURST_NANOS / 1_000_000_000.0F));
        int seed = 0;
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                seed++;
                float x = this.panelX + (column + 0.5F) * cellWidth;
                float y = this.panelY + (row + 0.5F) * cellHeight;
                float dx = x - centerX;
                float dy = y - centerY;
                float length = Math.max(1.0F, (float) Math.sqrt(dx * dx + dy * dy));
                float speed = 160.0F + 240.0F * HubMotion.hash(seed * 19L);
                float px = x + dx / length * speed * time;
                float py = y + dy / length * speed * time + 300.0F * time * time;
                float angle = (HubMotion.hash(seed * 23L) - 0.5F) * 10.0F * time;
                HubDraw.shard(graphics, px, py, cellWidth * 0.9F + 1, cellHeight * 0.9F + 1, angle,
                        CrystalTheme.withAlpha(0xFF6A7A, Math.round(200 * fade)));
                HubDraw.shard(graphics, px, py, cellWidth * 0.9F, cellHeight * 0.9F, angle,
                        CrystalTheme.withAlpha(0x240308, Math.round(235 * fade)));
            }
        }
    }

    /**
     * The rift: a line of light opens into a tall ellipse in the middle, runic rings spread from it,
     * and the screen fills with deep violet. It rises and stays; nothing in it blinks.
     */
    private void drawAscension(GuiGraphicsExtractor graphics, long ascended, CrystalAppearance look) {
        long riftStart = CHARGE_NANOS + BURST_NANOS / 2;
        if (ascended < riftStart) {
            return;
        }
        float t = HubMotion.progress(ascended - riftStart, RIFT_NANOS + BURST_NANOS / 2);
        double seconds = ascended / 1_000_000_000.0D;
        int centerX = this.width / 2;
        int centerY = this.height / 2;
        int halo = look.haloColor();
        // The world around the rift darkens first, so the light has something to cut through.
        float dusk = HubMotion.smoothstep(0.0F, 0.4F, t);
        graphics.fill(0, 0, this.width, this.height, CrystalTheme.withAlpha(0x05010A, Math.round(150 * dusk)));
        // Light rays turning slowly out of the middle.
        float rayIn = HubMotion.smoothstep(0.05F, 0.5F, t);
        float reach = Math.max(this.width, this.height) * 0.75F;
        for (int ray = 0; ray < 14; ray++) {
            double angle = seconds * 0.35D + ray * Math.PI * 2.0D / 14.0D;
            float length = reach * (0.55F + 0.45F * HubMotion.hash(ray * 13L)) * rayIn;
            HubDraw.line(graphics, centerX, centerY, centerX + (float) Math.cos(angle) * length, centerY + (float) Math.sin(angle) * length,
                    ray % 2 == 0 ? 2.0F : 1.0F, CrystalTheme.withAlpha(ray % 3 == 0 ? 0xFFFFFF : halo, Math.round((ray % 2 == 0 ? 110 : 70) * rayIn)));
        }
        // Stars streaking outward, faster and longer as the rift opens.
        for (int star = 0; star < 48; star++) {
            float angle = HubMotion.hash(star * 31L) * (float) Math.PI * 2.0F;
            float phase = (float) ((seconds * (0.6F + HubMotion.hash(star * 7L)) + HubMotion.hash(star * 3L)) % 1.0D);
            float distance = reach * phase * phase;
            float tail = 4.0F + 26.0F * t * phase;
            float x0 = centerX + (float) Math.cos(angle) * distance;
            float y0 = centerY + (float) Math.sin(angle) * distance;
            float x1 = centerX + (float) Math.cos(angle) * (distance + tail);
            float y1 = centerY + (float) Math.sin(angle) * (distance + tail);
            HubDraw.line(graphics, x0, y0, x1, y1, 1.0F, CrystalTheme.withAlpha(star % 4 == 0 ? halo : 0xF4E8FF, Math.round(220 * phase * rayIn)));
        }
        // The rift: a slit that opens into a tall eye of light, brightest at its heart.
        float open = HubMotion.easeInOutCubic(t);
        int halfWidth = Math.max(1, Math.round(2 + this.width * 0.42F * open * open));
        int halfHeight = Math.round(this.height * (0.12F + 0.5F * HubMotion.easeOutCubic(Math.min(1.0F, t * 1.5F))));
        int[] tones = {CrystalTheme.withAlpha(halo, 60), CrystalTheme.withAlpha(halo, 110), CrystalTheme.withAlpha(CrystalTheme.lerp(halo, 0xFFFFFFFF, 0.45F), 170),
                CrystalTheme.withAlpha(0xF6EEFF, 220), CrystalTheme.withAlpha(0xFFFFFF, 255)};
        float[] sizes = {1.25F, 1.0F, 0.72F, 0.45F, 0.2F};
        for (int layer = 0; layer < tones.length; layer++) {
            int rx = Math.max(1, Math.round(halfWidth * sizes[layer]));
            int ry = Math.max(1, Math.round(halfHeight * (0.55F + 0.45F * sizes[layer])));
            CrystalUi.ellipse(graphics, centerX, centerY, rx, ry, CrystalTheme.fade(tones[layer], Math.min(1.0F, t * 3.0F)));
        }
        CrystalUi.ellipseRim(graphics, centerX, centerY, halfWidth, halfHeight, CrystalTheme.withAlpha(0xFFFFFF, Math.round(200 * Math.min(1.0F, t * 3.0F))));
        for (int ring = 0; ring < 3; ring++) {
            float spread = HubMotion.clamp01(t * 1.4F - ring * 0.18F);
            if (spread <= 0.0F || spread >= 1.0F) {
                continue;
            }
            HubDraw.ring(graphics, centerX, centerY, Math.max(this.width, this.height) * 0.7F * spread, 2,
                    CrystalTheme.withAlpha(ring == 1 ? 0xFFFFFF : halo, Math.round(200 * (1.0F - spread))));
        }
        // Last, the screen settles into the colour the practice setup opens on. It rises and stays.
        float fill = HubMotion.smoothstep(0.6F, 1.0F, t);
        graphics.fill(0, 0, this.width, this.height, CrystalTheme.withAlpha(0x12061C, Math.round(255 * fill)));
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

    /** The warning sign: a triangle breathing inside rings of red light, its mark flickering now and then. */
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
    public boolean shouldCloseOnEsc() {
        return !ascending();
    }

    @Override
    public void onClose() {
        if (!ascending()) {
            this.minecraft.setScreen(this.parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }
}
