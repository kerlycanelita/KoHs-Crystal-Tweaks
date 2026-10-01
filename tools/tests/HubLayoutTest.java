import com.zymekoh.crystaltweaks.client.hub.HubLayout;
import com.zymekoh.crystaltweaks.client.hub.HubLayout.Rect;
import java.util.ArrayList;
import java.util.List;

/**
 * Checks the settings hub's geometry across thousands of window sizes. It calls the same
 * {@link HubLayout} the screen draws and places its widgets from, so a passing run describes the
 * shipping layout rather than a copy of it.
 *
 * <p>Run with tools/test-glow.ps1; no Minecraft instance is needed.</p>
 */
public final class HubLayoutTest {
    private static int problems;
    private static final List<String> reported = new ArrayList<>();

    public static void main(String[] args) {
        int layouts = 0;
        int wide = 0;
        for (int width = 240; width <= 2600; width += 13) {
            for (int height = 140; height <= 1500; height += 11) {
                HubLayout layout = HubLayout.fit(width, height);
                check(layout, width + "x" + height);
                layouts++;
                if (layout.wide) {
                    wide++;
                }
            }
        }
        // What players use: 1920x1001 at GUI scales 2 to 4, 1080p and 1440p.
        int[][] common = {{960, 500}, {640, 333}, {480, 250}, {960, 540}, {640, 360}, {854, 480}, {1280, 720}, {1920, 1080}};
        for (int[] size : common) {
            HubLayout layout = HubLayout.fit(size[0], size[1]);
            expect(layout.wide, size[0] + "x" + size[1] + ": the three columns must fit");
            expect(layout.stage.height() >= 80, size[0] + "x" + size[1] + ": the crystal needs room");
            expect(layout.panelViewport(layout.left).width() >= 110, size[0] + "x" + size[1] + ": panel rows too narrow");
        }
        System.out.println("hub layouts checked: " + layouts + "  three columns: " + wide + "  problems: " + problems);
        if (problems > 0) {
            System.exit(1);
        }
    }

    private static void check(HubLayout layout, String size) {
        Rect screen = new Rect(0, 0, layout.screenWidth, layout.screenHeight);
        for (Rect rect : new Rect[] {layout.header, layout.body, layout.footer, layout.switchButton, layout.done}) {
            expect(rect.width() > 0 && rect.height() > 0, size + ": empty frame part");
            expect(rect.inside(screen), size + ": frame outside the screen");
        }
        expect(!layout.header.overlaps(layout.body), size + ": header over body");
        expect(!layout.footer.overlaps(layout.body), size + ": footer over body");
        expect(layout.switchButton.inside(layout.header), size + ": switch outside the header");
        expect(layout.done.inside(layout.footer), size + ": Done outside the footer");
        expect(!layout.switchButton.overlaps(layout.done), size + ": switch over Done");
        expect(layout.center.inside(layout.body), size + ": centre outside the body");
        expect(layout.left.inside(layout.body) && layout.right.inside(layout.body), size + ": side panel outside the body");
        if (layout.wide) {
            expect(!layout.left.overlaps(layout.center) && !layout.right.overlaps(layout.center) && !layout.left.overlaps(layout.right),
                    size + ": columns overlap");
            expect(layout.left.width() >= HubLayout.SIDE_MIN && layout.center.width() >= HubLayout.CENTER_MIN,
                    size + ": a column below its minimum");
        } else {
            expect(layout.leftHandle.inside(layout.body) && layout.rightHandle.inside(layout.body), size + ": handle outside the body");
            expect(!layout.leftHandle.overlaps(layout.center) && !layout.rightHandle.overlaps(layout.center), size + ": handle over the centre");
        }
        for (Rect part : new Rect[] {layout.carousel, layout.stage, layout.practice}) {
            expect(part.inside(layout.center), size + ": centre part outside the centre");
        }
        expect(!layout.carousel.overlaps(layout.stage) && !layout.stage.overlaps(layout.practice) && !layout.carousel.overlaps(layout.practice),
                size + ": centre parts overlap");
        for (Rect panel : new Rect[] {layout.left, layout.right}) {
            Rect viewport = layout.panelViewport(panel);
            Rect title = layout.panelTitle(panel);
            Rect reset = layout.panelReset(panel);
            expect(viewport.inside(panel), size + ": rows outside their panel");
            expect(!viewport.overlaps(title), size + ": rows under the title");
            expect(reset.inside(title), size + ": reset outside the title strip");
            // The scrollbar sits right of the rows and must stay inside the panel.
            expect(viewport.right() + 4 <= panel.right(), size + ": scrollbar outside the panel");
        }
        for (int wanted : new int[] {0, 30, 90, 400, 10_000}) {
            for (float share : new float[] {0.46F, 0.64F, 1.0F}) {
                Rect drawer = layout.drawer(wanted, share);
                expect(drawer.inside(layout.stage) || drawer.height() == 0, size + ": drawer outside the stage");
                Rect crystal = layout.crystal(drawer.height());
                expect(crystal.width() == crystal.height(), size + ": the crystal's box is not square");
                expect(crystal.height() == 0 || crystal.inside(layout.stage), size + ": crystal outside the stage");
                expect(crystal.height() == 0 || drawer.height() == 0 || !crystal.overlaps(drawer), size + ": crystal under the drawer");
            }
        }
    }

    private static void expect(boolean condition, String message) {
        if (!condition) {
            problems++;
            if (reported.size() < 40 && !reported.contains(message)) {
                reported.add(message);
                System.out.println("FAIL " + message);
            }
        }
    }
}
