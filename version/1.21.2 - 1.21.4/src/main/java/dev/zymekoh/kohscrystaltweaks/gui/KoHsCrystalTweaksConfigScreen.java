package dev.zymekoh.kohscrystaltweaks.gui;

import dev.zymekoh.kohscrystaltweaks.config.KoHsCrystalTweaksConfig;
import dev.zymekoh.kohscrystaltweaks.core.CrystalPredictor;
import dev.zymekoh.kohscrystaltweaks.sound.CrystalSoundManager;
import net.fabricmc.loader.api.FabricLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;
import net.minecraft.text.OrderedText;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

public final class KoHsCrystalTweaksConfigScreen extends Screen {
    private static final long ENTER_ANIMATION_NANOS = 220_000_000L;
    private static final long DESCRIPTION_ANIMATION_NANOS = 120_000_000L;
    private static final long INFO_CHARACTER_NANOS = 100_000_000L;

    // ── Panel ──
    private static final int DEFAULT_PANEL_W = 360;
    private static final int DEFAULT_PANEL_H = 240;

    // ── Tab bar ──
    private static final int TAB_H = 16;
    private static final int TAB_GAP = 4;
    private static final int TAB_Y_OFFSET = 36;

    // ── Content area ──
    private static final int CONTENT_TOP = 56;
    private static final int CONTENT_PAD = 14;

    // ── Button sizes ──
    private static final int BTN_H = 16;
    private static final int ROW_SPACING = 28;

    // ── Color picker ──
    private static final int PICKER_W = 100;
    private static final int PICKER_H = 50;
    private static final int HUE_BAR_W = 8;
    private static final int HUE_GAP = 4;
    private static final int LAYER_BTN_W = 80;
    private static final int SWATCH_W = 110;
    private static final int SWATCH_H = 24;

    // ── Texts ──
    private static final Text TITLE    = Text.literal("KoHs Crystal Tweaks");
    private static final Text SUBTITLE = Text.literal("ZymeKoHs (" + buildVersion() + ")");
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

    // ── State ──
    private final Screen parent;
    private Tab activeTab = Tab.TWEAKS;
    private final List<ClickableWidget> contentWidgets = new ArrayList<>();
    private final Map<ClickableWidget, String> descriptions = new IdentityHashMap<>();
    private final Map<ClickableWidget, Float> hoverProgress = new IdentityHashMap<>();
    private int panelW;
    private int panelH;
    private int tabW;
    private int buttonW;
    private int contentTop;
    private int rowSpacing;
    private int tabYOffset;
    private boolean compact;

    // Tab buttons (persistent)
    private ButtonWidget tabVisualBtn, tabTweaksBtn, tabSoundBtn, tabInfoBtn, closeBtn, infoAcceptBtn;
    private long enterAnimationStartNanos;
    private long descriptionAnimationStartNanos;
    private long infoAnimationStartNanos;
    private long infoTextStartNanos;
    private long lastRenderNanos;
    private String visibleDescription;
    private boolean infoOpen;
    private boolean infoRevealAll;
    private int infoScroll;
    private boolean stateSaved;

    // Visuals tab state
    private boolean crystalTintEnabled;
    private boolean draggingPicker, draggingHue;
    private TintTarget activeTarget = TintTarget.FRAME;
    private ColorState frameColorState, coreColorState;

    // Tweaks tab state
    private float crystalSpinSpeed;
    private boolean crystalFlotationEnabled;
    private boolean staticCrystalEnabled;
    private boolean safeCrystalEnabled;
    private boolean visualCrystalPreviewEnabled;
    private boolean confirmedCrystalCleanupEnabled;

    // Sound tab state
    private boolean customSoundEnabled;
    private float soundVolume;
    private float soundSpeed;
    private String soundStatus = "";

    public KoHsCrystalTweaksConfigScreen(Screen parent) {
        super(TITLE);
        this.parent = parent;
    }

    // ══════════════════════════════════════════════════════════════════
    //  Layout
    // ══════════════════════════════════════════════════════════════════

    private int px() { return (width - panelW) / 2; }
    private int py() { return (height - panelH) / 2; }
    private int contentX() { return px() + CONTENT_PAD; }
    private int contentY() { return py() + contentTop; }
    private int contentW() { return panelW - CONTENT_PAD * 2; }
    private int cx() { return width / 2; }

    private void computeLayout() {
        panelW = Math.max(1, Math.min(DEFAULT_PANEL_W, width - 12));
        panelH = Math.max(1, Math.min(DEFAULT_PANEL_H, height - 12));
        compact = panelH < 220 || panelW < 320;
        contentTop = compact ? 48 : CONTENT_TOP;
        rowSpacing = compact ? 20 : ROW_SPACING;
        tabYOffset = compact ? 28 : TAB_Y_OFFSET;
        buttonW = Math.max(1, Math.min(180, contentW()));
        tabW = Math.max(1, (panelW - 8 - TAB_GAP * 3) / 4);
    }

    // ══════════════════════════════════════════════════════════════════
    //  Init
    // ══════════════════════════════════════════════════════════════════

