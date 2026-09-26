package com.zymekoh.crystaltweaks.client;

import com.zymekoh.crystaltweaks.client.CrystalScreenLayout.Group;
import com.zymekoh.crystaltweaks.client.CrystalScreenLayout.Rect;
import com.zymekoh.crystaltweaks.client.CrystalScreenLayout.Slot;
import com.zymekoh.crystaltweaks.client.CrystalScreenLayout.Tab;
import com.zymekoh.crystaltweaks.client.compat.OptimizerConflictDetector;
import com.zymekoh.crystaltweaks.client.sound.CrystalSoundManager;
import com.zymekoh.crystaltweaks.core.CrystalOptimizerGuard;
import com.zymekoh.crystaltweaks.core.GhostCrystalSupport;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.DoubleFunction;
import java.util.function.Function;
import net.fabricmc.loader.api.FabricLoader;
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

/**
 * The Crystal Tweaks settings screen.
 *
 * <p>Four tabs, Colors, Glow, Sound and Advanced, each laid out by {@link CrystalScreenLayout} as
 * cards of related rows beside a live crystal preview. The same screen, opened with the enemy
 * profile, edits the colours and glow of crystals the player did not place.</p>
 *
 * <p>Everything that moves here is decoration or a real-time fade: widgets are placed once per
 * layout and never slide, so what you click is always exactly what is drawn.</p>
 */
public final class CrystalTweaksScreen extends Screen {
    private static final long INTRO_NANOS = 360_000_000L;
    private static final long CONTENT_FADE_NANOS = 190_000_000L;
    private static final long ROW_STAGGER_NANOS = 32_000_000L;
    private static final long INDICATOR_NANOS = 240_000_000L;
    private static final long PREVIEW_APPEAR_NANOS = 460_000_000L;
    private static final long PREVIEW_EXPLOSION_NANOS = 280_000_000L;
    private static final long PREVIEW_RESPAWN_NANOS = 3_000_000_000L;

    private final Screen parent;
    private final boolean enemyEditor;
    private final boolean spanish;
    private final String versionLabel;
    private final EndCrystalRenderState previewState = new EndCrystalRenderState();
    private final AfterglowTimeline<CrystalAfterglowState> previewAfterglows = new AfterglowTimeline<>(4);
    private final List<AbstractWidget> contentWidgets = new ArrayList<>();
    private final Map<AbstractWidget, Integer> contentBaseY = new IdentityHashMap<>();
    private final Map<AbstractWidget, Integer> contentRow = new IdentityHashMap<>();
    private final Map<Tab, Integer> scrollByTab = new EnumMap<>(Tab.class);
    private final List<PurpleCloseButton> tabButtons = new ArrayList<>();

    private CrystalScreenLayout layout;
    private CrystalScreenLayout.Rows rows;
    private List<Tab> tabs = List.of();
    private float[] cardHover = new float[0];
    private Tab activeTab = Tab.COLORS;
    private Layer selectedLayer = Layer.OUTER;
    private PurpleCloseButton resetButton;
    private PurpleCloseButton outerButton;
    private PurpleCloseButton innerButton;
    private PurpleCloseButton coreButton;
    private PurpleCloseButton soundFileButton;
    private PurpleCloseButton ghostCrystalToggle;
    private PurpleCloseButton safeCrystalToggle;
    private PurpleCloseButton rescanButton;
    private EditBox hexBox;
    private ColorPickerWidget colorPicker;
    private boolean updatingControls;
    private boolean rescanReflectsPending;
    private boolean saved;
    private String soundStatus = "";
    private int maxScroll;

    private long openedAt;
    private long lastFrame;
    private long contentShownAt;
    private float indicatorFromX;
    private float indicatorFromWidth;
    private float indicatorToX;
    private float indicatorToWidth;
    private long indicatorStartedAt;
    private Rect statusChip;
    private Status status;

    private long previewPhaseStartedAt;
    private long previewRespawnAt;
    private PreviewPhase previewPhase = PreviewPhase.APPEARING;

    public CrystalTweaksScreen(Screen parent) {
        this(parent, false);
    }

    private CrystalTweaksScreen(Screen parent, boolean enemy) {
        super(Component.literal("Crystal Tweaks KoHs"));
        this.parent = parent;
        this.enemyEditor = enemy;
        this.spanish = usesSpanish();
        this.versionLabel = FabricLoader.getInstance().getModContainer("crystal_tweaks")
                .map(container -> "v" + container.getMetadata().getVersion().getFriendlyString())
                .orElse("");
        this.previewState.entityType = EntityType.END_CRYSTAL;
        this.previewState.showsBottom = false;
        this.previewState.boundingBoxWidth = 2.0F;
        this.previewState.boundingBoxHeight = 2.0F;
        this.previewState.eyeHeight = 1.0F;
        this.previewState.outlineColor = 0;
        // Without this the preview model is lit by nothing and renders black, and no colour the
        // player picks can brighten it. The GUI has no world light to sample, so it gets full block
        // and sky light, which is what vanilla inventory rendering effectively uses.
        this.previewState.lightCoords = 15728880;
    }

    /**
     * What Mod Menu opens: the settings, preceded once per session by the optimizer advice when no
     * crystal optimizer is installed.
     */
    public static Screen open(Screen parent) {
        CrystalTweaksScreen settings = new CrystalTweaksScreen(parent);
        return CrystalNoticeScreen.shouldShow() ? new CrystalNoticeScreen(settings) : settings;
    }

    @Override
    protected void init() {
        CrystalVisualConfig.load();
        long now = System.nanoTime();
        boolean firstOpen = this.openedAt == 0L;
        if (firstOpen) {
            this.openedAt = now;
            this.previewPhaseStartedAt = now;
        }
        this.lastFrame = now;
        this.saved = false;
        this.layout = CrystalScreenLayout.fit(this.width, this.height);
        this.tabs = this.enemyEditor ? List.of(Tab.COLORS, Tab.GLOW) : List.of(Tab.values());
        if (!this.tabs.contains(this.activeTab)) {
            this.activeTab = Tab.COLORS;
        }
        this.contentWidgets.clear();
        this.contentBaseY.clear();
        this.contentRow.clear();
        this.tabButtons.clear();
        addTabs();
        addFooter();
        addActiveTabContent();
        // The first opening fades the rows in behind the panel; a resize or a return from another
        // screen shows them at once, as they were.
        this.contentShownAt = firstOpen ? now + INTRO_NANOS / 3 : now - CONTENT_FADE_NANOS * 10;
        snapIndicator();
    }

    private CrystalAppearance visuals() {
        return CrystalVisualConfig.visuals(this.enemyEditor);
    }

    private void openEnemyEditor() {
        this.minecraft.setScreen(new CrystalTweaksScreen(this, true));
    }

    // ------------------------------------------------------------------------------------------
    // Widgets
    // ------------------------------------------------------------------------------------------

    private void addTabs() {
        List<Rect> rects = this.layout.tabs(this.tabs.size());
        for (int i = 0; i < this.tabs.size(); i++) {
            Tab tab = this.tabs.get(i);
            Rect rect = rects.get(i);
            PurpleCloseButton button = addRenderableWidget(new PurpleCloseButton(
                    rect.x(), rect.y(), rect.width(), rect.height(),
                    Component.literal(tabLabel(tab)),
                    ignored -> switchTab(tab)).icon(tabIcon(tab)));
            button.setSelected(tab == this.activeTab);
            this.tabButtons.add(button);
        }
    }

    private String tabLabel(Tab tab) {
        return switch (tab) {
            case COLORS -> this.spanish ? "Colores" : "Colors";
            case GLOW -> this.spanish ? "Brillo" : "Glow";
            case SOUNDS -> this.spanish ? "Sonido" : "Sound";
            case TWEAKS -> this.spanish ? "Avanzado" : "Advanced";
        };
    }

    private static PurpleCloseButton.Icon tabIcon(Tab tab) {
        return switch (tab) {
            case COLORS -> PurpleCloseButton.Icon.CRYSTAL;
            case GLOW -> PurpleCloseButton.Icon.GLOW;
            case SOUNDS -> PurpleCloseButton.Icon.SOUND;
            case TWEAKS -> PurpleCloseButton.Icon.GEAR;
        };
    }

