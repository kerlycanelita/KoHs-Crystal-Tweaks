package com.zymekoh.crystaltweaks.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.zymekoh.crystaltweaks.client.benchmark.CrystalBenchmarkScreen;
import com.zymekoh.crystaltweaks.client.compat.HerziumBridge;
import com.zymekoh.crystaltweaks.client.compat.OptimizerConflictDetector;
import com.zymekoh.crystaltweaks.client.hub.ConverterGrid;
import com.zymekoh.crystaltweaks.client.hub.CrystalStage;
import com.zymekoh.crystaltweaks.client.hub.GateScene;
import com.zymekoh.crystaltweaks.client.hub.HubButton;
import com.zymekoh.crystaltweaks.client.hub.HubColorPicker;
import com.zymekoh.crystaltweaks.client.hub.HubDraw;
import com.zymekoh.crystaltweaks.client.hub.HubEditBox;
import com.zymekoh.crystaltweaks.client.hub.HubLayout;
import com.zymekoh.crystaltweaks.client.hub.HubLayout.Rect;
import com.zymekoh.crystaltweaks.client.hub.HubMotion;
import com.zymekoh.crystaltweaks.client.hub.HubOverlays;
import com.zymekoh.crystaltweaks.client.hub.HubSkin;
import com.zymekoh.crystaltweaks.client.hub.HubSlider;
import com.zymekoh.crystaltweaks.client.hub.MiniCrystal;
import com.zymekoh.crystaltweaks.client.hub.RowList;
import com.zymekoh.crystaltweaks.client.hub.SidePanel;
import com.zymekoh.crystaltweaks.client.hub.TabCarousel;
import com.zymekoh.crystaltweaks.client.practice.PracticeWarningScreen;
import com.zymekoh.crystaltweaks.client.sound.CrystalSoundManager;
import com.zymekoh.crystaltweaks.core.CrystalBreakPrediction;
import com.zymekoh.crystaltweaks.core.CrystalOptimizerGuard;
import com.zymekoh.crystaltweaks.core.GhostCrystalSupport;
import com.zymekoh.crystaltweaks.core.GhostCrystalTracker;
import com.zymekoh.crystaltweaks.core.ObsidianDebounce;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

/**
 * The Crystal Tweaks settings hub.
 *
 * <p>Your crystal turns in the middle of the screen, in the colours, glow, flash and sound you have
 * chosen, and everything changed around it shows on it at once. Colours unfold on the left, the
 * glow and the flash on the right; above the crystal a carousel of tabs opens Sound, the crystal
 * helpers ("Crystal Tweaks") and the KoHs page in a drawer; under it, Crystal Practice. A switch in
 * the corner breaks the menu apart and drops the enemy profile's in its place, in crimson.</p>
 *
 * <p>It opens on a gate: the crystal alone in the middle of a wall of stone its glow lights, and a
 * line saying to click it. The first time in a session, when no other crystal optimizer is
 * installed, what the local optimization is and why it is legitimate comes first, beside the
 * crystal; continuing sends the crystal to the middle of the gate. The click blows it up: the stone
 * turns to obsidian and crying obsidian, bursts, and rains away while the menu comes up.</p>
 *
 * <p>Geometry comes from {@link HubLayout}, shared by drawing and input. Widgets are placed every
 * frame where their rows are drawn; while anything covers or rebuilds the menu they are hidden,
 * so no control is ever clickable where it is not drawn.</p>
 */
public final class CrystalTweaksScreen extends Screen {
    private enum Phase { INTRO, GATE, ENTER, SETTLE, MAIN, TRANSITION }

    private enum Layer { OUTER, INNER, CORE }

    private enum Narrow { NONE, LEFT, RIGHT }

    private static final long SETTLE_NANOS = 950_000_000L;
    private static final long GLOW_LIGHT_NANOS = 1_600_000_000L;
    private static final long FLASH_PREVIEW_DELAY_NANOS = 320_000_000L;
    private static final long SOUND_PREVIEW_DELAY_NANOS = 380_000_000L;
    private static boolean introShownThisSession;
    /** The gate's stone and its burst are an entrance: they play the first time the menu opens, once per launch. */
    private static boolean gateOpenedThisSession;
    private static boolean popupShownThisSession;
    private static boolean converterNoticeShownThisSession;

    private final Screen parent;
    private final boolean spanish;
    private final String versionLabel;
    private final CrystalStage stage = new CrystalStage();
    private final SidePanel leftPanel = new SidePanel(SidePanel.Side.LEFT);
    private final SidePanel rightPanel = new SidePanel(SidePanel.Side.RIGHT);
    private final MiniCrystal colorsIcon = new MiniCrystal(0.0F);
    private final MiniCrystal glowIcon = new MiniCrystal(0.45F);
    private final MiniCrystal titleCrystal = new MiniCrystal(0.7F);
    private final HubOverlays overlays;
    private final GateScene gate;
    private final TabCarousel userTabs;
    private final TabCarousel enemyTabs;
    private final List<AbstractWidget> drawerWidgets = new ArrayList<>();
    private HubLayout layout;
    private RowList drawerList;
    private HubButton profileUse;
    private String drawerId = "";
    private boolean enemy;
    private Phase phase = Phase.MAIN;
    private long phaseAt;
    private long openedAt;
    private long lastFrame;
    private float drawerProgress;
    private long glowTouchedAt = Long.MIN_VALUE / 2;
    private long flashPreviewAt;
    private long soundPreviewAt;
    private Narrow narrow = Narrow.NONE;
    private Layer layer = Layer.OUTER;
    private boolean updatingControls;
    private boolean saved;
    private boolean rescanReflectsPending;
    private CrystalOptimizerGuard.PauseReason shownPauseReason;
    private String soundStatus = "";
    private float practiceHover;
    private float handleHover;
    private Rect statusChip = Rect.EMPTY;

    private HubButton switchButton;
    private HubButton doneButton;
    private HubButton outerButton;
    private HubButton innerButton;
    private HubButton coreButton;
    private EditBox layerHex;
    private HubColorPicker layerPicker;
    private EditBox glowHex;
    private HubSlider blurSlider;
    private ConverterGrid converterGrid;
    private HubEditBox converterSearch;
    private String converterQuery = "";
    private boolean converterEntities;
    private boolean converterNoticeOpen;
    private HubColorPicker glowPicker;
    private HubButton soundFileButton;
    private HubButton ghostToggle;
    private HubButton safeToggle;
    private HubButton rescanButton;
    private HubButton herziumOrderButton;

    public CrystalTweaksScreen(Screen parent) {
        super(Component.literal("Crystal Tweaks KoHs"));
        this.parent = parent;
        this.spanish = CrystalUi.spanish();
        this.versionLabel = FabricLoader.getInstance().getModContainer("crystal_tweaks")
                .map(container -> "v" + container.getMetadata().getVersion().getFriendlyString())
                .orElse("");
        this.overlays = new HubOverlays(this, this.spanish);
        this.gate = new GateScene(this.spanish);
        this.userTabs = new TabCarousel(List.of(
                new TabCarousel.Item("sound", this.spanish ? "Sonido" : "Sound", HubOverlays::soundIcon),
                new TabCarousel.Item("tweaks", "Crystal Tweaks", HubOverlays::tweaksIcon),
                new TabCarousel.Item("advanced", this.spanish ? "Avanzado" : "Advanced", HubOverlays::toolsIcon),
                new TabCarousel.Item("converter", "Converter", HubOverlays::converterIcon),
                new TabCarousel.Item("kohs", "KoHs", HubOverlays::kohsIcon)), 1, false);
        this.enemyTabs = new TabCarousel(List.of(
                new TabCarousel.Item("profile", this.spanish ? "Perfil" : "Profile", HubOverlays::profileIcon),
                new TabCarousel.Item("converter", "Converter", HubOverlays::converterIcon),
                new TabCarousel.Item("soon", this.spanish ? "Avanzado" : "Advanced", HubOverlays::advancedIcon)), 0, false);
    }

    /** What Mod Menu opens. The intro, when due, is part of the screen itself. */
    public static Screen open(Screen parent) {
        return new CrystalTweaksScreen(parent);
    }

    private static boolean introDue() {
        return !introShownThisSession
                && !CrystalVisualConfig.optimizerNoticeDismissed()
                && !CrystalOptimizerGuard.conflictDetected()
                && !CrystalOptimizerGuard.forcedOff();
    }

    // ------------------------------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------------------------------

    @Override
    protected void init() {
        CrystalVisualConfig.load();
        long now = System.nanoTime();
        boolean first = this.openedAt == 0L;
        this.lastFrame = now;
        this.saved = false;
        this.layout = HubLayout.fit(this.width, this.height);
        this.overlays.layout(this.font, this.width, this.height);
        this.gate.layout(this.width, this.height);
        clearWidgets();
        this.drawerWidgets.clear();
        this.leftPanel.setGeometry(this.layout.left, this.layout);
        this.rightPanel.setGeometry(this.layout.right, this.layout);
        currentTabs().setRect(this.layout.carousel);
        addChrome();
        rebuildLeft(false);
        rebuildRight(false);
        rebuildDrawer();
        if (first) {
            this.openedAt = now;
            if (introDue()) {
                introShownThisSession = true;
                this.phase = Phase.INTRO;
                this.phaseAt = now;
                this.overlays.showIntro();
                this.stage.setTarget(this.overlays.introCrystal(), true);
                this.stage.appear(now);
            } else if (gateOpenedThisSession) {
                startSettle(now, true);
            } else {
                showGate(now, true);
            }
        } else {
            // A resize or a return from another screen: everything as it was, at once.
            if (this.phase == Phase.INTRO) {
                this.stage.setTarget(this.overlays.introCrystal(), true);
            } else if (this.phase == Phase.GATE) {
                this.stage.setTarget(this.gate.crystalRect(), true);
            } else {
                if (this.phase != Phase.MAIN) {
                    this.overlays.endTransition();
                    this.phase = Phase.MAIN;
                }
                showPanelsOpen();
                this.stage.setTarget(crystalTarget(), true);
            }
        }
        this.drawerProgress = currentTabs().open() ? 1.0F : 0.0F;
    }

    /** The gate: the crystal in the middle of the stone, waiting for its click. */
    private void showGate(long now, boolean appear) {
        this.phase = Phase.GATE;
        this.phaseAt = now;
        this.leftPanel.hide();
        this.rightPanel.hide();
        if (appear) {
            Rect target = this.gate.crystalRect();
            this.stage.setTarget(new Rect(target.centerX(), target.centerY(), 0, 0), true);
            this.stage.setTarget(target, false);
            this.stage.appear(now);
        } else {
            this.stage.setTarget(this.gate.crystalRect(), false);
        }
    }

    /** The click on the gate's crystal: it explodes, the stone turns and bursts, the menu follows. */
    private void beginEnter(long now) {
        if (this.phase != Phase.GATE) {
            return;
        }
        gateOpenedThisSession = true;
        this.stage.explode(now, visuals().copy(), true);
        this.gate.enter(now, this.stage.centerX(), this.stage.centerY());
        this.phase = Phase.ENTER;
        this.phaseAt = now;
    }

    private void startSettle(long now, boolean appear) {
        this.phase = Phase.SETTLE;
        this.phaseAt = now;
        this.stage.setTarget(crystalTarget(), false);
        if (appear) {
            Rect target = crystalTarget();
            // From a point in the middle of the stage, growing into place.
            this.stage.setTarget(new Rect(target.centerX(), target.centerY(), 0, 0), true);
            this.stage.setTarget(target, false);
            this.stage.appear(now);
        }
        if (this.layout.wide) {
            this.leftPanel.unfold(now + 230_000_000L);
            this.rightPanel.unfold(now + 330_000_000L);
        } else {
            this.leftPanel.hide();
            this.rightPanel.hide();
        }
    }

    private void showPanelsOpen() {
        if (this.layout.wide) {
            this.leftPanel.showOpen();
            this.rightPanel.showOpen();
            this.narrow = Narrow.NONE;
        } else {
            this.leftPanel.hide();
            this.rightPanel.hide();
            if (this.narrow == Narrow.LEFT) {
                this.leftPanel.showOpen();
            } else if (this.narrow == Narrow.RIGHT) {
                this.rightPanel.showOpen();
            }
        }
    }

    private TabCarousel currentTabs() {
        return this.enemy ? this.enemyTabs : this.userTabs;
    }

    private CrystalAppearance visuals() {
        return CrystalVisualConfig.visuals(this.enemy);
    }

