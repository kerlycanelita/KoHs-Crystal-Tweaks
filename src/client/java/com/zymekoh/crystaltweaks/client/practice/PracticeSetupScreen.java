package com.zymekoh.crystaltweaks.client.practice;

import com.zymekoh.crystaltweaks.client.CrystalTheme;
import com.zymekoh.crystaltweaks.client.CrystalUi;
import com.zymekoh.crystaltweaks.client.CrystalVisualConfig;
import com.zymekoh.crystaltweaks.client.PurpleCloseButton;
import com.zymekoh.crystaltweaks.practice.BotSkill;
import com.zymekoh.crystaltweaks.practice.KitItem;
import com.zymekoh.crystaltweaks.practice.KitLayout;
import com.zymekoh.crystaltweaks.practice.KitPreset;
import com.zymekoh.crystaltweaks.practice.PracticeSettings;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

/**
 * The Crystal Practice window, in three tabs: the gear (armour, sword, totems, kit), the bot
 * (difficulty and everything it will do), and the world (the netherite flat, the hole arena or a
 * natural meadow in a chosen biome). A preview on the right follows the tab; the way in is at the
 * bottom.
 *
 * <p>Protection IV, Unbreaking III and Mending are on every armour piece, always; up to two pieces
 * take Blast Protection IV instead of Protection IV, since Minecraft does not allow both on one.
 * The bot's techniques come with its difficulty and cannot be switched off, so the bot tab lists
 * them, each with how it works.</p>
 */
public final class PracticeSetupScreen extends Screen {
    private static final long ENTER_NANOS = 360_000_000L;
    private static final long REFUSE_NANOS = 1_600_000_000L;
    private static final int[] PIECES = {PracticeSettings.HEAD, PracticeSettings.CHEST, PracticeSettings.LEGS, PracticeSettings.FEET};
    private static final int ROW = 14;
    private static final int GAP = 3;

    private enum Tab { GEAR, BOT, WORLD }

    /** The tab shown when the window reopens, for as long as the game runs. */
    private static Tab remembered = Tab.GEAR;

    /** A widget and the height it sits at before scrolling. */
    private record Placed(AbstractWidget widget, int baseY) {
    }

    /** A line of text in the scrolling column, centred on {@code x} or starting at it. */
    private record Line(int x, int baseY, Supplier<String> text, IntSupplier color, boolean centered) {
    }

    private final Screen parent;
    private final boolean spanish;
    private Tab tab = remembered;
    private long openedAt;
    private long refusedAt;
    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;
    private int columnX;
    private int columnWidth;
    private int previewX;
    private int previewWidth;
    private int contentTop;
    private int contentBottom;
    private int scroll;
    private int maxScroll;
    private int skillsTop = -1;
    private final List<Placed> placed = new ArrayList<>();
    private final List<Line> lines = new ArrayList<>();
    private final List<PurpleCloseButton> armorButtons = new ArrayList<>();
    private final List<PurpleCloseButton> blastButtons = new ArrayList<>();

    public PracticeSetupScreen(Screen parent) {
        super(Component.literal("Crystal Practice"));
        this.parent = parent;
        this.spanish = CrystalUi.spanish();
    }

    // ------------------------------------------------------------------------------------------
    // Layout
    // ------------------------------------------------------------------------------------------

    @Override
    protected void init() {
        if (this.openedAt == 0L) {
            this.openedAt = System.nanoTime();
        }
        this.placed.clear();
        this.lines.clear();
        this.armorButtons.clear();
        this.blastButtons.clear();
        this.skillsTop = -1;
        int margin = Mth.clamp(this.width / 24, 6, 20);
        this.panelWidth = Math.max(200, Math.min(480, this.width - margin * 2));
        this.panelHeight = Math.max(180, Math.min(300, this.height - margin * 2));
        this.panelX = (this.width - this.panelWidth) / 2;
        this.panelY = (this.height - this.panelHeight) / 2;
        boolean preview = this.panelWidth >= 340 && this.panelHeight >= 190;
        this.columnX = this.panelX + 10;
        this.columnWidth = preview ? Math.min(210, (this.panelWidth - 30) * 11 / 20) : this.panelWidth - 20;
        this.previewX = this.columnX + this.columnWidth + 10;
        this.previewWidth = preview ? this.panelX + this.panelWidth - 10 - this.previewX : 0;
        this.contentTop = this.panelY + 42;
        this.contentBottom = this.panelY + this.panelHeight - 30;

        Tab[] tabs = Tab.values();
        String[] tabNames = this.spanish ? new String[] {"Equipo", "Bot", "Mundo"} : new String[] {"Gear", "Bot", "World"};
        PurpleCloseButton.Icon[] tabIcons = {PurpleCloseButton.Icon.CRYSTAL, PurpleCloseButton.Icon.SWORDS,
                PurpleCloseButton.Icon.GEAR};
        int tabWidth = (this.panelWidth - 20 - GAP * 2) / 3;
        for (int index = 0; index < tabs.length; index++) {
            Tab target = tabs[index];
            PurpleCloseButton button = addRenderableWidget(new PurpleCloseButton(this.columnX + (tabWidth + GAP) * index,
                    this.panelY + 23, tabWidth, ROW, Component.literal(tabNames[index]), ignored -> switchTab(target))
                    .icon(tabIcons[index]));
            button.setSelected(target == this.tab);
        }

        int bottom = switch (this.tab) {
            case GEAR -> buildGear(this.contentTop);
            case BOT -> buildBot(this.contentTop);
            case WORLD -> buildWorld(this.contentTop);
        };
        this.maxScroll = Math.max(0, bottom - this.contentBottom);
        this.scroll = Mth.clamp(this.scroll, 0, this.maxScroll);
        applyScroll();

        int footerY = this.panelY + this.panelHeight - 24;
        int buttonWidth = Math.max(60, Math.min(150, (this.panelWidth - 26) / 2));
        int buttonsX = this.panelX + (this.panelWidth - buttonWidth * 2 - 6) / 2;
        addRenderableWidget(new PurpleCloseButton(buttonsX, footerY, buttonWidth, 18,
                Component.literal(this.spanish ? "Volver" : "Back"), ignored -> onClose()));
        PurpleCloseButton enter = addRenderableWidget(new PurpleCloseButton(buttonsX + buttonWidth + 6, footerY, buttonWidth, 18,
                Component.literal(this.spanish ? "Entrar a practicar" : "Enter practice"), ignored -> enter())
                .icon(PurpleCloseButton.Icon.ARROW_RIGHT).accent(PurpleCloseButton.Accent.EXPERIMENTAL));
        enter.setSelected(true);
        enter.setTooltip(Tooltip.create(Component.literal(leaveWarning())));
        refreshSelection();
    }