    @Override
    protected void init() {
        if (enterAnimationStartNanos == 0L) {
            enterAnimationStartNanos = System.nanoTime();
        }
        computeLayout();
        contentWidgets.clear();
        descriptions.clear();
        hoverProgress.clear();
        visibleDescription = null;
        draggingPicker = false;
        draggingHue = false;
        // Load state from config on first init
        KoHsCrystalTweaksConfig cfg = KoHsCrystalTweaksConfig.get();
        if (frameColorState == null) {
            crystalTintEnabled = cfg.crystalTintEnabled;
            frameColorState = ColorState.fromArgb(KoHsCrystalTweaksConfig.getCrystalFrameTintArgb());
            coreColorState  = ColorState.fromArgb(KoHsCrystalTweaksConfig.getCrystalCoreTintArgb());
            crystalSpinSpeed = cfg.crystalSpinSpeed;
            crystalFlotationEnabled = cfg.crystalFlotationEnabled;
            staticCrystalEnabled = cfg.staticCrystalEnabled;
            safeCrystalEnabled = cfg.safeCrystalEnabled;
            visualCrystalPreviewEnabled = cfg.clientSideCrystalsEnabled;
            confirmedCrystalCleanupEnabled = cfg.confirmedCrystalCleanupEnabled;
            customSoundEnabled = cfg.customSoundEnabled;
            soundVolume = cfg.soundVolume;
            soundSpeed = cfg.soundSpeed;
            refreshSoundStatus();
        }

        // ── Tab bar (always visible) ──
        int tabTotalW = tabW * 4 + TAB_GAP * 3;
        int tabStartX = cx() - tabTotalW / 2;
        int tabY = py() + tabYOffset;

        tabVisualBtn = addDrawableChild(ButtonWidget.builder(Text.literal("Visuals"), b -> switchTab(Tab.VISUALS))
                .dimensions(tabStartX, tabY, tabW, TAB_H).build());
        tabTweaksBtn = addDrawableChild(ButtonWidget.builder(Text.literal("Tweaks"), b -> switchTab(Tab.TWEAKS))
                .dimensions(tabStartX + tabW + TAB_GAP, tabY, tabW, TAB_H).build());
        tabSoundBtn = addDrawableChild(ButtonWidget.builder(Text.literal("Sound"), b -> switchTab(Tab.SOUND))
                .dimensions(tabStartX + (tabW + TAB_GAP) * 2, tabY, tabW, TAB_H).build());
        tabInfoBtn = addDrawableChild(ButtonWidget.builder(Text.literal("Info Legit"), b -> openInfo())
                .dimensions(tabStartX + (tabW + TAB_GAP) * 3, tabY, tabW, TAB_H).build());

        // ── Close (always visible) ──
        closeBtn = addDrawableChild(ButtonWidget.builder(Text.literal("Close"), b -> close())
                .dimensions(cx() - 40, py() + panelH - 22, 80, BTN_H).build());

        // Add content for active tab
        initContent();
        infoAcceptBtn = addDrawableChild(ButtonWidget.builder(Text.literal("Accept"), b -> closeInfo())
                .dimensions(cx() - 40, py() + panelH - 24, 80, BTN_H).build());
        infoScroll = MathHelper.clamp(infoScroll, 0, maxInfoScroll());
        updateInfoWidgetVisibility();
    }

    private void rebuildTab() {
        for (ClickableWidget w : contentWidgets) {
            descriptions.remove(w);
            hoverProgress.remove(w);
            remove(w);
        }
        contentWidgets.clear();
        initContent();
    }

    private void switchTab(Tab tab) {
        if (activeTab == tab) return;
        activeTab = tab;
        rebuildTab();
    }

    // ══════════════════════════════════════════════════════════════════
    //  Tab content initialisation
    // ══════════════════════════════════════════════════════════════════

    private void initContent() {
        switch (activeTab) {
            case VISUALS -> initVisualsTab();
            case TWEAKS -> initTweaksTab();
            case SOUND -> initSoundTab();
        }
    }

    private void initVisualsTab() {
        int y = contentY();

        // Tint toggle — rebuilds content on click
        addContent(withTooltip(ButtonWidget.builder(tintLabel(), b -> requestCrystalTintToggle())
                .dimensions(cx() - buttonW / 2, y, buttonW, BTN_H).build(),
                "Applies separate custom colors to the crystal frame and core."));

        if (!crystalTintEnabled) return;

        // Layer buttons (only when ON)
        int lx = contentX();
        int layerButtonW = Math.max(1, Math.min(LAYER_BTN_W, (contentW() - 4) / 2));
        ButtonWidget outerBtn = ButtonWidget.builder(Text.literal(""), b -> {
            activeTarget = TintTarget.FRAME;
            updateLayerTexts();
        }).dimensions(lx, y + 22, layerButtonW, BTN_H).build();
        addContent(withTooltip(outerBtn, "Selects the crystal frame color for editing."));

        ButtonWidget innerBtn = ButtonWidget.builder(Text.literal(""), b -> {
            activeTarget = TintTarget.CORE;
            updateLayerTexts();
        }).dimensions(lx + layerButtonW + 4, y + 22, layerButtonW, BTN_H).build();
        addContent(withTooltip(innerBtn, "Selects the crystal core color for editing."));

        updateLayerTexts();
    }

    private void initTweaksTab() {
        int y = contentY();

        addContent(withTooltip(ButtonWidget.builder(safeCrystalLabel(), b -> requestSafeCrystalToggle())
                .dimensions(tweaksX(0), tweaksY(y, 0), tweaksControlW(), BTN_H).build(),
                "Blocks only accidental obsidian mining while an End Crystal is held."));

        addContent(withTooltip(ButtonWidget.builder(visualCrystalPreviewLabel(), b -> requestVisualCrystalPreviewToggle())
                .dimensions(tweaksX(1), tweaksY(y, 1), tweaksControlW(), BTN_H).build(),
                "Shows a non-targetable local preview after vanilla accepts a crystal placement."));

        addContent(withTooltip(ButtonWidget.builder(confirmedCrystalCleanupLabel(), b -> requestConfirmedCrystalCleanupToggle())
                .dimensions(tweaksX(2), tweaksY(y, 2), tweaksControlW(), BTN_H).build(),
                "Removes only the attacked server crystal from the local view after its vanilla attack packet is sent."));

        addContent(withTooltip(ButtonWidget.builder(staticCrystalLabel(), b -> requestStaticCrystalToggle())
                .dimensions(tweaksX(3), tweaksY(y, 3), tweaksControlW(), BTN_H).build(),
                "Keeps crystals completely still with no spin or floating animation."));

        if (staticCrystalEnabled) return;

        addContent(withTooltip(ButtonWidget.builder(crystalFlotationLabel(), b -> requestCrystalFlotationToggle())
                .dimensions(tweaksX(4), tweaksY(y, 4), tweaksControlW(), BTN_H).build(),
                "Enables or disables the crystal floating animation."));

        addContent(withTooltip(new PercentSlider(tweaksX(5), tweaksY(y, 5), tweaksControlW(), BTN_H,
                "Spin Speed", 0.0, 3.0, crystalSpinSpeed, v -> crystalSpinSpeed = v.floatValue()),
                "Controls crystal rotation speed from stopped to 300%."));
    }

