package com.zymekoh.crystaltweaks.client;

import com.zymekoh.crystaltweaks.client.sound.CrystalSoundManager;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.DoubleFunction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.entity.state.EndCrystalRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

public final class CrystalTweaksScreen extends Screen {
    private static final long MENU_INTRO_MILLIS = 360L;
    private static final long PREVIEW_APPEAR_MILLIS = 460L;
    private static final long PREVIEW_EXPLOSION_MILLIS = 280L;
    private static final long PREVIEW_RESPAWN_MILLIS = 3_000L;

    private final Screen parent;
    private final boolean spanish;
    private final EndCrystalRenderState previewState = new EndCrystalRenderState();
    private final List<AbstractWidget> contentWidgets = new ArrayList<>();
    private final Map<AbstractWidget, Integer> contentBaseY = new IdentityHashMap<>();

    private Tab activeTab = Tab.VISUALS;
    private Layer selectedLayer = Layer.OUTER;
    private PurpleCloseButton visualsTab;
    private PurpleCloseButton soundsTab;
    private PurpleCloseButton tweaksTab;
    private PurpleCloseButton resetButton;
    private PurpleCloseButton outerButton;
    private PurpleCloseButton innerButton;
    private PurpleCloseButton coreButton;
    private PurpleCloseButton soundToggle;
    private PurpleCloseButton soundFileButton;
    private PurpleCloseButton conflictMonitorButton;
    private EditBox hexBox;
    private ColorPickerWidget colorPicker;
    private boolean updatingControls;
    private boolean saved;
    private String soundStatus = "";
    private int soundStatusBaseY;
    private int logicalContentBottom;
    private int maxScroll;
    private int visualsScroll;
    private int soundsScroll;
    private int tweaksScroll;

    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;
    private int headerHeight;
    private int footerHeight;
    private int contentX;
    private int contentY;
    private int contentWidth;
    private int contentHeight;
    private int optionsX;
    private int optionsWidth;
    private int previewX;
    private int previewWidth;
    private int previewHeight;
    private int previewNoteHeight;
    private int controlHeight;
    private int rowGap;
    private long openedAt;
    private long previewPhaseStartedAt;
    private long previewRespawnAt;
    private PreviewPhase previewPhase = PreviewPhase.APPEARING;

    public CrystalTweaksScreen(Screen parent) {
        super(Component.literal("Crystal Tweaks"));
        this.parent = parent;
        this.spanish = usesSpanish();
        this.previewState.entityType = EntityType.END_CRYSTAL;
        this.previewState.showsBottom = false;
        this.previewState.boundingBoxWidth = 2.0F;
        this.previewState.boundingBoxHeight = 2.0F;
        this.previewState.eyeHeight = 1.0F;
        this.previewState.outlineColor = 0;
    }

    @Override
    protected void init() {
        CrystalVisualConfig.load();
        long now = System.currentTimeMillis();
        if (this.openedAt == 0L) {
            this.openedAt = now;
            this.previewPhaseStartedAt = now;
        }
        this.saved = false;
        this.contentWidgets.clear();
        this.contentBaseY.clear();
        calculateLayout();
        addTabs();
        addFooter();
        addActiveTabContent();
    }

    private void calculateLayout() {
        int horizontalMargin = Mth.clamp(this.width / 28, 4, 18);
        int verticalMargin = Mth.clamp(this.height / 28, 4, 14);
        int availableWidth = Math.max(1, this.width - horizontalMargin * 2);
        int availableHeight = Math.max(1, this.height - verticalMargin * 2);

        this.panelWidth = Math.min(470, availableWidth);
        this.panelHeight = Math.min(255, availableHeight);
        this.panelX = (this.width - this.panelWidth) / 2;
        this.panelY = (this.height - this.panelHeight) / 2;
        int desiredHeader = this.panelHeight < 190 ? 42 : 50;
        int desiredFooter = this.panelHeight < 190 ? 25 : 31;
        this.headerHeight = Math.min(desiredHeader, Math.max(1, this.panelHeight / 2));
        this.footerHeight = Math.min(
                desiredFooter,
                Math.max(1, (this.panelHeight - this.headerHeight) / 3));

        int padding = Mth.clamp(this.panelWidth / 48, 5, 11);
        this.contentX = this.panelX + padding;
        this.contentY = this.panelY + this.headerHeight;
        this.contentWidth = Math.max(1, this.panelWidth - padding * 2);
        this.contentHeight = Math.max(
                1,
                this.panelY + this.panelHeight - this.footerHeight - this.contentY);
        int desiredGap = this.panelHeight < 190 ? 3 : 5;
        this.rowGap = Math.min(desiredGap, Math.max(0, (this.contentHeight - 4) / 8));
        int desiredControlHeight = this.panelHeight < 190 ? 14 : 18;
        int fourRowHeight = Math.max(1, (this.contentHeight - this.rowGap * 3) / 4);
        this.controlHeight = Math.min(desiredControlHeight, fourRowHeight);

        int previewGap = this.contentWidth < 390 ? 6 : 10;
        boolean previewFits = this.contentWidth >= 300 && this.contentHeight >= 86;
        this.previewWidth = previewFits
                ? Mth.clamp(Math.round(this.contentWidth * 0.24F), 82, 118)
                : 0;
        this.optionsX = this.contentX;
        this.optionsWidth = this.previewWidth > 0
                ? Math.max(1, this.contentWidth - this.previewWidth - previewGap)
                : this.contentWidth;
        this.previewX = this.previewWidth > 0
                ? this.contentX + this.contentWidth - this.previewWidth
                : 0;
        this.previewNoteHeight = this.previewWidth > 0 && this.contentHeight >= 125 ? 31 : 0;
        this.previewHeight = this.previewWidth > 0
                ? Math.max(1, this.contentHeight - this.previewNoteHeight)
                : 0;
    }