    private int buildGear(int top) {
        int y = top;
        label(this.columnX, y, this.spanish ? "Armadura" : "Armour");
        y += 11;
        PracticeSettings.Armor[] armors = PracticeSettings.Armor.values();
        int armorWidth = (this.columnWidth - GAP * (armors.length - 1)) / armors.length;
        for (int index = 0; index < armors.length; index++) {
            PracticeSettings.Armor armor = armors[index];
            this.armorButtons.add(place(new PurpleCloseButton(this.columnX + (armorWidth + GAP) * index, y, armorWidth, ROW,
                    Component.literal(armor.label(this.spanish)), ignored -> selectArmor(armor))));
        }
        y += ROW + GAP;
        y = paragraph(this.columnX, y, this.columnWidth, this.spanish
                ? "En cada pieza: Protección IV, Irrompibilidad III y Reparación."
                : "On every piece: Protection IV, Unbreaking III and Mending.", CrystalTheme.TEXT_MUTED) + 4;
        dynamic(this.columnX, y, () -> {
            if (System.nanoTime() - this.refusedAt < REFUSE_NANOS) {
                return this.spanish ? "Máximo 2 piezas" : "At most 2 pieces";
            }
            int selected = Integer.bitCount(PracticeSettings.sanitize(CrystalVisualConfig.practiceBlastPieces()));
            return (this.spanish ? "Protección contra explosiones IV " : "Blast Protection IV ") + selected + "/2";
        }, () -> System.nanoTime() - this.refusedAt < REFUSE_NANOS ? CrystalTheme.DANGER : CrystalTheme.LEGEND);
        y += 11;
        String[] pieces = this.spanish
                ? new String[] {"Casco", "Peto", "Pantalones", "Botas"}
                : new String[] {"Helmet", "Chest", "Leggings", "Boots"};
        int pieceWidth = (this.columnWidth - GAP) / 2;
        for (int index = 0; index < PIECES.length; index++) {
            int piece = PIECES[index];
            PurpleCloseButton button = place(new PurpleCloseButton(this.columnX + (pieceWidth + GAP) * (index % 2),
                    y + (ROW + GAP) * (index / 2), pieceWidth, ROW, Component.literal(pieces[index]),
                    ignored -> toggleBlast(piece)));
            button.setTooltip(Tooltip.create(Component.literal(this.spanish
                    ? "Protección contra explosiones IV en lugar de Protección IV. Máximo 2 piezas."
                    : "Blast Protection IV instead of Protection IV. At most 2 pieces.")));
            this.blastButtons.add(button);
        }
        y += (ROW + GAP) * 2 + 3;

        int half = (this.columnWidth - 6) / 2;
        int rightX = this.columnX + half + 6;
        label(this.columnX, y, this.spanish ? "Espada · Empuje" : "Sword · Knockback");
        label(rightX, y, this.spanish ? "Tótems" : "Totems");
        y += 11;
        int knockbackWidth = (half - GAP) / 2;
        for (int level = PracticeSettings.MIN_KNOCKBACK; level <= PracticeSettings.MAX_KNOCKBACK; level++) {
            int chosen = level;
            PurpleCloseButton button = place(new PurpleCloseButton(this.columnX + (knockbackWidth + GAP) * (level - 1), y,
                    knockbackWidth, ROW, Component.literal(PracticeSettings.roman(level)), ignored -> selectKnockback(chosen)));
            button.setSelected(CrystalVisualConfig.practiceKnockback() == level);
            button.setTooltip(Tooltip.create(Component.literal(level == 1
                    ? (this.spanish
                            ? "Empuje I: el de los kits de crystal PvP. Levanta al rival lo justo para un hit-crystal sin mandarlo lejos."
                            : "Knockback I: what crystal PvP kits carry. Lifts the opponent just enough for a hit-crystal without sending them away.")
                    : (this.spanish
                            ? "Empuje II: aleja mucho más. Útil para separar, peor para encadenar cristales."
                            : "Knockback II: sends them much further. Good for spacing, worse for chaining crystals."))));
        }
        place(new PurpleCloseButton(rightX, y, 14, ROW, Component.literal("-"), ignored -> totems(false)))
                .setTooltip(Tooltip.create(Component.literal(totemAdvice())));
        place(new PurpleCloseButton(rightX + half - 14, y, 14, ROW, Component.literal("+"), ignored -> totems(true)))
                .setTooltip(Tooltip.create(Component.literal(totemAdvice())));
        int counterCenter = rightX + half / 2;
        this.lines.add(new Line(counterCenter, y + 3, this::totemCount, () -> CrystalTheme.ACCENT_BRIGHT, true));
        y += ROW + GAP + 3;

        label(this.columnX, y, this.spanish ? "Kit" : "Kit");
        y += 11;
        KitPreset preset = CrystalVisualConfig.practice().preset;
        place(new PurpleCloseButton(this.columnX, y, 14, ROW, Component.literal("<"), ignored -> selectPreset(preset.previous())));
        PurpleCloseButton name = place(new PurpleCloseButton(this.columnX + 14 + GAP, y, this.columnWidth - 2 * (14 + GAP), ROW,
                Component.literal(preset.label(this.spanish)
                        + (CrystalVisualConfig.practiceKitCustomized(preset) ? (this.spanish ? " · tuyo" : " · yours") : "")),
                ignored -> selectPreset(preset.next())));
        name.setSelected(true);
        name.setTooltip(Tooltip.create(Component.literal(preset.note(this.spanish))));
        place(new PurpleCloseButton(this.columnX + this.columnWidth - 14, y, 14, ROW, Component.literal(">"),
                ignored -> selectPreset(preset.next())));
        y += ROW + GAP;
        place(new PurpleCloseButton(this.columnX, y, this.columnWidth, ROW,
                Component.literal(this.spanish ? "Ordenar inventario…" : "Arrange inventory…"),
                ignored -> this.minecraft.setScreen(new PracticeKitScreen(this, CrystalVisualConfig.practice().preset)))
                .icon(PurpleCloseButton.Icon.GEAR))
                .setTooltip(Tooltip.create(Component.literal(this.spanish
                        ? "Abre el kit en un inventario para ordenarlo a tu gusto, con Guardar y Restablecer."
                        : "Opens the kit in an inventory to arrange it your way, with Save and Reset.")));
        y += ROW + GAP + 2;
        return paragraph(this.columnX, y, this.columnWidth, preset.note(this.spanish), CrystalTheme.TEXT_MUTED);
    }

