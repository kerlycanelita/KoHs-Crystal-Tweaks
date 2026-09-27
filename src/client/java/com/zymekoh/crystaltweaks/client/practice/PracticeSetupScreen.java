package com.zymekoh.crystaltweaks.client.practice;

import com.zymekoh.crystaltweaks.client.CrystalTheme;
import com.zymekoh.crystaltweaks.client.CrystalUi;
import com.zymekoh.crystaltweaks.client.CrystalVisualConfig;
import com.zymekoh.crystaltweaks.client.PurpleCloseButton;
import com.zymekoh.crystaltweaks.practice.PracticeSettings;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

/**
 * The Crystal Practice window: armour on the left, what the player and the bot will wear on the
 * right, and the way in at the bottom.
 *
 * <p>Protection IV, Unbreaking III and Mending are on every piece, always; the only armour choice
 * besides the material is which pieces, at most two, take Blast Protection IV instead of
 * Protection IV, since Minecraft does not allow both on one piece.</p>
 */
public final class PracticeSetupScreen extends Screen {
    private static final long ENTER_NANOS = 360_000_000L;
    private static final long REFUSE_NANOS = 1_600_000_000L;
    private static final int[] PIECES = {PracticeSettings.HEAD, PracticeSettings.CHEST, PracticeSettings.LEGS, PracticeSettings.FEET};

    private final Screen parent;
    private final boolean spanish;
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
    private int armorLabelY;
    private int blastLabelY;
    private int botLabelY;
    private int noteY;
    private List<FormattedCharSequence> mandatory = List.of();
    private final List<PurpleCloseButton> armorButtons = new ArrayList<>();
    private final List<PurpleCloseButton> blastButtons = new ArrayList<>();

    public PracticeSetupScreen(Screen parent) {
        super(Component.literal("Crystal Practice"));
        this.parent = parent;
        this.spanish = CrystalUi.spanish();
    }

