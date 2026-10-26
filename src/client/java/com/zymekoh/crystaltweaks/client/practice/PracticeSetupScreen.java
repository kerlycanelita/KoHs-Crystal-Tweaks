package com.zymekoh.crystaltweaks.client.practice;

import static com.zymekoh.crystaltweaks.client.practice.PracticeIcons.compass;
import static com.zymekoh.crystaltweaks.client.practice.PracticeIcons.item;

import com.zymekoh.crystaltweaks.client.CrystalTheme;
import com.zymekoh.crystaltweaks.client.CrystalUi;
import com.zymekoh.crystaltweaks.client.CrystalVisualConfig;
import com.zymekoh.crystaltweaks.client.FixedGuiScale;
import com.zymekoh.crystaltweaks.client.hub.GrimoireDraw;
import com.zymekoh.crystaltweaks.client.hub.GrimoireFonts;
import com.zymekoh.crystaltweaks.client.hub.GrimoireIcons;
import com.zymekoh.crystaltweaks.client.hub.HubButton;
import com.zymekoh.crystaltweaks.client.hub.HubDraw;
import com.zymekoh.crystaltweaks.client.hub.HubIcons;
import com.zymekoh.crystaltweaks.client.hub.HubLayout.Rect;
import com.zymekoh.crystaltweaks.client.hub.HubMotion;
import com.zymekoh.crystaltweaks.client.hub.HubSkin;
import com.zymekoh.crystaltweaks.client.hub.RowList;
import com.zymekoh.crystaltweaks.practice.BotSkill;
import com.zymekoh.crystaltweaks.practice.KitItem;
import com.zymekoh.crystaltweaks.practice.KitLayout;
import com.zymekoh.crystaltweaks.practice.KitPreset;
import com.zymekoh.crystaltweaks.practice.PracticeDrill;
import com.zymekoh.crystaltweaks.practice.PracticeSession;
import com.zymekoh.crystaltweaks.practice.PracticeSettings;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

/**
 * The Crystal Practice window, in three tabs: the gear (armour, sword, totems, kit), the bot (on or
 * off, difficulty, style and everything it will do) and the world (the netherite flat, the hole
 * arena, a natural meadow in a chosen biome, or a whole normal world). Over the menus a preview sits
 * beside the options when there is room, and the way in is at the bottom.
 *
 * <p>Inside a practice world the same window is the pause menu: Escape brings it up on the right of
 * the screen with the world paused and in view, and what changes here reaches the world at once:
 * the bot comes back as set, the kit is handed out again on request, another ground opens in place
 * of this one. The server the player came from, when there is one, stays connected behind it all,
 * and a light above the buttons says so.</p>
 *
 * <p>Geometry comes from {@link PracticeLayout}, shared by drawing and input; the options are a
 * {@link RowList}, so they scroll smoothly, unfold as a tree under the switch that owns them and only
 * take clicks where they show. Icons are the game's own items and blocks, moving. Changing tab sends
 * an End Crystal flying to the new tab, where it bursts as the new options come in from its side.</p>
 */
public final class PracticeSetupScreen extends Screen {
    private static final long ENTER_NANOS = 460_000_000L;
    private static final long QUICK_ENTER_NANOS = 170_000_000L;
    private static final long FLIGHT_NANOS = 240_000_000L;
    private static final long BURST_NANOS = 260_000_000L;
    private static final long REFUSE_NANOS = 1_600_000_000L;
    private static final int[] PIECES = {PracticeSettings.HEAD, PracticeSettings.CHEST, PracticeSettings.LEGS, PracticeSettings.FEET};
    private static final String[] PIECE_ITEMS = {"helmet", "chestplate", "leggings", "boots"};
    private static final Identifier END_CRYSTAL = HubDraw.vanilla("textures/item/end_crystal.png");

    private enum Tab { GEAR, BOT, WORLD }

    /** The tab shown when the window reopens, for as long as the game runs. */
    private static Tab remembered = Tab.GEAR;
    /** The window's full entrance plays once per game session; later it comes up at once. */
    private static boolean enteredThisSession;

    /** A skill drawn in the list, and where it was drawn this frame, for its tooltip. */
    private static final class SkillRow {
        final BotSkill skill;
        Rect drawn = Rect.EMPTY;

        SkillRow(BotSkill skill) {
            this.skill = skill;
        }
    }

    private final Screen parent;
    private final boolean spanish;
    private final boolean inWorld;
    private final List<SkillRow> skillRows = new ArrayList<>();
    /** What the last copy of the server inventory did, shown under its button; null before one. */
    private String copyNote;
    /** The ping choices, round trip in milliseconds; -1 is the parked server's own. */
    private static final int[] PINGS = {0, 50, 100, 150, 200, 300, -1};
    private final HubButton[] tabButtons = new HubButton[3];
    private PracticeLayout layout;
    /** The page on the cover, placed with the layout. */
    private Rect page = Rect.EMPTY;
    private Tab tab = remembered;
    private Tab listTab;
    private RowList list;
    private long openedAt;
    private long enterNanos;
    private long lastFrame;
    private long tabAt = Long.MIN_VALUE;
    private int tabFrom;
    private long refusedAt;

    public PracticeSetupScreen(Screen parent) {
        this(parent, false);
    }

    public PracticeSetupScreen(Screen parent, boolean inWorld) {
        // The book's own letters, which no resource pack changes.
        super(Minecraft.getInstance(), GrimoireFonts.font(), Component.literal("Crystal Practice"));
        this.parent = parent;
        this.inWorld = inWorld;
        this.spanish = CrystalUi.spanish();
    }

    // ------------------------------------------------------------------------------------------
    // Layout
    // ------------------------------------------------------------------------------------------

    @Override
    protected void init() {
        FixedGuiScale.apply(this, this.minecraft);
        long now = System.nanoTime();
        if (this.openedAt == 0L) {
            this.openedAt = now;
            this.enterNanos = this.inWorld || enteredThisSession ? QUICK_ENTER_NANOS : ENTER_NANOS;
            enteredThisSession = true;
        }
        this.lastFrame = now;
        this.layout = PracticeLayout.fit(this.width, this.height, this.inWorld);
        int cover = this.layout.compact ? 2 : 4;
        Rect panel = this.layout.panel;
        this.page = new Rect(panel.x() + cover, panel.y() + cover, panel.width() - cover * 2, panel.height() - cover * 2);

        Tab[] tabs = Tab.values();
        for (int index = 0; index < tabs.length; index++) {
            Tab target = tabs[index];
            Rect area = this.layout.tab[index];
            HubButton button = addRenderableWidget(new HubButton(area.x(), area.y(), area.width(), area.height(),
                    Component.literal(tabLabel(target)), ignored -> switchTab(target)).icon(tabIcon(target)));
            button.setSelected(target == this.tab);
            this.tabButtons[index] = button;
        }

        Rect[] buttons = this.layout.buttons;
        if (this.inWorld) {
            addFooter(buttons[0], this.spanish ? "Volver al juego" : "Back to game", item("end_crystal", 0.0F), HubButton.Style.PRIMARY,
                    ignored -> onClose(), null);
            addFooter(buttons[1], this.spanish ? "Opciones…" : "Options…", item("comparator", 0.4F), HubButton.Style.NORMAL,
                    ignored -> this.minecraft.setScreen(new OptionsScreen(this, this.minecraft.options, true)), null);
            addFooter(buttons[2], leaveLabel(), item("ender_pearl", 0.8F), HubButton.Style.NORMAL, ignored -> leavePractice(),
                    PracticeHost.parked()
                            ? (this.spanish
                                    ? "Cierra el mundo de práctica y vuelve a " + PracticeHost.serverName() + " donde estabas."
                                    : "Closes the practice world and takes you back to " + PracticeHost.serverName() + " where you were.")
                            : (this.spanish ? "Guarda el mundo de práctica y vuelve al menú principal."
                                    : "Saves the practice world and goes back to the title screen."));
        } else {
            addFooter(buttons[0], this.spanish ? "Volver" : "Back", null, HubButton.Style.NORMAL, ignored -> onClose(), null);
            addFooter(buttons[1], this.spanish ? "Entrar a practicar" : "Enter practice", item("end_crystal", 0.0F),
                    HubButton.Style.PRIMARY, ignored -> enter(), leaveWarning());
        }
        buildList(this.listTab != this.tab, 1);
    }

    private void addFooter(Rect area, String label, HubButton.Icon icon, HubButton.Style style, Consumer<HubButton> action,
            String tooltip) {
        HubButton button = addRenderableWidget(new HubButton(area.x(), area.y(), area.width(), area.height(),
                Component.literal(label), action).style(style));
        if (icon != null) {
            button.icon(icon);
        }
        if (tooltip != null) {
            button.setTooltip(Tooltip.create(Component.literal(tooltip)));
        }
    }

    /**
     * The current tab's options, built again: on a change of tab they come in from that tab's side;
     * after a change inside the tab they are simply there, at the same scroll.
     */
    private void buildList(boolean animate, int direction) {
        int keep = this.list != null && this.listTab == this.tab ? this.list.scrollTarget() : 0;
        if (this.list != null) {
            for (AbstractWidget widget : this.list.widgets()) {
                removeWidget(widget);
            }
        }
        this.skillRows.clear();
        RowList next = new RowList(this.layout.content.width(), this.layout.gap);
        switch (this.tab) {
            case GEAR -> buildGear(next);
            case BOT -> buildBot(next);
            case WORLD -> buildWorld(next);
        }
        for (AbstractWidget widget : next.widgets()) {
            widget.visible = false;
            addWidget(widget);
        }
        next.setViewport(this.layout.content);
        next.scrollTo(keep, true);
        long now = System.nanoTime();
        next.reveal(animate ? now : now - 2_000_000_000L, direction);
        this.list = next;
        this.listTab = this.tab;
    }

    // ------------------------------------------------------------------------------------------
    // The three tabs
    // ------------------------------------------------------------------------------------------