    private int buildBot(int top) {
        int y = top;
        label(this.columnX, y, "Bot");
        y += 11;
        boolean bot = CrystalVisualConfig.practiceBot();
        int switchWidth = bot ? (this.columnWidth - GAP) * 3 / 5 : this.columnWidth;
        place(new PurpleCloseButton(this.columnX, y, switchWidth, ROW,
                Component.literal(this.spanish ? "Generar bot" : "Spawn bot"), ignored -> toggleBot())
                .switchOf(CrystalVisualConfig::practiceBot).icon(PurpleCloseButton.Icon.SWORDS))
                .setTooltip(Tooltip.create(Component.literal(this.spanish
                        ? "Un rival con tu mismo kit: armadura, espada, tótems, manzanas y perlas."
                        : "An opponent with your same kit: armour, sword, totems, apples and pearls.")));
        if (bot) {
            PracticeSettings.Difficulty difficulty = CrystalVisualConfig.practice().difficulty;
            place(new PurpleCloseButton(this.columnX + switchWidth + GAP, y, this.columnWidth - switchWidth - GAP, ROW,
                    Component.literal(difficulty.label(this.spanish)), ignored -> cycleDifficulty()))
                    .setTooltip(Tooltip.create(Component.literal(this.spanish
                            ? "Cada dificultad suma técnicas; ninguna se puede desactivar."
                            : "Each difficulty adds techniques; none can be switched off.")));
        }
        y += ROW + GAP + 3;
        if (!bot) {
            return paragraph(this.columnX, y, this.columnWidth, this.spanish
                    ? "Sin bot: practica colocar y romper cristales y anclas a tu ritmo."
                    : "No bot: practise placing and breaking crystals and anchors at your own pace.", CrystalTheme.TEXT_MUTED);
        }
        PracticeSettings settings = CrystalVisualConfig.practice();
        PracticeSettings.Difficulty difficulty = settings.difficulty;
        label(this.columnX, y, this.spanish ? "Estilo" : "Style");
        y += 11;
        PracticeSettings.BotStyle[] styles = PracticeSettings.BotStyle.values();
        int styleWidth = (this.columnWidth - GAP) / 2;
        for (int index = 0; index < styles.length; index++) {
            PracticeSettings.BotStyle style = styles[index];
            PurpleCloseButton button = place(new PurpleCloseButton(this.columnX + (styleWidth + GAP) * index, y, styleWidth, ROW,
                    Component.literal(style.label(this.spanish)), ignored -> selectStyle(style)));
            button.setSelected(settings.style == style);
            button.setTooltip(Tooltip.create(Component.literal(style.note(this.spanish))));
        }
        y += ROW + GAP + 2;
        y = paragraph(this.columnX, y, this.columnWidth, settings.style.note(this.spanish), CrystalTheme.TEXT_MUTED) + 4;
        y = paragraph(this.columnX, y, this.columnWidth, timing(difficulty), CrystalTheme.TEXT_MUTED) + 4;
        y = paragraph(this.columnX, y, this.columnWidth, this.spanish
                ? "Sus habilidades vienen con la dificultad y no se pueden desactivar. Pasa el ratón por cada una para ver cómo se hace."
                : "Its skills come with the difficulty and cannot be switched off. Hover each one to see how it is done.",
                CrystalTheme.DANGER_BRIGHT) + 4;
        if (this.previewWidth > 0) {
            return y;
        }
        // Without room for the preview, the skills list goes in the column itself.
        this.skillsTop = y;
        for (BotSkill skill : BotSkill.of(difficulty, settings.style)) {
            this.lines.add(new Line(this.columnX, y, () -> "• " + skill.label(this.spanish), () -> skillColor(skill), false));
            y += 10;
        }
        return y;
    }

