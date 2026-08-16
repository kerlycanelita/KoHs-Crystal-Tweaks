package com.zymekoh.crystaltweaks.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.zymekoh.crystaltweaks.CrystalTweaksClient;
import com.zymekoh.crystaltweaks.client.compat.ConflictScanner;
import com.zymekoh.crystaltweaks.client.compat.ConflictScanner.ConflictArea;
import com.zymekoh.crystaltweaks.client.compat.ConflictScanner.ConflictEntry;
import com.zymekoh.crystaltweaks.client.compat.ConflictScanner.ConflictPoint;
import com.zymekoh.crystaltweaks.client.compat.ConflictScanner.ConflictReport;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

/** A non-blocking, local compatibility report. */
public final class ConflictMonitorScreen extends Screen {
    private static final int CARD_GAP = 6;
    private final Screen parent;
    private final boolean spanish;
    private final Map<String, Identifier> icons = new HashMap<>();
    private final Map<String, Integer> iconWidths = new HashMap<>();
    private final Map<String, Integer> iconHeights = new HashMap<>();
    private final Set<String> unavailableIcons = new LinkedHashSet<>();

    private volatile ConflictReport report;
    private volatile boolean scanning = true;
    private volatile boolean scanFailed;
    private PurpleCloseButton backButton;
    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;
    private int bodyX;
    private int bodyY;
    private int bodyWidth;
    private int bodyHeight;
    private int scroll;
    private int contentHeight;
    private long openedAt;

    public ConflictMonitorScreen(Screen parent) {
        super(Component.literal("Conflict Monitor"));
        this.parent = parent;
        this.spanish = usesSpanish();
    }

    @Override
    protected void init() {
        calculateLayout();
        int buttonWidth = Math.min(142, Math.max(70, this.panelWidth / 3));
        this.backButton = addRenderableWidget(new PurpleCloseButton(
                this.panelX + (this.panelWidth - buttonWidth) / 2,
                this.panelY + this.panelHeight - 25,
                buttonWidth,
                18,
                Component.literal(this.spanish ? "Volver" : "Back"),
                ignored -> onClose()));
        if (this.openedAt == 0L) {
            this.openedAt = System.currentTimeMillis();
            startScan();
        }
    }

    private void calculateLayout() {
        int horizontalMargin = Mth.clamp(this.width / 30, 5, 18);
        int verticalMargin = Mth.clamp(this.height / 30, 5, 14);
        this.panelWidth = Math.min(560, Math.max(1, this.width - horizontalMargin * 2));
        this.panelHeight = Math.min(350, Math.max(1, this.height - verticalMargin * 2));
        this.panelX = (this.width - this.panelWidth) / 2;
        this.panelY = (this.height - this.panelHeight) / 2;
        int padding = Mth.clamp(this.panelWidth / 55, 5, 10);
        this.bodyX = this.panelX + padding;
        this.bodyY = this.panelY + 42;
        this.bodyWidth = Math.max(1, this.panelWidth - padding * 2);
        this.bodyHeight = Math.max(1, this.panelHeight - 74);
        this.contentHeight = calculateContentHeight();
        this.scroll = Mth.clamp(this.scroll, 0, maxScroll());
    }

    private void startScan() {
        CompletableFuture.supplyAsync(ConflictScanner::scan).whenComplete((result, error) -> {
            Minecraft minecraft = Minecraft.getInstance();
            minecraft.execute(() -> {
                this.scanning = false;
                if (error != null) {
                    this.scanFailed = true;
                    CrystalTweaksClient.LOGGER.warn("Conflict Monitor scan failed", error);
                } else {
                    this.report = result;
                }
                this.contentHeight = calculateContentHeight();
                this.scroll = Mth.clamp(this.scroll, 0, maxScroll());
            });
        });
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0x24040109);
        this.minecraft.gui.extractDeferredSubtitles();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        long now = System.currentTimeMillis();
        drawParticles(graphics, now);
        drawPanel(graphics);
        drawHeader(graphics, now);

