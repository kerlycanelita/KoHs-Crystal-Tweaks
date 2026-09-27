package com.zymekoh.crystaltweaks.client.benchmark;

import com.zymekoh.crystaltweaks.client.CrystalTheme;
import com.zymekoh.crystaltweaks.client.CrystalUi;
import com.zymekoh.crystaltweaks.client.CrystalVisualConfig;
import com.zymekoh.crystaltweaks.client.PurpleCloseButton;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

/**
 * The optimizer benchmark: what is installed, the last run, and how it compares with the last run
 * taken under a different optimizer. A Normal/Dev switch at the top chooses between plain words
 * and the numbers with their definitions.
 */
public final class CrystalBenchmarkScreen extends Screen {
    private static final long ENTER_NANOS = 380_000_000L;
    private static final int MAX_WIDTH = 460;
    private static final int MAX_HEIGHT = 300;

    private final Screen parent;
    private final boolean spanish;
    private long openedAt;
    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;
    private int bodyTop;
    private int bodyBottom;
    private int bodyLeft;
    private int bodyRight;
    private int contentHeight;
    private int scroll;
    private boolean shownRecording;
    private final List<Row> rows = new ArrayList<>();
    private PurpleCloseButton normalTab;
    private PurpleCloseButton devTab;

    public CrystalBenchmarkScreen(Screen parent) {
        super(Component.literal("Crystal Tweaks benchmark"));
        this.parent = parent;
        this.spanish = CrystalUi.spanish();
    }

    @Override
    protected void init() {
        if (this.openedAt == 0L) {
            this.openedAt = System.nanoTime();
        }
        int margin = Mth.clamp(this.width / 24, 6, 18);
        this.panelWidth = Math.max(160, Math.min(MAX_WIDTH, this.width - margin * 2));
        this.panelHeight = Math.max(150, Math.min(MAX_HEIGHT, this.height - margin * 2));
        this.panelX = (this.width - this.panelWidth) / 2;
        this.panelY = (this.height - this.panelHeight) / 2;

        // The Normal/Dev switch sits above everything else in the window, as asked for.
        int tabWidth = Math.min(90, (this.panelWidth - 30) / 2);
        int tabY = this.panelY + 22;
        int tabsX = this.panelX + (this.panelWidth - tabWidth * 2 - 4) / 2;
        this.normalTab = addRenderableWidget(new PurpleCloseButton(tabsX, tabY, tabWidth, 15,
                Component.literal("Normal"), ignored -> setDev(false)));
        this.devTab = addRenderableWidget(new PurpleCloseButton(tabsX + tabWidth + 4, tabY, tabWidth, 15,
                Component.literal("Dev"), ignored -> setDev(true)).icon(PurpleCloseButton.Icon.GEAR));
        this.normalTab.setTooltip(Tooltip.create(Component.literal(this.spanish
                ? "Explicado para jugadores: qué significa cada número y qué puedes hacer."
                : "Explained for players: what each number means and what you can do about it.")));
        this.devTab.setTooltip(Tooltip.create(Component.literal(this.spanish
                ? "Para desarrolladores: percentiles, recuentos, puntos de enganche, hilos y sesgos."
                : "For developers: percentiles, counts, hook points, threads and biases.")));
        refreshTabs();

        int footerY = this.panelY + this.panelHeight - 24;
        int gap = 5;
        int buttonWidth = Math.max(40, Math.min(120, (this.panelWidth - 20 - gap * 2) / 3));
        int buttonsX = this.panelX + (this.panelWidth - buttonWidth * 3 - gap * 2) / 2;
        boolean inWorld = this.minecraft.level != null && this.minecraft.player != null;
        PurpleCloseButton start = addRenderableWidget(new PurpleCloseButton(buttonsX, footerY, buttonWidth, 18,
                Component.literal(CrystalBenchmark.recording()
                        ? (this.spanish ? "Detener" : "Stop")
                        : (this.spanish ? "Iniciar" : "Start")),
                ignored -> startOrStop()).icon(PurpleCloseButton.Icon.CHART));
        start.active = inWorld || CrystalBenchmark.recording();
        start.setSelected(!CrystalBenchmark.recording() && inWorld);
        start.setTooltip(Tooltip.create(Component.literal(inWorld || CrystalBenchmark.recording()
                ? (this.spanish
                        ? "Empieza a medir y vuelve al juego. Coloca y rompe cristales como siempre; termina sola tras "
                                + CrystalBenchmark.TARGET_BREAKS + " roturas o 2 minutos."
                        : "Starts measuring and returns to the game. Place and break crystals as usual; it ends by itself after "
                                + CrystalBenchmark.TARGET_BREAKS + " breaks or 2 minutes.")
                : (this.spanish ? "Entra a un mundo o servidor para medir." : "Join a world or server to measure."))));
        PurpleCloseButton clear = addRenderableWidget(new PurpleCloseButton(buttonsX + buttonWidth + gap, footerY,
                buttonWidth, 18, Component.literal(this.spanish ? "Borrar" : "Clear"), ignored -> clearRuns()));
        clear.active = !BenchmarkStore.runs().isEmpty();
        clear.setTooltip(Tooltip.create(Component.literal(this.spanish
                ? "Borra las pruebas guardadas."
                : "Deletes the saved runs.")));
        addRenderableWidget(new PurpleCloseButton(buttonsX + (buttonWidth + gap) * 2, footerY, buttonWidth, 18,
                Component.literal(this.spanish ? "Listo" : "Done"), ignored -> onClose()));

        this.bodyLeft = this.panelX + 12;
        this.bodyRight = this.panelX + this.panelWidth - 12;
        this.bodyTop = tabY + 22;
        this.bodyBottom = footerY - 6;
        rebuildRows();
    }

