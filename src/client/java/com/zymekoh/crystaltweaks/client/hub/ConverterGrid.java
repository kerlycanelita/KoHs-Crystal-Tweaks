package com.zymekoh.crystaltweaks.client.hub;

import com.zymekoh.crystaltweaks.client.CrystalConverter;
import com.zymekoh.crystaltweaks.client.CrystalTheme;
import com.zymekoh.crystaltweaks.client.CrystalUi;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The catalogue of Converter My Crystal: every item and block, or every entity, of this game
 * version, narrowed by a search and chosen with a click. It scrolls inside its own bounds, so the
 * hitbox is always what is drawn.
 */
public final class ConverterGrid extends AbstractWidget implements Clippable {
    /**
     * One thing a crystal can be drawn as.
     *
     * @param target what the profile stores, see {@link CrystalConverter}
     * @param search its id and its name in the player's language, in lower case
     */
    public record Entry(String target, ItemStack icon, Component name, String search) {
    }

    private static final int CELL = 20;
    /** Entities that draw nothing by themselves, or flashes of something far larger than a crystal. */
    private static final Set<String> NOT_OFFERED = Set.of("marker", "interaction", "area_effect_cloud", "item", "item_display",
            "block_display", "text_display", "falling_block", "end_crystal", "lightning_bolt", "ominous_item_spawner");
    private static List<Entry> items;
    private static List<Entry> entities;

    private static String namedTarget;
    private static String namedAs;

    private final Font font;
    private final String needsWorld;
    private final String noMatch;
    private final Supplier<String> chosen;
    private final Consumer<String> choose;
    private final List<Entry> shown = new ArrayList<>();
    private List<Entry> source = List.of();
    private HubLayout.Rect clip;
    private int scrollTarget;
    private float scroll;
    private long lastFrame;

    /**
     * @param needsWorld what the grid says while the game cannot make items yet
     * @param noMatch    what it says when the search leaves nothing
     */
    public ConverterGrid(Font font, Component message, String needsWorld, String noMatch, Supplier<String> chosen,
            Consumer<String> choose) {
        super(0, 0, 0, 0, message);
        this.font = font;
        this.needsWorld = needsWorld;
        this.noMatch = noMatch;
        this.chosen = chosen;
        this.choose = choose;
    }

    /** Every item, blocks first: a block is what stands in for a crystal best. Empty until the game can make items. */
    public static List<Entry> items() {
        if (items == null) {
            if (!CrystalConverter.itemsReady()) {
                return List.of();
            }
            List<Entry> found = new ArrayList<>();
            for (Item item : BuiltInRegistries.ITEM) {
                try {
                    ItemStack stack = new ItemStack(item);
                    if (item != Items.AIR && !stack.isEmpty()) {
                        found.add(entry(CrystalConverter.ITEM, BuiltInRegistries.ITEM.getKey(item), stack, stack.getHoverName()));
                    }
                } catch (RuntimeException unbound) {
                    // 26.1.2 exposes a few internal registry entries before their components are bound.
                }
            }
            found.sort(Comparator.comparing((Entry entry) -> !(entry.icon().getItem() instanceof BlockItem))
                    .thenComparing(Entry::target));
            items = found;
        }
        return items;
    }

    /** Every entity a command could summon, by its spawn egg where it has one. */
    public static List<Entry> entities() {
        if (entities == null) {
            if (!CrystalConverter.itemsReady()) {
                // Their icons are items.
                return List.of();
            }
            List<Entry> found = new ArrayList<>();
            // By its full name: the build rewrites this file's EntityType import for the versions
            // that moved the type constants, and the class itself is wanted here.
            for (net.minecraft.world.entity.EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
                Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
                if (type.canSummon() && !NOT_OFFERED.contains(id.getPath())) {
                    found.add(entry(CrystalConverter.ENTITY, id, icon(id), type.getDescription()));
                }
            }
            found.sort(Comparator.comparing(Entry::target));
            entities = found;
        }
        return entities;
    }

    private static Entry entry(String kind, Identifier id, ItemStack icon, Component name) {
        return new Entry(kind + id, icon, name, (id + " " + name.getString()).toLowerCase(Locale.ROOT));
    }