        graphics.enableScissor(
                this.bodyX,
                this.bodyY,
                this.bodyX + this.bodyWidth,
                this.bodyY + this.bodyHeight);
        drawBody(graphics, now);
        graphics.disableScissor();

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.nextStratum();
        drawScrollbar(graphics);
    }

    private void drawPanel(GuiGraphicsExtractor graphics) {
        fillRounded(graphics, this.panelX, this.panelY, this.panelWidth, this.panelHeight,
                0xC81A0927, 0xB208030E);
        drawRoundedOutline(graphics, this.panelX, this.panelY, this.panelWidth, this.panelHeight,
                0xE3C36AF0);
        graphics.fillGradient(
                this.bodyX,
                this.bodyY,
                this.bodyX + this.bodyWidth,
                this.bodyY + this.bodyHeight,
                0x4613071C,
                0x2C07020C);
    }

    private void drawHeader(GuiGraphicsExtractor graphics, long now) {
        int glow = 220 + Math.round(25.0F * (0.5F + 0.5F * (float) Math.sin(now / 270.0F)));
        graphics.centeredText(
                this.font,
                Component.literal(this.spanish ? "Monitor de conflictos" : "Conflict Monitor"),
                this.panelX + this.panelWidth / 2,
                this.panelY + 7,
                0xFF000000 | glow << 16 | 222 << 8 | 255);
        String subtitle = this.spanish
                ? "Análisis local de coincidencias exactas de mixins"
                : "Local scan for exact Mixin overlaps";
        graphics.centeredText(
                this.font,
                this.font.plainSubstrByWidth(subtitle, Math.max(1, this.panelWidth - 16)),
                this.panelX + this.panelWidth / 2,
                this.panelY + 21,
                0xFFCBBAD2);
    }

    private void drawBody(GuiGraphicsExtractor graphics, long now) {
        int y = this.bodyY - this.scroll + 5;
        if (this.scanning) {
            String dots = ".".repeat((int) (now / 350L % 4L));
            drawWrapped(graphics,
                    this.spanish ? "Analizando mixins instalados" + dots : "Scanning installed mixins" + dots,
                    this.bodyX + 6,
                    y,
                    this.bodyWidth - 12,
                    0xFFF0DFFF);
            return;
        }
        if (this.scanFailed || this.report == null) {
            drawWrapped(graphics,
                    this.spanish
                            ? "El análisis local no pudo completarse. Crystal Tweaks seguirá funcionando normalmente."
                            : "The local scan could not be completed. Crystal Tweaks will continue normally.",
                    this.bodyX + 6,
                    y,
                    this.bodyWidth - 12,
                    0xFFFFB4C8);
            return;
        }

        String summary = this.spanish
                ? "Mods analizados: " + this.report.scannedMods()
                        + "  •  Mixins: " + this.report.scannedMixins()
                        + "  •  Coincidencias: " + this.report.conflicts().size()
                : "Mods scanned: " + this.report.scannedMods()
                        + "  •  Mixins: " + this.report.scannedMixins()
                        + "  •  Matches: " + this.report.conflicts().size();
        y += drawWrapped(graphics, summary, this.bodyX + 6, y, this.bodyWidth - 12, 0xFFE0CBE8);
        y += 4;
        String qualification = this.spanish
                ? "Solo se muestran coincidencias de la misma clase y método. Los plugins dinámicos o transformadores externos pueden no ser visibles."
                : "Only identical class-and-method targets are shown. Dynamic plugins or external transformers may not be visible.";
        y += drawWrapped(graphics, qualification, this.bodyX + 6, y, this.bodyWidth - 12, 0xFFAFA0B7);
        y += 7;

        if (this.report.conflicts().isEmpty()) {
            String message = this.spanish
                    ? "No se detectaron coincidencias exactas. Esto no demuestra compatibilidad universal, pero el análisis no encontró interferencias estáticas directas."
                    : "No exact overlaps were detected. This does not prove universal compatibility, but no direct static interference was found.";
            drawWrapped(graphics, message, this.bodyX + 6, y, this.bodyWidth - 12, 0xFFC6F1D0);
        } else {
            for (ConflictEntry entry : this.report.conflicts()) {
                int cardHeight = cardHeight(entry);
                drawCard(graphics, entry, this.bodyX + 3, y, this.bodyWidth - 6, cardHeight);
                y += cardHeight + CARD_GAP;
            }
        }

        if (this.report.failedMods() > 0) {
            String incomplete = this.spanish
                    ? "Análisis parcial: no se pudieron leer " + this.report.failedMods() + " mod(s)."
                    : "Partial scan: " + this.report.failedMods() + " mod(s) could not be read.";
            drawWrapped(graphics, incomplete, this.bodyX + 6, y + 2, this.bodyWidth - 12, 0xFFFFC28F);
        }
    }

    private void drawCard(
            GuiGraphicsExtractor graphics,
            ConflictEntry entry,
            int x,
            int y,
            int width,
            int height
    ) {
        fillRounded(graphics, x, y, width, height, 0xB0271035, 0x9B100719);
        drawRoundedOutline(graphics, x, y, width, height, 0xB79048B2);
        int iconSize = Math.min(34, Math.max(22, height - 12));
        int iconX = x + 6;
        int iconY = y + 6;
        Identifier icon = iconFor(entry);
        if (icon != null) {
            graphics.blit(icon, iconX, iconY, iconSize, iconSize, 0.0F, 0.0F, 1.0F, 1.0F);
        } else {
            graphics.fill(iconX, iconY, iconX + iconSize, iconY + iconSize, 0xFF351044);
            drawOutline(graphics, iconX, iconY, iconSize, iconSize, 0xFFC06BE8);
            String initial = entry.modName().isBlank()
                    ? "?"
                    : entry.modName().substring(0, 1).toUpperCase(Locale.ROOT);
            graphics.centeredText(
                    this.font,
                    initial,
                    iconX + iconSize / 2,
                    iconY + (iconSize - this.font.lineHeight) / 2,
                    0xFFF4E6FA);
        }

        int textX = iconX + iconSize + 7;
        int textWidth = Math.max(20, x + width - textX - 6);
        graphics.text(this.font,
                this.font.plainSubstrByWidth(entry.modName(), textWidth),
                textX,
                y + 6,
                0xFFF2E5F7,
                false);
        String identity = (this.spanish ? "Autor: " : "Author: ") + entry.authors()
                + "  •  " + entry.modId() + " " + entry.version();
        graphics.text(this.font,
                this.font.plainSubstrByWidth(identity, textWidth),
                textX,
                y + 6 + this.font.lineHeight,
                0xFFBFAFC7,
                false);

        int lineY = Math.max(y + 6 + iconSize, y + 10 + this.font.lineHeight * 2);
        for (String reason : reasons(entry)) {
            lineY += drawWrapped(
                    graphics,
                    "• " + reason,
                    x + 7,
                    lineY,
                    width - 14,
                    0xFFE3CBEA);
        }
        lineY += 2;
        for (ConflictPoint point : entry.points()) {
            String target = point.targetClass() + "#" + point.targetMethod();
            lineY += drawWrapped(
                    graphics,
                    target,
                    x + 9,
                    lineY,
                    width - 18,
                    0xFFFFD58A);
            String mixin = (this.spanish ? "Mixin externo: " : "Foreign mixin: ")
                    + point.foreignMixinClass();
            lineY += drawWrapped(
                    graphics,
                    mixin,
                    x + 12,
                    lineY,
                    width - 21,
                    0xFFAD9DB5);
            String ownMixin = "Crystal Tweaks: " + point.ownMixinClass();
            lineY += drawWrapped(
                    graphics,
                    ownMixin,
                    x + 12,
                    lineY,
                    width - 21,
                    0xFF9B8BA3);
        }
    }

    private int calculateContentHeight() {
        int width = Math.max(20, this.bodyWidth - 12);
        if (this.scanning || this.scanFailed || this.report == null) {
            return this.font == null ? 30 : this.font.lineHeight * 4;
        }
        int height = 5;
        height += wrappedHeight(summaryText(), width) + 4;
        height += wrappedHeight(qualificationText(), width) + 7;
        if (this.report.conflicts().isEmpty()) {
            height += wrappedHeight(emptyText(), width);
        } else {
            for (ConflictEntry entry : this.report.conflicts()) {
                height += cardHeight(entry) + CARD_GAP;
            }
        }
        if (this.report.failedMods() > 0) {
            height += this.font.lineHeight * 2 + 3;
        }
        return height + 5;
    }

    private int cardHeight(ConflictEntry entry) {
        int width = Math.max(20, this.bodyWidth - 20);
        int height = 10 + Math.max(34, this.font.lineHeight * 2);
        for (String reason : reasons(entry)) {
            height += wrappedHeight("• " + reason, width);
        }
        height += 2;
        for (ConflictPoint point : entry.points()) {
            height += wrappedHeight(point.targetClass() + "#" + point.targetMethod(), width - 2);
            height += wrappedHeight(
                    (this.spanish ? "Mixin externo: " : "Foreign mixin: ") + point.foreignMixinClass(),
                    width - 5);
            height += wrappedHeight("Crystal Tweaks: " + point.ownMixinClass(), width - 5);
        }
        return height + 7;
    }

    private List<String> reasons(ConflictEntry entry) {
        Set<ConflictArea> areas = new LinkedHashSet<>();
        for (ConflictPoint point : entry.points()) {
            areas.add(point.area());
        }
        List<String> reasons = new ArrayList<>();
        for (ConflictArea area : areas) {
            reasons.add(reason(area));
        }
        return reasons;
    }

    private String reason(ConflictArea area) {
        if (this.spanish) {
            return switch (area) {
                case NETWORK_OBSERVER -> "Se relaciona con la ruta de envío que observa el core; el orden o la cancelación del mixin podría cambiar el momento observado.";
                case CRYSTAL_RENDERING -> "Se relaciona con el modelo o render del cristal; el color o la animación podrían aplicarse dos veces o ser reemplazados.";
                case SOUND -> "Se relaciona con la reproducción o carga de sonido; el audio personalizado podría ser sustituido o procesado dos veces.";
                case GENERAL -> "Ambos mixins modifican el mismo método; el orden, la prioridad o una cancelación podrían cambiar el resultado.";
            };
        }
        return switch (area) {
            case NETWORK_OBSERVER -> "It relates to the send path observed by the core; Mixin order or cancellation could change the observed timing.";
            case CRYSTAL_RENDERING -> "It relates to crystal rendering or its model; color or animation could be applied twice or replaced.";
            case SOUND -> "It relates to sound playback or loading; custom audio could be replaced or processed twice.";
            case GENERAL -> "Both mixins modify the same method; order, priority, or cancellation could change the result.";
        };
    }

    private Identifier iconFor(ConflictEntry entry) {
        if (entry.iconPath().isBlank() || this.unavailableIcons.contains(entry.modId())) {
            return null;
        }
        Identifier cached = this.icons.get(entry.modId());
        if (cached != null) {
            return cached;
        }
        Optional<ModContainer> container = FabricLoader.getInstance().getModContainer(entry.modId());
        Optional<Path> iconPath = container.flatMap(value -> value.findPath(entry.iconPath()));
        if (iconPath.isEmpty()) {
            this.unavailableIcons.add(entry.modId());
            return null;
        }
        NativeImage image = null;
        try (InputStream stream = Files.newInputStream(iconPath.get())) {
            image = NativeImage.read(stream);
            this.iconWidths.put(entry.modId(), image.getWidth());
            this.iconHeights.put(entry.modId(), image.getHeight());
            Identifier identifier = Identifier.fromNamespaceAndPath(
                    CrystalTweaksClient.MOD_ID,
                    "conflict_monitor/" + sanitize(entry.modId()));
            this.minecraft.getTextureManager().register(
                    identifier,
                    new DynamicTexture(() -> "Crystal Tweaks conflict icon " + entry.modId(), image));
            this.icons.put(entry.modId(), identifier);
            return identifier;
        } catch (Exception exception) {
            if (image != null) {
                image.close();
            }
            this.unavailableIcons.add(entry.modId());
            CrystalTweaksClient.LOGGER.debug(
                    "Conflict Monitor could not load the icon for {}",
                    entry.modId(),
                    exception);
            return null;
        }
    }

    private int drawWrapped(
            GuiGraphicsExtractor graphics,
            String text,
            int x,
            int y,
            int width,
            int color
    ) {
        List<FormattedCharSequence> lines = this.font.split(Component.literal(text), Math.max(1, width));
        int currentY = y;
        for (FormattedCharSequence line : lines) {
            graphics.text(this.font, line, x, currentY, color, false);
            currentY += this.font.lineHeight;
        }
        return currentY - y;
    }

    private int wrappedHeight(String text, int width) {
        return Math.max(1, this.font.split(Component.literal(text), Math.max(1, width)).size())
                * this.font.lineHeight;
    }

    private int maxScroll() {
        return Math.max(0, this.contentHeight - this.bodyHeight);
    }

    private void drawScrollbar(GuiGraphicsExtractor graphics) {
        int maximum = maxScroll();
        if (maximum <= 0 || this.contentHeight <= 0) {
            return;
        }
        int x = this.bodyX + this.bodyWidth - 3;
        int thumbHeight = Math.max(12, this.bodyHeight * this.bodyHeight / this.contentHeight);
        int travel = Math.max(1, this.bodyHeight - thumbHeight);
        int y = this.bodyY + Math.round(travel * this.scroll / (float) maximum);
        graphics.fill(x, this.bodyY, x + 2, this.bodyY + this.bodyHeight, 0x66331840);
        graphics.fill(x, y, x + 2, y + thumbHeight, 0xE4C46FEF);
    }

    private void drawParticles(GuiGraphicsExtractor graphics, long now) {
        double seconds = now / 1_000.0D;
        int count = Mth.clamp(this.width * this.height / 3_600, 48, 110);
        int verticalSpan = Math.max(1, this.height + 18);
        for (int index = 0; index < count; index++) {
            double phase = index * 1.32471795724D;
            int x = Math.floorMod(index * 79 + 31, Math.max(1, this.width));
            x += (int) Math.round(Math.sin(seconds * 0.55D + phase) * (3 + index % 5));
            int y = this.height + 7
                    - (int) ((seconds * (4.0D + index % 7) + phase * verticalSpan) % verticalSpan);
            int size = index % 8 == 0 ? 3 : index % 3 == 0 ? 2 : 1;
            int alpha = 72 + index % 6 * 13;
            graphics.fill(x - 1, y - 1, x + size + 1, y + size + 1,
                    alpha / 4 << 24 | 0x8E2ADE);
            graphics.fill(x, y, x + size, y + size,
                    alpha << 24 | 207 << 16 | (74 + index % 4 * 17) << 8 | 255);
        }
    }

    @Override
    public boolean mouseScrolled(
            double mouseX,
            double mouseY,
            double horizontalAmount,
            double verticalAmount
    ) {
        if (maxScroll() > 0
                && mouseX >= this.bodyX
                && mouseX <= this.bodyX + this.bodyWidth
                && mouseY >= this.bodyY
                && mouseY <= this.bodyY + this.bodyHeight) {
            int direction = verticalAmount > 0.0D ? -1 : verticalAmount < 0.0D ? 1 : 0;
            if (direction != 0) {
                this.scroll = Mth.clamp(
                        this.scroll + direction * Math.max(12, this.font.lineHeight * 3),
                        0,
                        maxScroll());
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }

    @Override
    public void removed() {
        for (Identifier identifier : this.icons.values()) {
            this.minecraft.getTextureManager().release(identifier);
        }
        this.icons.clear();
        this.iconWidths.clear();
        this.iconHeights.clear();
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    private String summaryText() {
        return this.spanish
                ? "Mods analizados: " + this.report.scannedMods()
                        + "  •  Mixins: " + this.report.scannedMixins()
                        + "  •  Coincidencias: " + this.report.conflicts().size()
                : "Mods scanned: " + this.report.scannedMods()
                        + "  •  Mixins: " + this.report.scannedMixins()
                        + "  •  Matches: " + this.report.conflicts().size();
    }

    private String qualificationText() {
        return this.spanish
                ? "Solo se muestran coincidencias de la misma clase y método. Los plugins dinámicos o transformadores externos pueden no ser visibles."
                : "Only identical class-and-method targets are shown. Dynamic plugins or external transformers may not be visible.";
    }

    private String emptyText() {
        return this.spanish
                ? "No se detectaron coincidencias exactas. Esto no demuestra compatibilidad universal, pero el análisis no encontró interferencias estáticas directas."
                : "No exact overlaps were detected. This does not prove universal compatibility, but no direct static interference was found.";
    }

    private static String sanitize(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9/._-]", "_");
    }

    private static boolean usesSpanish() {
        try {
            return Minecraft.getInstance().getLanguageManager().getSelected().toLowerCase(Locale.ROOT)
                    .startsWith("es");
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private static void fillRounded(
            GuiGraphicsExtractor graphics,
            int x,
            int y,
            int width,
            int height,
            int topColor,
            int bottomColor
    ) {
        if (width < 5 || height < 5) {
            graphics.fillGradient(x, y, x + width, y + height, topColor, bottomColor);
            return;
        }
        graphics.fillGradient(x + 2, y, x + width - 2, y + height, topColor, bottomColor);
        graphics.fillGradient(x, y + 2, x + width, y + height - 2, topColor, bottomColor);
    }

    private static void drawRoundedOutline(
            GuiGraphicsExtractor graphics,
            int x,
            int y,
            int width,
            int height,
            int color
    ) {
        if (width < 5 || height < 5) {
            drawOutline(graphics, x, y, width, height, color);
            return;
        }
        graphics.fill(x + 2, y, x + width - 2, y + 1, color);
        graphics.fill(x + 2, y + height - 1, x + width - 2, y + height, color);
        graphics.fill(x, y + 2, x + 1, y + height - 2, color);
        graphics.fill(x + width - 1, y + 2, x + width, y + height - 2, color);
        graphics.fill(x + 1, y + 1, x + 2, y + 2, color);
        graphics.fill(x + width - 2, y + 1, x + width - 1, y + 2, color);
        graphics.fill(x + 1, y + height - 2, x + 2, y + height - 1, color);
        graphics.fill(x + width - 2, y + height - 2, x + width - 1, y + height - 1, color);
    }

    private static void drawOutline(
            GuiGraphicsExtractor graphics,
            int x,
            int y,
            int width,
            int height,
            int color
    ) {
        graphics.fill(x, y, x + width, y + 1, color);
        graphics.fill(x, y + height - 1, x + width, y + height, color);
        graphics.fill(x, y + 1, x + 1, y + height - 1, color);
        graphics.fill(x + width - 1, y + 1, x + width, y + height - 1, color);
    }
}
