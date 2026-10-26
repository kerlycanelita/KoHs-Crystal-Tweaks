import com.zymekoh.crystaltweaks.client.hub.CrystalHubLayout;
import com.zymekoh.crystaltweaks.client.hub.HubLayout.Rect;

/**
 * Checks the Crystal Tweaks settings, the grimoire, across thousands of window sizes, with the same
 * {@link CrystalHubLayout} the screen draws and places its controls from.
 *
 * <p>Run with tools/test-glow.ps1; no Minecraft instance is needed.</p>
 */
public final class CrystalHubLayoutTest {
    private static int problems;

    public static void main(String[] args) {
        int layouts = 0;
        for (int width = CrystalHubLayout.MIN_WIDTH; width <= 2600; width += 7) {
            for (int height = CrystalHubLayout.MIN_HEIGHT; height <= 1500; height += 9) {
                check(CrystalHubLayout.fit(width, height), width + "x" + height);
                layouts++;
            }
        }
        // What players use: 1920x1001 and 1920x1080 at GUI scales 2 to 4, 1366x768 and 1280x720 at 2 and 3.
        int[][] common = {{960, 500}, {640, 333}, {480, 250}, {960, 540}, {640, 360}, {480, 270}, {683, 384}, {455, 256}, {640, 360},
                {427, 240}, {421, 233}};
        for (int[] size : common) {
            CrystalHubLayout layout = CrystalHubLayout.fit(size[0], size[1]);
            String where = size[0] + "x" + size[1];
            expect(layout.mode == CrystalHubLayout.Mode.TRIPTYCH, where + ": the sections by name, the crystal and the page should fit, got "
                    + layout.mode);
            expect(layout.panelViewport(layout.leftPanel).height() >= 90, where + ": the rows need room");
        }
        System.out.println("hub layouts checked: " + layouts + "  problems: " + problems);
        if (problems > 0) {
            System.exit(1);
        }
    }

