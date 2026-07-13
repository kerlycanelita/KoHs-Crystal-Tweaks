package dev.zymekoh.kohscrystaltweaks.gui;

import dev.zymekoh.kohscrystaltweaks.config.KoHsCrystalTweaksConfig;
import dev.zymekoh.kohscrystaltweaks.core.CrystalPredictor;
import dev.zymekoh.kohscrystaltweaks.sound.CrystalSoundManager;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

public final class KoHsCrystalTweaksConfigScreen extends Screen {

    // ── Panel ──
    private static final int PANEL_W = 360;
    private static final int PANEL_H = 240;

    // ── Tab bar ──
    private static final int TAB_W = 86;
    private static final int TAB_H = 16;
    private static final int TAB_GAP = 4;
    private static final int TAB_Y_OFFSET = 36;

    // ── Content area ──
    private static final int CONTENT_TOP = 56;
    private static final int CONTENT_PAD = 14;

    // ── Button sizes ──
    private static final int BTN_W = 180;
    private static final int BTN_H = 16;
    private static final int DESC_COLOR = 0xFF9E92B0;
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

    private int px() { return (width - PANEL_W) / 2; }
    private int py() { return (height - PANEL_H) / 2; }
    private int contentX() { return px() + CONTENT_PAD; }
    private int contentY() { return py() + CONTENT_TOP; }
    private int contentW() { return PANEL_W - CONTENT_PAD * 2; }
    private int cx() { return width / 2; }

    // ══════════════════════════════════════════════════════════════════
    //  Init
    // ══════════════════════════════════════════════════════════════════

    @Override
    protected void init() {
        // Load state from config on first init
        KoHsCrystalTweaksConfig cfg = KoHsCrystalTweaksConfig.get();
        if (frameColorState == null) {
            crystalTintEnabled = cfg.crystalTintEnabled;
            frameColorState = ColorState.fromArgb(KoHsCrystalTweaksConfig.getCrystalFrameTintArgb());
            coreColorState  = ColorState.fromArgb(KoHsCrystalTweaksConfig.getCrystalCoreTintArgb());
            crystalSpinSpeed = cfg.crystalSpinSpeed;
            crystalFlotationEnabled = cfg.crystalFlotationEnabled;
            staticCrystalEnabled = cfg.staticCrystalEnabled;
            customSoundEnabled = cfg.customSoundEnabled;
            soundVolume = cfg.soundVolume;
            soundSpeed = cfg.soundSpeed;
            refreshSoundStatus();
        }

        // ── Tab bar (always visible) ──
        int tabTotalW = TAB_W * 4 + TAB_GAP * 3;
        int tabStartX = cx() - tabTotalW / 2;
        int tabY = py() + TAB_Y_OFFSET;

        tabOptBtn = addDrawableChild(ButtonWidget.builder(Text.literal("Optimization"), b -> switchTab(Tab.OPTIMIZATION))
                .dimensions(tabStartX, tabY, TAB_W, TAB_H).build());
        tabVisualBtn = addDrawableChild(ButtonWidget.builder(Text.literal("Visuals"), b -> switchTab(Tab.VISUALS))
                .dimensions(tabStartX + TAB_W + TAB_GAP, tabY, TAB_W, TAB_H).build());
        tabTweaksBtn = addDrawableChild(ButtonWidget.builder(Text.literal("Tweaks"), b -> switchTab(Tab.TWEAKS))
                .dimensions(tabStartX + (TAB_W + TAB_GAP) * 2, tabY, TAB_W, TAB_H).build());
        tabSoundBtn = addDrawableChild(ButtonWidget.builder(Text.literal("Sound"), b -> switchTab(Tab.SOUND))
                .dimensions(tabStartX + (TAB_W + TAB_GAP) * 3, tabY, TAB_W, TAB_H).build());

        // ── Close (always visible) ──
        addDrawableChild(ButtonWidget.builder(Text.literal("Close"), b -> close())
                .dimensions(cx() - 40, py() + PANEL_H - 22, 80, BTN_H).build());

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
        addContent(ButtonWidget.builder(localCrystalLabel(), b -> {
            boolean next = !KoHsCrystalTweaksConfig.get().clientSideCrystalsEnabled;
            KoHsCrystalTweaksConfig.setClientSideCrystalsEnabled(next);
            CrystalPredictor.setEnabled(next);
            b.setMessage(localCrystalLabel());
        }).dimensions(cx() - BTN_W / 2, y, BTN_W, BTN_H).build());

        // Seamless toggle
        addContent(ButtonWidget.builder(seamlessLabel(), b -> {
            KoHsCrystalTweaksConfig cfg = KoHsCrystalTweaksConfig.get();
            cfg.seamlessEnabled = !cfg.seamlessEnabled;
            KoHsCrystalTweaksConfig.save();
            b.setMessage(seamlessLabel());
        }).dimensions(cx() - BTN_W / 2, y + ROW_SPACING, BTN_W, BTN_H).build());
    }