    private int buildWorld(int top) {
        int y = top;
        PracticeSettings settings = CrystalVisualConfig.practice();
        label(this.columnX, y, this.spanish ? "Tipo de mundo" : "World type");
        y += 11;
        PracticeSettings.WorldType[] types = PracticeSettings.WorldType.values();
        int typeWidth = (this.columnWidth - GAP * (types.length - 1)) / types.length;
        String[] shortNames = this.spanish ? new String[] {"Plano", "Hoyos", "Natural"} : new String[] {"Flat", "Holes", "Natural"};
        for (int index = 0; index < types.length; index++) {
            PracticeSettings.WorldType type = types[index];
            PurpleCloseButton button = place(new PurpleCloseButton(this.columnX + (typeWidth + GAP) * index, y, typeWidth, ROW,
                    Component.literal(shortNames[index]), ignored -> selectWorld(type)));
            button.setSelected(settings.worldType == type);
            button.setTooltip(Tooltip.create(Component.literal(type.label(this.spanish) + ": " + worldDescription(type))));
        }
        y += ROW + GAP + 2;
        y = paragraph(this.columnX, y, this.columnWidth, worldDescription(settings.worldType), CrystalTheme.TEXT_MUTED) + 4;
        if (settings.worldType == PracticeSettings.WorldType.NATURAL) {
            label(this.columnX, y, this.spanish ? "Bioma" : "Biome");
            y += 11;
            PracticeSettings.Biome[] biomes = PracticeSettings.Biome.values();
            // Four to a row when every name fits its button, else two: "Snowy plains" and "Cherry
            // grove" do not fit a quarter of the column in English.
            int widest = 0;
            for (PracticeSettings.Biome biome : biomes) {
                widest = Math.max(widest, this.font.width(biome.label(this.spanish)));
            }
            int columns = (this.columnWidth - GAP * 3) / 4 >= widest + 8 ? 4 : 2;
            int biomeWidth = (this.columnWidth - GAP * (columns - 1)) / columns;
            for (int index = 0; index < biomes.length; index++) {
                PracticeSettings.Biome biome = biomes[index];
                PurpleCloseButton button = place(new PurpleCloseButton(this.columnX + (biomeWidth + GAP) * (index % columns),
                        y + (ROW + GAP) * (index / columns), biomeWidth, ROW, Component.literal(biome.label(this.spanish)),
                        ignored -> selectBiome(biome)));
                button.setSelected(settings.biome == biome);
            }
            y += (ROW + GAP) * ((biomes.length + columns - 1) / columns) + 2;
            y = paragraph(this.columnX, y, this.columnWidth, this.spanish
                    ? "Cada bioma es un mundo propio: casi plano, pocos árboles y sin cuevas."
                    : "Each biome is a world of its own: almost flat, few trees and no caves.", CrystalTheme.TEXT_MUTED);
        }
        return paragraph(this.columnX, y + 2, this.columnWidth, this.spanish
                ? "La arena se reconstruye al empezar cada ronda: cada muerte deja el terreno como nuevo."
                : "The arena is rebuilt at the start of every round: each death leaves the ground as new.",
                CrystalTheme.TEXT_MUTED);
    }

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

    private static int skillColor(BotSkill skill) {
        return switch (skill.minimum) {
            case EASY -> CrystalTheme.TEXT_MUTED;
            case NORMAL -> CrystalTheme.ACCENT_BRIGHT;
            case HARD -> CrystalTheme.STATUS_PAUSED;
            case EXTREME -> 0xFFFF7A86;
        };
    }