    private void buildGear(RowList list) {
        PracticeSettings settings = CrystalVisualConfig.practice();
        String material = settings.armor.name().toLowerCase(Locale.ROOT);
        list.heading(this.spanish ? "Armadura" : "Armour", item("netherite_chestplate", 0.1F));
        PracticeSettings.Armor[] armors = PracticeSettings.Armor.values();
        List<HubButton> chips = new ArrayList<>();
        for (int index = 0; index < armors.length; index++) {
            PracticeSettings.Armor armor = armors[index];
            HubButton chip = chip(armor.label(this.spanish), item(armor.name().toLowerCase(Locale.ROOT) + "_chestplate", index * 0.3F),
                    ignored -> selectArmor(armor));
            chip.setSelected(settings.armor == armor);
            chips.add(chip);
        }
        chipRows(list, chips, 0);
        list.note(this.font, this.spanish
                ? "En cada pieza: Protección IV, Irrompibilidad III y Reparación."
                : "On every piece: Protection IV, Unbreaking III and Mending.", 0);

        list.heading(this.spanish ? "Protección contra explosiones IV" : "Blast Protection IV", item("enchanted_book", 0.3F));
        list.row(10).paint((graphics, font, x, y, width, height, alpha) -> {
            boolean refused = System.nanoTime() - this.refusedAt < REFUSE_NANOS;
            int selected = Integer.bitCount(PracticeSettings.sanitize(CrystalVisualConfig.practiceBlastPieces()));
            String text = refused
                    ? (this.spanish ? "Máximo 2 piezas" : "At most 2 pieces")
                    : (this.spanish ? "En lugar de Protección IV: " : "Instead of Protection IV: ") + selected + "/2";
            CrystalUi.label(graphics, font, HubDraw.fit(font, text, width), x, y + 1,
                    CrystalTheme.fade(refused ? CrystalTheme.DANGER : HubSkin.current().muted, alpha));
        }, 0, list.width());
        String[] pieces = this.spanish
                ? new String[] {"Casco", "Peto", "Pantalones", "Botas"}
                : new String[] {"Helmet", "Chest", "Leggings", "Boots"};
        chips = new ArrayList<>();
        for (int index = 0; index < PIECES.length; index++) {
            int piece = PIECES[index];
            HubButton chip = chip(pieces[index], item(material + "_" + PIECE_ITEMS[index], index * 0.25F), ignored -> toggleBlast(piece));
            chip.setSelected(settings.blast(piece));
            chip.setTooltip(Tooltip.create(Component.literal(this.spanish
                    ? "Protección contra explosiones IV en lugar de Protección IV. Máximo 2 piezas."
                    : "Blast Protection IV instead of Protection IV. At most 2 pieces.")));
            chips.add(chip);
        }
        chipRows(list, chips, 0);

        list.heading(this.spanish ? "Espada · Empuje" : "Sword · Knockback", item("netherite_sword", 0.5F));
        chips = new ArrayList<>();
        for (int level = PracticeSettings.MIN_KNOCKBACK; level <= PracticeSettings.MAX_KNOCKBACK; level++) {
            int chosen = level;
            HubButton chip = chip(PracticeSettings.roman(level), item("netherite_sword", level * 0.4F), ignored -> selectKnockback(chosen));
            chip.setSelected(settings.knockback == level);
            chip.setTooltip(Tooltip.create(Component.literal(level == 1
                    ? (this.spanish
                            ? "Empuje I: el de los kits de crystal PvP. Levanta al rival lo justo para un hit-crystal sin mandarlo lejos."
                            : "Knockback I: what crystal PvP kits carry. Lifts the opponent just enough for a hit-crystal without sending them away.")
                    : (this.spanish
                            ? "Empuje II: aleja mucho más. Útil para separar, peor para encadenar cristales."
                            : "Knockback II: sends them much further. Good for spacing, worse for chaining crystals."))));
            chips.add(chip);
        }
        chipRows(list, chips, 0);

        list.heading(this.spanish ? "Tótems" : "Totems", item("totem_of_undying", 0.7F));
        int row = this.layout.row;
        list.row(row);
        int step = row + 4;
        HubButton fewer = list.cell(new HubButton(0, 0, 0, 0, Component.literal("–"), ignored -> totems(false)), 0, step);
        HubButton more = list.cell(new HubButton(0, 0, 0, 0, Component.literal("+"), ignored -> totems(true)), list.width() - step, step);
        fewer.setTooltip(Tooltip.create(Component.literal(totemAdvice())));
        more.setTooltip(Tooltip.create(Component.literal(totemAdvice())));
        list.paint((graphics, font, x, y, width, height, alpha) -> {
            String count = totemCount();
            int iconSize = Math.min(12, height - 2);
            int group = iconSize + 4 + font.width(count);
            int left = x + (width - group) / 2;
            double seconds = System.nanoTime() / 1_000_000_000.0D;
            float spin = (float) Math.sin(seconds * 2.2D) * 0.12F;
            GrimoireIcons.drawItem(graphics, TOTEM, left + iconSize / 2.0F, y + height / 2.0F, iconSize, spin,
                    CrystalTheme.fade(0xFFFFFFFF, alpha));
            CrystalUi.label(graphics, font, count, left + iconSize + 4, y + (height - 8) / 2,
                    CrystalTheme.fade(HubSkin.current().accentBright, alpha));
        }, step + 4, list.width() - step * 2 - 8);

        list.heading("Kit", item("bundle", 0.9F));
        KitPreset preset = settings.preset;
        list.row(row);
        list.cell(new HubButton(0, 0, 0, 0, Component.literal("<"), ignored -> selectPreset(preset.previous())), 0, step);
        HubButton name = list.cell(new HubButton(0, 0, 0, 0, Component.literal(preset.label(this.spanish)
                        + (CrystalVisualConfig.practiceKitCustomized(preset) ? (this.spanish ? " · tuyo" : " · yours") : "")),
                ignored -> selectPreset(preset.next())).icon(item("bundle", 0.2F)), step + 3, list.width() - step * 2 - 6);
        name.setSelected(true);
        name.setTooltip(Tooltip.create(Component.literal(preset.note(this.spanish))));
        list.cell(new HubButton(0, 0, 0, 0, Component.literal(">"), ignored -> selectPreset(preset.next())), list.width() - step, step);
        list.row(row);
        list.full(new HubButton(0, 0, 0, 0, Component.literal(this.spanish ? "Ordenar inventario…" : "Arrange inventory…"),
                ignored -> this.minecraft.setScreen(new PracticeKitScreen(this, CrystalVisualConfig.practice().preset)))
                .icon(item("chest_minecart", 0.6F)))
                .setTooltip(Tooltip.create(Component.literal(this.spanish
                        ? "Abre el kit en un inventario para ordenarlo a tu gusto, con Guardar y Restablecer."
                        : "Opens the kit in an inventory to arrange it your way, with Save and Reset.")));
        LocalPlayer source = KitCopy.source(this.minecraft, this.inWorld);
        if (source != null) {
            list.row(row);
            list.full(new HubButton(0, 0, 0, 0, Component.literal(this.spanish ? "Copiar mi inventario del servidor" : "Copy my server inventory"),
                    ignored -> copyServerInventory(source)).icon(item("hopper", 0.4F)))
                    .setTooltip(Tooltip.create(Component.literal(this.spanish
                            ? "Pasa tu inventario del servidor a este kit, cada cosa en su ranura: cristales, obsidiana, anclas, piedra luminosa, tótems, manzanas, perlas, botellas, espada y pico. Lo demás se queda fuera; la armadura es la elegida arriba."
                            : "Turns your server inventory into this kit, each thing in its slot: crystals, obsidian, anchors, glowstone, totems, apples, pearls, bottles, sword and pickaxe. Anything else is left out; the armour is the one chosen above.")));
            if (this.copyNote != null) {
                list.note(this.font, this.copyNote, 0);
            }
        }
        if (this.inWorld) {
            list.row(row);
            list.full(new HubButton(0, 0, 0, 0, Component.literal(this.spanish ? "Dar el kit ahora (/kit)" : "Hand out the kit now (/kit)"),
                    ignored -> command("kit")).icon(item("totem_of_undying", 0.9F)).style(HubButton.Style.PRIMARY))
                    .setTooltip(Tooltip.create(Component.literal(this.spanish
                            ? "Te equipa a ti y al bot con lo elegido aquí, sin esperar a la siguiente ronda."
                            : "Equips you and the bot with what is chosen here, without waiting for the next round.")));
        }
        list.space(1);
        list.note(this.font, preset.note(this.spanish), 0);
    }

