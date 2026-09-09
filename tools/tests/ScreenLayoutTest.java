import com.zymekoh.crystaltweaks.client.GlowEditorLayout;
import java.util.ArrayList;
import java.util.List;

/**
 * Looks for widgets that would sit on top of each other in the settings screens.
 *
 * <p>The row placement is mirrored from CrystalTweaksScreen, which cannot be built without starting
 * Minecraft. The glow editor split is not mirrored: it calls the real GlowEditorLayout, so that half
 * is checked against the shipping code rather than a copy of it.</p>
 */
public final class ScreenLayoutTest {
    private record Rect(String name, int x, int y, int w, int h) {
        boolean overlaps(Rect other) {
            return x < other.x + other.w && other.x < x + w
                    && y < other.y + other.h && other.y < y + h;
        }
    }

    private static final class Layout {
        int contentX;
        int contentY;
        int contentWidth;
        int contentHeight;
        int optionsX;
        int optionsWidth;
        int previewX;
        int previewY;
        int previewWidth;
        int previewHeight;
        int controlHeight;
        int rowGap;

        int rowStep() {
            return controlHeight + rowGap;
        }
    }

    private static int clamp(int value, int low, int high) {
        return Math.max(low, Math.min(high, value));
    }

    private static Layout compute(int width, int height, boolean editor) {
        Layout l = new Layout();
        int horizontalMargin = clamp(width / 28, 4, 18);
        int verticalMargin = clamp(height / 28, 4, 14);
        int panelWidth = Math.min(470, Math.max(1, width - horizontalMargin * 2));
        int panelHeight = Math.min(255, Math.max(1, height - verticalMargin * 2));
        int panelX = (width - panelWidth) / 2;
        int panelY = (height - panelHeight) / 2;
        int desiredHeader = editor ? 25 : panelHeight < 190 ? 42 : 50;
        int desiredFooter = panelHeight < 190 ? 25 : 31;
        int headerHeight = Math.min(desiredHeader, Math.max(1, panelHeight / 2));
        int footerHeight = Math.min(desiredFooter, Math.max(1, (panelHeight - headerHeight) / 3));
        int padding = clamp(panelWidth / 48, 5, 11);
        l.contentX = panelX + padding;
        l.contentY = panelY + headerHeight;
        l.contentWidth = Math.max(1, panelWidth - padding * 2);
        l.contentHeight = Math.max(1, panelY + panelHeight - footerHeight - l.contentY);
        l.rowGap = Math.min(panelHeight < 190 ? 3 : 5, Math.max(0, (l.contentHeight - 4) / 8));
        int fourRowHeight = Math.max(1, (l.contentHeight - l.rowGap * 3) / 4);
        l.controlHeight = Math.min(panelHeight < 190 ? 14 : 18, fourRowHeight);

        int previewGap = l.contentWidth < 390 ? 6 : 10;
        boolean previewFits = l.contentWidth >= 300 && l.contentHeight >= 86;
        l.previewWidth = previewFits ? clamp(Math.round(l.contentWidth * 0.24F), 82, 118) : 0;
        l.optionsX = l.contentX;
        l.optionsWidth = l.previewWidth > 0
                ? Math.max(1, l.contentWidth - l.previewWidth - previewGap)
                : l.contentWidth;
        l.previewX = l.previewWidth > 0 ? l.contentX + l.contentWidth - l.previewWidth : 0;
        int previewNote = l.previewWidth > 0 && l.contentHeight >= 125 ? 31 : 0;
        l.previewHeight = l.previewWidth > 0 ? Math.max(1, l.contentHeight - previewNote) : 0;
        l.previewY = l.contentY;

        if (editor) {
            GlowEditorLayout g =
                    GlowEditorLayout.fit(l.contentX, l.contentY, l.contentWidth, l.contentHeight);
            l.previewHeight = g.previewHeight();
            l.previewWidth = g.previewWidth();
            l.previewX = g.previewX();
            l.contentY = g.optionsY();
            l.contentHeight = g.optionsHeight();
            l.optionsWidth = g.optionsWidth();
            l.optionsX = g.optionsX();
        }
        return l;
    }