    private void addFooter() {
        List<Rect> buttons = this.layout.footerButtons();
        Rect reset = buttons.get(0);
        Rect done = buttons.get(1);
        this.resetButton = addRenderableWidget(new PurpleCloseButton(
                reset.x(), reset.y(), reset.width(), reset.height(),
                Component.literal(this.spanish ? "Restablecer" : "Reset"),
                ignored -> resetActiveTab()));
        this.resetButton.setTooltip(Tooltip.create(Component.literal(this.spanish
                ? "Devuelve esta pestaña a sus valores de fábrica."
                : "Returns this tab to its default values.")));
        this.resetButton.active = this.activeTab != Tab.TWEAKS;
        addRenderableWidget(new PurpleCloseButton(
                done.x(), done.y(), done.width(), done.height(),
                Component.literal(this.spanish ? "Listo" : "Done"),
                ignored -> onClose()));
    }

    private void addActiveTabContent() {
        this.outerButton = null;
        this.innerButton = null;
        this.coreButton = null;
        this.soundFileButton = null;
        this.ghostCrystalToggle = null;
        this.safeCrystalToggle = null;
        this.rescanButton = null;
        this.hexBox = null;
        this.colorPicker = null;
        this.rows = this.layout.rows(this.activeTab, new CrystalScreenLayout.Content(
                this.enemyEditor,
                CrystalVisualConfig.flashStyle().scalable(),
                visuals().customGlowColor,
                GhostCrystalSupport.isAvailable()));
        this.cardHover = new float[this.rows.groups().size()];
        switch (this.activeTab) {
            case COLORS -> addColorControls();
            case GLOW -> addGlowControls();
            case SOUNDS -> addSoundControls();
            case TWEAKS -> addTweaksControls();
        }
        finishContentLayout();
    }

    private void addColorControls() {
        this.outerButton = addContent("layer.outer", rect -> layerButton(rect, Layer.OUTER));
        this.innerButton = addContent("layer.inner", rect -> layerButton(rect, Layer.INNER));
        this.coreButton = addContent("layer.core", rect -> layerButton(rect, Layer.CORE));
        this.hexBox = addContent("hex", rect -> new EditBox(this.font, rect.x(), rect.y(), rect.width(), rect.height(),
                Component.literal(this.spanish ? "Color hexadecimal" : "Hex color")));
        this.hexBox.setMaxLength(7);
        this.hexBox.setResponder(this::onHexChanged);
        this.colorPicker = addContent("picker", rect -> new ColorPickerWidget(rect.x(), rect.y(), rect.width(),
                rect.height(), Component.literal(this.spanish ? "Selector de color" : "Color picker"),
                selectedColor(), this::onPickerChanged));
        addContent("rotation", rect -> new CompactSlider(rect, 0.0D, 300.0D, visuals().rotationSpeedPercent,
                value -> visuals().rotationSpeedPercent = (int) Math.round(value),
                value -> animationSpeedLabel(this.spanish ? "Giro" : "Rotation", (int) Math.round(value))));
        addContent("floating", rect -> new CompactSlider(rect, 0.0D, 300.0D, visuals().floatingSpeedPercent,
                value -> visuals().floatingSpeedPercent = (int) Math.round(value),
                value -> animationSpeedLabel(this.spanish ? "Flotación" : "Floating", (int) Math.round(value))));
        if (this.enemyEditor) {
            PurpleCloseButton enemy = addContent("profile", rect -> new PurpleCloseButton(
                    rect.x(), rect.y(), rect.width(), rect.height(),
                    Component.literal(this.spanish ? "Usar este perfil" : "Use this profile"),
                    ignored -> {
                        CrystalVisualConfig.setEnemyCustomEnabled(!CrystalVisualConfig.enemyCustomEnabled());
                        CrystalVisualConfig.save();
                    }).switchOf(CrystalVisualConfig::enemyCustomEnabled));
            enemy.setTooltip(Tooltip.create(Component.literal(this.spanish
                    ? "Apagado, los cristales ajenos se ven con tu propio perfil."
                    : "When off, crystals you did not place use your own profile.")));
        } else {
            PurpleCloseButton enemy = addContent("profile", rect -> new PurpleCloseButton(
                    rect.x(), rect.y(), rect.width(), rect.height(),
                    Component.literal(this.spanish ? "Cristales ajenos…" : "Enemy crystals…"),
                    ignored -> openEnemyEditor()));
            enemy.setTooltip(Tooltip.create(Component.literal(this.spanish
                    ? "Identificación aproximada: se relacionan tus intentos de colocación con las apariciones. Los cristales sin coincidencia, incluidos los desconocidos, usan ese perfil; el servidor no envía su propietario."
                    : "Approximate attribution: matches your placement attempts to spawns. Unmatched crystals, including unknown owners, use that profile; the server does not send ownership.")));
        }
        selectLayer(this.selectedLayer);
    }

    private PurpleCloseButton layerButton(Rect rect, Layer layer) {
        String name = switch (layer) {
            case OUTER -> this.spanish ? "Exterior" : "Outer";
            case INNER -> this.spanish ? "Interior" : "Inner";
            case CORE -> this.spanish ? "Núcleo" : "Core";
            case GLOW -> "Glow";
        };
        return new PurpleCloseButton(rect.x(), rect.y(), rect.width(), rect.height(), Component.literal(name),
                ignored -> selectLayer(layer));
    }

    private void addGlowControls() {
        addContent("power", rect -> new CompactSlider(rect, 0, 300, visuals().glowPowerPercent,
                value -> visuals().glowPowerPercent = (int) Math.round(value),
                value -> (this.spanish ? "Potencia: " : "Power: ") + Math.round(value) + "%"));
        CompactSlider reflections = addContent("reflections", rect -> new CompactSlider(rect, 0, 300,
                visuals().glowReflectionsPercent,
                value -> visuals().glowReflectionsPercent = (int) Math.round(value),
                value -> (this.spanish ? "Reflejos: " : "Reflections: ") + Math.round(value) + "%"));
        reflections.setTooltip(Tooltip.create(Component.literal(this.spanish
                ? "Luz de color simulada en las caras superiores de bloques cercanos. Requiere potencia de brillo; no modifica la iluminación del mundo."
                : "Simulated coloured light on nearby block tops. Requires glow power; does not change world lighting.")));

        // The flash is glow: same colour, same material, same power switch.
        addContent("flash.previous", rect -> new PurpleCloseButton(rect.x(), rect.y(), rect.width(), rect.height(),
                Component.empty(), ignored -> changeFlashStyle(CrystalVisualConfig.flashStyle().previous()))
                .icon(PurpleCloseButton.Icon.ARROW_LEFT));
        PurpleCloseButton style = addContent("flash.style", rect -> new PurpleCloseButton(
                rect.x(), rect.y(), rect.width(), rect.height(), flashStyleMessage(),
                ignored -> changeFlashStyle(CrystalVisualConfig.flashStyle().next())));
        style.setTooltip(Tooltip.create(Component.literal(this.spanish
                ? "Forma del destello que deja un cristal al explotar, en este mismo color. Requiere potencia de brillo. Es solo dibujo: no cambia la explosión, el daño ni ningún paquete. Pulsa el cristal de la vista previa para verlo."
                : "Shape of the flash a crystal leaves when it explodes, in this same colour. Requires glow power. Drawing only: it changes no explosion, no damage and no packet. Click the preview crystal to see it.")));
        addContent("flash.next", rect -> new PurpleCloseButton(rect.x(), rect.y(), rect.width(), rect.height(),
                Component.empty(), ignored -> changeFlashStyle(CrystalVisualConfig.flashStyle().next()))
                .icon(PurpleCloseButton.Icon.ARROW_RIGHT));
        if (this.rows.slot("flash.size") != null) {
            addContent("flash.size", rect -> new CompactSlider(rect, CrystalAppearance.MIN_FLASH_SCALE, 300,
                    visuals().flashScalePercent,
                    value -> visuals().flashScalePercent = (int) Math.round(value),
                    value -> (this.spanish ? "Tamaño del destello: " : "Flash size: ") + Math.round(value) + "%"));
        }

        PurpleCloseButton colour = addContent("glow.color", rect -> new PurpleCloseButton(
                rect.x(), rect.y(), rect.width(), rect.height(),
                Component.literal(this.spanish ? "Color propio" : "Own colour"),
                ignored -> toggleGlowColor()).switchOf(() -> visuals().customGlowColor));
        colour.setTooltip(Tooltip.create(Component.literal(this.spanish
                ? "Elige el color del brillo y del destello. Los colores del cristal no cambian."
                : "Chooses the colour of the glow and the flash. The crystal's own colours are untouched.")));
        if (this.rows.slot("hex") != null) {
            this.hexBox = addContent("hex", rect -> new EditBox(this.font, rect.x(), rect.y(), rect.width(),
                    rect.height(), Component.literal(this.spanish ? "Color de brillo hexadecimal" : "Glow hex color")));
            this.hexBox.setMaxLength(7);
            this.hexBox.setResponder(this::onHexChanged);
        }
        if (this.rows.slot("picker") != null) {
            this.colorPicker = addContent("picker", rect -> new ColorPickerWidget(rect.x(), rect.y(), rect.width(),
                    rect.height(), Component.literal(this.spanish ? "Color de brillo" : "Glow color"),
                    selectedColor(), this::onPickerChanged));
        }
        if (this.hexBox != null) {
            this.updatingControls = true;
            this.hexBox.setValue(CrystalVisualConfig.toHex(selectedColor()));
            this.updatingControls = false;
        }
    }

