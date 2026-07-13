package dev.zymekoh.kohscrystaltweaks.gui;

import dev.zymekoh.kohscrystaltweaks.config.KoHsCrystalTweaksConfig;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

public final class KoHsCrystalTweaksConfigScreen extends Screen {
    private static final Component TITLE = Component.literal("KoHs Crystal Tweaks");
    private static final Component SUBTITLE = Component.literal("Legit Crystal Optimizer");
    private static final int DESC_COLOR = 0xFF9E92B0;

    private final Screen parent;
    private final List<AbstractWidget> contentWidgets = new ArrayList<>();

    private Tab activeTab = Tab.OPTIMIZATION;
    private Button tabOptimization;
    private Button tabVisuals;
    private Button tabTweaks;
    private Button tabSound;

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
    private int controlHeight;
    private int rowGap;
    private int buttonWidth;
    private int tabHeight;
    private boolean compact;

    private boolean stateLoaded;
    private boolean clientSideCrystalsEnabled;
    private boolean seamlessEnabled;
    private boolean crystalTintEnabled;
    private ColorState frameColor;
    private ColorState coreColor;
    private TintTarget activeTintTarget = TintTarget.FRAME;
    private boolean draggingSpectrum;
    private boolean draggingHue;
    private float crystalSpinSpeed;
    private boolean crystalFlotationEnabled;
    private boolean staticCrystalEnabled;
    private boolean placementFixEnabled;
    private boolean customSoundEnabled;
    private String selectedSoundFileName = "";
    private float soundVolume;
    private float soundSpeed;
    private String soundStatus = "";

    public KoHsCrystalTweaksConfigScreen(Screen parent) {
        super(TITLE);
        this.parent = parent;
    }

    @Override
    protected void init() {
        computeLayout();
        loadStateOnce();
        contentWidgets.clear();

        int tabGap = panelWidth < 320 ? 2 : 4;
        int tabsWidth = panelWidth - 12;
        int tabWidth = Math.max(1, (tabsWidth - tabGap * 3) / 4);
        int tabStartX = panelX + (panelWidth - (tabWidth * 4 + tabGap * 3)) / 2;
        int tabY = panelY + (compact ? 23 : 35);

        tabOptimization = addRenderableWidget(Button.builder(
                Component.literal("Optimization"), button -> switchTab(Tab.OPTIMIZATION))
                .bounds(tabStartX, tabY, tabWidth, tabHeight)
                .build());
        tabVisuals = addRenderableWidget(Button.builder(
                Component.literal("Visuals"), button -> switchTab(Tab.VISUALS))
                .bounds(tabStartX + tabWidth + tabGap, tabY, tabWidth, tabHeight)
                .build());
        tabTweaks = addRenderableWidget(Button.builder(
                Component.literal("Tweaks"), button -> switchTab(Tab.TWEAKS))
                .bounds(tabStartX + (tabWidth + tabGap) * 2, tabY, tabWidth, tabHeight)
                .build());
        tabSound = addRenderableWidget(Button.builder(
                Component.literal("Sound"), button -> switchTab(Tab.SOUND))
                .bounds(tabStartX + (tabWidth + tabGap) * 3, tabY, tabWidth, tabHeight)
                .build());

        int closeHeight = Math.max(12, Math.min(16, footerHeight - 6));
        addRenderableWidget(Button.builder(Component.literal("Close"), button -> onClose())
                .bounds(centerX() - 40, panelY + panelHeight - footerHeight + 3, 80, closeHeight)
                .build());

        initContent();
    }