    private void addTabs() {
        int gap = this.panelWidth < 330 ? 3 : 6;
        int available = Math.max(2, this.panelWidth - 18);
        int tabWidth = Math.min(112, Math.max(1, (available - gap * 2) / 3));
        int groupWidth = tabWidth * 3 + gap * 2;
        int x = this.panelX + (this.panelWidth - groupWidth) / 2;
        int y = this.panelY + this.headerHeight - this.controlHeight - 4;

        this.visualsTab = addRenderableWidget(new PurpleCloseButton(
                x,
                y,
                tabWidth,
                this.controlHeight,
                Component.literal(this.spanish ? "Visuales" : "Visuals"),
                ignored -> switchTab(Tab.VISUALS)));
        this.soundsTab = addRenderableWidget(new PurpleCloseButton(
                x + tabWidth + gap,
                y,
                tabWidth,
                this.controlHeight,
                Component.literal(this.spanish ? "Sonidos" : "Sounds"),
                ignored -> switchTab(Tab.SOUNDS)));
        this.tweaksTab = addRenderableWidget(new PurpleCloseButton(
                x + (tabWidth + gap) * 2,
                y,
                tabWidth,
                this.controlHeight,
                Component.literal(this.spanish ? "Ajustes avanzados" : "Advanced Tweaks"),
                ignored -> switchTab(Tab.TWEAKS)));
        updateTabState();
    }

    private void addFooter() {
        int footerTop = this.panelY + this.panelHeight - this.footerHeight;
        int height = Math.max(1, Math.min(18, this.footerHeight - 4));
        int gap = 6;
        int width = Math.min(104, Math.max(1, (this.panelWidth - 18 - gap) / 2));
        int groupWidth = width * 2 + gap;
        int x = this.panelX + (this.panelWidth - groupWidth) / 2;
        int y = footerTop + (this.footerHeight - height) / 2;

        this.resetButton = addRenderableWidget(new PurpleCloseButton(
                x,
                y,
                width,
                height,
                Component.literal(this.spanish ? "Restablecer" : "Reset"),
                ignored -> resetActiveTab()));
        addRenderableWidget(new PurpleCloseButton(
                x + width + gap,
                y,
                width,
                height,
                Component.literal(this.spanish ? "Listo" : "Done"),
                ignored -> onClose()));
    }

    private void addActiveTabContent() {
        this.outerButton = null;
        this.innerButton = null;
        this.coreButton = null;
        this.soundToggle = null;
        this.soundFileButton = null;
        this.conflictMonitorButton = null;
        this.hexBox = null;
        this.colorPicker = null;
        this.logicalContentBottom = 0;
        switch (this.activeTab) {
            case VISUALS -> addVisualControls();
            case SOUNDS -> addSoundControls();
            case TWEAKS -> addTweaksControls();
        }
        finishContentLayout();
    }

    private void addVisualControls() {
        int gap = this.optionsWidth < 250 ? 2 : 5;
        int layerWidth = Math.max(1, (this.optionsWidth - gap * 2) / 3);
        this.outerButton = addContent(new PurpleCloseButton(
                this.optionsX,
                this.contentY,
                layerWidth,
                this.controlHeight,
                Component.empty(),
                ignored -> selectLayer(Layer.OUTER)));
        this.innerButton = addContent(new PurpleCloseButton(
                this.optionsX + layerWidth + gap,
                this.contentY,
                layerWidth,
                this.controlHeight,
                Component.empty(),
                ignored -> selectLayer(Layer.INNER)));
        this.coreButton = addContent(new PurpleCloseButton(
                this.optionsX + (layerWidth + gap) * 2,
                this.contentY,
                layerWidth,
                this.controlHeight,
                Component.empty(),
                ignored -> selectLayer(Layer.CORE)));

        int hexY = this.contentY + rowStep();
        int hexWidth = Math.min(94, this.optionsWidth);
        this.hexBox = addContent(new EditBox(
                this.font,
                this.optionsX,
                hexY,
                hexWidth,
                this.controlHeight,
                Component.literal(this.spanish ? "Color hexadecimal" : "Hex color")));
        this.hexBox.setMaxLength(7);
        this.hexBox.setResponder(this::onHexChanged);

        int pickerY = hexY + this.controlHeight + this.rowGap;
        int pickerHeight = Math.min(48, Math.max(12, this.contentHeight / 4));
        this.colorPicker = addContent(new ColorPickerWidget(
                this.optionsX,
                pickerY,
                this.optionsWidth,
                pickerHeight,
                Component.literal(this.spanish ? "Selector de color" : "Color picker"),
                selectedColor(),
                this::onPickerChanged));

        int rotationY = pickerY + pickerHeight + this.rowGap;
        addContent(new CompactSlider(
                this.optionsX,
                rotationY,
                this.optionsWidth,
                this.controlHeight,
                0.0D,
                300.0D,
                CrystalVisualConfig.rotationSpeedPercent(),
                value -> CrystalVisualConfig.setRotationSpeedPercent((int) Math.round(value)),
                value -> animationSpeedLabel(
                        this.spanish ? "Giro" : "Rotation",
                        (int) Math.round(value))));
        addContent(new CompactSlider(
                this.optionsX,
                rotationY + rowStep(),
                this.optionsWidth,
                this.controlHeight,
                0.0D,
                300.0D,
                CrystalVisualConfig.floatingSpeedPercent(),
                value -> CrystalVisualConfig.setFloatingSpeedPercent((int) Math.round(value)),
                value -> animationSpeedLabel(
                        this.spanish ? "Flotación" : "Floating",
                        (int) Math.round(value))));
        selectLayer(this.selectedLayer);
    }