    private void initVisualsTab() {
        int y = contentY();

        // Tint toggle — rebuilds content on click
        addContent(ButtonWidget.builder(tintLabel(), b -> {
            crystalTintEnabled = !crystalTintEnabled;
            b.setMessage(tintLabel());
            rebuildTab(); // rebuild to show/hide color config
        }).dimensions(cx() - BTN_W / 2, y, BTN_W, BTN_H).build());

        if (!crystalTintEnabled) return; // Only description text rendered

        // Layer buttons (only when ON)
        int lx = contentX();
        ButtonWidget outerBtn = ButtonWidget.builder(Text.literal(""), b -> {
            activeTarget = TintTarget.FRAME;
            updateLayerTexts();
        }).dimensions(lx, y + 22, LAYER_BTN_W, BTN_H).build();
        addContent(outerBtn);

        ButtonWidget innerBtn = ButtonWidget.builder(Text.literal(""), b -> {
            activeTarget = TintTarget.CORE;
            updateLayerTexts();
        }).dimensions(lx + LAYER_BTN_W + 4, y + 22, LAYER_BTN_W, BTN_H).build();
        addContent(innerBtn);

        updateLayerTexts();
    }

    private void initTweaksTab() {
        int y = contentY();

        addContent(ButtonWidget.builder(staticCrystalLabel(), b -> {
            staticCrystalEnabled = !staticCrystalEnabled;
            b.setMessage(staticCrystalLabel());
            rebuildTab();
        }).dimensions(cx() - BTN_W / 2, y, BTN_W, BTN_H).build());

        if (staticCrystalEnabled) return;

        addContent(ButtonWidget.builder(crystalFlotationLabel(), b -> {
            crystalFlotationEnabled = !crystalFlotationEnabled;
            b.setMessage(crystalFlotationLabel());
        }).dimensions(cx() - BTN_W / 2, y + ROW_SPACING, BTN_W, BTN_H).build());

        addContent(new PercentSlider(cx() - BTN_W / 2, y + ROW_SPACING * 2, BTN_W, BTN_H,
                "Spin Speed", 0.0, 3.0, crystalSpinSpeed, v -> crystalSpinSpeed = v.floatValue()));
    }