    @Override
    protected void init() {
        if (this.openedAt == 0L) {
            this.openedAt = System.nanoTime();
        }
        this.armorButtons.clear();
        this.blastButtons.clear();
        int margin = Mth.clamp(this.width / 24, 6, 20);
        this.panelWidth = Math.max(180, Math.min(440, this.width - margin * 2));
        this.panelHeight = Math.max(170, Math.min(270, this.height - margin * 2));
        this.panelX = (this.width - this.panelWidth) / 2;
        this.panelY = (this.height - this.panelHeight) / 2;
        boolean preview = this.panelWidth >= 330 && this.panelHeight >= 190;
        this.columnX = this.panelX + 10;
        this.columnWidth = preview ? Math.min(200, (this.panelWidth - 30) * 11 / 20) : this.panelWidth - 20;
        this.previewX = this.columnX + this.columnWidth + 10;
        this.previewWidth = preview ? this.panelX + this.panelWidth - 10 - this.previewX : 0;
        this.contentTop = this.panelY + 26;

        int row = 14;
        int gap = 3;
        int y = this.contentTop;
        this.armorLabelY = y;
        y += 11;
        PracticeSettings.Armor[] armors = PracticeSettings.Armor.values();
        int armorWidth = (this.columnWidth - gap * (armors.length - 1)) / armors.length;
        for (int index = 0; index < armors.length; index++) {
            PracticeSettings.Armor armor = armors[index];
            PurpleCloseButton button = addRenderableWidget(new PurpleCloseButton(
                    this.columnX + (armorWidth + gap) * index, y, armorWidth, row,
                    Component.literal(armor.label(this.spanish)), ignored -> selectArmor(armor)));
            this.armorButtons.add(button);
        }
        y += row + 3;
        this.mandatory = this.font.split(Component.literal(this.spanish
                ? "En cada pieza: Protección IV, Irrompibilidad III y Reparación."
                : "On every piece: Protection IV, Unbreaking III and Mending."), this.columnWidth);
        y += this.mandatory.size() * (this.font.lineHeight + 1) + 4;
        this.blastLabelY = y;
        y += 11;
        String[] pieces = this.spanish
                ? new String[] {"Casco", "Peto", "Pantalones", "Botas"}
                : new String[] {"Helmet", "Chest", "Leggings", "Boots"};
        int pieceWidth = (this.columnWidth - gap) / 2;
        for (int index = 0; index < PIECES.length; index++) {
            int piece = PIECES[index];
            PurpleCloseButton button = addRenderableWidget(new PurpleCloseButton(
                    this.columnX + (pieceWidth + gap) * (index % 2), y + (row + gap) * (index / 2), pieceWidth, row,
                    Component.literal(pieces[index]), ignored -> toggleBlast(piece)));
            button.setTooltip(Tooltip.create(Component.literal(this.spanish
                    ? "Protección contra explosiones IV en lugar de Protección IV. Máximo 2 piezas."
                    : "Blast Protection IV instead of Protection IV. At most 2 pieces.")));
            this.blastButtons.add(button);
        }
        y += (row + gap) * 2 + 3;
        this.botLabelY = y;
        y += 11;
        boolean bot = CrystalVisualConfig.practiceBot();
        int switchWidth = bot ? (this.columnWidth - gap) * 3 / 5 : this.columnWidth;
        addRenderableWidget(new PurpleCloseButton(this.columnX, y, switchWidth, row,
                Component.literal(this.spanish ? "Generar bot" : "Spawn bot"), ignored -> toggleBot())
                .switchOf(CrystalVisualConfig::practiceBot).icon(PurpleCloseButton.Icon.SWORDS))
                .setTooltip(Tooltip.create(Component.literal(this.spanish
                        ? "Un rival que se mueve, esquiva, pone obsidiana y cristales, los rompe, come manzanas y recoloca tótems, con tu misma armadura y encantamientos."
                        : "An opponent that moves, dodges, places obsidian and crystals, breaks them, eats golden apples and re-equips totems, in your same armour and enchantments.")));
        if (bot) {
            PurpleCloseButton difficulty = addRenderableWidget(new PurpleCloseButton(this.columnX + switchWidth + gap, y,
                    this.columnWidth - switchWidth - gap, row, Component.literal(difficulty().label(this.spanish)),
                    ignored -> cycleDifficulty()));
            difficulty.setTooltip(Tooltip.create(Component.literal(this.spanish
                    ? "Fácil reacciona despacio y elige peor; Extremo reacciona en dos ticks y casi no falla."
                    : "Easy reacts slowly and picks worse spots; Extreme reacts in two ticks and hardly misses.")));
        }
        this.noteY = y + row + 4;

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

    private PracticeSettings.Difficulty difficulty() {
        return CrystalVisualConfig.practice().difficulty;
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

    private void toggleBot() {
        CrystalVisualConfig.setPracticeBot(!CrystalVisualConfig.practiceBot());
        CrystalVisualConfig.save();
        rebuildWidgets();
    }

    private void cycleDifficulty() {
        CrystalVisualConfig.setPracticeBotDifficulty(difficulty().next().name());
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
        String badge = this.spanish ? "EXPERIMENTAL" : "EXPERIMENTAL";
        int badgeWidth = this.font.width(badge) + 8;
        int badgeX = this.panelX + this.panelWidth - badgeWidth - 8;
        float alarm = 0.5F + 0.5F * (float) Math.sin(seconds * 5.0D);
        graphics.fill(badgeX, this.panelY + 5, badgeX + badgeWidth, this.panelY + 16,
                CrystalTheme.fade(CrystalTheme.lerp(0xFF7A0A16, 0xFFB0101E, alarm), enter));
        CrystalUi.label(graphics, this.font, badge, badgeX + 4, this.panelY + 7, CrystalTheme.fade(0xFFFFE4E8, enter));

        CrystalUi.label(graphics, this.font, this.spanish ? "Armadura" : "Armour", this.columnX, this.armorLabelY,
                CrystalTheme.fade(CrystalTheme.LEGEND, enter));
        int lineY = this.armorLabelY + 11 + 14 + 3;
        for (FormattedCharSequence line : this.mandatory) {
            graphics.text(this.font, line, this.columnX, lineY, CrystalTheme.fade(CrystalTheme.TEXT_MUTED, enter), false);
            lineY += this.font.lineHeight + 1;
        }
        int selected = Integer.bitCount(PracticeSettings.sanitize(CrystalVisualConfig.practiceBlastPieces()));
        String blastLabel = (this.spanish ? "Protección contra explosiones IV" : "Blast Protection IV") + " " + selected + "/2";
        boolean refusing = now - this.refusedAt < REFUSE_NANOS;
        int blastColor = refusing ? CrystalTheme.lerp(CrystalTheme.DANGER, 0xFFFFFFFF, alarm * 0.4F) : CrystalTheme.LEGEND;
        CrystalUi.label(graphics, this.font, refusing ? (this.spanish ? "Máximo 2 piezas" : "At most 2 pieces") : blastLabel,
                this.columnX, this.blastLabelY, CrystalTheme.fade(blastColor, enter));
        CrystalUi.label(graphics, this.font, "Bot", this.columnX, this.botLabelY, CrystalTheme.fade(CrystalTheme.LEGEND, enter));
        String note = CrystalVisualConfig.practiceBot()
                ? (this.spanish ? "Usa tu misma armadura y encantamientos." : "Wears your same armour and enchantments.")
                : (this.spanish ? "Sin bot: practica colocar y romper." : "No bot: practise placing and breaking.");
        int noteLineY = this.noteY;
        for (FormattedCharSequence line : this.font.split(Component.literal(note), this.columnWidth)) {
            if (noteLineY + this.font.lineHeight >= this.panelY + this.panelHeight - 28) {
                break;
            }
            graphics.text(this.font, line, this.columnX, noteLineY, CrystalTheme.fade(CrystalTheme.TEXT_MUTED, enter), false);
            noteLineY += this.font.lineHeight + 1;
        }
        if (this.previewWidth > 0) {
            drawLoadouts(graphics, enter, seconds);
        }
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    /** What the player and the bot will wear and carry, drawn from real item stacks. */
    private void drawLoadouts(GuiGraphicsExtractor graphics, float enter, double seconds) {
        PracticeSettings settings = CrystalVisualConfig.practice();
        int top = this.contentTop;
        int bottom = this.panelY + this.panelHeight - 30;
        int cardWidth = (this.previewWidth - 4) / 2;
        drawCard(graphics, this.previewX, top, cardWidth, bottom - top, this.spanish ? "Tú" : "You", settings, true, enter, seconds);
        drawCard(graphics, this.previewX + cardWidth + 4, top, cardWidth, bottom - top,
                settings.bot ? "Bot · " + settings.difficulty.label(this.spanish) : (this.spanish ? "Sin bot" : "No bot"),
                settings, settings.bot, enter, seconds);
    }

    private void drawCard(GuiGraphicsExtractor graphics, int x, int y, int width, int height, String title,
            PracticeSettings settings, boolean lit, float enter, double seconds) {
        float alpha = enter * (lit ? 1.0F : 0.45F);
        CrystalUi.card(graphics, this.font, x, y, width, height, null, lit ? 0.35F + 0.25F * (float) Math.sin(seconds * 2.0D) : 0.0F, alpha);
        CrystalUi.centered(graphics, this.font, this.font.plainSubstrByWidth(title, width - 6), x + width / 2, y + 4,
                CrystalTheme.fade(lit ? CrystalTheme.ACCENT_BRIGHT : CrystalTheme.TEXT_DISABLED, alpha));
        String material = settings.armor.name().toLowerCase(Locale.ROOT);
        String[] pieces = {"helmet", "chestplate", "leggings", "boots"};
        int textWidth = width - 28;
        // Each piece gets three lines, one per enchantment, where the card is tall enough, and the
        // two shared ones on a single line where it is not.
        int step = Math.max(19, Math.min(32, (height - 42) / 4));
        boolean threeLines = step >= 30;
        int rowY = y + 15;
        for (int index = 0; index < 4; index++) {
            icon(graphics, "item/" + material + "_" + pieces[index], x + 5, rowY + (threeLines ? 5 : 1), lit,
                    seconds + index * 0.4D);
            boolean blast = settings.blast(PIECES[index]);
            String protection = blast
                    ? fit(textWidth, this.spanish ? "Protección explosiones IV" : "Blast Protection IV",
                            this.spanish ? "Prot. explosiones IV" : "Blast Prot. IV", this.spanish ? "Explosiones IV" : "Blast IV")
                    : fit(textWidth, this.spanish ? "Protección IV" : "Protection IV", "Prot. IV");
            CrystalUi.label(graphics, this.font, protection, x + 24, rowY, CrystalTheme.fade(blast ? 0xFFFFC48A : CrystalTheme.TEXT, alpha));
            int muted = CrystalTheme.fade(CrystalTheme.TEXT_MUTED, alpha);
            if (threeLines) {
                CrystalUi.label(graphics, this.font, fit(textWidth, this.spanish ? "Irrompibilidad III" : "Unbreaking III",
                        this.spanish ? "Irromp. III" : "Unbr. III"), x + 24, rowY + 9, muted);
                CrystalUi.label(graphics, this.font, fit(textWidth, this.spanish ? "Reparación" : "Mending",
                        this.spanish ? "Rep." : "Mend."), x + 24, rowY + 18, muted);
            } else {
                CrystalUi.label(graphics, this.font, fit(textWidth,
                        this.spanish ? "Irrompibilidad III · Reparación" : "Unbreaking III · Mending",
                        this.spanish ? "Irromp. III · Reparación" : "Unbr. III · Mending",
                        this.spanish ? "Irromp. III · Rep." : "Unbr. III · Mend.",
                        this.spanish ? "I III · R" : "U III · M"), x + 24, rowY + 9, muted);
            }
            rowY += step;
        }
        String[] kit = {"item/" + material + "_sword", "item/end_crystal", "block/obsidian", "item/totem_of_undying",
                "item/golden_apple"};
        int kitY = y + height - 20;
        int spacing = Math.max(16, Math.min(18, (width - 8) / kit.length));
        int kitX = x + (width - spacing * kit.length) / 2 + 1;
        for (int index = 0; index < kit.length; index++) {
            icon(graphics, kit[index], kitX + spacing * index, kitY, lit && index == 0, seconds);
        }
        if (!lit) {
            graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, CrystalTheme.fade(0x9A0A0310, enter));
        }
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

    /**
     * An item drawn straight from its texture file. Item stacks cannot be built on the title screen
     * in 26.x, where item components are only bound once a world loads, so the preview never makes
     * one; a sweep of light stands in for the enchantment glint.
     */
    private static void icon(GuiGraphicsExtractor graphics, String texture, int x, int y, boolean glint, double seconds) {
        Identifier location = Identifier.withDefaultNamespace("textures/" + texture + ".png");
        graphics.blit(location, x, y, x + 16, y + 16, 0.0F, 1.0F, 0.0F, 1.0F);
        if (glint) {
            int sweep = (int) Math.floor((seconds * 14.0D) % 40.0D) - 12;
            for (int row = 0; row < 16; row++) {
                int left = x + sweep + row / 2;
                int right = Math.min(x + 16, left + 3);
                if (left >= x && left < right) {
                    graphics.fill(left, y + row, right, y + row + 1, 0x55C88CFF);
                }
            }
        }
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
