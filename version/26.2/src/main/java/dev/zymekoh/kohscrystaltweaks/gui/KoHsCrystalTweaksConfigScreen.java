package dev.zymekoh.kohscrystaltweaks.gui;

import dev.zymekoh.kohscrystaltweaks.config.KoHsCrystalTweaksConfig;
import dev.zymekoh.kohscrystaltweaks.core.CrystalPredictor;
import dev.zymekoh.kohscrystaltweaks.sound.CrystalSoundManager;
import net.fabricmc.loader.api.FabricLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

public final class KoHsCrystalTweaksConfigScreen extends Screen {
    private static final Component TITLE = Component.literal("KoHs Crystal Tweaks");
    private static final Component SUBTITLE = Component.literal("ZymeKoHs (" + buildVersion() + ")");
    private static final long ENTER_ANIMATION_NANOS = 220_000_000L;
    private static final long DESCRIPTION_ANIMATION_NANOS = 120_000_000L;
    private static final long INFO_CHARACTER_NANOS = 100_000_000L;
    private static final String INFO_TEXT = String.join("\n",
            "[ENGLISH]",
            "safety_contract {",
            "  physical_input_required = true;",
            "  automatic_targeting = false;",
            "  automatic_item_selection = false;",
            "  input_replay_or_delayed_attacks = false;",
            "  custom_network_payloads = false;",
            "  extra_combat_packets = false;",
            "  server_authority = preserved;",
            "}",
            "",
            "// Visual previews cannot be targeted and never alter the crosshair.",
            "// Confirmed Cleanup observes a real vanilla attack packet, then removes",
            "// only that matching crystal from the local world. It sends nothing.",
            "// Server rules differ; this screen is not a universal permission claim.",
            "",
            "KoHs components {",
            "  SafeCrystalGuard -> blocks only accidental local obsidian mining;",
            "  ConfirmedCrystalCleanup -> optional confirmed local cleanup;",
            "  CrystalPredictor -> optional non-targetable visual preview;",
            "  SoundManagerOverrideMixin -> End Crystal audio only;",
            "}",
            "",
            "[ESPA\u00D1OL]",
            "contrato_de_seguridad {",
            "  entrada_fisica_obligatoria = true;",
            "  seleccion_automatica_de_objetivos = false;",
            "  seleccion_automatica_de_items = false;",
            "  repeticion_o_ataques_diferidos = false;",
            "  payloads_de_red_personalizados = false;",
            "  paquetes_de_combate_extra = false;",
            "  autoridad_del_servidor = preservada;",
            "}",
            "",
            "// La vista previa no puede ser atacada ni cambia el punto de mira.",
            "// La limpieza confirmada observa un ataque vanilla real y elimina",
            "// solamente ese cristal del mundo local. No envia nada al servidor.",
            "// Cada servidor tiene sus reglas; esto no garantiza permiso universal.",
            "",
            "componentes KoHs {",
            "  SafeCrystalGuard -> evita solo minar obsidiana local por accidente;",
            "  ConfirmedCrystalCleanup -> limpieza local confirmada y opcional;",
            "  CrystalPredictor -> vista previa visual no atacable y opcional;",
            "  SoundManagerOverrideMixin -> solo audio del End Crystal;",
            "}");

    private final Screen parent;
    private final List<AbstractWidget> contentWidgets = new ArrayList<>();
    private final Map<AbstractWidget, String> descriptions = new IdentityHashMap<>();

    private Tab activeTab = Tab.TWEAKS;
    private Button tabVisuals;
    private Button tabTweaks;
    private Button tabSound;
    private Button tabInfo;
    private Button closeButton;
    private Button infoAcceptButton;
    private long enterAnimationStartNanos;
    private long descriptionAnimationStartNanos;
    private long infoAnimationStartNanos;
    private long infoTextStartNanos;
    private String visibleDescription;
    private boolean infoOpen;
    private boolean infoRevealAll;
    private int infoScroll;
    private boolean stateSaved;

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
    private boolean crystalTintEnabled;
    private ColorState frameColor;
    private ColorState coreColor;
    private TintTarget activeTintTarget = TintTarget.FRAME;
    private boolean draggingSpectrum;
    private boolean draggingHue;
    private float crystalSpinSpeed;
    private boolean crystalFlotationEnabled;
    private boolean staticCrystalEnabled;
    private boolean safeCrystalEnabled;
    private boolean visualCrystalPreviewEnabled;
    private boolean confirmedCrystalCleanupEnabled;
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
        if (enterAnimationStartNanos == 0L) {
            enterAnimationStartNanos = System.nanoTime();
        }
        computeLayout();
        loadStateOnce();
        contentWidgets.clear();
        descriptions.clear();
        visibleDescription = null;
        draggingSpectrum = false;
        draggingHue = false;