    private void addSoundControls() {
        int width = this.optionsWidth;
        int y = this.contentY;
        this.soundToggle = addContent(new PurpleCloseButton(
                this.optionsX,
                y,
                width,
                this.controlHeight,
                soundToggleMessage(),
                ignored -> toggleSound()));

        this.soundFileButton = addContent(new PurpleCloseButton(
                this.optionsX,
                y + rowStep(),
                width,
                this.controlHeight,
                Component.literal(this.spanish ? "Seleccionar archivo…" : "Select file…"),
                ignored -> openSoundPicker()));
        this.soundFileButton.active = CrystalVisualConfig.customSoundEnabled();

        addContent(new CompactSlider(
                this.optionsX,
                y + rowStep() * 2,
                width,
                this.controlHeight,
                0.0D,
                2.0D,
                CrystalVisualConfig.soundVolume(),
                value -> CrystalVisualConfig.setSoundVolume(value.floatValue()),
                value -> (this.spanish ? "Volumen: " : "Volume: ")
                        + Math.round(value * 100.0D) + "%"));
        addContent(new CompactSlider(
                this.optionsX,
                y + rowStep() * 3,
                width,
                this.controlHeight,
                0.5D,
                2.0D,
                CrystalVisualConfig.soundSpeed(),
                value -> CrystalVisualConfig.setSoundSpeed(value.floatValue()),
                value -> String.format(
                        Locale.ROOT,
                        this.spanish ? "Velocidad: %.2fx" : "Speed: %.2fx",
                        value)));
        this.soundStatusBaseY = y + rowStep() * 4 + 2;
        this.logicalContentBottom = Math.max(
                this.logicalContentBottom,
                this.soundStatusBaseY - this.contentY + this.font.lineHeight);
    }

    private void addTweaksControls() {
        this.conflictMonitorButton = addContent(new PurpleCloseButton(
                this.optionsX,
                this.contentY,
                this.optionsWidth,
                this.controlHeight,
                Component.literal(this.spanish ? "Monitor de conflictos" : "Conflict Monitor"),
                ignored -> openConflictMonitor()));
        this.conflictMonitorButton.setTooltip(Tooltip.create(Component.literal(this.spanish
                ? "Analiza localmente los mixins instalados y muestra coincidencias de clase y método que podrían interferir con Crystal Tweaks."
                : "Locally scans installed mixins and shows matching classes and methods that could interfere with Crystal Tweaks.")));
    }

    private <T extends AbstractWidget> T addContent(T widget) {
        this.contentWidgets.add(widget);
        this.contentBaseY.put(widget, widget.getY());
        this.logicalContentBottom = Math.max(
                this.logicalContentBottom,
                widget.getY() + widget.getHeight() - this.contentY);
        return addRenderableWidget(widget);
    }

    private void finishContentLayout() {
        this.maxScroll = Math.max(0, this.logicalContentBottom - this.contentHeight);
        setCurrentScroll(Mth.clamp(currentScroll(), 0, this.maxScroll));
        applyContentScroll();
    }

    private void switchTab(Tab tab) {
        if (this.activeTab == tab) {
            return;
        }
        this.activeTab = tab;
        for (AbstractWidget widget : this.contentWidgets) {
            removeWidget(widget);
        }
        this.contentWidgets.clear();
        this.contentBaseY.clear();
        updateTabState();
        addActiveTabContent();
    }

    private void updateTabState() {
        this.visualsTab.active = this.activeTab != Tab.VISUALS;
        this.soundsTab.active = this.activeTab != Tab.SOUNDS;
        this.tweaksTab.active = this.activeTab != Tab.TWEAKS;
        if (this.resetButton != null) {
            this.resetButton.active = this.activeTab != Tab.TWEAKS;
        }
    }

    private int currentScroll() {
        return switch (this.activeTab) {
            case VISUALS -> this.visualsScroll;
            case SOUNDS -> this.soundsScroll;
            case TWEAKS -> this.tweaksScroll;
        };
    }

    private void setCurrentScroll(int value) {
        switch (this.activeTab) {
            case VISUALS -> this.visualsScroll = value;
            case SOUNDS -> this.soundsScroll = value;
            case TWEAKS -> this.tweaksScroll = value;
        }
    }

    private void applyContentScroll() {
        int scroll = currentScroll();
        int viewportBottom = this.contentY + this.contentHeight;
        for (AbstractWidget widget : this.contentWidgets) {
            Integer baseY = this.contentBaseY.get(widget);
            if (baseY == null) {
                continue;
            }
            int y = baseY - scroll;
            widget.setY(y);
            widget.visible = y >= this.contentY && y + widget.getHeight() <= viewportBottom;
        }
    }

    private void selectLayer(Layer layer) {
        this.selectedLayer = layer;
        int color = selectedColor();
        this.updatingControls = true;
        this.colorPicker.setColor(color);
        this.hexBox.setValue(CrystalVisualConfig.toHex(color));
        this.hexBox.setTextColor(0xFFF0E5F6);
        this.updatingControls = false;
        updateLayerMessages();
    }

    private void onPickerChanged(int color) {
        if (this.updatingControls) {
            return;
        }
        setSelectedColor(color);
        this.updatingControls = true;
        this.hexBox.setValue(CrystalVisualConfig.toHex(color));
        this.hexBox.setTextColor(0xFFF0E5F6);
        this.updatingControls = false;
        updateLayerMessages();
    }

