package dev.zymekoh.kohscrystaltweaks.gui;

import dev.zymekoh.kohscrystaltweaks.config.KoHsCrystalTweaksConfig;
import dev.zymekoh.kohscrystaltweaks.core.CrystalPredictor;
import dev.zymekoh.kohscrystaltweaks.sound.CrystalSoundManager;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

public final class KoHsCrystalTweaksConfigScreen extends Screen {

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
    private static final Text SUBTITLE = Text.literal("Legit Crystal Optimizer");

    // ── State ──
    private final Screen parent;
    private Tab activeTab = Tab.OPTIMIZATION;
    private final List<ClickableWidget> contentWidgets = new ArrayList<>();
    private int panelW;
    private int panelH;
    private int tabW;
    private int buttonW;
    private int contentTop;
    private int rowSpacing;
    private int tabYOffset;
    private boolean compact;

    // Tab buttons (persistent)
    private ButtonWidget tabOptBtn, tabVisualBtn, tabTweaksBtn, tabSoundBtn;

    // Visuals tab state
    private boolean crystalTintEnabled;
    private boolean draggingPicker, draggingHue;
    private TintTarget activeTarget = TintTarget.FRAME;
    private ColorState frameColorState, coreColorState;

    // Tweaks tab state
    private float crystalSpinSpeed;
    private boolean crystalFlotationEnabled;
    private boolean staticCrystalEnabled;
    private boolean placementFixEnabled;
    private boolean rapidAttackFixEnabled;

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
        computeLayout();
        // Load state from config on first init
        KoHsCrystalTweaksConfig cfg = KoHsCrystalTweaksConfig.get();
        if (frameColorState == null) {
            crystalTintEnabled = cfg.crystalTintEnabled;
            frameColorState = ColorState.fromArgb(KoHsCrystalTweaksConfig.getCrystalFrameTintArgb());
            coreColorState  = ColorState.fromArgb(KoHsCrystalTweaksConfig.getCrystalCoreTintArgb());
            crystalSpinSpeed = cfg.crystalSpinSpeed;
            crystalFlotationEnabled = cfg.crystalFlotationEnabled;
            staticCrystalEnabled = cfg.staticCrystalEnabled;
            placementFixEnabled = cfg.placementFixEnabled;
            rapidAttackFixEnabled = cfg.rapidAttackFixEnabled;
            customSoundEnabled = cfg.customSoundEnabled;
            soundVolume = cfg.soundVolume;
            soundSpeed = cfg.soundSpeed;
            refreshSoundStatus();
        }

        // ── Tab bar (always visible) ──
        int tabTotalW = tabW * 4 + TAB_GAP * 3;
        int tabStartX = cx() - tabTotalW / 2;
        int tabY = py() + tabYOffset;

        tabOptBtn = addDrawableChild(ButtonWidget.builder(Text.literal("Optimization"), b -> switchTab(Tab.OPTIMIZATION))
                .dimensions(tabStartX, tabY, tabW, TAB_H).build());
        tabVisualBtn = addDrawableChild(ButtonWidget.builder(Text.literal("Visuals"), b -> switchTab(Tab.VISUALS))
                .dimensions(tabStartX + tabW + TAB_GAP, tabY, tabW, TAB_H).build());
        tabTweaksBtn = addDrawableChild(ButtonWidget.builder(Text.literal("Tweaks"), b -> switchTab(Tab.TWEAKS))
                .dimensions(tabStartX + (tabW + TAB_GAP) * 2, tabY, tabW, TAB_H).build());
        tabSoundBtn = addDrawableChild(ButtonWidget.builder(Text.literal("Sound"), b -> switchTab(Tab.SOUND))
                .dimensions(tabStartX + (tabW + TAB_GAP) * 3, tabY, tabW, TAB_H).build());

        // ── Close (always visible) ──
        addDrawableChild(ButtonWidget.builder(Text.literal("Close"), b -> close())
                .dimensions(cx() - 40, py() + panelH - 22, 80, BTN_H).build());

