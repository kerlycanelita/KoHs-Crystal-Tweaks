import com.zymekoh.crystaltweaks.client.hub.HubLayout.Rect;
import com.zymekoh.crystaltweaks.client.practice.ServerWindowLayout;

/**
 * Checks the window showing the parked server across thousands of window sizes and view shapes,
 * with the same {@link ServerWindowLayout} the HUD draws from.
 *
 * <p>Run with tools/test-glow.ps1; no Minecraft instance is needed.</p>
 */
public final class ServerWindowLayoutTest {
    private static int problems;

    public static void main(String[] args) {
        int layouts = 0;
        float[] aspects = {16.0F / 9.0F, 16.0F / 10.0F, 4.0F / 3.0F, 21.0F / 9.0F, 1.0F};
        for (boolean withView : new boolean[] {false, true}) {
            for (float aspect : aspects) {
                for (int width = 200; width <= 2600; width += 11) {
                    for (int height = 150; height <= 1500; height += 13) {
                        check(ServerWindowLayout.fit(width, height, withView, aspect), withView, aspect,
                                width + "x" + height + (withView ? " view " + aspect : ""));
                        layouts++;
                    }
                }
            }
        }
        // What players use: 1920x1080 at GUI scales 2 to 4, and 1280x720.
        int[][] common = {{960, 540}, {640, 360}, {480, 270}, {640, 333}, {854, 480}};
        for (int[] size : common) {
            ServerWindowLayout layout = ServerWindowLayout.fit(size[0], size[1], true, 16.0F / 9.0F);
            String where = size[0] + "x" + size[1];
            expect(!layout.view.isEmpty(), where + ": the live view should fit");
            expect(layout.bodyLines >= 2, where + ": at least two lines under the view, got " + layout.bodyLines);
            expect(layout.card.width() >= ServerWindowLayout.MIN_WIDTH, where + ": window too narrow");
        }
        System.out.println("server window layouts checked: " + layouts + "  problems: " + problems);
        if (problems > 0) {
            System.exit(1);
        }
    }

    private static void check(ServerWindowLayout layout, boolean withView, float aspect, String where) {
        Rect screen = new Rect(0, 0, layout.screenWidth, layout.screenHeight);
        expect(layout.card.inside(screen), where + ": window leaves the screen " + layout.card);
        expect(layout.header.inside(layout.card), where + ": header outside the window");
        expect(layout.body.isEmpty() || layout.body.inside(layout.card), where + ": body outside the window");
        expect(layout.bodyLines * ServerWindowLayout.LINE <= Math.max(0, layout.body.height()), where + ": lines overflow the body");
        if (layout.screenHeight >= 300) {
            // Clear of the chat and the hotbar in the bottom-left corner.
            expect(layout.card.bottom() <= layout.screenHeight - ServerWindowLayout.CHAT_RESERVE, where + ": window over the chat");
        }
        if (layout.screenWidth >= 2 * (ServerWindowLayout.MIN_WIDTH + ServerWindowLayout.BOSS_BAR_HALF + 12)) {
            expect(layout.card.right() <= layout.screenWidth / 2 - ServerWindowLayout.BOSS_BAR_HALF, where + ": window under the boss bars");
        }
        if (!layout.view.isEmpty()) {
            expect(withView, where + ": a view nobody asked for");
            expect(layout.view.inside(layout.card), where + ": view outside the window");
            expect(layout.view.y() >= layout.header.bottom(), where + ": view over the header");
            expect(layout.view.bottom() <= layout.body.y(), where + ": view over the body");
            expect(layout.view.height() >= ServerWindowLayout.MIN_VIEW_HEIGHT, where + ": view too small to see anything");
            float shape = layout.view.width() / (float) layout.view.height();
            expect(Math.abs(shape - aspect) / aspect < 0.06F, where + ": view stretched, " + shape + " for " + aspect);
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