    private void computeLayout() {
        int horizontalMargin = width < 560 ? 12 : 80;
        int verticalMargin = height < 320 ? 8 : 54;
        int availableWidth = Math.max(1, width - horizontalMargin);
        int availableHeight = Math.max(1, height - verticalMargin);
        int minWidth = Math.min(280, availableWidth);
        int minHeight = Math.min(132, availableHeight);
        int preferredWidth = width < 420 ? availableWidth : 360;
        int preferredHeight = height < 260 ? availableHeight : 240;

        panelWidth = Mth.clamp(preferredWidth, minWidth, availableWidth);
        panelHeight = Mth.clamp(preferredHeight, minHeight, availableHeight);
        panelX = (width - panelWidth) / 2;
        panelY = (height - panelHeight) / 2;

        compact = panelHeight < 210 || panelWidth < 320;
        headerHeight = compact ? 43 : 56;
        footerHeight = compact ? 22 : 28;
        int padding = panelWidth < 320 ? 8 : 14;

        contentX = panelX + padding;
        contentY = panelY + headerHeight;
        contentWidth = Math.max(1, panelWidth - padding * 2);
        contentHeight = Math.max(1, panelHeight - headerHeight - footerHeight - 3);
        controlHeight = compact
                ? Math.max(10, Math.min(14, Math.max(10, (contentHeight - 6) / 4)))
                : 16;
        rowGap = compact ? 2 : 6;
        buttonWidth = Math.min(190, contentWidth);
        tabHeight = compact ? 14 : 16;
    }

    private void loadStateOnce() {
        if (stateLoaded) {
            return;
        }

        KoHsCrystalTweaksConfig config = KoHsCrystalTweaksConfig.get();
        clientSideCrystalsEnabled = config.clientSideCrystalsEnabled;
        seamlessEnabled = config.seamlessEnabled;
        crystalTintEnabled = config.crystalTintEnabled;
        frameColor = ColorState.fromArgb(KoHsCrystalTweaksConfig.getCrystalFrameTintArgb());
        coreColor = ColorState.fromArgb(KoHsCrystalTweaksConfig.getCrystalCoreTintArgb());
        crystalSpinSpeed = config.crystalSpinSpeed;
        crystalFlotationEnabled = config.crystalFlotationEnabled;
        staticCrystalEnabled = config.staticCrystalEnabled;
        placementFixEnabled = config.placementFixEnabled;
        customSoundEnabled = config.customSoundEnabled;
        selectedSoundFileName = config.customSoundFileName;
        soundVolume = config.soundVolume;
        soundSpeed = config.soundSpeed;
        stateLoaded = true;
    }

    private void switchTab(Tab tab) {
        if (activeTab == tab) {
            return;
        }
        activeTab = tab;
        rebuildContent();
    }

    private void rebuildContent() {
        for (AbstractWidget widget : contentWidgets) {
            removeWidget(widget);
        }
        contentWidgets.clear();
        initContent();
    }

    private void initContent() {
        switch (activeTab) {
            case OPTIMIZATION -> initOptimizationTab();
            case VISUALS -> initVisualsTab();
            case TWEAKS -> initTweaksTab();
            case SOUND -> initSoundTab();
        }
    }

    private void initOptimizationTab() {
        int secondY = contentY + controlHeight + (compact ? 5 : 16);

        addContent(Button.builder(localCrystalLabel(), button -> {
            clientSideCrystalsEnabled = !clientSideCrystalsEnabled;
            button.setMessage(localCrystalLabel());
        }).bounds(centerX() - buttonWidth / 2, contentY, buttonWidth, controlHeight).build());

        addContent(Button.builder(seamlessLabel(), button -> {
            seamlessEnabled = !seamlessEnabled;
            button.setMessage(seamlessLabel());
        }).bounds(centerX() - buttonWidth / 2, secondY, buttonWidth, controlHeight).build());
    }

    private void initVisualsTab() {
        addContent(Button.builder(tintLabel(), button -> {
            crystalTintEnabled = !crystalTintEnabled;
            rebuildContent();
        }).bounds(centerX() - buttonWidth / 2, contentY, buttonWidth, controlHeight).build());

        if (!crystalTintEnabled) {
            return;
        }

        int layerY = contentY + rowStep();
        int layerWidth = Math.min(84, Math.max(1, (buttonWidth - 4) / 2));
        int layerStartX = centerX() - (layerWidth * 2 + 4) / 2;
        addContent(Button.builder(layerLabel(TintTarget.FRAME), button -> {
            activeTintTarget = TintTarget.FRAME;
            updateLayerLabels();
        }).bounds(layerStartX, layerY, layerWidth, controlHeight).build());
        addContent(Button.builder(layerLabel(TintTarget.CORE), button -> {
            activeTintTarget = TintTarget.CORE;
            updateLayerLabels();
        }).bounds(layerStartX + layerWidth + 4, layerY, layerWidth, controlHeight).build());
    }