    private static List<Rect> visualsRects(Layout l) {
        List<Rect> rects = new ArrayList<>();
        int gap = l.optionsWidth < 250 ? 2 : 5;
        int layerWidth = Math.max(1, (l.optionsWidth - gap * 3) / 4);
        String[] names = {"layer:outer", "layer:inner", "layer:core", "layer:glow"};
        for (int i = 0; i < 4; i++) {
            rects.add(new Rect(names[i], l.optionsX + (layerWidth + gap) * i, l.contentY,
                    layerWidth, l.controlHeight));
        }
        int hexY = l.contentY + l.rowStep();
        rects.add(new Rect("hex", l.optionsX, hexY, Math.min(94, l.optionsWidth), l.controlHeight));
        int pickerY = hexY + l.controlHeight + l.rowGap;
        int pickerHeight = Math.min(48, Math.max(12, l.contentHeight / 4));
        rects.add(new Rect("picker", l.optionsX, pickerY, l.optionsWidth, pickerHeight));
        int rotationY = pickerY + pickerHeight + l.rowGap;
        rects.add(new Rect("rotation", l.optionsX, rotationY, l.optionsWidth, l.controlHeight));
        rects.add(new Rect("floating", l.optionsX, rotationY + l.rowStep(), l.optionsWidth,
                l.controlHeight));
        rects.add(new Rect("enemy", l.optionsX, rotationY + l.rowStep() * 2, l.optionsWidth,
                l.controlHeight));
        return rects;
    }

    private static List<Rect> glowRects(Layout l) {
        List<Rect> rects = new ArrayList<>();
        int y = l.contentY;
        rects.add(new Rect("power", l.optionsX, y, l.optionsWidth, l.controlHeight));
        rects.add(new Rect("reflections", l.optionsX, y + l.rowStep(), l.optionsWidth,
                l.controlHeight));
        rects.add(new Rect("glowToggle", l.optionsX, y + l.rowStep() * 2, l.optionsWidth,
                l.controlHeight));
        rects.add(new Rect("glowHex", l.optionsX, y + l.rowStep() * 3,
                Math.min(94, l.optionsWidth), l.controlHeight));
        int pickerHeight = clamp(l.contentHeight - l.rowStep() * 4, 14, 42);
        rects.add(new Rect("glowPicker", l.optionsX, y + l.rowStep() * 4, l.optionsWidth,
                pickerHeight));
        return rects;
    }

    public static void main(String[] args) {
        int[][] sizes = {
                {320, 240}, {427, 240}, {480, 270}, {640, 360}, {854, 480},
                {1024, 576}, {1280, 720}, {1600, 900}, {1920, 1080}, {2560, 1440},
                {400, 220}, {300, 200}, {1920, 400}, {600, 1080},
        };

        int problems = 0;
        int checked = 0;
        int needsScroll = 0;

        for (int[] size : sizes) {
            for (boolean editor : new boolean[] {false, true}) {
                Layout l = compute(size[0], size[1], editor);
                List<Rect> rects = editor ? glowRects(l) : visualsRects(l);
                String tag = size[0] + "x" + size[1] + (editor ? " glow-editor" : " visuals");
                checked++;

                for (int i = 0; i < rects.size(); i++) {
                    for (int j = i + 1; j < rects.size(); j++) {
                        if (rects.get(i).overlaps(rects.get(j))) {
                            System.out.println("  OVERLAP  " + tag + ": " + rects.get(i).name()
                                    + " x " + rects.get(j).name());
                            problems++;
                        }
                    }
                }

                if (l.previewWidth > 0) {
                    Rect preview =
                            new Rect("preview", l.previewX, l.previewY, l.previewWidth, l.previewHeight);
                    for (Rect c : rects) {
                        if (c.overlaps(preview)) {
                            System.out.println("  OVERLAP  " + tag + ": " + c.name() + " x preview");
                            problems++;
                        }
                    }
                }

                for (Rect c : rects) {
                    if (c.x() + c.w() > l.optionsX + l.optionsWidth) {
                        System.out.println("  PAST-COLUMN " + tag + ": " + c.name() + " by "
                                + (c.x() + c.w() - l.optionsX - l.optionsWidth) + "px");
                        problems++;
                    }
                }

                int bottom = 0;
                for (Rect c : rects) {
                    bottom = Math.max(bottom, c.y() + c.h());
                }
                if (bottom > l.contentY + l.contentHeight) {
                    needsScroll++;
                    System.out.println("  scrolls   " + tag + ": content runs "
                            + (bottom - l.contentY - l.contentHeight) + "px past the viewport");
                }
            }
        }

        System.out.println();
        System.out.println("layouts checked: " + checked
                + "  overlaps: " + problems
                + "  needing scroll: " + needsScroll);
        if (problems > 0) {
            System.exit(1);
        }
    }
}
