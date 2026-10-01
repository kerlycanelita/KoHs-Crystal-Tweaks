package com.zymekoh.crystaltweaks.client.hub;

import com.zymekoh.crystaltweaks.client.CrystalAppearance;
import com.zymekoh.crystaltweaks.client.CrystalTheme;
import com.zymekoh.crystaltweaks.client.CrystalUi;
import com.zymekoh.crystaltweaks.client.hub.HubLayout.Rect;
import java.util.Locale;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * The enemy menu's Advanced tab: nothing to set yet, said loudly. "Coming soon" rises letter by
 * letter in crimson, rides a slow wave, glitches now and then, and a scan line passes over it; two
 * runic rings turn behind it. Under it, the mining scene of {@link MiningGame}.
 */
final class ComingSoonPanel {
    private final MiningGame game = new MiningGame();
    private long enteredAt = System.nanoTime();

    void enter() {
        this.enteredAt = System.nanoTime();
    }

    boolean mouseClicked(double mouseX, double mouseY) {
        return this.game.mouseClicked(mouseX, mouseY);
    }

    void render(GuiGraphicsExtractor graphics, Font font, Rect area, long now, int mouseX, int mouseY, CrystalAppearance look,
            boolean spanish, float alpha) {
        if (area.isEmpty() || alpha <= 0.02F) {
            return;
        }
        HubSkin skin = HubSkin.current();
        double seconds = now / 1_000_000_000.0D;
        float since = (now - this.enteredAt) / 1_000_000_000.0F;
        int titleBlock = Math.min(area.height() / 2, 58);
        int centerX = area.centerX();
        int titleCenterY = area.y() + titleBlock / 2 + 2;
        // Rings of runes turning behind the words.
        float ringIn = HubMotion.easeOutCubic(HubMotion.clamp01(since / 0.6F));
        float radius = Math.min(area.width() * 0.32F, titleBlock * 0.62F) * (0.8F + 0.2F * ringIn);
        HubDraw.isolate(graphics);
        drawRuneRing(graphics, centerX, titleCenterY, radius, seconds * 0.35D, CrystalTheme.withAlpha(skin.accent, Math.round(110 * alpha * ringIn)));
        drawRuneRing(graphics, centerX, titleCenterY, radius * 0.72F, -seconds * 0.5D, CrystalTheme.withAlpha(skin.hot, Math.round(80 * alpha * ringIn)));
        HubDraw.isolate(graphics);
        String title = (spanish ? "Próximamente" : "Coming soon").toUpperCase(Locale.ROOT);
        float scale = Math.max(1.0F, Math.min(2.6F, (area.width() - 16) / (float) Math.max(1, font.width(title) + title.length())));
        drawTitle(graphics, font, title, centerX, titleCenterY - Math.round(4.5F * scale), scale, seconds, since, alpha, skin);
        String subtitle = spanish ? "Opciones avanzadas para cristales ajenos, en camino." : "Advanced options for enemy crystals, on their way.";
        float subtitleIn = HubMotion.easeOutCubic(HubMotion.clamp01((since - 0.6F) / 0.4F));
        int subtitleY = titleCenterY + Math.round(6 * scale) + 2;
        String fitted = HubDraw.fit(font, subtitle, area.width() - 12);
        CrystalUi.centered(graphics, font, fitted, centerX, subtitleY + Math.round((1.0F - subtitleIn) * 4),
                CrystalTheme.fade(skin.muted, alpha * subtitleIn));
        Rect scene = new Rect(area.x(), subtitleY + 12, area.width(), Math.max(0, area.bottom() - subtitleY - 12));
        this.game.render(graphics, font, scene, now, mouseX, mouseY, look, spanish, alpha * HubMotion.clamp01((since - 0.3F) / 0.4F));
    }