    private void initTweaksTab() {
        addContent(Button.builder(placementFixLabel(), button -> requestPlacementFixToggle())
                .bounds(centerX() - buttonWidth / 2, contentY, buttonWidth, controlHeight).build());

        addContent(Button.builder(staticCrystalLabel(), button -> {
            staticCrystalEnabled = !staticCrystalEnabled;
            rebuildContent();
        }).bounds(centerX() - buttonWidth / 2, contentY + rowStep(), buttonWidth, controlHeight).build());

        if (staticCrystalEnabled) {
            return;
        }

        addContent(Button.builder(flotationLabel(), button -> {
            crystalFlotationEnabled = !crystalFlotationEnabled;
            button.setMessage(flotationLabel());
        }).bounds(centerX() - buttonWidth / 2, contentY + rowStep() * 2, buttonWidth, controlHeight).build());

        addContent(new PercentSlider(
                centerX() - buttonWidth / 2,
                contentY + rowStep() * 3,
                buttonWidth,
                controlHeight,
                "Spin Speed",
                0.0,
                3.0,
                crystalSpinSpeed,
                value -> crystalSpinSpeed = value.floatValue()));
    }

    private void requestPlacementFixToggle() {
        Minecraft client = Minecraft.getInstance();
        if (!placementFixEnabled) {
            placementFixEnabled = true;
            rebuildContent();
            return;
        }

        client.setScreen(new ConfirmScreen(
                accepted -> {
                    placementFixEnabled = !accepted;
                    client.setScreen(this);
                },
                Component.literal("¿Desactivar Placement Fix?"),
                Component.literal("Desactivarlo puede reintroducir delay al colocar cristales rápidamente tras la obsidiana."),
                Component.literal("Aceptar"),
                Component.literal("Restablecer")));
    }

    private void initSoundTab() {
        addContent(Button.builder(soundToggleLabel(), button -> {
            customSoundEnabled = !customSoundEnabled;
            rebuildContent();
        }).bounds(centerX() - buttonWidth / 2, contentY, buttonWidth, controlHeight).build());

        if (!customSoundEnabled) {
            return;
        }

        addContent(Button.builder(Component.literal("Select Sound File..."), button -> openFilePicker())
                .bounds(centerX() - buttonWidth / 2, contentY + rowStep(), buttonWidth, controlHeight)
                .build());
        addContent(new PercentSlider(
                centerX() - buttonWidth / 2,
                contentY + rowStep() * 2,
                buttonWidth,
                controlHeight,
                "Volume",
                0.0,
                2.0,
                soundVolume,
                value -> soundVolume = value.floatValue()));
        addContent(new PercentSlider(
                centerX() - buttonWidth / 2,
                contentY + rowStep() * 3,
                buttonWidth,
                controlHeight,
                "Speed",
                0.5,
                2.0,
                soundSpeed,
                value -> soundSpeed = value.floatValue()));
    }