    private <T extends AbstractWidget> T place(T widget) {
        addRenderableWidget(widget);
        this.placed.add(new Placed(widget, widget.getY()));
        return widget;
    }

    private void label(int x, int y, String text) {
        this.lines.add(new Line(x, y, () -> text, () -> CrystalTheme.LEGEND, false));
    }

    private void dynamic(int x, int y, Supplier<String> text, IntSupplier color) {
        this.lines.add(new Line(x, y, text, color, false));
    }

    /** Wrapped text in the column; returns the height below it. */
    private int paragraph(int x, int y, int width, String text, int color) {
        for (FormattedCharSequence line : this.font.split(Component.literal(text), width)) {
            StringBuilder plain = new StringBuilder();
            line.accept((index, style, codePoint) -> {
                plain.appendCodePoint(codePoint);
                return true;
            });
            String value = plain.toString();
            this.lines.add(new Line(x, y, () -> value, () -> color, false));
            y += this.font.lineHeight + 1;
        }
        return y;
    }

    /**
     * Moves the column's widgets by the scroll and hides the ones not fully inside it: a half-shown
     * widget would still take clicks outside the viewport.
     */
    private void applyScroll() {
        for (Placed entry : this.placed) {
            int y = entry.baseY() - this.scroll;
            entry.widget().setY(y);
            entry.widget().visible = y >= this.contentTop && y + entry.widget().getHeight() <= this.contentBottom;
        }
    }

    // ------------------------------------------------------------------------------------------
    // Actions
    // ------------------------------------------------------------------------------------------

    private void switchTab(Tab target) {
        if (this.tab != target) {
            this.tab = target;
            remembered = target;
            this.scroll = 0;
            rebuildWidgets();
        }
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
        return this.spanish
                ? "Te desconecta del servidor actual y abre el mundo de práctica."
                : "Disconnects you from the current server and opens the practice world.";
    }

    private void selectArmor(PracticeSettings.Armor armor) {
        CrystalVisualConfig.setPracticeArmor(armor.name());
        CrystalVisualConfig.save();
        refreshSelection();
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
        CrystalVisualConfig.save();
        refreshSelection();
    }

    private void selectKnockback(int level) {
        CrystalVisualConfig.setPracticeKnockback(level);
        CrystalVisualConfig.save();
        rebuildWidgets();
    }

    private void totems(boolean more) {
        KitPreset preset = CrystalVisualConfig.practice().preset;
        KitLayout kit = CrystalVisualConfig.practiceKit(preset);
        CrystalVisualConfig.setPracticeKit(preset, kit.withTotems(KitLayout.nextTotemStep(kit, more)));
        CrystalVisualConfig.save();
        rebuildWidgets();
    }

    private void selectPreset(KitPreset preset) {
        CrystalVisualConfig.setPracticePreset(preset.id);
        CrystalVisualConfig.save();
        rebuildWidgets();
    }

    private void toggleBot() {
        CrystalVisualConfig.setPracticeBot(!CrystalVisualConfig.practiceBot());
        CrystalVisualConfig.save();
        rebuildWidgets();
    }

    private void selectStyle(PracticeSettings.BotStyle style) {
        CrystalVisualConfig.setPracticeBotStyle(style.name());
        CrystalVisualConfig.save();
        rebuildWidgets();
    }

    private void cycleDifficulty() {
        CrystalVisualConfig.setPracticeBotDifficulty(CrystalVisualConfig.practice().difficulty.next().name());
        CrystalVisualConfig.save();
        rebuildWidgets();
    }

    private void selectWorld(PracticeSettings.WorldType type) {
        CrystalVisualConfig.setPracticeWorld(type.name());
        CrystalVisualConfig.save();
        rebuildWidgets();
    }

    private void selectBiome(PracticeSettings.Biome biome) {
        CrystalVisualConfig.setPracticeBiome(biome.id);
        CrystalVisualConfig.save();
        rebuildWidgets();
    }

    private void refreshSelection() {
        PracticeSettings settings = CrystalVisualConfig.practice();
        PracticeSettings.Armor[] armors = PracticeSettings.Armor.values();
        for (int index = 0; index < this.armorButtons.size(); index++) {
            this.armorButtons.get(index).setSelected(armors[index] == settings.armor);
        }
        for (int index = 0; index < this.blastButtons.size(); index++) {
            this.blastButtons.get(index).setSelected(settings.blast(PIECES[index]));
        }
    }

