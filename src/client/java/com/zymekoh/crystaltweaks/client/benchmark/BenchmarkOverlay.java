package com.zymekoh.crystaltweaks.client.benchmark;

import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * The small "recording" chip at the top of the screen while a benchmark runs. It is also where
 * frames are counted: the HUD is drawn once per frame, so its calls are the frame clock.
 */
public final class BenchmarkOverlay {
    private static final long HINT_NANOS = 7_000_000_000L;

    private BenchmarkOverlay() {
    }

    static void draw(GuiGraphicsExtractor graphics) {
        long now = System.nanoTime();
        CrystalBenchmark.onFrame(now);
        if (!CrystalBenchmark.recording()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        Font font = minecraft.font;
        boolean spanish = minecraft.getLanguageManager().getSelected().startsWith("es");
        long elapsed = CrystalBenchmark.elapsedNanos();
        long seconds = elapsed / 1_000_000_000L;
        String line = String.format(Locale.ROOT, spanish ? "Benchmark %d:%02d · %d/%d roturas" : "Benchmark %d:%02d · %d/%d breaks",
                seconds / 60, seconds % 60, CrystalBenchmark.confirmedBreaks(), CrystalBenchmark.TARGET_BREAKS);
        String hint = spanish ? "Coloca y rompe cristales como siempre" : "Place and break crystals as usual";
        boolean showHint = elapsed < HINT_NANOS;
        int width = Math.max(font.width(line) + 18, showHint ? font.width(hint) + 12 : 0);
        int height = showHint ? 24 : 14;
        int x = (graphics.guiWidth() - width) / 2;
        int y = 4;
        float pulse = 0.5F + 0.5F * (float) Math.sin(now / 1_000_000_000.0D * 4.0D);
        graphics.fill(x, y, x + width, y + height, 0xC0120616);
        int border = 0xFF000000 | Math.round(200 + 55 * pulse) << 16 | 0x3B << 8 | 0x4E;
        graphics.fill(x, y, x + width, y + 1, border);
        graphics.fill(x, y + height - 1, x + width, y + height, border);
        graphics.fill(x, y, x + 1, y + height, border);
        graphics.fill(x + width - 1, y, x + width, y + height, border);
        int dot = pulse > 0.5F ? 0xFFFF3B4E : 0xFF8A1A26;
        graphics.fill(x + 5, y + 5, x + 9, y + 9, dot);
        graphics.text(font, line, x + 13, y + 3, 0xFFF7EDFF, true);
        if (showHint) {
            graphics.text(font, hint, x + (width - font.width(hint)) / 2, y + 14, 0xFFC6B5CC, true);
        }
    }
}