    private void addContent(AbstractWidget widget) {
        contentWidgets.add(widget);
        addRenderableWidget(widget);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0x99000000);
        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xE01A0A2E);
        graphics.outline(panelX, panelY, panelWidth, panelHeight, 0xE0B86BFF);

        int contentBorderY = contentY - 4;
        int contentBorderHeight = Math.max(1, panelY + panelHeight - footerHeight - contentBorderY);
        graphics.outline(panelX + 4, contentBorderY, panelWidth - 8, contentBorderHeight, 0x60B86BFF);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        graphics.centeredText(font, TITLE, centerX(), panelY + 5, 0xFFEAD1FF);
        if (!compact) {
            graphics.centeredText(font, SUBTITLE, centerX(), panelY + 17, 0xFFDDA6FF);
        }
        drawActiveTabUnderline(graphics);

        switch (activeTab) {
            case OPTIMIZATION -> extractOptimizationContent(graphics);
            case VISUALS -> extractVisualsContent(graphics);
            case TWEAKS -> extractTweaksContent(graphics);
            case SOUND -> extractSoundContent(graphics);
        }
    }

    private void drawActiveTabUnderline(GuiGraphicsExtractor graphics) {
        Button activeButton = switch (activeTab) {
            case OPTIMIZATION -> tabOptimization;
            case VISUALS -> tabVisuals;
            case TWEAKS -> tabTweaks;
            case SOUND -> tabSound;
        };
        if (activeButton != null) {
            graphics.fill(
                    activeButton.getX(),
                    activeButton.getY() + activeButton.getHeight(),
                    activeButton.getX() + activeButton.getWidth(),
                    activeButton.getY() + activeButton.getHeight() + 2,
                    0xFFFFE38D);
        }
    }

    private void extractOptimizationContent(GuiGraphicsExtractor graphics) {
        if (compact) {
            return;
        }
        drawDescription(graphics, "Predicts crystal placement client-side", contentX, contentY + controlHeight + 2);
        int secondY = contentY + controlHeight + 16;
        drawDescription(graphics, "Smooths transition to server crystals", contentX, secondY + controlHeight + 2);
    }

    private void extractVisualsContent(GuiGraphicsExtractor graphics) {
        if (!crystalTintEnabled) {
            if (!compact) {
                drawDescription(graphics, "Apply independent color tints to the crystal frame and core.",
                        contentX, contentY + controlHeight + 4);
            }
            return;
        }

        int pickerY = pickerY();
        if (pickerY + pickerHeight() > contentY + contentHeight) {
            return;
        }
        extractColorPicker(graphics, pickerX(), pickerY);

        if (contentWidth >= 300) {
            int infoX = contentX + 170;
            int infoY = pickerY;
            ColorState activeColor = activeColor();
            graphics.text(font, Component.literal(activeTintTarget == TintTarget.FRAME ? "Editing: Outer" : "Editing: Inner"),
                    infoX, infoY, 0xFFEAD1FF, true);
            graphics.text(font, Component.literal(activeColor.toHex()), infoX, infoY + 11, 0xFFDDEEFF, true);
            drawSwatch(graphics, "Outer", frameColor, activeTintTarget == TintTarget.FRAME, infoX, infoY + 25);
            drawSwatch(graphics, "Inner", coreColor, activeTintTarget == TintTarget.CORE, infoX, infoY + 51);
        } else {
            graphics.text(font, Component.literal(activeColor().toHex()),
                    hueBarX() + 13, pickerY, 0xFFDDEEFF, true);
        }
    }

    private void extractColorPicker(GuiGraphicsExtractor graphics, int x, int y) {
        ColorState color = activeColor();
        int pickerWidth = pickerWidth();
        int pickerHeight = pickerHeight();
        int hueRgb = 0xFF000000 | Mth.hsvToRgb(color.hue, 1.0F, 1.0F);

        for (int dx = 0; dx < pickerWidth; dx++) {
            float saturation = dx / (float) Math.max(pickerWidth - 1, 1);
            int top = lerpColor(0xFFFFFFFF, hueRgb, saturation);
            graphics.fillGradient(x + dx, y, x + dx + 1, y + pickerHeight, top, 0xFF000000);
        }
        graphics.outline(x - 1, y - 1, pickerWidth + 2, pickerHeight + 2, 0xE0B86BFF);

        int markerX = x + Math.round(color.saturation * (pickerWidth - 1));
        int markerY = y + Math.round((1.0F - color.value) * (pickerHeight - 1));
        graphics.outline(markerX - 3, markerY - 3, 7, 7, 0xFFFFFFFF);
        graphics.outline(markerX - 2, markerY - 2, 5, 5, 0xFF201826);

        int hueX = hueBarX();
        for (int dy = 0; dy < pickerHeight; dy++) {
            float hue = dy / (float) Math.max(pickerHeight - 1, 1);
            graphics.fill(hueX, y + dy, hueX + 8, y + dy + 1,
                    0xFF000000 | Mth.hsvToRgb(hue, 1.0F, 1.0F));
        }
        graphics.outline(hueX - 1, y - 1, 10, pickerHeight + 2, 0xE0B86BFF);

        int hueMarkerY = y + Math.round(color.hue * (pickerHeight - 1));
        graphics.outline(hueX - 2, hueMarkerY - 2, 12, 5, 0xFFFFFFFF);
    }

    private void drawSwatch(GuiGraphicsExtractor graphics, String label, ColorState color, boolean active, int x, int y) {
        int width = Math.max(60, Math.min(110, panelX + panelWidth - 8 - x));
        int height = 22;
        graphics.fill(x, y, x + width, y + height, 0x7A130822);
        graphics.outline(x, y, width, height, active ? 0xFFFFE38D : 0xE0B86BFF);
        graphics.fill(x + 3, y + 3, x + 19, y + 19, color.toArgb());
        graphics.outline(x + 3, y + 3, 16, 16, 0xFFFFFFFF);
        graphics.text(font, Component.literal(label), x + 23, y + 2, 0xFFF4E8FF, true);
        graphics.text(font, Component.literal(color.toHex()), x + 23, y + 12,
                active ? 0xFFFFE38D : 0xFFD8D0E6, true);
    }

    private void extractTweaksContent(GuiGraphicsExtractor graphics) {
        if (staticCrystalEnabled) {
            if (!compact) {
                drawDescription(graphics, "Keeps the crystal completely still: no spin and no flotation.",
                        contentX, contentY + rowStep() + controlHeight + 4);
            }
            return;
        }

        int descriptionY = contentY + rowStep() * 4 + 2;
        if (!compact && descriptionY + 9 <= contentY + contentHeight) {
            drawDescription(graphics, "Placement Fix changes only the target of the current vanilla click.",
                    contentX, descriptionY);
        }
    }

    private void extractSoundContent(GuiGraphicsExtractor graphics) {
        if (!customSoundEnabled) {
            if (!compact) {
                drawDescription(graphics, "Replace the crystal explosion sound with WAV, OGG or MP3.",
                        contentX, contentY + controlHeight + 4);
            }
            return;
        }

        int statusY = contentY + rowStep() * 4 + 2;
        if (statusY + 9 > contentY + contentHeight) {
            return;
        }

        String status = soundStatus;
        int color = 0xFFFF8888;
        if (status.isEmpty() && !selectedSoundFileName.isBlank()) {
            status = "Selected: " + selectedSoundFileName;
            color = 0xFF88FF88;
        } else if (status.isEmpty()) {
            status = "No sound file selected";
            color = 0xFFD8D0E6;
        }

        if (font.width(status) > contentWidth) {
            String ellipsis = "...";
            status = font.plainSubstrByWidth(status, Math.max(1, contentWidth - font.width(ellipsis))) + ellipsis;
        }
        graphics.text(font, status, contentX, statusY, color, true);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0 && activeTab == Tab.VISUALS && crystalTintEnabled) {
            if (isInSpectrum(event.x(), event.y())) {
                applySaturationAndValue(event.x(), event.y());
                draggingSpectrum = true;
                return true;
            }
            if (isInHueBar(event.x(), event.y())) {
                applyHue(event.y());
                draggingHue = true;
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        if (event.button() == 0 && activeTab == Tab.VISUALS && crystalTintEnabled) {
            if (draggingSpectrum) {
                applySaturationAndValue(event.x(), event.y());
                return true;
            }
            if (draggingHue) {
                applyHue(event.y());
                return true;
            }
        }
        return super.mouseDragged(event, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() == 0) {
            draggingSpectrum = false;
            draggingHue = false;
        }
        return super.mouseReleased(event);
    }

    private boolean isInSpectrum(double mouseX, double mouseY) {
        return mouseX >= pickerX()
                && mouseX < pickerX() + pickerWidth()
                && mouseY >= pickerY()
                && mouseY < pickerY() + pickerHeight();
    }

    private boolean isInHueBar(double mouseX, double mouseY) {
        return mouseX >= hueBarX()
                && mouseX < hueBarX() + 8
                && mouseY >= pickerY()
                && mouseY < pickerY() + pickerHeight();
    }

    private void applySaturationAndValue(double mouseX, double mouseY) {
        ColorState color = activeColor();
        color.saturation = Mth.clamp(
                (float) ((mouseX - pickerX()) / Math.max(pickerWidth() - 1.0, 1.0)),
                0.0F,
                1.0F);
        color.value = Mth.clamp(
                1.0F - (float) ((mouseY - pickerY()) / Math.max(pickerHeight() - 1.0, 1.0)),
                0.0F,
                1.0F);
    }

    private void applyHue(double mouseY) {
        activeColor().hue = Mth.clamp(
                (float) ((mouseY - pickerY()) / Math.max(pickerHeight() - 1.0, 1.0)),
                0.0F,
                1.0F);
    }

    private void openFilePicker() {
        Thread pickerThread = new Thread(() -> {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                PointerBuffer patterns = stack.mallocPointer(3);
                patterns.put(stack.UTF8("*.wav"));
                patterns.put(stack.UTF8("*.ogg"));
                patterns.put(stack.UTF8("*.mp3"));
                patterns.flip();

                String result = TinyFileDialogs.tinyfd_openFileDialog(
                        "Select Explosion Sound",
                        "",
                        patterns,
                        "Audio Files (*.wav, *.ogg, *.mp3)",
                        false);
                if (result != null) {
                    Minecraft.getInstance().execute(() -> importSoundFile(Path.of(result)));
                }
            } catch (Exception exception) {
                Minecraft.getInstance().execute(() -> soundStatus = "File picker failed: " + exception.getMessage());
            }
        }, "KCT-Sound-File-Picker");
        pickerThread.setDaemon(true);
        pickerThread.start();
    }

    private void importSoundFile(Path source) {
        try {
            String fileName = source.getFileName().toString();
            String lowerName = fileName.toLowerCase(Locale.ROOT);
            if (!lowerName.endsWith(".wav") && !lowerName.endsWith(".ogg") && !lowerName.endsWith(".mp3")) {
                soundStatus = "Unsupported audio format";
                return;
            }

            Path soundsDirectory = KoHsCrystalTweaksConfig.getSoundsDir();
            Files.createDirectories(soundsDirectory);
            Files.copy(source, soundsDirectory.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
            selectedSoundFileName = fileName;
            soundStatus = "";
        } catch (Exception exception) {
            soundStatus = "Import failed: " + exception.getMessage();
        }
    }

    @Override
    public void onClose() {
        KoHsCrystalTweaksConfig config = KoHsCrystalTweaksConfig.get();
        config.clientSideCrystalsEnabled = clientSideCrystalsEnabled;
        config.seamlessEnabled = seamlessEnabled;
        config.crystalTintEnabled = crystalTintEnabled;
        config.crystalFrameTintHex = frameColor.toHex();
        config.crystalCoreTintHex = coreColor.toHex();
        config.crystalSpinSpeed = crystalSpinSpeed;
        config.crystalFlotationEnabled = crystalFlotationEnabled;
        config.staticCrystalEnabled = staticCrystalEnabled;
        config.placementFixEnabled = placementFixEnabled;
        config.customSoundEnabled = customSoundEnabled;
        config.customSoundFileName = selectedSoundFileName;
        config.soundVolume = soundVolume;
        config.soundSpeed = soundSpeed;
        KoHsCrystalTweaksConfig.save();
        minecraft.setScreen(parent);
    }

    private Component localCrystalLabel() {
        return toggleLabel("Local Crystal", clientSideCrystalsEnabled);
    }

    private Component seamlessLabel() {
        return toggleLabel("Seamless Mode", seamlessEnabled);
    }

    private Component tintLabel() {
        return toggleLabel("Crystal Tint", crystalTintEnabled);
    }

    private Component staticCrystalLabel() {
        return toggleLabel("Static Crystal", staticCrystalEnabled);
    }

    private Component placementFixLabel() {
        return toggleLabel("Placement Fix", placementFixEnabled);
    }

    private Component flotationLabel() {
        return toggleLabel("Crystal Flotation", crystalFlotationEnabled);
    }

    private Component soundToggleLabel() {
        return toggleLabel("Custom Sound", customSoundEnabled);
    }

    private static Component toggleLabel(String label, boolean enabled) {
        return Component.literal(label + ": " + (enabled ? "ON" : "OFF"));
    }

    private Component layerLabel(TintTarget target) {
        boolean active = activeTintTarget == target;
        String layer = target == TintTarget.FRAME ? "Outer" : "Inner";
        return Component.literal(active ? layer + ": EDITING" : "Edit " + layer);
    }

    private void updateLayerLabels() {
        if (contentWidgets.size() < 3) {
            return;
        }
        contentWidgets.get(1).setMessage(layerLabel(TintTarget.FRAME));
        contentWidgets.get(2).setMessage(layerLabel(TintTarget.CORE));
    }

    private ColorState activeColor() {
        return activeTintTarget == TintTarget.FRAME ? frameColor : coreColor;
    }

    private void drawDescription(GuiGraphicsExtractor graphics, String text, int x, int y) {
        String display = text;
        if (font.width(display) > contentWidth) {
            display = font.plainSubstrByWidth(display, contentWidth);
        }
        graphics.text(font, display, x, y, DESC_COLOR, false);
    }

    private int centerX() {
        return panelX + panelWidth / 2;
    }

    private int rowStep() {
        return controlHeight + rowGap;
    }

    private int pickerX() {
        return contentX;
    }

    private int pickerY() {
        return contentY + rowStep() * 2 + 2;
    }

    private int pickerWidth() {
        int reserved = contentWidth >= 300 ? 170 : 26;
        return Math.max(48, Math.min(100, contentWidth - reserved));
    }

    private int pickerHeight() {
        int available = contentY + contentHeight - pickerY() - 2;
        return Math.max(24, Math.min(52, available));
    }

    private int hueBarX() {
        return pickerX() + pickerWidth() + 8;
    }

    private static int lerpColor(int first, int second, float amount) {
        float clamped = Mth.clamp(amount, 0.0F, 1.0F);
        int red = (int) (((first >> 16) & 0xFF)
                + (((second >> 16) & 0xFF) - ((first >> 16) & 0xFF)) * clamped);
        int green = (int) (((first >> 8) & 0xFF)
                + (((second >> 8) & 0xFF) - ((first >> 8) & 0xFF)) * clamped);
        int blue = (int) ((first & 0xFF) + ((second & 0xFF) - (first & 0xFF)) * clamped);
        return 0xFF000000 | (red << 16) | (green << 8) | blue;
    }

    private enum Tab {
        OPTIMIZATION,
        VISUALS,
        TWEAKS,
        SOUND
    }

    private enum TintTarget {
        FRAME,
        CORE
    }

    private static final class ColorState {
        private float hue;
        private float saturation;
        private float value;

        private static ColorState fromArgb(int color) {
            float red = ((color >> 16) & 0xFF) / 255.0F;
            float green = ((color >> 8) & 0xFF) / 255.0F;
            float blue = (color & 0xFF) / 255.0F;
            float max = Math.max(red, Math.max(green, blue));
            float min = Math.min(red, Math.min(green, blue));
            float delta = max - min;
            float hue = 0.0F;

            if (delta > 0.0F) {
                if (max == red) {
                    hue = ((green - blue) / delta) % 6.0F;
                } else if (max == green) {
                    hue = ((blue - red) / delta) + 2.0F;
                } else {
                    hue = ((red - green) / delta) + 4.0F;
                }
                hue /= 6.0F;
                if (hue < 0.0F) {
                    hue += 1.0F;
                }
            }

            ColorState state = new ColorState();
            state.hue = hue;
            state.saturation = max <= 0.0F ? 0.0F : delta / max;
            state.value = max;
            return state;
        }

        private int toArgb() {
            return 0xFF000000 | Mth.hsvToRgb(hue, saturation, value);
        }

        private String toHex() {
            return String.format("#%06X", toArgb() & 0xFFFFFF);
        }
    }

    private static final class PercentSlider extends AbstractSliderButton {
        private final String label;
        private final double minimum;
        private final double maximum;
        private final java.util.function.Consumer<Double> onChange;

        private PercentSlider(
                int x,
                int y,
                int width,
                int height,
                String label,
                double minimum,
                double maximum,
                double current,
                java.util.function.Consumer<Double> onChange) {
            super(x, y, width, height, Component.empty(), (current - minimum) / (maximum - minimum));
            this.label = label;
            this.minimum = minimum;
            this.maximum = maximum;
            this.onChange = onChange;
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            double displayValue = minimum + value * (maximum - minimum);
            setMessage(Component.literal(label + ": " + Math.round(displayValue * 100.0) + "%"));
        }

        @Override
        protected void applyValue() {
            onChange.accept(minimum + value * (maximum - minimum));
        }
    }
}