        int tabGap = panelWidth < 320 ? 2 : 4;
        int tabsWidth = panelWidth - 12;
        int tabWidth = Math.max(1, (tabsWidth - tabGap * 3) / 4);
        int tabStartX = panelX + (panelWidth - (tabWidth * 4 + tabGap * 3)) / 2;
        int tabY = panelY + (compact ? 23 : 35);

        tabVisuals = addRenderableWidget(purpleButton(
                Component.literal("Visuals"), button -> switchTab(Tab.VISUALS),
                tabStartX, tabY, tabWidth, tabHeight));
        tabTweaks = addRenderableWidget(purpleButton(
                Component.literal("Tweaks"), button -> switchTab(Tab.TWEAKS),
                tabStartX + tabWidth + tabGap, tabY, tabWidth, tabHeight));
        tabSound = addRenderableWidget(purpleButton(
                Component.literal("Sound"), button -> switchTab(Tab.SOUND),
                tabStartX + (tabWidth + tabGap) * 2, tabY, tabWidth, tabHeight));
        tabInfo = addRenderableWidget(purpleButton(
                Component.literal("Info Legit"), button -> openInfo(),
                tabStartX + (tabWidth + tabGap) * 3, tabY, tabWidth, tabHeight));

        int closeHeight = Math.max(12, Math.min(16, footerHeight - 6));
        closeButton = addRenderableWidget(purpleButton(
                Component.literal("Close"), button -> onClose(),
                centerX() - 40, panelY + panelHeight - footerHeight + 3, 80, closeHeight));