    private void enter() {
        CrystalVisualConfig.save();
        PracticeWorld.enter(this.minecraft, this.parent);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (this.maxScroll > 0 && mouseX >= this.columnX - 4 && mouseX <= this.columnX + this.columnWidth + 4
                && mouseY >= this.contentTop && mouseY <= this.contentBottom) {
            int direction = verticalAmount > 0.0D ? -1 : verticalAmount < 0.0D ? 1 : 0;
            if (direction != 0) {
                this.scroll = Mth.clamp(this.scroll + direction * 17, 0, this.maxScroll);
                applyScroll();
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    // ------------------------------------------------------------------------------------------
    // Drawing
    // ------------------------------------------------------------------------------------------

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0x9A05020A);
        this.minecraft.gui.extractDeferredSubtitles();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        long now = System.nanoTime();
        double seconds = now / 1_000_000_000.0D;
        float enter = CrystalTheme.easeOutCubic(Mth.clamp((now - this.openedAt) / (float) ENTER_NANOS, 0.0F, 1.0F));
        CrystalUi.floatingParticles(graphics, this.width, this.height, seconds);
        CrystalUi.panel(graphics, this.panelX, this.panelY, this.panelWidth, this.panelHeight,
                CrystalTheme.fade(0xEE1B0928, 0.4F + 0.6F * enter), CrystalTheme.fade(0xEE0A0310, 0.4F + 0.6F * enter));
        CrystalUi.roundedOutline(graphics, this.panelX, this.panelY, this.panelWidth, this.panelHeight,
                CrystalTheme.fade(CrystalTheme.PANEL_BORDER, enter));
        CrystalUi.hazardEdge(graphics, this.panelX + 4, this.panelY + 18, this.panelWidth - 8, now, enter * 0.7F);
        if (enter > 0.98F) {
            CrystalUi.comets(graphics, this.panelX, this.panelY, this.panelWidth, this.panelHeight, seconds, 0xFFE6C4FF);
        }
        CrystalUi.crystalIcon(graphics, this.panelX + 14, this.panelY + 10, 9, seconds, enter);
        String title = this.spanish ? "Práctica de cristales" : "Crystal Practice";
        CrystalUi.label(graphics, this.font, title, this.panelX + 24, this.panelY + 6, CrystalTheme.fade(CrystalTheme.TITLE, enter));
        String badge = "EXPERIMENTAL";
        int badgeWidth = this.font.width(badge) + 8;
        int badgeX = this.panelX + this.panelWidth - badgeWidth - 8;
        float alarm = 0.5F + 0.5F * (float) Math.sin(seconds * 5.0D);
        graphics.fill(badgeX, this.panelY + 5, badgeX + badgeWidth, this.panelY + 16,
                CrystalTheme.fade(CrystalTheme.lerp(0xFF7A0A16, 0xFFB0101E, alarm), enter));
        CrystalUi.label(graphics, this.font, badge, badgeX + 4, this.panelY + 7, CrystalTheme.fade(0xFFFFE4E8, enter));

        graphics.enableScissor(this.columnX - 4, this.contentTop - 1, this.columnX + this.columnWidth + 4, this.contentBottom);
        for (Line line : this.lines) {
            int y = line.baseY() - this.scroll;
            if (y + this.font.lineHeight < this.contentTop || y > this.contentBottom) {
                continue;
            }
            String text = line.text().get();
            int x = line.centered() ? line.x() - this.font.width(text) / 2 : line.x();
            CrystalUi.label(graphics, this.font, text, x, y, CrystalTheme.fade(line.color().getAsInt(), enter));
        }
        graphics.disableScissor();
        drawScrollbar(graphics);

        if (this.previewWidth > 0) {
            switch (this.tab) {
                case GEAR -> drawGearPreview(graphics, enter, seconds);
                case BOT -> drawBotPreview(graphics, enter, mouseX, mouseY);
                case WORLD -> drawWorldPreview(graphics, enter, seconds);
            }
        }
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        if (this.tab == Tab.BOT && this.previewWidth == 0 && this.skillsTop >= 0) {
            drawColumnSkillTooltip(graphics, mouseX, mouseY);
        }
    }

    private void drawScrollbar(GuiGraphicsExtractor graphics) {
        if (this.maxScroll <= 0) {
            return;
        }
        int trackX = this.columnX + this.columnWidth + 3;
        int height = this.contentBottom - this.contentTop;
        graphics.fill(trackX, this.contentTop, trackX + 2, this.contentBottom, CrystalTheme.SCROLL_TRACK);
        int thumb = Math.max(12, height * height / (height + this.maxScroll));
        int thumbY = this.contentTop + Math.round((height - thumb) * this.scroll / (float) this.maxScroll);
        graphics.fill(trackX, thumbY, trackX + 2, thumbY + thumb, CrystalTheme.SCROLL_THUMB);
    }

    /** Your kit as it will be handed out: the armour and the hotbar, off hand included. */
    private void drawGearPreview(GuiGraphicsExtractor graphics, float enter, double seconds) {
        PracticeSettings settings = CrystalVisualConfig.practice();
        KitLayout kit = CrystalVisualConfig.practiceKit(settings.preset);
        int x = this.previewX;
        int y = this.contentTop;
        int width = this.previewWidth;
        int height = this.contentBottom - this.contentTop;
        CrystalUi.card(graphics, this.font, x, y, width, height, null, 0.35F + 0.25F * (float) Math.sin(seconds * 2.0D), enter);
        CrystalUi.centered(graphics, this.font, this.font.plainSubstrByWidth(
                        (this.spanish ? "Tu kit y el del bot · " : "Your kit and the bot's · ") + settings.preset.label(this.spanish),
                        width - 8), x + width / 2, y + 5, CrystalTheme.fade(CrystalTheme.ACCENT_BRIGHT, enter));
        String material = settings.armor.name().toLowerCase(Locale.ROOT);
        String[] pieces = {"helmet", "chestplate", "leggings", "boots"};
        int rowY = y + 18;
        int step = Math.max(18, Math.min(24, (height - 80) / 4));
        int textWidth = width - 30;
        for (int index = 0; index < pieces.length; index++) {
            PracticeIcons.texture(graphics, "item/" + material + "_" + pieces[index], x + 6, rowY, true, seconds + index * 0.4D);
            boolean blast = settings.blast(PIECES[index]);
            String protection = blast
                    ? fit(textWidth, this.spanish ? "Protección contra explosiones IV" : "Blast Protection IV",
                            this.spanish ? "Prot. explosiones IV" : "Blast Prot. IV")
                    : fit(textWidth, this.spanish ? "Protección IV" : "Protection IV", "Prot. IV");
            CrystalUi.label(graphics, this.font, protection, x + 26, rowY, CrystalTheme.fade(blast ? 0xFFFFC48A : CrystalTheme.TEXT, enter));
            CrystalUi.label(graphics, this.font, fit(textWidth,
                            this.spanish ? "Irrompibilidad III · Reparación" : "Unbreaking III · Mending",
                            this.spanish ? "Irromp. III · Rep." : "Unbr. III · Mend."),
                    x + 26, rowY + 9, CrystalTheme.fade(CrystalTheme.TEXT_MUTED, enter));
            rowY += step;
        }
        String sword = (this.spanish ? "Espada: Filo V · Empuje " : "Sword: Sharpness V · Knockback ")
                + PracticeSettings.roman(settings.knockback);
        CrystalUi.label(graphics, this.font, fit(width - 12, sword), x + 6, rowY + 2, CrystalTheme.fade(CrystalTheme.TEXT, enter));
        // The hotbar and off hand, scaled to fit the card.
        int slot = Math.max(10, Math.min(16, (width - 18) / 10));
        int barWidth = slot * 9 + 4 + slot;
        int barX = x + (width - barWidth) / 2;
        int barY = y + height - slot - 18;
        for (int index = 0; index < 9; index++) {
            miniSlot(graphics, kit.get(index), settings, barX + index * slot, barY, slot, seconds);
        }
        miniSlot(graphics, kit.get(KitLayout.OFFHAND), settings, barX + 9 * slot + 4, barY, slot, seconds);
        String counts = kit.totems() + (this.spanish ? " tótems · " : " totems · ") + kit.count(KitItem.END_CRYSTAL)
                + (this.spanish ? " cristales · " : " crystals · ") + kit.count(KitItem.RESPAWN_ANCHOR)
                + (this.spanish ? " anclas" : " anchors");
        CrystalUi.centered(graphics, this.font, fit(width - 8, counts), x + width / 2, barY + slot + 4,
                CrystalTheme.fade(CrystalTheme.TEXT_MUTED, enter));
    }

    private void miniSlot(GuiGraphicsExtractor graphics, KitLayout.Entry entry, PracticeSettings settings, int x, int y, int size,
            double seconds) {
        graphics.fill(x, y, x + size, y + size, CrystalTheme.CARD_BORDER);
        graphics.fill(x + 1, y + 1, x + size - 1, y + size - 1, 0xC0160A20);
        if (entry == null) {
            return;
        }
        String texture = entry.item().texture(settings.armor);
        net.minecraft.resources.Identifier location = net.minecraft.resources.Identifier.withDefaultNamespace(
                "textures/" + texture + ".png");
        graphics.blit(location, x + 1, y + 1, x + size - 1, y + size - 1, 0.0F, 1.0F, 0.0F, 1.0F);
    }

    /** Everything the bot will do, easiest first, each with how it works on hover. */
    private void drawBotPreview(GuiGraphicsExtractor graphics, float enter, int mouseX, int mouseY) {
        PracticeSettings settings = CrystalVisualConfig.practice();
        int x = this.previewX;
        int y = this.contentTop;
        int width = this.previewWidth;
        int height = this.contentBottom - this.contentTop;
        CrystalUi.card(graphics, this.font, x, y, width, height, null, 0.3F, settings.bot ? enter : enter * 0.45F);
        String title = settings.bot
                ? (this.spanish ? "Lo que hará el bot · " : "What the bot will do · ") + settings.difficulty.label(this.spanish)
                        + " · " + settings.style.label(this.spanish)
                : (this.spanish ? "Sin bot" : "No bot");
        CrystalUi.centered(graphics, this.font, fit(width - 8, title), x + width / 2, y + 5,
                CrystalTheme.fade(settings.bot ? CrystalTheme.ACCENT_BRIGHT : CrystalTheme.TEXT_DISABLED, enter));
        if (!settings.bot) {
            return;
        }
        List<BotSkill> skills = BotSkill.of(settings.difficulty, settings.style);
        int lineHeight = skills.size() * 10 <= height - 22 ? 10 : 9;
        int columns = skills.size() * lineHeight > height - 22 && width >= 200 ? 2 : 1;
        int perColumn = (skills.size() + columns - 1) / columns;
        int columnWidth = (width - 12) / columns;
        BotSkill hovered = null;
        for (int index = 0; index < skills.size(); index++) {
            BotSkill skill = skills.get(index);
            int column = index / perColumn;
            int lineX = x + 6 + column * columnWidth;
            int lineY = y + 18 + (index % perColumn) * lineHeight;
            if (lineY + lineHeight > y + height - 2) {
                continue;
            }
            CrystalUi.label(graphics, this.font, fit(columnWidth - 4, "• " + skill.label(this.spanish)), lineX, lineY,
                    CrystalTheme.fade(skillColor(skill), enter));
            if (mouseX >= lineX && mouseX < lineX + columnWidth - 4 && mouseY >= lineY && mouseY < lineY + lineHeight) {
                hovered = skill;
            }
        }
        if (hovered != null) {
            skillTooltip(graphics, hovered, mouseX, mouseY);
        }
    }

    private void drawColumnSkillTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (mouseX < this.columnX || mouseX > this.columnX + this.columnWidth || mouseY < this.contentTop
                || mouseY > this.contentBottom) {
            return;
        }
        PracticeSettings current = CrystalVisualConfig.practice();
        List<BotSkill> skills = BotSkill.of(current.difficulty, current.style);
        int index = (mouseY + this.scroll - this.skillsTop) / 10;
        if (mouseY + this.scroll >= this.skillsTop && index >= 0 && index < skills.size()) {
            skillTooltip(graphics, skills.get(index), mouseX, mouseY);
        }
    }