    private void setDev(boolean dev) {
        CrystalVisualConfig.setBenchmarkDevMode(dev);
        CrystalVisualConfig.save();
        refreshTabs();
        this.scroll = 0;
        rebuildRows();
    }

    private void refreshTabs() {
        boolean dev = CrystalVisualConfig.benchmarkDevMode();
        this.normalTab.setSelected(!dev);
        this.devTab.setSelected(dev);
    }

    private void startOrStop() {
        if (CrystalBenchmark.recording()) {
            CrystalBenchmark.stop();
            rebuildWidgets();
            return;
        }
        if (this.minecraft.level == null || this.minecraft.player == null) {
            return;
        }
        CrystalBenchmark.start();
        this.minecraft.setScreen(null);
    }

    private void clearRuns() {
        BenchmarkStore.clear();
        rebuildWidgets();
    }

    /** Wraps the report to the current width once, not every frame. */
    private void rebuildRows() {
        this.shownRecording = CrystalBenchmark.recording();
        List<BenchmarkRun> runs = BenchmarkStore.runs();
        BenchmarkRun latest = runs.isEmpty() ? CrystalBenchmark.lastFinished() : runs.get(runs.size() - 1);
        BenchmarkRun previous = BenchmarkStore.comparisonFor(latest);
        List<BenchmarkReport.Line> lines = BenchmarkReport.build(this.spanish, CrystalVisualConfig.benchmarkDevMode(),
                latest, previous, this.shownRecording);
        this.rows.clear();
        int width = Math.max(40, this.bodyRight - this.bodyLeft - 6);
        int y = 0;
        for (BenchmarkReport.Line line : lines) {
            switch (line.kind()) {
                case SPACER -> y += 5;
                case HEADING -> {
                    y += 2;
                    this.rows.add(new Row(line.kind(), this.font.split(Component.literal(line.text()), width), null, y));
                    y += this.font.lineHeight + 4;
                }
                case VALUE -> {
                    int labelWidth = Math.min(width * 55 / 100, this.font.width(line.text()) + 6);
                    List<FormattedCharSequence> label = this.font.split(Component.literal(line.text()), Math.max(20, labelWidth));
                    List<FormattedCharSequence> value = this.font.split(Component.literal(line.value()),
                            Math.max(20, width - Math.max(labelWidth, width * 42 / 100) - 4));
                    this.rows.add(new Row(line.kind(), label, value, y));
                    y += Math.max(label.size(), value.size()) * (this.font.lineHeight + 1) + 2;
                }
                default -> {
                    List<FormattedCharSequence> text = this.font.split(Component.literal(line.text()), width);
                    this.rows.add(new Row(line.kind(), text, null, y));
                    y += text.size() * (this.font.lineHeight + 1) + 2;
                }
            }
        }
        this.contentHeight = y;
        this.scroll = Mth.clamp(this.scroll, 0, maxScroll());
    }