    private boolean tweaksUseTwoColumns() {
        return compact && contentW() >= 220;
    }

    private int tweaksControlW() {
        return tweaksUseTwoColumns() ? Math.max(1, (contentW() - 4) / 2) : buttonW;
    }

    private int tweaksX(int index) {
        if (!tweaksUseTwoColumns()) {
            return cx() - buttonW / 2;
        }
        return contentX() + (index % 2) * (tweaksControlW() + 4);
    }

    private int tweaksY(int startY, int index) {
        int row = tweaksUseTwoColumns() ? index / 2 : index;
        return startY + rowSpacing * row;
    }

    private void initSoundTab() {
        int y = contentY();

        // Custom Sound toggle — rebuilds content on click
        addContent(withTooltip(ButtonWidget.builder(soundToggleLabel(), b -> requestCustomSoundToggle())
                .dimensions(cx() - buttonW / 2, y, buttonW, BTN_H).build(),
                "Replaces the default crystal explosion sound through the vanilla sound pipeline."));

        if (!customSoundEnabled) return;

        // Select file button (only when ON)
        addContent(withTooltip(ButtonWidget.builder(Text.literal("Select Sound File..."), b -> openFilePicker())
                .dimensions(cx() - buttonW / 2, y + 20, buttonW, BTN_H).build(),
                "Imports a WAV, OGG or MP3 file for crystal explosions."));

        // Volume slider
        addContent(withTooltip(new PercentSlider(cx() - buttonW / 2, y + 42, buttonW, BTN_H,
                "Volume", 0.0, 2.0, soundVolume, v -> soundVolume = v.floatValue()),
                "Controls the custom explosion sound volume."));

        // Speed slider
        addContent(withTooltip(new PercentSlider(cx() - buttonW / 2, y + 64, buttonW, BTN_H,
                "Speed", 0.5, 2.0, soundSpeed, v -> soundSpeed = v.floatValue()),
                "Controls custom sound playback speed from 50% to 200%."));
    }

    private <T extends ClickableWidget> T withTooltip(T widget, String description) {
        descriptions.put(widget, description);
        return widget;
    }

    private void requestVisualCrystalPreviewToggle() {
        visualCrystalPreviewEnabled = !visualCrystalPreviewEnabled;
        CrystalPredictor.setEnabled(visualCrystalPreviewEnabled);
        rebuildTab();
    }

    private void requestCrystalTintToggle() {
        crystalTintEnabled = !crystalTintEnabled;
        rebuildTab();
    }

