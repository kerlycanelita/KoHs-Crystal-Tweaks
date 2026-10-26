package com.zymekoh.crystaltweaks.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.zymekoh.crystaltweaks.client.benchmark.CrystalBenchmarkScreen;
import com.zymekoh.crystaltweaks.client.compat.HerziumBridge;
import com.zymekoh.crystaltweaks.client.hub.ConverterGrid;
import com.zymekoh.crystaltweaks.client.hub.CrystalHubLayout;
import com.zymekoh.crystaltweaks.client.hub.CrystalStage;
import com.zymekoh.crystaltweaks.client.hub.GrimoireDraw;
import com.zymekoh.crystaltweaks.client.hub.GrimoireFonts;
import com.zymekoh.crystaltweaks.client.hub.GrimoireIcons;
import com.zymekoh.crystaltweaks.client.hub.HubButton;
import com.zymekoh.crystaltweaks.client.hub.HubColorPicker;
import com.zymekoh.crystaltweaks.client.hub.HubDraw;
import com.zymekoh.crystaltweaks.client.hub.HubEditBox;
import com.zymekoh.crystaltweaks.client.hub.HubIcons;
import com.zymekoh.crystaltweaks.client.hub.HubLayout.Rect;
import com.zymekoh.crystaltweaks.client.hub.HubMotion;
import com.zymekoh.crystaltweaks.client.hub.HubOverlays;
import com.zymekoh.crystaltweaks.client.hub.HubSkin;
import com.zymekoh.crystaltweaks.client.hub.HubSlider;
import com.zymekoh.crystaltweaks.client.hub.HubWindow;
import com.zymekoh.crystaltweaks.client.hub.OptionFx;
import com.zymekoh.crystaltweaks.client.hub.RowList;
import com.zymekoh.crystaltweaks.client.hub.SectionRail;
import com.zymekoh.crystaltweaks.client.hub.SidePanel;
import com.zymekoh.crystaltweaks.client.practice.PracticeHost;
import com.zymekoh.crystaltweaks.client.practice.PracticeWarningScreen;
import com.zymekoh.crystaltweaks.client.sound.CrystalSoundManager;
import com.zymekoh.crystaltweaks.core.CrystalBreakPrediction;
import com.zymekoh.crystaltweaks.core.CrystalCore;
import com.zymekoh.crystaltweaks.core.FastPlacement;
import com.zymekoh.crystaltweaks.core.ObsidianDebounce;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

/**
 * The Crystal Tweaks settings: a grimoire of three pages.
 *
 * <p>The left page lists the sections, each with its icon and its name in gothic letters, with
 * Crystal Practice and KoHs at its foot. The middle page is your crystal, floating over a turning
 * sigil in the colours, glow, flash and sound you choose, so every change shows on it at once; under
 * it, the glow it wears and a button to try its flash. The right page is the open section: its title,
 * what it is, and its rows. The book draws with its own letters and its own icons, read from the mod
 * and the game's built-in pack, so no resource pack changes them; only the crystal follows the packs,
 * as a crystal in the world does. What the option under the pointer does shows under the crystal.
 * Narrower windows fold the list into a strip of icons, then leave the crystal out.</p>
 *
 * <p>Geometry comes from {@link CrystalHubLayout}, shared by drawing and input. Controls are placed
 * every frame where they are drawn; while anything covers them they get no pointer.</p>
 */
public final class CrystalTweaksScreen extends Screen {
    private enum Phase { SETTLE, MAIN, TRANSITION }

    private enum Layer { OUTER, INNER, CORE }

    private static final long SETTLE_NANOS = 950_000_000L;
    private static final long GLOW_LIGHT_NANOS = 1_600_000_000L;
    private static final long FLASH_PREVIEW_DELAY_NANOS = 320_000_000L;
    private static final long SOUND_PREVIEW_DELAY_NANOS = 380_000_000L;
    private static boolean settledThisSession;
    private static boolean converterNoticeShownThisSession;
    /** The section open when the settings last closed, this game session. */
    private static String lastOpened = "colours";

    private final Screen parent;
    private final boolean spanish;
    private final String versionLabel;
    private final CrystalStage stage = new CrystalStage();
    private final OptionFx fx = new OptionFx();
    private final HubOverlays overlays;
    /** The open section's page. It lies right of the crystal, so it opens from its left edge, as a right-hand panel does. */
    private final SidePanel page = new SidePanel(SidePanel.Side.RIGHT).asPage();
    /** What each option control does, in full: the bubble under the crystal shows it. */
    private final Map<AbstractWidget, String> details = new IdentityHashMap<>();
    private CrystalHubLayout layout;
    /** The sections: a list on the left page, or a strip of icons in a narrow window. */
    private SectionRail sections;
    private boolean kohsPage;
    private HubWindow window;
    private boolean enemy;
    private Phase phase = Phase.MAIN;
    private long phaseAt;
    private boolean initialized;
    private long lastFrame;
    private long glowTouchedAt = Long.MIN_VALUE / 2;
    private long flashPreviewAt;
    private long soundPreviewAt;
    private Layer layer = Layer.OUTER;
    private boolean updatingControls;
    private boolean saved;
    private String soundStatus = "";
    private String converterQuery = "";
    private boolean converterEntities;
    /** The client-side crystal's live numbers, as text, and what they were made from. */
    private String coreStatus;
    private String coreStatusLine;
    private long coreStatusKey;
    private boolean converterNoticeOpen;
    private float practiceHover;
    private float captionShown;
    private String captionKey = "";
    private List<FormattedCharSequence> captionLines = List.of();

    private HubButton switchButton;
    private HubButton kohsButton;
    private HubButton doneButton;
    /** What the crystal wears, under it, as text, and what it was made from. */
    private long summaryKey = Long.MIN_VALUE;
    private String summaryName = "";
    private String summaryValues = "";
    private String summaryFitted = "";
    private int summaryFittedWidth = -1;
    private float summaryScale = 1.0F;
    private HubButton profileUse;
    private HubButton outerButton;
    private HubButton innerButton;
    private HubButton coreButton;
    private HubEditBox layerHex;
    private HubColorPicker layerPicker;
    private HubEditBox glowHex;
    private HubColorPicker glowPicker;
    private HubSlider blurSlider;
    private HubButton shapeButton;
    private final List<HubButton> shapeButtons = new ArrayList<>();
    private HubButton styleButton;
    private final List<HubButton> styleButtons = new ArrayList<>();
    private ConverterGrid converterGrid;
    private HubEditBox converterSearch;
    private HubButton blocksButton;
    private HubButton entitiesButton;

    public CrystalTweaksScreen(Screen parent) {
        // The settings draw with their own letters, which no resource pack changes.
        super(Minecraft.getInstance(), GrimoireFonts.font(), Component.literal("Crystal Tweaks KoHs"));
        this.parent = parent;
        this.spanish = CrystalUi.spanish();
        this.versionLabel = FabricLoader.getInstance().getModContainer("crystal_tweaks")
                .map(container -> "v" + container.getMetadata().getVersion().getFriendlyString())
                .orElse("");
        this.overlays = new HubOverlays(this, this.spanish);
        // The crystal floats over the sigil, not on a pedestal.
        this.stage.setPedestal(false);
        this.stage.setScenery(true);
    }

    /** What Mod Menu opens. */
    public static Screen open(Screen parent) {
        return new CrystalTweaksScreen(parent);
    }

    private String s(String spanishText, String englishText) {
        return this.spanish ? spanishText : englishText;
    }

    // ------------------------------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------------------------------

    @Override
    protected void init() {
        FixedGuiScale.apply(this, this.minecraft);
        CrystalVisualConfig.load();
        long now = System.nanoTime();
        boolean first = !this.initialized;
        this.initialized = true;
        this.lastFrame = now;
        this.saved = false;
        this.layout = CrystalHubLayout.fit(this.width, this.height);
        this.overlays.layout(this.font, this.width, this.height);
        // A window has no place in a resized screen: it goes with the old controls.
        this.window = null;
        clearWidgets();
        this.details.clear();
        createRails();
        this.page.setGeometry(this.layout.leftPanel, this.layout.panelViewport(this.layout.leftPanel),
                this.layout.panelTitle(this.layout.leftPanel));
        // The book's art is made now, as the settings open, not in the middle of an animation.
        GrimoireIcons.warm();
        HubIcons.warm();
        addChrome();
        rebuildPanels(!first);
        if (first) {
            if (!settledThisSession) {
                settledThisSession = true;
                startSettle(now);
            } else {
                this.phase = Phase.MAIN;
                showPanelsOpen();
                this.stage.setTarget(crystalTarget(), true);
                this.stage.appear(now);
            }
        } else {
            // A resize or a return from another screen: everything as it was, at once.
            if (this.phase == Phase.TRANSITION) {
                this.overlays.endTransition();
            }
            this.phase = Phase.MAIN;
            showPanelsOpen();
            this.stage.setTarget(crystalTarget(), true);
        }
    }

    /** The sections, in the book's order: the crystal's own first, then its light and your play. */
    private void createRails() {
        List<String> ids = new ArrayList<>(this.enemy ? List.of("colours", "converter", "glow", "flash", "soon")
                : List.of("colours", "converter", "sound", "glow", "flash", "helpers", "core", "advanced"));
        if (this.layout.practice.isEmpty() && !this.enemy) {
            // No room for Crystal Practice on the sections' page: it gets a section of its own.
            ids.add("practice");
        }
        this.sections = rail(ids, lastOpened, true);
        if (this.layout.sectionList()) {
            this.sections.asList(this.layout.listRowHeight);
        }
    }

    private SectionRail rail(List<String> ids, String open, boolean leftEdge) {
        List<SectionRail.Section> sections = new ArrayList<>();
        int selected = 0;
        for (String id : ids) {
            if (id.equals(open)) {
                selected = sections.size();
            }
            sections.add(new SectionRail.Section(id, sectionName(id), sectionIcon(id)));
        }
        return new SectionRail(sections, selected, leftEdge);
    }

    private String sectionName(String id) {
        return switch (id) {
            case "colours" -> s("Colores", "Colours");
            case "converter" -> "Converter";
            case "sound" -> s("Sonido", "Sound");
            case "glow" -> s("Brillo", "Glow");
            case "flash" -> s("Destello", "Flash");
            case "helpers" -> s("Ayudas", "Helpers");
            case "core" -> s("Cristal cliente", "Client crystal");
            case "practice" -> s("Práctica", "Practice");
            default -> s("Avanzado", "Advanced");
        };
    }

    /** A section's name at the top of its panel: the enemy's say so. */
    private String sectionTitle(String id) {
        if (!this.enemy) {
            return sectionName(id);
        }
        return switch (id) {
            case "colours" -> s("Colores ajenos", "Enemy colours");
            case "glow" -> s("Brillo ajeno", "Enemy glow");
            case "flash" -> s("Destello ajeno", "Enemy flash");
            default -> sectionName(id);
        };
    }

    /** A section's icon: the game's own sprite, redrawn in the book's colours. */
    private static HubButton.Icon sectionIcon(String id) {
        return switch (id) {
            case "colours" -> GrimoireIcons.icon(GrimoireIcons.Sprite.END_CRYSTAL, 0.0F);
            case "converter" -> GrimoireIcons.icon(GrimoireIcons.Sprite.ENDER_EYE, 1.5F);
            case "sound" -> GrimoireIcons.icon(GrimoireIcons.Sprite.NOTE_BLOCK, 0.9F);
            case "glow" -> GrimoireIcons.icon(GrimoireIcons.Sprite.GLOWSTONE, 0.3F);
            case "flash" -> GrimoireIcons.icon(GrimoireIcons.Sprite.NETHER_STAR, 0.6F);
            case "helpers" -> GrimoireIcons.icon(GrimoireIcons.Sprite.OBSIDIAN, 1.2F);
            case "core" -> GrimoireIcons.icon(GrimoireIcons.Sprite.AMETHYST_SHARD, 0.7F);
            case "practice" -> GrimoireIcons.icon(GrimoireIcons.Sprite.NETHERITE_SWORD, 1.8F);
            case "soon" -> GrimoireIcons.icon(GrimoireIcons.Sprite.TOTEM, 0.4F);
            default -> GrimoireIcons.icon(GrimoireIcons.Sprite.COMPARATOR, 2.1F);
        };
    }

    /** What a section is, in a line under its title. */
    private String sectionDescription(String id) {
        return switch (id) {
            case "colours" -> s("El tinte de cada capa del cristal y su movimiento.", "The tint of each layer of the crystal, and its motion.");
            case "converter" -> s("Otro bloque, ítem o entidad en lugar del cristal.", "Another block, item or entity in the crystal's place.");
            case "sound" -> s("Cómo suenan tus cristales al estallar.", "What your crystals sound like when they burst.");
            case "glow" -> s("La luz del cristal y la que deja a su alrededor.", "The crystal's light, and the light it casts around it.");
            case "flash" -> s("El destello que deja el cristal al estallar.", "The flash a crystal leaves when it bursts.");
            case "helpers" -> s("Ayudas legales para colocar y entender cada pelea.", "Fair helpers to place crystals and read every fight.");
            case "core" -> s("Lo que tu cliente muestra antes de que el servidor responda.", "What your client shows before the server answers.");
            case "practice" -> s("Practica contra un bot en un mundo propio.", "Practise against a bot in a world of your own.");
            case "soon" -> "";
            default -> s("Ajustes finos e integraciones.", "Fine settings and integrations.");
        };
    }

    private CrystalAppearance visuals() {
        return CrystalVisualConfig.visuals(this.enemy);
    }

    private void startSettle(long now) {
        this.phase = Phase.SETTLE;
        this.phaseAt = now;
        Rect target = crystalTarget();
        // From a point in the middle of the stage, growing into place, then the page turns in from it.
        this.stage.setTarget(new Rect(target.centerX(), target.centerY(), 0, 0), true);
        this.stage.setTarget(target, false);
        this.stage.appear(now);
        this.page.unfold(now + 230_000_000L);
        play(SoundEvents.AMETHYST_BLOCK_RESONATE, 0.8F, 0.5F);
    }

    private void skipSettle(long now) {
        if (this.phase != Phase.SETTLE) {
            return;
        }
        this.phase = Phase.MAIN;
        showPanelsOpen();
        this.stage.setTarget(crystalTarget(), true);
        this.stage.appear(now - 1_000_000_000L);
    }