    private int maxScroll() {
        return Math.max(0, this.contentHeight - (this.bodyBottom - this.bodyTop));
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0x9005020A);
        this.minecraft.gui.extractDeferredSubtitles();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        long now = System.nanoTime();
        double seconds = now / 1_000_000_000.0D;
        if (this.shownRecording != CrystalBenchmark.recording()) {
            rebuildWidgets();
        }
        float enter = CrystalTheme.easeOutCubic(Mth.clamp((now - this.openedAt) / (float) ENTER_NANOS, 0.0F, 1.0F));
        CrystalUi.floatingParticles(graphics, this.width, this.height, seconds);
        CrystalUi.panel(graphics, this.panelX, this.panelY, this.panelWidth, this.panelHeight,
                CrystalTheme.fade(0xEE1B0928, 0.4F + 0.6F * enter), CrystalTheme.fade(0xEE0A0310, 0.4F + 0.6F * enter));
        CrystalUi.roundedOutline(graphics, this.panelX, this.panelY, this.panelWidth, this.panelHeight,
                CrystalTheme.fade(CrystalTheme.PANEL_BORDER, enter));
        if (enter > 0.98F) {
            CrystalUi.comets(graphics, this.panelX, this.panelY, this.panelWidth, this.panelHeight, seconds, 0xFFE6C4FF);
        }
        CrystalUi.crystalIcon(graphics, this.panelX + 14, this.panelY + 11, 9, seconds, enter);
        String title = this.spanish ? "Benchmark de optimizadores" : "Optimizer benchmark";
        CrystalUi.label(graphics, this.font, title, this.panelX + 24, this.panelY + 7, CrystalTheme.fade(CrystalTheme.TITLE, enter));
        CrystalUi.glint(graphics, this.font, title, this.panelX + 24, this.panelY + 7, seconds + 0.7D, enter);
        if (CrystalBenchmark.recording()) {
            float pulse = 0.5F + 0.5F * (float) Math.sin(seconds * 5.0D);
            int dotX = this.panelX + this.panelWidth - 16;
            graphics.fill(dotX, this.panelY + 9, dotX + 5, this.panelY + 14,
                    CrystalTheme.lerp(0xFF8A1A26, CrystalTheme.DANGER, pulse));
        }
        graphics.fill(this.panelX + 10, this.bodyTop - 4, this.panelX + this.panelWidth - 10, this.bodyTop - 3,
                CrystalTheme.HEADER_LINE);

        drawBody(graphics, enter);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    private void drawBody(GuiGraphicsExtractor graphics, float enter) {
        graphics.enableScissor(this.bodyLeft - 2, this.bodyTop, this.bodyRight + 2, this.bodyBottom);
        int lineStep = this.font.lineHeight + 1;
        int width = this.bodyRight - this.bodyLeft - 6;
        for (Row row : this.rows) {
            int y = this.bodyTop + row.y() - this.scroll;
            int height = Math.max(row.text().size(), row.value() == null ? 0 : row.value().size()) * lineStep;
            if (y + height < this.bodyTop || y > this.bodyBottom) {
                continue;
            }
            int color = switch (row.kind()) {
                case HEADING -> CrystalTheme.ACCENT_BRIGHT;
                case NOTE -> CrystalTheme.TEXT_MUTED;
                case GOOD -> CrystalTheme.STATUS_ACTIVE;
                case WARN -> CrystalTheme.STATUS_PAUSED;
                default -> CrystalTheme.TEXT;
            };
            color = CrystalTheme.fade(color, enter);
            if (row.kind() == BenchmarkReport.Kind.HEADING) {
                graphics.fill(this.bodyLeft, y + this.font.lineHeight + 1, this.bodyLeft + Math.min(width, 120),
                        y + this.font.lineHeight + 2, CrystalTheme.fade(0x66B85BE8, enter));
            }
            int lineY = y;
            for (FormattedCharSequence line : row.text()) {
                graphics.text(this.font, line, this.bodyLeft, lineY, color, false);
                lineY += lineStep;
            }
            if (row.value() != null) {
                int valueY = y;
                for (FormattedCharSequence line : row.value()) {
                    graphics.text(this.font, line, this.bodyRight - 6 - this.font.width(line), valueY,
                            CrystalTheme.fade(0xFFE6C4FF, enter), false);
                    valueY += lineStep;
                }
            }
        }
        graphics.disableScissor();
        int maxScroll = maxScroll();
        if (maxScroll > 0) {
            int trackX = this.bodyRight + 1;
            int trackHeight = this.bodyBottom - this.bodyTop;
            int thumbHeight = Math.max(10, trackHeight * trackHeight / Math.max(1, this.contentHeight));
            int thumbY = this.bodyTop + Math.round((trackHeight - thumbHeight) * this.scroll / (float) maxScroll);
            graphics.fill(trackX, this.bodyTop, trackX + 2, this.bodyBottom, CrystalTheme.SCROLL_TRACK);
            graphics.fill(trackX, thumbY, trackX + 2, thumbY + thumbHeight, CrystalTheme.SCROLL_THUMB);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (maxScroll() > 0 && verticalAmount != 0.0D) {
            this.scroll = Mth.clamp(this.scroll - (int) Math.signum(verticalAmount) * (this.font.lineHeight + 1) * 3,
                    0, maxScroll());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    private record Row(BenchmarkReport.Kind kind, List<FormattedCharSequence> text, List<FormattedCharSequence> value,
            int y) {
    }
}