    private static void check(CrystalHubLayout layout, String where) {
        Rect screen = new Rect(0, 0, layout.screenWidth, layout.screenHeight);
        for (Rect part : new Rect[] {layout.book, layout.body, layout.footer, layout.leftWing, layout.leftRail, layout.leftPanel,
                layout.rightWing, layout.enemySwitch, layout.kohsButton, layout.title, layout.done, layout.kohsArea, layout.kohsBack,
                layout.kohsDone}) {
            expect(!part.isEmpty() && part.inside(screen), where + ": part outside the screen " + part);
        }
        expect(layout.body.inside(layout.book), where + ": pages outside the cover");
        expect(!layout.enemySwitch.overlaps(layout.kohsButton) && !layout.title.overlaps(layout.enemySwitch)
                && !layout.title.overlaps(layout.kohsButton), where + ": title and corner buttons overlap");
        if (!layout.header.isEmpty()) {
            expect(layout.header.bottom() <= layout.book.y(), where + ": header over the book");
            expect(layout.enemySwitch.inside(layout.header) && layout.kohsButton.inside(layout.header) && layout.title.inside(layout.header),
                    where + ": corner buttons off the header");
        }
        // The pages, side by side, never over each other, with a spine between.
        expect(layout.leftWing.inside(layout.body) && layout.rightWing.inside(layout.body), where + ": a page outside the book");
        Rect before = layout.leftWing;
        if (!layout.center.isEmpty()) {
            expect(layout.center.inside(layout.body), where + ": the crystal's page outside the book");
            expect(layout.center.x() >= before.right() + 1, where + ": the crystal's page over the sections");
            before = layout.center;
        }
        expect(layout.rightWing.x() >= before.right() + 1, where + ": the options' page over the one before it");
        expect(layout.rightRail.isEmpty() && layout.rightPanel.isEmpty(), where + ": a second panel");
        // The sections: a list with names wherever the window allows it, a strip of icons only in the narrowest.
        expect(layout.leftRail.inside(layout.leftWing), where + ": sections outside their page");
        expect(layout.sectionList() == (layout.mode != CrystalHubLayout.Mode.NARROW), where + ": names missing outside the narrowest");
        Rect lastSection = layout.sectionList()
                ? CrystalHubLayout.listRow(layout.leftRail, 8, 9, layout.listRowHeight)
                : CrystalHubLayout.railButton(layout.leftRail, 9, 10);
        expect(lastSection.inside(layout.leftRail), where + ": the sections do not fit, " + lastSection);
        if (layout.sectionList()) {
            expect(lastSection.height() >= 12, where + ": section rows too short, " + lastSection.height());
            expect(layout.leftRail.width() >= 88, where + ": section names need room, " + layout.leftRail.width());
            expect(layout.practice.inside(layout.leftWing) && layout.leftRail.bottom() <= layout.practice.y(),
                    where + ": Practice off the foot of the sections' page");
        } else {
            expect(lastSection.width() >= 6, where + ": strip buttons too small");
            expect(layout.practice.isEmpty(), where + ": a Practice button without room");
        }
        if (layout.header.isEmpty()) {
            // The sections' page holds its title, the profile switch, the list, Practice and KoHs, in that order.
            for (Rect part : new Rect[] {layout.title, layout.enemySwitch, layout.practice, layout.kohsButton}) {
                expect(part.inside(layout.leftWing), where + ": part outside the sections' page " + part);
            }
            expect(layout.title.bottom() <= layout.enemySwitch.y() && layout.enemySwitch.bottom() <= layout.leftRail.y(),
                    where + ": the sections' page out of order");
            expect(!layout.practice.overlaps(layout.kohsButton), where + ": Practice over KoHs");
        }
        // The open section's page: its title, its rows and the row with Done.
        Rect viewport = layout.panelViewport(layout.leftPanel);
        expect(layout.leftPanel.inside(layout.rightWing), where + ": rows outside their page");
        expect(viewport.inside(layout.leftPanel) && viewport.right() + 4 <= layout.leftPanel.right(), where + ": rows or scrollbar outside");
        expect(viewport.width() >= 120, where + ": rows too narrow, " + viewport.width());
        expect(layout.panelTitle(layout.leftPanel).bottom() <= viewport.y(), where + ": title over the rows");
        expect(layout.footer.inside(layout.rightWing) && layout.done.inside(layout.footer) && layout.leftPanel.bottom() <= layout.footer.y(),
                where + ": Done off its page");
        // The crystal's page: the crystal before its castle wall, what it wears, and how to click it.
        if (layout.center.isEmpty()) {
            expect(layout.stage.isEmpty() && layout.hint.isEmpty(), where + ": a crystal without room");
        } else {
            expect(layout.center.width() >= CrystalHubLayout.CENTER_MIN, where + ": the crystal's page too narrow");
            expect(layout.stage.inside(layout.center) && layout.hint.inside(layout.center), where + ": stage or hint off the page");
            expect(layout.caption.isEmpty() || layout.caption.inside(layout.center) && layout.stage.bottom() <= layout.caption.y()
                    && layout.caption.bottom() <= layout.hint.y(), where + ": caption out of place");
            expect(layout.stage.bottom() <= layout.hint.y(), where + ": hint over the crystal");
            // The castle wall and the crystal before it need a few blocks of room each way.
            expect(layout.stage.width() >= 64 && layout.stage.height() >= 64,
                    where + ": the crystal's scene too small, " + layout.stage.width() + "x" + layout.stage.height());
        }
        // The KoHs window: inside the book, its buttons under it and apart.
        expect(layout.kohsArea.inside(layout.body) && layout.kohsBack.inside(layout.body) && layout.kohsDone.inside(layout.body),
                where + ": the KoHs window off the book");
        expect(layout.kohsArea.bottom() <= layout.kohsBack.y() && layout.kohsArea.bottom() <= layout.kohsDone.y()
                && !layout.kohsBack.overlaps(layout.kohsDone), where + ": the KoHs buttons over its page or each other");
        expect(layout.kohsArea.height() >= 100 || layout.screenHeight < 200, where + ": the KoHs page too short, " + layout.kohsArea.height());
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