    private void buildBot(RowList list) {
        PracticeSettings settings = CrystalVisualConfig.practice();
        int row = this.layout.row;
        int indent = list.width() >= 170 ? 10 : 6;
        list.row(row);
        list.full(new HubButton(0, 0, 0, 0, Component.literal(this.spanish ? "Generar bot" : "Spawn bot"), ignored -> toggleBot())
                .switchOf(CrystalVisualConfig::practiceBot).icon(item("netherite_sword", 0.0F)))
                .setTooltip(Tooltip.create(Component.literal(this.spanish
                        ? "Un rival con tu mismo kit: armadura, espada, tótems, manzanas y perlas."
                        : "An opponent with your same kit: armour, sword, totems, apples and pearls.")));
        if (!settings.bot) {
            list.space(2);
            list.note(this.font, this.spanish
                    ? "Sin bot: practica colocar y romper cristales y anclas a tu ritmo."
                    : "No bot: practise placing and breaking crystals and anchors at your own pace.", 0);
            addTraining(list, false);
            return;
        }
        // Everything below belongs to the switch: it unfolds as a branch of it.
        list.note(this.font, this.spanish ? "Dificultad" : "Difficulty", indent);
        String[] swords = {"wooden_sword", "iron_sword", "diamond_sword", "netherite_sword"};
        PracticeSettings.Difficulty[] difficulties = PracticeSettings.Difficulty.values();
        List<HubButton> chips = new ArrayList<>();
        for (int index = 0; index < difficulties.length; index++) {
            PracticeSettings.Difficulty difficulty = difficulties[index];
            HubButton chip = chip(difficulty.label(this.spanish), item(swords[Math.min(index, swords.length - 1)], index * 0.35F),
                    ignored -> selectDifficulty(difficulty));
            chip.setSelected(settings.difficulty == difficulty);
            chip.setTooltip(Tooltip.create(Component.literal(timing(difficulty))));
            chips.add(chip);
        }
        chipRows(list, chips, indent);
        list.note(this.font, this.spanish ? "Estilo" : "Style", indent);
        String[] emblems = {"ender_pearl", "fire_charge", "golden_apple"};
        PracticeSettings.BotStyle[] styles = PracticeSettings.BotStyle.values();
        chips = new ArrayList<>();
        for (int index = 0; index < styles.length; index++) {
            PracticeSettings.BotStyle style = styles[index];
            HubButton chip = chip(style.label(this.spanish), item(emblems[Math.min(index, emblems.length - 1)], index * 0.45F),
                    ignored -> selectStyle(style));
            chip.setSelected(settings.style == style);
            chip.setTooltip(Tooltip.create(Component.literal(style.note(this.spanish))));
            chips.add(chip);
        }
        chipRows(list, chips, indent);
        if (this.inWorld) {
            list.row(row, indent);
            list.full(new HubButton(0, 0, 0, 0, Component.literal(this.spanish ? "Traer el bot aquí (/bot here)" : "Bring the bot here (/bot here)"),
                    ignored -> command("bot here")).icon(item("lead", 0.3F)));
        }
        list.space(1);
        list.note(this.font, settings.style.note(this.spanish), indent);
        list.note(this.font, timing(settings.difficulty), indent);
        if (this.layout.preview) {
            // The preview shows what it will do; the list keeps to the settings.
            addTraining(list, true);
            return;
        }
        list.heading(this.spanish ? "Lo que hará" : "What it will do", item("armor_stand", 1.1F));
        addSkills(list, BotSkill.of(settings.difficulty, settings.style), 0);
        addTraining(list, true);
    }

    /** Exercises with a goal and a clock, and what a round leaves behind: its report, its map, and the damage map. */
    private void addTraining(RowList list, boolean withBot) {
        int row = this.layout.row;
        int indent = list.width() >= 170 ? 10 : 6;
        list.heading(this.spanish ? "Entrenamiento" : "Training", HubIcons.cube("target_top", "target_side", 0.4F));
        PracticeDrill drill = PracticeDrill.parse(CrystalVisualConfig.practiceDrill());
        list.row(row);
        list.full(new HubButton(0, 0, 0, 0, Component.literal((this.spanish ? "Ejercicio: " : "Drill: ") + drill.label(this.spanish) + "  ›"),
                ignored -> {
                    CrystalVisualConfig.setPracticeDrill(drill.next().name());
                    CrystalVisualConfig.save();
                    buildList(false, 1);
                }).icon(HubIcons.clock(0.3F)))
                .setTooltip(Tooltip.create(Component.literal(this.spanish
                        ? "Un reto con meta y reloj encima de la práctica: 20 cristales lo más rápido posible, 5 tótems del bot, o aguantar 60 segundos. El progreso sale en la barra de acción y el resultado en el chat."
                        : "A challenge with a goal and a clock on top of practice: 20 crystals as fast as you can, 5 of the bot's totems, or holding out 60 seconds. Progress shows on the action bar and the result in chat.")));
        if (drill != PracticeDrill.NONE) {
            list.note(this.font, drill.note(this.spanish), indent);
            if (!withBot && drill != PracticeDrill.SPEED) {
                list.note(this.font, this.spanish ? "Este ejercicio necesita el bot." : "This drill needs the bot.", indent);
            }
        }
        list.row(row);
        list.full(new HubButton(0, 0, 0, 0, Component.literal(this.spanish ? "Reporte de cada ronda" : "Report after each round"),
                ignored -> toggle(CrystalVisualConfig::setPracticeRoundReport, !CrystalVisualConfig.practiceRoundReport()))
                .switchOf(CrystalVisualConfig::practiceRoundReport).icon(item("writable_book", 0.3F)))
                .setTooltip(Tooltip.create(Component.literal(this.spanish
                        ? "Al terminar cada ronda, en el chat: cuánto duró, cristales puestos y rotos, el tiempo de poner a romper, el daño que hiciste y recibiste, la mejor explosión y los tótems de cada lado."
                        : "When each round ends, in chat: how long it took, crystals placed and broken, the time from placing to breaking, the damage dealt and taken, the best blast and the totems on each side.")));
        list.row(row);
        list.full(new HubButton(0, 0, 0, 0, Component.literal(this.spanish ? "Mapa de la ronda" : "Round map"),
                ignored -> toggle(CrystalVisualConfig::setPracticeRoundMap, !CrystalVisualConfig.practiceRoundMap()))
                .switchOf(CrystalVisualConfig::practiceRoundMap).icon(item("filled_map", 0.6F)))
                .setTooltip(Tooltip.create(Component.literal(this.spanish
                        ? "Durante 10 segundos tras cada ronda, cada explosión que hizo daño queda marcada donde estalló: violeta si dañó al bot, carmesí si te dañó a ti, más grande cuanto más daño."
                        : "For 10 seconds after each round, every blast that did damage stays marked where it went off: violet where it hurt the bot, crimson where it hurt you, bigger the more it did.")));
        list.row(row);
        list.full(new HubButton(0, 0, 0, 0, Component.literal(this.spanish ? "Mapa de daño" : "Damage map"),
                ignored -> toggle(CrystalVisualConfig::setPracticeDamageMap, !CrystalVisualConfig.practiceDamageMap()))
                .switchOf(CrystalVisualConfig::practiceDamageMap).icon(item("fire_charge", 0.9F)))
                .setTooltip(Tooltip.create(Component.literal(this.spanish
                        ? "Solo en la práctica: cada obsidiana a tu alcance donde cabe un cristal se ilumina según lo que su explosión le haría al bot. Violeta: vale la pena. Ámbar: te daña casi igual. Carmesí: te daña más o te mataría. La mejor lleva una chispa encima."
                        : "Practice only: every obsidian within your reach where a crystal fits lights up by what its blast would do to the bot. Violet: worth it. Amber: it hurts you nearly as much. Crimson: it hurts you more or would kill you. The best one has a spark above it.")));
        list.note(this.font, this.spanish
                ? "El mapa de daño es una herramienta de entrenamiento: nunca existe en un servidor real."
                : "The damage map is a training tool: it never exists on a real server.", 0);
    }

    /** The server's timing in practice: its ping, and going back by itself when its match starts. */
    private void addTiming(RowList list) {
        int row = this.layout.row;
        list.heading(this.spanish ? "Ritmo del servidor" : "Server timing", HubIcons.clock(0.6F));
        int ping = CrystalVisualConfig.practicePing();
        list.row(row);
        list.full(new HubButton(0, 0, 0, 0, Component.literal(pingLabel(ping)), ignored -> {
                    CrystalVisualConfig.setPracticePing(nextPing(ping));
                    CrystalVisualConfig.save();
                    buildList(false, 1);
                }).icon(item("repeater", 0.2F)))
                .setTooltip(Tooltip.create(Component.literal(this.spanish
                        ? "Practica con el retraso de un servidor: cada paquete entre el mundo de práctica y su servidor espera la mitad del ping en cada sentido, así un cristal aparece y se rompe una ida y vuelta tarde, como en línea. «El del servidor» copia el ping del servidor que dejaste detrás."
                        : "Practise with a server's delay: every packet between the practice world and its server waits half the ping each way, so a crystal appears and breaks a round trip late, as online. \"The server's\" copies the ping of the server you left behind.")));
        if (PracticeHost.OVER_SERVERS) {
            list.row(row);
            list.full(new HubButton(0, 0, 0, 0, Component.literal(this.spanish ? "Volver al empezar la partida" : "Back when the match starts"),
                    ignored -> toggle(CrystalVisualConfig::setPracticeAutoReturn, !CrystalVisualConfig.practiceAutoReturn()))
                    .switchOf(CrystalVisualConfig::practiceAutoReturn).icon(item("ender_pearl", 0.5F)))
                    .setTooltip(Tooltip.create(Component.literal(this.spanish
                            ? "Cuando el servidor que dejaste detrás empieza tu partida, con un título como «¡Pelea!», «Go» o una cuenta atrás, cierra la práctica y te devuelve a él."
                            : "When the server you left behind starts your match, with a title such as \"Fight!\", \"Go\" or a countdown, practice closes and takes you back to it.")));
        }
    }

    private String pingLabel(int ping) {
        if (ping == 0) {
            return this.spanish ? "Ping: sin retraso" : "Ping: none";
        }
        if (ping < 0) {
            int server = PracticeHost.parkedPing();
            return (this.spanish ? "Ping: el del servidor" : "Ping: the server's") + (server > 0 ? " (" + server + " ms)" : "");
        }
        return "Ping: " + ping + " ms";
    }

    private static int nextPing(int ping) {
        for (int index = 0; index < PINGS.length; index++) {
            if (PINGS[index] == ping) {
                return PINGS[(index + 1) % PINGS.length];
            }
        }
        return PINGS[0];
    }

    /** The server inventory into the kit of the chosen preset, saved as the player's own. */
    private void copyServerInventory(LocalPlayer source) {
        KitPreset preset = CrystalVisualConfig.practice().preset;
        KitLayout layout = KitCopy.from(source.getInventory());
        int left = KitCopy.leftOut(source.getInventory());
        if (layout.isEmpty()) {
            this.copyNote = this.spanish ? "Nada que copiar: tu inventario no lleva nada de un kit de cristales."
                    : "Nothing to copy: your inventory holds nothing a crystal kit does.";
        } else {
            CrystalVisualConfig.setPracticeKit(preset, layout);
            CrystalVisualConfig.save();
            PracticeSession.settingsChanged();
            this.copyNote = this.spanish
                    ? "Copiado al kit " + preset.label(true) + (left > 0 ? "; " + left + " cosas no caben en un kit." : ".")
                    : "Copied into the " + preset.label(false) + " kit" + (left > 0 ? "; " + left + " things do not fit a kit." : ".");
        }
        buildList(false, 1);
    }