        initContent();
        infoAcceptButton = addRenderableWidget(purpleButton(
                Component.literal("Accept"), button -> closeInfo(),
                centerX() - 40, panelY + panelHeight - footerHeight + 1, 80, closeHeight));
        infoScroll = Mth.clamp(infoScroll, 0, maxInfoScroll());
        updateInfoWidgetVisibility();
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
        crystalTintEnabled = config.crystalTintEnabled;
        frameColor = ColorState.fromArgb(KoHsCrystalTweaksConfig.getCrystalFrameTintArgb());
        coreColor = ColorState.fromArgb(KoHsCrystalTweaksConfig.getCrystalCoreTintArgb());
        crystalSpinSpeed = config.crystalSpinSpeed;
        crystalFlotationEnabled = config.crystalFlotationEnabled;
        staticCrystalEnabled = config.staticCrystalEnabled;
        safeCrystalEnabled = config.safeCrystalEnabled;
        visualCrystalPreviewEnabled = config.clientSideCrystalsEnabled;
        confirmedCrystalCleanupEnabled = config.confirmedCrystalCleanupEnabled;
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
            descriptions.remove(widget);
            removeWidget(widget);
        }
        contentWidgets.clear();
        initContent();
    }

    private void initContent() {
        switch (activeTab) {
            case VISUALS -> initVisualsTab();
            case TWEAKS -> initTweaksTab();
            case SOUND -> initSoundTab();
        }
    }

    private void initVisualsTab() {
        addContent(withTooltip(purpleButton(tintLabel(), button -> {
            crystalTintEnabled = !crystalTintEnabled;
            rebuildContent();
        }, centerX() - buttonWidth / 2, contentY, buttonWidth, controlHeight),
                "Applies separate client-side colors to the crystal frame and core."));

        if (!crystalTintEnabled) {
            return;
        }

        int layerY = contentY + rowStep();
        int layerWidth = Math.min(84, Math.max(1, (buttonWidth - 4) / 2));
        int layerStartX = centerX() - (layerWidth * 2 + 4) / 2;
        addContent(purpleButton(layerLabel(TintTarget.FRAME), button -> {
            activeTintTarget = TintTarget.FRAME;
            updateLayerLabels();
        }, layerStartX, layerY, layerWidth, controlHeight));
        addContent(purpleButton(layerLabel(TintTarget.CORE), button -> {
            activeTintTarget = TintTarget.CORE;
            updateLayerLabels();
        }, layerStartX + layerWidth + 4, layerY, layerWidth, controlHeight));
    }

    private void initTweaksTab() {
        int gap = 4;
        int columnWidth = Math.max(1, (contentWidth - gap) / 2);
        int leftX = contentX;
        int rightX = contentX + columnWidth + gap;

        addContent(withTooltip(purpleButton(safeCrystalLabel(), button -> {
            safeCrystalEnabled = !safeCrystalEnabled;
            button.setMessage(safeCrystalLabel());
        },
                leftX, contentY, columnWidth, controlHeight),
                "Blocks only accidental obsidian mining while an End Crystal is held."));

        addContent(withTooltip(purpleButton(visualCrystalPreviewLabel(), button -> {
            visualCrystalPreviewEnabled = !visualCrystalPreviewEnabled;
            CrystalPredictor.setEnabled(visualCrystalPreviewEnabled);
            button.setMessage(visualCrystalPreviewLabel());
        }, rightX, contentY, columnWidth, controlHeight),
                "Shows a non-targetable local preview after vanilla accepts a crystal placement."));

        addContent(withTooltip(purpleButton(confirmedCrystalCleanupLabel(), button ->
                        requestConfirmedCrystalCleanupToggle(),
                centerX() - buttonWidth / 2, contentY + rowStep(), buttonWidth, controlHeight),
                "Removes only the attacked server crystal from the local view after its vanilla attack packet is sent."));

        addContent(withTooltip(purpleButton(staticCrystalLabel(), button -> {
            staticCrystalEnabled = !staticCrystalEnabled;
            rebuildContent();
        }, centerX() - buttonWidth / 2, contentY + rowStep() * 2, buttonWidth, controlHeight),
                "Stops both crystal spin and flotation animation."));

        if (staticCrystalEnabled) {
            return;
        }

        addContent(withTooltip(purpleButton(flotationLabel(), button -> {
            crystalFlotationEnabled = !crystalFlotationEnabled;
            button.setMessage(flotationLabel());
        }, leftX, contentY + rowStep() * 3, columnWidth, controlHeight),
                "Enables or disables the vanilla crystal bobbing motion."));

        PercentSlider spinSlider = new PercentSlider(
                rightX,
                contentY + rowStep() * 3,
                columnWidth,
                controlHeight,
                "Spin Speed",
                0.0,
                3.0,
                crystalSpinSpeed,
                value -> crystalSpinSpeed = value.floatValue());
        addContent(withTooltip(spinSlider, "Adjusts crystal rotation speed without changing gameplay timing."));
    }

    private void requestConfirmedCrystalCleanupToggle() {
        Minecraft client = Minecraft.getInstance();
        if (confirmedCrystalCleanupEnabled) {
            confirmedCrystalCleanupEnabled = false;
            rebuildContent();
            return;
        }

        client.gui.setScreen(new ConfirmScreen(
                accepted -> {
                    confirmedCrystalCleanupEnabled = accepted;
                    client.gui.setScreen(this);
                    rebuildContent();
                },
                Component.literal("Enable Confirmed Crystal Cleanup?"),
                Component.literal(
                        "English: This changes only local rendering after a real vanilla crystal attack. "
                                + "Some servers forbid every client-side cleanup feature; check their rules.\n\n"
                                + "Español: Esto solo cambia el renderizado local después de un ataque vanilla real. "
                                + "Algunos servidores prohíben toda limpieza local; revisa sus reglas."),
                Component.literal("Continue"),
                Component.literal("Cancel")));
    }

    private void initSoundTab() {
        addContent(withTooltip(purpleButton(soundToggleLabel(), button -> {
            customSoundEnabled = !customSoundEnabled;
            rebuildContent();
        }, centerX() - buttonWidth / 2, contentY, buttonWidth, controlHeight),
                "Replaces End Crystal explosion audio locally with the selected file."));

        if (!customSoundEnabled) {
            return;
        }

        addContent(withTooltip(purpleButton(Component.literal("Select Sound File..."), button -> openFilePicker(),
                centerX() - buttonWidth / 2, contentY + rowStep(), buttonWidth, controlHeight),
                "Imports a WAV, OGG, or MP3 file into the KoHs config folder."));
        PercentSlider volumeSlider = new PercentSlider(
                centerX() - buttonWidth / 2,
                contentY + rowStep() * 2,
                buttonWidth,
                controlHeight,
                "Volume",
                0.0,
                2.0,
                soundVolume,
                value -> soundVolume = value.floatValue());
        addContent(withTooltip(volumeSlider, "Adjusts only the replacement crystal explosion volume."));
        PercentSlider speedSlider = new PercentSlider(
                centerX() - buttonWidth / 2,
                contentY + rowStep() * 3,
                buttonWidth,
                controlHeight,
                "Speed",
                0.5,
                2.0,
                soundSpeed,
                value -> soundSpeed = value.floatValue());
        addContent(withTooltip(speedSlider, "Adjusts playback speed and pitch for the replacement sound."));
    }

    private void confirmDisable(String optionName, String english, String spanish, Runnable disableAction) {
        Minecraft client = Minecraft.getInstance();
        client.gui.setScreen(new ConfirmScreen(
                accepted -> {
                    if (accepted) {
                        disableAction.run();
                    }
                    client.gui.setScreen(this);
                    rebuildContent();
                },
                Component.literal("Disable " + optionName + "?"),
                Component.literal("English: " + english + "\n\nEspañol: " + spanish),
                Component.literal("Accept"),
                Component.literal("Restore")));
    }

    private <T extends AbstractWidget> T withTooltip(T widget, String description) {
        descriptions.put(widget, description);
        return widget;
    }

    private static Button purpleButton(
            Component message,
            Button.OnPress onPress,
            int x,
            int y,
            int width,
            int height) {
        return new PurpleButton(x, y, width, height, message, onPress);
    }

    private void addContent(AbstractWidget widget) {
        contentWidgets.add(widget);
        addRenderableWidget(widget);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        float visibility = animationVisibility();
        graphics.fill(0, 0, width, height, withAlpha(0x000000, 0.60F * visibility));
        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight,
                withAlpha(0x1A0A2E, 0.88F * visibility));
        graphics.outline(panelX, panelY, panelWidth, panelHeight,
                withAlpha(0xB86BFF, 0.88F * visibility));

        int contentBorderY = contentY - 4;
        int contentBorderHeight = Math.max(1, panelY + panelHeight - footerHeight - contentBorderY);
        graphics.outline(panelX + 4, contentBorderY, panelWidth - 8, contentBorderHeight,
                withAlpha(0xB86BFF, 0.38F * visibility));
        if (infoOpen) {
            extractInfoBackground(graphics, visibility);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        float visibility = animationVisibility();
        updateWidgetAlpha(visibility);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        if (infoOpen) {
            graphics.nextStratum();
            extractInfoContent(graphics, visibility);
            return;
        }

        graphics.centeredText(font, TITLE, centerX(), panelY + 5, withAlpha(0xEAD1FF, visibility));
        if (!compact) {
            graphics.centeredText(font, SUBTITLE, centerX(), panelY + 17, withAlpha(0xDDA6FF, visibility));
        }
        drawActiveTabUnderline(graphics);

        switch (activeTab) {
            case VISUALS -> extractVisualsContent(graphics);
            case TWEAKS -> extractTweaksContent(graphics);
            case SOUND -> extractSoundContent(graphics);
        }

        graphics.nextStratum();
        extractAnimatedDescription(graphics, mouseX, mouseY, visibility);
    }

    private float animationVisibility() {
        long now = System.nanoTime();
        float elapsed = Mth.clamp((now - enterAnimationStartNanos) / (float) ENTER_ANIMATION_NANOS, 0.0F, 1.0F);
        float remaining = 1.0F - elapsed;
        return 1.0F - remaining * remaining * remaining;
    }

    private void updateWidgetAlpha(float visibility) {
        if (tabVisuals != null) tabVisuals.setAlpha(visibility);
        if (tabTweaks != null) tabTweaks.setAlpha(visibility);
        if (tabSound != null) tabSound.setAlpha(visibility);
        if (tabInfo != null) tabInfo.setAlpha(visibility);
        if (closeButton != null) closeButton.setAlpha(visibility);
        if (infoAcceptButton != null) infoAcceptButton.setAlpha(visibility * infoVisibility());
        for (AbstractWidget widget : contentWidgets) {
            widget.setAlpha(visibility);
        }
    }

    private void extractAnimatedDescription(
            GuiGraphicsExtractor graphics,
            int mouseX,
            int mouseY,
            float screenVisibility) {
        if (infoOpen) {
            visibleDescription = null;
            return;
        }

        String hovered = null;
        for (Map.Entry<AbstractWidget, String> entry : descriptions.entrySet()) {
            AbstractWidget widget = entry.getKey();
            if (widget.visible && widget.active && widget.isMouseOver(mouseX, mouseY)) {
                hovered = entry.getValue();
                break;
            }
        }

        if (hovered == null) {
            visibleDescription = null;
            return;
        }
        if (!hovered.equals(visibleDescription)) {
            visibleDescription = hovered;
            descriptionAnimationStartNanos = System.nanoTime();
        }

        float progress = Mth.clamp(
                (System.nanoTime() - descriptionAnimationStartNanos) / (float) DESCRIPTION_ANIMATION_NANOS,
                0.0F,
                1.0F);
        progress = smoothStep(progress) * screenVisibility;
        int maxTextWidth = Math.max(80, Math.min(230, width - 26));
        List<FormattedCharSequence> lines = font.split(Component.literal(visibleDescription), maxTextWidth);
        int textWidth = 0;
        for (FormattedCharSequence line : lines) {
            textWidth = Math.max(textWidth, font.width(line));
        }

        int boxWidth = textWidth + 10;
        int boxHeight = lines.size() * 9 + 8;
        int boxX = Mth.clamp(mouseX + 11, 6, Math.max(6, width - boxWidth - 6));
        int preferredY = mouseY + 13;
        int boxY = preferredY + boxHeight <= height - 6 ? preferredY : mouseY - boxHeight - 8;
        boxY = Mth.clamp(boxY + Math.round((1.0F - progress) * 4.0F), 6, Math.max(6, height - boxHeight - 6));

        graphics.fill(boxX, boxY, boxX + boxWidth, boxY + boxHeight,
                withAlpha(0x170B24, 0.94F * progress));
        graphics.outline(boxX, boxY, boxWidth, boxHeight,
                withAlpha(0xC47CFF, progress));
        int textY = boxY + 4;
        for (FormattedCharSequence line : lines) {
            graphics.text(font, line, boxX + 5, textY, withAlpha(0xF5E9FF, progress), true);
            textY += 9;
        }
    }

    private void openInfo() {
        infoOpen = true;
        infoRevealAll = false;
        infoScroll = 0;
        infoAnimationStartNanos = System.nanoTime();
        infoTextStartNanos = infoAnimationStartNanos;
        visibleDescription = null;
        updateInfoWidgetVisibility();
    }

    private void closeInfo() {
        infoOpen = false;
        updateInfoWidgetVisibility();
    }

    private void updateInfoWidgetVisibility() {
        boolean mainVisible = !infoOpen;
        if (tabVisuals != null) tabVisuals.visible = mainVisible;
        if (tabTweaks != null) tabTweaks.visible = mainVisible;
        if (tabSound != null) tabSound.visible = mainVisible;
        if (tabInfo != null) tabInfo.visible = mainVisible;
        if (closeButton != null) closeButton.visible = mainVisible;
        for (AbstractWidget widget : contentWidgets) widget.visible = mainVisible;
        if (infoAcceptButton != null) infoAcceptButton.visible = infoOpen;
    }

    private float infoVisibility() {
        if (!infoOpen) return 0.0F;
        float elapsed = Mth.clamp(
                (System.nanoTime() - infoAnimationStartNanos) / (float) ENTER_ANIMATION_NANOS,
                0.0F,
                1.0F);
        return smoothStep(elapsed);
    }

    private int revealedInfoCharacters() {
        if (infoRevealAll) return INFO_TEXT.length();
        long elapsed = Math.max(0L, System.nanoTime() - infoTextStartNanos);
        return Math.min(INFO_TEXT.length(), (int) (elapsed / INFO_CHARACTER_NANOS));
    }

    private boolean infoTextComplete() {
        return revealedInfoCharacters() >= INFO_TEXT.length();
    }

    private int infoCodeX() { return panelX + 10; }
    private int infoCodeY() { return panelY + (compact ? 27 : 32); }
    private int infoCodeWidth() { return Math.max(24, panelWidth - 20); }
    private int infoCodeHeight() { return Math.max(18, panelHeight - (compact ? 52 : 60)); }

    private void extractInfoBackground(GuiGraphicsExtractor graphics, float screenVisibility) {
        float visibility = screenVisibility * infoVisibility();
        int x = panelX + 5;
        int y = panelY + 5;
        int width = panelWidth - 10;
        int height = panelHeight - 10;
        graphics.fill(panelX + 1, panelY + 1, panelX + panelWidth - 1, panelY + panelHeight - 1,
                withAlpha(0x08030D, 0.82F * visibility));
        graphics.fill(x, y, x + width, y + height, withAlpha(0x170B24, 0.98F * visibility));
        graphics.outline(x, y, width, height, withAlpha(0xC47CFF, visibility));
        graphics.fill(infoCodeX(), infoCodeY(), infoCodeX() + infoCodeWidth(), infoCodeY() + infoCodeHeight(),
                withAlpha(0x0D1117, 0.98F * visibility));
        graphics.outline(infoCodeX(), infoCodeY(), infoCodeWidth(), infoCodeHeight(),
                withAlpha(0x663A7A, visibility));
    }

    private void extractInfoContent(GuiGraphicsExtractor graphics, float screenVisibility) {
        float visibility = screenVisibility * infoVisibility();
        graphics.centeredText(font, Component.literal("Info Legit"), centerX(), panelY + 9,
                withAlpha(0xEAD1FF, visibility));
        if (!compact) {
            graphics.centeredText(font,
                    Component.literal(infoTextComplete() ? "Esc: close" : "Esc: reveal all"),
                    centerX(), panelY + 20, withAlpha(0x9DA5B4, visibility));
        }

        int codeX = infoCodeX();
        int codeY = infoCodeY();
        int codeWidth = infoCodeWidth();
        int codeHeight = infoCodeHeight();
        int textX = codeX + 28;
        int textWidth = Math.max(8, codeWidth - 35);
        String visibleText = INFO_TEXT.substring(0, revealedInfoCharacters());
        String[] logicalLines = visibleText.split("\\n", -1);
        int renderedLine = 0;

        graphics.enableScissor(codeX + 1, codeY + 1, codeX + codeWidth - 1, codeY + codeHeight - 1);
        for (int lineNumber = 0; lineNumber < logicalLines.length; lineNumber++) {
            String logicalLine = logicalLines[lineNumber];
            List<FormattedCharSequence> wrapped = font.split(
                    Component.literal(logicalLine.isEmpty() ? " " : logicalLine), textWidth);
            if (wrapped.isEmpty()) wrapped = List.of(Component.literal(" ").getVisualOrderText());
            int color = infoLineColor(logicalLine);
            for (int segment = 0; segment < wrapped.size(); segment++) {
                int drawY = codeY + 4 + renderedLine * 9 - infoScroll;
                if (drawY >= codeY - 8 && drawY < codeY + codeHeight) {
                    if (segment == 0) {
                        graphics.text(font, String.format("%02d", lineNumber + 1), codeX + 5, drawY,
                                withAlpha(0x6E7681, visibility), true);
                    }
                    graphics.text(font, wrapped.get(segment), textX, drawY,
                            withAlpha(color, visibility), true);
                }
                renderedLine++;
            }
        }
        graphics.disableScissor();
        extractInfoScrollbar(graphics, visibility);
    }

    private int infoLineColor(String line) {
        String trimmed = line.stripLeading();
        if (trimmed.startsWith("//")) return 0x6A9955;
        if (trimmed.startsWith("[")) return 0x569CD6;
        if (trimmed.contains("Mixin")) return 0xC586C0;
        if (trimmed.contains("=")) return 0xDCDCAA;
        if (trimmed.endsWith("{") || trimmed.equals("}")) return 0xCE9178;
        return 0xD4D4D4;
    }

    private int totalInfoTextHeight() {
        int textWidth = Math.max(8, infoCodeWidth() - 35);
        int lines = 0;
        for (String line : INFO_TEXT.split("\\n", -1)) {
            lines += Math.max(1, font.split(
                    Component.literal(line.isEmpty() ? " " : line), textWidth).size());
        }
        return lines * 9 + 8;
    }

    private int maxInfoScroll() {
        if (font == null) return 0;
        return Math.max(0, totalInfoTextHeight() - infoCodeHeight());
    }

    private void extractInfoScrollbar(GuiGraphicsExtractor graphics, float visibility) {
        int maxScroll = maxInfoScroll();
        if (maxScroll <= 0) return;
        int x = infoCodeX() + infoCodeWidth() - 4;
        int y = infoCodeY() + 2;
        int height = infoCodeHeight() - 4;
        int total = totalInfoTextHeight();
        int thumbHeight = Math.max(10, height * height / Math.max(height, total));
        int thumbY = y + Math.round((height - thumbHeight) * (infoScroll / (float) maxScroll));
        graphics.fill(x, y, x + 2, y + height, withAlpha(0x2B1738, visibility));
        graphics.fill(x, thumbY, x + 2, thumbY + thumbHeight, withAlpha(0xC47CFF, visibility));
    }

    private static String buildVersion() {
        return FabricLoader.getInstance().getModContainer("kohs_crystal_tweaks")
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("development");
    }

    private static float smoothStep(float value) {
        float clamped = Mth.clamp(value, 0.0F, 1.0F);
        return clamped * clamped * (3.0F - 2.0F * clamped);
    }

    private static int withAlpha(int rgb, float alpha) {
        int alphaByte = Math.round(Mth.clamp(alpha, 0.0F, 1.0F) * 255.0F);
        return (alphaByte << 24) | (rgb & 0xFFFFFF);
    }

    private static int lerpRgb(int first, int second, float amount) {
        float clamped = Mth.clamp(amount, 0.0F, 1.0F);
        int red = Math.round(((first >> 16) & 0xFF) + (((second >> 16) & 0xFF) - ((first >> 16) & 0xFF)) * clamped);
        int green = Math.round(((first >> 8) & 0xFF) + (((second >> 8) & 0xFF) - ((first >> 8) & 0xFF)) * clamped);
        int blue = Math.round((first & 0xFF) + ((second & 0xFF) - (first & 0xFF)) * clamped);
        return (red << 16) | (green << 8) | blue;
    }

    private void drawActiveTabUnderline(GuiGraphicsExtractor graphics) {
        Button activeButton = switch (activeTab) {
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

    private void extractVisualsContent(GuiGraphicsExtractor graphics) {
        if (!crystalTintEnabled) {
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
        int markerTop = Mth.clamp(hueMarkerY - 1, y - 1, y + pickerHeight - 2);
        graphics.outline(hueX - 2, markerTop, 12, 3, 0xFFFFFFFF);
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
    }

    private void extractSoundContent(GuiGraphicsExtractor graphics) {
        if (!customSoundEnabled) {
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
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        if (infoOpen && event.key() == 256) {
            if (!infoTextComplete()) {
                infoRevealAll = true;
            } else {
                closeInfo();
            }
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (infoOpen
                && mouseX >= infoCodeX() && mouseX < infoCodeX() + infoCodeWidth()
                && mouseY >= infoCodeY() && mouseY < infoCodeY() + infoCodeHeight()) {
            infoScroll = Mth.clamp(infoScroll - (int) Math.round(verticalAmount * 18.0), 0, maxInfoScroll());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (infoOpen) return super.mouseClicked(event, doubleClick);
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
        if (infoOpen) return super.mouseDragged(event, deltaX, deltaY);
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
        if (infoOpen) {
            closeInfo();
            return;
        }
        saveState();
        minecraft.gui.setScreen(parent);
    }

    private void saveState() {
        if (stateSaved) {
            return;
        }
        KoHsCrystalTweaksConfig config = KoHsCrystalTweaksConfig.get();
        config.crystalTintEnabled = crystalTintEnabled;
        config.crystalFrameTintHex = frameColor.toHex();
        config.crystalCoreTintHex = coreColor.toHex();
        config.crystalSpinSpeed = crystalSpinSpeed;
        config.crystalFlotationEnabled = crystalFlotationEnabled;
        config.staticCrystalEnabled = staticCrystalEnabled;
        config.safeCrystalEnabled = safeCrystalEnabled;
        config.clientSideCrystalsEnabled = visualCrystalPreviewEnabled;
        config.seamlessEnabled = visualCrystalPreviewEnabled;
        config.confirmedCrystalCleanupEnabled = confirmedCrystalCleanupEnabled;
        config.customSoundEnabled = customSoundEnabled;
        config.customSoundFileName = selectedSoundFileName;
        config.soundVolume = soundVolume;
        config.soundSpeed = soundSpeed;
        KoHsCrystalTweaksConfig.save();
        CrystalPredictor.setEnabled(visualCrystalPreviewEnabled);
        CrystalSoundManager.reloadFromConfig();
        stateSaved = true;
    }

    private Component tintLabel() {
        return toggleLabel("Crystal Tint", crystalTintEnabled);
    }

    private Component staticCrystalLabel() {
        return toggleLabel("Static Crystal", staticCrystalEnabled);
    }

    private Component safeCrystalLabel() {
        return toggleLabel("Safe Crystal", safeCrystalEnabled);
    }

    private Component visualCrystalPreviewLabel() {
        return toggleLabel("Visual Preview", visualCrystalPreviewEnabled);
    }

    private Component confirmedCrystalCleanupLabel() {
        return toggleLabel("Confirmed Cleanup", confirmedCrystalCleanupEnabled);
    }

    private Component flotationLabel() {
        return toggleLabel("Flotation", crystalFlotationEnabled);
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

    private static final class PurpleButton extends Button {
        private long lastFrameNanos;
        private float hoverProgress;

        private PurpleButton(
                int x,
                int y,
                int width,
                int height,
                Component message,
                OnPress onPress) {
            super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            hoverProgress = animateHover(hoverProgress, isHoveredOrFocused(), this);
            int fill = active
                    ? lerpRgb(0x54206F, 0x8F45C4, hoverProgress)
                    : 0x2D1B35;
            int outline = lerpRgb(0x9C5DCA, 0xE0A5FF, hoverProgress);
            graphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(),
                    withAlpha(fill, 0.94F * getAlpha()));
            graphics.outline(getX(), getY(), getWidth(), getHeight(),
                    withAlpha(outline, getAlpha()));
            extractDefaultLabel(graphics.textRendererForWidget(this, GuiGraphicsExtractor.HoveredTextEffects.NONE));
        }

        private static float animateHover(float current, boolean hovered, PurpleButton owner) {
            long now = System.nanoTime();
            if (owner.lastFrameNanos == 0L) {
                owner.lastFrameNanos = now;
                return hovered ? 1.0F : current;
            }
            float step = Mth.clamp((now - owner.lastFrameNanos) / 95_000_000.0F, 0.0F, 1.0F);
            owner.lastFrameNanos = now;
            float target = hovered ? 1.0F : 0.0F;
            return Mth.lerp(smoothStep(step), current, target);
        }
    }

    private static final class PercentSlider extends AbstractSliderButton {
        private final String label;
        private final double minimum;
        private final double maximum;
        private final java.util.function.Consumer<Double> onChange;
        private long lastFrameNanos;
        private float hoverProgress;

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
        public void extractWidgetRenderState(
                GuiGraphicsExtractor graphics,
                int mouseX,
                int mouseY,
                float partialTick) {
            long now = System.nanoTime();
            if (lastFrameNanos == 0L) {
                lastFrameNanos = now;
                hoverProgress = isHoveredOrFocused() ? 1.0F : 0.0F;
            } else {
                float step = Mth.clamp((now - lastFrameNanos) / 95_000_000.0F, 0.0F, 1.0F);
                lastFrameNanos = now;
                hoverProgress = Mth.lerp(
                        smoothStep(step),
                        hoverProgress,
                        isHoveredOrFocused() ? 1.0F : 0.0F);
            }

            int fill = lerpRgb(0x351542, 0x54206F, hoverProgress);
            int outline = lerpRgb(0x8B50B5, 0xD493FF, hoverProgress);
            graphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(),
                    withAlpha(fill, 0.94F * getAlpha()));
            graphics.outline(getX(), getY(), getWidth(), getHeight(),
                    withAlpha(outline, getAlpha()));

            int handleX = getX() + (int) Math.round(value * Math.max(0, getWidth() - 8));
            graphics.fill(handleX, getY() + 1, handleX + 8, getY() + getHeight() - 1,
                    withAlpha(lerpRgb(0xA95ADB, 0xE1ABFF, hoverProgress), getAlpha()));
            extractScrollingStringOverContents(
                    graphics.textRendererForWidget(this, GuiGraphicsExtractor.HoveredTextEffects.NONE),
                    getMessage(),
                    2);
            handleCursor(graphics);
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