    private void addSoundControls() {
        addContent("sound.toggle", rect -> new PurpleCloseButton(
                rect.x(), rect.y(), rect.width(), rect.height(),
                Component.literal(this.spanish ? "Sonido personalizado" : "Custom sound"),
                ignored -> toggleSound()).switchOf(CrystalVisualConfig::customSoundEnabled)
                .icon(PurpleCloseButton.Icon.SOUND));
        this.soundFileButton = addContent("sound.file", rect -> new PurpleCloseButton(
                rect.x(), rect.y(), rect.width(), rect.height(),
                Component.literal(this.spanish ? "Seleccionar archivo…" : "Select file…"),
                ignored -> openSoundPicker()));
        this.soundFileButton.active = CrystalVisualConfig.customSoundEnabled();
        addContent("sound.volume", rect -> new CompactSlider(rect, 0.0D, 2.0D, CrystalVisualConfig.soundVolume(),
                value -> CrystalVisualConfig.setSoundVolume(value.floatValue()),
                value -> (this.spanish ? "Volumen: " : "Volume: ") + Math.round(value * 100.0D) + "%"));
        addContent("sound.speed", rect -> new CompactSlider(rect, 0.5D, 2.0D, CrystalVisualConfig.soundSpeed(),
                value -> CrystalVisualConfig.setSoundSpeed(value.floatValue()),
                value -> String.format(Locale.ROOT, this.spanish ? "Velocidad: %.2fx" : "Speed: %.2fx", value)));
    }

    private void addTweaksControls() {
        if (this.rows.slot("ghost") != null) {
            this.ghostCrystalToggle = addContent("ghost", rect -> new PurpleCloseButton(
                    rect.x(), rect.y(), rect.width(), rect.height(), ghostCrystalMessage(),
                    ignored -> toggleGhostCrystals()).switchOf(CrystalVisualConfig::ghostCrystals));
            this.ghostCrystalToggle.setTooltip(Tooltip.create(Component.literal(this.spanish
                    ? "Dibuja un cristal provisional mientras llega el del servidor, para que colocar no dependa de tu ping. Es solo visual: no es una entidad, no se puede golpear ni mirar, y no cambia ningún paquete."
                    : "Draws a stand-in crystal while the server's real one is in flight, so placing does not depend on your ping. Purely visual: it is not an entity, cannot be hit or looked at, and changes no packet.")));
            this.ghostCrystalToggle.active = CrystalOptimizerGuard.optimizationsAllowed();
        }
        this.safeCrystalToggle = addContent("safe", rect -> new PurpleCloseButton(
                rect.x(), rect.y(), rect.width(), rect.height(), safeCrystalMessage(),
                ignored -> toggleSafeCrystal()).switchOf(CrystalVisualConfig::safeCrystal));
        this.safeCrystalToggle.setTooltip(Tooltip.create(Component.literal(this.spanish
                ? "Evita minar la obsidiana sobre la que pones cristales mientras sostienes uno. Envía menos acciones que Vanilla; apágalo si tu servidor no permite filtros de entrada."
                : "Keeps you from mining the obsidian under your crystals while you hold one. It sends fewer actions than Vanilla; turn it off if your server forbids input filters.")));
        this.safeCrystalToggle.active = CrystalOptimizerGuard.optimizationsAllowed();

        PurpleCloseButton monitor = addContent("monitor", rect -> new PurpleCloseButton(
                rect.x(), rect.y(), rect.width(), rect.height(),
                Component.literal(this.spanish ? "Monitor de conflictos" : "Conflict Monitor"),
                ignored -> openConflictMonitor()).icon(PurpleCloseButton.Icon.GEAR));
        monitor.setTooltip(Tooltip.create(Component.literal(this.spanish
                ? "Analiza localmente los mixins instalados y muestra coincidencias de clase y método que podrían interferir con Crystal Tweaks."
                : "Locally scans installed mixins and shows matching classes and methods that could interfere with Crystal Tweaks.")));
        this.rescanButton = addContent("rescan", rect -> new PurpleCloseButton(
                rect.x(), rect.y(), rect.width(), rect.height(), rescanButtonMessage(),
                ignored -> rescanCompatibility()));
        this.rescanButton.setTooltip(Tooltip.create(Component.literal(this.spanish
                ? "Repite la comprobación de optimizadores sin reiniciar. Útil tras quitar el mod que dejó las ayudas en pausa."
                : "Runs the optimizer check again without restarting. Useful after removing the mod that paused the helpers.")));
        this.rescanReflectsPending = CrystalOptimizerGuard.scanPending();
        this.rescanButton.active = !this.rescanReflectsPending;
    }

    private <T extends AbstractWidget> T addContent(String name, Function<Rect, T> factory) {
        Slot slot = this.rows.slot(name);
        T widget = factory.apply(slot.rect());
        this.contentWidgets.add(widget);
        this.contentBaseY.put(widget, slot.rect().y());
        this.contentRow.put(widget, slot.row());
        return addRenderableWidget(widget);
    }

    private void finishContentLayout() {
        this.maxScroll = Math.max(0, this.rows.bottom() - this.layout.content.height());
        setCurrentScroll(Mth.clamp(currentScroll(), 0, this.maxScroll));
        applyContentScroll();
    }

    // ------------------------------------------------------------------------------------------
    // State changes
    // ------------------------------------------------------------------------------------------

    private void switchTab(Tab tab) {
        if (this.activeTab == tab) {
            return;
        }
        float fromX = currentIndicatorX();
        float fromWidth = currentIndicatorWidth();
        this.activeTab = tab;
        for (int i = 0; i < this.tabButtons.size(); i++) {
            this.tabButtons.get(i).setSelected(this.tabs.get(i) == tab);
        }
        this.resetButton.active = tab != Tab.TWEAKS;
        rebuildActiveContent();
        long now = System.nanoTime();
        this.contentShownAt = now;
        Rect to = tabRect(tab);
        if (to != null) {
            this.indicatorFromX = fromX;
            this.indicatorFromWidth = fromWidth;
            this.indicatorToX = to.x();
            this.indicatorToWidth = to.width();
            this.indicatorStartedAt = now;
        }
    }

    private void rebuildActiveContent() {
        for (AbstractWidget widget : this.contentWidgets) {
            removeWidget(widget);
        }
        this.contentWidgets.clear();
        this.contentBaseY.clear();
        this.contentRow.clear();
        addActiveTabContent();
    }

    private int currentScroll() {
        return this.scrollByTab.getOrDefault(this.activeTab, 0);
    }

    private void setCurrentScroll(int value) {
        this.scrollByTab.put(this.activeTab, value);
    }

    private void applyContentScroll() {
        int scroll = currentScroll();
        Rect viewport = this.layout.content;
        for (AbstractWidget widget : this.contentWidgets) {
            Integer baseY = this.contentBaseY.get(widget);
            if (baseY == null) {
                continue;
            }
            int y = baseY - scroll;
            widget.setY(y);
            // Hidden rather than clipped: a half-visible widget would still take clicks outside the
            // viewport, where the player cannot see it.
            widget.visible = y >= viewport.y() && y + widget.getHeight() <= viewport.bottom();
        }
    }