    private void onHexChanged(String value) {
        if (this.updatingControls) {
            return;
        }
        try {
            int color = CrystalVisualConfig.parseHex(value);
            setSelectedColor(color);
            this.colorPicker.setColor(color);
            this.hexBox.setTextColor(0xFFF0E5F6);
            updateLayerMessages();
        } catch (IllegalArgumentException exception) {
            this.hexBox.setTextColor(0xFFFF7D9C);
        }
    }

    private void updateLayerMessages() {
        this.outerButton.setMessage(layerMessage(Layer.OUTER, this.spanish ? "Exterior" : "Outer"));
        this.innerButton.setMessage(layerMessage(Layer.INNER, this.spanish ? "Interior" : "Inner"));
        this.coreButton.setMessage(layerMessage(Layer.CORE, this.spanish ? "Núcleo" : "Core"));
    }

    private Component layerMessage(Layer layer, String name) {
        return Component.literal((this.selectedLayer == layer ? "◆ " : "◇ ") + name);
    }

    private void setSelectedColor(int color) {
        switch (this.selectedLayer) {
            case OUTER -> CrystalVisualConfig.setOuterColor(color);
            case INNER -> CrystalVisualConfig.setInnerColor(color);
            case CORE -> CrystalVisualConfig.setCoreColor(color);
        }
    }

    private int selectedColor() {
        return switch (this.selectedLayer) {
            case OUTER -> CrystalVisualConfig.outerColor();
            case INNER -> CrystalVisualConfig.innerColor();
            case CORE -> CrystalVisualConfig.coreColor();
        };
    }

    private String animationSpeedLabel(String label, int percent) {
        if (percent == 0) {
            return label + ": " + (this.spanish ? "Estático" : "Static");
        }
        if (percent == 100) {
            return label + ": 100% (Vanilla)";
        }
        return label + ": " + percent + "%";
    }

    private void toggleSound() {
        CrystalVisualConfig.setCustomSoundEnabled(!CrystalVisualConfig.customSoundEnabled());
        CrystalVisualConfig.save();
        CrystalSoundManager.reloadFromConfig();
        this.soundToggle.setMessage(soundToggleMessage());
        this.soundFileButton.active = CrystalVisualConfig.customSoundEnabled();
    }

    private Component soundToggleMessage() {
        String label = this.spanish ? "Sonido personalizado" : "Custom sound";
        String state = CrystalVisualConfig.customSoundEnabled()
                ? (this.spanish ? "ACTIVO" : "ON")
                : (this.spanish ? "INACTIVO" : "OFF");
        return Component.literal(label + ": " + state);
    }

    private void openConflictMonitor() {
        this.minecraft.setScreen(new ConflictMonitorScreen(this));
    }