        // Add content for active tab
        initContent();
    }

    private void rebuildTab() {
        for (ClickableWidget w : contentWidgets) {
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
            case OPTIMIZATION -> initOptimizationTab();
            case VISUALS -> initVisualsTab();
            case TWEAKS -> initTweaksTab();
            case SOUND -> initSoundTab();
        }
    }

    private void initOptimizationTab() {
        int y = contentY();

        // Local Crystal toggle
        addContent(withTooltip(ButtonWidget.builder(localCrystalLabel(), b -> {
            boolean next = !KoHsCrystalTweaksConfig.get().clientSideCrystalsEnabled;
            KoHsCrystalTweaksConfig.setClientSideCrystalsEnabled(next);
            CrystalPredictor.setEnabled(next);
            b.setMessage(localCrystalLabel());
        }).dimensions(cx() - buttonW / 2, y, buttonW, BTN_H).build(),
                "Renders accepted crystal placements immediately on the client."));

        // Seamless toggle
        addContent(withTooltip(ButtonWidget.builder(seamlessLabel(), b -> {
            KoHsCrystalTweaksConfig cfg = KoHsCrystalTweaksConfig.get();
            cfg.seamlessEnabled = !cfg.seamlessEnabled;
            KoHsCrystalTweaksConfig.save();
            b.setMessage(seamlessLabel());
        }).dimensions(cx() - buttonW / 2, y + rowSpacing, buttonW, BTN_H).build(),
                "Smooths the handoff from a predicted crystal to the server entity."));
    }

    private void initVisualsTab() {
        int y = contentY();

        // Tint toggle — rebuilds content on click
        addContent(withTooltip(ButtonWidget.builder(tintLabel(), b -> {
            crystalTintEnabled = !crystalTintEnabled;
            b.setMessage(tintLabel());
            rebuildTab(); // rebuild to show/hide color config
        }).dimensions(cx() - buttonW / 2, y, buttonW, BTN_H).build(),
                "Applies separate custom colors to the crystal frame and core."));

        if (!crystalTintEnabled) return;

        // Layer buttons (only when ON)
        int lx = contentX();
        int layerButtonW = Math.max(1, Math.min(LAYER_BTN_W, (contentW() - 4) / 2));
        ButtonWidget outerBtn = ButtonWidget.builder(Text.literal(""), b -> {
            activeTarget = TintTarget.FRAME;
            updateLayerTexts();
        }).dimensions(lx, y + 22, layerButtonW, BTN_H).build();
        outerBtn.setTooltip(Tooltip.of(Text.literal("Selects the crystal frame color for editing.")));
        addContent(outerBtn);

        ButtonWidget innerBtn = ButtonWidget.builder(Text.literal(""), b -> {
            activeTarget = TintTarget.CORE;
            updateLayerTexts();
        }).dimensions(lx + layerButtonW + 4, y + 22, layerButtonW, BTN_H).build();
        innerBtn.setTooltip(Tooltip.of(Text.literal("Selects the crystal core color for editing.")));
        addContent(innerBtn);

        updateLayerTexts();
    }

    private void initTweaksTab() {
        int y = contentY();

        addContent(withTooltip(ButtonWidget.builder(placementFixLabel(), b -> requestPlacementFixToggle())
                .dimensions(cx() - buttonW / 2, y, buttonW, BTN_H).build(),
                "Retargets only the current crystal use to freshly predicted obsidian. Sends no extra use packets."));

        addContent(withTooltip(ButtonWidget.builder(rapidAttackFixLabel(), b -> {
            rapidAttackFixEnabled = !rapidAttackFixEnabled;
            b.setMessage(rapidAttackFixLabel());
        }).dimensions(cx() - buttonW / 2, y + rowSpacing, buttonW, BTN_H).build(),
                "Queues one validated attack when a predicted crystal is clicked before the server entity arrives. Never repeats attacks."));

        addContent(withTooltip(ButtonWidget.builder(staticCrystalLabel(), b -> {
            staticCrystalEnabled = !staticCrystalEnabled;
            b.setMessage(staticCrystalLabel());
            rebuildTab();
        }).dimensions(cx() - buttonW / 2, y + rowSpacing * 2, buttonW, BTN_H).build(),
                "Keeps crystals completely still with no spin or floating animation."));

        if (staticCrystalEnabled) return;

        addContent(withTooltip(ButtonWidget.builder(crystalFlotationLabel(), b -> {
            crystalFlotationEnabled = !crystalFlotationEnabled;
            b.setMessage(crystalFlotationLabel());
        }).dimensions(cx() - buttonW / 2, y + rowSpacing * 3, buttonW, BTN_H).build(),
                "Enables or disables the crystal floating animation."));

        addContent(withTooltip(new PercentSlider(cx() - buttonW / 2, y + rowSpacing * 4, buttonW, BTN_H,
                "Spin Speed", 0.0, 3.0, crystalSpinSpeed, v -> crystalSpinSpeed = v.floatValue()),
                "Controls crystal rotation speed from stopped to 300%."));
    }

    private void requestPlacementFixToggle() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!placementFixEnabled) {
            placementFixEnabled = true;
            rebuildTab();
            return;
        }

        client.setScreen(new ConfirmScreen(
                accepted -> {
                    placementFixEnabled = !accepted;
                    client.setScreen(this);
                },
                Text.literal("Disable Placement Fix?"),
                Text.literal("Disabling it may reintroduce a delay when placing End Crystals immediately after obsidian."),
                Text.literal("Accept"),
                Text.literal("Restore")));
    }

    private void initSoundTab() {
        int y = contentY();

        // Custom Sound toggle — rebuilds content on click
        addContent(withTooltip(ButtonWidget.builder(soundToggleLabel(), b -> {
            customSoundEnabled = !customSoundEnabled;
            b.setMessage(soundToggleLabel());
            rebuildTab(); // rebuild to show/hide sound config
        }).dimensions(cx() - buttonW / 2, y, buttonW, BTN_H).build(),
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
        widget.setTooltip(Tooltip.of(Text.literal(description)));
        return widget;
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
        // Manually draw dark background to prevent 1.21.2+ vanilla blur
        ctx.fill(0, 0, this.width, this.height, 0x88000000);
        
        
        int p = py(), pxl = px();
        // Panel bg + border
        ctx.fill(pxl, p, pxl + panelW, p + panelH, 0x8A1A0A2E);
        kctDrawBorder(ctx, pxl, p, panelW, panelH, 0xE0B86BFF);

        // Content-area border
        int cy = py() + contentTop - 4;
        kctDrawBorder(ctx, pxl + 4, cy, Math.max(1, panelW - 8),
                Math.max(1, panelH - contentTop - 22), 0x60B86BFF);
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        super.render(ctx, mx, my, delta);

        int p = py();
        
        // Title
        ctx.drawCenteredTextWithShadow(textRenderer, TITLE, cx(), p + 6, 0xFFEAD1FF);
        if (!compact) {
            ctx.drawCenteredTextWithShadow(textRenderer, SUBTITLE, cx(), p + 18, 0xFFDDA6FF);
        }

        // Tab highlight underline
        drawTabHighlight(ctx);

        // Tab-specific rendering
        switch (activeTab) {
            case VISUALS -> renderVisualsContent(ctx, mx, my);
            case SOUND -> renderSoundContent(ctx);
            case OPTIMIZATION, TWEAKS -> {
            }
        }
    }

    private void drawTabHighlight(DrawContext ctx) {
        ButtonWidget active = switch (activeTab) {
            case OPTIMIZATION -> tabOptBtn;
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

        int mX = x + (int) (cs.saturation * pickerWidth);
        int mY = y + PICKER_H - (int) (cs.value * PICKER_H);
        kctDrawBorder(ctx, mX - 3, mY - 3, 7, 7, 0xFFFFFFFF);
        kctDrawBorder(ctx, mX - 2, mY - 2, 5, 5, 0xFF201826);

        int hx = x + pickerWidth + 10;
        for (int dy = 0; dy < PICKER_H; dy++) {
            float h = dy / (float) Math.max(PICKER_H - 1, 1);
            ctx.fill(hx, y + dy, hx + HUE_BAR_W, y + dy + 1,
                    0xFF000000 | MathHelper.hsvToRgb(h, 1f, 1f));
        }
        kctDrawBorder(ctx, hx - 1, y - 1, HUE_BAR_W + 2, PICKER_H + 2, 0xE0B86BFF);

        int hm = y + (int) (cs.hue * PICKER_H);
        kctDrawBorder(ctx, hx - 2, hm - 2, HUE_BAR_W + 4, 5, 0xFFFFFFFF);
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
    public boolean mouseClicked(net.minecraft.client.gui.Click click, boolean doubled) {
        double mx = click.x();
        double my = click.y();
        int btn = click.button();
        if (btn == 0 && activeTab == Tab.VISUALS && crystalTintEnabled) {
            if (inSpectrum(mx, my)) { applySV(mx, my); draggingPicker = true; setDragging(true); return true; }
            if (inHueBar(mx, my))   { applyHue(my);    draggingHue = true;    setDragging(true); return true; }
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.gui.Click click, double dx, double dy) {
        double mx = click.x();
        double my = click.y();
        int btn = click.button();
        if (btn == 0 && activeTab == Tab.VISUALS && crystalTintEnabled) {
            if (draggingPicker) { applySV(mx, my); return true; }
            if (draggingHue)    { applyHue(my);    return true; }
        }
        return super.mouseDragged(click, dx, dy);
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.gui.Click click) {
        int btn = click.button();
        if (btn == 0) { draggingPicker = false; draggingHue = false; setDragging(false); }
        return super.mouseReleased(click);
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
        // Save visuals
        KoHsCrystalTweaksConfig.setCrystalTintSettings(
                crystalTintEnabled, frameColorState.toHex(), coreColorState.toHex());

        KoHsCrystalTweaksConfig.setCrystalTweaksSettings(
                crystalSpinSpeed, crystalFlotationEnabled, staticCrystalEnabled);
        KoHsCrystalTweaksConfig.get().placementFixEnabled = placementFixEnabled;
        KoHsCrystalTweaksConfig.get().rapidAttackFixEnabled = rapidAttackFixEnabled;
        KoHsCrystalTweaksConfig.save();

        // Save sound
        KoHsCrystalTweaksConfig.setCustomSoundSettings(
                customSoundEnabled,
                KoHsCrystalTweaksConfig.get().customSoundFileName,
                soundVolume, soundSpeed);

        CrystalSoundManager.reloadFromConfig();
        MinecraftClient.getInstance().setScreen(parent);
    }

    // ══════════════════════════════════════════════════════════════════
    //  Label helpers
    // ══════════════════════════════════════════════════════════════════

    private static Text localCrystalLabel() {
        return Text.literal("Local Crystal: "
                + (KoHsCrystalTweaksConfig.get().clientSideCrystalsEnabled ? "ON" : "OFF"));
    }

    private static Text seamlessLabel() {
        return Text.literal("Seamless Mode: "
                + (KoHsCrystalTweaksConfig.get().seamlessEnabled ? "ON" : "OFF"));
    }

    private Text tintLabel() {
        return Text.literal("Crystal Tint: " + (crystalTintEnabled ? "ON" : "OFF"));
    }

    private Text staticCrystalLabel() {
        return Text.literal("Static Crystal: ").append(staticCrystalEnabled ? Text.literal("ON").formatted(Formatting.GREEN) : Text.literal("OFF").formatted(Formatting.RED));
    }

    private Text placementFixLabel() {
        return Text.literal("Placement Fix: ").append(placementFixEnabled
                ? Text.literal("ON").formatted(Formatting.GREEN)
                : Text.literal("OFF").formatted(Formatting.RED));
    }

    private Text rapidAttackFixLabel() {
        return Text.literal("Rapid Attack Fix: ").append(rapidAttackFixEnabled
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

    private enum Tab { OPTIMIZATION, VISUALS, TWEAKS, SOUND }
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
    }
}