    /** One row per technique, a dot in its difficulty's colour; hover shows how it works. */
    private void addSkills(RowList list, List<BotSkill> skills, int indent) {
        for (BotSkill skill : skills) {
            SkillRow entry = new SkillRow(skill);
            this.skillRows.add(entry);
            list.row(10, indent).paint((graphics, font, x, y, width, height, alpha) -> {
                entry.drawn = new Rect(x, y, width, height);
                int color = skillColor(skill);
                skillIcon(skill).draw(graphics, x, y, 9, CrystalTheme.fade(0xFFFFFFFF, alpha));
                CrystalUi.label(graphics, font, HubDraw.fit(font, skill.label(this.spanish), width - 13), x + 12, y + 1,
                        CrystalTheme.fade(CrystalTheme.lerp(color, 0xFFFFFFFF, 0.25F), alpha));
            }, 0, list.width() - indent);
        }
    }

    private void buildWorld(RowList list) {
        PracticeSettings settings = CrystalVisualConfig.practice();
        int row = this.layout.row;
        int indent = list.width() >= 170 ? 10 : 6;
        list.heading(this.spanish ? "Tipo de mundo" : "World type", item("map", 1.3F));
        PracticeSettings.WorldType[] types = PracticeSettings.WorldType.values();
        String[] names = this.spanish ? new String[] {"Plano", "Hoyos", "Natural", "Normal"} : new String[] {"Flat", "Holes", "Natural", "Normal"};
        List<HubButton> chips = new ArrayList<>();
        for (int index = 0; index < types.length; index++) {
            PracticeSettings.WorldType type = types[index];
            HubButton chip = chip(names[Math.min(index, names.length - 1)], groundIcon(type, null, index * 0.3F), ignored -> selectWorld(type));
            chip.setSelected(settings.worldType == type);
            chip.setTooltip(Tooltip.create(Component.literal(type.label(this.spanish) + ": " + worldDescription(type))));
            chips.add(chip);
        }
        chipRows(list, chips, 0);
        list.note(this.font, worldDescription(settings.worldType), 0);
        if (settings.worldType == PracticeSettings.WorldType.NATURAL) {
            list.note(this.font, this.spanish ? "Bioma" : "Biome", indent);
            chips = new ArrayList<>();
            PracticeSettings.Biome[] biomes = PracticeSettings.Biome.values();
            for (int index = 0; index < biomes.length; index++) {
                PracticeSettings.Biome biome = biomes[index];
                HubButton chip = chip(biome.label(this.spanish), groundIcon(PracticeSettings.WorldType.NATURAL, biome, index * 0.2F),
                        ignored -> selectBiome(biome));
                chip.setSelected(settings.biome == biome);
                chips.add(chip);
            }
            chipRows(list, chips, indent);
            list.note(this.font, this.spanish
                    ? "Cada bioma es un mundo propio: casi plano, pocos árboles y sin cuevas."
                    : "Each biome is a world of its own: almost flat, few trees and no caves.", indent);
        }
        String chosen = PracticeSession.levelId(settings.worldType, settings.biome);
        if (this.inWorld && !chosen.equals(PracticeSession.runningFolder())) {
            // Right under the choice it opens: this ground is another world.
            list.row(row);
            list.full(new HubButton(0, 0, 0, 0, Component.literal(this.spanish ? "Ir a este mundo" : "Go to this world"),
                    ignored -> PracticeWorld.enter(this.minecraft, null)).icon(item("ender_eye", 0.0F)).style(HubButton.Style.PRIMARY))
                    .setTooltip(Tooltip.create(Component.literal(this.spanish
                            ? "Cierra este mundo de práctica y abre el elegido. El servidor sigue conectado detrás."
                            : "Closes this practice world and opens the chosen one. The server stays connected behind it.")));
        }
        list.space(1);
        list.note(this.font, settings.worldType == PracticeSettings.WorldType.SURVIVAL
                ? (this.spanish
                        ? "No hay arena: cada ronda empieza donde estés, y /rtp te lleva a un sitio al azar del mundo."
                        : "There is no arena: every round starts where you are, and /rtp takes you somewhere random in the world.")
                : (this.spanish
                        ? "La arena se reconstruye al empezar cada ronda: cada muerte deja el terreno como nuevo."
                        : "The arena is rebuilt at the start of every round: each death leaves the ground as new."), 0);
        if (this.inWorld) {
            addThisWorld(list);
        }
        addTiming(list);
        if (PracticeHost.OVER_SERVERS) {
            addServerWindow(list, indent);
        }
    }

    /** In a practice world: somewhere random, back to the start, and the commands that do the same. */
    private void addThisWorld(RowList list) {
        int row = this.layout.row;
        list.heading(this.spanish ? "En este mundo" : "In this world", compass());
        list.row(row);
        int half = (list.width() - 4) / 2;
        list.cell(new HubButton(0, 0, 0, 0, Component.literal(this.spanish ? "Al azar (/rtp)" : "Random (/rtp)"), ignored -> command("rtp"))
                .icon(item("chorus_fruit", 0.5F)), 0, half)
                .setTooltip(Tooltip.create(Component.literal(this.spanish
                        ? "Te lleva a un sitio al azar del mundo, con una ronda nueva allí."
                        : "Takes you somewhere random in the world, with a new round there.")));
        list.cell(new HubButton(0, 0, 0, 0, Component.literal(this.spanish ? "Inicio (/spawn)" : "Home (/spawn)"), ignored -> command("spawn"))
                .icon(compass()), half + 4, list.width() - half - 4)
                .setTooltip(Tooltip.create(Component.literal(this.spanish
                        ? "La ronda vuelve a donde empezó."
                        : "The round goes back to where it started.")));
        list.space(1);
        list.note(this.font, this.spanish
                ? "En el chat: /rtp, /spawn, /kit y /bot (on, off, here, smart, aggressive, peaceful, easy, normal, hard, extreme)."
                : "In chat: /rtp, /spawn, /kit and /bot (on, off, here, smart, aggressive, peaceful, easy, normal, hard, extreme).", 0);
    }

    /**
     * The window that shows the server behind the practice world, in the top-left corner: whether it
     * shows, its live view of the server's world, and its chime.
     */
    private void addServerWindow(RowList list, int indent) {
        int row = this.layout.row;
        list.heading(this.spanish ? "Ventana del servidor" : "Server window", item("spyglass", 1.5F));
        list.row(row);
        list.full(new HubButton(0, 0, 0, 0, Component.literal(this.spanish ? "Mostrar arriba a la izquierda" : "Show in the top-left corner"),
                ignored -> toggle(CrystalVisualConfig::setPracticeServerWindow, !CrystalVisualConfig.practiceServerWindow()))
                .switchOf(CrystalVisualConfig::practiceServerWindow).icon(item("spyglass", 0.2F)))
                .setTooltip(Tooltip.create(Component.literal(this.spanish
                        ? "Practicando sobre un servidor, una ventana con su chat, sus títulos, sus barras, su marcador y su mundo en vivo: para ver cuándo te toca, como en una cola de PvP."
                        : "While practising over a server, a window with its chat, titles, bars, sidebar and its world live: to see when your turn comes, as in a PvP queue.")));
        if (!CrystalVisualConfig.practiceServerWindow()) {
            return;
        }
        String unavailable = ServerView.unavailableReason(this.spanish);
        list.row(row, indent);
        HubButton live = list.full(new HubButton(0, 0, 0, 0, Component.literal(this.spanish ? "Mundo en vivo" : "Live world"),
                ignored -> toggle(CrystalVisualConfig::setPracticeServerView, !CrystalVisualConfig.practiceServerView()))
                .switchOf(() -> unavailable == null && CrystalVisualConfig.practiceServerView()).icon(item("ender_eye", 0.6F)));
        live.active = unavailable == null;
        live.setTooltip(Tooltip.create(Component.literal(unavailable != null ? unavailable : this.spanish
                ? "Dibuja el mundo del servidor desde los ojos de tu jugador, 15 veces por segundo. Es un segundo mundo a la vez: más carga para la gráfica."
                : "Draws the server's world from your player's eyes, 15 times a second. It is a second world at once: more load on the graphics card.")));
        list.row(row, indent);
        list.full(new HubButton(0, 0, 0, 0, Component.literal(this.spanish ? "Sonido de aviso" : "Alert chime"),
                ignored -> toggle(CrystalVisualConfig::setPracticeServerAlerts, !CrystalVisualConfig.practiceServerAlerts()))
                .switchOf(CrystalVisualConfig::practiceServerAlerts).icon(item("bell", 0.9F)))
                .setTooltip(Tooltip.create(Component.literal(this.spanish
                        ? "Suena cuando pasa algo en el servidor: te teletransportan, sale un título, te abre un menú, te hacen daño o te nombran en el chat."
                        : "Sounds when something happens on the server: you are teleported, a title shows, it opens a menu, you take damage or someone says your name.")));
        list.note(this.font, unavailable != null ? unavailable : this.spanish
                ? "Con Sodium o Iris la vista en vivo no funciona y se queda apagada; lo demás de la ventana sí."
                : "With Sodium or Iris the live view does not work and stays off; the rest of the window does.", indent);
    }

    private void toggle(Consumer<Boolean> setter, boolean value) {
        setter.accept(value);
        CrystalVisualConfig.save();
        buildList(false, 1);
    }

    /** A chip: a small button with a moving icon, made to sit in a row of its kind. */
    private static HubButton chip(String label, HubButton.Icon icon, Consumer<HubButton> action) {
        return new HubButton(0, 0, 0, 0, Component.literal(label), action).icon(icon);
    }

    /**
     * Lays chips out in rows: all of them in one row when every label fits its share, else two to a
     * row, else one; set in by {@code indent} under the switch they belong to.
     */
    private void chipRows(RowList list, List<HubButton> chips, int indent) {
        int width = list.width() - indent;
        int widest = 0;
        for (HubButton chip : chips) {
            widest = Math.max(widest, this.font.width(chip.getMessage().getString()) + 26);
        }
        int perRow = chips.size();
        while (perRow > 1 && (width - 3 * (perRow - 1)) / perRow < widest) {
            perRow = perRow > 2 ? (perRow + 1) / 2 : 1;
        }
        int chipWidth = (width - 3 * (perRow - 1)) / perRow;
        for (int index = 0; index < chips.size(); index++) {
            if (index % perRow == 0) {
                list.row(this.layout.row, indent);
            }
            int column = index % perRow;
            int x = column * (chipWidth + 3);
            int cellWidth = column == perRow - 1 ? width - x : chipWidth;
            list.cell(chips.get(index), x, cellWidth);
        }
    }