    private Layer activeLayer() {
        return this.activeTab == Tab.GLOW ? Layer.GLOW : this.selectedLayer;
    }

    private void selectLayer(Layer layer) {
        if (layer != Layer.GLOW) {
            this.selectedLayer = layer;
        }
        int color = selectedColor();
        this.updatingControls = true;
        if (this.colorPicker != null) {
            this.colorPicker.setColor(color);
        }
        if (this.hexBox != null) {
            this.hexBox.setValue(CrystalVisualConfig.toHex(color));
            this.hexBox.setTextColor(0xFFF0E5F6);
        }
        this.updatingControls = false;
        if (this.outerButton != null) {
            this.outerButton.setSelected(this.selectedLayer == Layer.OUTER);
            this.innerButton.setSelected(this.selectedLayer == Layer.INNER);
            this.coreButton.setSelected(this.selectedLayer == Layer.CORE);
        }
    }

    private void onPickerChanged(int color) {
        if (this.updatingControls) {
            return;
        }
        setSelectedColor(color);
        this.updatingControls = true;
        if (this.hexBox != null) {
            this.hexBox.setValue(CrystalVisualConfig.toHex(color));
            this.hexBox.setTextColor(0xFFF0E5F6);
        }
        this.updatingControls = false;
    }

    private void onHexChanged(String value) {
        if (this.updatingControls) {
            return;
        }
        try {
            int color = CrystalVisualConfig.parseHex(value);
            setSelectedColor(color);
            if (this.colorPicker != null) {
                this.colorPicker.setColor(color);
            }
            if (this.hexBox != null) {
                this.hexBox.setTextColor(0xFFF0E5F6);
            }
        } catch (IllegalArgumentException exception) {
            if (this.hexBox != null) {
                this.hexBox.setTextColor(0xFFFF7D9C);
            }
        }
    }

    private void setSelectedColor(int color) {
        switch (activeLayer()) {
            case OUTER -> visuals().outerColor = color;
            case INNER -> visuals().innerColor = color;
            case CORE -> visuals().coreColor = color;
            case GLOW -> visuals().glowColor = color;
        }
    }

    private int selectedColor() {
        return switch (activeLayer()) {
            case OUTER -> visuals().outerColor;
            case INNER -> visuals().innerColor;
            case CORE -> visuals().coreColor;
            case GLOW -> visuals().glowColor;
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

    private Component flashStyleMessage() {
        return Component.literal((this.spanish ? "Destello: " : "Flash: ")
                + CrystalVisualConfig.flashStyle().label(this.spanish));
    }

    private void changeFlashStyle(CrystalFlashStyle style) {
        CrystalVisualConfig.setFlashStyle(style);
        CrystalVisualConfig.save();
        // The size slider belongs to the shaped styles only, so the row list changes with it.
        rebuildActiveContent();
        // Show the new shape at once instead of making the player go and blow up a crystal.
        long now = System.nanoTime();
        if (this.layout.preview.width() > 0 && visuals().glowPowerPercent > 0) {
            // Silent: browsing fourteen styles should not mean fourteen explosions in your ears.
            this.previewAfterglows.clear();
            startPreviewAppearance(now);
            explodePreview(now, false);
        }
    }

    private void toggleGlowColor() {
        visuals().customGlowColor = !visuals().customGlowColor;
        CrystalVisualConfig.save();
        rebuildActiveContent();
    }

    private void toggleSound() {
        CrystalVisualConfig.setCustomSoundEnabled(!CrystalVisualConfig.customSoundEnabled());
        CrystalVisualConfig.save();
        CrystalSoundManager.reloadFromConfig();
        if (this.soundFileButton != null) {
            this.soundFileButton.active = CrystalVisualConfig.customSoundEnabled();
        }
    }

    private void toggleGhostCrystals() {
        CrystalVisualConfig.setGhostCrystals(!CrystalVisualConfig.ghostCrystals());
        CrystalVisualConfig.save();
    }

    private void toggleSafeCrystal() {
        CrystalVisualConfig.setSafeCrystal(!CrystalVisualConfig.safeCrystal());
        CrystalVisualConfig.save();
    }

    private Component ghostCrystalMessage() {
        String label = this.spanish ? "Cristales fantasma" : "Ghost crystals";
        return Component.literal(CrystalOptimizerGuard.optimizationsAllowed()
                ? label : label + (this.spanish ? " (en pausa)" : " (paused)"));
    }

    private Component safeCrystalMessage() {
        String label = this.spanish ? "Cristal seguro" : "Safe Crystal";
        return Component.literal(CrystalOptimizerGuard.optimizationsAllowed()
                ? label : label + (this.spanish ? " (en pausa)" : " (paused)"));
    }

    private Component rescanButtonMessage() {
        if (CrystalOptimizerGuard.scanPending()) {
            return Component.literal(this.spanish ? "Comprobando…" : "Checking…");
        }
        return Component.literal(this.spanish ? "Volver a comprobar compatibilidad" : "Re-check compatibility");
    }

    private void rescanCompatibility() {
        OptimizerConflictDetector.rescan();
        refreshOptimizerControls();
    }

    /**
     * Keeps the Advanced controls in step with a scan that finishes on another thread, so a re-scan
     * reports its own result instead of waiting for the screen to be reopened.
     */
    private void refreshOptimizerControls() {
        boolean pending = CrystalOptimizerGuard.scanPending();
        if (this.rescanButton == null || pending == this.rescanReflectsPending) {
            return;
        }
        this.rescanReflectsPending = pending;
        this.rescanButton.setMessage(rescanButtonMessage());
        this.rescanButton.active = !pending;
        boolean allowed = CrystalOptimizerGuard.optimizationsAllowed();
        if (this.ghostCrystalToggle != null) {
            this.ghostCrystalToggle.setMessage(ghostCrystalMessage());
            this.ghostCrystalToggle.active = allowed;
        }
        if (this.safeCrystalToggle != null) {
            this.safeCrystalToggle.setMessage(safeCrystalMessage());
            this.safeCrystalToggle.active = allowed;
        }
    }

    private void openConflictMonitor() {
        this.minecraft.setScreen(new ConflictMonitorScreen(this));
    }

    private void openSoundPicker() {
        SoundFilePicker.choose(this.spanish, this::importSound, message -> this.soundStatus = message);
    }

    private void importSound(Path file) {
        String error = CrystalSoundManager.importFile(file);
        this.soundStatus = error.isEmpty() ? (this.spanish ? "Sonido cargado" : "Sound loaded") : localizeSoundError(error);
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
            case COLORS -> {
                visuals().outerColor = -1;
                visuals().innerColor = -1;
                visuals().coreColor = -1;
                visuals().rotationSpeedPercent = 100;
                visuals().floatingSpeedPercent = 100;
            }
            case GLOW -> {
                CrystalAppearance defaults = CrystalVisualConfig.defaults(this.enemyEditor);
                visuals().glowPowerPercent = defaults.glowPowerPercent;
                visuals().glowReflectionsPercent = defaults.glowReflectionsPercent;
                visuals().customGlowColor = defaults.customGlowColor;
                visuals().glowColor = defaults.glowColor;
                visuals().flashScalePercent = defaults.flashScalePercent;
                if (!this.enemyEditor) {
                    CrystalVisualConfig.setFlashStyle(CrystalFlashStyle.EXPLOSION);
                }
            }
            case SOUNDS -> {
                CrystalVisualConfig.setCustomSoundEnabled(false);
                CrystalVisualConfig.setCustomSoundFileName("");
                CrystalVisualConfig.setSoundVolume(1.0F);
                CrystalVisualConfig.setSoundSpeed(1.0F);
                this.soundStatus = "";
                CrystalSoundManager.reloadFromConfig();
            }
            case TWEAKS -> {
                return;
            }
        }
        CrystalVisualConfig.save();
        rebuildActiveContent();
    }

    // ------------------------------------------------------------------------------------------
    // Drawing
    // ------------------------------------------------------------------------------------------

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, CrystalTheme.BACKDROP);
        this.minecraft.gui.extractDeferredSubtitles();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        long now = System.nanoTime();
        float frameMillis = Mth.clamp((now - this.lastFrame) / 1_000_000.0F, 0.0F, 50.0F);
        this.lastFrame = now;
        double seconds = now / 1_000_000_000.0D;
        float intro = CrystalTheme.easeOutCubic(progress(now - this.openedAt, INTRO_NANOS));