    private void requestConfirmedCrystalCleanupToggle() {
        if (confirmedCrystalCleanupEnabled) {
            confirmedCrystalCleanupEnabled = false;
            rebuildTab();
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        client.setScreen(new ConfirmScreen(
                accepted -> {
                    confirmedCrystalCleanupEnabled = accepted;
                    client.setScreen(this);
                    rebuildTab();
                },
                Text.literal("Enable Confirmed Crystal Cleanup?"),
                Text.literal(
                        "English: This changes only local rendering after a real vanilla crystal attack. "
                                + "Some servers forbid every client-side cleanup feature; check their rules.\n\n"
                                + "Español: Esto solo cambia el renderizado local después de un ataque vanilla real. "
                                + "Algunos servidores prohíben toda limpieza local; revisa sus reglas."),
                Text.literal("Continue"),
                Text.literal("Cancel")));
    }

    private void requestStaticCrystalToggle() {
        staticCrystalEnabled = !staticCrystalEnabled;
        rebuildTab();
    }

    private void requestSafeCrystalToggle() {
        safeCrystalEnabled = !safeCrystalEnabled;
        KoHsCrystalTweaksConfig.get().safeCrystalEnabled = safeCrystalEnabled;
        KoHsCrystalTweaksConfig.save();
        rebuildTab();
    }

    private void requestCrystalFlotationToggle() {
        crystalFlotationEnabled = !crystalFlotationEnabled;
        rebuildTab();
    }

    private void requestCustomSoundToggle() {
        customSoundEnabled = !customSoundEnabled;
        rebuildTab();
    }

    private void confirmDisable(String optionName, String english, String spanish, Runnable disableAction) {
        MinecraftClient client = MinecraftClient.getInstance();
        client.setScreen(new ConfirmScreen(
                accepted -> {
                    if (accepted) {
                        disableAction.run();
                    }
                    client.setScreen(this);
                    rebuildTab();
                },
                Text.literal("Disable " + optionName + "?"),
                Text.literal("English: " + english + "\n\nEspañol: " + spanish),
                Text.literal("Accept"),
                Text.literal("Restore")));
    }

    private void addContent(ClickableWidget widget) {
        contentWidgets.add(widget);
        addDrawableChild(widget);
    }

    // ══════════════════════════════════════════════════════════════════
    //  Render
    // ══════════════════════════════════════════════════════════════════

    @Override
    public void renderBackground(DrawContext ctx, int mx, int my, float delta) {
        float visibility = animationVisibility();
        // Manually draw dark background to prevent 1.21.2+ vanilla blur
        ctx.fill(0, 0, this.width, this.height, withAlpha(0x000000, 0.54F * visibility));
        int p = py(), pxl = px();
        // Panel bg + border
        ctx.fill(pxl, p, pxl + panelW, p + panelH, withAlpha(0x1A0A2E, 0.88F * visibility));
        kctDrawBorder(ctx, pxl, p, panelW, panelH, withAlpha(0xB86BFF, 0.88F * visibility));

        // Content-area border
        int cy = py() + contentTop - 4;
        kctDrawBorder(ctx, pxl + 4, cy, Math.max(1, panelW - 8),
                Math.max(1, panelH - contentTop - 22), withAlpha(0xB86BFF, 0.38F * visibility));
        if (infoOpen) {
            renderInfoBackground(ctx, visibility);
        }
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        float visibility = animationVisibility();
        updateWidgetAlpha(visibility);
        super.render(ctx, mx, my, delta);
        drawPurpleWidgetStyles(ctx, mx, my, visibility);

        if (infoOpen) {
            renderInfoContent(ctx, visibility);
            return;
        }

        int p = py();
        
        // Title
        ctx.drawCenteredTextWithShadow(textRenderer, TITLE, cx(), p + 6, withAlpha(0xEAD1FF, visibility));
        if (!compact) {
            ctx.drawCenteredTextWithShadow(textRenderer, SUBTITLE, cx(), p + 18, withAlpha(0xDDA6FF, visibility));
        }

        // Tab highlight underline
        drawTabHighlight(ctx);

        // Tab-specific rendering
        switch (activeTab) {
            case VISUALS -> renderVisualsContent(ctx, mx, my);
            case SOUND -> renderSoundContent(ctx);
            case TWEAKS -> {
            }
        }
        renderAnimatedDescription(ctx, mx, my, visibility);
    }

    private float animationVisibility() {
        long now = System.nanoTime();
        float elapsed = MathHelper.clamp(
                (now - enterAnimationStartNanos) / (float) ENTER_ANIMATION_NANOS, 0.0F, 1.0F);
        float remaining = 1.0F - elapsed;
        return 1.0F - remaining * remaining * remaining;
    }

    private void updateWidgetAlpha(float visibility) {
        if (tabVisualBtn != null) tabVisualBtn.setAlpha(visibility);
        if (tabTweaksBtn != null) tabTweaksBtn.setAlpha(visibility);
        if (tabSoundBtn != null) tabSoundBtn.setAlpha(visibility);
        if (tabInfoBtn != null) tabInfoBtn.setAlpha(visibility);
        if (closeBtn != null) closeBtn.setAlpha(visibility);
        if (infoAcceptBtn != null) infoAcceptBtn.setAlpha(visibility * infoVisibility());
        for (ClickableWidget widget : contentWidgets) widget.setAlpha(visibility);
    }

    private void drawPurpleWidgetStyles(DrawContext context, int mouseX, int mouseY, float visibility) {
        long now = System.nanoTime();
        float step = lastRenderNanos == 0L
                ? 1.0F
                : MathHelper.clamp((now - lastRenderNanos) / 95_000_000.0F, 0.0F, 1.0F);
        lastRenderNanos = now;
        drawPurpleWidget(context, tabVisualBtn, mouseX, mouseY, visibility, step);
        drawPurpleWidget(context, tabTweaksBtn, mouseX, mouseY, visibility, step);
        drawPurpleWidget(context, tabSoundBtn, mouseX, mouseY, visibility, step);
        drawPurpleWidget(context, tabInfoBtn, mouseX, mouseY, visibility, step);
        drawPurpleWidget(context, closeBtn, mouseX, mouseY, visibility, step);
        drawPurpleWidget(context, infoAcceptBtn, mouseX, mouseY, visibility * infoVisibility(), step);
        for (ClickableWidget widget : contentWidgets) {
            drawPurpleWidget(context, widget, mouseX, mouseY, visibility, step);
        }
    }

    private void drawPurpleWidget(
            DrawContext context,
            ClickableWidget widget,
            int mouseX,
            int mouseY,
            float visibility,
            float step) {
        if (widget == null || !widget.visible) return;
        float current = hoverProgress.getOrDefault(widget, 0.0F);
        float target = widget.isMouseOver(mouseX, mouseY) || widget.isFocused() ? 1.0F : 0.0F;
        current = MathHelper.lerp(smoothStep(step), current, target);
        hoverProgress.put(widget, current);
        int fill = lerpRgb(0x5B2078, 0xA052D0, current);
        int outline = lerpRgb(0xA463D0, 0xE1A7FF, current);
        context.fill(widget.getX(), widget.getY(), widget.getRight(), widget.getBottom(),
                withAlpha(fill, (0.42F + current * 0.20F) * visibility));
        kctDrawBorder(context, widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight(),
                withAlpha(outline, visibility));
        if (widget instanceof PercentSlider slider) {
            int trackY = widget.getBottom() - 3;
            int trackLeft = widget.getX() + 4;
            int trackRight = widget.getRight() - 4;
            context.fill(trackLeft, trackY, trackRight, trackY + 1, withAlpha(0x4A275F, visibility));
            int knobX = trackLeft + Math.round(slider.normalizedValue() * Math.max(0, trackRight - trackLeft - 1));
            context.fill(knobX - 1, trackY - 1, knobX + 2, trackY + 2, withAlpha(0xFFE38D, visibility));
        }
        String label = widget.getMessage().getString();
        int maxLabelWidth = Math.max(1, widget.getWidth() - 8);
        if (textRenderer.getWidth(label) > maxLabelWidth) {
            String ellipsis = "...";
            label = textRenderer.trimToWidth(label, Math.max(1, maxLabelWidth - textRenderer.getWidth(ellipsis))) + ellipsis;
        }
        context.drawCenteredTextWithShadow(textRenderer, Text.literal(label),
                widget.getX() + widget.getWidth() / 2,
                widget.getY() + Math.max(0, (widget.getHeight() - 8) / 2),
                withAlpha(widget.active ? 0xF5E9FF : 0xA08AAE, visibility));
    }

    private void renderAnimatedDescription(DrawContext context, int mouseX, int mouseY, float visibility) {
        if (infoOpen) {
            visibleDescription = null;
            return;
        }
        String hovered = null;
        for (Map.Entry<ClickableWidget, String> entry : descriptions.entrySet()) {
            ClickableWidget widget = entry.getKey();
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

        float progress = MathHelper.clamp(
                (System.nanoTime() - descriptionAnimationStartNanos) / (float) DESCRIPTION_ANIMATION_NANOS,
                0.0F,
                1.0F);
        progress = smoothStep(progress) * visibility;
        int maxTextWidth = Math.max(80, Math.min(230, width - 26));
        List<OrderedText> lines = textRenderer.wrapLines(Text.literal(visibleDescription), maxTextWidth);
        context.drawOrderedTooltip(textRenderer, lines, mouseX,
                mouseY + Math.round((1.0F - progress) * 4.0F));
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
        if (tabVisualBtn != null) tabVisualBtn.visible = mainVisible;
        if (tabTweaksBtn != null) tabTweaksBtn.visible = mainVisible;
        if (tabSoundBtn != null) tabSoundBtn.visible = mainVisible;
        if (tabInfoBtn != null) tabInfoBtn.visible = mainVisible;
        if (closeBtn != null) closeBtn.visible = mainVisible;
        for (ClickableWidget widget : contentWidgets) widget.visible = mainVisible;
        if (infoAcceptBtn != null) infoAcceptBtn.visible = infoOpen;
    }

    private float infoVisibility() {
        if (!infoOpen) return 0.0F;
        float elapsed = MathHelper.clamp(
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

    private int infoCodeX() { return px() + 10; }
    private int infoCodeY() { return py() + (compact ? 27 : 32); }
    private int infoCodeW() { return Math.max(24, panelW - 20); }
    private int infoCodeH() { return Math.max(18, panelH - (compact ? 52 : 60)); }

    private void renderInfoBackground(DrawContext context, float screenVisibility) {
        float visibility = screenVisibility * infoVisibility();
        int x = px() + 5;
        int y = py() + 5;
        int w = panelW - 10;
        int h = panelH - 10;
        context.fill(px() + 1, py() + 1, px() + panelW - 1, py() + panelH - 1,
                withAlpha(0x08030D, 0.82F * visibility));
        context.fill(x, y, x + w, y + h, withAlpha(0x170B24, 0.98F * visibility));
        kctDrawBorder(context, x, y, w, h, withAlpha(0xC47CFF, visibility));
        context.fill(infoCodeX(), infoCodeY(), infoCodeX() + infoCodeW(), infoCodeY() + infoCodeH(),
                withAlpha(0x0D1117, 0.98F * visibility));
        kctDrawBorder(context, infoCodeX(), infoCodeY(), infoCodeW(), infoCodeH(),
                withAlpha(0x663A7A, visibility));
    }

    private void renderInfoContent(DrawContext context, float screenVisibility) {
        float visibility = screenVisibility * infoVisibility();
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("Info Legit"), cx(), py() + 9,
                withAlpha(0xEAD1FF, visibility));
        if (!compact) {
            context.drawCenteredTextWithShadow(textRenderer,
                    Text.literal(infoTextComplete() ? "Esc: close" : "Esc: reveal all"),
                    cx(), py() + 20, withAlpha(0x9DA5B4, visibility));
        }

        int codeX = infoCodeX();
        int codeY = infoCodeY();
        int codeW = infoCodeW();
        int codeH = infoCodeH();
        int textX = codeX + 28;
        int textWidth = Math.max(8, codeW - 35);
        String visibleText = INFO_TEXT.substring(0, revealedInfoCharacters());
        String[] logicalLines = visibleText.split("\\n", -1);
        int renderedLine = 0;

        context.enableScissor(codeX + 1, codeY + 1, codeX + codeW - 1, codeY + codeH - 1);
        for (int lineNumber = 0; lineNumber < logicalLines.length; lineNumber++) {
            String logicalLine = logicalLines[lineNumber];
            List<OrderedText> wrapped = textRenderer.wrapLines(
                    Text.literal(logicalLine.isEmpty() ? " " : logicalLine), textWidth);
            if (wrapped.isEmpty()) wrapped = List.of(Text.literal(" ").asOrderedText());
            int color = infoLineColor(logicalLine);
            for (int segment = 0; segment < wrapped.size(); segment++) {
                int drawY = codeY + 4 + renderedLine * 9 - infoScroll;
                if (drawY >= codeY - 8 && drawY < codeY + codeH) {
                    if (segment == 0) {
                        context.drawTextWithShadow(textRenderer, Text.literal(String.format("%02d", lineNumber + 1)),
                                codeX + 5, drawY, withAlpha(0x6E7681, visibility));
                    }
                    context.drawTextWithShadow(textRenderer, wrapped.get(segment), textX, drawY,
                            withAlpha(color, visibility));
                }
                renderedLine++;
            }
        }
        context.disableScissor();
        drawInfoScrollbar(context, visibility);
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
        int textWidth = Math.max(8, infoCodeW() - 35);
        int lines = 0;
        for (String line : INFO_TEXT.split("\\n", -1)) {
            lines += Math.max(1, textRenderer.wrapLines(
                    Text.literal(line.isEmpty() ? " " : line), textWidth).size());
        }
        return lines * 9 + 8;
    }

    private int maxInfoScroll() {
        if (textRenderer == null) return 0;
        return Math.max(0, totalInfoTextHeight() - infoCodeH());
    }

    private void drawInfoScrollbar(DrawContext context, float visibility) {
        int maxScroll = maxInfoScroll();
        if (maxScroll <= 0) return;
        int x = infoCodeX() + infoCodeW() - 4;
        int y = infoCodeY() + 2;
        int h = infoCodeH() - 4;
        int total = totalInfoTextHeight();
        int thumbH = Math.max(10, h * h / Math.max(h, total));
        int thumbY = y + Math.round((h - thumbH) * (infoScroll / (float) maxScroll));
        context.fill(x, y, x + 2, y + h, withAlpha(0x2B1738, visibility));
        context.fill(x, thumbY, x + 2, thumbY + thumbH, withAlpha(0xC47CFF, visibility));
    }

    private static String buildVersion() {
        return FabricLoader.getInstance().getModContainer("kohs_crystal_tweaks")
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("development");
    }

    private static float smoothStep(float value) {
        float clamped = MathHelper.clamp(value, 0.0F, 1.0F);
        return clamped * clamped * (3.0F - 2.0F * clamped);
    }

    private static int withAlpha(int rgb, float alpha) {
        int alphaByte = Math.round(MathHelper.clamp(alpha, 0.0F, 1.0F) * 255.0F);
        return (alphaByte << 24) | (rgb & 0xFFFFFF);
    }

    private static int lerpRgb(int first, int second, float amount) {
        float clamped = MathHelper.clamp(amount, 0.0F, 1.0F);
        int red = Math.round(((first >> 16) & 0xFF) + (((second >> 16) & 0xFF) - ((first >> 16) & 0xFF)) * clamped);
        int green = Math.round(((first >> 8) & 0xFF) + (((second >> 8) & 0xFF) - ((first >> 8) & 0xFF)) * clamped);
        int blue = Math.round((first & 0xFF) + ((second & 0xFF) - (first & 0xFF)) * clamped);
        return (red << 16) | (green << 8) | blue;
    }

    private void drawTabHighlight(DrawContext ctx) {
        ButtonWidget active = switch (activeTab) {
            case VISUALS -> tabVisualBtn;
            case TWEAKS -> tabTweaksBtn;
            case SOUND -> tabSoundBtn;
        };
        if (active != null) {
            ctx.fill(active.getX(), active.getY() + TAB_H,
                    active.getX() + tabW, active.getY() + TAB_H + 2, 0xFFFFE38D);
        }
    }

    // ── Tab: Optimization ──

    // ── Tab: Visuals ──

    private void renderVisualsContent(DrawContext ctx, int mx, int my) {
        int cy = contentY();
        int lx = contentX();

        if (!crystalTintEnabled) {
            return;
        }

        // Picker (only when ON)
        int pickerY = cy + 44;
        renderPicker(ctx, lx, pickerY);

        ColorState cs = activeColor();
        if (contentW() >= 300) {
            int rx = lx + 170;
            int ry = cy + 24;
            String editLabel = activeTarget == TintTarget.FRAME ? "Editing: Outer" : "Editing: Inner";
            ctx.drawTextWithShadow(textRenderer, Text.literal(editLabel), rx, ry, 0xFFEAD1FF);
            ctx.drawTextWithShadow(textRenderer, Text.literal(cs.toHex()), rx, ry + 10, 0xFFDDEEFF);
            drawSwatch(ctx, Text.literal("Outer"), frameColorState,
                    activeTarget == TintTarget.FRAME, rx, ry + 24);
            drawSwatch(ctx, Text.literal("Inner"), coreColorState,
                    activeTarget == TintTarget.CORE, rx, ry + 24 + SWATCH_H + 4);
        } else {
            ctx.drawTextWithShadow(textRenderer, Text.literal(cs.toHex()),
                    hueBarX() + HUE_BAR_W + 6, pickerY, 0xFFDDEEFF);
        }
    }

    private void renderPicker(DrawContext ctx, int x, int y) {
        ColorState cs = activeColor();
        int pickerWidth = pickerW();
        int hueRgb = 0xFF000000 | MathHelper.hsvToRgb(cs.hue, 1f, 1f);

        for (int dx = 0; dx < pickerWidth; dx++) {
            float s = dx / (float) Math.max(pickerWidth - 1, 1);
            int top = lerpColor(0xFFFFFFFF, hueRgb, s);
            ctx.fillGradient(x + dx, y, x + dx + 1, y + PICKER_H, top, 0xFF000000);
        }
        kctDrawBorder(ctx, x - 1, y - 1, pickerWidth + 2, PICKER_H + 2, 0xE0B86BFF);

        int mX = x + Math.round(cs.saturation * (pickerWidth - 1));
        int mY = y + Math.round((1.0F - cs.value) * (PICKER_H - 1));
        kctDrawBorder(ctx, mX - 3, mY - 3, 7, 7, 0xFFFFFFFF);
        kctDrawBorder(ctx, mX - 2, mY - 2, 5, 5, 0xFF201826);

        int hx = hueBarX();
        for (int dy = 0; dy < PICKER_H; dy++) {
            float h = dy / (float) Math.max(PICKER_H - 1, 1);
            ctx.fill(hx, y + dy, hx + HUE_BAR_W, y + dy + 1,
                    0xFF000000 | MathHelper.hsvToRgb(h, 1f, 1f));
        }
        kctDrawBorder(ctx, hx - 1, y - 1, HUE_BAR_W + 2, PICKER_H + 2, 0xE0B86BFF);

        int hm = y + Math.round(cs.hue * (PICKER_H - 1));
        int markerTop = MathHelper.clamp(hm - 1, y - 1, y + PICKER_H - 2);
        kctDrawBorder(ctx, hx - 2, markerTop, HUE_BAR_W + 4, 3, 0xFFFFFFFF);
    }

    private void drawSwatch(DrawContext ctx, Text label, ColorState cs, boolean active, int x, int y) {
        ctx.fill(x, y, x + SWATCH_W, y + SWATCH_H, 0x7A130822);
        kctDrawBorder(ctx, x, y, SWATCH_W, SWATCH_H, active ? 0xFFFFE38D : 0xE0B86BFF);
        ctx.fill(x + 3, y + 3, x + 19, y + 19, cs.toArgb());
        kctDrawBorder(ctx, x + 3, y + 3, 16, 16, 0xFFFFFFFF);
        ctx.drawTextWithShadow(textRenderer, label, x + 22, y + 3, 0xFFF4E8FF);
        ctx.drawTextWithShadow(textRenderer, Text.literal(cs.toHex()),
                x + 22, y + 13, active ? 0xFFFFE38D : 0xFFD8D0E6);
    }

    private void kctDrawBorder(DrawContext ctx, int x, int y, int width, int height, int color) {
        ctx.fill(x, y, x + width, y + 1, color);
        ctx.fill(x, y + height - 1, x + width, y + height, color);
        ctx.fill(x, y + 1, x + 1, y + height - 1, color);
        ctx.fill(x + width - 1, y + 1, x + width, y + height - 1, color);
    }

    // ── Tab: Sound ──

    private void renderSoundContent(DrawContext ctx) {
        int y = contentY();
        int lx = contentX();

        if (!customSoundEnabled) {
            return;
        }

        // File info (only when ON)
        int infoY = y + 100;
        String file = CrystalSoundManager.getLoadedFileName();
        if (!file.isEmpty()) {
            String fullStr = "Loaded: " + file + " (" + String.format("%.1fs", CrystalSoundManager.getLoadedDuration()) + ")";
            int maxW = contentW();
            if (textRenderer.getWidth(fullStr) > maxW) {
                fullStr = textRenderer.trimToWidth(fullStr, maxW - textRenderer.getWidth("...")) + "...";
            }
            ctx.drawTextWithShadow(textRenderer, Text.literal(fullStr), lx, infoY, 0xFF88FF88);
        } else if (!soundStatus.isEmpty()) {
            ctx.drawTextWithShadow(textRenderer, Text.literal(soundStatus), lx, infoY, 0xFFFF8888);
        } else {
            ctx.drawTextWithShadow(textRenderer, Text.literal("No sound file selected"), lx, infoY, 0xFFD8D0E6);
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  Mouse (for color picker)
    // ══════════════════════════════════════════════════════════════════

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (infoOpen && keyCode == 256) {
            if (!infoTextComplete()) {
                infoRevealAll = true;
            } else {
                closeInfo();
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (infoOpen
                && mouseX >= infoCodeX() && mouseX < infoCodeX() + infoCodeW()
                && mouseY >= infoCodeY() && mouseY < infoCodeY() + infoCodeH()) {
            infoScroll = MathHelper.clamp(infoScroll - (int) Math.round(verticalAmount * 18.0), 0, maxInfoScroll());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (btn == 0 && activeTab == Tab.VISUALS && crystalTintEnabled) {
            if (inSpectrum(mx, my)) { applySV(mx, my); draggingPicker = true; setDragging(true); return true; }
            if (inHueBar(mx, my))   { applyHue(my);    draggingHue = true;    setDragging(true); return true; }
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
        if (btn == 0 && activeTab == Tab.VISUALS && crystalTintEnabled) {
            if (draggingPicker) { applySV(mx, my); return true; }
            if (draggingHue)    { applyHue(my);    return true; }
        }
        return super.mouseDragged(mx, my, btn, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int btn) {
        if (btn == 0) { draggingPicker = false; draggingHue = false; setDragging(false); }
        return super.mouseReleased(mx, my, btn);
    }

    // Picker geometry helpers
    private int pickerX() { return contentX(); }
    private int pickerY() { return contentY() + 44; }
    private int pickerW() { return Math.max(48, Math.min(PICKER_W, contentW() - 30)); }
    private int hueBarX() { return pickerX() + pickerW() + HUE_GAP; }

    private boolean inSpectrum(double mx, double my) {
        return mx >= pickerX() && mx < pickerX() + pickerW()
                && my >= pickerY() && my < pickerY() + PICKER_H;
    }

    private boolean inHueBar(double mx, double my) {
        return mx >= hueBarX() && mx < hueBarX() + HUE_BAR_W
                && my >= pickerY() && my < pickerY() + PICKER_H;
    }

    private void applySV(double mx, double my) {
        ColorState cs = activeColor();
        cs.saturation = MathHelper.clamp((float) ((mx - pickerX()) / Math.max(pickerW() - 1.0, 1)), 0, 1);
        cs.value      = MathHelper.clamp(1f - (float) ((my - pickerY()) / Math.max(PICKER_H - 1.0, 1)), 0, 1);
    }

    private void applyHue(double my) {
        activeColor().hue = MathHelper.clamp(
                (float) ((my - pickerY()) / Math.max(PICKER_H - 1.0, 1)), 0, 1);
    }

    // ══════════════════════════════════════════════════════════════════
    //  File picker
    // ══════════════════════════════════════════════════════════════════

    private void openFilePicker() {
        new Thread(() -> {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                PointerBuffer patterns = stack.mallocPointer(3);
                patterns.put(stack.UTF8("*.wav"))
                        .put(stack.UTF8("*.ogg"))
                        .put(stack.UTF8("*.mp3"))
                        .flip();

                String result = TinyFileDialogs.tinyfd_openFileDialog(
                        "Select Explosion Sound",
                        "", patterns,
                        "Audio Files (*.wav, *.ogg, *.mp3)", false);

                if (result != null) {
                    MinecraftClient.getInstance().execute(() -> {
                        String err = CrystalSoundManager.importFile(Path.of(result));
                        if (err.isEmpty()) {
                            soundStatus = "";
                        } else {
                            soundStatus = err;
                        }
                        refreshSoundStatus();
                    });
                }
            }
        }, "KCT-FilePicker").start();
    }

    private void refreshSoundStatus() {
        String err = CrystalSoundManager.getLastError();
        if (!err.isEmpty()) {
            soundStatus = err;
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  Close / Save
    // ══════════════════════════════════════════════════════════════════



    @Override
    public void close() {
        if (infoOpen) {
            closeInfo();
            return;
        }
        saveState();
        MinecraftClient.getInstance().setScreen(parent);
    }

    private void saveState() {
        if (stateSaved) return;
        // Save visuals
        KoHsCrystalTweaksConfig.setCrystalTintSettings(
                crystalTintEnabled, frameColorState.toHex(), coreColorState.toHex());

        KoHsCrystalTweaksConfig.setCrystalTweaksSettings(
                crystalSpinSpeed, crystalFlotationEnabled, staticCrystalEnabled);
        KoHsCrystalTweaksConfig.get().safeCrystalEnabled = safeCrystalEnabled;
        KoHsCrystalTweaksConfig.get().clientSideCrystalsEnabled = visualCrystalPreviewEnabled;
        KoHsCrystalTweaksConfig.get().seamlessEnabled = visualCrystalPreviewEnabled;
        KoHsCrystalTweaksConfig.get().confirmedCrystalCleanupEnabled = confirmedCrystalCleanupEnabled;
        KoHsCrystalTweaksConfig.save();
        CrystalPredictor.setEnabled(visualCrystalPreviewEnabled);

        // Save sound
        KoHsCrystalTweaksConfig.setCustomSoundSettings(
                customSoundEnabled,
                KoHsCrystalTweaksConfig.get().customSoundFileName,
                soundVolume, soundSpeed);

        CrystalSoundManager.reloadFromConfig();
        stateSaved = true;
    }

    // ══════════════════════════════════════════════════════════════════
    //  Label helpers
    // ══════════════════════════════════════════════════════════════════

    private Text tintLabel() {
        return Text.literal("Crystal Tint: " + (crystalTintEnabled ? "ON" : "OFF"));
    }

    private Text staticCrystalLabel() {
        return Text.literal("Static Crystal: ").append(staticCrystalEnabled ? Text.literal("ON").formatted(Formatting.GREEN) : Text.literal("OFF").formatted(Formatting.RED));
    }

    private Text visualCrystalPreviewLabel() {
        return Text.literal("Visual Preview: ").append(visualCrystalPreviewEnabled
                ? Text.literal("ON").formatted(Formatting.GREEN)
                : Text.literal("OFF").formatted(Formatting.RED));
    }

    private Text confirmedCrystalCleanupLabel() {
        return Text.literal("Confirmed Cleanup: ").append(confirmedCrystalCleanupEnabled
                ? Text.literal("ON").formatted(Formatting.GREEN)
                : Text.literal("OFF").formatted(Formatting.RED));
    }

    private Text safeCrystalLabel() {
        return Text.literal("Safe Crystal: ").append(safeCrystalEnabled
                ? Text.literal("ON").formatted(Formatting.GREEN)
                : Text.literal("OFF").formatted(Formatting.RED));
    }

    private Text crystalFlotationLabel() {
        return Text.literal("Crystal Flotation: ").append(crystalFlotationEnabled ? Text.literal("ON").formatted(Formatting.GREEN) : Text.literal("OFF").formatted(Formatting.RED));
    }

    private Text soundToggleLabel() {
        return Text.literal("Custom Sound: ").append(customSoundEnabled ? Text.literal("ON").formatted(Formatting.GREEN) : Text.literal("OFF").formatted(Formatting.RED));
    }

    private void updateLayerTexts() {
        for (ClickableWidget w : contentWidgets) {
            if (w instanceof ButtonWidget btn) {
                String msg = btn.getMessage().getString();
                if (msg.contains("Outer") || msg.contains("Inner") || msg.contains("EDITING") || msg.contains("Edit")) {
                    // Update based on position — first layer btn = outer
                }
            }
        }
        // Direct approach: layer buttons are 2nd and 3rd in the content list
        if (contentWidgets.size() >= 3) {
            ClickableWidget outer = contentWidgets.get(1);
            ClickableWidget inner = contentWidgets.get(2);
            if (outer instanceof ButtonWidget ob) {
                ob.setMessage(Text.literal(activeTarget == TintTarget.FRAME ? "Outer: EDITING" : "Edit Outer"));
            }
            if (inner instanceof ButtonWidget ib) {
                ib.setMessage(Text.literal(activeTarget == TintTarget.CORE ? "Inner: EDITING" : "Edit Inner"));
            }
        }
    }

    private ColorState activeColor() {
        return activeTarget == TintTarget.FRAME ? frameColorState : coreColorState;
    }

    // ══════════════════════════════════════════════════════════════════
    //  Drawing helpers
    // ══════════════════════════════════════════════════════════════════

    private static int lerpColor(int a, int b, float t) {
        float c = MathHelper.clamp(t, 0, 1);
        int r = (int) (((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * c);
        int g = (int) (((a >> 8) & 0xFF)  + (((b >> 8) & 0xFF)  - ((a >> 8) & 0xFF))  * c);
        int bl= (int) ((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * c);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    // ══════════════════════════════════════════════════════════════════
    //  Inner types
    // ══════════════════════════════════════════════════════════════════

    private enum Tab { VISUALS, TWEAKS, SOUND }
    private enum TintTarget { FRAME, CORE }

    private static final class ColorState {
        float hue, saturation, value;

        static ColorState fromArgb(int color) {
            float r = ((color >> 16) & 0xFF) / 255f;
            float g = ((color >> 8) & 0xFF) / 255f;
            float b = (color & 0xFF) / 255f;
            float mx = Math.max(r, Math.max(g, b));
            float mn = Math.min(r, Math.min(g, b));
            float d = mx - mn;
            float h = 0;
            if (d > 0) {
                if (mx == r) h = ((g - b) / d) % 6f;
                else if (mx == g) h = ((b - r) / d) + 2f;
                else h = ((r - g) / d) + 4f;
                h /= 6f;
                if (h < 0) h += 1f;
            }
            ColorState s = new ColorState();
            s.hue = h;
            s.saturation = mx <= 0 ? 0 : d / mx;
            s.value = mx;
            return s;
        }

        int toArgb() { return 0xFF000000 | MathHelper.hsvToRgb(hue, saturation, value); }
        String toHex() { return String.format("#%06X", toArgb() & 0xFFFFFF); }
    }

    /** Simple percentage slider. */
    private static final class PercentSlider extends SliderWidget {
        private final String label;
        private final double min, max;
        private final java.util.function.Consumer<Double> onChange;

        PercentSlider(int x, int y, int w, int h, String label,
                      double min, double max, double current,
                      java.util.function.Consumer<Double> onChange) {
            super(x, y, w, h, Text.empty(), (current - min) / (max - min));
            this.label = label;
            this.min = min;
            this.max = max;
            this.onChange = onChange;
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            double val = min + value * (max - min);
            setMessage(Text.literal(label + ": " + Math.round(val * 100) + "%"));
        }

        @Override
        protected void applyValue() {
            onChange.accept(min + value * (max - min));
        }

        float normalizedValue() {
            return (float) value;
        }
    }
}