    // ------------------------------------------------------------------------------------------
    // Icons: the game's own items and blocks, moving
    // ------------------------------------------------------------------------------------------

    /** The ground of a world as a small block that rocks: netherite, obsidian, grass, or the compass. */
    private static HubButton.Icon groundIcon(PracticeSettings.WorldType type, PracticeSettings.Biome biome, float phase) {
        if (type == PracticeSettings.WorldType.SURVIVAL) {
            return compass();
        }
        String[] ground = groundTextures(type, biome);
        GrimoireIcons.Art top = GrimoireIcons.art(topOf(ground[0]), GrimoireIcons.Kind.BLOCK);
        GrimoireIcons.Art side = GrimoireIcons.art(ground[0], GrimoireIcons.Kind.BLOCK);
        return (graphics, x, y, size, color) -> {
            double seconds = System.nanoTime() / 1_000_000_000.0D + phase * 3.0D;
            float bob = (float) Math.sin(seconds * 2.2D) * 0.6F;
            GrimoireIcons.drawCube(graphics, top, side, x + size / 2.0F, y + size / 2.0F + bob, size * 0.62F,
                    ((color >>> 24) & 255) / 255.0F, 0xFFFFFFFF);
        };
    }

    /** The top face of a block whose side texture is given: grass and podzol have their own tops. */
    private static String topOf(String side) {
        return switch (side) {
            case "block/grass_block_side" -> "block/grass_block_top";
            case "block/grass_block_snow" -> "block/snow";
            case "block/podzol_side" -> "block/podzol_top";
            default -> side;
        };
    }

    private HubButton.Icon tabIcon(Tab target) {
        return switch (target) {
            case GEAR -> item("netherite_chestplate", 0.0F);
            case BOT -> item("netherite_sword", 0.5F);
            case WORLD -> (graphics, x, y, size, color) -> {
                PracticeSettings settings = CrystalVisualConfig.practice();
                groundIcon(settings.worldType, settings.biome, 1.0F).draw(graphics, x, y, size, color);
            };
        };
    }

    private String tabLabel(Tab target) {
        return switch (target) {
            case GEAR -> this.spanish ? "Equipo" : "Gear";
            case BOT -> "Bot";
            case WORLD -> this.spanish ? "Mundo" : "World";
        };
    }

    // ------------------------------------------------------------------------------------------
    // Words
    // ------------------------------------------------------------------------------------------

    private String worldDescription(PracticeSettings.WorldType type) {
        return switch (type) {
            case FLAT -> this.spanish
                    ? "Piso de bloques de netherite: los cristales solo van sobre la obsidiana que pongas, como en los flats de los tests."
                    : "A floor of netherite blocks: crystals only go on obsidian you place, as on the flats of tier tests.";
            case HOLES -> this.spanish
                    ? "Piso de obsidiana lleno de hoyos con fondo de bedrock y algunos escalones: el terreno de las peleas en hoyos."
                    : "An obsidian floor full of bedrock-bottomed holes and a few steps: the ground of hole fights.";
            case NATURAL -> this.spanish
                    ? "Una pradera casi plana generada en el bioma que elijas."
                    : "An almost flat meadow in the biome you choose.";
            case SURVIVAL -> this.spanish
                    ? "Un mundo entero como lo genera Minecraft: montañas, cuevas, agua y estructuras, para pelear donde caigas."
                    : "A whole world as Minecraft generates it: mountains, caves, water and structures, to fight wherever you land.";
        };
    }

    private String timing(PracticeSettings.Difficulty difficulty) {
        int reaction = difficulty.reaction * 50;
        int step = difficulty.step * 50;
        return this.spanish
                ? "Reacciona y recoloca tótems en " + reaction + " ms; un paso de combo cada " + step + " ms."
                : "Reacts and re-equips totems in " + reaction + " ms; one combo step every " + step + " ms.";
    }

    private String totemAdvice() {
        return this.spanish
                ? "Cuántos tótems llevas tú, y el bot los mismos. Cada comunidad de tests fija su regla en su Discord; MCTiers Vanilla deja traer tu propio kit. «Lleno» ocupa cada hueco libre."
                : "How many totems you carry, and the bot the same. Each testing community sets its rule on its Discord; MCTiers Vanilla lets you bring your own kit. \"Full\" fills every free slot.";
    }

    private String totemCount() {
        KitLayout kit = CrystalVisualConfig.practiceKit(CrystalVisualConfig.practice().preset);
        return kit.full() ? (this.spanish ? "Lleno · " : "Full · ") + kit.totems() : Integer.toString(kit.totems());
    }

    /** Each technique as the game's own thing, alive, where a dot used to be. */
    private static final java.util.Map<BotSkill, HubButton.Icon> SKILL_ICONS = new java.util.EnumMap<>(BotSkill.class);

    private static HubButton.Icon skillIcon(BotSkill skill) {
        return SKILL_ICONS.computeIfAbsent(skill, value -> {
            float phase = value.ordinal() * 0.37F;
            return switch (value) {
                case MOVEMENT -> item("feather", phase);
                case CRYSTALS -> item("end_crystal", phase);
                case OBSIDIAN -> com.zymekoh.crystaltweaks.client.hub.HubIcons.obsidian(phase);
                case TOTEMS -> item("totem_of_undying", phase);
                case GAPPLES -> item("golden_apple", phase);
                case MINE_OUT -> item("netherite_pickaxe", phase);
                case RUSH -> item("netherite_sword", phase);
                case HIT_CRYSTAL -> item("rabbit_foot", phase);
                case ANCHORS, DOUBLE_ANCHOR -> com.zymekoh.crystaltweaks.client.hub.HubIcons.cube("respawn_anchor_top_off",
                        "respawn_anchor_side4", phase);
                case FACEPLACE -> item("netherite_helmet", phase);
                case PEARL_CHASE -> item("ender_pearl", phase);
                case BLOCK_OFF -> com.zymekoh.crystaltweaks.client.hub.HubIcons.block("cobblestone", phase);
                case PEARL_ESCAPE -> item("chorus_fruit", phase);
                case DOUBLE_TAP, TRIPLE_TAP -> com.zymekoh.crystaltweaks.client.hub.HubIcons.pulse("end_crystal", phase);
                case SAFE_ANCHOR -> item("glowstone_dust", phase);
                case TOPBLOCK -> com.zymekoh.crystaltweaks.client.hub.HubIcons.block("cobbled_deepslate", phase);
                case HOLES -> com.zymekoh.crystaltweaks.client.hub.HubIcons.block("bedrock", phase);
                case MENDING -> item("experience_bottle", phase);
                case BUTTERFLY -> item("phantom_membrane", phase);
                case CHAIN_POP -> com.zymekoh.crystaltweaks.client.hub.HubIcons.pulse("totem_of_undying", phase);
                case CRITS -> item("diamond_sword", phase);
                case PREDICTION -> item("spyglass", phase);
            };
        });
    }

    private static int skillColor(BotSkill skill) {
        return switch (skill.minimum) {
            case EASY -> CrystalTheme.TEXT_MUTED;
            case NORMAL -> CrystalTheme.ACCENT_BRIGHT;
            case HARD -> CrystalTheme.STATUS_PAUSED;
            case EXTREME -> 0xFFFF7A86;
        };
    }

    private String leaveWarning() {
        if (this.minecraft.level == null) {
            return this.spanish ? "Abre el mundo de práctica." : "Opens the practice world.";
        }
        if (this.minecraft.getSingleplayerServer() != null) {
            return this.spanish
                    ? "Guarda y cierra tu mundo actual y abre el mundo de práctica."
                    : "Saves and closes your current world and opens the practice world.";
        }
        if (!PracticeHost.canPark(this.minecraft)) {
            return this.spanish
                    ? "Te desconecta del servidor actual y abre el mundo de práctica."
                    : "Disconnects you from the current server and opens the practice world.";
        }
        return this.spanish
                ? "Abre el mundo de práctica en esta ventana sin salir del servidor, que sigue conectado detrás. Dos mundos a la vez pueden causar lag, y tu jugador se queda quieto en el servidor."
                : "Opens the practice world in this window without leaving the server, which stays connected behind it. Two worlds at once can cause lag, and your player stands still on the server.";
    }

    private String leaveLabel() {
        if (PracticeHost.parked() && !PracticeHost.serverLost()) {
            return (this.spanish ? "Volver a " : "Back to ") + PracticeHost.serverName();
        }
        return this.spanish ? "Salir de la práctica" : "Leave practice";
    }

    // ------------------------------------------------------------------------------------------
    // Actions
    // ------------------------------------------------------------------------------------------

    private void switchTab(Tab target) {
        if (this.tab == target) {
            return;
        }
        this.tabFrom = this.tab.ordinal();
        this.tabAt = System.nanoTime();
        this.tab = target;
        remembered = target;
        for (int index = 0; index < this.tabButtons.length; index++) {
            this.tabButtons[index].setSelected(index == target.ordinal());
        }
        buildList(true, target.ordinal() >= this.tabFrom ? 1 : -1);
    }

    /** A setting changed inside the tab: the options show it at once, and the world, in a world, too. */
    private void changed() {
        CrystalVisualConfig.save();
        if (this.inWorld) {
            PracticeSession.settingsChanged();
        }
        buildList(false, 1);
    }

    private void selectArmor(PracticeSettings.Armor armor) {
        CrystalVisualConfig.setPracticeArmor(armor.name());
        changed();
    }