        CrystalUi.floatingParticles(graphics, this.width, this.height, seconds);
        drawPanel(graphics, intro, seconds);
        drawHeader(graphics, seconds, intro);
        refreshOptimizerControls();
        updateWidgetFades(now);
        drawCards(graphics, mouseX, mouseY, now, frameMillis);
        if (this.activeTab == Tab.COLORS) {
            drawColorSelection(graphics);
        } else if (this.activeTab == Tab.SOUNDS) {
            drawSoundStatus(graphics);
        }
        drawPreview(graphics, now, mouseX, mouseY);
        drawTabIndicator(graphics, now);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.nextStratum();
        drawScrollbar(graphics);
        drawFooterInfo(graphics);
        drawStatusTooltip(graphics, mouseX, mouseY);
    }

    private void drawPanel(GuiGraphicsExtractor graphics, float intro, double seconds) {
        Rect panel = this.layout.panel;
        int width = Math.max(1, Math.round(panel.width() * (0.9F + intro * 0.1F)));
        int height = Math.max(1, Math.round(panel.height() * (0.88F + intro * 0.12F)));
        int x = panel.x() + (panel.width() - width) / 2;
        int y = panel.y() + (panel.height() - height) / 2;
        CrystalUi.panel(graphics, x, y, width, height,
                CrystalTheme.fade(CrystalTheme.PANEL_TOP, 0.4F + intro * 0.6F),
                CrystalTheme.fade(CrystalTheme.PANEL_BOTTOM, 0.4F + intro * 0.6F));
        CrystalUi.roundedOutline(graphics, x, y, width, height, CrystalTheme.fade(CrystalTheme.PANEL_BORDER, intro));
        if (intro < 0.98F) {
            return;
        }
        CrystalUi.comets(graphics, panel.x(), panel.y(), panel.width(), panel.height(), seconds, 0xFFE6C4FF);
        Rect content = this.layout.content;
        graphics.fillGradient(content.x(), content.y(), content.right(), content.bottom(),
                CrystalTheme.CONTENT_TOP, CrystalTheme.CONTENT_BOTTOM);
        int lineY = panel.y() + this.layout.headerHeight - 1;
        graphics.fill(panel.x() + 8, lineY, panel.right() - 8, lineY + 1, CrystalTheme.HEADER_LINE);
        drawPanelParticles(graphics, seconds);
    }

    private void drawPanelParticles(GuiGraphicsExtractor graphics, double seconds) {
        Rect panel = this.layout.panel;
        int count = Mth.clamp(panel.width() * panel.height() / 2_800, 28, 54);
        int horizontalSpan = Math.max(1, panel.width() - 8);
        int verticalSpan = Math.max(1, panel.height() - 8);
        for (int index = 0; index < count; index++) {
            double phase = index * 1.32471795724D;
            int baseX = panel.x() + 4 + Math.floorMod(index * 67 + 19, horizontalSpan);
            int x = baseX + (int) Math.round(Math.sin(seconds * 0.48D + phase) * (2 + index % 4));
            int y = panel.bottom() - 5 - (int) ((seconds * (2.0D + index % 5) + phase * verticalSpan) % verticalSpan);
            int size = index % 8 == 0 ? 3 : index % 3 == 0 ? 2 : 1;
            x = Mth.clamp(x, panel.x() + 2, Math.max(panel.x() + 2, panel.right() - size - 2));
            y = Mth.clamp(y, panel.y() + 2, Math.max(panel.y() + 2, panel.bottom() - size - 2));
            int alpha = 52 + index % 6 * 9;
            int glow = alpha / 3 << 24 | 140 << 16 | 45 << 8 | 220;
            int color = alpha << 24 | 224 << 16 | (92 + index % 4 * 18) << 8 | 255;
            graphics.fill(x - 1, y - 1, x + size + 1, y + size + 1, glow);
            graphics.fill(x, y, x + size, y + size, color);
        }
    }

    private void drawHeader(GuiGraphicsExtractor graphics, double seconds, float intro) {
        Rect panel = this.layout.panel;
        int titleY = this.layout.titleY;
        int iconX = panel.x() + 13;
        CrystalUi.crystalIcon(graphics, iconX, titleY + 4, 9, seconds, intro);
        String title = this.enemyEditor
                ? (this.spanish ? "Cristales ajenos" : "Enemy crystals")
                : "Crystal Tweaks";
        int titleX = iconX + 9;
        CrystalUi.label(graphics, this.font, title, titleX, titleY, CrystalTheme.fade(CrystalTheme.TITLE, intro));
        int titleRight = titleX + this.font.width(title);
        String brand = " KoHs";
        CrystalUi.label(graphics, this.font, brand, titleRight, titleY, CrystalTheme.fade(0xFF9C7BB8, intro));
        titleRight += this.font.width(brand);
        CrystalUi.shimmer(graphics, titleX - 2, titleY - 1, titleRight + 2, titleY + 9, seconds + 1.3D);

        this.status = currentStatus();
        String chip = this.status.chip();
        int chipWidth = this.font.width(chip) + 18;
        int chipRight = panel.right() - 9;
        boolean textFits = chipRight - chipWidth > titleRight + 8;
        if (!textFits) {
            chipWidth = 12;
        }
        int chipX = chipRight - chipWidth;
        int chipY = titleY - 2;
        this.statusChip = new Rect(chipX, chipY, chipWidth, 12);
        CrystalUi.panel(graphics, chipX, chipY, chipWidth, 12,
                CrystalTheme.fade(0x9A1B0928, intro), CrystalTheme.fade(0x9A12061C, intro));
        CrystalUi.roundedOutline(graphics, chipX, chipY, chipWidth, 12,
                CrystalTheme.fade(CrystalTheme.withAlpha(this.status.color(), 150), intro));
        double beat = this.status.color() == CrystalTheme.STATUS_CHECKING ? 7.0D : 2.6D;
        float pulse = 0.55F + 0.45F * (float) Math.sin(seconds * beat);
        int dotX = chipX + 4;
        int dotY = chipY + 4;
        graphics.fill(dotX - 1, dotY - 1, dotX + 5, dotY + 5,
                CrystalTheme.fade(CrystalTheme.withAlpha(this.status.color(), Math.round(90 * pulse)), intro));
        graphics.fill(dotX, dotY, dotX + 4, dotY + 4, CrystalTheme.fade(this.status.color(), intro));
        if (textFits) {
            CrystalUi.label(graphics, this.font, chip, chipX + 13, chipY + 2,
                    CrystalTheme.fade(CrystalTheme.lerp(this.status.color(), 0xFFFFFFFF, 0.35F), intro));
        }
    }

    private void drawStatusTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (this.statusChip == null || this.status == null || !this.statusChip.contains(mouseX, mouseY)) {
            return;
        }
        List<FormattedCharSequence> lines = CrystalUi.wrap(this.font, this.status.detail(),
                Math.min(220, this.width - 30));
        CrystalUi.tooltip(graphics, this.font, lines, mouseX, mouseY, this.width, this.height);
    }

    private Status currentStatus() {
        if (CrystalOptimizerGuard.conflictDetected()) {
            String other = CrystalOptimizerGuard.conflictingModName();
            return new Status(CrystalTheme.STATUS_PAUSED,
                    this.spanish ? "En pausa" : "Paused",
                    this.spanish
                            ? "Optimización en pausa: " + other + " ya optimiza cristales. Colores, brillo y sonidos siguen activos."
                            : "Optimization paused: " + other + " already optimizes crystals. Colours, glow and sounds keep working.");
        }
        if (CrystalOptimizerGuard.scanPending()) {
            return new Status(CrystalTheme.STATUS_CHECKING,
                    this.spanish ? "Comprobando…" : "Checking…",
                    this.spanish
                            ? "Ayudas en pausa mientras se comprueba la compatibilidad con otros mods."
                            : "Interaction helpers wait while compatibility with other mods is checked.");
        }
        if (CrystalOptimizerGuard.scanIncomplete()) {
            return new Status(CrystalTheme.STATUS_PARTIAL,
                    this.spanish ? "Activo · parcial" : "Active · partial",
                    this.spanish
                            ? "Ayudas activas: la comprobación de compatibilidad no pudo leer todos los mods."
                            : "Interaction helpers active: the compatibility check could not read every mod.");
        }
        return new Status(CrystalTheme.STATUS_ACTIVE,
                this.spanish ? "Cliente activo" : "Client active",
                this.spanish
                        ? "Crystal Tweaks optimiza solo tu cliente: un cristal roto desaparece al instante en tu pantalla, pero lo que llega al servidor no se acelera."
                        : "Crystal Tweaks only optimizes your client: a broken crystal disappears instantly on your screen, but what reaches the server is not sped up.");
    }

    private void updateWidgetFades(long now) {
        for (AbstractWidget widget : this.contentWidgets) {
            Integer row = this.contentRow.get(widget);
            widget.setAlpha(rowFade(now, row == null ? 0 : row));
        }
    }

    /**
     * How far a row has faded in. Never below 0.35: a control is always drawn where it can be
     * clicked, so the entrance can never hide a live hitbox.
     */
    private float rowFade(long now, int row) {
        long elapsed = now - this.contentShownAt - row * ROW_STAGGER_NANOS;
        return 0.35F + 0.65F * CrystalTheme.easeOutCubic(progress(elapsed, CONTENT_FADE_NANOS));
    }

    private void drawCards(GuiGraphicsExtractor graphics, int mouseX, int mouseY, long now, float frameMillis) {
        List<Group> groups = this.rows.groups();
        if (groups.isEmpty()) {
            return;
        }
        Rect viewport = this.layout.content;
        int scroll = currentScroll();
        float response = 1.0F - (float) Math.exp(-frameMillis / 80.0F);
        graphics.enableScissor(this.layout.optionsX - 5, viewport.y(),
                this.layout.optionsX + this.layout.optionsWidth + 5, viewport.bottom());
        for (int index = 0; index < groups.size(); index++) {
            Group group = groups.get(index);
            Rect rect = group.rect();
            int y = rect.y() - scroll;
            boolean hovered = viewport.contains(mouseX, mouseY)
                    && mouseX >= rect.x() && mouseX < rect.right() && mouseY >= y && mouseY < y + rect.height();
            if (index < this.cardHover.length) {
                this.cardHover[index] += ((hovered ? 1.0F : 0.0F) - this.cardHover[index]) * response;
            }
            float hover = index < this.cardHover.length ? this.cardHover[index] : 0.0F;
            String legend = this.layout.legends ? legend(group.key()) : null;
            CrystalUi.card(graphics, this.font, rect.x(), y, rect.width(), rect.height(), legend, hover,
                    rowFade(now, group.firstRow()));
        }
        graphics.disableScissor();
    }

    private String legend(String key) {
        return switch (key) {
            case "colors" -> this.spanish ? "Color de las capas" : "Layer colours";
            case "colors.enemy" -> this.spanish ? "Color de las capas · ajenos" : "Layer colours · enemy";
            case "animation" -> this.spanish ? "Animación" : "Animation";
            case "profile" -> this.spanish ? "Perfiles" : "Profiles";
            case "profile.enemy" -> this.spanish ? "Perfil ajeno" : "Enemy profile";
            case "glow" -> this.spanish ? "Brillo" : "Glow";
            case "flash" -> this.spanish ? "Destello al explotar" : "Flash on explosion";
            case "glow.color" -> this.spanish ? "Color del brillo" : "Glow colour";
            case "sound.file" -> this.spanish ? "Explosión personalizada" : "Custom explosion";
            case "sound.playback" -> this.spanish ? "Reproducción" : "Playback";
            case "helpers" -> this.spanish ? "Ayudas de cristal" : "Crystal helpers";
            case "compatibility" -> this.spanish ? "Compatibilidad" : "Compatibility";
            default -> "";
        };
    }

    private void drawColorSelection(GuiGraphicsExtractor graphics) {
        if (this.hexBox == null || !this.hexBox.visible) {
            return;
        }
        int swatchSize = Math.min(11, Math.max(6, this.hexBox.getHeight() - 5));
        int swatchX = Math.min(this.layout.optionsX + this.layout.optionsWidth - swatchSize, this.hexBox.getRight() + 5);
        int swatchY = this.hexBox.getY() + (this.hexBox.getHeight() - swatchSize) / 2;
        graphics.fill(swatchX, swatchY, swatchX + swatchSize, swatchY + swatchSize, selectedColor());
        CrystalUi.outline(graphics, swatchX, swatchY, swatchSize, swatchSize, 0xFFE8D8F1);
        String layer = switch (this.selectedLayer) {
            case OUTER -> this.spanish ? "Editando: exterior" : "Editing: outer";
            case INNER -> this.spanish ? "Editando: interior" : "Editing: inner";
            default -> this.spanish ? "Editando: núcleo" : "Editing: core";
        };
        int hintX = swatchX + swatchSize + 6;
        if (hintX + this.font.width(layer) <= this.layout.optionsX + this.layout.optionsWidth) {
            CrystalUi.label(graphics, this.font, layer, hintX, swatchY + (swatchSize - 8) / 2, CrystalTheme.TEXT_MUTED);
        }
    }

    private void drawSoundStatus(GuiGraphicsExtractor graphics) {
        if (this.rows.textY() < 0) {
            return;
        }
        int statusY = this.rows.textY() - currentScroll();
        Rect viewport = this.layout.content;
        if (statusY < viewport.y() || statusY + this.font.lineHeight > viewport.bottom()) {
            return;
        }
        String fileName = CrystalVisualConfig.customSoundFileName();
        String status = this.soundStatus.isBlank()
                ? (fileName.isBlank() ? (this.spanish ? "Sin archivo" : "No file selected") : fileName)
                : this.soundStatus;
        String clipped = this.font.plainSubstrByWidth(status, Math.max(1, this.layout.optionsWidth));
        CrystalUi.label(graphics, this.font, clipped, this.layout.optionsX, statusY, 0xFFD7C2DF);
    }

    private void snapIndicator() {
        Rect rect = tabRect(this.activeTab);
        if (rect == null) {
            return;
        }
        this.indicatorFromX = rect.x();
        this.indicatorFromWidth = rect.width();
        this.indicatorToX = rect.x();
        this.indicatorToWidth = rect.width();
        this.indicatorStartedAt = 0L;
    }

    private Rect tabRect(Tab tab) {
        int index = this.tabs.indexOf(tab);
        if (index < 0 || this.layout == null) {
            return null;
        }
        return this.layout.tabs(this.tabs.size()).get(index);
    }

    private float indicatorProgress() {
        if (this.indicatorStartedAt == 0L) {
            return 1.0F;
        }
        return CrystalTheme.easeOutCubic(progress(System.nanoTime() - this.indicatorStartedAt, INDICATOR_NANOS));
    }

    private float currentIndicatorX() {
        return this.indicatorFromX + (this.indicatorToX - this.indicatorFromX) * indicatorProgress();
    }

    private float currentIndicatorWidth() {
        return this.indicatorFromWidth + (this.indicatorToWidth - this.indicatorFromWidth) * indicatorProgress();
    }

    /** A bar that slides under the tab row to the selected tab. */
    private void drawTabIndicator(GuiGraphicsExtractor graphics, long now) {
        Rect active = tabRect(this.activeTab);
        if (active == null) {
            return;
        }
        int x = Math.round(currentIndicatorX());
        int width = Math.max(8, Math.round(currentIndicatorWidth()));
        int y = active.bottom() + 1;
        float shine = 0.5F + 0.5F * (float) Math.sin(now / 1_000_000_000.0D * 3.0D);
        graphics.fill(x + 3, y, x + width - 3, y + 2, CrystalTheme.lerp(0xFFB064F0, CrystalTheme.END_CYAN, shine));
        graphics.fill(x + 1, y + 2, x + width - 1, y + 3, 0x55B064F0);
        if (!this.enemyEditor && CrystalOptimizerGuard.conflictDetected()) {
            Rect tweaks = tabRect(Tab.TWEAKS);
            if (tweaks != null) {
                graphics.fill(tweaks.right() - 6, tweaks.y() + 2, tweaks.right() - 3, tweaks.y() + 5,
                        CrystalTheme.STATUS_PAUSED);
            }
        }
    }

    private void drawFooterInfo(GuiGraphicsExtractor graphics) {
        if (this.versionLabel.isEmpty()) {
            return;
        }
        Rect panel = this.layout.panel;
        Rect reset = this.layout.footerButtons().get(0);
        int textY = reset.y() + (reset.height() - 8) / 2;
        if (reset.x() - panel.x() - 12 >= this.font.width(this.versionLabel)) {
            CrystalUi.label(graphics, this.font, this.versionLabel, panel.x() + 9, textY, 0xFF8F74A8);
        }
    }

    private void drawPreview(GuiGraphicsExtractor graphics, long now, int mouseX, int mouseY) {
        Rect preview = this.layout.preview;
        if (preview.width() <= 0) {
            return;
        }
        graphics.fillGradient(preview.x(), preview.y(), preview.right(), preview.bottom(), 0x15150720, 0x0807030C);
        boolean hovered = preview.contains(mouseX, mouseY);
        CrystalUi.outline(graphics, preview.x(), preview.y(), preview.width(), preview.height(),
                hovered ? 0xCCB067E0 : 0x997C3CA0);
        updatePreviewPhase(now);
        this.previewState.ageInTicks = previewAnimationAge(now);
        CrystalAppearance look = visuals().copy();
        if ((Object) this.previewState instanceof CrystalAppearanceAccess access) {
            CrystalAppearance shown = look.copy();
            if (this.previewPhase == PreviewPhase.EXPLODING || this.previewPhase == PreviewPhase.HIDDEN) {
                shown.glowPowerPercent = 0; // The detached light fades at full size, not with the shrinking model.
            }
            access.crystalTweaks$appearance(shown);
        }
        if ((Object) this.previewState instanceof CrystalGlowAccess glow) {
            glow.crystalTweaks$surfaces(this.activeTab == Tab.GLOW
                    ? List.of(new CrystalGlowRenderer.Surface(-1.4F, -0.02F, -1.4F, 1.4F, 1.4F))
                    : List.of());
        }
        int renderTop = preview.y() + 3;
        int renderBottom = preview.bottom() - 3;
        int renderSize = Math.max(1, Math.min(preview.width() - 6, renderBottom - renderTop));
        int x = preview.x() + (preview.width() - renderSize) / 2;
        int y = renderTop + Math.max(0, (renderBottom - renderTop - renderSize) / 2);
        drawPedestal(graphics, now, x + renderSize / 2, y + Math.round(renderSize * 0.86F), renderSize, look);
        Quaternionf rotation = new Quaternionf().rotateZ((float) Math.PI).rotateX(0.18F);
        float previewScale = previewScale(now);
        drawPreviewAmbientParticles(graphics, now, x + renderSize / 2, y + renderSize / 2, renderSize);
        if (previewScale > 0.01F) {
            graphics.entity(
                    this.previewState,
                    renderSize * glowPreviewScale(look) * previewScale,
                    new Vector3f(0.0F, 1.0F, 0.0F),
                    rotation,
                    new Quaternionf(),
                    x,
                    y,
                    x + renderSize,
                    y + renderSize);
        }
        for (AfterglowTimeline.Sample<CrystalAfterglowState> tail : this.previewAfterglows.samples(now)) {
            CrystalAfterglowState light = tail.value();
            light.opacity = tail.opacity();
            light.progress = tail.progress();
            graphics.entity(light,
                    renderSize * glowPreviewScale(CrystalAppearanceAccess.of(light)),
                    new Vector3f(0.0F, 1.0F, 0.0F), rotation, new Quaternionf(),
                    x, y, x + renderSize, y + renderSize);
        }
        drawPreviewEffectParticles(graphics, now, x + renderSize / 2, y + renderSize / 2, renderSize);

        if (hovered && (this.previewPhase == PreviewPhase.VISIBLE || this.previewPhase == PreviewPhase.APPEARING)) {
            CrystalUi.centered(graphics, this.font, this.spanish ? "Clic: explotar" : "Click: explode",
                    preview.x() + preview.width() / 2, preview.bottom() - 12, 0xDDF3D2FF);
        }

        if (this.layout.previewNoteHeight > 0 && this.activeTab == Tab.COLORS) {
            Component note = Component.literal(this.spanish
                    ? "El tinte se aplica sobre la textura activa."
                    : "Tint is applied over the active texture.");
            List<FormattedCharSequence> lines = this.font.split(note, Math.max(20, preview.width() - 6));
            int noteY = preview.bottom() + 2;
            for (FormattedCharSequence line : lines) {
                graphics.text(this.font, line, preview.x() + 3, noteY, 0xFFC6B5CC, false);
                noteY += this.font.lineHeight;
            }
        }
    }

    /** An obsidian base under the preview crystal, ringed in the crystal's own glow colour. */
    private void drawPedestal(GuiGraphicsExtractor graphics, long now, int centerX, int centerY, int renderSize,
            CrystalAppearance look) {
        int radiusX = Math.max(6, Math.round(renderSize * 0.3F));
        int radiusY = Math.max(2, Math.round(renderSize * 0.065F));
        CrystalUi.ellipse(graphics, centerX, centerY + 2, radiusX + 2, radiusY + 1, 0x66000000);
        CrystalUi.ellipse(graphics, centerX, centerY, radiusX, radiusY, 0xE0140A1E);
        CrystalUi.ellipse(graphics, centerX, centerY - 1, Math.max(1, radiusX - 2), Math.max(1, radiusY - 1), 0xE0231233);
        float pulse = 0.5F + 0.5F * (float) Math.sin(now / 1_000_000_000.0D * 2.2D);
        int glowAlpha = look.glowPowerPercent > 0 ? Math.round(110 + 90 * pulse) : 60;
        CrystalUi.ellipseRim(graphics, centerX, centerY, radiusX, radiusY,
                CrystalTheme.withAlpha(look.haloColor(), glowAlpha));
        if (look.glowPowerPercent > 0) {
            CrystalUi.ellipse(graphics, centerX, centerY - 1, Math.round(radiusX * 0.55F), Math.max(1, radiusY / 2),
                    CrystalTheme.withAlpha(look.haloColor(), Math.round(40 + 40 * pulse)));
        }
    }

    private void drawPreviewAmbientParticles(GuiGraphicsExtractor graphics, long now, int centerX, int centerY, int renderSize) {
        double seconds = now / 1_000_000_000.0D;
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
        if (this.previewPhase == PreviewPhase.APPEARING && elapsed >= PREVIEW_APPEAR_NANOS) {
            this.previewPhase = PreviewPhase.VISIBLE;
            this.previewPhaseStartedAt = now;
        } else if (this.previewPhase == PreviewPhase.EXPLODING && elapsed >= PREVIEW_EXPLOSION_NANOS) {
            this.previewPhase = PreviewPhase.HIDDEN;
            this.previewPhaseStartedAt = now;
        } else if (this.previewPhase == PreviewPhase.HIDDEN && now >= this.previewRespawnAt) {
            startPreviewAppearance(now);
        }
    }

    private float previewScale(long now) {
        return switch (this.previewPhase) {
            case APPEARING -> easeOutBack(progress(now - this.previewPhaseStartedAt, PREVIEW_APPEAR_NANOS));
            case VISIBLE -> 1.0F;
            case EXPLODING -> 1.0F - CrystalTheme.easeOutCubic(progress(now - this.previewPhaseStartedAt, PREVIEW_EXPLOSION_NANOS));
            case HIDDEN -> 0.0F;
        };
    }

    private void drawPreviewEffectParticles(GuiGraphicsExtractor graphics, long now, int centerX, int centerY, int renderSize) {
        if (this.previewPhase != PreviewPhase.EXPLODING && this.previewPhase != PreviewPhase.APPEARING) {
            return;
        }
        boolean exploding = this.previewPhase == PreviewPhase.EXPLODING;
        float value = progress(now - this.previewPhaseStartedAt, exploding ? PREVIEW_EXPLOSION_NANOS : PREVIEW_APPEAR_NANOS);
        int count = exploding ? 34 : 22;
        int tint = visuals().haloColor();
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
            // Sparks take the glow colour, so the preview's burst matches the flash it leaves.
            int color = CrystalTheme.withAlpha(index % 3 == 0 ? 0xF5E8FF : tint, alpha);
            int size = index % 7 == 0 ? 3 : index % 3 == 0 ? 2 : 1;
            graphics.fill(x - 1, y - 1, x + size + 1, y + size + 1, CrystalTheme.withAlpha(tint, alpha / 4));
            graphics.fill(x, y, x + size, y + size, color);
        }
    }

    private void startPreviewAppearance(long now) {
        this.previewPhase = PreviewPhase.APPEARING;
        this.previewPhaseStartedAt = now;
        this.previewRespawnAt = 0L;
    }

    private void explodePreview(long now, boolean withSound) {
        if (withSound) {
            CrystalSoundManager.playPreviewExplosion();
        }
        if (visuals().glowPowerPercent > 0) {
            CrystalAfterglowState light = CrystalAfterglow.snapshot(this.previewState);
            ((CrystalAppearanceAccess) light).crystalTweaks$appearance(visuals().copy());
            this.previewAfterglows.start(java.util.UUID.randomUUID(), light, now);
        }
        this.previewPhase = PreviewPhase.EXPLODING;
        this.previewPhaseStartedAt = now;
        this.previewRespawnAt = now + PREVIEW_RESPAWN_NANOS;
    }

    private void handlePreviewInput(boolean attackInput) {
        long now = System.nanoTime();
        updatePreviewPhase(now);
        if (attackInput) {
            if (this.previewPhase == PreviewPhase.VISIBLE || this.previewPhase == PreviewPhase.APPEARING) {
                explodePreview(now, true);
            }
        } else {
            startPreviewAppearance(now);
        }
    }

    private void drawScrollbar(GuiGraphicsExtractor graphics) {
        if (this.maxScroll <= 0 || this.rows.bottom() <= 0) {
            return;
        }
        Rect viewport = this.layout.content;
        int trackX = this.layout.optionsX + this.layout.optionsWidth + 1;
        int trackHeight = viewport.height();
        int thumbHeight = Math.max(9, trackHeight * trackHeight / this.rows.bottom());
        int travel = Math.max(1, trackHeight - thumbHeight);
        int thumbY = viewport.y() + Math.round(travel * currentScroll() / (float) this.maxScroll);
        graphics.fill(trackX, viewport.y(), trackX + 2, viewport.bottom(), CrystalTheme.SCROLL_TRACK);
        graphics.fill(trackX, thumbY, trackX + 2, thumbY + thumbHeight, CrystalTheme.SCROLL_THUMB);
        drawScrollHints(graphics);
    }

    /**
     * Marks the edges the content continues past. A thin bar at the far right is easy to miss, so a
     * control below the fold would read as simply not existing.
     */
    private void drawScrollHints(GuiGraphicsExtractor graphics) {
        Rect viewport = this.layout.content;
        int left = this.layout.optionsX;
        int right = this.layout.optionsX + this.layout.optionsWidth;
        int fade = Math.min(11, Math.max(4, viewport.height() / 8));
        if (currentScroll() > 0) {
            graphics.fillGradient(left, viewport.y(), right, viewport.y() + fade, 0xB2150A20, 0x00150A20);
            CrystalUi.centered(graphics, this.font, "▴", (left + right) / 2, viewport.y() - 1, 0xFFE0B6F2);
        }
        if (currentScroll() < this.maxScroll) {
            graphics.fillGradient(left, viewport.bottom() - fade, right, viewport.bottom(), 0x00150A20, 0xC6150A20);
            CrystalUi.centered(graphics, this.font, "▾", (left + right) / 2, viewport.bottom() - this.font.lineHeight,
                    0xFFE0B6F2);
        }
    }

    // ------------------------------------------------------------------------------------------
    // Input and lifecycle
    // ------------------------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        boolean mappedAttack = this.minecraft != null && this.minecraft.options.keyAttack.matchesMouse(event);
        boolean mappedUse = this.minecraft != null && this.minecraft.options.keyUse.matchesMouse(event);
        boolean attackInput = mappedAttack || (!mappedUse && event.button() == 0);
        boolean useInput = mappedUse || (!mappedAttack && event.button() == 1);
        if ((attackInput || useInput) && this.layout.preview.width() > 0
                && this.layout.preview.contains(event.x(), event.y())) {
            handlePreviewInput(attackInput);
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (this.hexBox != null && this.hexBox.isFocused()) {
            return super.keyPressed(event);
        }
        if (this.minecraft != null && this.layout.preview.width() > 0) {
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

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        Rect viewport = this.layout.content;
        if (this.maxScroll > 0
                && mouseX >= this.layout.optionsX - 4
                && mouseX <= this.layout.optionsX + this.layout.optionsWidth + 4
                && mouseY >= viewport.y()
                && mouseY <= viewport.bottom()) {
            int direction = verticalAmount > 0.0D ? -1 : verticalAmount < 0.0D ? 1 : 0;
            if (direction != 0) {
                setCurrentScroll(Mth.clamp(currentScroll() + direction * Math.max(8, this.layout.rowStep()), 0,
                        this.maxScroll));
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

    private float previewAnimationAge(long now) {
        return Math.max(0L, now - this.openedAt) / 50_000_000.0F;
    }

    private static float glowPreviewScale(CrystalAppearance look) {
        return look.glowPowerPercent <= 0 ? 0.43F
                : Math.min(0.30F, 0.47F / CrystalGlowMath.radius(CrystalGlowMath.power(look.glowPowerPercent)));
    }

    private static boolean usesSpanish() {
        String language = Minecraft.getInstance().getLanguageManager().getSelected()
                .toLowerCase(Locale.ROOT).replace('-', '_');
        return language.equals("es") || language.startsWith("es_");
    }

    private static float progress(long elapsed, long duration) {
        return Mth.clamp(elapsed / (float) duration, 0.0F, 1.0F);
    }

    private static float easeOutBack(float value) {
        float shifted = value - 1.0F;
        return 1.0F + 2.70158F * shifted * shifted * shifted + 1.70158F * shifted * shifted;
    }

    private record Status(int color, String chip, String detail) { }

    private enum Layer {
        OUTER,
        INNER,
        CORE,
        GLOW
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
                Rect rect,
                double minimum,
                double maximum,
                double current,
                Consumer<Double> onChange,
                DoubleFunction<String> formatter
        ) {
            super(rect.x(), rect.y(), rect.width(), rect.height(), Component.empty(),
                    Mth.clamp((current - minimum) / (maximum - minimum), 0.0D, 1.0D));
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
        public void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            float fade = Mth.clamp(this.alpha, 0.0F, 1.0F);
            boolean hot = isHoveredOrFocused();
            int fill = hot ? 0xD15D2877 : 0xB53A1748;
            int outline = hot ? 0xE1D493FF : 0xB58B50B5;
            graphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), CrystalTheme.fade(fill, fade));
            // The filled part of the track shows the value at a glance.
            int filled = (int) Math.round(this.value * Math.max(0, getWidth() - 2));
            graphics.fillGradient(getX() + 1, getY() + 1, getX() + 1 + filled, getY() + getHeight() - 1,
                    CrystalTheme.fade(0x668A3FD0, fade), CrystalTheme.fade(0x33501E80, fade));
            CrystalUi.outline(graphics, getX(), getY(), getWidth(), getHeight(), CrystalTheme.fade(outline, fade));
            int handleX = getX() + (int) Math.round(this.value * Math.max(0, getWidth() - 7));
            graphics.fill(handleX, getY() + 1, handleX + 7, getY() + getHeight() - 1,
                    CrystalTheme.fade(hot ? 0xFFF0C8FF : 0xDDD590F3, fade));
            String text = getMessage().getString();
            int room = Math.max(1, getWidth() - 6);
            if (Minecraft.getInstance().font.width(text) > room) {
                text = Minecraft.getInstance().font.plainSubstrByWidth(text, room);
            }
            CrystalUi.centered(graphics, Minecraft.getInstance().font, text, getX() + getWidth() / 2,
                    getY() + (getHeight() - 8) / 2, CrystalTheme.fade(0xFFF5E9FA, fade));
        }
    }
}