    /** The entity's spawn egg, the item of its own name (a boat, a minecart), or a name tag. */
    private static ItemStack icon(Identifier id) {
        Item item = BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath(id.getNamespace(), id.getPath() + "_spawn_egg"));
        if (item == Items.AIR) {
            item = BuiltInRegistries.ITEM.getValue(id);
        }
        return new ItemStack(item == Items.AIR ? Items.NAME_TAG : item);
    }

    /** The name of a stored choice, for a label; the choice itself when this version has no such thing. */
    public static String nameOf(String target) {
        if (target.equals(namedTarget)) {
            return namedAs;
        }
        List<Entry> catalogue = target.startsWith(CrystalConverter.ENTITY) ? entities() : items();
        String name = target;
        for (Entry entry : catalogue) {
            if (entry.target().equals(target)) {
                name = entry.name().getString();
                break;
            }
        }
        if (!catalogue.isEmpty()) {
            // Asked every frame for the label: looked up once per choice.
            namedTarget = target;
            namedAs = name;
        }
        return name;
    }

    /** What the grid lists, and the search that narrows it. */
    public void show(List<Entry> source, String query) {
        this.source = source;
        String wanted = query.strip().toLowerCase(Locale.ROOT);
        this.shown.clear();
        for (Entry entry : source) {
            if (wanted.isEmpty() || entry.search().contains(wanted)) {
                this.shown.add(entry);
            }
        }
        this.scrollTarget = 0;
        this.scroll = 0.0F;
    }

    public List<Entry> source() {
        return this.source;
    }

    private int columns() {
        return Math.max(1, (this.width - 8) / CELL);
    }

    private int maxScroll() {
        int rows = (this.shown.size() + columns() - 1) / columns();
        return Math.max(0, rows * CELL - (this.height - 4));
    }

    private int gridLeft() {
        return getX() + (this.width - 4 - columns() * CELL) / 2;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        HubSkin skin = HubSkin.current();
        float fade = Mth.clamp(this.alpha, 0.0F, 1.0F);
        long now = System.nanoTime();
        float frame = this.lastFrame == 0L ? 16.0F : Math.min(100.0F, (now - this.lastFrame) / 1_000_000.0F);
        this.lastFrame = now;
        this.scrollTarget = Math.max(0, Math.min(maxScroll(), this.scrollTarget));
        this.scroll = HubMotion.damp(this.scroll, this.scrollTarget, frame, 70.0F);
        int left = getX();
        int top = getY();
        graphics.fill(left, top, left + this.width, top + this.height, CrystalTheme.fade(0x80100618, fade));
        CrystalUi.outline(graphics, left, top, this.width, this.height, CrystalTheme.fade(CrystalTheme.withAlpha(skin.border, 150), fade));
        if (this.shown.isEmpty()) {
            CrystalUi.centered(graphics, this.font, HubDraw.fit(this.font, this.source.isEmpty() ? this.needsWorld : this.noMatch,
                    this.width - 8), left + this.width / 2, top + (this.height - this.font.lineHeight) / 2,
                    CrystalTheme.fade(skin.muted, fade));
            return;
        }
        int columns = columns();
        int gridLeft = gridLeft();
        int offset = Math.round(this.scroll);
        String chosen = this.chosen.get();
        boolean hot = this.active && isMouseOver(mouseX, mouseY);
        graphics.enableScissor(left + 1, top + 1, left + this.width - 1, top + this.height - 1);
        int firstRow = offset / CELL;
        int lastRow = (offset + this.height) / CELL;
        for (int row = firstRow; row <= lastRow; row++) {
            for (int column = 0; column < columns; column++) {
                int index = row * columns + column;
                if (index >= this.shown.size()) {
                    break;
                }
                Entry entry = this.shown.get(index);
                int x = gridLeft + column * CELL;
                int y = top + 2 + row * CELL - offset;
                boolean selected = entry.target().equals(chosen);
                boolean hovered = hot && mouseX >= x && mouseX < x + CELL && mouseY >= y && mouseY < y + CELL;
                int fill = selected ? CrystalTheme.withAlpha(skin.accent, 150) : hovered ? 0x40FFFFFF : 0x50271838;
                graphics.fill(x + 1, y + 1, x + CELL - 1, y + CELL - 1, CrystalTheme.fade(fill, fade));
                if (selected || hovered) {
                    CrystalUi.outline(graphics, x + 1, y + 1, CELL - 2, CELL - 2,
                            CrystalTheme.fade(selected ? skin.accentBright : skin.border, fade));
                }
                // An item is drawn whole or not at all: it waits for its row to have come up.
                if (fade > 0.6F) {
                    graphics.fakeItem(entry.icon(), x + 2, y + 2);
                }
                if (hovered) {
                    graphics.setTooltipForNextFrame(this.font, entry.name(), mouseX, mouseY);
                }
            }
        }
        graphics.disableScissor();
        int maximum = maxScroll();
        if (maximum > 0) {
            int track = this.height - 4;
            int thumb = Math.max(10, track * track / (track + maximum));
            int y = top + 2 + Math.round((track - thumb) * (this.scroll / maximum));
            graphics.fill(left + this.width - 3, y, left + this.width - 1, y + thumb, CrystalTheme.fade(skin.accent, fade));
        }
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        int column = (int) Math.floor((event.x() - gridLeft()) / CELL);
        int row = (int) Math.floor((event.y() - getY() - 2 + Math.round(this.scroll)) / CELL);
        int index = row * columns() + column;
        if (column >= 0 && column < columns() && row >= 0 && index < this.shown.size()) {
            this.choose.accept(this.shown.get(index).target());
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (!this.visible || !this.active || !isMouseOver(mouseX, mouseY) || maxScroll() <= 0) {
            return false;
        }
        this.scrollTarget = Math.max(0, Math.min(maxScroll(),
                this.scrollTarget - (int) Math.round(Math.max(-3.0D, Math.min(3.0D, verticalAmount)) * CELL * 2)));
        return true;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, getMessage());
    }

    @Override
    public void clipTo(HubLayout.Rect area) {
        this.clip = area;
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return super.isMouseOver(mouseX, mouseY) && Clippable.inside(this.clip, mouseX, mouseY);
    }
}