    @Override
    public void onClose() {
        if (this.overlays.closeTopmost()) {
            return;
        }
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

    /** Persists at the moment a control is let go, not on every step of a drag. */
    private void commit() {
        CrystalVisualConfig.save();
        this.saved = false;
    }

    // ------------------------------------------------------------------------------------------
    // Called back by the overlays
    // ------------------------------------------------------------------------------------------

    /** The intro's Continue: the crystal goes to the middle of the gate and waits for its click. */
    public void introContinued(boolean dismissForever) {
        if (dismissForever) {
            CrystalVisualConfig.setOptimizerNoticeDismissed(true);
            CrystalVisualConfig.save();
        }
        if (gateOpenedThisSession) {
            startSettle(System.nanoTime(), false);
        } else {
            showGate(System.nanoTime(), false);
        }
    }

    public void popupClosed(boolean dismissForever) {
        if (dismissForever) {
            if (this.converterNoticeOpen) {
                CrystalVisualConfig.setConverterNoticeDismissed(true);
            } else {
                CrystalVisualConfig.setOptimizerPopupDismissed(true);
            }
            CrystalVisualConfig.save();
        }
        this.converterNoticeOpen = false;
    }

    /** The shards have flown and the new menu has landed: its controls come up. */
    public void transitionLanded() {
        long now = System.nanoTime();
        currentTabs().setRect(this.layout.carousel);
        rebuildLeft(false);
        rebuildRight(false);
        rebuildDrawer();
        if (this.layout.wide) {
            this.leftPanel.unfold(now - 300_000_000L);
            this.rightPanel.unfold(now - 300_000_000L);
        } else {
            this.narrow = Narrow.NONE;
            this.leftPanel.hide();
            this.rightPanel.hide();
        }
        this.stage.setTarget(crystalTarget(), true);
        this.stage.appear(now);
        this.drawerProgress = currentTabs().open() ? 1.0F : 0.0F;
    }

    public void transitionFinished() {
        this.phase = Phase.MAIN;
    }

    // ------------------------------------------------------------------------------------------
    // Fixed widgets: the enemy switch and Done
    // ------------------------------------------------------------------------------------------

    private void addChrome() {
        Rect switchRect = this.layout.switchButton;
        String label = this.enemy
                ? (this.spanish ? "Tus cristales" : "Your crystals")
                : (this.spanish ? "Cristales ajenos" : "Enemy crystals");
        this.switchButton = addRenderableWidget(new HubButton(switchRect.x(), switchRect.y(), switchRect.width(),
                switchRect.height(), Component.literal(label), ignored -> switchMode())
                .icon(this.enemy ? HubOverlays::ownCrystalIcon : HubOverlays::enemyCrystalIcon)
                .style(this.enemy ? HubButton.Style.PRIMARY : HubButton.Style.DANGER));
        this.switchButton.setTooltip(Tooltip.create(Component.literal(this.enemy
                ? (this.spanish ? "Vuelve a los colores y el brillo de tus propios cristales." : "Back to the colours and glow of your own crystals.")
                : (this.spanish
                        ? "Colores y brillo de los cristales que no colocaste tú. Identificación aproximada: se relacionan tus intentos de colocación con las apariciones; el servidor no envía el propietario."
                        : "Colours and glow of the crystals you did not place. Approximate attribution: your placement attempts are matched to spawns; the server does not send the owner."))));
        Rect done = this.layout.done;
        this.doneButton = addRenderableWidget(new HubButton(done.x(), done.y(), done.width(), done.height(),
                Component.literal(this.spanish ? "Listo" : "Done"), ignored -> onClose()).style(HubButton.Style.PRIMARY));
    }

    private void switchMode() {
        if (this.phase != Phase.MAIN) {
            return;
        }
        long now = System.nanoTime();
        CrystalAppearance before = visuals().copy();
        HubSkin skin = HubSkin.current();
        // The skin is one shared object: what the old menu looked like is read out before it changes.
        int oldTop = skin.sideTop;
        int oldBottom = skin.sideBottom;
        int oldBorder = skin.sideBorder;
        int oldHalo = skin.halo;
        float oldInfluence = skin.influence;
        List<Rect> outgoing = new ArrayList<>();
        if (this.leftPanel.shown()) {
            outgoing.add(this.layout.left);
        }
        if (this.rightPanel.shown()) {
            outgoing.add(this.layout.right);
        }
        outgoing.add(this.layout.carousel);
        outgoing.add(this.layout.practice);
        Rect drawer = currentDrawerRect();
        if (!drawer.isEmpty()) {
            outgoing.add(drawer);
        }
        this.stage.explode(now, before, true);
        this.enemy = !this.enemy;
        this.narrow = Narrow.NONE;
        // The new mode's colours, for the panels that fall in.
        HubSkin.current().update(this.enemy, visuals().haloColor(), 0.15F);
        HubSkin next = HubSkin.current();
        int newTop = next.sideTop;
        int newBottom = next.sideBottom;
        int newBorder = next.sideBorder;
        int newAccent = next.accent;
        List<Rect> incoming = new ArrayList<>();
        if (this.layout.wide) {
            incoming.add(this.layout.left);
            incoming.add(this.layout.right);
        }
        incoming.add(this.layout.carousel);
        incoming.add(this.layout.practice);
        Rect switchRect = this.layout.switchButton;
        this.overlays.startTransition(now, outgoing, switchRect.centerX(), switchRect.centerY(), oldTop, oldBottom, oldBorder,
                oldHalo, incoming, newTop, newBottom, newBorder, newAccent);
        HubSkin.current().update(!this.enemy, before.haloColor(), oldInfluence);
        this.phase = Phase.TRANSITION;
        this.phaseAt = now;
        // The old controls go before the first crack, the new ones come with the landing.
        clearWidgets();
        this.drawerWidgets.clear();
        this.leftPanel.hide();
        this.rightPanel.hide();
        addChrome();
        this.switchButton.visible = false;
        CrystalVisualConfig.save();
    }

    // ------------------------------------------------------------------------------------------
    // The side panels
    // ------------------------------------------------------------------------------------------

    private void replaceList(SidePanel panel, RowList list, boolean keepScroll) {
        if (panel.list() != null) {
            for (AbstractWidget widget : panel.list().widgets()) {
                removeWidget(widget);
            }
        }
        for (AbstractWidget widget : list.widgets()) {
            widget.visible = false;
            addWidget(widget);
        }
        panel.setList(list, keepScroll);
    }

    private int pickerHeight(int width, float share, int maximum) {
        return Mth.clamp(Math.round(width * share), 36, maximum);
    }

    private void rebuildLeft(boolean keepScroll) {
        int width = this.layout.panelViewport(this.layout.left).width();
        int control = this.layout.controlHeight;
        int slider = this.layout.sliderHeight;
        RowList list = new RowList(width, this.layout.rowGap);
        this.leftPanel.setLabel(this.enemy ? (this.spanish ? "Colores ajenos" : "Enemy colours") : (this.spanish ? "Colores" : "Colours"));
        if (this.enemy) {
            list.row(control);
            HubButton use = list.full(new HubButton(0, 0, 0, 0, Component.literal(this.spanish ? "Usar este perfil" : "Use this profile"),
                    ignored -> profileUseChanged()).switchOf(CrystalVisualConfig::enemyCustomEnabled).lit());
            this.profileUse = use;
            use.setTooltip(Tooltip.create(Component.literal(this.spanish
                    ? "Apagado, los cristales ajenos se ven con tu propio perfil."
                    : "When off, crystals you did not place use your own profile.")));
        }
        list.heading(this.spanish ? "Capa" : "Layer");
        int gap = 3;
        int third = (width - gap * 2) / 3;
        list.row(control);
        this.outerButton = list.cell(layerButton(Layer.OUTER), 0, third);
        this.innerButton = list.cell(layerButton(Layer.INNER), third + gap, third);
        this.coreButton = list.cell(layerButton(Layer.CORE), (third + gap) * 2, width - (third + gap) * 2);
        list.row(control);
        int hexWidth = Math.min(70, width / 2);
        this.layerHex = list.cell(new HubEditBox(this.font, 0, 0, hexWidth, control, Component.literal(this.spanish ? "Color hexadecimal" : "Hex colour")),
                0, hexWidth);
        this.layerHex.setMaxLength(7);
        this.layerHex.setResponder(this::onLayerHex);
        list.paint((graphics, font, x, y, cellWidth, height, alpha) -> drawSwatch(graphics, x, y, height, layerColor(), layerName(), alpha),
                hexWidth + 5, width - hexWidth - 5);
        list.row(pickerHeight(width, 0.6F, 100));
        this.layerPicker = list.full(new HubColorPicker(0, 0, 0, 0, Component.literal(this.spanish ? "Selector de color" : "Colour picker"),
                layerColor(), this::onLayerPicker).lit().onRelease(this::commit));
        list.heading(this.spanish ? "Animación" : "Animation");
        list.row(slider);
        list.full(HubSlider.linear(0, 0, 0, 0, 0.0D, 300.0D, visuals().rotationSpeedPercent,
                value -> {
                    visuals().rotationSpeedPercent = (int) Math.round(value);
                    refreshBlurControl();
                },
                value -> speedLabel(this.spanish ? "Giro" : "Rotation", (int) Math.round(value))).lit().onRelease(this::commit));
        list.row(slider);
        list.full(HubSlider.linear(0, 0, 0, 0, 0.0D, 300.0D, visuals().floatingSpeedPercent,
                value -> {
                    visuals().floatingSpeedPercent = (int) Math.round(value);
                    refreshBlurControl();
                },
                value -> speedLabel(this.spanish ? "Flotación" : "Floating", (int) Math.round(value))).lit().onRelease(this::commit));
        list.heading(this.spanish ? "Tamaño" : "Size");
        list.row(slider);
        HubSlider size = list.full(HubSlider.linear(0, 0, 0, 0, CrystalAppearance.MIN_SIZE, CrystalAppearance.MAX_SIZE,
                visuals().sizePercent, value -> visuals().sizePercent = (int) Math.round(value),
                value -> (this.spanish ? "Tamaño: " : "Size: ") + Math.round(value) + "%" + (Math.round(value) == 100 ? " (Vanilla)" : ""))
                .lit().onRelease(this::commit));
        size.setTooltip(Tooltip.create(Component.literal(this.spanish
                ? "Lo grande que se dibuja el cristal, con su brillo. Solo cambia el dibujo: la zona que puedes golpear es la del servidor y sigue igual, centrada en el cristal."
                : "How large the crystal is drawn, with its glow. Only the drawing changes: the box you can hit is the server's and stays the same, centred on the crystal.")));
        list.space(2);
        list.note(this.font, this.spanish ? "El tinte se aplica sobre la textura activa." : "The tint is applied over the active texture.", 0);
        replaceList(this.leftPanel, list, keepScroll);
        selectLayer(this.layer);
        if (visuals().converted()) {
            // Something else is drawn in the crystal's place: its layers' colours have nothing to tint.
            Component note = Component.literal(this.spanish
                    ? "No se usa mientras Converter My Crystal dibuja otra cosa en lugar del cristal: no hay capas que teñir."
                    : "Not used while Converter My Crystal draws something else in the crystal's place: there are no layers to tint.");
            for (AbstractWidget widget : List.of(this.outerButton, this.innerButton, this.coreButton, this.layerHex, this.layerPicker)) {
                widget.active = false;
                widget.setTooltip(Tooltip.create(note));
            }
            this.layerHex.setEditable(false);
            // No layer is being edited: a selected one would look like the live control it is not.
            this.outerButton.setSelected(false);
            this.innerButton.setSelected(false);
            this.coreButton.setSelected(false);
        }
        dimUnusedProfile(list, this.enemy ? this.profileUse : null);
    }

    private HubButton layerButton(Layer target) {
        String name = switch (target) {
            case OUTER -> this.spanish ? "Exterior" : "Outer";
            case INNER -> this.spanish ? "Interior" : "Inner";
            case CORE -> this.spanish ? "Núcleo" : "Core";
        };
        return new HubButton(0, 0, 0, 0, Component.literal(name), ignored -> selectLayer(target)).lit();
    }

    private void rebuildRight(boolean keepScroll) {
        int width = this.layout.panelViewport(this.layout.right).width();
        int control = this.layout.controlHeight;
        int slider = this.layout.sliderHeight;
        int indent = width >= 150 ? 9 : 5;
        CrystalAppearance look = visuals();
        RowList list = new RowList(width, this.layout.rowGap);
        this.rightPanel.setLabel(this.enemy ? (this.spanish ? "Brillo ajeno" : "Enemy glow") : (this.spanish ? "Brillo" : "Glow"));
        list.heading(this.spanish ? "Brillo" : "Glow");
        list.row(control);
        HubButton glow = list.full(new HubButton(0, 0, 0, 0, Component.literal(this.spanish ? "Brillo" : "Glow"), ignored -> {
            visuals().glowEnabled = !visuals().glowEnabled;
            if (visuals().glowEnabled && visuals().glowPowerPercent <= 0) {
                visuals().glowPowerPercent = CrystalVisualConfig.defaults(this.enemy).glowPowerPercent;
            }
            glowTouched();
            commit();
            rebuildRight(true);
        }).switchOf(() -> visuals().glowEnabled).lit());
        glow.setTooltip(Tooltip.create(Component.literal(this.spanish
                ? "La luz del cristal: en sus marcos, la del núcleo, sus reflejos en el suelo y la luz propia del cristal. El destello al explotar y el desenfoque tienen su propio control."
                : "The crystal's light: in its frames, the core's own, its reflections on the ground and the crystal's own light. The flash on explosion and the blur have controls of their own.")));
        // The halo of Old KoHs Crystal Glow has no layers to light: what belongs to them is dimmed.
        boolean halo = CrystalVisualConfig.oldGlow();
        String haloNote = this.spanish
                ? "No se usa con Old KoHs Crystal Glow, que es un halo detrás del cristal. Apágalo en Avanzado para usarlo."
                : "Not used by Old KoHs Crystal Glow, which is a halo behind the crystal. Turn it off in Advanced to use this.";
        // A crystal drawn as something else has no layers either: their style and their trail wait.
        boolean converted = look.converted();
        String convertedNote = this.spanish
                ? "No se usa mientras Converter My Crystal dibuja otra cosa en lugar del cristal: su luz es un resplandor redondo."
                : "Not used while Converter My Crystal draws something else in the crystal's place: its light is a round glow.";
        if (look.glowEnabled) {
            list.row(slider, indent);
            list.full(HubSlider.linear(0, 0, 0, 0, 0, 300, look.glowPowerPercent, value -> {
                visuals().glowPowerPercent = (int) Math.round(value);
                glowTouched();
            }, value -> (this.spanish ? "Potencia: " : "Power: ") + Math.round(value) + "%").lit().onRelease(this::commit));
            list.row(slider, indent);
            HubSlider core = list.full(HubSlider.linear(0, 0, 0, 0, 0, 300, look.coreGlowPercent, value -> {
                visuals().coreGlowPercent = (int) Math.round(value);
                glowTouched();
            }, value -> (this.spanish ? "Núcleo: " : "Core: ") + Math.round(value) + "%").lit().onRelease(this::commit));
            core.setTooltip(Tooltip.create(Component.literal(this.spanish
                    ? "Cuánto brilla el núcleo, con cualquier tipo de brillo: en Luz, una luz redonda a su alrededor; en Capas, su propia luz y su aura; en Old KoHs Crystal Glow, el centro del halo. Con 0% solo brilla lo demás."
                    : "How much the core glows, with every kind of glow: in Light, a round light about it; in Layers, its own light and aura; in Old KoHs Crystal Glow, the middle of the halo. At 0% only the rest glows.")));
            list.row(slider, indent);
            HubSlider reflections = list.full(HubSlider.linear(0, 0, 0, 0, 0, 300, look.glowReflectionsPercent, value -> {
                visuals().glowReflectionsPercent = (int) Math.round(value);
                glowTouched();
            }, value -> (this.spanish ? "Reflejos: " : "Reflections: ") + Math.round(value) + "%").lit().onRelease(this::commit));
            reflections.setTooltip(Tooltip.create(Component.literal(this.spanish
                    ? "Luz de color simulada sobre los bloques cercanos, del cristal y de su destello. No cambia la iluminación del mundo."
                    : "Simulated coloured light on nearby block tops, from the crystal and its flash. Does not change world lighting.")));
            list.row(control, indent);
            HubButton style = list.full(new HubButton(0, 0, 0, 0, Component.literal((this.spanish ? "Estilo: " : "Style: ")
                    + CrystalVisualConfig.glowStyle().label(this.spanish)), ignored -> {
                CrystalVisualConfig.setGlowStyle(CrystalVisualConfig.glowStyle().next());
                glowTouched();
                commit();
                rebuildRight(true);
            }).lit());
            style.active = !halo && !converted;
            style.setTooltip(Tooltip.create(Component.literal(halo ? haloNote : converted ? convertedNote : this.spanish
                    ? "Luz: un resplandor suave alrededor de cada marco, que no quema el color ni de día. Capas: las capas dibujadas otra vez como luz añadida, más intensas y saturadas. Lo comparten tus cristales y los ajenos."
                    : "Light: a soft glow round every frame, which keeps its colour even in daylight. Layers: the layers drawn again as added light, stronger and more saturated. Shared by your crystals and the enemy's.")));
        }
        list.heading(this.spanish ? "Movimiento" : "Motion");
        list.row(slider);
        this.blurSlider = list.full(HubSlider.linear(0, 0, 0, 0, 0, 100, look.motionBlurPercent, value -> {
            visuals().motionBlurPercent = (int) Math.round(value);
            glowTouched();
        }, value -> (this.spanish ? "Desenfoque: " : "Motion blur: ")
                + (Math.round(value) == 0 ? (this.spanish ? "No" : "Off") : Math.round(value) + "%")).lit().onRelease(this::commit));
        refreshBlurControl();
        list.heading(this.spanish ? "Calidad" : "Quality");
        int qualityArrow = Math.max(12, Math.min(width / 6, control + 4));
        list.row(control);
        list.cell(new HubButton(0, 0, 0, 0, Component.literal("‹"), ignored -> changeGlowQuality(CrystalVisualConfig.glowQuality().previous())).lit(),
                0, qualityArrow);
        HubButton quality = list.cell(new HubButton(0, 0, 0, 0, Component.literal(CrystalVisualConfig.glowQuality().label(this.spanish)),
                ignored -> changeGlowQuality(CrystalVisualConfig.glowQuality().next())).lit(), qualityArrow + 2, width - qualityArrow * 2 - 4);
        quality.setTooltip(Tooltip.create(Component.literal(this.spanish
                ? "Cuánto dibujan el brillo, el desenfoque y los reflejos. Rendimiento: solo el marco exterior y el núcleo, estela corta, reflejos simples y nada a lo lejos. Equilibrado: todas las capas. Calidad: también las caras de atrás, la estela más larga y los reflejos más finos. Lo comparten tus cristales y los ajenos."
                : "How much the glow, the blur and the reflections draw. Performance: the outer frame and the core only, a short trail, plain reflections and nothing far away. Balanced: every layer. Quality: the far faces too, the longest trail and the finest reflections. Shared by your crystals and the enemy's.")));
        list.cell(new HubButton(0, 0, 0, 0, Component.literal("›"), ignored -> changeGlowQuality(CrystalVisualConfig.glowQuality().next())).lit(),
                width - qualityArrow, qualityArrow);
        list.heading(this.spanish ? "Color del brillo" : "Glow colour");
        list.row(control);
        HubButton own = list.full(new HubButton(0, 0, 0, 0, Component.literal(this.spanish ? "Color propio" : "Own colour"), ignored -> {
            visuals().customGlowColor = !visuals().customGlowColor;
            glowTouched();
            commit();
            rebuildRight(true);
        }).switchOf(() -> visuals().customGlowColor).lit());
        own.setTooltip(Tooltip.create(Component.literal(this.spanish
                ? "Elige el color del brillo y del destello. Apagado, el brillo toma la media de los colores del cristal."
                : "Chooses the colour of the glow and the flash. When off, the glow takes the average of the crystal's colours.")));
        this.glowHex = null;
        this.glowPicker = null;
        if (look.customGlowColor) {
            list.row(control);
            int hexWidth = Math.min(70, width / 2);
            this.glowHex = list.cell(new HubEditBox(this.font, 0, 0, hexWidth, control,
                    Component.literal(this.spanish ? "Color de brillo hexadecimal" : "Glow hex colour")), 0, hexWidth);
            this.glowHex.setMaxLength(7);
            this.glowHex.setResponder(this::onGlowHex);
            this.updatingControls = true;
            this.glowHex.setValue(CrystalVisualConfig.toHex(look.glowColor));
            this.updatingControls = false;
            list.paint((graphics, font, x, y, cellWidth, height, alpha) -> drawSwatch(graphics, x, y, height, visuals().glowColor,
                    this.spanish ? "Color del halo" : "Halo colour", alpha), hexWidth + 5, width - hexWidth - 5);
            list.row(pickerHeight(width, 0.46F, 84));
            this.glowPicker = list.full(new HubColorPicker(0, 0, 0, 0, Component.literal(this.spanish ? "Color del brillo" : "Glow colour"),
                    look.glowColor, this::onGlowPicker).lit().onRelease(this::commit));
        }
        list.heading(this.spanish ? "Destello al explotar" : "Flash on explosion");
        list.row(control);
        HubButton flash = list.full(new HubButton(0, 0, 0, 0, Component.literal(this.spanish ? "Destello" : "Flash"), ignored -> {
            visuals().flashEnabled = !visuals().flashEnabled;
            commit();
            rebuildRight(true);
            if (visuals().flashEnabled) {
                scheduleFlashPreview();
            }
        }).switchOf(() -> visuals().flashEnabled).lit());
        flash.setTooltip(Tooltip.create(Component.literal(this.spanish
                ? "La luz que deja un cristal al explotar. Es solo dibujo: no cambia la explosión, el daño ni ningún paquete. Haz clic en el cristal del centro para verlo."
                : "The light a crystal leaves when it explodes. Drawing only: it changes no explosion, no damage and no packet. Click the crystal in the middle to see it.")));
        if (look.flashEnabled) {
            int branch = width - indent;
            int arrow = Math.max(12, Math.min(branch / 6, control + 4));
            list.row(control, indent);
            list.cell(new HubButton(0, 0, 0, 0, Component.literal("‹"), ignored -> changeFlashStyle(CrystalVisualConfig.flashStyle().previous())).lit(),
                    0, arrow);
            HubButton style = list.cell(new HubButton(0, 0, 0, 0, Component.literal((this.spanish ? "Forma: " : "Shape: ")
                    + CrystalVisualConfig.flashStyle().label(this.spanish)), ignored -> changeFlashStyle(CrystalVisualConfig.flashStyle().next())).lit(),
                    arrow + 2, branch - arrow * 2 - 4);
            style.setTooltip(Tooltip.create(Component.literal(this.spanish
                    ? "Forma del destello, en el color del brillo. La comparten tus cristales y los ajenos."
                    : "Shape of the flash, in the glow colour. Shared by your crystals and the enemy's.")));
            list.cell(new HubButton(0, 0, 0, 0, Component.literal("›"), ignored -> changeFlashStyle(CrystalVisualConfig.flashStyle().next())).lit(),
                    branch - arrow, arrow);
            if (CrystalVisualConfig.flashStyle().scalable()) {
                list.row(slider, indent);
                list.full(HubSlider.linear(0, 0, 0, 0, CrystalAppearance.MIN_FLASH_SCALE, 300, look.flashScalePercent, value -> {
                    visuals().flashScalePercent = (int) Math.round(value);
                    scheduleFlashPreview();
                }, value -> (this.spanish ? "Tamaño: " : "Size: ") + Math.round(value) + "%").lit().onRelease(this::commit));
            }
            list.row(slider, indent);
            HubSlider opacity = list.full(HubSlider.linear(0, 0, 0, 0, CrystalAppearance.MIN_FLASH_OPACITY, 100, look.flashOpacityPercent, value -> {
                visuals().flashOpacityPercent = (int) Math.round(value);
                scheduleFlashPreview();
            }, value -> (this.spanish ? "Opacidad: " : "Opacity: ") + Math.round(value) + "%").lit().onRelease(this::commit));
            opacity.setTooltip(Tooltip.create(Component.literal(this.spanish
                    ? "Cuánto se ve el destello. 50% es el de siempre; 100%, el más intenso."
                    : "How strongly the flash shows. 50% is the usual flash; 100% is the most intense.")));
            list.row(slider, indent);
            list.full(HubSlider.linear(0, 0, 0, 0, CrystalAppearance.MIN_FLASH_DURATION / 1000.0D, CrystalAppearance.MAX_FLASH_DURATION / 1000.0D,
                    look.flashDurationMillis / 1000.0D, value -> {
                        visuals().flashDurationMillis = (int) Math.round(value * 1000.0D);
                        scheduleFlashPreview();
                    }, value -> String.format(Locale.ROOT, this.spanish ? "Duración: %.1f s" : "Duration: %.1f s", value))
                    .lit().onRelease(this::commit));
        }
        replaceList(this.rightPanel, list, keepScroll);
        dimUnusedProfile(list, null);
    }

    private void changeGlowQuality(CrystalGlowQuality quality) {
        CrystalVisualConfig.setGlowQuality(quality);
        commit();
        rebuildRight(true);
    }

    /** The blur needs something that moves: a crystal that neither turns nor floats has nothing to trail. */
    private void refreshBlurControl() {
        if (this.blurSlider == null) {
            return;
        }
        boolean moving = visuals().rotationSpeedPercent > 0 || visuals().floatingSpeedPercent > 0;
        boolean converted = visuals().converted();
        this.blurSlider.active = moving && !converted;
        this.blurSlider.setTooltip(Tooltip.create(Component.literal(converted
                ? (this.spanish
                        ? "No se usa mientras Converter My Crystal dibuja otra cosa en lugar del cristal: la estela es la de sus capas."
                        : "Not used while Converter My Crystal draws something else in the crystal's place: the trail is its layers'.")
                : moving
                ? (this.spanish
                        ? "Las capas dejan una estela suave al girar, como una foto en movimiento. Más alto, estela más larga. Funciona también con el brillo apagado. Solo dibujo, en tu pantalla."
                        : "The layers leave a soft trail as they turn, like a photo in motion. Higher, a longer trail. It works with the glow off too. Drawing only, on your screen.")
                : (this.spanish
                        ? "Un cristal que ni gira ni flota no deja estela: sube Giro o Flotación en Colores."
                        : "A crystal that neither turns nor floats leaves no trail: raise Rotation or Floating in Colours."))));
    }

    private void changeFlashStyle(CrystalFlashStyle style) {
        CrystalVisualConfig.setFlashStyle(style);
        commit();
        rebuildRight(true);
        // Show the new shape at once, silently: browsing fourteen shapes is not fourteen explosions.
        long now = System.nanoTime();
        if (visuals().flashActive()) {
            this.stage.clearFlashes();
            this.stage.appear(now);
            this.stage.explode(now, visuals().copy(), false);
        }
    }

    private void scheduleFlashPreview() {
        this.flashPreviewAt = System.nanoTime() + FLASH_PREVIEW_DELAY_NANOS;
    }

    private void scheduleSoundPreview() {
        this.soundPreviewAt = System.nanoTime() + SOUND_PREVIEW_DELAY_NANOS;
    }

    private void glowTouched() {
        this.glowTouchedAt = System.nanoTime();
    }

    // ------------------------------------------------------------------------------------------
    // Colours
    // ------------------------------------------------------------------------------------------

    private int layerColor() {
        return switch (this.layer) {
            case OUTER -> visuals().outerColor;
            case INNER -> visuals().innerColor;
            case CORE -> visuals().coreColor;
        };
    }

    private String layerName() {
        return switch (this.layer) {
            case OUTER -> this.spanish ? "Editando: exterior" : "Editing: outer";
            case INNER -> this.spanish ? "Editando: interior" : "Editing: inner";
            case CORE -> this.spanish ? "Editando: núcleo" : "Editing: core";
        };
    }

    private void setLayerColor(int color) {
        switch (this.layer) {
            case OUTER -> visuals().outerColor = color;
            case INNER -> visuals().innerColor = color;
            case CORE -> visuals().coreColor = color;
        }
        if (!visuals().customGlowColor) {
            // The glow follows the layers here, so the panels' light does too.
            glowTouched();
        }
    }

    private void selectLayer(Layer target) {
        this.layer = target;
        int color = layerColor();
        this.updatingControls = true;
        if (this.layerPicker != null) {
            this.layerPicker.setColor(color);
        }
        if (this.layerHex != null) {
            this.layerHex.setValue(CrystalVisualConfig.toHex(color));
            this.layerHex.setTextColor(0xFFF0E5F6);
        }
        this.updatingControls = false;
        if (this.outerButton != null) {
            this.outerButton.setSelected(target == Layer.OUTER);
            this.innerButton.setSelected(target == Layer.INNER);
            this.coreButton.setSelected(target == Layer.CORE);
        }
    }

    private void onLayerPicker(int color) {
        if (this.updatingControls) {
            return;
        }
        setLayerColor(color);
        this.updatingControls = true;
        if (this.layerHex != null) {
            this.layerHex.setValue(CrystalVisualConfig.toHex(color));
            this.layerHex.setTextColor(0xFFF0E5F6);
        }
        this.updatingControls = false;
    }

    private void onLayerHex(String value) {
        if (this.updatingControls) {
            return;
        }
        try {
            int color = CrystalVisualConfig.parseHex(value);
            setLayerColor(color);
            this.updatingControls = true;
            if (this.layerPicker != null) {
                this.layerPicker.setColor(color);
            }
            this.updatingControls = false;
            this.layerHex.setTextColor(0xFFF0E5F6);
        } catch (IllegalArgumentException exception) {
            this.layerHex.setTextColor(0xFFFF7D9C);
        }
    }

    private void onGlowPicker(int color) {
        if (this.updatingControls) {
            return;
        }
        visuals().glowColor = color;
        glowTouched();
        this.updatingControls = true;
        if (this.glowHex != null) {
            this.glowHex.setValue(CrystalVisualConfig.toHex(color));
            this.glowHex.setTextColor(0xFFF0E5F6);
        }
        this.updatingControls = false;
    }

    private void onGlowHex(String value) {
        if (this.updatingControls) {
            return;
        }
        try {
            int color = CrystalVisualConfig.parseHex(value);
            visuals().glowColor = color;
            glowTouched();
            this.updatingControls = true;
            if (this.glowPicker != null) {
                this.glowPicker.setColor(color);
            }
            this.updatingControls = false;
            this.glowHex.setTextColor(0xFFF0E5F6);
        } catch (IllegalArgumentException exception) {
            this.glowHex.setTextColor(0xFFFF7D9C);
        }
    }

    private void drawSwatch(GuiGraphicsExtractor graphics, int x, int y, int height, int color, String label, float alpha) {
        int size = Math.max(6, Math.min(11, height - 5));
        int swatchY = y + (height - size) / 2;
        graphics.fill(x, swatchY, x + size, swatchY + size, CrystalTheme.fade(color | 0xFF000000, alpha));
        CrystalUi.outline(graphics, x - 1, swatchY - 1, size + 2, size + 2, CrystalTheme.fade(0xFFE8D8F1, alpha));
        int textX = x + size + 5;
        Rect viewport = this.leftPanel.viewport();
        int room = Math.max(0, Math.max(viewport.right(), this.rightPanel.viewport().right()) - textX);
        if (room > 20) {
            CrystalUi.label(graphics, this.font, HubDraw.fit(this.font, label, room), textX, swatchY + (size - 8) / 2 + 1,
                    CrystalTheme.fade(HubSkin.current().muted, alpha));
        }
    }

    private String speedLabel(String label, int percent) {
        if (percent == 0) {
            return label + ": " + (this.spanish ? "Estático" : "Static");
        }
        return label + ": " + percent + "%" + (percent == 100 ? " (Vanilla)" : "");
    }

    private void resetPanel(SidePanel panel) {
        CrystalAppearance defaults = CrystalVisualConfig.defaults(this.enemy);
        if (panel == this.leftPanel) {
            // Back to the shipped look: purple for your crystals, the plain texture for the enemy's.
            visuals().outerColor = defaults.outerColor;
            visuals().innerColor = defaults.innerColor;
            visuals().coreColor = defaults.coreColor;
            visuals().rotationSpeedPercent = 100;
            visuals().floatingSpeedPercent = 100;
            visuals().sizePercent = 100;
            commit();
            rebuildLeft(true);
            refreshBlurControl();
        } else {
            visuals().glowEnabled = defaults.glowEnabled;
            visuals().glowPowerPercent = defaults.glowPowerPercent;
            visuals().glowReflectionsPercent = defaults.glowReflectionsPercent;
            visuals().customGlowColor = defaults.customGlowColor;
            visuals().glowColor = defaults.glowColor;
            visuals().flashEnabled = defaults.flashEnabled;
            visuals().flashScalePercent = defaults.flashScalePercent;
            visuals().flashOpacityPercent = defaults.flashOpacityPercent;
            visuals().flashDurationMillis = defaults.flashDurationMillis;
            visuals().motionBlurPercent = defaults.motionBlurPercent;
            visuals().coreGlowPercent = defaults.coreGlowPercent;
            if (!this.enemy) {
                CrystalVisualConfig.setFlashStyle(CrystalFlashStyle.EXPLOSION);
                CrystalVisualConfig.setGlowStyle(CrystalGlowStyle.LIGHT);
            }
            glowTouched();
            commit();
            rebuildRight(true);
        }
        this.minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME, 1.2F, 0.6F));
    }

    // ------------------------------------------------------------------------------------------
    // The drawer under the carousel
    // ------------------------------------------------------------------------------------------

    private void rebuildDrawer() {
        for (AbstractWidget widget : this.drawerWidgets) {
            removeWidget(widget);
        }
        this.drawerWidgets.clear();
        this.drawerList = null;
        this.soundFileButton = null;
        this.ghostToggle = null;
        this.safeToggle = null;
        this.rescanButton = null;
        this.herziumOrderButton = null;
        this.converterGrid = null;
        this.converterSearch = null;
        TabCarousel tabs = currentTabs();
        this.drawerId = tabs.selectedItem().id();
        Rect drawer = this.layout.drawer(10_000, drawerShare(this.drawerId));
        int width = Math.max(40, drawer.width() - 16);
        RowList list = switch (this.drawerId) {
            case "sound" -> buildSound(width);
            case "tweaks" -> buildTweaks(width);
            case "advanced" -> buildAdvanced(width);
            case "profile" -> buildProfile(width);
            case "converter" -> buildConverter(width);
            default -> null;
        };
        if (list != null) {
            this.drawerList = list;
            for (AbstractWidget widget : list.widgets()) {
                widget.visible = false;
                addWidget(widget);
                this.drawerWidgets.add(widget);
            }
            list.setViewport(drawerViewport(currentDrawerTarget()));
            list.reveal(System.nanoTime(), 1);
        }
        if (this.drawerId.equals("tweaks") || this.drawerId.equals("advanced")) {
            applyPauseReason();
        }
    }

    private static float drawerShare(String id) {
        return switch (id) {
            case "sound" -> 0.6F;
            case "tweaks" -> 0.64F;
            case "advanced" -> 0.66F;
            case "profile" -> 0.46F;
            case "converter" -> 0.74F;
            default -> 1.0F;
        };
    }

    private boolean drawerHidesCrystal() {
        return this.drawerId.equals("kohs") || this.drawerId.equals("soon");
    }

    /** The drawer's full size for the selected tab, open or not. */
    private Rect currentDrawerTarget() {
        if (this.drawerList != null) {
            return this.layout.drawer(this.drawerList.contentHeight() + 12, drawerShare(this.drawerId));
        }
        return this.layout.drawer(this.layout.stage.height(), 1.0F);
    }

    /** The drawer as drawn this frame: its target height times how far it is open. */
    private Rect currentDrawerRect() {
        Rect target = currentDrawerTarget();
        int height = Math.round(target.height() * HubMotion.easeOutCubic(this.drawerProgress));
        return height <= 1 ? Rect.EMPTY : target.withHeight(height);
    }

    private static Rect drawerViewport(Rect drawer) {
        return new Rect(drawer.x() + 6, drawer.y() + 5, Math.max(1, drawer.width() - 16), Math.max(1, drawer.height() - 10));
    }

    private Rect crystalTarget() {
        if (this.phase == Phase.INTRO) {
            return this.overlays.introCrystal();
        }
        if (this.phase == Phase.GATE || this.phase == Phase.ENTER) {
            return this.gate.crystalRect();
        }
        boolean open = currentTabs().open();
        if (open && drawerHidesCrystal()) {
            Rect stage = this.layout.stage;
            return new Rect(stage.centerX(), stage.centerY(), 0, 0);
        }
        return this.layout.crystal(open ? currentDrawerTarget().height() : 0);
    }

    private RowList buildSound(int width) {
        int control = this.layout.controlHeight;
        int slider = this.layout.sliderHeight;
        RowList list = new RowList(width, this.layout.rowGap);
        list.row(control);
        list.full(new HubButton(0, 0, 0, 0, Component.literal(this.spanish ? "Sonido personalizado" : "Custom sound"), ignored -> {
            CrystalVisualConfig.setCustomSoundEnabled(!CrystalVisualConfig.customSoundEnabled());
            commit();
            CrystalSoundManager.reloadFromConfig();
            if (this.soundFileButton != null) {
                this.soundFileButton.active = CrystalVisualConfig.customSoundEnabled();
            }
            scheduleSoundPreview();
        }).switchOf(CrystalVisualConfig::customSoundEnabled).icon(HubOverlays::noteIcon));
        int half = (width - 4) / 2;
        list.row(control);
        this.soundFileButton = list.cell(new HubButton(0, 0, 0, 0, Component.literal(this.spanish ? "Elegir archivo…" : "Choose file…"),
                ignored -> SoundFilePicker.choose(this.spanish, this::importSound, message -> this.soundStatus = message)), 0, half);
        this.soundFileButton.active = CrystalVisualConfig.customSoundEnabled();
        list.cell(new HubButton(0, 0, 0, 0, Component.literal(this.spanish ? "▶ Probar" : "▶ Test"), ignored -> {
            long now = System.nanoTime();
            this.stage.appear(now - 1_000_000_000L);
            this.stage.explode(now, visuals().copy(), true);
        }).style(HubButton.Style.PRIMARY), half + 4, width - half - 4);
        list.row(this.font.lineHeight + 1);
        list.paint((graphics, font, x, y, cellWidth, height, alpha) -> {
            String file = CrystalVisualConfig.customSoundFileName();
            String status = this.soundStatus.isBlank()
                    ? (file.isBlank() ? (this.spanish ? "Sin archivo: suena la explosión de Vanilla" : "No file: Vanilla's explosion plays") : file)
                    : this.soundStatus;
            CrystalUi.label(graphics, font, HubDraw.fit(font, status, cellWidth), x, y, CrystalTheme.fade(0xFFD7C2DF, alpha));
        }, 0, width);
        list.row(slider);
        list.full(HubSlider.linear(0, 0, 0, 0, 0.0D, 1.0D, CrystalVisualConfig.soundVolume(),
                value -> CrystalVisualConfig.setSoundVolume((float) value),
                value -> (this.spanish ? "Volumen: " : "Volume: ") + Math.round(value * 100.0D) + "%").onRelease(() -> {
                    commit();
                    scheduleSoundPreview();
                }));
        list.row(slider);
        list.full(HubSlider.linear(0, 0, 0, 0, 0.5D, 2.0D, CrystalVisualConfig.soundSpeed(),
                value -> CrystalVisualConfig.setSoundSpeed((float) value),
                value -> String.format(Locale.ROOT, this.spanish ? "Velocidad: %.2fx" : "Speed: %.2fx", value)).onRelease(() -> {
                    commit();
                    scheduleSoundPreview();
                }));
        list.space(1);
        list.note(this.font, this.spanish ? "WAV, OGG o MP3 de hasta 5 segundos. Solo lo oyes tú." : "WAV, OGG or MP3, up to 5 seconds. Only you hear it.", 0);
        return list;
    }

    private void importSound(Path file) {
        String error = CrystalSoundManager.importFile(file);
        this.soundStatus = error.isEmpty() ? (this.spanish ? "Sonido cargado" : "Sound loaded") : localizeSoundError(error);
        if (error.isEmpty()) {
            scheduleSoundPreview();
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

    private RowList buildTweaks(int width) {
        int control = this.layout.controlHeight;
        int slider = this.layout.sliderHeight;
        int indent = width >= 150 ? 9 : 5;
        RowList list = new RowList(width, this.layout.rowGap);
        list.heading(this.spanish ? "Ayudas de cristal" : "Crystal helpers");
        if (GhostCrystalSupport.isAvailable()) {
            list.row(control);
            this.ghostToggle = list.full(new HubButton(0, 0, 0, 0, Component.literal(this.spanish ? "Cristales fantasma" : "Ghost crystals"), ignored -> {
                CrystalVisualConfig.setGhostCrystals(!CrystalVisualConfig.ghostCrystals());
                commit();
            }).switchOf(CrystalVisualConfig::ghostCrystals));
        }
        list.row(control);
        this.safeToggle = list.full(new HubButton(0, 0, 0, 0, Component.literal(this.spanish ? "Evitar romper obsidiana" : "Don't break obsidian"), ignored -> {
            CrystalVisualConfig.setSafeCrystal(!CrystalVisualConfig.safeCrystal());
            commit();
        }).switchOf(CrystalVisualConfig::safeCrystal));
        list.row(control);
        HubButton debounce = list.full(new HubButton(0, 0, 0, 0, Component.literal(this.spanish ? "Antirrebote de obsidiana" : "Obsidian debounce"),
                ignored -> {
                    CrystalVisualConfig.setObsidianDebounce(!CrystalVisualConfig.obsidianDebounce());
                    ObsidianDebounce.settingsChanged();
                    commit();
                    rebuildDrawer();
                }).switchOf(CrystalVisualConfig::obsidianDebounce));
        debounce.setTooltip(Tooltip.create(Component.literal(this.spanish
                ? "Evita poner dos obsidianas sin querer: tras colocar una, rechaza otra durante el tiempo elegido. Cambiar de ranura lo reinicia, así obsidiana, cristal, obsidiana nunca se frena. Un clic rechazado no envía nada; apágalo si tu servidor no permite filtros de entrada."
                : "Stops accidental double obsidian: after placing one, it refuses another for the chosen time. Switching slots resets it, so obsidian, crystal, obsidian is never slowed. A refused click sends nothing; turn it off if your server forbids input filters.")));
        if (CrystalVisualConfig.obsidianDebounce()) {
            list.row(slider, indent);
            list.full(HubSlider.curved(0, 0, 0, 0, 0, CrystalVisualConfig.MAX_OBSIDIAN_DEBOUNCE_MILLIS, CrystalVisualConfig.obsidianDebounceMillis(),
                    value -> {
                        CrystalVisualConfig.setObsidianDebounceMillis(debounceStep(value));
                        ObsidianDebounce.settingsChanged();
                    }, value -> (this.spanish ? "Tiempo: " : "Window: ") + debounceLabel(debounceStep(value))).onRelease(this::commit));
        }
        list.space(1);
        list.note(this.font, this.spanish
                ? "Evitar romper obsidiana y el antirrebote filtran clics: envían menos acciones que Vanilla. Apágalos si tu servidor no permite filtros de entrada."
                : "Don't break obsidian and the debounce filter clicks: they send fewer actions than Vanilla. Turn them off if your server forbids input filters.", 0);
        return list;
    }

    /** Converter My Crystal: something else drawn in the crystal's place, chosen from a searched catalogue. */
    private RowList buildConverter(int width) {
        int control = this.layout.controlHeight;
        int indent = width >= 150 ? 9 : 5;
        RowList list = new RowList(width, this.layout.rowGap);
        list.heading("Converter My Crystal");
        list.row(control);
        HubButton toggle = list.full(new HubButton(0, 0, 0, 0, Component.literal(this.enemy
                ? (this.spanish ? "Convertir los cristales ajenos" : "Convert enemy crystals")
                : (this.spanish ? "Convertir mi cristal" : "Convert my crystal")), ignored -> {
            visuals().converterEnabled = !visuals().converterEnabled;
            commit();
            converterChanged();
            rebuildDrawer();
        }).switchOf(() -> visuals().converterEnabled));
        toggle.setTooltip(Tooltip.create(Component.literal(this.spanish
                ? "Dibuja un bloque, un ítem o una entidad en lugar del cristal. Solo cambia el dibujo en tu pantalla: el cristal sigue donde está, con la misma zona de golpe."
                : "Draws a block, an item or an entity in the crystal's place. Only the drawing on your screen changes: the crystal stays where it is, with the same box to hit.")));
        if (visuals().converterEnabled) {
            int branch = width - indent;
            int gap = 3;
            int half = (branch - gap) / 2;
            list.row(control, indent);
            list.cell(new HubButton(0, 0, 0, 0, Component.literal(this.spanish ? "Bloques e ítems" : "Blocks & items"),
                    ignored -> showConverterKind(false)), 0, half).setSelected(!this.converterEntities);
            list.cell(new HubButton(0, 0, 0, 0, Component.literal(this.spanish ? "Entidades" : "Entities"),
                    ignored -> showConverterKind(true)), half + gap, branch - half - gap).setSelected(this.converterEntities);
            list.row(control, indent);
            this.converterSearch = list.full(new HubEditBox(this.font, 0, 0, 0, control, Component.literal(this.spanish ? "Buscar" : "Search")));
            this.converterSearch.setHint(Component.literal(this.spanish ? "Buscar por nombre o id…" : "Search by name or id…"));
            this.converterSearch.setMaxLength(60);
            this.converterSearch.setValue(this.converterQuery);
            this.converterSearch.setResponder(value -> {
                this.converterQuery = value;
                if (this.converterGrid != null) {
                    this.converterGrid.show(this.converterGrid.source(), value);
                }
            });
            list.row(Math.max(64, Math.min(124, this.layout.stage.height() / 3)), indent);
            this.converterGrid = list.full(new ConverterGrid(this.font, Component.literal(this.spanish ? "Catálogo" : "Catalogue"),
                    this.spanish ? "Entra en un mundo para ver el catálogo." : "Join a world to see the catalogue.",
                    this.spanish ? "Nada coincide con la búsqueda." : "Nothing matches the search.",
                    () -> visuals().converterTarget, target -> {
                        // A second click on the chosen one lets go of it.
                        visuals().converterTarget = target.equals(visuals().converterTarget) ? "" : target;
                        commit();
                        converterChanged();
                    }));
            this.converterGrid.show(this.converterEntities ? ConverterGrid.entities() : ConverterGrid.items(), this.converterQuery);
            list.row(this.font.lineHeight + 2, indent);
            list.paint((graphics, font, x, y, cellWidth, height, alpha) -> drawConverterChoice(graphics, font, x, y, cellWidth, alpha), 0, branch);
        }
        list.space(1);
        list.note(this.font, this.spanish
                ? "Lo que elijas se dibuja por encima de la textura original de Minecraft y de la de cualquier resource pack. El giro, la flotación y el tamaño siguen en Colores; el brillo, en Brillo."
                : "What you choose is drawn over Minecraft's own texture and over any resource pack's. Rotation, floating and size stay in Colours; the glow, in Glow.", 0);
        dimUnusedProfile(list, null);
        return list;
    }

    private void showConverterKind(boolean entities) {
        this.converterEntities = entities;
        rebuildDrawer();
    }

    /** The side panels dim what a converted crystal does not draw. */
    private void converterChanged() {
        rebuildLeft(true);
        rebuildRight(true);
    }

    private void drawConverterChoice(GuiGraphicsExtractor graphics, Font font, int x, int y, int width, float alpha) {
        HubSkin skin = HubSkin.current();
        String target = visuals().converterTarget;
        String text;
        int color = skin.muted;
        if (target.isEmpty()) {
            text = this.spanish ? "Nada elegido: el cristal se ve como siempre." : "Nothing chosen: the crystal looks as usual.";
        } else if (!CrystalConverter.drawable(target)) {
            boolean needsWorld = this.minecraft == null || this.minecraft.level == null;
            text = ConverterGrid.nameOf(target) + (needsWorld
                    ? (this.spanish ? ": se verá al entrar en un mundo." : ": shows once you are in a world.")
                    : (this.spanish ? ": no se puede dibujar; el cristal se queda como cristal." : ": cannot be drawn; the crystal stays a crystal."));
            color = 0xFFFFC48A;
        } else {
            text = (this.spanish ? "Elegido: " : "Chosen: ") + ConverterGrid.nameOf(target);
            color = skin.text;
        }
        CrystalUi.label(graphics, font, HubDraw.fit(font, text, width), x, y + 1, CrystalTheme.fade(color, alpha));
    }

    /** The enemy profile switched off: its controls change nothing that is drawn, so they wait, dimmed. */
    private void dimUnusedProfile(RowList list, AbstractWidget except) {
        if (!this.enemy || CrystalVisualConfig.enemyCustomEnabled()) {
            return;
        }
        Component note = Component.literal(this.spanish
                ? "Este perfil está apagado: los cristales ajenos se ven con tu propio perfil. Enciende «Usar este perfil» para cambiarlo."
                : "This profile is off: enemy crystals use your own profile. Turn \"Use this profile\" on to change it.");
        for (AbstractWidget widget : list.widgets()) {
            if (widget != except) {
                widget.active = false;
                widget.setTooltip(Tooltip.create(note));
                if (widget instanceof EditBox box) {
                    box.setEditable(false);
                }
            }
        }
    }

    private void profileUseChanged() {
        CrystalVisualConfig.setEnemyCustomEnabled(!CrystalVisualConfig.enemyCustomEnabled());
        commit();
        rebuildLeft(true);
        rebuildRight(true);
        rebuildDrawer();
    }

    /** The old glow, optimization, compatibility and Herzium: the tools behind the helpers. */
    private RowList buildAdvanced(int width) {
        int control = this.layout.controlHeight;
        int indent = width >= 150 ? 9 : 5;
        RowList list = new RowList(width, this.layout.rowGap);
        list.heading(this.spanish ? "Brillo" : "Glow");
        list.row(control);
        HubButton oldGlow = list.full(new HubButton(0, 0, 0, 0, Component.literal("Old KoHs Crystal Glow"), ignored -> {
            CrystalVisualConfig.setOldGlow(!CrystalVisualConfig.oldGlow());
            glowTouched();
            commit();
            rebuildRight(true);
        }).switchOf(CrystalVisualConfig::oldGlow));
        oldGlow.setTooltip(Tooltip.create(Component.literal(this.spanish
                ? "El brillo de antes: un halo con rayos detrás del cristal en lugar de luz en sus capas. La potencia, los reflejos, el color y el desenfoque siguen igual; el estilo y el brillo del núcleo, que son de las capas, quedan atenuados. Para tus cristales y los ajenos."
                : "The glow as it used to be: a halo with rays behind the crystal instead of light in its layers. Power, reflections, colour and blur work the same; the style and the core's glow, which belong to the layers, are dimmed. For your crystals and the enemy's.")));
        list.heading(this.spanish ? "Optimización" : "Optimization");
        list.row(control);
        HubButton forceOff = list.full(new HubButton(0, 0, 0, 0, Component.literal(this.spanish ? "Forzar apagado" : "Force off"), ignored -> {
            boolean off = !CrystalVisualConfig.forceOffOptimizations();
            CrystalVisualConfig.setForceOffOptimizations(off);
            if (off) {
                CrystalBreakPrediction.reset();
                GhostCrystalTracker.reset();
                CrystalPlacementFeedback.resetPredictionState();
            }
            commit();
            applyPauseReason();
        }).switchOf(CrystalVisualConfig::forceOffOptimizations).style(HubButton.Style.DANGER));
        forceOff.setTooltip(Tooltip.create(Component.literal(this.spanish
                ? "Apaga todas las optimizaciones de cristal de Crystal Tweaks: la rotura y la explosión instantáneas en pantalla, los cristales fantasma, Evitar romper obsidiana y el seguimiento de colocaciones. Todo queda como en Vanilla; colores, brillo, destellos y sonidos siguen igual."
                : "Turns off every crystal optimization of Crystal Tweaks: the instant break and explosion on screen, ghost crystals, Don't break obsidian and placement tracking. Everything behaves like Vanilla; colours, glow, flashes and sounds stay as they are.")));
        list.row(control);
        HubButton benchmark = list.full(new HubButton(0, 0, 0, 0, Component.literal(this.spanish ? "Benchmark de optimizadores…" : "Optimizer benchmark…"),
                ignored -> this.minecraft.setScreen(new CrystalBenchmarkScreen(this))).icon(HubOverlays::chartIcon));
        benchmark.setTooltip(Tooltip.create(Component.literal(this.spanish
                ? "Mide lo que tardan tus cristales en aparecer y desaparecer, en tu pantalla y en el servidor, con el optimizador que tengas. Solo observa lo que ya haces."
                : "Measures how long your crystals take to appear and disappear, on your screen and on the server, with whichever optimizer you have. It only watches what you already do.")));
        list.heading(this.spanish ? "Compatibilidad" : "Compatibility");
        list.row(control);
        HubButton monitor = list.full(new HubButton(0, 0, 0, 0, Component.literal(this.spanish ? "Monitor de conflictos" : "Conflict Monitor"),
                ignored -> this.minecraft.setScreen(new ConflictMonitorScreen(this))).icon(HubOverlays::gearIcon));
        monitor.setTooltip(Tooltip.create(Component.literal(this.spanish
                ? "Analiza localmente los mixins instalados y muestra coincidencias que podrían interferir con Crystal Tweaks."
                : "Locally scans installed mixins and shows matches that could interfere with Crystal Tweaks.")));
        list.row(control);
        this.rescanButton = list.full(new HubButton(0, 0, 0, 0, rescanMessage(), ignored -> {
            OptimizerConflictDetector.rescan();
            refreshOptimizerControls();
        }));
        this.rescanButton.setTooltip(Tooltip.create(Component.literal(this.spanish
                ? "Repite la comprobación de optimizadores sin reiniciar. Útil tras quitar el mod que dejó las ayudas en pausa."
                : "Runs the optimizer check again without restarting. Useful after removing the mod that paused the helpers.")));
        this.rescanReflectsPending = CrystalOptimizerGuard.scanPending();
        this.rescanButton.active = !this.rescanReflectsPending;
        list.heading("Herzium");
        boolean installed = HerziumBridge.installed();
        list.row(control);
        HubButton herzium = list.full(new HubButton(0, 0, 0, 0, Component.literal(installed
                ? (this.spanish ? "Integrar con Herzium" : "Integrate with Herzium")
                : (this.spanish ? "Herzium (no instalado)" : "Herzium (not installed)")), ignored -> {
            if (HerziumBridge.installed()) {
                CrystalVisualConfig.setHerziumIntegration(!CrystalVisualConfig.herziumIntegration());
                commit();
                rebuildDrawer();
            }
        }).switchOf(() -> installed && CrystalVisualConfig.herziumIntegration()).style(HubButton.Style.HERZIUM));
        herzium.active = installed;
        herzium.setTooltip(Tooltip.create(Component.literal(installed
                ? (this.spanish ? "Herzium " + HerziumBridge.version() + " detectado: su orden de hotbar, aquí mismo."
                        : "Herzium " + HerziumBridge.version() + " detected: its hotbar order, right here.")
                : (this.spanish ? "Descárgalo en modrinth.com/mod/herzium para activar esta integración."
                        : "Get it from modrinth.com/mod/herzium to enable this integration."))));
        if (installed && CrystalVisualConfig.herziumIntegration()) {
            list.row(control, indent);
            this.herziumOrderButton = list.full(new HubButton(0, 0, 0, 0, herziumOrderMessage(), ignored -> {
                HerziumBridge.cycleHotbarOrder();
                if (this.herziumOrderButton != null) {
                    this.herziumOrderButton.setMessage(herziumOrderMessage());
                    this.herziumOrderButton.active = HerziumBridge.orderAvailable();
                }
            }));
            this.herziumOrderButton.active = HerziumBridge.orderAvailable();
            this.herziumOrderButton.setTooltip(Tooltip.create(Component.literal(this.spanish
                    ? "Qué ranura gana si pulsas dos teclas de hotbar a la vez. Herzium: la última. Vanilla: la más alta. Vanilla invertido: la más baja. Cambia la ranura real que sostienes, así que el servidor lo ve; revisa sus normas."
                    : "Which slot wins when you press two hotbar keys at once. Herzium: the last one. Vanilla: the highest. Vanilla reversed: the lowest. It changes the slot you really hold, so the server sees it; check its rules.")));
        }
        return list;
    }

    private RowList buildProfile(int width) {
        int control = this.layout.controlHeight;
        RowList list = new RowList(width, this.layout.rowGap);
        list.row(control);
        list.full(new HubButton(0, 0, 0, 0, Component.literal(this.spanish ? "Usar este perfil" : "Use this profile"),
                ignored -> profileUseChanged()).switchOf(CrystalVisualConfig::enemyCustomEnabled).style(HubButton.Style.DANGER));
        list.space(1);
        list.note(this.font, this.spanish
                ? "Identificación aproximada: Crystal Tweaks relaciona tus intentos de colocación con las apariciones de cristales. Los que no coinciden, incluidos los de dueño desconocido, usan este perfil; el servidor no envía quién colocó cada cristal."
                : "Approximate attribution: Crystal Tweaks matches your placement attempts to crystal spawns. The ones that do not match, unknown owners included, use this profile; the server does not send who placed each crystal.", 0);
        return list;
    }

    private static int debounceStep(double millis) {
        int value = (int) Math.round(millis);
        return value < 1000 ? Math.round(value / 10.0F) * 10 : Math.round(value / 50.0F) * 50;
    }

    private String debounceLabel(int millis) {
        if (millis <= 0) {
            return "Vanilla";
        }
        if (millis < 1000) {
            return millis + " ms";
        }
        return String.format(Locale.ROOT, "%.2f s", millis / 1000.0D).replace(".00 s", " s");
    }

    private Component rescanMessage() {
        return Component.literal(CrystalOptimizerGuard.scanPending()
                ? (this.spanish ? "Comprobando…" : "Checking…")
                : (this.spanish ? "Volver a comprobar compatibilidad" : "Re-check compatibility"));
    }

    private Component herziumOrderMessage() {
        String current = HerziumBridge.hotbarOrder();
        String order = current.isEmpty() ? (this.spanish ? "no disponible" : "unavailable") : HerziumBridge.orderLabel(current, this.spanish);
        return Component.literal((this.spanish ? "Orden: " : "Order: ") + order);
    }

    private void refreshOptimizerControls() {
        boolean pending = CrystalOptimizerGuard.scanPending();
        if (this.rescanButton != null && pending != this.rescanReflectsPending) {
            this.rescanReflectsPending = pending;
            this.rescanButton.setMessage(rescanMessage());
            this.rescanButton.active = !pending;
        }
        if ((this.drawerId.equals("tweaks") || this.drawerId.equals("advanced"))
                && CrystalOptimizerGuard.pauseReason() != this.shownPauseReason) {
            applyPauseReason();
        }
    }

    /** Dims the helpers while the optimizations are paused, and says why in their tooltips. */
    private void applyPauseReason() {
        this.shownPauseReason = CrystalOptimizerGuard.pauseReason();
        boolean allowed = CrystalOptimizerGuard.optimizationsAllowed();
        String paused = allowed ? "" : (this.spanish ? " (en pausa)" : " (paused)");
        if (this.ghostToggle != null) {
            this.ghostToggle.setMessage(Component.literal((this.spanish ? "Cristales fantasma" : "Ghost crystals") + paused));
            this.ghostToggle.active = allowed;
            this.ghostToggle.setTooltip(helperTooltip(this.spanish
                    ? "Dibuja un cristal provisional mientras llega el del servidor, para que colocar no dependa de tu ping. Solo visual: no es una entidad, no se puede golpear y no cambia ningún paquete."
                    : "Draws a stand-in crystal while the server's real one is in flight, so placing does not depend on your ping. Visual only: it is not an entity, cannot be hit and changes no packet."));
        }
        if (this.safeToggle != null) {
            this.safeToggle.setMessage(Component.literal((this.spanish ? "Evitar romper obsidiana" : "Don't break obsidian") + paused));
            this.safeToggle.active = allowed;
            this.safeToggle.setTooltip(helperTooltip(this.spanish
                    ? "Evita minar la obsidiana sobre la que pones cristales mientras sostienes uno. Envía menos acciones que Vanilla; apágalo si tu servidor no permite filtros de entrada."
                    : "Keeps you from mining the obsidian under your crystals while you hold one. It sends fewer actions than Vanilla; turn it off if your server forbids input filters."));
        }
    }

    private Tooltip helperTooltip(String description) {
        String reason = switch (CrystalOptimizerGuard.pauseReason()) {
            case FORCED_OFF -> this.spanish ? "activaste «Forzar apagado» en la pestaña Avanzado." : "you turned on \"Force off\" in the Advanced tab.";
            case CONFLICT -> this.spanish
                    ? CrystalOptimizerGuard.conflictingModName() + " ya optimiza cristales y dos optimizadores sobre el mismo clic se pelean. Quítalo y pulsa «Volver a comprobar»."
                    : CrystalOptimizerGuard.conflictingModName() + " already optimizes crystals, and two optimizers on the same click fight. Remove it and press \"Re-check\".";
            case CHECKING -> this.spanish ? "se está comprobando la compatibilidad con tus mods." : "compatibility with your mods is being checked.";
            case NONE -> "";
        };
        if (reason.isEmpty()) {
            return Tooltip.create(Component.literal(description));
        }
        return Tooltip.create(Component.literal(description).append(Component.literal("\n\n"))
                .append(Component.literal((this.spanish ? "En pausa: " : "Paused: ") + reason).withStyle(ChatFormatting.GOLD)));
    }

    // ------------------------------------------------------------------------------------------
    // Drawing
    // ------------------------------------------------------------------------------------------

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        HubSkin skin = HubSkin.current();
        graphics.fillGradient(0, 0, this.width, this.height, skin.backdropTop, skin.backdropBottom);
        this.minecraft.gui.extractDeferredSubtitles();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        long now = System.nanoTime();
        float frame = Mth.clamp((now - this.lastFrame) / 1_000_000.0F, 0.0F, 50.0F);
        this.lastFrame = now;
        double seconds = now / 1_000_000_000.0D;
        CrystalAppearance look = visuals().copy();
        updateSkin(now, look);
        advance(now);
        HubSkin skin = HubSkin.current();

        HubDraw.isolate(graphics);
        CrystalUi.floatingParticles(graphics, this.width, this.height, seconds);
        HubDraw.isolate(graphics);

        boolean main = this.phase == Phase.MAIN || this.phase == Phase.SETTLE;
        boolean gateUp = this.phase == Phase.INTRO || this.phase == Phase.GATE || this.phase == Phase.ENTER;
        boolean interactive = this.phase == Phase.MAIN && !this.overlays.blocking();
        float chrome = gateUp ? 0.0F
                : this.phase == Phase.SETTLE ? HubMotion.easeOutCubic(HubMotion.progress(now - this.phaseAt - 380_000_000L, 360_000_000L)) : 1.0F;
        if (this.phase == Phase.TRANSITION) {
            chrome = this.overlays.transitionLanded(now) ? 1.0F : 0.0F;
        }
        drawHeader(graphics, now, seconds, chrome, skin);
        this.switchButton.visible = chrome > 0.5F && (this.phase == Phase.MAIN || this.phase == Phase.SETTLE);
        this.switchButton.setAlpha(chrome);
        this.doneButton.visible = !gateUp;
        this.doneButton.setAlpha(Math.max(0.35F, chrome));

        // The drawer's height follows the carousel.
        TabCarousel tabs = currentTabs();
        this.drawerProgress = HubMotion.damp(this.drawerProgress, tabs.open() ? 1.0F : 0.0F, frame, 65.0F);
        if (Math.abs(this.drawerProgress - (tabs.open() ? 1.0F : 0.0F)) < 0.002F) {
            this.drawerProgress = tabs.open() ? 1.0F : 0.0F;
        }
        if (this.phase != Phase.INTRO) {
            this.stage.setTarget(crystalTarget(), false);
        }
        // The stone wall: behind the intro and the gate, and still breaking while the menu comes up.
        if (gateUp || (this.gate.entering() && !this.gate.finished(now))) {
            this.gate.renderBackdrop(graphics, now, look.haloColor(), glowPower(look), this.stage.centerX(), this.stage.centerY(),
                    this.stage.size(), 1.0F);
        }

        // The panels, their rows and the drawer: placed first, so their widgets are where they are drawn.
        boolean panelsInteractive = interactive && (this.layout.wide || this.narrow != Narrow.NONE);
        this.leftPanel.place(now, frame, panelsInteractive);
        this.rightPanel.place(now, frame, panelsInteractive);
        Rect drawer = currentDrawerRect();
        if (this.drawerList != null) {
            Rect viewport = drawerViewport(currentDrawerTarget());
            this.drawerList.setViewport(new Rect(viewport.x(), viewport.y(), viewport.width(),
                    Math.max(1, Math.min(viewport.height(), drawer.height() - 10))));
            if (!this.layout.wide && this.narrow != Narrow.NONE) {
                // A narrow window's open panel covers the drawer: its controls step aside.
                this.drawerList.hide();
            } else {
                this.drawerList.place(now, frame, main ? chrome : 0.0F, interactive && this.drawerProgress > 0.97F && main);
            }
        }
        refreshOptimizerControls();
        runScheduledPreviews(now, look);

        if (this.phase == Phase.INTRO) {
            this.overlays.renderIntroBehind(graphics, now, look.haloColor(), this.stage.centerX(), this.stage.centerY(), this.stage.size());
        }
        if (main || gateUp || this.overlays.transitionLanded(now)) {
            this.stage.render(graphics, this.font, now, frame, look, 1.0F, mouseX, mouseY, this.phase == Phase.MAIN);
        }
        if (this.phase == Phase.GATE || this.phase == Phase.ENTER) {
            Rect at = this.gate.crystalRect();
            boolean onCrystal = this.phase == Phase.GATE && !this.overlays.blocking() && this.stage.contains(mouseX, mouseY);
            this.gate.renderPrompt(graphics, this.font, now, frame, at.centerX(), Math.min(this.height - 22, at.bottom() + 6), 1.0F,
                    onCrystal);
        } else if (this.phase == Phase.TRANSITION) {
            // The old crystal's explosion keeps playing while the menu breaks.
            this.stage.render(graphics, this.font, now, frame, look, 1.0F, -1, -1, false);
        }
        if (main || this.overlays.transitionLanded(now)) {
            int originX = this.stage.centerX();
            int originY = this.stage.centerY();
            int pointerX = interactive ? mouseX : -10_000;
            int pointerY = interactive ? mouseY : -10_000;
            if (this.layout.wide) {
                this.leftPanel.render(graphics, this.font, now, frame, pointerX, pointerY, originX, originY);
                this.rightPanel.render(graphics, this.font, now, frame, pointerX, pointerY, originX, originY);
                drawPanelIcons(graphics, now, look);
            }
            drawDrawer(graphics, drawer, now, frame, mouseX, mouseY, chrome, look);
            drawPractice(graphics, now, frame, mouseX, mouseY, chrome, skin);
            if (!this.layout.wide) {
                drawHandles(graphics, now, frame, mouseX, mouseY, chrome, skin);
                // A narrow window's panel is a drawer over the centre: drawn last, over a dimmed stage.
                if (this.narrow != Narrow.NONE) {
                    SidePanel open = this.narrow == Narrow.LEFT ? this.leftPanel : this.rightPanel;
                    HubDraw.isolate(graphics);
                    Rect body = this.layout.body;
                    graphics.fill(body.x(), body.y(), body.right(), body.bottom(),
                            CrystalTheme.withAlpha(0x05020A, Math.round(150 * open.openProgress(now))));
                    open.render(graphics, this.font, now, frame, pointerX, pointerY, originX, originY);
                    drawPanelIcons(graphics, now, look);
                }
            }
        }
        drawFooter(graphics, chrome, skin);

        // Under an overlay the controls stay drawn but get no pointer: nothing lights up or shows a
        // tooltip through the window on top of them.
        boolean pointer = interactive;
        super.extractRenderState(graphics, pointer ? mouseX : -10_000, pointer ? mouseY : -10_000, partialTick);

        HubDraw.isolate(graphics);
        boolean narrowOpen = !this.layout.wide && this.narrow != Narrow.NONE;
        if ((main || this.overlays.transitionLanded(now)) && !narrowOpen) {
            tabs.render(graphics, this.font, now, frame, mouseX, mouseY, chrome,
                    this.phase == Phase.MAIN && !CrystalVisualConfig.carouselHintDone() && !tabs.open());
            if (this.phase == Phase.MAIN && !CrystalVisualConfig.scrollHintDone()) {
                if (this.layout.wide || this.narrow == Narrow.LEFT) {
                    this.leftPanel.renderScrollHint(graphics, this.font, now);
                }
                if (this.layout.wide || this.narrow == Narrow.RIGHT) {
                    this.rightPanel.renderScrollHint(graphics, this.font, now);
                }
            }
        } else if (narrowOpen && this.phase == Phase.MAIN && !CrystalVisualConfig.scrollHintDone()) {
            (this.narrow == Narrow.LEFT ? this.leftPanel : this.rightPanel).renderScrollHint(graphics, this.font, now);
        }
        // The blast and the pieces of the gate fall in front of the menu as it comes up.
        this.gate.renderBlast(graphics, now, look.haloColor());
        this.gate.renderDebris(graphics, now);
        this.overlays.render(graphics, this.font, now, frame, mouseX, mouseY, this.width, this.height);
        drawStatusTooltip(graphics, mouseX, mouseY);
        drawHandleTooltip(graphics, mouseX, mouseY);
        drawResetTooltip(graphics, mouseX, mouseY);
        drawFoldTooltip(graphics, mouseX, mouseY);
    }

    /** On the open tab: a second click folds it and gives the crystal the middle back. */
    private void drawFoldTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (this.overlays.blocking() || this.phase != Phase.MAIN || (!this.layout.wide && this.narrow != Narrow.NONE)) {
            return;
        }
        String text = null;
        if (currentTabs().openTabBox(this.font).contains(mouseX, mouseY)) {
            text = this.spanish ? "Haz clic otra vez: el cristal vuelve al centro" : "Click again: the crystal goes back to the middle";
        } else {
            // The tabs beside the open one show only their icons: their names on hover.
            text = currentTabs().hoveredLabel(this.font, mouseX, mouseY);
        }
        if (text != null) {
            HubDraw.isolate(graphics);
            CrystalUi.tooltip(graphics, this.font, CrystalUi.wrap(this.font, text, Math.min(200, this.width - 30)), mouseX, mouseY,
                    this.width, this.height);
        }
    }

    /** What the reset buttons do, since a symbol alone does not say it. */
    private void drawResetTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (this.overlays.blocking() || this.phase != Phase.MAIN) {
            return;
        }
        String text = null;
        if (this.leftPanel.overReset(mouseX, mouseY)) {
            text = this.spanish
                    ? "Vuelve los colores de las tres capas y la animación a los de fábrica. Haz clic dos veces: el primero pide confirmación."
                    : "Returns the three layer colours and the animation to their defaults. Click twice: the first click asks to confirm.";
        } else if (this.rightPanel.overReset(mouseX, mouseY)) {
            text = this.spanish
                    ? "Vuelve el brillo y el destello a los de fábrica. Haz clic dos veces: el primero pide confirmación."
                    : "Returns the glow and the flash to their defaults. Click twice: the first click asks to confirm.";
        }
        if (text != null) {
            HubDraw.isolate(graphics);
            CrystalUi.tooltip(graphics, this.font, CrystalUi.wrap(this.font, text, Math.min(200, this.width - 30)), mouseX, mouseY,
                    this.width, this.height);
        }
    }

    /** The phase machine: settle into the main menu, finish transitions, play the intro out. */
    private void advance(long now) {
        if (this.overlays.introGone(now)) {
            this.overlays.clearIntro();
        }
        if (this.phase == Phase.ENTER && this.gate.opened(now)) {
            // The first blocks have burst: the crystal comes back on the stage and the menu unfolds.
            startSettle(now, true);
        }
        if (this.phase == Phase.SETTLE && now - this.phaseAt >= SETTLE_NANOS) {
            this.phase = Phase.MAIN;
        }
        if (this.phase == Phase.TRANSITION) {
            this.overlays.tickTransition(now);
        }
    }

    /** How strongly the glow lights the gate's stone: 0 with the glow off, 1 from 150% power up. */
    private static float glowPower(CrystalAppearance look) {
        return look.glowActive() ? Mth.clamp(look.glowPowerPercent / 150.0F, 0.15F, 1.0F) : 0.0F;
    }

    /** How much of the glow lights the side panels: a little always, more while it is being changed. */
    private void updateSkin(long now, CrystalAppearance look) {
        float activity = 1.0F - HubMotion.smoothstep(0.0F, 1.0F, HubMotion.progress(now - this.glowTouchedAt, GLOW_LIGHT_NANOS));
        float power = look.glowActive() ? 0.35F + 0.65F * Math.min(1.0F, look.glowPowerPercent / 100.0F) : 0.12F;
        float influence = (0.16F + 0.84F * activity) * power;
        boolean showEnemy = this.phase == Phase.TRANSITION && !this.overlays.transitionLanded(now) ? !this.enemy : this.enemy;
        HubSkin.current().update(showEnemy, look.haloColor(), influence);
    }

    private void runScheduledPreviews(long now, CrystalAppearance look) {
        if (this.flashPreviewAt != 0L && now >= this.flashPreviewAt) {
            this.flashPreviewAt = 0L;
            if (look.flashActive() && this.phase == Phase.MAIN) {
                this.stage.clearFlashes();
                this.stage.appear(now - 1_000_000_000L);
                this.stage.explode(now, look, false);
            }
        }
        if (this.soundPreviewAt != 0L && now >= this.soundPreviewAt) {
            this.soundPreviewAt = 0L;
            if (this.phase == Phase.MAIN) {
                this.stage.appear(now - 1_000_000_000L);
                this.stage.explode(now, look, true);
            }
        }
    }

    private void drawHeader(GuiGraphicsExtractor graphics, long now, double seconds, float alpha, HubSkin skin) {
        if (alpha <= 0.02F) {
            this.statusChip = Rect.EMPTY;
            return;
        }
        Rect header = this.layout.header;
        String title = this.enemy ? (this.spanish ? "CRISTALES AJENOS" : "ENEMY CRYSTALS") : "CRYSTAL TWEAKS";
        String brand = " KoHs";
        int titleWidth = this.font.width(title) + this.font.width(brand);
        int titleX = header.centerX() - titleWidth / 2;
        int titleY = header.y() + (header.height() - 8) / 2;
        int left = this.layout.switchButton.right() + 6;
        CrystalOptimizerGuard.PauseReason reason = CrystalOptimizerGuard.pauseReason();
        String chip = chipLabel(reason);
        int chipWidth = this.font.width(chip) + 16;
        int chipRight = header.right();
        boolean chipText = chipRight - chipWidth > titleX + titleWidth + 6;
        if (!chipText) {
            chipWidth = 12;
        }
        boolean showTitle = titleX >= left && titleX + titleWidth <= chipRight - chipWidth - 4;
        if (showTitle) {
            // The player's own crystal before the title, turning in place.
            this.titleCrystal.draw(graphics, titleX - 15, titleY - 4, 14, MiniCrystal.spinning(visuals()), now, 0.2F, 1.5F);
            CrystalUi.label(graphics, this.font, title, titleX, titleY, CrystalTheme.fade(skin.title, alpha), true);
            CrystalUi.label(graphics, this.font, brand, titleX + this.font.width(title), titleY, CrystalTheme.fade(skin.muted, alpha));
            CrystalUi.glint(graphics, this.font, title + brand, titleX, titleY, seconds + 1.3D, alpha);
        }
        int chipX = chipRight - chipWidth;
        int chipY = header.y() + (header.height() - 12) / 2;
        this.statusChip = new Rect(chipX, chipY, chipWidth, 12);
        int tone = chipTone(reason);
        HubDraw.glass(graphics, chipX, chipY, chipWidth, 12, 0x9A1B0928, 0x9A12061C, CrystalTheme.withAlpha(tone, 150), alpha);
        float pulse = 0.55F + 0.45F * (float) Math.sin(seconds * (reason == CrystalOptimizerGuard.PauseReason.CHECKING ? 7.0D : 2.6D));
        graphics.fill(chipX + 3, chipY + 3, chipX + 9, chipY + 9, CrystalTheme.fade(CrystalTheme.withAlpha(tone, Math.round(90 * pulse)), alpha));
        graphics.fill(chipX + 4, chipY + 4, chipX + 8, chipY + 8, CrystalTheme.fade(tone, alpha));
        if (chipText) {
            CrystalUi.label(graphics, this.font, chip, chipX + 12, chipY + 2, CrystalTheme.fade(CrystalTheme.lerp(tone, 0xFFFFFFFF, 0.35F), alpha));
        }
    }

    private String chipLabel(CrystalOptimizerGuard.PauseReason reason) {
        return switch (reason) {
            case FORCED_OFF -> this.spanish ? "Apagado" : "Forced off";
            case CONFLICT -> this.spanish ? "En pausa" : "Paused";
            case CHECKING -> this.spanish ? "Comprobando…" : "Checking…";
            case NONE -> CrystalOptimizerGuard.scanIncomplete()
                    ? (this.spanish ? "Activo · parcial" : "Active · partial")
                    : (this.spanish ? "Cliente activo" : "Client active");
        };
    }

    private static int chipTone(CrystalOptimizerGuard.PauseReason reason) {
        return switch (reason) {
            case FORCED_OFF -> CrystalTheme.STATUS_FORCED_OFF;
            case CONFLICT -> CrystalTheme.STATUS_PAUSED;
            case CHECKING -> CrystalTheme.STATUS_CHECKING;
            case NONE -> CrystalOptimizerGuard.scanIncomplete() ? CrystalTheme.STATUS_PARTIAL : CrystalTheme.STATUS_ACTIVE;
        };
    }

    private void drawStatusTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (!this.statusChip.contains(mouseX, mouseY) || this.overlays.blocking()) {
            return;
        }
        String detail = switch (CrystalOptimizerGuard.pauseReason()) {
            case FORCED_OFF -> this.spanish
                    ? "Optimizaciones apagadas por ti en Crystal Tweaks: todo se comporta como en Vanilla. Colores, brillo y sonidos siguen activos."
                    : "Optimizations turned off by you in Crystal Tweaks: everything behaves like Vanilla. Colours, glow and sounds keep working.";
            case CONFLICT -> this.spanish
                    ? "Optimización en pausa: " + CrystalOptimizerGuard.conflictingModName() + " ya optimiza cristales. Colores, brillo y sonidos siguen activos."
                    : "Optimization paused: " + CrystalOptimizerGuard.conflictingModName() + " already optimizes crystals. Colours, glow and sounds keep working.";
            case CHECKING -> this.spanish
                    ? "Ayudas en pausa mientras se comprueba la compatibilidad con otros mods."
                    : "Helpers wait while compatibility with other mods is checked.";
            case NONE -> this.spanish
                    ? "Crystal Tweaks optimiza solo tu cliente: el cristal que rompes desaparece al instante en tu pantalla, pero lo que llega al servidor no se acelera."
                    : "Crystal Tweaks only optimizes your client: a broken crystal disappears instantly on your screen, but what reaches the server is not sped up.";
        };
        List<FormattedCharSequence> lines = CrystalUi.wrap(this.font, detail, Math.min(220, this.width - 30));
        HubDraw.isolate(graphics);
        CrystalUi.tooltip(graphics, this.font, lines, mouseX, mouseY, this.width, this.height);
    }

    private void drawPanelIcons(GuiGraphicsExtractor graphics, long now, CrystalAppearance look) {
        HubSkin skin = HubSkin.current();
        if (this.leftPanel.shown() && this.leftPanel.openProgress(now) > 0.5F) {
            Rect icon = this.leftPanel.iconRect();
            this.colorsIcon.draw(graphics, icon.x() - 2, icon.y() - 2, icon.width() + 4, MiniCrystal.rainbow(now, false, skin.halo), now, 0.3F, 1.5F);
        }
        if (this.rightPanel.shown() && this.rightPanel.openProgress(now) > 0.5F) {
            Rect icon = this.rightPanel.iconRect();
            CrystalAppearance glowing = MiniCrystal.rainbow(now, true, look.haloColor());
            glowing.outerColor = look.haloColor();
            glowing.innerColor = look.haloColor();
            glowing.coreColor = -1;
            glowing.glowPowerPercent = Math.round(70 + 50 * HubMotion.breathe(now / 1_000_000_000.0D, 1.4D));
            this.glowIcon.draw(graphics, icon.x() - 2, icon.y() - 2, icon.width() + 4, glowing, now, 0.3F, 1.5F);
        }
    }

    private void drawDrawer(GuiGraphicsExtractor graphics, Rect drawer, long now, float frame, int mouseX, int mouseY, float alpha,
            CrystalAppearance look) {
        if (drawer.isEmpty() || alpha <= 0.02F) {
            return;
        }
        HubSkin skin = HubSkin.current();
        float open = HubMotion.easeOutCubic(this.drawerProgress);
        boolean full = drawerHidesCrystal();
        if (!full) {
            HubDraw.glass(graphics, drawer.x(), drawer.y(), drawer.width(), drawer.height(), skin.panelTop, skin.panelBottom,
                    skin.border, alpha * Math.min(1.0F, open * 1.4F));
        }
        if (this.drawerList != null) {
            boolean pointer = this.phase == Phase.MAIN && !this.overlays.blocking();
            this.drawerList.render(graphics, this.font, now, alpha * open, skin.accent, skin.muted, skin.border,
                    pointer ? mouseX : -10_000, pointer ? mouseY : -10_000, 0.0F);
        } else if (this.drawerId.equals("kohs")) {
            graphics.enableScissor(drawer.x(), drawer.y(), drawer.right(), drawer.bottom());
            this.overlays.renderKohs(graphics, this.font, drawer, mouseX, mouseY, alpha * open, frame);
            graphics.disableScissor();
        } else if (this.drawerId.equals("soon")) {
            graphics.enableScissor(drawer.x(), drawer.y(), drawer.right(), drawer.bottom());
            this.overlays.renderComingSoon(graphics, this.font, drawer, now, mouseX, mouseY, look, alpha * open);
            graphics.disableScissor();
        }
    }

    /**
     * Crystal Practice, under the crystal: obsidian tiles sliding along a dark strip, a sword, and
     * small crystals bobbing at either end. A click opens the practice warning.
     */
    private void drawPractice(GuiGraphicsExtractor graphics, long now, float frame, int mouseX, int mouseY, float alpha, HubSkin skin) {
        Rect rect = this.layout.practice;
        if (alpha <= 0.02F || rect.isEmpty()) {
            return;
        }
        float rise = this.phase == Phase.SETTLE ? HubMotion.easeOutCubic(HubMotion.progress(now - this.phaseAt - 480_000_000L, 380_000_000L)) : 1.0F;
        if (rise <= 0.02F) {
            return;
        }
        int offset = Math.round((1.0F - rise) * 10.0F);
        Rect box = new Rect(rect.x(), rect.y() + offset, rect.width(), rect.height());
        boolean hovered = this.phase == Phase.MAIN && !this.overlays.blocking() && rect.contains(mouseX, mouseY);
        this.practiceHover = HubMotion.damp(this.practiceHover, hovered ? 1.0F : 0.0F, frame, 60.0F);
        float fade = alpha * rise;
        double seconds = now / 1_000_000_000.0D;
        if (this.practiceHover > 0.05F) {
            HubDraw.halo(graphics, box.x(), box.y(), box.width(), box.height(), skin.hot, 4, this.practiceHover * 0.6F * fade);
        }
        HubDraw.glass(graphics, box.x(), box.y(), box.width(), box.height(), CrystalTheme.lerp(0xE0200818, 0xF0381028, this.practiceHover),
                0xE0100410, CrystalTheme.lerp(skin.hot, skin.accentBright, this.practiceHover * 0.6F), fade);
        // Obsidian tiles sliding along the strip.
        graphics.enableScissor(box.x() + 1, box.y() + 1, box.right() - 1, box.bottom() - 1);
        int tile = Math.max(8, box.height() - 4);
        int slide = (int) Math.floor((seconds * (8.0D + 18.0D * this.practiceHover)) % tile);
        for (int x = box.x() - tile + slide; x < box.right(); x += tile) {
            HubDraw.icon(graphics, HubDraw.vanilla("textures/block/obsidian.png"), x + tile / 2.0F, box.centerY(), tile,
                    0.0F, CrystalTheme.fade(0x55FFFFFF, fade));
        }
        graphics.fill(box.x() + 1, box.y() + 1, box.right() - 1, box.bottom() - 1, CrystalTheme.fade(0x8C12040E, fade));
        graphics.disableScissor();
        String label = this.spanish ? "PRÁCTICA DE CRISTALES" : "CRYSTAL PRACTICE";
        int iconSize = Math.min(12, box.height() - 4);
        label = HubDraw.fit(this.font, label, box.width() - iconSize - 34);
        int group = iconSize + 4 + this.font.width(label);
        int x = box.centerX() - group / 2;
        float swing = (float) Math.sin(seconds * 3.0D) * 0.15F * (0.3F + this.practiceHover);
        HubDraw.icon(graphics, HubDraw.vanilla("textures/item/netherite_sword.png"), x + iconSize / 2.0F, box.centerY(), iconSize,
                swing, CrystalTheme.fade(0xFFFFFFFF, fade));
        CrystalUi.label(graphics, this.font, label, x + iconSize + 4, box.centerY() - 4,
                CrystalTheme.fade(CrystalTheme.lerp(0xFFFFD5E0, 0xFFFFFFFF, this.practiceHover), fade), true);
        for (int side = 0; side < 2; side++) {
            float bob = (float) Math.sin(seconds * 2.4D + side * 1.7D) * 1.0F;
            int crystalX = side == 0 ? box.x() + 9 : box.right() - 9;
            HubDraw.icon(graphics, HubDraw.vanilla("textures/item/end_crystal.png"), crystalX, box.centerY() + bob,
                    Math.min(10, box.height() - 6), 0.0F, CrystalTheme.fade(0xFFFFFFFF, fade));
        }
    }

    private void drawHandles(GuiGraphicsExtractor graphics, long now, float frame, int mouseX, int mouseY, float alpha, HubSkin skin) {
        boolean hovered = this.layout.leftHandle.contains(mouseX, mouseY) || this.layout.rightHandle.contains(mouseX, mouseY);
        this.handleHover = HubMotion.damp(this.handleHover, hovered ? 1.0F : 0.0F, frame, 60.0F);
        for (int side = 0; side < 2; side++) {
            Rect handle = side == 0 ? this.layout.leftHandle : this.layout.rightHandle;
            boolean open = side == 0 ? this.narrow == Narrow.LEFT : this.narrow == Narrow.RIGHT;
            if (open) {
                continue;
            }
            HubDraw.glass(graphics, handle.x(), handle.y(), handle.width(), handle.height(), skin.sideTop, skin.sideBottom,
                    CrystalTheme.lerp(skin.sideBorder, skin.accentBright, this.handleHover), alpha);
            HubDraw.chevron(graphics, handle.centerX() - 2, handle.centerY() - 3, side == 0, CrystalTheme.fade(skin.accentBright, alpha));
        }
    }

    private void drawHandleTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (this.layout.wide || this.overlays.blocking()) {
            return;
        }
        String text = null;
        if (this.layout.leftHandle.contains(mouseX, mouseY) && this.narrow != Narrow.LEFT) {
            text = this.spanish ? "Colores" : "Colours";
        } else if (this.layout.rightHandle.contains(mouseX, mouseY) && this.narrow != Narrow.RIGHT) {
            text = this.spanish ? "Brillo" : "Glow";
        }
        if (text != null) {
            CrystalUi.tooltip(graphics, this.font, CrystalUi.wrap(this.font, text, 120), mouseX, mouseY, this.width, this.height);
        }
    }

    private void drawFooter(GuiGraphicsExtractor graphics, float alpha, HubSkin skin) {
        if (this.versionLabel.isEmpty() || alpha <= 0.02F) {
            return;
        }
        Rect footer = this.layout.footer;
        CrystalUi.label(graphics, this.font, this.versionLabel, footer.x() + 2, footer.y() + (footer.height() - 8) / 2,
                CrystalTheme.fade(skin.muted, alpha * 0.8F));
    }

    // ------------------------------------------------------------------------------------------
    // Input
    // ------------------------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        if (this.overlays.blocking()) {
            this.overlays.mouseClicked(mouseX, mouseY);
            return true;
        }
        if (this.phase == Phase.GATE) {
            if (this.stage.contains(mouseX, mouseY)) {
                beginEnter(System.nanoTime());
            }
            return true;
        }
        if (this.phase != Phase.MAIN) {
            return true;
        }
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }
        // Mouse buttons by the game's own numbers: 26.3 counts them from 1 (left) and 3 (right).
        boolean attack = this.minecraft.options.keyAttack.matchesMouse(event) || event.button() == InputConstants.MOUSE_BUTTON_LEFT;
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT && event.button() != InputConstants.MOUSE_BUTTON_RIGHT) {
            return false;
        }
        // The panels' reset buttons: the first click arms, the second resets.
        for (SidePanel panel : new SidePanel[] {this.leftPanel, this.rightPanel}) {
            if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && panel.fullyOpen(System.nanoTime())) {
                SidePanel.ResetClick click = panel.resetClick(mouseX, mouseY);
                if (click == SidePanel.ResetClick.RESET) {
                    resetPanel(panel);
                    return true;
                }
                if (click == SidePanel.ResetClick.ARMED) {
                    this.minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                            net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 0.8F));
                    return true;
                }
            }
        }
        // The narrow window's drawers: a handle opens one, a click elsewhere closes it.
        if (!this.layout.wide) {
            if (this.layout.leftHandle.contains(mouseX, mouseY) && this.narrow != Narrow.LEFT) {
                openNarrow(Narrow.LEFT);
                return true;
            }
            if (this.layout.rightHandle.contains(mouseX, mouseY) && this.narrow != Narrow.RIGHT) {
                openNarrow(Narrow.RIGHT);
                return true;
            }
            if (this.narrow != Narrow.NONE && !(this.narrow == Narrow.LEFT ? this.leftPanel : this.rightPanel).contains(mouseX, mouseY)) {
                openNarrow(Narrow.NONE);
                return true;
            }
        }
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            TabCarousel tabs = currentTabs();
            String changed = tabs.mouseClicked(this.font, mouseX, mouseY);
            if (changed != null) {
                tabSelected(changed);
                return true;
            }
            if (this.layout.practice.contains(mouseX, mouseY)) {
                this.minecraft.setScreen(new PracticeWarningScreen(this));
                return true;
            }
            Rect drawer = currentDrawerRect();
            if (drawer.contains(mouseX, mouseY)) {
                if (this.drawerId.equals("kohs") && this.overlays.kohsClicked(this.font, drawer, mouseX, mouseY)) {
                    return true;
                }
                if (this.drawerId.equals("soon") && this.overlays.comingSoonClicked(mouseX, mouseY)) {
                    return true;
                }
            }
        }
        if (this.stage.contains(mouseX, mouseY)) {
            this.stage.click(System.nanoTime(), attack, visuals().copy());
            return true;
        }
        return false;
    }

    private void openNarrow(Narrow side) {
        this.narrow = side;
        this.leftPanel.hide();
        this.rightPanel.hide();
        long now = System.nanoTime();
        if (side == Narrow.LEFT) {
            this.leftPanel.unfold(now);
        } else if (side == Narrow.RIGHT) {
            this.rightPanel.unfold(now);
        }
    }

    private void tabSelected(String id) {
        if (!CrystalVisualConfig.carouselHintDone() && currentTabs().turnedByPlayer()) {
            CrystalVisualConfig.setCarouselHintDone(true);
            commit();
        }
        if (!id.equals(this.drawerId)) {
            rebuildDrawer();
            this.overlays.drawerOpened(id);
        }
        if (id.equals("converter") && currentTabs().open() && !converterNoticeShownThisSession
                && !CrystalVisualConfig.converterNoticeDismissed()) {
            converterNoticeShownThisSession = true;
            this.converterNoticeOpen = true;
            this.overlays.showNotice("Converter My Crystal", this.spanish
                    ? "Converter My Crystal dibuja el bloque, el ítem o la entidad que elijas en lugar del End Crystal. Mientras esté activado, tu elección tiene prioridad sobre la textura original de Minecraft y sobre la de cualquier resource pack instalado: en tus cristales no verás ninguna de las dos. El giro y la flotación se mantienen, y se ajustan en sus propias secciones."
                    : "Converter My Crystal draws the block, item or entity you choose in the End Crystal's place. While it is on, your choice takes priority over Minecraft's original texture and over any installed resource pack's: you will see neither on your crystals. The spin and the float stay, and are set in their own sections.",
                    this.spanish
                            ? "Solo cambia el dibujo en tu pantalla. El cristal sigue donde está, con la misma zona de golpe."
                            : "Only the drawing on your screen changes. The crystal stays where it is, with the same box to hit.");
        }
        if (id.equals("tweaks") && currentTabs().open() && !popupShownThisSession && !CrystalVisualConfig.optimizerPopupDismissed()) {
            popupShownThisSession = true;
            this.overlays.showOptimizerPopup();
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (this.overlays.blocking()) {
            this.overlays.mouseScrolled(verticalAmount, this.font.lineHeight + 1);
            return true;
        }
        if (this.phase != Phase.MAIN) {
            return true;
        }
        double amount = verticalAmount != 0.0D ? verticalAmount : -horizontalAmount;
        TabCarousel tabs = currentTabs();
        String before = tabs.selectedItem().id();
        if (tabs.mouseScrolled(mouseX, mouseY, amount)) {
            if (!tabs.selectedItem().id().equals(before)) {
                tabSelected(tabs.selectedItem().id());
            } else if (!CrystalVisualConfig.carouselHintDone()) {
                CrystalVisualConfig.setCarouselHintDone(true);
                commit();
            }
            return true;
        }
        int step = Math.max(10, this.layout.controlHeight + this.layout.rowGap) * 2;
        for (SidePanel panel : new SidePanel[] {this.leftPanel, this.rightPanel}) {
            if (panel.mouseScrolled(mouseX, mouseY, verticalAmount, step)) {
                if (!CrystalVisualConfig.scrollHintDone() && panel.scrolledByPlayer()) {
                    CrystalVisualConfig.setScrollHintDone(true);
                    commit();
                }
                return true;
            }
        }
        if (this.converterGrid != null && this.converterGrid.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)) {
            return true;
        }
        if (this.drawerList != null && this.drawerList.mouseScrolled(mouseX, mouseY, verticalAmount, step)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (this.overlays.blocking()) {
            if (event.isEscape() || event.isConfirmation()) {
                this.overlays.closeTopmost();
            }
            return true;
        }
        if (this.phase == Phase.GATE && event.isConfirmation()) {
            // Enter or Space opens the gate as the click does.
            beginEnter(System.nanoTime());
            return true;
        }
        if (event.isEscape() && !this.layout.wide && this.narrow != Narrow.NONE) {
            // Escape folds an open side panel away first, as a click beside it does, and keeps the menu.
            openNarrow(Narrow.NONE);
            return true;
        }
        boolean typing = (this.layerHex != null && this.layerHex.isFocused()) || (this.glowHex != null && this.glowHex.isFocused())
                || (this.converterSearch != null && this.converterSearch.isFocused());
        if (!typing && this.phase == Phase.MAIN && this.minecraft != null) {
            if (this.minecraft.options.keyAttack.matches(event)) {
                this.stage.click(System.nanoTime(), true, visuals().copy());
                return true;
            }
            if (this.minecraft.options.keyUse.matches(event)) {
                this.stage.click(System.nanoTime(), false, visuals().copy());
                return true;
            }
        }
        return super.keyPressed(event);
    }
}