    private void initSoundTab() {
        int y = contentY();

        // Custom Sound toggle — rebuilds content on click
        addContent(ButtonWidget.builder(soundToggleLabel(), b -> {
            customSoundEnabled = !customSoundEnabled;
            b.setMessage(soundToggleLabel());
            rebuildTab(); // rebuild to show/hide sound config
        }).dimensions(cx() - BTN_W / 2, y, BTN_W, BTN_H).build());

        if (!customSoundEnabled) return; // Only description text rendered

        // Select file button (only when ON)
        addContent(ButtonWidget.builder(Text.literal("Select Sound File..."), b -> openFilePicker())
                .dimensions(cx() - BTN_W / 2, y + 20, BTN_W, BTN_H).build());

        // Volume slider
        addContent(new PercentSlider(cx() - BTN_W / 2, y + 42, BTN_W, BTN_H,
                "Volume", 0.0, 2.0, soundVolume, v -> soundVolume = v.floatValue()));

        // Speed slider
        addContent(new PercentSlider(cx() - BTN_W / 2, y + 64, BTN_W, BTN_H,
                "Speed", 0.5, 2.0, soundSpeed, v -> soundSpeed = v.floatValue()));
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
        ctx.fill(pxl, p, pxl + PANEL_W, p + PANEL_H, 0x8A1A0A2E);
        kctDrawBorder(ctx, pxl, p, PANEL_W, PANEL_H, 0xE0B86BFF);

        // Content-area border
        int cy = py() + CONTENT_TOP - 4;
        kctDrawBorder(ctx, pxl + 4, cy, PANEL_W - 8, PANEL_H - CONTENT_TOP - 22, 0x60B86BFF);
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        super.render(ctx, mx, my, delta);

        int p = py();
        
        // Title
        ctx.drawCenteredTextWithShadow(textRenderer, TITLE, cx(), p + 6, 0xFFEAD1FF);
        ctx.drawCenteredTextWithShadow(textRenderer, SUBTITLE, cx(), p + 18, 0xFFDDA6FF);

        // Tab highlight underline
        drawTabHighlight(ctx);

        // Tab-specific rendering
        switch (activeTab) {
            case OPTIMIZATION -> renderOptimizationContent(ctx);
            case VISUALS -> renderVisualsContent(ctx, mx, my);
            case TWEAKS -> renderTweaksContent(ctx);
            case SOUND -> renderSoundContent(ctx);
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
                    active.getX() + TAB_W, active.getY() + TAB_H + 2, 0xFFFFE38D);
        }
    }

    // ── Tab: Optimization ──

    private void renderTweaksContent(DrawContext ctx) {
        int y = contentY();
        int lx = contentX();

        if (staticCrystalEnabled) {
            drawDesc(ctx, "Static Crystal keeps the crystal", lx, y + BTN_H + 3);
            drawDesc(ctx, "completely still with no spin", lx, y + BTN_H + 13);
            drawDesc(ctx, "and no floating animation.", lx, y + BTN_H + 23);
            return;
        }

        drawDesc(ctx, "Crystal Flotation toggles the", lx, y + 88);
        drawDesc(ctx, "vertical floating animation.", lx, y + 98);
        drawDesc(ctx, "Spin Speed controls how fast", lx, y + 108);
        drawDesc(ctx, "the crystal rotates.", lx, y + 118);
    }

    private void renderOptimizationContent(DrawContext ctx) {
        int y = contentY();
        int lx = contentX();

        // Description under Local Crystal button
        drawDesc(ctx, "Predicts crystal placement client-side", lx, y + BTN_H + 2);

        // Description under Seamless button
        drawDesc(ctx, "Smooths transition to server crystals", lx, y + ROW_SPACING + BTN_H + 2);
    }

    // ── Tab: Visuals ──

    private void renderVisualsContent(DrawContext ctx, int mx, int my) {
        int cy = contentY();
        int lx = contentX();

        if (!crystalTintEnabled) {
            // Show description when OFF
            drawDesc(ctx, "Apply custom color tints to crystal", lx, cy + BTN_H + 3);
            drawDesc(ctx, "frame (outer) and core (inner) layers.", lx, cy + BTN_H + 13);
            drawDesc(ctx, "Enable to configure colors.", lx, cy + BTN_H + 23);
            return;
        }

        // Picker (only when ON)
        int pickerY = cy + 44;
        renderPicker(ctx, lx, pickerY);

        // Info column (right side)
        int rx = lx + 170;
        int ry = cy + 24;

        ColorState cs = activeColor();
        String editLabel = activeTarget == TintTarget.FRAME ? "Editing: Outer" : "Editing: Inner";
        ctx.drawTextWithShadow(textRenderer, Text.literal(editLabel), rx, ry, 0xFFEAD1FF);
        ctx.drawTextWithShadow(textRenderer, Text.literal(cs.toHex()), rx, ry + 10, 0xFFDDEEFF);

        // Swatches
        drawSwatch(ctx, Text.literal("Outer"), frameColorState,
                activeTarget == TintTarget.FRAME, rx, ry + 24);
        drawSwatch(ctx, Text.literal("Inner"), coreColorState,
                activeTarget == TintTarget.CORE, rx, ry + 24 + SWATCH_H + 4);
    }

    private void renderPicker(DrawContext ctx, int x, int y) {
        ColorState cs = activeColor();
        int hueRgb = 0xFF000000 | MathHelper.hsvToRgb(cs.hue, 1f, 1f);

        for (int dx = 0; dx < PICKER_W; dx++) {
            float s = dx / (float) Math.max(PICKER_W - 1, 1);
            int top = lerpColor(0xFFFFFFFF, hueRgb, s);
            ctx.fillGradient(x + dx, y, x + dx + 1, y + PICKER_H, top, 0xFF000000);
        }
        kctDrawBorder(ctx, x - 1, y - 1, PICKER_W + 2, PICKER_H + 2, 0xE0B86BFF);

        int mX = x + (int) (cs.saturation * PICKER_W);
        int mY = y + PICKER_H - (int) (cs.value * PICKER_H);
        kctDrawBorder(ctx, mX - 3, mY - 3, 7, 7, 0xFFFFFFFF);
        kctDrawBorder(ctx, mX - 2, mY - 2, 5, 5, 0xFF201826);

        int hx = x + PICKER_W + 10;
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
            // Show description when OFF
            drawDesc(ctx, "Replace the default explosion sound", lx, y + BTN_H + 3);
            drawDesc(ctx, "through the vanilla sound pipeline.", lx, y + BTN_H + 13);
            drawDesc(ctx, "Supports WAV, OGG and MP3 formats.", lx, y + BTN_H + 23);
            drawDesc(ctx, "Enable to configure sound settings.", lx, y + BTN_H + 33);
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
    private int hueBarX() { return pickerX() + PICKER_W + HUE_GAP; }

    private boolean inSpectrum(double mx, double my) {
        return mx >= pickerX() && mx < pickerX() + PICKER_W
                && my >= pickerY() && my < pickerY() + PICKER_H;
    }

    private boolean inHueBar(double mx, double my) {
        return mx >= hueBarX() && mx < hueBarX() + HUE_BAR_W
                && my >= pickerY() && my < pickerY() + PICKER_H;
    }

    private void applySV(double mx, double my) {
        ColorState cs = activeColor();
        cs.saturation = MathHelper.clamp((float) ((mx - pickerX()) / Math.max(PICKER_W - 1.0, 1)), 0, 1);
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

    private int drawWrapped(DrawContext ctx, Text text, int cx, int y, int maxW, int color) {
        List<OrderedText> lines = textRenderer.wrapLines(text, maxW);
        int ly = y;
        for (OrderedText line : lines) {
            ctx.drawText(textRenderer, line, cx - textRenderer.getWidth(line) / 2, ly, color, false);
            ly += 10;
        }
        return ly;
    }

    private void drawDesc(DrawContext ctx, String text, int x, int y) {
        ctx.drawText(textRenderer, Text.literal(text), x, y, DESC_COLOR, false);
    }

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