    private void showPanelsOpen() {
        if (this.kohsPage) {
            this.page.hide();
        } else {
            this.page.showOpen();
        }
    }

    @Override
    public void onClose() {
        if (this.overlays.closeTopmost()) {
            return;
        }
        if (this.window != null) {
            closeWindow();
            return;
        }
        if (this.kohsPage) {
            toggleKohs();
            return;
        }
        saveSettings();
        this.minecraft.setScreen(this.parent);
    }

    @Override
    public void removed() {
        saveSettings();
        FixedGuiScale.restore(this.minecraft);
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

    private void play(SoundEvent sound, float pitch, float volume) {
        this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
    }

    // ------------------------------------------------------------------------------------------
    // Called back by the overlays
    // ------------------------------------------------------------------------------------------

    public void popupClosed(boolean dismissForever) {
        if (dismissForever && this.converterNoticeOpen) {
            CrystalVisualConfig.setConverterNoticeDismissed(true);
            CrystalVisualConfig.save();
        }
        this.converterNoticeOpen = false;
    }

    /** The shards have flown and the new screen has landed: its controls come up. */
    public void transitionLanded() {
        long now = System.nanoTime();
        rebuildPanels(false);
        this.page.unfold(now - 300_000_000L);
        this.stage.setTarget(crystalTarget(), true);
        this.stage.appear(now);
    }

    public void transitionFinished() {
        this.phase = Phase.MAIN;
    }

    // ------------------------------------------------------------------------------------------
    // The fixed controls: the enemy switch, KoHs and Done
    // ------------------------------------------------------------------------------------------

    private void addChrome() {
        Rect switchRect = this.layout.enemySwitch;
        boolean repair = CrystalVisualConfig.ENEMY_PROFILE_UNDER_REPAIR && !this.enemy;
        String label = this.enemy ? s("Tus cristales", "Your crystals")
                : repair ? s("Ajenos: reparando", "Enemy: under repair") : s("Cristales ajenos", "Enemy crystals");
        if (this.font.width(label) > switchRect.width() - 26) {
            // A narrow header keeps a word; the tooltip says the rest.
            label = this.enemy ? s("Tuyos", "Yours") : s("Ajenos", "Enemy");
        }
        this.switchButton = addRenderableWidget(new HubButton(switchRect.x(), switchRect.y(), switchRect.width(), switchRect.height(),
                Component.literal(label), ignored -> switchMode())
                .icon(GrimoireIcons.icon(GrimoireIcons.Sprite.END_CRYSTAL, 0.9F))
                .style(this.enemy ? HubButton.Style.PRIMARY : HubButton.Style.DANGER));
        // Dimmed and out of reach while it is being repaired.
        this.switchButton.active = !repair;
        tooltip(this.switchButton, this.enemy
                ? s("Vuelve a los colores y el brillo de tus propios cristales.", "Back to the colours and glow of your own crystals.")
                : repair ? s("En reparación. Por ahora todos los cristales, también los ajenos, se ven con tu propio perfil.",
                        "Under repair. For now every crystal, the enemy's too, is drawn with your own profile.")
                : s("Colores y brillo de los cristales que no colocaste tú. Identificación aproximada: se relacionan tus intentos de colocación con las apariciones; el servidor no envía el propietario.",
                        "Colours and glow of the crystals you did not place. Approximate attribution: your placement attempts are matched to spawns; the server does not send the owner."));
        Rect kohs = this.layout.kohsButton;
        this.kohsButton = addRenderableWidget(new HubButton(kohs.x(), kohs.y(), kohs.width(), kohs.height(),
                Component.literal(kohsLabel()), ignored -> toggleKohs()).icon(HubIcons.kohs(2.4F)));
        Rect done = this.layout.done;
        this.doneButton = addRenderableWidget(new HubButton(done.x(), done.y(), done.width(), done.height(),
                Component.literal(s("Listo", "Done")), ignored -> onClose()).style(HubButton.Style.PRIMARY));
        placeChrome();
    }

    /**
     * Back and Done: at the foot of the book's pages, or under the KoHs window while it is open,
     * which takes the whole book and leaves nothing of the settings around it.
     */
    private void placeChrome() {
        Rect back = this.kohsPage ? this.layout.kohsBack : this.layout.kohsButton;
        place(this.kohsButton, back);
        place(this.doneButton, this.kohsPage ? this.layout.kohsDone : this.layout.done);
        this.kohsButton.setMessage(Component.literal(kohsLabel()));
        tooltip(this.kohsButton, this.kohsPage ? s("Vuelve a los ajustes.", "Back to the settings.")
                : s("Quién hace Crystal Tweaks y dónde encontrarla.", "Who makes Crystal Tweaks and where to find her."));
    }

    private static void place(HubButton button, Rect where) {
        button.setX(where.x());
        button.setY(where.y());
        button.setWidth(where.width());
        button.setHeight(where.height());
    }

    private String kohsLabel() {
        if (this.kohsPage) {
            return this.font.width(s("Volver", "Back")) + 8 <= this.layout.kohsBack.width() ? s("Volver", "Back") : "";
        }
        return this.font.width("KoHs") + 8 <= this.layout.kohsButton.width() ? "KoHs" : "";
    }

    /** The KoHs page takes the whole body; the same button, or Escape, brings the settings back. */
    private void toggleKohs() {
        long now = System.nanoTime();
        closeWindowNow();
        this.kohsPage = !this.kohsPage;
        placeChrome();
        if (this.kohsPage) {
            this.overlays.drawerOpened("kohs");
            this.page.hide();
            this.stage.setTarget(new Rect(crystalTarget().centerX(), crystalTarget().centerY(), 0, 0), false);
        } else {
            this.page.unfold(now);
            this.stage.setTarget(crystalTarget(), true);
            this.stage.appear(now);
        }
    }

    private void switchMode() {
        if (this.phase != Phase.MAIN || (CrystalVisualConfig.ENEMY_PROFILE_UNDER_REPAIR && !this.enemy)) {
            return;
        }
        long now = System.nanoTime();
        closeWindowNow();
        if (this.kohsPage) {
            toggleKohs();
        }
        CrystalAppearance before = visuals().copy();
        HubSkin skin = HubSkin.current();
        // The skin is one shared object: what the old screen looked like is read out before it changes.
        int oldTop = skin.sideTop;
        int oldBottom = skin.sideBottom;
        int oldBorder = skin.sideBorder;
        int oldHalo = skin.halo;
        float oldInfluence = skin.influence;
        List<Rect> parts = new ArrayList<>();
        parts.add(this.layout.leftWing);
        parts.add(this.layout.rightWing);
        this.stage.explode(now, before, true);
        this.enemy = !this.enemy;
        createRails();
        // The new mode's colours, for the parts that fall in.
        HubSkin.current().update(this.enemy, visuals().haloColor(), 0.15F);
        HubSkin next = HubSkin.current();
        Rect switchRect = this.layout.enemySwitch;
        this.overlays.startTransition(now, parts, switchRect.centerX(), switchRect.centerY(), oldTop, oldBottom, oldBorder, oldHalo,
                parts, next.sideTop, next.sideBottom, next.sideBorder, next.accent);
        HubSkin.current().update(!this.enemy, before.haloColor(), oldInfluence);
        this.phase = Phase.TRANSITION;
        this.phaseAt = now;
        // The old controls go before the first crack, the new ones come with the landing.
        clearWidgets();
        this.details.clear();
        this.page.hide();
        addChrome();
        this.switchButton.visible = false;
        CrystalVisualConfig.save();
    }

    // ------------------------------------------------------------------------------------------
    // The page
    // ------------------------------------------------------------------------------------------

    private void rebuildPanels(boolean keepScroll) {
        showSection(this.page, this.sections.selectedSection().id(), keepScroll);
    }

    private void dropList(SidePanel panel) {
        if (panel.list() != null) {
            for (AbstractWidget widget : panel.list().widgets()) {
                removeWidget(widget);
                this.details.remove(widget);
            }
        }
    }

    /** Fills {@code panel} with section {@code id}'s rows, from the settings as they are now. */
    private void showSection(SidePanel panel, String id, boolean keepScroll) {
        dropList(panel);
        setFocused(null);
        int width = Math.max(40, this.layout.panelViewport(this.layout.leftPanel).width());
        RowList list = switch (id) {
            case "colours" -> buildColours(width);
            case "converter" -> buildConverter(width);
            case "sound" -> buildSound(width);
            case "glow" -> buildGlow(width);
            case "flash" -> buildFlash(width);
            case "helpers" -> buildHelpers(width);
            case "core" -> buildCore(width);
            case "practice" -> buildPractice(width);
            case "soon" -> buildSoon(width);
            default -> buildAdvanced(width);
        };
        if (this.enemy) {
            dimUnusedProfile(list, this.profileUse);
        }
        for (AbstractWidget widget : list.widgets()) {
            widget.visible = false;
            addWidget(widget);
        }
        panel.setList(list, keepScroll);
        panel.setLabel(sectionTitle(id));
        panel.setDescription(sectionDescription(id));
        panel.setIcon(sectionIcon(id));
        if (id.equals("colours")) {
            selectLayer(this.layer);
            if (visuals().converted()) {
                dimLayers();
            }
        }
    }

    /** The page, if it shows {@code id} now; null if it shows another section. */
    private SidePanel panelShowing(String id) {
        return this.sections.selectedSection().id().equals(id) ? this.page : null;
    }

    private String sectionOf(SidePanel panel) {
        return this.sections.selectedSection().id();
    }

    /** Builds again whichever panel shows {@code id}, keeping its scroll. */
    private void refreshSection(String id) {
        SidePanel panel = panelShowing(id);
        if (panel != null) {
            showSection(panel, id, true);
        }
    }

    private void openSection(SectionRail rail, int index) {
        if (index < 0 || index == rail.selected()) {
            return;
        }
        long now = System.nanoTime();
        closeWindowNow();
        rail.select(index);
        String id = rail.selectedSection().id();
        lastOpened = id;
        showSection(this.page, id, false);
        // The rows come in once the shards are on their way, from the crystal's side.
        this.page.list().reveal(now + 120_000_000L, -1);
        if (id.equals("soon")) {
            this.overlays.drawerOpened("soon");
        }
        play(SoundEvents.AMETHYST_BLOCK_BREAK, 1.5F, 0.35F);
        if (id.equals("converter") && !converterNoticeShownThisSession && !CrystalVisualConfig.converterNoticeDismissed()) {
            converterNoticeShownThisSession = true;
            this.converterNoticeOpen = true;
            this.overlays.showNotice("Converter My Crystal", s(
                    "Converter My Crystal dibuja el bloque, el ítem o la entidad que elijas en lugar del End Crystal. Mientras esté activado, tu elección tiene prioridad sobre la textura original de Minecraft y sobre la de cualquier resource pack instalado: en tus cristales no verás ninguna de las dos. El giro y la flotación se mantienen, y se ajustan en sus propias secciones.",
                    "Converter My Crystal draws the block, item or entity you choose in the End Crystal's place. While it is on, your choice takes priority over Minecraft's original texture and over any installed resource pack's: you will see neither on your crystals. The spin and the float stay, and are set in their own sections."),
                    s("Solo cambia el dibujo en tu pantalla. El cristal sigue donde está, con la misma zona de golpe.",
                            "Only the drawing on your screen changes. The crystal stays where it is, with the same box to hit."));
        }
    }

    private boolean resettable(String id) {
        if (this.enemy && !CrystalVisualConfig.enemyCustomEnabled()) {
            return false;
        }
        return switch (id) {
            case "colours", "glow", "flash", "sound", "helpers", "core", "converter", "advanced" -> true;
            default -> false;
        };
    }

    private void resetSection(SidePanel panel) {
        String id = sectionOf(panel);
        if (!resettable(id)) {
            return;
        }
        CrystalAppearance defaults = CrystalVisualConfig.defaults(this.enemy);
        CrystalAppearance look = visuals();
        switch (id) {
            case "colours" -> {
                // Back to the shipped look: purple for your crystals, the plain texture for the enemy's.
                look.outerColor = defaults.outerColor;
                look.innerColor = defaults.innerColor;
                look.coreColor = defaults.coreColor;
                look.rotationSpeedPercent = 100;
                look.floatingSpeedPercent = 100;
                look.sizePercent = 100;
            }
            case "glow" -> {
                look.glowEnabled = defaults.glowEnabled;
                look.glowPowerPercent = defaults.glowPowerPercent;
                look.coreGlowPercent = defaults.coreGlowPercent;
                look.glowReflectionsPercent = defaults.glowReflectionsPercent;
                look.motionBlurPercent = defaults.motionBlurPercent;
                look.customGlowColor = defaults.customGlowColor;
                look.glowColor = defaults.glowColor;
                if (!this.enemy) {
                    CrystalVisualConfig.setGlowStyle(CrystalGlowStyle.LIGHT);
                    CrystalGlowStyle.resetSettings();
                    CrystalVisualConfig.setGlowQuality(CrystalGlowQuality.BALANCED);
                }
                glowTouched();
            }
            case "flash" -> {
                look.flashEnabled = defaults.flashEnabled;
                look.flashScalePercent = defaults.flashScalePercent;
                look.flashOpacityPercent = defaults.flashOpacityPercent;
                look.flashDurationMillis = defaults.flashDurationMillis;
                if (!this.enemy) {
                    CrystalVisualConfig.setFlashStyle(CrystalFlashStyle.CROWN);
                    CrystalFlashStyle.resetSettings();
                }
            }
            case "sound" -> {
                CrystalVisualConfig.setCustomSoundEnabled(false);
                CrystalVisualConfig.setSoundVolume(1.0F);
                CrystalVisualConfig.setSoundSpeed(1.0F);
                CrystalSoundManager.reloadFromConfig();
            }
            case "helpers" -> {
                CrystalVisualConfig.setSafeCrystal(false);
                CrystalVisualConfig.setObsidianDebounce(false);
                CrystalVisualConfig.setObsidianDebounceMillis(0);
                ObsidianDebounce.settingsChanged();
                CrystalVisualConfig.setPlacementDiagnostic(false);
                CrystalVisualConfig.setLatencyMeter(false);
                CrystalVisualConfig.setDeathRecap(false);
            }
            case "converter" -> {
                look.converterEnabled = false;
                look.converterTarget = "";
            }
            case "core" -> {
                CrystalVisualConfig.setClientCrystal(false);
                CrystalVisualConfig.setInstantExplosion(true);
                CrystalVisualConfig.setGhostCrystals(false);
                CrystalVisualConfig.setRefusedHitCue(true);
                CrystalVisualConfig.setFastPlacement(false);
            }
            default -> {
                CrystalVisualConfig.setOldGlow(false);
                CrystalVisualConfig.setHerziumIntegration(true);
                glowTouched();
            }
        }
        commit();
        rebuildPanels(true);
        play(SoundEvents.AMETHYST_BLOCK_CHIME, 1.2F, 0.6F);
    }

    // ------------------------------------------------------------------------------------------
    // Building the rows
    // ------------------------------------------------------------------------------------------

    private RowList newList(int width) {
        return new RowList(width, this.layout.gap + 1);
    }

    private static int indent(RowList list) {
        return list.width() >= 150 ? 9 : 5;
    }

    private HubButton toggle(RowList list, int indent, String label, BooleanSupplier state, HubButton.Icon icon, Runnable action) {
        list.row(this.layout.control, indent);
        HubButton button = list.full(new HubButton(0, 0, 0, 0, Component.literal(label), ignored -> action.run()).switchOf(state).lit());
        return icon == null ? button : button.icon(icon);
    }

    private HubButton button(RowList list, int indent, String label, HubButton.Icon icon, Runnable action) {
        list.row(this.layout.control, indent);
        HubButton button = list.full(new HubButton(0, 0, 0, 0, Component.literal(label), ignored -> action.run()).lit());
        return icon == null ? button : button.icon(icon);
    }

    /** A button that names its current value and moves to the next one on a click. */
    private HubButton choice(RowList list, int indent, Supplier<String> label, Runnable action) {
        list.row(this.layout.control, indent);
        return list.full(new HubButton(0, 0, 0, 0, Component.literal(label.get()), self -> {
            action.run();
            self.setMessage(Component.literal(label.get()));
        }).lit());
    }

    private HubSlider slider(RowList list, int indent, HubSlider slider) {
        list.row(this.layout.control, indent);
        return list.full(slider.lit());
    }

    private static void tooltip(AbstractWidget widget, String text) {
        widget.setTooltip(Tooltip.create(Component.literal(text)));
    }

    /** What a control does, in full: in the bubble under the crystal, or as its tooltip where there is none. */
    private void tip(AbstractWidget widget, String text) {
        this.details.put(widget, text);
        if (this.layout.caption.isEmpty()) {
            tooltip(widget, text);
        }
    }

    private void playFx(OptionFx.Effect effect) {
        this.fx.play(effect, visuals().haloColor());
    }

    private int pickerHeight(int width, float share, int maximum) {
        return Mth.clamp(Math.round(width * share), 36, maximum);
    }

    private RowList buildColours(int width) {
        RowList list = newList(width);
        int control = this.layout.control;
        this.profileUse = null;
        if (this.enemy) {
            list.row(control);
            this.profileUse = list.full(new HubButton(0, 0, 0, 0, Component.literal(s("Usar este perfil", "Use this profile")),
                    ignored -> profileUseChanged()).switchOf(CrystalVisualConfig::enemyCustomEnabled).style(HubButton.Style.DANGER));
            tip(this.profileUse, s("Apagado, los cristales ajenos se ven con tu propio perfil. Identificación aproximada: se relacionan tus intentos de colocación con las apariciones; el servidor no envía quién colocó cada cristal.",
                    "When off, crystals you did not place use your own profile. Approximate attribution: your placement attempts are matched to spawns; the server does not send who placed each crystal."));
        }
        list.heading(s("Capa", "Layer"), HubIcons.tinted("white_stained_glass", this::layerColor, 0.1F));
        int gap = 3;
        int third = (width - gap * 2) / 3;
        list.row(control);
        this.outerButton = list.cell(layerButton(Layer.OUTER), 0, third);
        this.innerButton = list.cell(layerButton(Layer.INNER), third + gap, third);
        this.coreButton = list.cell(layerButton(Layer.CORE), (third + gap) * 2, width - (third + gap) * 2);
        list.row(control);
        int hexWidth = Math.min(70, width / 2);
        this.layerHex = list.cell(new HubEditBox(this.font, 0, 0, hexWidth, control, Component.literal(s("Color hexadecimal", "Hex colour"))),
                0, hexWidth);
        this.layerHex.setMaxLength(7);
        this.layerHex.setResponder(this::onLayerHex);
        list.paint((graphics, font, x, y, cellWidth, height, alpha) -> drawSwatch(graphics, font, x, y, cellWidth, height, layerColor(),
                layerName(), alpha), hexWidth + 5, width - hexWidth - 5);
        list.row(pickerHeight(width, 0.6F, 100));
        this.layerPicker = list.full(new HubColorPicker(0, 0, 0, 0, Component.literal(s("Selector de color", "Colour picker")),
                layerColor(), this::onLayerPicker).lit().onRelease(this::commit));
        list.heading(s("Animación", "Animation"), HubIcons.clock(0.2F));
        slider(list, 0, HubSlider.linear(0, 0, 0, 0, 0.0D, 300.0D, visuals().rotationSpeedPercent,
                value -> visuals().rotationSpeedPercent = (int) Math.round(value),
                value -> speedLabel(s("Giro", "Rotation"), (int) Math.round(value)))).onRelease(this::commit);
        slider(list, 0, HubSlider.linear(0, 0, 0, 0, 0.0D, 300.0D, visuals().floatingSpeedPercent,
                value -> visuals().floatingSpeedPercent = (int) Math.round(value),
                value -> speedLabel(s("Flotación", "Floating"), (int) Math.round(value)))).onRelease(this::commit);
        list.heading(s("Tamaño", "Size"), HubIcons.growing(0.4F));
        HubSlider size = slider(list, 0, HubSlider.linear(0, 0, 0, 0, CrystalAppearance.MIN_SIZE, CrystalAppearance.MAX_SIZE,
                visuals().sizePercent, value -> visuals().sizePercent = (int) Math.round(value),
                value -> s("Tamaño: ", "Size: ") + Math.round(value) + "%" + (Math.round(value) == 100 ? " (Vanilla)" : "")))
                .onRelease(this::commit);
        tip(size, s("Lo grande que se dibuja el cristal, con su brillo. Solo cambia el dibujo: la zona que puedes golpear es la del servidor y sigue igual, centrada en el cristal.",
                "How large the crystal is drawn, with its glow. Only the drawing changes: the box you can hit is the server's and stays the same, centred on the crystal."));
        list.space(2);
        list.note(this.font, s("El tinte se aplica sobre la textura activa.", "The tint is applied over the active texture."), 0);
        return list;
    }

    private HubButton layerButton(Layer target) {
        String name = switch (target) {
            case OUTER -> s("Exterior", "Outer");
            case INNER -> s("Interior", "Inner");
            case CORE -> s("Núcleo", "Core");
        };
        HubButton button = new HubButton(0, 0, 0, 0, Component.literal(name), ignored -> selectLayer(target)).lit();
        tip(button, switch (target) {
            case OUTER -> s("El cubo de cristal de fuera.", "The outer glass cube.");
            case INNER -> s("El cubo de cristal de dentro.", "The inner glass cube.");
            case CORE -> s("El cubo del centro.", "The cube at the centre.");
        });
        return button;
    }

    /** Something else is drawn in the crystal's place: its layers' colours have nothing to tint. */
    private void dimLayers() {
        String note = s("No se usa mientras Converter My Crystal dibuja otra cosa en lugar del cristal: no hay capas que teñir.",
                "Not used while Converter My Crystal draws something else in the crystal's place: there are no layers to tint.");
        for (AbstractWidget widget : List.of(this.outerButton, this.innerButton, this.coreButton, this.layerHex, this.layerPicker)) {
            widget.active = false;
            tip(widget, note);
        }
        this.layerHex.setEditable(false);
        // No layer is being edited: a selected one would look like the live control it is not.
        this.outerButton.setSelected(false);
        this.innerButton.setSelected(false);
        this.coreButton.setSelected(false);
    }

    private RowList buildGlow(int width) {
        RowList list = newList(width);
        int indent = indent(list);
        CrystalAppearance look = visuals();
        HubButton glow = toggle(list, 0, s("Brillo", "Glow"), () -> visuals().glowEnabled, HubIcons.glowstone(0.3F), this::toggleGlow);
        tip(glow, s("La luz del cristal: en sus marcos, la del núcleo, sus reflejos en el suelo y la luz propia del cristal. El destello al explotar y el desenfoque tienen su propio control.",
                "The crystal's light: in its frames, the core's own, its reflections on the ground and the crystal's own light. The flash on explosion and the blur have controls of their own."));
        list.openBranch(() -> visuals().glowEnabled);
        slider(list, indent, HubSlider.linear(0, 0, 0, 0, 0, 300, look.glowPowerPercent, value -> {
            visuals().glowPowerPercent = (int) Math.round(value);
            glowTouched();
        }, value -> s("Potencia: ", "Power: ") + Math.round(value) + "%")).onRelease(this::commit);
        HubSlider core = slider(list, indent, HubSlider.linear(0, 0, 0, 0, 0, 300, look.coreGlowPercent, value -> {
            visuals().coreGlowPercent = (int) Math.round(value);
            glowTouched();
        }, value -> s("Núcleo: ", "Core: ") + Math.round(value) + "%")).onRelease(this::commit);
        tip(core, s("Cuánto brilla el núcleo, con cualquier tipo de brillo: en Luz, una luz redonda a su alrededor; en Capas, su propia luz y su aura; en Old KoHs Crystal Glow, el centro del halo. Con 0% solo brilla lo demás.",
                "How much the core glows, with every kind of glow: in Light, a round light about it; in Layers, its own light and aura; in Old KoHs Crystal Glow, the middle of the halo. At 0% only the rest glows."));
        HubSlider reflections = slider(list, indent, HubSlider.linear(0, 0, 0, 0, 0, 300, look.glowReflectionsPercent, value -> {
            visuals().glowReflectionsPercent = (int) Math.round(value);
            glowTouched();
        }, value -> s("Reflejos: ", "Reflections: ") + Math.round(value) + "%")).onRelease(this::commit);
        tip(reflections, s("Luz de color simulada sobre los bloques cercanos, del cristal y de su destello. No cambia la iluminación del mundo.",
                "Simulated coloured light on nearby block tops, from the crystal and its flash. Does not change world lighting."));
        HubButton style = button(list, indent, styleLabel(), null, this::openStyleWindow);
        this.styleButton = style;
        // The halo of Old KoHs Crystal Glow and a crystal drawn as something else have no layers to style.
        boolean halo = CrystalVisualConfig.oldGlow();
        style.active = !halo && !look.converted();
        tip(style, halo
                ? s("No se usa con Old KoHs Crystal Glow, que es un halo detrás del cristal. Apágalo en Avanzado para usarlo.",
                        "Not used by Old KoHs Crystal Glow, which is a halo behind the crystal. Turn it off in Advanced to use this.")
                : look.converted()
                ? s("No se usa mientras Converter My Crystal dibuja otra cosa en lugar del cristal: su luz es un resplandor redondo.",
                        "Not used while Converter My Crystal draws something else in the crystal's place: its light is a round glow.")
                : s("Los " + CrystalGlowStyle.values().length + " estilos del brillo: Luz, Capas y los que añaden luz propia que se mueve alrededor del cristal. Los comparten tus cristales y los ajenos.",
                        "The glow's " + CrystalGlowStyle.values().length + " styles: Light, Layers and the ones that add light of their own moving round the crystal. Shared by your crystals and the enemy's."));
        list.closeBranch();
        list.heading(s("Movimiento", "Motion"), HubIcons.item("feather", 0.6F));
        this.blurSlider = slider(list, 0, HubSlider.linear(0, 0, 0, 0, 0, 100, look.motionBlurPercent, value -> {
            visuals().motionBlurPercent = (int) Math.round(value);
            glowTouched();
        }, value -> s("Desenfoque: ", "Motion blur: ") + (Math.round(value) == 0 ? s("No", "Off") : Math.round(value) + "%")))
                .onRelease(this::commit);
        refreshBlurControl();
        list.heading(s("Calidad", "Quality"), HubIcons.item("diamond", 0.8F));
        HubButton quality = choice(list, 0, () -> s("Calidad: ", "Quality: ") + CrystalVisualConfig.glowQuality().label(this.spanish), () -> {
            CrystalVisualConfig.setGlowQuality(CrystalVisualConfig.glowQuality().next());
            commit();
            playFx(OptionFx.Effect.QUALITY);
        }).icon(HubIcons.item("amethyst_shard", 0.4F));
        tip(quality, s("Cuánto dibujan el brillo, el desenfoque y los reflejos. Rendimiento: solo el marco exterior y el núcleo, estela corta, reflejos simples y nada a lo lejos. Equilibrado: todas las capas. Calidad: también las caras de atrás, la estela más larga y los reflejos más finos. Lo comparten tus cristales y los ajenos.",
                "How much the glow, the blur and the reflections draw. Performance: the outer frame and the core only, a short trail, plain reflections and nothing far away. Balanced: every layer. Quality: the far faces too, the longest trail and the finest reflections. Shared by your crystals and the enemy's."));
        list.heading(s("Color del brillo", "Glow colour"), HubIcons.item("glow_ink_sac", 1.0F));
        HubButton own = toggle(list, 0, s("Color propio", "Own colour"), () -> visuals().customGlowColor,
                HubIcons.tinted("white_stained_glass", () -> visuals().haloColor(), 0.8F), () -> {
                    visuals().customGlowColor = !visuals().customGlowColor;
                    glowTouched();
                    commit();
                    if (visuals().customGlowColor) {
                        playFx(OptionFx.Effect.OWN_COLOR);
                    }
                });
        tip(own, s("Elige el color del brillo y del destello. Apagado, el brillo toma la media de los colores del cristal.",
                "Chooses the colour of the glow and the flash. When off, the glow takes the average of the crystal's colours."));
        list.openBranch(() -> visuals().customGlowColor);
        list.row(this.layout.control, indent);
        int branch = width - indent;
        int hexWidth = Math.min(70, branch / 2);
        this.glowHex = list.cell(new HubEditBox(this.font, 0, 0, hexWidth, this.layout.control,
                Component.literal(s("Color de brillo hexadecimal", "Glow hex colour"))), 0, hexWidth);
        this.glowHex.setMaxLength(7);
        this.updatingControls = true;
        this.glowHex.setValue(CrystalVisualConfig.toHex(look.glowColor));
        this.updatingControls = false;
        this.glowHex.setResponder(this::onGlowHex);
        list.paint((graphics, font, x, y, cellWidth, height, alpha) -> drawSwatch(graphics, font, x, y, cellWidth, height,
                visuals().glowColor, s("Color del halo", "Halo colour"), alpha), hexWidth + 5, branch - hexWidth - 5);
        list.row(pickerHeight(branch, 0.46F, 84), indent);
        this.glowPicker = list.full(new HubColorPicker(0, 0, 0, 0, Component.literal(s("Color del brillo", "Glow colour")),
                look.glowColor, this::onGlowPicker).lit().onRelease(this::commit));
        list.closeBranch();
        return list;
    }

    private void toggleGlow() {
        CrystalAppearance look = visuals();
        if (!look.glowEnabled && look.glowPowerPercent <= 0) {
            // Coming back at no power would light nothing: it comes back at the default, and its
            // slider is built again to show it, still folded, so it unfolds below.
            look.glowPowerPercent = CrystalVisualConfig.defaults(this.enemy).glowPowerPercent;
            refreshSection("glow");
        }
        look.glowEnabled = !look.glowEnabled;
        glowTouched();
        commit();
        if (look.glowEnabled) {
            playFx(OptionFx.Effect.GLOW);
        }
    }

    /** The blur needs something that moves: a crystal that neither turns nor floats has nothing to trail. */
    private void refreshBlurControl() {
        if (this.blurSlider == null) {
            return;
        }
        boolean moving = visuals().rotationSpeedPercent > 0 || visuals().floatingSpeedPercent > 0;
        boolean converted = visuals().converted();
        tip(this.blurSlider, converted
                ? s("No se usa mientras Converter My Crystal dibuja otra cosa en lugar del cristal: la estela es la de sus capas.",
                        "Not used while Converter My Crystal draws something else in the crystal's place: the trail is its layers'.")
                : moving
                ? s("Las capas dejan una estela suave al girar, como una foto en movimiento. Más alto, estela más larga. Funciona también con el brillo apagado. Solo dibujo, en tu pantalla.",
                        "The layers leave a soft trail as they turn, like a photo in motion. Higher, a longer trail. It works with the glow off too. Drawing only, on your screen.")
                : s("Un cristal que ni gira ni flota no deja estela: sube Giro o Flotación en Colores.",
                        "A crystal that neither turns nor floats leaves no trail: raise Rotation or Floating in Colours."));
    }

    /** The slider follows the crystal's motion, which the other wing may be changing right now. */
    private void refreshBlurState() {
        if (this.blurSlider == null) {
            return;
        }
        boolean moving = visuals().rotationSpeedPercent > 0 || visuals().floatingSpeedPercent > 0;
        this.blurSlider.active = moving && !visuals().converted() && !(this.enemy && !CrystalVisualConfig.enemyCustomEnabled());
    }

    private RowList buildFlash(int width) {
        RowList list = newList(width);
        int indent = indent(list);
        CrystalAppearance look = visuals();
        HubButton flash = toggle(list, 0, s("Destello al explotar", "Flash on explosion"), () -> visuals().flashEnabled,
                HubIcons.explosion(0.6F), () -> {
                    visuals().flashEnabled = !visuals().flashEnabled;
                    commit();
                    if (visuals().flashEnabled) {
                        scheduleFlashPreview();
                    }
                });
        tip(flash, s("La luz que deja un cristal al explotar. Es solo dibujo: no cambia la explosión, el daño ni ningún paquete. Haz clic en el cristal del centro para verlo.",
                "The light a crystal leaves when it explodes. Drawing only: it changes no explosion, no damage and no packet. Click the crystal in the middle to see it."));
        list.openBranch(() -> visuals().flashEnabled);
        this.shapeButton = button(list, indent, shapeLabel(), null, this::openShapeWindow);
        tip(this.shapeButton, s("Las " + CrystalFlashStyle.values().length + " formas del destello, en el color del brillo. Las comparten tus cristales y los ajenos.",
                "The flash's " + CrystalFlashStyle.values().length + " shapes, in the glow colour. Shared by your crystals and the enemy's."));
        list.openBranch(() -> CrystalVisualConfig.flashStyle().scalable());
        slider(list, indent, HubSlider.linear(0, 0, 0, 0, CrystalAppearance.MIN_FLASH_SCALE, 300, look.flashScalePercent, value -> {
            visuals().flashScalePercent = (int) Math.round(value);
            scheduleFlashPreview();
        }, value -> s("Tamaño: ", "Size: ") + Math.round(value) + "%")).onRelease(this::commit);
        list.closeBranch();
        HubSlider opacity = slider(list, indent, HubSlider.linear(0, 0, 0, 0, CrystalAppearance.MIN_FLASH_OPACITY, 100,
                look.flashOpacityPercent, value -> {
                    visuals().flashOpacityPercent = (int) Math.round(value);
                    scheduleFlashPreview();
                }, value -> s("Opacidad: ", "Opacity: ") + Math.round(value) + "%")).onRelease(this::commit);
        tip(opacity, s("Cuánto se ve el destello. 50% es el de siempre; 100%, el más intenso.",
                "How strongly the flash shows. 50% is the usual flash; 100% is the most intense."));
        slider(list, indent, HubSlider.linear(0, 0, 0, 0, CrystalAppearance.MIN_FLASH_DURATION / 1000.0D,
                CrystalAppearance.MAX_FLASH_DURATION / 1000.0D, look.flashDurationMillis / 1000.0D, value -> {
                    visuals().flashDurationMillis = (int) Math.round(value * 1000.0D);
                    scheduleFlashPreview();
                }, value -> String.format(Locale.ROOT, s("Duración: %.1f s", "Duration: %.1f s"), value))).onRelease(this::commit);
        list.closeBranch();
        if (!this.layout.stage.isEmpty()) {
            list.space(2);
            list.note(this.font, s("Haz clic en el cristal para verlo.", "Click the crystal to see it."), 0);
        }
        return list;
    }

    private String styleLabel() {
        return s("Estilo: ", "Style: ") + CrystalVisualConfig.glowStyle().label(this.spanish) + "  ›";
    }

    private void changeGlowStyle(CrystalGlowStyle style) {
        CrystalVisualConfig.setGlowStyle(style);
        glowTouched();
        commit();
        for (int index = 0; index < this.styleButtons.size(); index++) {
            this.styleButtons.get(index).setSelected(CrystalGlowStyle.values()[index] == style);
        }
        if (this.styleButton != null) {
            this.styleButton.setMessage(Component.literal(styleLabel()));
        }
        // The chosen style's card unfolds its own settings: they glide into view as they come out.
        if (this.window != null && !this.styleButtons.isEmpty()) {
            this.window.list().follow(styleTop(style));
        }
        playFx(OptionFx.Effect.GLOW);
    }

    /** Where the window scrolls to for {@code style}: its card at the top, the one above just showing. */
    private int styleTop(CrystalGlowStyle style) {
        return cardTop(style.ordinal());
    }

    /** Where a window of cards scrolls to for its {@code index}-th card: at the top, the one above just showing. */
    private int cardTop(int index) {
        int step = this.layout.control + this.layout.gap + 1;
        return Math.max(0, index * step - step / 2);
    }

    /**
     * Under a chosen card: what it is, then its own settings, one slider each; {@code touched} runs
     * as one moves and {@code released} when it is let go.
     */
    private void cardBranch(RowList rows, int indent, String description, StyleSetting[] settings, Runnable touched, Runnable released) {
        rows.note(this.font, description, indent);
        for (StyleSetting setting : settings) {
            HubSlider slider = slider(rows, indent, HubSlider.linear(0, 0, 0, 0, setting.min(), setting.max(), setting.value(), value -> {
                setting.set((int) Math.round(value));
                touched.run();
            }, value -> setting.label(this.spanish) + ": " + setting.shown((int) Math.round(value), this.spanish)))
                    .onRelease(released);
            tip(slider, setting.tip(this.spanish));
        }
    }

    /** Each glow style's mark, from the game's own items. */
    private static HubButton.Icon styleIcon(CrystalGlowStyle style) {
        return switch (style) {
            case LIGHT -> HubIcons.glowstone(0.2F);
            case LAYERS -> HubIcons.item("amethyst_shard", 0.4F);
            case HEARTBEAT -> HubIcons.item("heart_of_the_sea", 0.6F);
            case AURORA -> HubIcons.item("prismarine_crystals", 0.8F);
            case ORBIT -> HubIcons.item("ender_eye", 1.0F);
            case FLAMES -> HubIcons.item("blaze_powder", 1.2F);
            case SIGIL -> HubIcons.item("enchanted_book", 1.4F);
        };
    }

    /**
     * The glow's styles, one card each, in a window over the page, beside the crystal that wears each
     * one. The chosen card unfolds what the style is and its own settings, which no other style shares.
     */
    private void openStyleWindow() {
        Rect area = this.layout.rightWing;
        RowList rows = new RowList(HubWindow.listWidth(area), this.layout.gap + 1);
        int indent = indent(rows);
        this.styleButtons.clear();
        CrystalGlowStyle current = CrystalVisualConfig.glowStyle();
        for (CrystalGlowStyle style : CrystalGlowStyle.values()) {
            rows.row(this.layout.control);
            HubButton row = rows.full(new HubButton(0, 0, 0, 0, Component.literal(style.label(this.spanish)),
                    ignored -> changeGlowStyle(style)).lit().icon(styleIcon(style)));
            row.setSelected(style == current);
            tip(row, style.description(this.spanish));
            this.styleButtons.add(row);
            rows.openBranch(() -> CrystalVisualConfig.glowStyle() == style);
            cardBranch(rows, indent, style.description(this.spanish), style.settings(), this::glowTouched, this::commit);
            rows.closeBranch();
        }
        openWindow(new HubWindow(area, s("Estilo del brillo", "Glow style"), HubIcons.glowstone(0.2F), rows,
                Component.literal(s("Listo", "Done")), this.layout.control, this::closeWindow));
        rows.scrollTo(styleTop(current), true);
    }

    private String shapeLabel() {
        return s("Forma: ", "Shape: ") + CrystalVisualConfig.flashStyle().label(this.spanish) + "  ›";
    }

    private void changeFlashStyle(CrystalFlashStyle style) {
        CrystalVisualConfig.setFlashStyle(style);
        commit();
        for (int index = 0; index < this.shapeButtons.size(); index++) {
            this.shapeButtons.get(index).setSelected(CrystalFlashStyle.values()[index] == style);
        }
        if (this.shapeButton != null) {
            this.shapeButton.setMessage(Component.literal(shapeLabel()));
        }
        if (this.window != null && !this.shapeButtons.isEmpty()) {
            this.window.list().follow(cardTop(style.ordinal()));
        }
        showFlash();
    }

    /** Shows the flash at once, silently: browsing the shapes and their settings is not one explosion each. */
    private void showFlash() {
        long now = System.nanoTime();
        if (visuals().flashActive()) {
            this.stage.clearFlashes();
            this.stage.appear(now);
            this.stage.explode(now, visuals().copy(), false);
        }
    }

    /** Each flash shape's mark, from the game's own items. */
    private static HubButton.Icon shapeIcon(CrystalFlashStyle style) {
        float phase = style.ordinal() * 0.2F;
        return switch (style) {
            case EXPLOSION -> HubIcons.item("fire_charge", phase);
            case SKULL -> HubIcons.item("bone", phase);
            case MY_HEAD -> HubIcons.item("totem_of_undying", phase);
            case LIGHTNING -> HubIcons.item("blaze_rod", phase);
            case HEART -> HubIcons.item("heart_pottery_sherd", phase);
            case STAR -> HubIcons.item("nether_star", phase);
            case SHOCKWAVE -> HubIcons.item("echo_shard", phase);
            case VORTEX -> HubIcons.item("ender_pearl", phase);
            case CROWN -> HubIcons.item("golden_helmet", phase);
            case CRESCENT -> HubIcons.item("phantom_membrane", phase);
            case SNOWFLAKE -> HubIcons.item("snowball", phase);
            case FLOWER -> HubIcons.item("pink_petals", phase);
            case GEM -> HubIcons.item("emerald", phase);
            case SWORDS -> HubIcons.item("iron_sword", phase);
        };
    }

    /**
     * The flash's shapes, one card each, in a window over the page, beside the crystal that shows each
     * one. The chosen card unfolds what the shape is and its own settings; letting go of one shows
     * the flash again.
     */
    private void openShapeWindow() {
        Rect area = this.layout.rightWing;
        RowList rows = new RowList(HubWindow.listWidth(area), this.layout.gap + 1);
        int indent = indent(rows);
        this.shapeButtons.clear();
        CrystalFlashStyle current = CrystalVisualConfig.flashStyle();
        for (CrystalFlashStyle style : CrystalFlashStyle.values()) {
            rows.row(this.layout.control);
            HubButton row = rows.full(new HubButton(0, 0, 0, 0, Component.literal(style.label(this.spanish)),
                    ignored -> changeFlashStyle(style)).lit().icon(shapeIcon(style)));
            row.setSelected(style == current);
            tip(row, style.description(this.spanish));
            this.shapeButtons.add(row);
            rows.openBranch(() -> CrystalVisualConfig.flashStyle() == style);
            cardBranch(rows, indent, style.description(this.spanish), style.settings(), () -> { }, () -> {
                commit();
                showFlash();
            });
            rows.closeBranch();
        }
        openWindow(new HubWindow(area, s("Forma del destello", "Flash shape"), HubIcons.explosion(0.2F), rows,
                Component.literal(s("Listo", "Done")), this.layout.control, this::closeWindow));
        rows.scrollTo(cardTop(current.ordinal()), true);
    }

    private void openWindow(HubWindow next) {
        closeWindowNow();
        this.window = next;
        for (AbstractWidget widget : next.widgets()) {
            widget.visible = false;
            addWidget(widget);
        }
        setFocused(null);
        play(SoundEvents.AMETHYST_CLUSTER_PLACE, 1.4F, 0.45F);
    }

    private void closeWindow() {
        if (this.window != null && !this.window.closing()) {
            this.window.close(System.nanoTime());
            commit();
        }
    }

    private void closeWindowNow() {
        if (this.window == null) {
            return;
        }
        for (AbstractWidget widget : this.window.widgets()) {
            removeWidget(widget);
        }
        this.window = null;
        this.shapeButtons.clear();
        setFocused(null);
    }

    private RowList buildSound(int width) {
        RowList list = newList(width);
        int indent = indent(list);
        toggle(list, 0, s("Sonido personalizado", "Custom sound"), CrystalVisualConfig::customSoundEnabled, HubIcons.note(0.9F), () -> {
            CrystalVisualConfig.setCustomSoundEnabled(!CrystalVisualConfig.customSoundEnabled());
            commit();
            CrystalSoundManager.reloadFromConfig();
            scheduleSoundPreview();
            if (CrystalVisualConfig.customSoundEnabled()) {
                playFx(OptionFx.Effect.SOUND);
            }
        });
        list.openBranch(CrystalVisualConfig::customSoundEnabled);
        HubButton file = button(list, indent, s("Elegir archivo…", "Choose file…"), HubIcons.item("music_disc_pigstep", 0.5F),
                () -> SoundFilePicker.choose(this.spanish, this::importSound, message -> this.soundStatus = message));
        tip(file, s("WAV, OGG o MP3 de hasta 5 segundos.", "WAV, OGG or MP3, up to 5 seconds."));
        list.row(this.font.lineHeight + 2, indent);
        list.paint((graphics, font, x, y, cellWidth, height, alpha) -> {
            String name = CrystalVisualConfig.customSoundFileName();
            String status = this.soundStatus.isBlank()
                    ? (name.isBlank() ? s("Sin archivo: suena la explosión de Vanilla", "No file: Vanilla's explosion plays") : name)
                    : this.soundStatus;
            CrystalUi.label(graphics, font, HubDraw.fit(font, status, cellWidth), x, y + 1, CrystalTheme.fade(0xFFD7C2DF, alpha));
        }, 0, width - indent);
        list.closeBranch();
        list.heading(s("Mezcla", "Mix"), HubIcons.block("note_block", 1.2F));
        slider(list, 0, HubSlider.linear(0, 0, 0, 0, 0.0D, 1.0D, CrystalVisualConfig.soundVolume(),
                value -> CrystalVisualConfig.setSoundVolume((float) value),
                value -> s("Volumen: ", "Volume: ") + Math.round(value * 100.0D) + "%")).onRelease(() -> {
                    commit();
                    scheduleSoundPreview();
                });
        slider(list, 0, HubSlider.linear(0, 0, 0, 0, 0.5D, 2.0D, CrystalVisualConfig.soundSpeed(),
                value -> CrystalVisualConfig.setSoundSpeed((float) value),
                value -> String.format(Locale.ROOT, s("Velocidad: %.2fx", "Speed: %.2fx"), value))).onRelease(() -> {
                    commit();
                    scheduleSoundPreview();
                });
        list.row(this.layout.control);
        list.full(new HubButton(0, 0, 0, 0, Component.literal(s("▶ Probar", "▶ Test")), ignored -> {
            long now = System.nanoTime();
            this.stage.appear(now - 1_000_000_000L);
            this.stage.explode(now, visuals().copy(), true);
        }).style(HubButton.Style.PRIMARY));
        list.space(2);
        list.note(this.font, s("Solo lo oyes tú.", "Only you hear it."), 0);
        return list;
    }

    private void importSound(Path file) {
        String error = CrystalSoundManager.importFile(file);
        this.soundStatus = error.isEmpty() ? s("Sonido cargado", "Sound loaded") : localizeSoundError(error);
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

    private RowList buildHelpers(int width) {
        RowList list = newList(width);
        int indent = indent(list);
        list.heading(s("Ayudas de cristal", "Crystal helpers"), HubIcons.obsidian(1.4F));
        HubButton safe = toggle(list, 0, s("Evitar romper obsidiana", "Don't break obsidian"), CrystalVisualConfig::safeCrystal,
                HubIcons.obsidian(1.2F), () -> {
                    CrystalVisualConfig.setSafeCrystal(!CrystalVisualConfig.safeCrystal());
                    commit();
                    if (CrystalVisualConfig.safeCrystal()) {
                        playFx(OptionFx.Effect.SHIELD);
                    }
                });
        tip(safe, s("Evita minar la obsidiana sobre la que pones cristales mientras sostienes uno. Envía menos acciones que Vanilla; apágalo si tu servidor no permite filtros de entrada.",
                "Keeps you from mining the obsidian under your crystals while you hold one. It sends fewer actions than Vanilla; turn it off if your server forbids input filters."));
        HubButton debounce = toggle(list, 0, s("Antirrebote de obsidiana", "Obsidian debounce"), CrystalVisualConfig::obsidianDebounce,
                HubIcons.item("clock_00", 0.7F), () -> {
                    CrystalVisualConfig.setObsidianDebounce(!CrystalVisualConfig.obsidianDebounce());
                    ObsidianDebounce.settingsChanged();
                    commit();
                    if (CrystalVisualConfig.obsidianDebounce()) {
                        playFx(OptionFx.Effect.DEBOUNCE);
                    }
                });
        tip(debounce, s("Evita poner dos obsidianas sin querer: tras colocar una, rechaza otra durante el tiempo elegido. Cambiar de ranura lo reinicia, así obsidiana, cristal, obsidiana nunca se frena. Un clic rechazado no envía nada; apágalo si tu servidor no permite filtros de entrada.",
                "Stops accidental double obsidian: after placing one, it refuses another for the chosen time. Switching slots resets it, so obsidian, crystal, obsidian is never slowed. A refused click sends nothing; turn it off if your server forbids input filters."));
        list.openBranch(CrystalVisualConfig::obsidianDebounce);
        slider(list, indent, HubSlider.curved(0, 0, 0, 0, 0, CrystalVisualConfig.MAX_OBSIDIAN_DEBOUNCE_MILLIS,
                CrystalVisualConfig.obsidianDebounceMillis(), value -> {
                    CrystalVisualConfig.setObsidianDebounceMillis(debounceStep(value));
                    ObsidianDebounce.settingsChanged();
                }, value -> s("Tiempo: ", "Window: ") + debounceLabel(debounceStep(value)))).onRelease(this::commit);
        list.closeBranch();
        list.heading(s("En la pelea", "In the fight"), HubIcons.item("totem_of_undying", 0.8F));
        HubButton why = toggle(list, 0, s("¿Por qué no salió?", "Why didn't it place?"), CrystalVisualConfig::placementDiagnostic,
                HubIcons.item("barrier", 1.3F), () -> {
                    CrystalVisualConfig.setPlacementDiagnostic(!CrystalVisualConfig.placementDiagnostic());
                    commit();
                    if (CrystalVisualConfig.placementDiagnostic()) {
                        playFx(OptionFx.Effect.REFUSED);
                    }
                });
        tip(why, s("Cuando un cristal no aparece donde hiciste clic, dice por qué bajo la mira: no era obsidiana, había un ítem, experiencia, un jugador u otro cristal, tu clic dio en el cristal que rompías, o el servidor no lo colocó. Solo lee lo que ya ves; no envía nada.",
                "When a crystal does not appear where you clicked, it says why under the crosshair: not obsidian, an item, experience, a player or another crystal in the way, your click hit the crystal you were breaking, or the server did not place it. It only reads what you already see; it sends nothing."));
        HubButton meter = toggle(list, 0, s("Medidor de latencia", "Latency meter"), CrystalVisualConfig::latencyMeter,
                HubIcons.clock(0.7F), () -> {
                    CrystalVisualConfig.setLatencyMeter(!CrystalVisualConfig.latencyMeter());
                    commit();
                    if (CrystalVisualConfig.latencyMeter()) {
                        playFx(OptionFx.Effect.DEBOUNCE);
                    }
                });
        tip(meter, s("Junto a la barra rápida, mientras tienes cristales u obsidiana en la mano: cuánto tardó el servidor en poner tu último cristal y en quitar el último que rompiste, medido aquí mismo.",
                "Beside the hotbar, while you hold crystals or obsidian: how long the server took to place your last crystal and to remove the last one you broke, measured right here."));
        HubButton recap = toggle(list, 0, s("Resumen al morir", "Death recap"), CrystalVisualConfig::deathRecap,
                HubIcons.item("totem_of_undying", 1.9F), () -> {
                    CrystalVisualConfig.setDeathRecap(!CrystalVisualConfig.deathRecap());
                    commit();
                    if (CrystalVisualConfig.deathRecap()) {
                        playFx(OptionFx.Effect.SHIELD);
                    }
                });
        tip(recap, s("En la pantalla de muerte, o en tu chat si el servidor la salta: las explosiones de los últimos 8 segundos y de quién eran los cristales, los tótems de cada lado y lo rápido que bajó tu vida.",
                "On the death screen, or in your chat when the server skips it: the blasts of the last 8 seconds and whose crystals they were, the totems on each side and how fast your health went."));
        list.space(1);
        list.note(this.font, s("Evitar romper obsidiana y el antirrebote filtran clics: envían menos acciones que Vanilla. Apágalos si tu servidor no permite filtros de entrada.",
                "Don't break obsidian and the debounce filter clicks: they send fewer actions than Vanilla. Turn them off if your server forbids input filters."), 0);
        return list;
    }

    /** The client-side crystal: the break, the explosion and the placement on screen at once, packets left to Vanilla. */
    private RowList buildCore(int width) {
        RowList list = newList(width);
        int indent = indent(list);
        list.heading(s("Golpe y colocación", "Hit and place"), HubIcons.item("netherite_sword", 0.3F));
        HubButton core = toggle(list, 0, s("Rotura al instante", "Instant break"), CrystalVisualConfig::clientCrystal,
                HubIcons.shatter(0.5F), () -> {
                    CrystalVisualConfig.setClientCrystal(!CrystalVisualConfig.clientCrystal());
                    commit();
                    if (CrystalVisualConfig.clientCrystal()) {
                        playFx(OptionFx.Effect.CORE);
                    }
                });
        tip(core, s("Un cristal que golpeas desaparece de tu pantalla al instante, sin esperar al servidor. Solo deja de dibujarse: sigue en tu mundo, así tu siguiente clic, tu alcance y cada paquete son los de Vanilla. Si el servidor no acepta el golpe, vuelve a verse.",
                "A crystal you hit vanishes from your screen at once, without waiting for the server. It only stops being drawn: it stays in your world, so your next click, your reach and every packet are Vanilla's. If the server refuses the hit, it shows again."));
        list.openBranch(CrystalVisualConfig::clientCrystal);
        HubButton blast = toggle(list, indent, s("Explosión al instante", "Instant explosion"), CrystalVisualConfig::instantExplosion,
                HubIcons.explosion(0.9F), () -> {
                    CrystalVisualConfig.setInstantExplosion(!CrystalVisualConfig.instantExplosion());
                    commit();
                    if (CrystalVisualConfig.instantExplosion()) {
                        playFx(OptionFx.Effect.BLAST);
                    }
                });
        tip(blast, s("El sonido y la explosión suenan en el golpe, no un viaje de ida y vuelta después. El empuje y los bloques siguen llegando del servidor, y su explosión no suena dos veces.",
                "The sound and the blast play at the hit, not a round trip later. The knockback and the blocks still come from the server, and its explosion never plays twice."));
        HubButton ghosts = toggle(list, indent, s("Cristales fantasma", "Ghost crystals"), CrystalVisualConfig::ghostCrystals,
                HubIcons.ghost(0.4F), () -> {
                    CrystalVisualConfig.setGhostCrystals(!CrystalVisualConfig.ghostCrystals());
                    commit();
                    if (CrystalVisualConfig.ghostCrystals()) {
                        playFx(OptionFx.Effect.GHOST);
                    }
                });
        tip(ghosts, s("Al poner un cristal, dibuja uno de muestra sobre la obsidiana hasta que llega el del servidor. No existe en el mundo: no se puede golpear, no choca y ningún paquete lo nombra.",
                "When you place a crystal, a stand-in is drawn on the obsidian until the server's arrives. It is not in the world: it cannot be hit, has no collision and no packet names it."));
        HubButton cue = toggle(list, indent, s("Aviso de golpe rechazado", "Refused hit cue"), CrystalVisualConfig::refusedHitCue,
                HubIcons.block("note_block", 1.1F), () -> {
                    CrystalVisualConfig.setRefusedHitCue(!CrystalVisualConfig.refusedHitCue());
                    commit();
                    if (CrystalVisualConfig.refusedHitCue()) {
                        playFx(OptionFx.Effect.REFUSED);
                    }
                });
        tip(cue, s("Si el servidor se queda con un cristal que golpeaste, vuelve a verse con una nota grave, para que no lo tomes por uno nuevo.",
                "If the server keeps a crystal you hit, it shows again with a low note, so you don't take it for a new one."));
        HubButton pace = toggle(list, indent, s("Colocar a tiempo", "Paced placement"), CrystalVisualConfig::fastPlacement,
                HubIcons.drop(0.6F), () -> {
                    CrystalVisualConfig.setFastPlacement(!CrystalVisualConfig.fastPlacement());
                    commit();
                    if (CrystalVisualConfig.fastPlacement()) {
                        playFx(OptionFx.Effect.PACE);
                    }
                });
        tip(pace, s("Un clic sobre el cristal que acabas de romper espera a que el servidor lo quite y entonces coloca, en vez de gastarse en el cristal que se va. Nunca se adelanta al servidor: es el mismo paquete de Vanilla, en el mismo punto del tick, con tu misma mirada, a cualquier ping. Cambia cuándo llega tu clic, así que viene apagado: revisa las normas de tu servidor.",
                "A click on the crystal you just broke waits for the server to remove it, then places, instead of being spent on the crystal on its way out. It never gets ahead of the server: the same Vanilla packet, at the same point of the tick, with your own aim, at any ping. It changes when your click lands, so it comes off: check your server's rules."));
        list.row(this.font.lineHeight * 2 + 4, indent);
        list.paint((graphics, font, x, y, cellWidth, height, alpha) -> drawCoreStatus(graphics, font, x, y, cellWidth, alpha), 0, width - indent);
        list.closeBranch();
        list.space(1);
        List<String> others = CrystalCore.otherOptimizers();
        if (!others.isEmpty()) {
            list.note(this.font, s("En pausa: cede ante " + others.get(0) + ", que ya maneja los cristales. Quítalo para usar el cristal del cliente.",
                    "Paused: it stands down for " + others.get(0) + ", which already drives the crystals. Remove it to use the client-side crystal."), 0);
        }
        list.note(this.font, s("Ocultar, la explosión, los fantasmas y el aviso son solo dibujo y sonido en tu pantalla; tus paquetes siguen siendo los de Vanilla. Colocar a tiempo cambia cuándo sale un clic, nunca qué sale.",
                "Hiding, the explosion, the ghosts and the cue are only drawing and sound on your screen; your packets stay Vanilla's. Paced placement changes when a click goes out, never what goes out."), 0);
        return list;
    }

    /** The round trip the client-side crystal follows and what it did, rebuilt only when a number changes. */
    private void drawCoreStatus(GuiGraphicsExtractor graphics, Font font, int x, int y, int width, float alpha) {
        long latency = CrystalBreakPrediction.latencyMillis();
        long window = CrystalBreakPrediction.predictionWindowNanos() / 1_000_000L;
        int refused = CrystalBreakPrediction.refused();
        int held = FastPlacement.held();
        int sent = FastPlacement.sent();
        long key = (((latency * 1009L + window) * 1009L + refused) * 1009L + held) * 1009L + sent;
        if (this.coreStatus == null || key != this.coreStatusKey) {
            this.coreStatusKey = key;
            this.coreStatus = "Ping " + latency + " ms · " + s("ventana ", "window ") + window + " ms";
            this.coreStatusLine = s("Rechazados ", "Refused ") + refused + (CrystalVisualConfig.fastPlacement()
                    ? " · " + s("esperados ", "held ") + held + " (" + sent + s(" puestos)", " placed)") : "");
        }
        HubSkin skin = HubSkin.current();
        CrystalUi.label(graphics, font, HubDraw.fit(font, this.coreStatus, width), x, y + 1, CrystalTheme.fade(skin.muted, alpha));
        CrystalUi.label(graphics, font, HubDraw.fit(font, this.coreStatusLine, width), x, y + font.lineHeight + 3, CrystalTheme.fade(skin.muted, alpha));
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

    /** Converter My Crystal: something else drawn in the crystal's place, chosen from a searched catalogue. */
    private RowList buildConverter(int width) {
        RowList list = newList(width);
        int indent = indent(list);
        HubButton toggle = toggle(list, 0, this.enemy ? s("Convertir los ajenos", "Convert enemy crystals")
                : s("Convertir mi cristal", "Convert my crystal"), () -> visuals().converterEnabled, HubIcons.converter(1.5F), () -> {
                    visuals().converterEnabled = !visuals().converterEnabled;
                    commit();
                    converterChanged();
                    if (visuals().converterEnabled) {
                        playFx(OptionFx.Effect.CONVERT);
                    }
                });
        tip(toggle, s("Dibuja un bloque, un ítem o una entidad en lugar del cristal. Solo cambia el dibujo en tu pantalla: el cristal sigue donde está, con la misma zona de golpe.",
                "Draws a block, an item or an entity in the crystal's place. Only the drawing on your screen changes: the crystal stays where it is, with the same box to hit."));
        list.openBranch(() -> visuals().converterEnabled);
        int branch = width - indent;
        int gap = 3;
        int half = (branch - gap) / 2;
        list.row(this.layout.control, indent);
        this.blocksButton = list.cell(new HubButton(0, 0, 0, 0, Component.literal(s("Bloques e ítems", "Blocks & items")),
                ignored -> showConverterKind(false)).lit(), 0, half);
        this.entitiesButton = list.cell(new HubButton(0, 0, 0, 0, Component.literal(s("Entidades", "Entities")),
                ignored -> showConverterKind(true)).lit(), half + gap, branch - half - gap);
        this.blocksButton.setSelected(!this.converterEntities);
        this.entitiesButton.setSelected(this.converterEntities);
        list.row(this.layout.control, indent);
        this.converterSearch = list.full(new HubEditBox(this.font, 0, 0, 0, this.layout.control, Component.literal(s("Buscar", "Search"))));
        this.converterSearch.setHint(Component.literal(s("Buscar por nombre o id…", "Search by name or id…")));
        this.converterSearch.setMaxLength(60);
        this.converterSearch.setValue(this.converterQuery);
        this.converterSearch.setResponder(value -> {
            this.converterQuery = value;
            if (this.converterGrid != null) {
                this.converterGrid.show(this.converterGrid.source(), value);
            }
        });
        list.row(Mth.clamp(this.layout.body.height() / 2, 64, 140), indent);
        this.converterGrid = list.full(new ConverterGrid(this.font, Component.literal(s("Catálogo", "Catalogue")),
                s("Entra en un mundo para ver el catálogo.", "Join a world to see the catalogue."),
                s("Nada coincide con la búsqueda.", "Nothing matches the search."),
                () -> visuals().converterTarget, target -> {
                    // A second click on the chosen one lets go of it.
                    visuals().converterTarget = target.equals(visuals().converterTarget) ? "" : target;
                    commit();
                    converterChanged();
                }));
        this.converterGrid.show(this.converterEntities ? ConverterGrid.entities() : ConverterGrid.items(), this.converterQuery);
        list.row(this.font.lineHeight + 2, indent);
        list.paint((graphics, font, x, y, cellWidth, height, alpha) -> drawConverterChoice(graphics, font, x, y, cellWidth, alpha), 0, branch);
        list.closeBranch();
        list.space(1);
        list.note(this.font, s("Lo que elijas se dibuja por encima de la textura original de Minecraft y de la de cualquier resource pack. El giro, la flotación y el tamaño siguen en Colores; el brillo, en Brillo.",
                "What you choose is drawn over Minecraft's own texture and over any resource pack's. Rotation, floating and size stay in Colours; the glow, in Glow."), 0);
        return list;
    }

    private void showConverterKind(boolean entities) {
        this.converterEntities = entities;
        if (this.converterGrid != null) {
            this.blocksButton.setSelected(!entities);
            this.entitiesButton.setSelected(entities);
            this.converterGrid.show(entities ? ConverterGrid.entities() : ConverterGrid.items(), this.converterQuery);
        }
    }

    /** The other wing dims what a converted crystal does not draw. */
    private void converterChanged() {
        refreshSection("colours");
        refreshSection("glow");
    }

    private void drawConverterChoice(GuiGraphicsExtractor graphics, Font font, int x, int y, int width, float alpha) {
        HubSkin skin = HubSkin.current();
        String target = visuals().converterTarget;
        String text;
        int color = skin.muted;
        if (target.isEmpty()) {
            text = s("Nada elegido: el cristal se ve como siempre.", "Nothing chosen: the crystal looks as usual.");
        } else if (!CrystalConverter.drawable(target)) {
            boolean needsWorld = this.minecraft == null || this.minecraft.level == null;
            text = ConverterGrid.nameOf(target) + (needsWorld
                    ? s(": se verá al entrar en un mundo.", ": shows once you are in a world.")
                    : s(": no se puede dibujar; el cristal se queda como cristal.", ": cannot be drawn; the crystal stays a crystal."));
            color = 0xFFFFC48A;
        } else {
            text = s("Elegido: ", "Chosen: ") + ConverterGrid.nameOf(target);
            color = skin.text;
        }
        CrystalUi.label(graphics, font, HubDraw.fit(font, text, width), x, y + 1, CrystalTheme.fade(color, alpha));
    }

    /** Crystal Practice as a section, for windows with no room for it under the crystal. */
    private RowList buildPractice(int width) {
        RowList list = newList(width);
        list.row(Mth.clamp(width / 6, 30, 50));
        list.paint(this::drawPracticeBanner, 0, width);
        HubButton open = button(list, 0, s("Abrir Crystal Practice", "Open Crystal Practice"), GrimoireIcons.icon(GrimoireIcons.Sprite.NETHERITE_SWORD, 1.8F),
                () -> this.minecraft.setScreen(PracticeWarningScreen.open(this)));
        open.style(HubButton.Style.PRIMARY);
        list.space(2);
        list.note(this.font, PracticeHost.OVER_SERVERS
                ? s("Un mundo para ti con un bot, sin salir del servidor en el que estás.", "A world of your own with a bot, without leaving the server you are on.")
                : s("Un mundo para ti con un bot contra el que practicar.", "A world of your own with a bot to practise against."), 0);
        return list;
    }

    /** The enemy profile's advanced options, still to come: the stage shows what is coming. */
    private RowList buildSoon(int width) {
        RowList list = newList(width);
        list.note(this.font, s("Opciones avanzadas para los cristales ajenos, en camino.", "Advanced options for enemy crystals, on their way."), 0);
        return list;
    }

    /** The old glow, the benchmark, the tools and Herzium. */
    private RowList buildAdvanced(int width) {
        RowList list = newList(width);
        int indent = indent(list);
        list.heading(s("Brillo", "Glow"), HubIcons.glowstone(1.6F));
        HubButton oldGlow = toggle(list, 0, "Old KoHs Crystal Glow", CrystalVisualConfig::oldGlow, HubIcons.item("nether_star", 0.5F), () -> {
            CrystalVisualConfig.setOldGlow(!CrystalVisualConfig.oldGlow());
            glowTouched();
            commit();
            refreshSection("glow");
            if (CrystalVisualConfig.oldGlow()) {
                playFx(OptionFx.Effect.OLD_GLOW);
            }
        });
        tip(oldGlow, s("El brillo de antes: un halo con rayos detrás del cristal en lugar de luz en sus capas. La potencia, los reflejos, el color y el desenfoque siguen igual; el estilo y el brillo del núcleo, que son de las capas, quedan atenuados. Para tus cristales y los ajenos.",
                "The glow as it used to be: a halo with rays behind the crystal instead of light in its layers. Power, reflections, colour and blur work the same; the style and the core's glow, which belong to the layers, are dimmed. For your crystals and the enemy's."));
        list.heading(s("Mediciones", "Measurements"), HubIcons.item("repeater", 1.8F));
        HubButton benchmark = button(list, 0, s("Benchmark de optimizadores…", "Optimizer benchmark…"), HubIcons.item("clock_00", 1.3F),
                () -> this.minecraft.setScreen(new CrystalBenchmarkScreen(this)));
        tip(benchmark, s("Mide lo que tardan tus cristales en aparecer y desaparecer, en tu pantalla y en el servidor, con el optimizador que tengas. Solo observa lo que ya haces.",
                "Measures how long your crystals take to appear and disappear, on your screen and on the server, with whichever optimizer you have. It only watches what you already do."));
        list.heading(s("Compatibilidad", "Compatibility"), HubIcons.block("observer_front", 2.0F));
        HubButton monitor = button(list, 0, s("Monitor de conflictos", "Conflict Monitor"), HubIcons.block("observer_front", 1.6F),
                () -> this.minecraft.setScreen(new ConflictMonitorScreen(this)));
        tip(monitor, s("Analiza localmente los mixins instalados y muestra coincidencias que podrían interferir con Crystal Tweaks.",
                "Locally scans installed mixins and shows matches that could interfere with Crystal Tweaks."));
        list.heading("Herzium", HubIcons.item("sugar", 2.2F));
        boolean installed = HerziumBridge.installed();
        HubButton herzium = toggle(list, 0, installed ? s("Integrar con Herzium", "Integrate with Herzium")
                : s("Herzium (no instalado)", "Herzium (not installed)"), () -> installed && CrystalVisualConfig.herziumIntegration(), null, () -> {
                    if (HerziumBridge.installed()) {
                        CrystalVisualConfig.setHerziumIntegration(!CrystalVisualConfig.herziumIntegration());
                        commit();
                        if (CrystalVisualConfig.herziumIntegration()) {
                            playFx(OptionFx.Effect.HERZIUM);
                        }
                    }
                });
        herzium.style(HubButton.Style.HERZIUM);
        herzium.active = installed;
        tip(herzium, installed
                ? s("Herzium " + HerziumBridge.version() + " detectado: su orden de hotbar, aquí mismo.",
                        "Herzium " + HerziumBridge.version() + " detected: its hotbar order, right here.")
                : s("Descárgalo en modrinth.com/mod/herzium para activar esta integración.",
                        "Get it from modrinth.com/mod/herzium to enable this integration."));
        list.openBranch(() -> installed && CrystalVisualConfig.herziumIntegration());
        HubButton order = choice(list, indent, () -> s("Orden: ", "Order: ") + herziumOrder(), HerziumBridge::cycleHotbarOrder);
        order.active = HerziumBridge.orderAvailable();
        tip(order, s("Qué ranura gana si pulsas dos teclas de hotbar a la vez. Herzium: la última. Vanilla: la más alta. Vanilla invertido: la más baja. Cambia la ranura real que sostienes, así que el servidor lo ve; revisa sus normas.",
                "Which slot wins when you press two hotbar keys at once. Herzium: the last one. Vanilla: the highest. Vanilla reversed: the lowest. It changes the slot you really hold, so the server sees it; check its rules."));
        list.closeBranch();
        return list;
    }

    private String herziumOrder() {
        String current = HerziumBridge.hotbarOrder();
        return current.isEmpty() ? s("no disponible", "unavailable") : HerziumBridge.orderLabel(current, this.spanish);
    }

    /** The enemy profile switched off: its controls change nothing that is drawn, so they wait, dimmed. */
    private void dimUnusedProfile(RowList list, AbstractWidget except) {
        if (CrystalVisualConfig.enemyCustomEnabled()) {
            return;
        }
        String note = s("Este perfil está apagado: los cristales ajenos se ven con tu propio perfil. Enciende «Usar este perfil» en Colores para cambiarlo.",
                "This profile is off: enemy crystals use your own profile. Turn \"Use this profile\" on in Colours to change it.");
        for (AbstractWidget widget : list.widgets()) {
            if (widget != except) {
                widget.active = false;
                tip(widget, note);
                if (widget instanceof EditBox box) {
                    box.setEditable(false);
                }
            }
        }
    }

    private void profileUseChanged() {
        CrystalVisualConfig.setEnemyCustomEnabled(!CrystalVisualConfig.enemyCustomEnabled());
        commit();
        rebuildPanels(true);
    }

    private String speedLabel(String label, int percent) {
        if (percent == 0) {
            return label + ": " + s("Estático", "Static");
        }
        return label + ": " + percent + "%" + (percent == 100 ? " (Vanilla)" : "");
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
            case OUTER -> s("Editando: exterior", "Editing: outer");
            case INNER -> s("Editando: interior", "Editing: inner");
            case CORE -> s("Editando: núcleo", "Editing: core");
        };
    }

    private void setLayerColor(int color) {
        switch (this.layer) {
            case OUTER -> visuals().outerColor = color;
            case INNER -> visuals().innerColor = color;
            case CORE -> visuals().coreColor = color;
        }
        if (!visuals().customGlowColor) {
            // The glow follows the layers here, so the wings' light does too.
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

    private void drawSwatch(GuiGraphicsExtractor graphics, Font font, int x, int y, int width, int height, int color, String label,
            float alpha) {
        int size = Math.max(6, Math.min(11, height - 5));
        int swatchY = y + (height - size) / 2;
        graphics.fill(x, swatchY, x + size, swatchY + size, CrystalTheme.fade(color | 0xFF000000, alpha));
        CrystalUi.outline(graphics, x - 1, swatchY - 1, size + 2, size + 2, CrystalTheme.fade(0xFFE8D8F1, alpha));
        int textX = x + size + 5;
        int room = x + width - textX;
        if (room > 20) {
            CrystalUi.label(graphics, font, HubDraw.fit(font, label, room), textX, swatchY + (size - 8) / 2 + 1,
                    CrystalTheme.fade(HubSkin.current().muted, alpha));
        }
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
        GrimoireDraw.active = true;
        try {
            drawGrimoire(graphics, mouseX, mouseY, partialTick);
        } finally {
            GrimoireDraw.active = false;
        }
    }

    private void drawGrimoire(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        long now = System.nanoTime();
        float frame = Mth.clamp((now - this.lastFrame) / 1_000_000.0F, 0.0F, 50.0F);
        this.lastFrame = now;
        double seconds = now / 1_000_000_000.0D;
        CrystalAppearance look = visuals().copy();
        updateSkin(now, look);
        if (this.phase == Phase.SETTLE && now - this.phaseAt >= SETTLE_NANOS) {
            this.phase = Phase.MAIN;
        }
        if (this.phase == Phase.TRANSITION) {
            this.overlays.tickTransition(now);
        }
        HubSkin skin = HubSkin.current();

        HubDraw.isolate(graphics);
        CrystalUi.floatingParticles(graphics, this.width, this.height, seconds);
        HubDraw.isolate(graphics);
        drawBook(graphics, this.phase == Phase.SETTLE ? HubMotion.easeOutCubic(HubMotion.progress(now - this.phaseAt, 260_000_000L)) : 1.0F);

        boolean interactive = this.phase == Phase.MAIN && !this.overlays.blocking() && this.window == null;
        int pointerX = interactive ? mouseX : -10_000;
        int pointerY = interactive ? mouseY : -10_000;
        boolean landed = this.phase != Phase.TRANSITION || this.overlays.transitionLanded(now);
        float chrome = this.phase == Phase.SETTLE ? HubMotion.easeOutCubic(HubMotion.progress(now - this.phaseAt - 380_000_000L, 360_000_000L))
                : landed ? 1.0F : 0.0F;
        if (!this.kohsPage) {
            drawHeader(graphics, now, seconds, chrome, skin);
        }
        this.switchButton.visible = chrome > 0.5F && this.phase != Phase.TRANSITION && !this.kohsPage;
        this.switchButton.setAlpha(chrome);
        this.kohsButton.visible = chrome > 0.05F;
        this.kohsButton.setAlpha(chrome);
        this.doneButton.visible = true;
        this.doneButton.setAlpha(Math.max(0.35F, chrome));
        boolean soon = soonOpen();

        // The rows: placed first, so their controls are where they are drawn.
        boolean rowsInteractive = interactive && !this.kohsPage;
        this.page.place(now, frame, rowsInteractive);
        refreshBlurState();
        runScheduledPreviews(now, look);
        if (this.kohsPage) {
            Rect area = this.layout.kohsArea;
            graphics.enableScissor(area.x(), area.y(), area.right(), area.bottom());
            this.overlays.renderKohs(graphics, this.font, area, pointerX, pointerY, chrome, frame);
            graphics.disableScissor();
        } else if (!this.layout.center.isEmpty()) {
            if (soon && landed) {
                Rect stageArea = this.layout.stage;
                graphics.enableScissor(stageArea.x(), stageArea.y(), stageArea.right(), stageArea.bottom());
                this.overlays.renderComingSoon(graphics, this.font, stageArea, now, pointerX, pointerY, look, chrome);
                graphics.disableScissor();
            } else {
                // The castle wall, its floor and the crystal at its real size before it.
                this.stage.render(graphics, this.font, now, frame, look, Math.max(0.15F, chrome), pointerX, pointerY, false);
                this.fx.render(graphics, this.layout.stage, this.stage.centerX(), this.stage.centerY(), this.stage.size() * 0.4F, skin);
            }
        }
        if (landed && !this.kohsPage) {
            drawPractice(graphics, now, frame, pointerX, pointerY, chrome, skin);
        }
        if (landed && !this.kohsPage) {
            int originX = this.layout.stage.isEmpty() ? this.layout.leftPanel.right() : this.stage.centerX();
            int originY = this.layout.stage.isEmpty() ? this.layout.leftPanel.centerY() : this.stage.centerY();
            this.page.render(graphics, this.font, now, frame, pointerX, pointerY, originX, originY);
            float rails = this.phase == Phase.SETTLE ? this.page.openProgress(now) : 1.0F;
            this.sections.render(graphics, this.font, this.layout.leftRail, this.layout.leftPanel, frame, pointerX, pointerY, rails, skin);
            drawCaption(graphics, now, frame, look);
            if (!this.layout.hint.isEmpty() && !soon) {
                drawClickHint(graphics, seconds, chrome);
            }
            drawFooter(graphics, chrome, skin);
        }

        super.extractRenderState(graphics, pointerX, pointerY, partialTick);

        if (this.window != null) {
            this.window.place(now, frame);
            boolean windowPointer = this.phase == Phase.MAIN && !this.overlays.blocking();
            this.window.render(graphics, this.font, now, windowPointer ? mouseX : -10_000, windowPointer ? mouseY : -10_000, partialTick, skin);
            if (this.window.gone(now)) {
                closeWindowNow();
            }
        }
        HubDraw.isolate(graphics);
        if (interactive && !this.kohsPage && !CrystalVisualConfig.scrollHintDone()) {
            // Between the version and Done, never over a row.
            Rect footer = this.layout.footer;
            int from = footer.x() + this.font.width(this.versionLabel) + 8;
            this.page.renderScrollHint(graphics, this.font, now,
                    new Rect(from, footer.y(), Math.max(0, this.layout.done.x() - 6 - from), footer.height()));
        }
        this.overlays.render(graphics, this.font, now, frame, mouseX, mouseY, this.width, this.height);
        if (interactive) {
            drawResetTooltip(graphics, mouseX, mouseY);
        }
    }

    /** The cover and the pages, with the spines between them. */
    private void drawBook(GuiGraphicsExtractor graphics, float alpha) {
        CrystalHubLayout book = this.layout;
        GrimoireDraw.cover(graphics, book.book, alpha);
        HubDraw.isolate(graphics);
        if (this.kohsPage) {
            GrimoireDraw.page(graphics, book.body, GrimoireDraw.PAGE_DEEP, alpha);
            HubDraw.isolate(graphics);
            return;
        }
        GrimoireDraw.page(graphics, book.leftWing, GrimoireDraw.PAGE, alpha);
        Rect last = book.leftWing;
        if (!book.center.isEmpty()) {
            GrimoireDraw.spine(graphics, last.right(), last.y(), book.center.x() - last.right(), last.height(), alpha);
            GrimoireDraw.page(graphics, book.center, GrimoireDraw.PAGE_DEEP, alpha);
            last = book.center;
        }
        GrimoireDraw.spine(graphics, last.right(), last.y(), book.rightWing.x() - last.right(), last.height(), alpha);
        GrimoireDraw.page(graphics, book.rightWing, GrimoireDraw.PAGE, alpha);
        HubDraw.isolate(graphics);
    }

    /**
     * At the crystal's foot, two small mice, each with a word: the left button beats for explode,
     * then the right one for place, a heartbeat each, in turn.
     */
    private void drawClickHint(GuiGraphicsExtractor graphics, double seconds, float alpha) {
        Rect area = this.layout.hint;
        String explode = s("explotar", "explode");
        String place = s("colocar", "place");
        int mouseWidth = 7;
        int gap = 3;
        int spacing = 14;
        int total = mouseWidth + gap + this.font.width(explode) + spacing + mouseWidth + gap + this.font.width(place);
        if (total > area.width()) {
            // Closer together, then the two mice alone: they still say which button does what.
            spacing = 7;
            total = mouseWidth + gap + this.font.width(explode) + spacing + mouseWidth + gap + this.font.width(place);
            if (total > area.width()) {
                explode = "";
                place = "";
                gap = 0;
                spacing = 10;
                total = mouseWidth * 2 + spacing;
            }
        }
        double cycle = seconds % 2.4D;
        boolean leftTurn = cycle < 1.2D;
        float beat = heartbeat((float) (cycle % 1.2D));
        int x = area.centerX() - total / 2;
        int mouseY = area.y() + (area.height() - 10) / 2;
        x = drawMouseHint(graphics, x, mouseY, true, leftTurn ? beat : 0.0F, explode, alpha);
        drawMouseHint(graphics, x + spacing, mouseY, false, leftTurn ? 0.0F : beat, place, alpha);
    }

    /** A lub-dub over {@code t} seconds into a beat: a strong pulse, a softer one, then rest. */
    private static float heartbeat(float t) {
        float lub = (float) Math.exp(-Math.pow((t - 0.08F) / 0.055F, 2.0D));
        float dub = 0.65F * (float) Math.exp(-Math.pow((t - 0.3F) / 0.06F, 2.0D));
        return Math.min(1.0F, lub + dub);
    }

    /** One mouse, its {@code left} or right button lit by {@code beat}, and its word; returns where the next one starts. */
    private int drawMouseHint(GuiGraphicsExtractor graphics, int x, int y, boolean left, float beat, String word, float alpha) {
        int body = CrystalTheme.fade(CrystalTheme.lerp(GrimoireDraw.FAINT, GrimoireDraw.MUTED, beat), alpha);
        // The mouse: a rounded outline, split under its two buttons.
        graphics.fill(x + 1, y, x + 6, y + 1, body);
        graphics.fill(x, y + 1, x + 1, y + 9, body);
        graphics.fill(x + 6, y + 1, x + 7, y + 9, body);
        graphics.fill(x + 1, y + 9, x + 6, y + 10, body);
        graphics.fill(x + 1, y + 4, x + 6, y + 5, body);
        graphics.fill(x + 3, y + 1, x + 4, y + 4, body);
        int buttonX = left ? x + 1 : x + 4;
        if (beat > 0.02F) {
            HubDraw.glowDisc(graphics, buttonX + 1, y + 2, 5 + Math.round(3 * beat), GrimoireDraw.ACCENT, 0.5F * beat * alpha);
        }
        graphics.fill(buttonX, y + 1, buttonX + 2, y + 4, CrystalTheme.fade(CrystalTheme.lerp(0xFF3A2A4E, GrimoireDraw.TITLE, beat), alpha));
        int textX = x + 7 + 3;
        CrystalUi.label(graphics, this.font, word, textX, y + 1, CrystalTheme.fade(CrystalTheme.lerp(GrimoireDraw.FAINT, GrimoireDraw.TEXT, beat), alpha));
        return textX + this.font.width(word);
    }

    /** Whether the enemy profile's page that is still to come is open. */
    private boolean soonOpen() {
        return this.sections.selectedSection().id().equals("soon");
    }

    /** What the reset buttons do, since a symbol alone does not say it. */
    private void drawResetTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (!this.page.overReset(mouseX, mouseY)) {
            return;
        }
        String id = sectionOf(this.page);
        String text = resettable(id)
                ? s("Vuelve esta sección a los ajustes de fábrica. Haz clic dos veces: el primero pide confirmación.",
                        "Returns this section to its defaults. Click twice: the first click asks to confirm.")
                : s("Aquí no hay nada que restablecer.", "Nothing to reset here.");
        HubDraw.isolate(graphics);
        CrystalUi.tooltip(graphics, this.font, CrystalUi.wrap(this.font, text, Math.min(200, this.width - 30)), mouseX, mouseY, this.width,
                this.height);
    }

    /** How much of the glow lights the wings: a little always, more while it is being changed. */
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

    /** The page the crystal stands in; an empty one, where it shrinks away, while it has none. */
    private Rect crystalTarget() {
        Rect stage = this.layout.stage;
        if (stage.isEmpty() || this.kohsPage) {
            Rect area = this.layout.center.isEmpty() ? this.layout.body : this.layout.center;
            return new Rect(area.centerX(), area.centerY(), 0, 0);
        }
        return stage;
    }

    private void drawHeader(GuiGraphicsExtractor graphics, long now, double seconds, float alpha, HubSkin skin) {
        if (alpha <= 0.02F) {
            return;
        }
        Rect header = this.layout.title;
        String title = this.enemy ? s("Cristales ajenos", "Enemy crystals") : "Crystal Tweaks";
        boolean onPage = this.layout.header.isEmpty();
        float scale = Math.min(GrimoireDraw.fitScale(this.font, title, header.width(), onPage ? 2.4F : 1.6F), (header.height() - 2) / 10.0F);
        if (scale < 0.75F) {
            return;
        }
        float textY = header.y() + (header.height() - 10.0F * scale) / 2.0F;
        // A slow breath of violet behind the letters.
        float glow = 0.35F + 0.25F * HubMotion.breathe(seconds, 3.2D);
        int color = CrystalTheme.fade(this.enemy ? GrimoireDraw.CRIMSON_TEXT : GrimoireDraw.TITLE, alpha);
        if (onPage) {
            GrimoireDraw.gothic(graphics, this.font, title, header.x(), textY, scale, color, glow);
            graphics.fill(header.x(), header.bottom() + 1, header.right(), header.bottom() + 2, CrystalTheme.fade(GrimoireDraw.RULE_TITLE, alpha));
        } else {
            GrimoireDraw.gothicCentered(graphics, this.font, title, header.centerX(), textY, scale, color, glow);
        }
    }

    private void drawFooter(GuiGraphicsExtractor graphics, float alpha, HubSkin skin) {
        if (this.versionLabel.isEmpty() || alpha <= 0.02F) {
            return;
        }
        Rect footer = this.layout.footer;
        CrystalUi.label(graphics, this.font, this.versionLabel, footer.x() + 2, footer.y() + (footer.height() - 8) / 2,
                CrystalTheme.fade(GrimoireDraw.FAINT, alpha));
    }

    /**
     * Under the crystal: the glow it wears, its name in gothic letters and its values; or, while the
     * pointer is on an option, what that option does, on a plate that may grow up over the crystal.
     */
    private void drawCaption(GuiGraphicsExtractor graphics, long now, float frame, CrystalAppearance look) {
        Rect area = this.layout.caption;
        if (area.isEmpty()) {
            return;
        }
        AbstractWidget shown = null;
        if (this.page.list() != null && this.page.shown()) {
            for (AbstractWidget widget : this.page.list().widgets()) {
                if (widget.visible && widget.isHovered() && this.details.containsKey(widget)) {
                    shown = widget;
                    break;
                }
            }
        }
        String text = shown == null ? null : this.details.get(shown);
        this.captionShown = HubMotion.damp(this.captionShown, text != null ? 1.0F : 0.0F, frame, text != null ? 55.0F : 90.0F);
        if (text != null && !text.equals(this.captionKey)) {
            this.captionKey = text;
            this.captionLines = CrystalUi.wrap(this.font, text, Math.max(20, area.width() - 12));
        }
        if (this.captionShown < 0.98F && !soonOpen()) {
            drawSummary(graphics, area, look, 1.0F - this.captionShown);
        }
        if (this.captionShown <= 0.02F || this.captionLines.isEmpty()) {
            return;
        }
        // It may grow upward over the lower half of the stage when the text is long.
        int lineHeight = GrimoireDraw.line(this.font);
        int maxLines = Math.max(1, (this.layout.stage.height() / 2 - 8) / lineHeight);
        int lines = Math.min(this.captionLines.size(), maxLines);
        int height = lines * lineHeight + 8;
        int y = area.bottom() - height + Math.round((1.0F - this.captionShown) * 4.0F);
        float alpha = this.captionShown;
        HubDraw.isolate(graphics);
        GrimoireDraw.plate(graphics, area.x(), y, area.width(), height, 0xF40C0811, CrystalTheme.lerp(GrimoireDraw.BORDER, GrimoireDraw.ACCENT, 0.5F),
                alpha);
        int lineY = y + 5;
        for (int index = 0; index < lines; index++) {
            HubDraw.line(graphics, this.font, this.captionLines.get(index), area.x() + 6, lineY, CrystalTheme.fade(GrimoireDraw.TEXT, alpha));
            lineY += lineHeight;
        }
        HubDraw.isolate(graphics);
    }

    /** The glow the crystal wears: its name in gothic letters and its values under it. */
    private void drawSummary(GuiGraphicsExtractor graphics, Rect area, CrystalAppearance look, float alpha) {
        boolean lit = look.glowActive();
        long key = (long) CrystalVisualConfig.glowStyle().ordinal() << 42 | (lit ? 1L : 0L) << 41 | (long) look.glowPowerPercent << 28
                | (long) look.coreGlowPercent << 14 | look.glowReflectionsPercent;
        if (key != this.summaryKey || area.width() != this.summaryFittedWidth) {
            this.summaryKey = key;
            this.summaryFittedWidth = area.width();
            this.summaryName = lit ? CrystalVisualConfig.glowStyle().label(this.spanish) : s("Sin brillo", "No glow");
            this.summaryValues = lit
                    ? s("Potencia ", "Power ") + look.glowPowerPercent + " · " + s("Núcleo ", "Core ") + look.coreGlowPercent + " · "
                            + s("Reflejos ", "Reflections ") + look.glowReflectionsPercent
                    : s("Se enciende en Brillo", "Turned on under Glow");
            // A little smaller first, then whole items go, before any word is cut.
            String values = this.summaryValues;
            while (this.font.width(values) * 0.8F > area.width() && values.lastIndexOf(" · ") > 0) {
                values = values.substring(0, values.lastIndexOf(" · "));
            }
            this.summaryScale = Math.max(0.8F, Math.min(1.0F, area.width() / (float) Math.max(1, this.font.width(values))));
            this.summaryFitted = HubDraw.fit(this.font, values, (int) (area.width() / this.summaryScale));
        }
        float scale = Math.min((area.height() - 12) / 10.0F, GrimoireDraw.fitScale(this.font, this.summaryName, area.width(), 2.0F));
        GrimoireDraw.gothicCentered(graphics, this.font, this.summaryName, area.centerX(), area.y() + 1, scale,
                CrystalTheme.fade(GrimoireDraw.TITLE, alpha), 0.5F);
        // Scaled about its baseline, so it stays on the same line.
        float scaled = this.summaryScale;
        GrimoireDraw.text(graphics, this.font, this.summaryFitted, area.centerX() - this.font.width(this.summaryFitted) * scaled / 2.0F,
                area.bottom() - 2 - 7 * scaled, scaled, CrystalTheme.fade(GrimoireDraw.MUTED, alpha));
    }

    /**
     * Crystal Practice, under the crystal: obsidian tiles sliding along a dark strip, a sword, and
     * small crystals bobbing at either end. A click opens the practice warning.
     */
    private void drawPractice(GuiGraphicsExtractor graphics, long now, float frame, int mouseX, int mouseY, float alpha, HubSkin skin) {
        Rect rect = this.layout.practice;
        if (alpha <= 0.02F || rect.isEmpty() || this.enemy) {
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
        // Its crimson edge breathes slowly; the pointer kindles it.
        float pulse = HubMotion.breathe(seconds, 2.6D);
        HubDraw.isolate(graphics);
        HubDraw.halo(graphics, box.x(), box.y(), box.width(), box.height(), 0xFFD11F4A, 2, (0.10F + 0.08F * pulse + 0.35F * this.practiceHover) * fade);
        HubDraw.isolate(graphics);
        GrimoireDraw.plate(graphics, box.x(), box.y(), box.width(), box.height(),
                CrystalTheme.lerp(GrimoireDraw.CRIMSON_FILL, 0xFF2C0E19, this.practiceHover),
                CrystalTheme.lerp(GrimoireDraw.CRIMSON, 0xFFB8384F, Math.max(this.practiceHover, pulse * 0.3F)), fade);
        String label = s("Práctica", "Practice");
        int labelColor = CrystalTheme.fade(CrystalTheme.lerp(GrimoireDraw.CRIMSON_TEXT, 0xFFFFFFFF, this.practiceHover), fade);
        float labelScale = GrimoireDraw.gothicFit(box.height(), 1.0F);
        int textWidth = GrimoireDraw.gothicWidth(this.font, label, labelScale);
        int iconSize = Math.min(12, box.height() - 4);
        // The word first: the sword goes when both do not fit, and the plain letters when the gothic ones do not.
        boolean withIcon = iconSize + 4 + textWidth + 6 <= box.width();
        boolean gothic = textWidth + 6 <= box.width();
        if (!gothic) {
            textWidth = this.font.width(label);
        }
        int group = (withIcon ? iconSize + 4 : 0) + textWidth;
        int x = box.centerX() - group / 2;
        if (withIcon) {
            float swing = (float) Math.sin(seconds * 3.0D) * 0.15F * (0.3F + this.practiceHover);
            GrimoireIcons.draw(graphics, GrimoireIcons.Sprite.NETHERITE_SWORD, x, box.y() + (box.height() - iconSize) / 2.0F, iconSize, swing,
                    CrystalTheme.fade(0xFFFFFFFF, fade));
            x += iconSize + 4;
        }
        if (gothic) {
            GrimoireDraw.gothic(graphics, this.font, label, x, GrimoireDraw.gothicY(box.y(), box.height(), labelScale), labelScale, labelColor, 0.0F);
        } else {
            float plainScale = GrimoireDraw.textFit(box.height(), 1.0F);
            GrimoireDraw.text(graphics, this.font, HubDraw.fit(this.font, label, box.width() - 4), x, GrimoireDraw.textY(box.y(), box.height(), plainScale),
                    plainScale, labelColor);
        }
    }

    /** Crystal Practice's banner in its own section: obsidian sliding along a dark strip, a sword, crystals at both ends. */
    private void drawPracticeBanner(GuiGraphicsExtractor graphics, Font font, int x, int y, int width, int height, float alpha) {
        HubSkin skin = HubSkin.current();
        double seconds = System.nanoTime() / 1_000_000_000.0D;
        HubDraw.glass(graphics, x, y, width, height, 0xE0200818, 0xE0100410, skin.hot, alpha);
        graphics.enableScissor(x + 1, y + 1, x + width - 1, y + height - 1);
        int tile = Math.max(8, (height - 2) / 2);
        int slide = (int) Math.floor(seconds * 12.0D % tile);
        for (int row = 0; row < 2; row++) {
            int shift = row == 0 ? slide : tile - slide;
            for (int tileX = x - tile + shift; tileX < x + width; tileX += tile) {
                GrimoireIcons.tile(graphics, GrimoireIcons.Sprite.OBSIDIAN, tileX, y + 1 + row * tile, tile,
                        CrystalTheme.fade(0x55FFFFFF, alpha));
            }
        }
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, CrystalTheme.fade(0x8C12040E, alpha));
        graphics.disableScissor();
        String label = HubDraw.fit(font, s("PRÁCTICA DE CRISTALES", "CRYSTAL PRACTICE"), Math.max(1, width - 50));
        int iconSize = Math.min(14, height - 10);
        int group = iconSize + 5 + font.width(label);
        int left = x + (width - group) / 2;
        int centerY = y + height / 2;
        GrimoireIcons.draw(graphics, GrimoireIcons.Sprite.NETHERITE_SWORD, left, centerY - iconSize / 2.0F, iconSize,
                (float) Math.sin(seconds * 3.0D) * 0.25F, CrystalTheme.fade(0xFFFFFFFF, alpha));
        CrystalUi.label(graphics, font, label, left + iconSize + 5, centerY - 4, CrystalTheme.fade(0xFFFFD5E0, alpha), true);
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
        if (this.phase == Phase.SETTLE) {
            skipSettle(System.nanoTime());
            return true;
        }
        if (this.phase != Phase.MAIN) {
            return true;
        }
        if (this.window != null) {
            // A window answers alone: its controls, or a click beside it, which closes it.
            if (!this.window.closing()) {
                for (AbstractWidget widget : this.window.widgets()) {
                    if (widget.mouseClicked(event, doubleClick)) {
                        setFocused(widget);
                        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
                            setDragging(true);
                        }
                        return true;
                    }
                }
                setFocused(null);
                if (!this.window.contains(mouseX, mouseY)) {
                    closeWindow();
                }
            }
            return true;
        }
        boolean left = event.button() == InputConstants.MOUSE_BUTTON_LEFT;
        if (this.kohsPage) {
            if (left && this.overlays.kohsClicked(this.font, this.layout.kohsArea, mouseX, mouseY)) {
                return true;
            }
            return super.mouseClicked(event, doubleClick);
        }
        if (left) {
            int section = this.sections.at(this.layout.leftRail, mouseX, mouseY);
            if (section >= 0) {
                openSection(this.sections, section);
                return true;
            }
            // The page's reset button: the first click arms, the second resets.
            if (this.page.fullyOpen(System.nanoTime())) {
                SidePanel.ResetClick click = this.page.resetClick(mouseX, mouseY);
                if (click == SidePanel.ResetClick.RESET) {
                    resetSection(this.page);
                    return true;
                }
                if (click == SidePanel.ResetClick.ARMED) {
                    play(SoundEvents.UI_BUTTON_CLICK.value(), 0.8F, 0.25F);
                    return true;
                }
            }
            if (!this.enemy && this.layout.practice.contains(mouseX, mouseY)) {
                this.minecraft.setScreen(PracticeWarningScreen.open(this));
                return true;
            }
        }
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }
        // Mouse buttons by the game's own numbers: 26.3 counts them from 1 (left) and 3 (right).
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT && event.button() != InputConstants.MOUSE_BUTTON_RIGHT) {
            return false;
        }
        boolean soon = soonOpen();
        if (soon && this.overlays.comingSoonClicked(mouseX, mouseY)) {
            return true;
        }
        if (!soon && !this.layout.stage.isEmpty() && this.stage.contains(mouseX, mouseY)) {
            boolean attack = this.minecraft.options.keyAttack.matchesMouse(event) || left;
            this.stage.click(System.nanoTime(), attack, visuals().copy());
            return true;
        }
        return false;
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
        int step = (this.layout.control + this.layout.gap) * 2;
        if (this.window != null) {
            this.window.mouseScrolled(mouseX, mouseY, verticalAmount, step);
            return true;
        }
        if (this.kohsPage) {
            return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        }
        double amount = verticalAmount != 0.0D ? verticalAmount : -horizontalAmount;
        // The wheel over the sections walks through them.
        if (amount != 0.0D && this.layout.leftRail.contains(mouseX, mouseY)) {
            openSection(this.sections, this.sections.selected() + (amount < 0.0D ? 1 : -1));
            return true;
        }
        if (this.converterGrid != null && this.converterGrid.visible
                && this.converterGrid.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)) {
            return true;
        }
        if (this.page.mouseScrolled(mouseX, mouseY, verticalAmount, step)) {
            if (!CrystalVisualConfig.scrollHintDone() && this.page.scrolledByPlayer()) {
                CrystalVisualConfig.setScrollHintDone(true);
                commit();
            }
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
        if (this.phase == Phase.SETTLE) {
            skipSettle(System.nanoTime());
            return true;
        }
        if (this.window != null) {
            if (event.isEscape()) {
                closeWindow();
                return true;
            }
            GuiEventListener focused = getFocused();
            if (focused != null && this.window.widgets().contains(focused)) {
                focused.keyPressed(event);
            }
            return true;
        }
        boolean typing = (this.layerHex != null && this.layerHex.isFocused()) || (this.glowHex != null && this.glowHex.isFocused())
                || (this.converterSearch != null && this.converterSearch.isFocused());
        if (!typing && this.phase == Phase.MAIN && this.minecraft != null && !this.kohsPage && !this.layout.stage.isEmpty()) {
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