    private void openSoundPicker() {
        Thread picker = new Thread(() -> {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                PointerBuffer patterns = stack.mallocPointer(3);
                patterns.put(stack.UTF8("*.wav"));
                patterns.put(stack.UTF8("*.ogg"));
                patterns.put(stack.UTF8("*.mp3"));
                patterns.flip();
                String selected = TinyFileDialogs.tinyfd_openFileDialog(
                        this.spanish ? "Seleccionar sonido" : "Select sound",
                        null,
                        patterns,
                        "Audio (*.wav, *.ogg, *.mp3)",
                        false);
                if (selected != null) {
                    Minecraft.getInstance().execute(() -> importSound(Path.of(selected)));
                }
            } catch (RuntimeException exception) {
                Minecraft.getInstance().execute(() -> this.soundStatus = this.spanish
                        ? "No se pudo abrir el selector"
                        : "Could not open file picker");
            }
        }, "Crystal Tweaks sound picker");
        picker.setDaemon(true);
        picker.start();
    }

    private void importSound(Path file) {
        String error = CrystalSoundManager.importFile(file);
        if (error.isEmpty()) {
            this.soundStatus = this.spanish ? "Sonido cargado" : "Sound loaded";
        } else {
            this.soundStatus = localizeSoundError(error);
        }
    }

    private String localizeSoundError(String error) {
        if (!this.spanish) {
            return error;
        }
        if (error.startsWith("Unsupported format")) {
            return "Formato no compatible. Usa WAV, OGG o MP3.";
        }
        if (error.startsWith("Too long")) {
            return "El audio supera el máximo de 5 segundos.";
        }
        if (error.contains("File not found")) {
            return "Archivo no encontrado.";
        }
        return "No se pudo cargar el audio.";
    }

    private void resetActiveTab() {
        switch (this.activeTab) {
            case VISUALS -> {
                CrystalVisualConfig.resetColors();
                CrystalVisualConfig.setRotationSpeedPercent(100);
                CrystalVisualConfig.setFloatingSpeedPercent(100);
                rebuildActiveContent();
            }
            case SOUNDS -> {
                CrystalVisualConfig.setCustomSoundEnabled(false);
                CrystalVisualConfig.setCustomSoundFileName("");
                CrystalVisualConfig.setSoundVolume(1.0F);
                CrystalVisualConfig.setSoundSpeed(1.0F);
                this.soundStatus = "";
                CrystalVisualConfig.save();
                CrystalSoundManager.reloadFromConfig();
                rebuildActiveContent();
            }
            case TWEAKS -> {
            }
        }
    }

    private void rebuildActiveContent() {
        for (AbstractWidget widget : this.contentWidgets) {
            removeWidget(widget);
        }
        this.contentWidgets.clear();
        this.contentBaseY.clear();
        addActiveTabContent();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0x2604010A);
        this.minecraft.gui.extractDeferredSubtitles();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        long now = System.currentTimeMillis();
        float intro = easeOutCubic(progress(now - this.openedAt, MENU_INTRO_MILLIS));
        drawFloatingParticles(graphics, now);
        drawPanel(graphics, intro);
        if (intro < 0.72F) {
            return;
        }
        drawPanelParticles(graphics, now);
        drawHeader(graphics);
        if (this.activeTab == Tab.VISUALS) {
            drawVisualSelection(graphics);
        } else if (this.activeTab == Tab.SOUNDS) {
            drawSoundStatus(graphics);
        }
        drawPreview(graphics);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.nextStratum();
        drawScrollbar(graphics);
    }

    private void drawPanel(GuiGraphicsExtractor graphics, float intro) {
        int animatedWidth = Math.max(1, Math.round(this.panelWidth * (0.88F + intro * 0.12F)));
        int animatedHeight = Math.max(1, Math.round(this.panelHeight * (0.86F + intro * 0.14F)));
        int animatedX = this.panelX + (this.panelWidth - animatedWidth) / 2;
        int animatedY = this.panelY + (this.panelHeight - animatedHeight) / 2;
        fillRounded(graphics, animatedX, animatedY, animatedWidth, animatedHeight,
                0xB71B0928, 0xA60A0310);
        drawRoundedOutline(graphics,
                animatedX,
                animatedY,
                animatedWidth,
                animatedHeight,
                0xC6B85BE8);
        if (intro >= 0.72F) {
            graphics.fillGradient(
                    this.contentX,
                    this.contentY,
                    this.contentX + this.contentWidth,
                    this.contentY + this.contentHeight,
                    0x5014081D,
                    0x3907030D);
        }
    }

    private void drawHeader(GuiGraphicsExtractor graphics) {
        int glow = 214 + Math.round(35.0F * (0.5F
                + 0.5F * (float) Math.sin(System.currentTimeMillis() / 280.0F)));
        graphics.centeredText(
                this.font,
                Component.literal("Crystal Tweaks"),
                this.panelX + this.panelWidth / 2,
                this.panelY + 6,
                0xFF000000 | glow << 16 | 225 << 8 | 255);
    }

    private void drawFloatingParticles(GuiGraphicsExtractor graphics, long now) {
        double seconds = now / 1_000.0D;
        int span = Math.max(1, this.height + 24);
        int count = Mth.clamp(this.width * this.height / 4_000, 56, 110);
        for (int index = 0; index < count; index++) {
            double phase = index * 0.61803398875D;
            double speed = 4.5D + index % 7;
            int baseX = Math.floorMod(index * 83 + 29, Math.max(1, this.width));
            int x = baseX + (int) Math.round(Math.sin(seconds * 0.62D + phase) * (4 + index % 6));
            int y = this.height + 8 - (int) ((seconds * speed + phase * span) % span);
            int size = index % 6 == 0 ? 3 : index % 3 == 0 ? 2 : 1;
            int alpha = 84 + index % 6 * 12;
            int green = 82 + index % 4 * 18;
            int glow = alpha / 4 << 24 | 164 << 16 | 58 << 8 | 231;
            int color = alpha << 24 | 210 << 16 | green << 8 | 255;
            graphics.fill(x - 1, y - 1, x + size + 1, y + size + 1, glow);
            graphics.fill(x, y, x + size, y + size, color);
        }
    }

    private void drawPanelParticles(GuiGraphicsExtractor graphics, long now) {
        double seconds = now / 1_000.0D;
        int count = Mth.clamp(this.panelWidth * this.panelHeight / 2_800, 28, 54);
        int horizontalSpan = Math.max(1, this.panelWidth - 8);
        int verticalSpan = Math.max(1, this.panelHeight - 8);
        for (int index = 0; index < count; index++) {
            double phase = index * 1.32471795724D;
            int baseX = this.panelX + 4 + Math.floorMod(index * 67 + 19, horizontalSpan);
            int x = baseX + (int) Math.round(Math.sin(seconds * 0.48D + phase) * (2 + index % 4));
            int y = this.panelY + this.panelHeight - 5
                    - (int) ((seconds * (2.0D + index % 5) + phase * verticalSpan) % verticalSpan);
            int size = index % 8 == 0 ? 3 : index % 3 == 0 ? 2 : 1;
            int minimumX = this.panelX + 2;
            int minimumY = this.panelY + 2;
            int maximumX = Math.max(minimumX, this.panelX + this.panelWidth - size - 2);
            int maximumY = Math.max(minimumY, this.panelY + this.panelHeight - size - 2);
            x = Mth.clamp(x, minimumX, maximumX);
            y = Mth.clamp(y, minimumY, maximumY);
            int alpha = 52 + index % 6 * 9;
            int glow = alpha / 3 << 24 | 140 << 16 | 45 << 8 | 220;
            int color = alpha << 24 | 224 << 16 | (92 + index % 4 * 18) << 8 | 255;
            graphics.fill(x - 1, y - 1, x + size + 1, y + size + 1, glow);
            graphics.fill(x, y, x + size, y + size, color);
        }
    }

    private void drawVisualSelection(GuiGraphicsExtractor graphics) {
        if (this.outerButton == null) {
            return;
        }
        PurpleCloseButton selected = switch (this.selectedLayer) {
            case OUTER -> this.outerButton;
            case INNER -> this.innerButton;
            case CORE -> this.coreButton;
        };
        if (selected.visible) {
            drawOutline(graphics,
                    selected.getX() - 1,
                    selected.getY() - 1,
                    selected.getWidth() + 2,
                    selected.getHeight() + 2,
                    0xFFE2A2FF);
        }

        if (!this.hexBox.visible) {
            return;
        }
        int swatchSize = Math.min(11, Math.max(6, this.hexBox.getHeight() - 5));
        int swatchX = Math.min(this.optionsX + this.optionsWidth - swatchSize, this.hexBox.getRight() + 5);
        int swatchY = this.hexBox.getY() + (this.hexBox.getHeight() - swatchSize) / 2;
        graphics.fill(swatchX, swatchY, swatchX + swatchSize, swatchY + swatchSize, selectedColor());
        drawOutline(graphics, swatchX, swatchY, swatchSize, swatchSize, 0xFFE8D8F1);
    }

    private void drawSoundStatus(GuiGraphicsExtractor graphics) {
        int statusY = this.soundStatusBaseY - currentScroll();
        if (statusY < this.contentY
                || statusY + this.font.lineHeight > this.contentY + this.contentHeight) {
            return;
        }
        String fileName = CrystalVisualConfig.customSoundFileName();
        String status = this.soundStatus.isBlank()
                ? (fileName.isBlank()
                        ? (this.spanish ? "Sin archivo" : "No file selected")
                        : fileName)
                : this.soundStatus;
        String clipped = this.font.plainSubstrByWidth(status, Math.max(1, this.optionsWidth));
        graphics.text(this.font, clipped, this.optionsX, statusY, 0xFFD7C2DF, false);
    }

    private void drawPreview(GuiGraphicsExtractor graphics) {
        if (this.previewWidth <= 0) {
            return;
        }
        graphics.fillGradient(
                this.previewX,
                this.contentY,
                this.previewX + this.previewWidth,
                this.contentY + this.previewHeight,
                0x15150720,
                0x0807030C);
        drawOutline(graphics,
                this.previewX,
                this.contentY,
                this.previewWidth,
                this.previewHeight,
                0x997C3CA0);
        long now = System.currentTimeMillis();
        updatePreviewPhase(now);
        this.previewState.ageInTicks = previewAnimationAge(now);
        int renderTop = this.contentY + 3;
        int renderBottom = this.contentY + this.previewHeight - 3;
        int renderSize = Math.max(1, Math.min(this.previewWidth - 6, renderBottom - renderTop));
        int x = this.previewX + (this.previewWidth - renderSize) / 2;
        int y = renderTop + Math.max(0, (renderBottom - renderTop - renderSize) / 2);
        Quaternionf rotation = new Quaternionf()
                .rotateZ((float) Math.PI)
                .rotateX(0.18F);
        float previewScale = previewScale(now);
        drawPreviewAmbientParticles(graphics, now, x + renderSize / 2, y + renderSize / 2, renderSize);
        if (previewScale > 0.01F) {
            graphics.entity(
                    this.previewState,
                    renderSize * 0.43F * previewScale,
                    new Vector3f(0.0F, 1.0F, 0.0F),
                    rotation,
                    new Quaternionf(),
                    x,
                    y,
                    x + renderSize,
                    y + renderSize);
        }
        drawPreviewEffectParticles(graphics, now, x + renderSize / 2, y + renderSize / 2, renderSize);

        if (this.previewNoteHeight > 0 && this.activeTab == Tab.VISUALS) {
            Component note = Component.literal(this.spanish
                    ? "El tinte se aplica sobre la textura activa."
                    : "Tint is applied over the active texture.");
            List<FormattedCharSequence> lines = this.font.split(note, Math.max(20, this.previewWidth - 6));
            int noteY = this.contentY + this.previewHeight + 2;
            for (FormattedCharSequence line : lines) {
                graphics.text(this.font, line, this.previewX + 3, noteY, 0xFFC6B5CC, false);
                noteY += this.font.lineHeight;
            }
        }
    }

    private void drawPreviewAmbientParticles(
            GuiGraphicsExtractor graphics,
            long now,
            int centerX,
            int centerY,
            int renderSize
    ) {
        double seconds = now / 1_000.0D;
        int count = Mth.clamp(renderSize / 4, 16, 28);
        for (int index = 0; index < count; index++) {
            double angle = seconds * (0.55D + index % 3 * 0.12D) + index * 2.399963229728653D;
            float radius = renderSize * (0.24F + index % 5 * 0.035F);
            int x = centerX + Math.round((float) Math.cos(angle) * radius);
            int y = centerY + Math.round((float) Math.sin(angle * 1.13D) * radius * 0.72F);
            int size = index % 7 == 0 ? 2 : 1;
            int alpha = 92 + index % 5 * 16;
            int color = alpha << 24 | (index % 3 == 0 ? 244 : 188) << 16 | 78 << 8 | 255;
            graphics.fill(x - 1, y - 1, x + size + 1, y + size + 1, alpha / 4 << 24 | 0x8E2AE0);
            graphics.fill(x, y, x + size, y + size, color);
        }
    }

    private void updatePreviewPhase(long now) {
        long elapsed = now - this.previewPhaseStartedAt;
        if (this.previewPhase == PreviewPhase.APPEARING && elapsed >= PREVIEW_APPEAR_MILLIS) {
            this.previewPhase = PreviewPhase.VISIBLE;
            this.previewPhaseStartedAt = now;
        } else if (this.previewPhase == PreviewPhase.EXPLODING && elapsed >= PREVIEW_EXPLOSION_MILLIS) {
            this.previewPhase = PreviewPhase.HIDDEN;
            this.previewPhaseStartedAt = now;
        } else if (this.previewPhase == PreviewPhase.HIDDEN && now >= this.previewRespawnAt) {
            startPreviewAppearance(now);
        }
    }

    private float previewScale(long now) {
        return switch (this.previewPhase) {
            case APPEARING -> easeOutBack(progress(now - this.previewPhaseStartedAt, PREVIEW_APPEAR_MILLIS));
            case VISIBLE -> 1.0F;
            case EXPLODING -> 1.0F - easeOutCubic(
                    progress(now - this.previewPhaseStartedAt, PREVIEW_EXPLOSION_MILLIS));
            case HIDDEN -> 0.0F;
        };
    }

    private void drawPreviewEffectParticles(
            GuiGraphicsExtractor graphics,
            long now,
            int centerX,
            int centerY,
            int renderSize
    ) {
        if (this.previewPhase != PreviewPhase.EXPLODING && this.previewPhase != PreviewPhase.APPEARING) {
            return;
        }

        boolean exploding = this.previewPhase == PreviewPhase.EXPLODING;
        float value = progress(
                now - this.previewPhaseStartedAt,
                exploding ? PREVIEW_EXPLOSION_MILLIS : PREVIEW_APPEAR_MILLIS);
        int count = exploding ? 34 : 22;
        for (int index = 0; index < count; index++) {
            double angle = index * 2.399963229728653D;
            float distance = exploding
                    ? value * renderSize * (0.24F + index % 4 * 0.055F)
                    : (1.0F - value) * renderSize * (0.20F + index % 3 * 0.045F);
            int x = centerX + Math.round((float) Math.cos(angle) * distance);
            int y = centerY + Math.round((float) Math.sin(angle) * distance);
            int alpha = exploding
                    ? Math.round(220.0F * (1.0F - value))
                    : Math.round(170.0F * (1.0F - Math.abs(value * 2.0F - 1.0F)));
            int color = alpha << 24 | (index % 3 == 0 ? 245 : 183) << 16 | 88 << 8 | 255;
            int size = index % 7 == 0 ? 3 : index % 3 == 0 ? 2 : 1;
            graphics.fill(x - 1, y - 1, x + size + 1, y + size + 1, alpha / 4 << 24 | 0x9228E8);
            graphics.fill(x, y, x + size, y + size, color);
        }
    }

    private void startPreviewAppearance(long now) {
        this.previewPhase = PreviewPhase.APPEARING;
        this.previewPhaseStartedAt = now;
        this.previewRespawnAt = 0L;
    }

    private void explodePreview(long now) {
        CrystalSoundManager.playPreviewExplosion();
        this.previewPhase = PreviewPhase.EXPLODING;
        this.previewPhaseStartedAt = now;
        this.previewRespawnAt = now + PREVIEW_RESPAWN_MILLIS;
    }

    private void handlePreviewInput(boolean attackInput) {
        long now = System.currentTimeMillis();
        updatePreviewPhase(now);
        if (attackInput) {
            if (this.previewPhase == PreviewPhase.VISIBLE
                    || this.previewPhase == PreviewPhase.APPEARING) {
                explodePreview(now);
            }
        } else {
            startPreviewAppearance(now);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        boolean mappedAttack = this.minecraft != null
                && this.minecraft.options.keyAttack.matchesMouse(event);
        boolean mappedUse = this.minecraft != null
                && this.minecraft.options.keyUse.matchesMouse(event);
        boolean attackInput = mappedAttack || (!mappedUse && event.button() == 0);
        boolean useInput = mappedUse || (!mappedAttack && event.button() == 1);
        if ((attackInput || useInput)
                && this.previewWidth > 0
                && event.x() >= this.previewX
                && event.x() <= this.previewX + this.previewWidth
                && event.y() >= this.contentY
                && event.y() <= this.contentY + this.previewHeight) {
            handlePreviewInput(attackInput);
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (this.minecraft != null && this.previewWidth > 0) {
            if (this.minecraft.options.keyAttack.matches(event)) {
                handlePreviewInput(true);
                return true;
            }
            if (this.minecraft.options.keyUse.matches(event)) {
                handlePreviewInput(false);
                return true;
            }
        }
        return super.keyPressed(event);
    }

    private float previewAnimationAge(long now) {
        return Math.max(0L, now - this.openedAt) / 50.0F;
    }

    private void drawScrollbar(GuiGraphicsExtractor graphics) {
        if (this.maxScroll <= 0 || this.logicalContentBottom <= 0) {
            return;
        }
        int trackX = this.optionsX + this.optionsWidth - 2;
        int trackHeight = this.contentHeight;
        int thumbHeight = Math.max(9, trackHeight * trackHeight / this.logicalContentBottom);
        int travel = Math.max(1, trackHeight - thumbHeight);
        int thumbY = this.contentY + Math.round(travel * currentScroll() / (float) this.maxScroll);
        graphics.fill(trackX, this.contentY, trackX + 2, this.contentY + trackHeight, 0x522B1236);
        graphics.fill(trackX, thumbY, trackX + 2, thumbY + thumbHeight, 0xE2C06BE8);
    }

    @Override
    public boolean mouseScrolled(
            double mouseX,
            double mouseY,
            double horizontalAmount,
            double verticalAmount
    ) {
        if (this.maxScroll > 0
                && mouseX >= this.optionsX
                && mouseX <= this.optionsX + this.optionsWidth
                && mouseY >= this.contentY
                && mouseY <= this.contentY + this.contentHeight) {
            int direction = verticalAmount > 0.0D ? -1 : verticalAmount < 0.0D ? 1 : 0;
            if (direction != 0) {
                int next = Mth.clamp(
                        currentScroll() + direction * Math.max(8, rowStep()),
                        0,
                        this.maxScroll);
                setCurrentScroll(next);
                applyContentScroll();
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void onClose() {
        saveSettings();
        this.minecraft.setScreen(this.parent);
    }

    @Override
    public void removed() {
        saveSettings();
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    private void saveSettings() {
        if (this.saved) {
            return;
        }
        CrystalVisualConfig.save();
        CrystalSoundManager.reloadFromConfig();
        this.saved = true;
    }

    private int rowStep() {
        return this.controlHeight + this.rowGap;
    }

    private static boolean usesSpanish() {
        String language = Minecraft.getInstance()
                .getLanguageManager()
                .getSelected()
                .toLowerCase(Locale.ROOT)
                .replace('-', '_');
        return language.equals("es")
                || language.startsWith("es_");
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
        if (width <= 0 || height <= 0) {
            return;
        }
        int radius = Math.min(4, Math.min(width / 2, height / 2));
        graphics.fillGradient(x + radius, y, x + width - radius, y + height, topColor, bottomColor);
        graphics.fillGradient(x, y + radius, x + width, y + height - radius, topColor, bottomColor);
        if (radius == 4) {
            graphics.fill(x + 2, y + 1, x + width - 2, y + 2, topColor);
            graphics.fill(x + 1, y + 2, x + width - 1, y + 4, topColor);
            graphics.fill(x + 1, y + height - 4, x + width - 1, y + height - 2, bottomColor);
            graphics.fill(x + 2, y + height - 2, x + width - 2, y + height - 1, bottomColor);
        }
    }

    private static void drawRoundedOutline(
            GuiGraphicsExtractor graphics,
            int x,
            int y,
            int width,
            int height,
            int color
    ) {
        if (width < 9 || height < 9) {
            drawOutline(graphics, x, y, width, height, color);
            return;
        }

        int right = x + width;
        int bottom = y + height;
        graphics.fill(x + 4, y, right - 4, y + 1, color);
        graphics.fill(x + 2, y + 1, x + 4, y + 2, color);
        graphics.fill(x + 1, y + 2, x + 2, y + 4, color);
        graphics.fill(x, y + 4, x + 1, bottom - 4, color);

        graphics.fill(right - 4, y + 1, right - 2, y + 2, color);
        graphics.fill(right - 2, y + 2, right - 1, y + 4, color);
        graphics.fill(right - 1, y + 4, right, bottom - 4, color);

        graphics.fill(x + 1, bottom - 4, x + 2, bottom - 2, color);
        graphics.fill(x + 2, bottom - 2, x + 4, bottom - 1, color);
        graphics.fill(x + 4, bottom - 1, right - 4, bottom, color);

        graphics.fill(right - 2, bottom - 4, right - 1, bottom - 2, color);
        graphics.fill(right - 4, bottom - 2, right - 2, bottom - 1, color);
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

    private static float progress(long elapsed, long duration) {
        return Mth.clamp(elapsed / (float) duration, 0.0F, 1.0F);
    }

    private static float easeOutCubic(float value) {
        float inverse = 1.0F - value;
        return 1.0F - inverse * inverse * inverse;
    }

    private static float easeOutBack(float value) {
        float shifted = value - 1.0F;
        return 1.0F + 2.70158F * shifted * shifted * shifted + 1.70158F * shifted * shifted;
    }

    private enum Tab {
        VISUALS,
        SOUNDS,
        TWEAKS
    }

    private enum Layer {
        OUTER,
        INNER,
        CORE
    }

    private enum PreviewPhase {
        APPEARING,
        VISIBLE,
        EXPLODING,
        HIDDEN
    }

    private static final class CompactSlider extends AbstractSliderButton {
        private final double minimum;
        private final double maximum;
        private final Consumer<Double> onChange;
        private final DoubleFunction<String> formatter;

        private CompactSlider(
                int x,
                int y,
                int width,
                int height,
                double minimum,
                double maximum,
                double current,
                Consumer<Double> onChange,
                DoubleFunction<String> formatter
        ) {
            super(x, y, width, height, Component.empty(), (current - minimum) / (maximum - minimum));
            this.minimum = minimum;
            this.maximum = maximum;
            this.onChange = onChange;
            this.formatter = formatter;
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal(this.formatter.apply(currentValue())));
        }

        @Override
        protected void applyValue() {
            this.onChange.accept(currentValue());
        }

        private double currentValue() {
            return this.minimum + this.value * (this.maximum - this.minimum);
        }

        @Override
        public void extractWidgetRenderState(
                GuiGraphicsExtractor graphics,
                int mouseX,
                int mouseY,
                float partialTick
        ) {
            int fill = isHoveredOrFocused() ? 0xD15D2877 : 0xB53A1748;
            int outline = isHoveredOrFocused() ? 0xE1D493FF : 0xB58B50B5;
            graphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), fill);
            drawOutline(graphics, getX(), getY(), getWidth(), getHeight(), outline);
            int handleX = getX() + (int) Math.round(this.value * Math.max(0, getWidth() - 7));
            graphics.fill(handleX, getY() + 1, handleX + 7, getY() + getHeight() - 1, 0xDDD590F3);
            graphics.centeredText(
                    Minecraft.getInstance().font,
                    getMessage(),
                    getX() + getWidth() / 2,
                    getY() + (getHeight() - Minecraft.getInstance().font.lineHeight) / 2,
                    0xFFF5E9FA);
        }
    }
}