    private void toggleBlast(int piece) {
        int mask = PracticeSettings.sanitize(CrystalVisualConfig.practiceBlastPieces());
        if ((mask & piece) != 0) {
            mask &= ~piece;
        } else if (Integer.bitCount(mask) >= PracticeSettings.MAX_BLAST_PIECES) {
            this.refusedAt = System.nanoTime();
            return;
        } else {
            mask |= piece;
        }
        CrystalVisualConfig.setPracticeBlastPieces(mask);
        changed();
    }

    private void selectKnockback(int level) {
        CrystalVisualConfig.setPracticeKnockback(level);
        changed();
    }

    private void totems(boolean more) {
        KitPreset preset = CrystalVisualConfig.practice().preset;
        KitLayout kit = CrystalVisualConfig.practiceKit(preset);
        CrystalVisualConfig.setPracticeKit(preset, kit.withTotems(KitLayout.nextTotemStep(kit, more)));
        changed();
    }

    private void selectPreset(KitPreset preset) {
        CrystalVisualConfig.setPracticePreset(preset.id);
        changed();
    }

    private void toggleBot() {
        CrystalVisualConfig.setPracticeBot(!CrystalVisualConfig.practiceBot());
        changed();
    }

    private void selectStyle(PracticeSettings.BotStyle style) {
        CrystalVisualConfig.setPracticeBotStyle(style.name());
        changed();
    }

    private void selectDifficulty(PracticeSettings.Difficulty difficulty) {
        CrystalVisualConfig.setPracticeBotDifficulty(difficulty.name());
        changed();
    }

    /**
     * A ground the world in play can take, such as the flat and the hole arena, which share one
     * world, comes in with the next round; any other arms "Go to this world".
     */
    private void selectWorld(PracticeSettings.WorldType type) {
        CrystalVisualConfig.setPracticeWorld(type.name());
        changed();
    }

    private void selectBiome(PracticeSettings.Biome biome) {
        CrystalVisualConfig.setPracticeBiome(biome.id);
        changed();
    }

    private void enter() {
        CrystalVisualConfig.save();
        PracticeWorld.enter(this.minecraft, this.parent);
    }

    private void leavePractice() {
        if (PracticeHost.parked()) {
            PracticeHost.leave();
        } else {
            this.minecraft.disconnectFromWorld(ClientLevel.DEFAULT_QUIT_MESSAGE);
        }
    }