    private void skillTooltip(GuiGraphicsExtractor graphics, BotSkill skill, int mouseX, int mouseY) {
        List<FormattedCharSequence> tip = new ArrayList<>();
        tip.addAll(CrystalUi.wrap(this.font, skill.label(this.spanish) + " · " + skill.minimum.label(this.spanish), 220));
        tip.addAll(CrystalUi.wrap(this.font, skill.how(this.spanish), 220));
        CrystalUi.tooltip(graphics, this.font, tip, mouseX, mouseY, this.width, this.height);
    }

    /** A cut through the chosen ground, drawn from the blocks it is made of. */
    private void drawWorldPreview(GuiGraphicsExtractor graphics, float enter, double seconds) {
        PracticeSettings settings = CrystalVisualConfig.practice();
        int x = this.previewX;
        int y = this.contentTop;
        int width = this.previewWidth;
        int height = this.contentBottom - this.contentTop;
        CrystalUi.card(graphics, this.font, x, y, width, height, null, 0.35F + 0.25F * (float) Math.sin(seconds * 2.0D), enter);
        String name = settings.worldType == PracticeSettings.WorldType.NATURAL
                ? settings.worldType.label(this.spanish) + " · " + settings.biome.label(this.spanish)
                : settings.worldType.label(this.spanish);
        CrystalUi.centered(graphics, this.font, fit(width - 8, name), x + width / 2, y + 5,
                CrystalTheme.fade(CrystalTheme.ACCENT_BRIGHT, enter));
        String[] ground = groundTextures(settings);
        int blocks = Math.max(3, Math.min(9, (width - 12) / 16));
        int stripX = x + (width - blocks * 16) / 2;
        int stripY = y + Math.max(40, height / 2 - 8);
        for (int index = 0; index < blocks; index++) {
            boolean hole = settings.worldType == PracticeSettings.WorldType.HOLES && (index == 1 || index == blocks - 2);
            int columnX = stripX + index * 16;
            if (hole) {
                PracticeIcons.texture(graphics, "block/bedrock", columnX, stripY + 16, false, seconds);
            } else {
                PracticeIcons.texture(graphics, ground[0], columnX, stripY, false, seconds);
                PracticeIcons.texture(graphics, ground[1], columnX, stripY + 16, false, seconds);
            }
            PracticeIcons.texture(graphics, ground[1], columnX, stripY + 32, false, seconds);
        }
        int middle = stripX + (blocks / 2) * 16;
        if (settings.worldType == PracticeSettings.WorldType.FLAT) {
            PracticeIcons.texture(graphics, "block/obsidian", middle, stripY - 16, false, seconds);
            PracticeIcons.texture(graphics, "item/end_crystal", middle, stripY - 32, false, seconds);
        } else if (ground.length > 2) {
            PracticeIcons.texture(graphics, ground[2], middle + 16, stripY - 16, false, seconds);
        }
        List<FormattedCharSequence> facts = CrystalUi.wrap(this.font, worldDescription(settings.worldType), width - 12);
        int factsY = stripY + 52;
        for (FormattedCharSequence line : facts) {
            if (factsY + this.font.lineHeight > y + height - 2) {
                break;
            }
            graphics.text(this.font, line, x + 6, factsY, CrystalTheme.fade(CrystalTheme.TEXT_MUTED, enter), false);
            factsY += this.font.lineHeight + 1;
        }
    }

    /** Surface, what lies under it, and something that grows on it. */
    private static String[] groundTextures(PracticeSettings settings) {
        return switch (settings.worldType) {
            case FLAT -> new String[] {"block/netherite_block", "block/netherite_block"};
            case HOLES -> new String[] {"block/obsidian", "block/obsidian"};
            case NATURAL -> switch (settings.biome) {
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
        return this.font.plainSubstrByWidth(variants[variants.length - 1], width);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }
}
