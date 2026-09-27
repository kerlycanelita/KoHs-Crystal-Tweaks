import com.zymekoh.crystaltweaks.client.CrystalScreenLayout;
import com.zymekoh.crystaltweaks.client.CrystalScreenLayout.Content;
import com.zymekoh.crystaltweaks.client.CrystalScreenLayout.Group;
import com.zymekoh.crystaltweaks.client.CrystalScreenLayout.Rect;
import com.zymekoh.crystaltweaks.client.CrystalScreenLayout.Rows;
import com.zymekoh.crystaltweaks.client.CrystalScreenLayout.Slot;
import com.zymekoh.crystaltweaks.client.CrystalScreenLayout.Tab;
import java.util.ArrayList;
import java.util.List;

/**
 * Checks the settings screen's geometry across thousands of window sizes, every tab and every
 * combination of optional rows. It calls the same {@link CrystalScreenLayout} the screen places its
 * widgets from, so a passing run describes the shipping layout rather than a copy of it.
 *
 * <p>Run with tools/test-glow.ps1; no Minecraft instance is needed.</p>
 */
public final class ScreenLayoutTest {
    private static int problems;
    private static final List<String> reported = new ArrayList<>();

    public static void main(String[] args) {
        int layouts = 0;
        int needScroll = 0;
        int legendLayouts = 0;
        for (int width = 200; width <= 2600; width += 23) {
            for (int height = 150; height <= 1500; height += 19) {
                CrystalScreenLayout layout = CrystalScreenLayout.fit(width, height);
                if (layout.legends) {
                    legendLayouts++;
                }
                String size = width + "x" + height;
                checkFrame(layout, size);
                for (Tab tab : Tab.values()) {
                    for (int flags = 0; flags < 256; flags++) {
                        Content content = new Content((flags & 1) != 0, (flags & 2) != 0, (flags & 4) != 0,
                                (flags & 8) != 0, (flags & 16) != 0, (flags & 32) != 0, (flags & 64) != 0,
                                (flags & 128) != 0);
                        if (content.enemy() && (tab == Tab.SOUNDS || tab == Tab.TWEAKS)) {
                            continue;
                        }
                        if (!relevant(tab, flags)) {
                            continue;
                        }
                        Rows rows = layout.rows(tab, content);
                        checkRows(layout, rows, size + " " + tab + " " + flags);
                        if (rows.bottom() > layout.content.height()) {
                            needScroll++;
                        }
                        layouts++;
                    }
                }
            }
        }
        // The sizes players actually use must never need to scroll the colour tab.
        for (int[] common : new int[][] {{480, 270}, {640, 360}, {854, 480}, {960, 540}, {1280, 720}, {1920, 1080}}) {
            CrystalScreenLayout layout = CrystalScreenLayout.fit(common[0], common[1]);
            Rows colours = layout.rows(Tab.COLORS, Content.expanded(false));
            check(colours.bottom() <= layout.content.height(),
                    common[0] + "x" + common[1] + ": the colour tab must fit without scrolling");
        }
        System.out.println("layouts checked: " + layouts + "  with card titles: " + legendLayouts
                + " sizes  needing scroll: " + needScroll + "  problems: " + problems);
        if (problems > 0) {
            System.exit(1);
        }
    }

    /**
     * Only the flags a tab reads: the others cannot change its rows, so combining them would only
     * repeat the same layout. Enemy, glow, flash and custom colour for Glow; ghost, debounce and the
     * two Herzium flags for Advanced; enemy alone for Colors and Sound.
     */
    private static boolean relevant(Tab tab, int flags) {
        int used = switch (tab) {
            case COLORS, SOUNDS -> 1;
            case GLOW -> 1 | 2 | 4 | 8;
            case TWEAKS -> 16 | 32 | 64 | 128;
        };
        return (flags & ~used) == 0;
    }

    private static void checkFrame(CrystalScreenLayout layout, String tag) {
        Rect screen = new Rect(0, 0, layout.screenWidth, layout.screenHeight);
        check(layout.panel.inside(screen), tag + ": panel leaves the screen");
        check(layout.panel.width() <= CrystalScreenLayout.MAX_PANEL_WIDTH, tag + ": panel wider than its maximum");
        check(layout.panel.height() <= CrystalScreenLayout.MAX_PANEL_HEIGHT, tag + ": panel taller than its maximum");
        check(layout.content.inside(layout.panel), tag + ": content leaves the panel");
        check(layout.content.y() >= layout.panel.y() + layout.headerHeight, tag + ": content under the header");
        check(layout.content.bottom() <= layout.panel.bottom() - layout.footerHeight, tag + ": content under the footer");
        check(layout.controlHeight > 0 && layout.content.height() > 0, tag + ": empty viewport");
        if (layout.preview.width() > 0) {
            check(layout.preview.inside(layout.content), tag + ": preview leaves the content area");
            check(layout.optionsX + layout.optionsWidth <= layout.preview.x() - 6,
                    tag + ": options touch the preview");
        }
        for (int count : new int[] {2, 4}) {
            List<Rect> tabs = layout.tabs(count);
            for (int i = 0; i < tabs.size(); i++) {
                check(tabs.get(i).inside(layout.panel), tag + ": tab " + i + " leaves the panel");
                check(tabs.get(i).bottom() <= layout.panel.y() + layout.headerHeight,
                        tag + ": tab " + i + " runs into the content");
                for (int j = i + 1; j < tabs.size(); j++) {
                    check(!tabs.get(i).overlaps(tabs.get(j)), tag + ": tabs " + i + " and " + j + " overlap");
                }
            }
        }
        List<Rect> footer = layout.footerButtons();
        for (Rect button : footer) {
            check(button.inside(layout.panel), tag + ": footer button leaves the panel");
            check(button.y() >= layout.content.bottom(), tag + ": footer button over the content");
        }
        check(!footer.get(0).overlaps(footer.get(1)), tag + ": footer buttons overlap");
    }

