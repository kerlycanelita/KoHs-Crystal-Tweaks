import com.zymekoh.crystaltweaks.client.hub.HubLayout.Rect;
import com.zymekoh.crystaltweaks.client.practice.PracticeLayout;

/**
 * Checks the Crystal Practice window and its in-world panel across thousands of window sizes, with
 * the same {@link PracticeLayout} the screen draws and places its controls from.
 *
 * <p>Run with tools/test-glow.ps1; no Minecraft instance is needed.</p>
 */
public final class PracticeLayoutTest {
    private static int problems;

    public static void main(String[] args) {
        int layouts = 0;
        for (boolean inWorld : new boolean[] {false, true}) {
            for (int width = 240; width <= 2600; width += 13) {
                for (int height = 140; height <= 1500; height += 11) {
                    check(PracticeLayout.fit(width, height, inWorld), width + "x" + height + (inWorld ? " in world" : ""));
                    layouts++;
                }
            }
        }
        // What players use: 1920x1001 at GUI scales 2 to 4, 1080p and 1440p.
        int[][] common = {{960, 500}, {640, 333}, {480, 250}, {960, 540}, {640, 360}, {854, 480}, {1280, 720}};
        for (int[] size : common) {
            for (boolean inWorld : new boolean[] {false, true}) {
                PracticeLayout layout = PracticeLayout.fit(size[0], size[1], inWorld);
                String where = size[0] + "x" + size[1] + (inWorld ? " in world" : "");
                expect(layout.content.height() >= 70, where + ": the options need room, got " + layout.content.height());
                expect(layout.content.width() >= 150, where + ": options too narrow, got " + layout.content.width());
            }
            PracticeLayout window = PracticeLayout.fit(size[0], size[1], false);
            expect(window.preview || size[0] < 480, size[0] + "x" + size[1] + ": the preview should fit");
            PracticeLayout panel = PracticeLayout.fit(size[0], size[1], true);
            expect(panel.panel.x() >= size[0] / 2, size[0] + "x" + size[1] + ": the panel leaves the world in view");
        }
        System.out.println("practice layouts checked: " + layouts + "  problems: " + problems);
        if (problems > 0) {
            System.exit(1);
        }
    }

    private static void check(PracticeLayout layout, String where) {
        Rect screen = new Rect(0, 0, layout.screenWidth, layout.screenHeight);
        expect(layout.panel.inside(screen), where + ": panel leaves the screen");
        expect(layout.panel.x() >= layout.margin && layout.panel.y() >= layout.margin
                && layout.panel.right() <= layout.screenWidth - layout.margin
                && layout.panel.bottom() <= layout.screenHeight - layout.margin, where + ": panel inside the margin");
        for (Rect part : new Rect[] {layout.header, layout.tabs, layout.content, layout.footer}) {
            expect(!part.isEmpty() && part.inside(layout.panel), where + ": part outside the panel " + part);
        }
        expect(layout.header.bottom() <= layout.tabs.y(), where + ": header over the tabs");
        expect(layout.tabs.bottom() <= layout.content.y(), where + ": tabs over the options");
        int below = layout.status.isEmpty() ? layout.footer.y() : layout.status.y();
        expect(layout.content.bottom() <= below, where + ": options under the footer");
        expect(layout.content.height() >= 18, where + ": no room for even one row, got " + layout.content.height());
        // The scrollbar is drawn two pixels right of the list.
        expect(layout.content.right() + 4 <= layout.panel.right(), where + ": scrollbar outside the panel");
        for (int index = 0; index < 3; index++) {
            Rect tab = layout.tab[index];
            expect(tab.width() >= 50 && tab.inside(layout.tabs), where + ": tab " + index + " " + tab);
            for (int other = index + 1; other < 3; other++) {
                expect(!tab.overlaps(layout.tab[other]), where + ": tabs overlap");
            }
        }
        for (int index = 0; index < layout.buttons.length; index++) {
            Rect button = layout.buttons[index];
            expect(button.width() >= 40 && button.inside(layout.footer), where + ": button " + index + " " + button);
            for (int other = index + 1; other < layout.buttons.length; other++) {
                expect(!button.overlaps(layout.buttons[other]), where + ": buttons overlap");
            }
        }
        if (layout.preview) {
            expect(layout.previewArea.inside(layout.panel), where + ": preview outside the panel");
            expect(!layout.previewArea.overlaps(new Rect(layout.content.x(), layout.content.y(), layout.content.width() + 4,
                    layout.content.height())), where + ": preview over the options");
            expect(layout.content.width() >= 150, where + ": options squeezed by the preview");
        }
        if (layout.inWorld) {
            expect(!layout.status.isEmpty() && layout.status.inside(layout.panel), where + ": status line");
            expect(layout.status.bottom() <= layout.footer.y(), where + ": status over the buttons");
            expect(layout.panel.right() == layout.screenWidth - layout.margin, where + ": panel not on the right");
        }
    }

    private static void expect(boolean condition, String message) {
        if (!condition) {
            problems++;
            if (problems <= 25) {
                System.out.println("PROBLEM " + message);
            }
        }
    }
}