    /** Runs one of the practice world's commands, as typing it in the chat would. */
    private void command(String command) {
        if (this.minecraft.player != null) {
            this.minecraft.player.connection.sendCommand(command);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (this.list != null && this.list.mouseScrolled(mouseX, mouseY, verticalAmount, this.layout.row + this.layout.gap)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void removed() {
        FixedGuiScale.restore(this.minecraft);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.inWorld ? null : this.parent);
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    // ------------------------------------------------------------------------------------------
    // Drawing
    // ------------------------------------------------------------------------------------------

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        HubSkin skin = HubSkin.current();
        if (this.inWorld) {
            // The paused fight stays in view: only the side the panel stands on darkens.
            Rect panel = this.layout.panel;
            int from = Math.max(0, panel.x() - 48);
            for (int band = 0; band < 16; band++) {
                int x0 = from + (panel.x() - from) * band / 16;
                int x1 = from + (panel.x() - from) * (band + 1) / 16;
                graphics.fill(x0, 0, x1, this.height, CrystalTheme.withAlpha(0x05020A, 4 + band * 6));
            }
            graphics.fill(panel.x(), 0, this.width, this.height, 0x7005020A);
        } else {
            graphics.fillGradient(0, 0, this.width, this.height, skin.backdropTop, skin.backdropBottom);
        }
        this.minecraft.gui.extractDeferredSubtitles();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        GrimoireDraw.active = true;
        try {
            drawScreen(graphics, mouseX, mouseY, partialTick);
        } finally {
            GrimoireDraw.active = false;
        }
    }

    private void drawScreen(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        long now = System.nanoTime();
        float frame = Mth.clamp((now - this.lastFrame) / 1_000_000.0F, 0.0F, 50.0F);
        this.lastFrame = now;
        double seconds = now / 1_000_000_000.0D;
        HubSkin skin = HubSkin.current();
        float enter = HubMotion.easeOutCubic(HubMotion.progress(now - this.openedAt, this.enterNanos));
        if (!this.inWorld) {
            CrystalUi.floatingParticles(graphics, this.width, this.height, seconds);
        }
        drawPanel(graphics, now, seconds, enter, skin);
        drawHeader(graphics, now, seconds, enter, skin);

        // The tabs and the footer are ordinary widgets; the option list draws its own, clipped.
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        if (this.list != null) {
            this.list.place(now, frame, enter, true);
            this.list.render(graphics, this.font, now, enter, GrimoireDraw.ACCENT, GrimoireDraw.MUTED, GrimoireDraw.BORDER, mouseX, mouseY,
                    partialTick);
        }
        if (this.layout.preview) {
            drawPreview(graphics, now, seconds, enter, skin, mouseX, mouseY);
        }
        if (this.inWorld) {
            drawServerStatus(graphics, seconds, enter);
        }
        drawTabFlight(graphics, now, skin);
        drawSkillTooltip(graphics, mouseX, mouseY);
    }

    /** A page of the grimoire, on its cover, and the entrance sweep. */
    private void drawPanel(GuiGraphicsExtractor graphics, long now, double seconds, float enter, HubSkin skin) {
        Rect panel = this.layout.panel;
        HubDraw.isolate(graphics);
        GrimoireDraw.cover(graphics, panel, enter);
        GrimoireDraw.page(graphics, this.page, GrimoireDraw.PAGE, enter);
        if (enter < 1.0F) {
            // The entrance: a line of light sweeps down the panel as it fills in.
            int sweepY = panel.y() + Math.round(panel.height() * enter);
            graphics.fill(panel.x() + 2, sweepY, panel.right() - 2, sweepY + 1,
                    CrystalTheme.withAlpha(skin.accentBright, Math.round(200 * (1.0F - enter))));
            HubDraw.glowDisc(graphics, panel.centerX(), sweepY, Math.max(8, panel.width() / 6), skin.accent, 0.4F * (1.0F - enter));
        }
        HubDraw.isolate(graphics);
    }

    private void drawHeader(GuiGraphicsExtractor graphics, long now, double seconds, float enter, HubSkin skin) {
        Rect header = this.layout.header;
        int crystalSize = this.layout.compact ? 12 : 16;
        CRYSTAL_ICON.draw(graphics, header.x() - 1, header.y() + (header.height() - crystalSize) / 2 - 1, crystalSize,
                CrystalTheme.fade(0xFFFFFFFF, enter));
        String title = this.spanish ? "Práctica de cristales" : "Crystal Practice";
        String badge = "EXPERIMENTAL";
        int badgeHeight = 13;
        float badgeScale = GrimoireDraw.textFit(badgeHeight, 1.0F);
        int badgeWidth = Math.round(this.font.width(badge) * badgeScale) + 10;
        int titleX = header.x() + crystalSize + 4;
        float scale = Math.min(1.5F, (header.height() - 1) / 10.0F);
        boolean showBadge = titleX + GrimoireDraw.gothicWidth(this.font, title, scale) + badgeWidth + 8 <= header.right();
        int titleRoom = showBadge ? header.right() - badgeWidth - 6 - titleX : header.right() - titleX;
        scale = Math.min(scale, GrimoireDraw.fitScale(this.font, title, titleRoom, scale));
        GrimoireDraw.gothic(graphics, this.font, title, titleX, header.y() + (header.height() - 10.0F * scale) / 2.0F, scale,
                CrystalTheme.fade(GrimoireDraw.TITLE, enter), 0.35F + 0.25F * HubMotion.breathe(seconds, 3.2D));
        if (showBadge) {
            // Crimson for danger only: this is still being tested.
            float alarm = 0.5F + 0.5F * (float) Math.sin(seconds * 3.4D);
            int badgeX = header.right() - badgeWidth;
            int badgeY = header.y() + (header.height() - badgeHeight) / 2;
            graphics.fill(badgeX, badgeY, badgeX + badgeWidth, badgeY + badgeHeight,
                    CrystalTheme.fade(CrystalTheme.lerp(0xFF6A0A16, 0xFFA3122A, alarm), enter));
            CrystalUi.outline(graphics, badgeX, badgeY, badgeWidth, badgeHeight, CrystalTheme.fade(0xC0FF5A6E, enter));
            GrimoireDraw.text(graphics, this.font, badge, badgeX + 5, GrimoireDraw.textY(badgeY, badgeHeight, badgeScale), badgeScale,
                    CrystalTheme.fade(0xFFFFE4E8, enter));
        }
        int lineY = this.layout.tabs.y() - 2;
        graphics.fill(header.x(), lineY, header.right(), lineY + 1, CrystalTheme.fade(GrimoireDraw.RULE_TITLE, enter));
    }

    /** Above the buttons, in a world: whether a server is still connected behind. */
    private void drawServerStatus(GuiGraphicsExtractor graphics, double seconds, float enter) {
        Rect status = this.layout.status;
        String text;
        int color;
        if (!PracticeHost.parked()) {
            text = this.spanish ? "Sin servidor detrás" : "No server behind";
            color = CrystalTheme.TEXT_MUTED;
        } else if (PracticeHost.serverLost()) {
            text = PracticeHost.serverName() + (this.spanish ? " · desconectado" : " · disconnected");
            color = CrystalTheme.DANGER;
        } else {
            text = PracticeHost.serverName() + (this.spanish ? " · conectado detrás" : " · connected behind");
            color = 0xFF6BE39A;
        }
        float pulse = 0.55F + 0.45F * (float) Math.sin(seconds * 2.6D);
        graphics.fill(status.x() + 1, status.y() + 2, status.x() + 5, status.y() + 6,
                CrystalTheme.fade(CrystalTheme.withAlpha(color, Math.round(255 * pulse)), enter));
        CrystalUi.label(graphics, this.font, HubDraw.fit(this.font, text, status.width() - 10), status.x() + 9, status.y(),
                CrystalTheme.fade(color, enter));
    }

    /** The End Crystal that flies from the old tab to the new one and bursts there. */
    private void drawTabFlight(GuiGraphicsExtractor graphics, long now, HubSkin skin) {
        if (this.tabAt == Long.MIN_VALUE) {
            return;
        }
        long elapsed = now - this.tabAt;
        if (elapsed > FLIGHT_NANOS + BURST_NANOS) {
            this.tabAt = Long.MIN_VALUE;
            return;
        }
        Rect from = this.layout.tab[this.tabFrom];
        Rect to = this.layout.tab[this.tab.ordinal()];
        HubDraw.isolate(graphics);
        if (elapsed < FLIGHT_NANOS) {
            float t = HubMotion.easeInOutCubic(HubMotion.progress(elapsed, FLIGHT_NANOS));
            float arc = (float) Math.sin(Math.PI * t) * Math.max(6.0F, to.height() * 0.7F);
            float x = HubMotion.lerp(from.centerX(), to.centerX(), t);
            float y = HubMotion.lerp(from.centerY(), to.centerY(), t) - arc;
            for (int trail = 1; trail <= 3; trail++) {
                float back = Math.max(0.0F, t - trail * 0.07F);
                float trailX = HubMotion.lerp(from.centerX(), to.centerX(), back);
                float trailY = HubMotion.lerp(from.centerY(), to.centerY(), back) - (float) Math.sin(Math.PI * back) * Math.max(6.0F, to.height() * 0.7F);
                HubDraw.sparkle(graphics, Math.round(trailX), Math.round(trailY), 3 - trail / 2,
                        CrystalTheme.withAlpha(skin.accentBright, 200 - trail * 50));
            }
            HubDraw.glowDisc(graphics, Math.round(x), Math.round(y), 9, skin.accent, 0.55F);
            GrimoireIcons.drawItem(graphics, GrimoireIcons.Sprite.END_CRYSTAL.art(), x, y, 12.0F, t * (float) Math.PI, 0xFFFFFFFF);
        } else {
            // The burst: a ring of light and shards flying out of the new tab.
            float t = HubMotion.progress(elapsed - FLIGHT_NANOS, BURST_NANOS);
            float eased = HubMotion.easeOutCubic(t);
            int alpha = Math.round(230 * (1.0F - t));
            HubDraw.ring(graphics, to.centerX(), to.centerY(), 3.0F + eased * Math.max(14.0F, to.width() * 0.35F), 1,
                    CrystalTheme.withAlpha(skin.accentBright, alpha));
            for (int shard = 0; shard < 6; shard++) {
                double angle = shard * Math.PI / 3.0D + 0.4D;
                float distance = 4.0F + eased * 16.0F;
                HubDraw.shard(graphics, to.centerX() + (float) Math.cos(angle) * distance, to.centerY() + (float) Math.sin(angle) * distance * 0.6F,
                        2.0F, 5.0F * (1.0F - t) + 1.0F, (float) angle, CrystalTheme.withAlpha(skin.accent, alpha));
            }
        }
        HubDraw.isolate(graphics);
    }

    private void drawSkillTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (this.list == null || !this.layout.content.contains(mouseX, mouseY)) {
            return;
        }
        for (SkillRow entry : this.skillRows) {
            if (entry.drawn.contains(mouseX, mouseY)) {
                skillTooltip(graphics, entry.skill, mouseX, mouseY);
                return;
            }
        }
    }

    private void skillTooltip(GuiGraphicsExtractor graphics, BotSkill skill, int mouseX, int mouseY) {
        List<FormattedCharSequence> tip = new ArrayList<>();
        tip.addAll(CrystalUi.wrap(this.font, skill.label(this.spanish) + " · " + skill.minimum.label(this.spanish), 220));
        tip.addAll(CrystalUi.wrap(this.font, skill.how(this.spanish), 220));
        HubDraw.isolate(graphics);
        CrystalUi.tooltip(graphics, this.font, tip, mouseX, mouseY, this.width, this.height);
    }

    // ------------------------------------------------------------------------------------------
    // The preview beside the options
    // ------------------------------------------------------------------------------------------

    private void drawPreview(GuiGraphicsExtractor graphics, long now, double seconds, float enter, HubSkin skin, int mouseX, int mouseY) {
        Rect area = this.layout.previewArea;
        boolean enemy = this.tab == Tab.BOT;
        HubDraw.isolate(graphics);
        // The bot's card is the opponent's: crimson edges, the only red in the window.
        GrimoireDraw.page(graphics, area, GrimoireDraw.PAGE_DEEP, enter);
        if (enemy) {
            CrystalUi.outline(graphics, area.x(), area.y(), area.width(), area.height(), CrystalTheme.fade(GrimoireDraw.CRIMSON, enter));
        }
        int corner = CrystalTheme.fade(enemy ? 0xFFFF5A6E : GrimoireDraw.ACCENT, 0.8F * enter);
        int pulse = 3 + Math.round((float) Math.sin(seconds * 3.0D) * 1.5F);
        graphics.fill(area.x(), area.y(), area.x() + 6 + pulse, area.y() + 1, corner);
        graphics.fill(area.x(), area.y(), area.x() + 1, area.y() + 6 + pulse, corner);
        graphics.fill(area.right() - 6 - pulse, area.bottom() - 1, area.right(), area.bottom(), corner);
        graphics.fill(area.right() - 1, area.bottom() - 6 - pulse, area.right(), area.bottom(), corner);
        switch (this.tab) {
            case GEAR -> drawGearPreview(graphics, area, enter, seconds, skin);
            case BOT -> drawBotPreview(graphics, area, enter, seconds, skin, mouseX, mouseY);
            case WORLD -> drawWorldPreview(graphics, area, enter, seconds, skin);
        }
        HubDraw.isolate(graphics);
    }

    /** Your kit as it will be handed out: the armour, the sword and the hotbar, off hand included. */
    private void drawGearPreview(GuiGraphicsExtractor graphics, Rect area, float enter, double seconds, HubSkin skin) {
        PracticeSettings settings = CrystalVisualConfig.practice();
        KitLayout kit = CrystalVisualConfig.practiceKit(settings.preset);
        int x = area.x();
        int y = area.y();
        int width = area.width();
        int height = area.height();
        String preset = settings.preset.label(this.spanish);
        CrystalUi.centered(graphics, this.font, fit(width - 8, (this.spanish ? "Tu kit y el del bot · " : "Your kit and the bot's · ") + preset,
                "Kit · " + preset), x + width / 2, y + 7, CrystalTheme.fade(skin.accentBright, enter));
        int step = Math.max(17, Math.min(24, (height - 80) / 4));
        int slot = Math.max(10, Math.min(16, (width - 18) / 10));
        int block = step * 4 + 18 + slot + 13;
        int rowY = y + 20 + Math.max(0, (height - 24 - block) / 2);
        // Room for the second line (the same on every piece) only when a row is a full two lines tall.
        boolean detail = step >= GrimoireDraw.LINE * 2;
        int textWidth = width - 30;
        for (int index = 0; index < PIECE_ITEMS.length; index++) {
            float bob = (float) Math.sin(seconds * 2.0D + index * 0.8D) * 0.8F;
            HubDraw.icon(graphics, armorPieces(settings.armor)[index], x + 14, rowY + 8 + bob, 16.0F, 0.0F,
                    CrystalTheme.fade(0xFFFFFFFF, enter));
            boolean blast = settings.blast(PIECES[index]);
            String protection = blast
                    ? fit(textWidth, this.spanish ? "Protección contra explosiones IV" : "Blast Protection IV",
                            this.spanish ? "Prot. explosiones IV" : "Blast Prot. IV")
                    : fit(textWidth, this.spanish ? "Protección IV" : "Protection IV", "Prot. IV");
            CrystalUi.label(graphics, this.font, protection, x + 26, detail ? rowY : rowY + 4,
                    CrystalTheme.fade(blast ? 0xFFFFC48A : skin.text, enter));
            if (detail) {
                CrystalUi.label(graphics, this.font, fit(textWidth,
                            this.spanish ? "Irrompibilidad III · Reparación" : "Unbreaking III · Mending",
                            this.spanish ? "Irromp. III · Rep." : "Unbr. III · Mend."),
                        x + 26, rowY + GrimoireDraw.LINE, CrystalTheme.fade(skin.muted, enter));
            }
            rowY += step;
        }
        String sword = (this.spanish ? "Espada: Filo V · Empuje " : "Sword: Sharpness V · Knockback ") + PracticeSettings.roman(settings.knockback);
        CrystalUi.label(graphics, this.font, fit(width - 12, sword), x + 6, rowY + 2, CrystalTheme.fade(skin.text, enter));
        int barWidth = slot * 9 + 4 + slot;
        int barX = x + (width - barWidth) / 2;
        int barY = rowY + 18;
        for (int index = 0; index < 9; index++) {
            miniSlot(graphics, kit.get(index), settings, barX + index * slot, barY, slot);
        }
        miniSlot(graphics, kit.get(KitLayout.OFFHAND), settings, barX + 9 * slot + 4, barY, slot);
        String counts = kit.totems() + (this.spanish ? " tótems · " : " totems · ") + kit.count(KitItem.END_CRYSTAL)
                + (this.spanish ? " cristales" : " crystals");
        String anchors = counts + " · " + kit.count(KitItem.RESPAWN_ANCHOR) + (this.spanish ? " anclas" : " anchors");
        CrystalUi.centered(graphics, this.font, fit(width - 8, anchors, counts), x + width / 2, barY + slot + 4,
                CrystalTheme.fade(skin.muted, enter));
    }

    private void miniSlot(GuiGraphicsExtractor graphics, KitLayout.Entry entry, PracticeSettings settings, int x, int y, int size) {
        graphics.fill(x, y, x + size, y + size, CrystalTheme.CARD_BORDER);
        graphics.fill(x + 1, y + 1, x + size - 1, y + size - 1, 0xC0160A20);
        if (entry == null) {
            return;
        }
        graphics.blit(PracticeIcons.location(entry.item().texture(settings.armor)), x + 1, y + 1, x + size - 1, y + size - 1, 0.0F, 1.0F,
                0.0F, 1.0F);
    }

    /**
     * The opponent: its sword large and turning, how hard it plays on a bar of four, its style by an
     * item, and everything it will do, each technique explained on hover.
     */
    private void drawBotPreview(GuiGraphicsExtractor graphics, Rect area, float enter, double seconds, HubSkin skin, int mouseX,
            int mouseY) {
        PracticeSettings settings = CrystalVisualConfig.practice();
        int x = area.x();
        int y = area.y();
        int width = area.width();
        float alpha = settings.bot ? enter : enter * 0.45F;
        String title = settings.bot
                ? settings.difficulty.label(this.spanish) + " · " + settings.style.label(this.spanish)
                : (this.spanish ? "Sin bot" : "No bot");
        CrystalUi.centered(graphics, this.font, fit(width - 8, title), x + width / 2, y + 5,
                CrystalTheme.fade(settings.bot ? 0xFFFF8A9A : skin.disabled, enter));
        int level = settings.difficulty.ordinal();
        float swing = (float) Math.sin(seconds * 2.4D) * 0.35F;
        int heroY = y + 32;
        HubDraw.glowDisc(graphics, x + width / 2, heroY, 16, 0xFFE04A5C, 0.35F * alpha);
        GrimoireIcons.drawItem(graphics, SWORDS[Math.min(level, 3)], x + width / 2.0F, heroY, 26.0F, swing,
                CrystalTheme.fade(0xFFFFFFFF, alpha));
        // How hard: four bars, lit up to its difficulty.
        int barWidth = Math.max(10, (width - 40) / 4);
        int barsX = x + (width - (barWidth * 4 + 9)) / 2;
        int barsY = heroY + 18;
        for (int bar = 0; bar < 4; bar++) {
            boolean lit = bar <= level && settings.bot;
            int color = lit ? CrystalTheme.lerp(0xFFB0303E, 0xFFFF5A6E, bar / 3.0F) : 0x60301638;
            graphics.fill(barsX + bar * (barWidth + 3), barsY, barsX + bar * (barWidth + 3) + barWidth, barsY + 3,
                    CrystalTheme.fade(color, enter));
        }
        int listY = barsY + 9;
        if (!settings.bot) {
            List<FormattedCharSequence> lines = CrystalUi.wrap(this.font, this.spanish
                    ? "Sin bot: practica colocar y romper cristales y anclas a tu ritmo."
                    : "No bot: practise placing and breaking crystals and anchors at your own pace.", width - 12);
            for (FormattedCharSequence line : lines) {
                HubDraw.line(graphics, this.font, line, x + 6, listY, CrystalTheme.fade(skin.muted, enter));
                listY += this.font.lineHeight + 1;
            }
            return;
        }
        List<BotSkill> skills = BotSkill.of(settings.difficulty, settings.style);
        int room = area.bottom() - listY - 3;
        int lineHeight = skills.size() * 10 <= room ? 10 : 9;
        int columns = skills.size() * lineHeight > room && width >= 200 ? 2 : 1;
        int perColumn = (skills.size() + columns - 1) / columns;
        int columnWidth = (width - 12) / columns;
        BotSkill hovered = null;
        for (int index = 0; index < skills.size(); index++) {
            BotSkill skill = skills.get(index);
            int lineX = x + 6 + (index / perColumn) * columnWidth;
            int lineY = listY + (index % perColumn) * lineHeight;
            if (lineY + lineHeight > area.bottom() - 2) {
                continue;
            }
            int color = skillColor(skill);
            skillIcon(skill).draw(graphics, lineX - 2, lineY, 9, CrystalTheme.fade(0xFFFFFFFF, enter));
            CrystalUi.label(graphics, this.font, fit(columnWidth - 13, skill.label(this.spanish)), lineX + 10, lineY + 1,
                    CrystalTheme.fade(CrystalTheme.lerp(color, 0xFFFFFFFF, 0.25F), enter));
            if (mouseX >= lineX && mouseX < lineX + columnWidth - 2 && mouseY >= lineY && mouseY < lineY + lineHeight) {
                hovered = skill;
            }
        }
        if (hovered != null) {
            skillTooltip(graphics, hovered, mouseX, mouseY);
        }
    }

    /** A cut through the chosen ground, drawn from the blocks it is made of. */
    private void drawWorldPreview(GuiGraphicsExtractor graphics, Rect area, float enter, double seconds, HubSkin skin) {
        PracticeSettings settings = CrystalVisualConfig.practice();
        int x = area.x();
        int y = area.y();
        int width = area.width();
        int height = area.height();
        String name = settings.worldType == PracticeSettings.WorldType.NATURAL
                ? settings.worldType.label(this.spanish) + " · " + settings.biome.label(this.spanish)
                : settings.worldType.label(this.spanish);
        CrystalUi.centered(graphics, this.font, fit(width - 8, name), x + width / 2, y + 5, CrystalTheme.fade(skin.accentBright, enter));
        String[] ground = groundTextures(settings.worldType, settings.biome);
        // Whole multiples of the 16-pixel textures keep every texel square.
        int size = width >= 140 && height >= 200 ? 32 : 16;
        int blocks = Math.max(3, Math.min(9, (width - 12) / size));
        int stripX = x + (width - blocks * size) / 2;
        // The ground and what stands on it, five blocks tall, centred under the title.
        int stripY = Math.max(y + 16 + size * 2, y + (height + 12) / 2 - size / 2);
        for (int index = 0; index < blocks; index++) {
            boolean hole = settings.worldType == PracticeSettings.WorldType.HOLES && (index == 1 || index == blocks - 2);
            int columnX = stripX + index * size;
            if (hole) {
                block(graphics, "block/bedrock", columnX, stripY + size, size);
            } else {
                block(graphics, ground[0], columnX, stripY, size);
                block(graphics, ground[1], columnX, stripY + size, size);
            }
            block(graphics, ground[1], columnX, stripY + size * 2, size);
        }
        int middle = stripX + (blocks / 2) * size;
        float bob = (float) Math.sin(seconds * 2.2D) * 1.5F;
        if (settings.worldType == PracticeSettings.WorldType.FLAT) {
            block(graphics, "block/obsidian", middle, stripY - size, size);
            float crystalY = stripY - size * 1.6F + bob;
            HubDraw.glowDisc(graphics, middle + size / 2, Math.round(crystalY), Math.round(size * 0.7F), skin.accent, 0.5F * enter);
            HubDraw.icon(graphics, END_CRYSTAL, middle + size / 2.0F, crystalY, size, 0.0F, CrystalTheme.fade(0xFFFFFFFF, enter));
        } else if (ground.length > 2) {
            block(graphics, ground[2], middle + size, stripY - size, size);
        }
    }

    /** A block's texture at {@code size} pixels. */
    private static void block(GuiGraphicsExtractor graphics, String texture, int x, int y, int size) {
        graphics.blit(PracticeIcons.location(texture), x, y, x + size, y + size, 0.0F, 1.0F, 0.0F, 1.0F);
    }

    /** The bot's sword by how hard it plays, in the book's art. */
    private static final GrimoireIcons.Art[] SWORDS = {GrimoireIcons.art("item/wooden_sword", GrimoireIcons.Kind.ITEM),
            GrimoireIcons.art("item/iron_sword", GrimoireIcons.Kind.ITEM), GrimoireIcons.art("item/diamond_sword", GrimoireIcons.Kind.ITEM),
            GrimoireIcons.art("item/netherite_sword", GrimoireIcons.Kind.ITEM)};
    private static final GrimoireIcons.Art TOTEM = GrimoireIcons.art("item/totem_of_undying", GrimoireIcons.Kind.ITEM);
    /** The crystal before the title, bobbing, in the book's art. */
    private static final HubButton.Icon CRYSTAL_ICON = GrimoireIcons.icon(GrimoireIcons.Sprite.END_CRYSTAL, 0.35F);
    /** The armour's four pieces by material, as the game draws them, made once. */
    private static final EnumMap<PracticeSettings.Armor, Identifier[]> ARMOR_PIECES = new EnumMap<>(PracticeSettings.Armor.class);

    private static Identifier[] armorPieces(PracticeSettings.Armor armor) {
        return ARMOR_PIECES.computeIfAbsent(armor, value -> {
            String material = value.name().toLowerCase(Locale.ROOT);
            Identifier[] pieces = new Identifier[PIECE_ITEMS.length];
            for (int index = 0; index < pieces.length; index++) {
                pieces[index] = PracticeIcons.location("item/" + material + "_" + PIECE_ITEMS[index]);
            }
            return pieces;
        });
    }

    /** Surface, what lies under it, and something that grows on it. */
    private static String[] groundTextures(PracticeSettings.WorldType type, PracticeSettings.Biome biome) {
        return switch (type) {
            case FLAT -> new String[] {"block/netherite_block", "block/netherite_block"};
            case HOLES -> new String[] {"block/obsidian", "block/obsidian"};
            case SURVIVAL -> new String[] {"block/grass_block_side", "block/stone", "block/oak_sapling"};
            case NATURAL -> switch (biome == null ? PracticeSettings.Biome.PLAINS : biome) {
                case PLAINS -> new String[] {"block/grass_block_side", "block/dirt", "block/poppy"};
                case DESERT -> new String[] {"block/sand", "block/sandstone", "block/dead_bush"};
                case TAIGA -> new String[] {"block/podzol_side", "block/dirt", "block/spruce_sapling"};
                case SNOWY -> new String[] {"block/grass_block_snow", "block/dirt", "block/spruce_sapling"};
                case SAVANNA -> new String[] {"block/grass_block_side", "block/dirt", "block/acacia_sapling"};
                case CHERRY -> new String[] {"block/grass_block_side", "block/dirt", "block/cherry_sapling"};
                case BADLANDS -> new String[] {"block/red_sand", "block/terracotta", "block/dead_bush"};
                case END -> new String[] {"block/end_stone", "block/end_stone"};
            };
        };
    }

    /** The first variant that fits {@code width}, or the last one cut to it. */
    private String fit(int width, String... variants) {
        for (String variant : variants) {
            if (this.font.width(variant) <= width) {
                return variant;
            }
        }
        return HubDraw.fit(this.font, variants[variants.length - 1], width);
    }
}