    private static void checkRows(CrystalScreenLayout layout, Rows rows, String tag) {
        int optionsRight = layout.optionsX + layout.optionsWidth;
        List<Slot> slots = rows.slots();
        for (int i = 0; i < slots.size(); i++) {
            Slot slot = slots.get(i);
            Rect rect = slot.rect();
            check(rect.width() > 0 && rect.height() > 0, tag + ": " + slot.name() + " is empty");
            check(rect.x() >= layout.optionsX && rect.right() <= optionsRight,
                    tag + ": " + slot.name() + " leaves the options column");
            check(rect.y() >= layout.content.y(), tag + ": " + slot.name() + " starts above the content");
            if (layout.preview.width() > 0) {
                Rect preview = layout.preview;
                check(!new Rect(rect.x(), preview.y(), rect.width(), preview.height()).overlaps(preview),
                        tag + ": " + slot.name() + " reaches under the preview");
            }
            for (int j = i + 1; j < slots.size(); j++) {
                check(!rect.overlaps(slots.get(j).rect()),
                        tag + ": " + slot.name() + " overlaps " + slots.get(j).name());
            }
        }
        List<Group> groups = rows.groups();
        for (int i = 0; i < groups.size(); i++) {
            Rect card = groups.get(i).rect();
            check(card.x() >= layout.panel.x() && card.right() <= layout.panel.right(),
                    tag + ": card " + groups.get(i).key() + " leaves the panel");
            if (layout.preview.width() > 0) {
                check(card.right() <= layout.preview.x(), tag + ": card " + groups.get(i).key() + " covers the preview");
            }
            check(card.y() >= layout.content.y(), tag + ": card " + groups.get(i).key() + " starts above the content");
            if (layout.legends) {
                // The title sits four pixels above the card's edge and must stay in the viewport,
                // clear of the header line just above it.
                check(card.y() - 4 >= layout.content.y() + 1, tag + ": title of " + groups.get(i).key() + " is cut off");
                // It reaches three pixels below the edge; the card's rows keep two pixels from it.
                for (Slot slot : slots) {
                    if (slot.group() == i) {
                        check(slot.rect().y() >= card.y() + 6,
                                tag + ": title of " + groups.get(i).key() + " touches " + slot.name());
                    }
                }
            }
            for (int j = i + 1; j < groups.size(); j++) {
                Rect other = groups.get(j).rect();
                check(!card.overlaps(other), tag + ": cards " + groups.get(i).key() + " and " + groups.get(j).key() + " overlap");
                if (layout.legends) {
                    check(other.y() - 5 >= card.bottom(),
                            tag + ": title of " + groups.get(j).key() + " touches " + groups.get(i).key());
                }
            }
            for (Slot slot : slots) {
                if (slot.group() == i) {
                    check(slot.rect().inside(card), tag + ": " + slot.name() + " sticks out of its card");
                }
            }
        }
        // A switch's unfolded rows sit on its branch: never further left than the switch itself.
        String[][] branches = {{"glow.toggle", "power", "reflections"},
                {"flash.toggle", "flash.previous", "flash.size", "flash.opacity", "flash.duration"},
                {"debounce.toggle", "debounce.time"}, {"herzium.toggle", "herzium.order", "herzium.optimizer"}};
        for (String[] branch : branches) {
            Slot parent = rows.slot(branch[0]);
            for (int child = 1; child < branch.length; child++) {
                Slot slot = rows.slot(branch[child]);
                if (slot == null) {
                    continue;
                }
                check(parent != null, tag + ": " + branch[child] + " shown without its switch");
                if (parent != null) {
                    check(slot.rect().x() >= parent.rect().x() && slot.rect().y() > parent.rect().y(),
                            tag + ": " + branch[child] + " is not under " + branch[0]);
                    check(slot.group() == parent.group(), tag + ": " + branch[child] + " left its switch's card");
                }
            }
        }
        check(rows.bottom() > 0, tag + ": no content");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            problems++;
            if (reported.size() < 25) {
                reported.add(message);
                System.out.println("  FAIL " + message);
            }
        }
    }
}