    private static void drawRuneRing(GuiGraphicsExtractor graphics, int centerX, int centerY, float radius, double turn, int color) {
        if (((color >>> 24) & 255) < 4 || radius < 6.0F) {
            return;
        }
        HubDraw.ring(graphics, centerX, centerY, radius, 1, color);
        // Short ticks around it, every fourth one longer: the ring's runes.
        for (int index = 0; index < 24; index++) {
            double angle = turn + index * Math.PI * 2.0D / 24.0D;
            float inner = radius - (index % 4 == 0 ? 5.0F : 2.5F);
            float x0 = centerX + (float) Math.cos(angle) * inner;
            float y0 = centerY + (float) Math.sin(angle) * inner * 0.92F;
            float x1 = centerX + (float) Math.cos(angle) * radius;
            float y1 = centerY + (float) Math.sin(angle) * radius * 0.92F;
            HubDraw.line(graphics, x0, y0, x1, y1, 1.0F, color);
        }
    }

    private static void drawTitle(GuiGraphicsExtractor graphics, Font font, String title, int centerX, int y, float scale,
            double seconds, float since, float alpha, HubSkin skin) {
        int total = 0;
        for (int index = 0; index < title.length(); index++) {
            total += font.width(String.valueOf(title.charAt(index))) + 1;
        }
        float x = centerX - (total - 1) * scale / 2.0F;
        boolean glitch = seconds % 2.8D < 0.12D && since > 1.2F;
        float jitter = glitch ? (float) Math.sin(seconds * 95.0D) * 1.6F : 0.0F;
        HubDraw.isolate(graphics);
        for (int index = 0; index < title.length(); index++) {
            String letter = String.valueOf(title.charAt(index));
            float in = HubMotion.easeOutCubic(HubMotion.clamp01((since - 0.1F - index * 0.05F) / 0.3F));
            float advance = (font.width(letter) + 1) * scale;
            if (letter.equals(" ") || in <= 0.02F) {
                x += advance;
                continue;
            }
            float wave = (float) Math.sin(seconds * 3.0D - index * 0.5D) * 1.6F;
            float letterY = y + wave + (1.0F - in) * 10.0F;
            float shift = (float) ((seconds * 0.3D + index / (double) title.length()) % 1.0D);
            int color = CrystalTheme.lerp(skin.hot, skin.accentBright, 0.5F + 0.5F * (float) Math.sin(shift * Math.PI * 2.0D));
            float letterAlpha = alpha * in;
            if (glitch) {
                letter(graphics, font, letter, x - 1.5F + jitter, letterY, scale, CrystalTheme.fade(0xB0FF2040, letterAlpha));
                letter(graphics, font, letter, x + 1.5F - jitter, letterY, scale, CrystalTheme.fade(0xB040E0FF, letterAlpha));
            }
            letter(graphics, font, letter, x + 1.0F, letterY + 1.0F, scale, CrystalTheme.fade(0xFF3A0610, letterAlpha));
            letter(graphics, font, letter, x, letterY, scale, CrystalTheme.fade(color, letterAlpha));
            x += advance;
        }
        // A scan line passing down over the word every few seconds.
        double scanPhase = seconds % 3.6D;
        if (scanPhase < 0.6D && since > 1.0F) {
            int height = Math.round(9 * scale) + 4;
            int scanY = y - 2 + (int) Math.round(scanPhase / 0.6D * height);
            int width = Math.round(total * scale) + 8;
            graphics.fill(centerX - width / 2, scanY, centerX + width / 2, scanY + 1, CrystalTheme.withAlpha(0xFFFFFF, Math.round(110 * alpha)));
        }
        HubDraw.isolate(graphics);
    }

    private static void letter(GuiGraphicsExtractor graphics, Font font, String letter, float x, float y, float scale, int color) {
        if (((color >>> 24) & 255) < 8) {
            return;
        }
        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y);
        graphics.pose().scale(scale, scale);
        graphics.text(font, letter, 0, 0, color, false);
        graphics.pose().popMatrix();
    }
}
