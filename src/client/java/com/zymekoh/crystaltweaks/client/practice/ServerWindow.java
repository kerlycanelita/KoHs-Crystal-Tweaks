package com.zymekoh.crystaltweaks.client.practice;

import com.zymekoh.crystaltweaks.client.CrystalTheme;
import com.zymekoh.crystaltweaks.client.CrystalUi;
import com.zymekoh.crystaltweaks.client.CrystalVisualConfig;
import com.zymekoh.crystaltweaks.client.hub.HubDraw;
import com.zymekoh.crystaltweaks.client.hub.HubLayout.Rect;
import com.zymekoh.crystaltweaks.client.hub.HubMotion;
import com.zymekoh.crystaltweaks.client.hub.HubSkin;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.numbers.StyledFormat;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Util;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

/**
 * The window in the top-left corner of the practice world that shows the server parked behind it:
 * the server's name and latency, its world live from the player's eyes, and under it what the server
 * says and shows, such as an alert when something happens there, its title and action bar, its boss
 * bars, its sidebar and its chat. Players waiting in a queue keep practising and see the moment their
 * match comes.
 *
 * <p>Drawn with the HUD, so it takes no clicks and hides with it. Geometry comes from
 * {@link ServerWindowLayout}; the window then shrinks to what it has to show.</p>
 */
final class ServerWindow {
    private static final Identifier SPYGLASS = HubDraw.vanilla("textures/item/spyglass.png");
    private static final Identifier BELL = HubDraw.vanilla("textures/item/bell.png");
    private static final long ENTER_NANOS = 320_000_000L;
    private static final long CHAT_FRESH_MILLIS = 400L;
    private static final long CHAT_OLD_MILLIS = 60_000L;
    private static final Comparator<PlayerScoreEntry> SIDEBAR_ORDER = Comparator.comparing(PlayerScoreEntry::value).reversed()
            .thenComparing(PlayerScoreEntry::owner, String.CASE_INSENSITIVE_ORDER);

    /** One row of the body: a line of text, or a boss bar under its name. */
    private record Row(FormattedCharSequence text, int color, float progress, int barColor, boolean alert) {
        static Row text(FormattedCharSequence text, int color) {
            return new Row(text, color, -1.0F, 0, false);
        }
    }

    private static long shownSince;

    private ServerWindow() {
    }

    /** Draws the window, with the HUD, while a server is parked behind the practice world on show. */
    static void draw(GuiGraphicsExtractor graphics) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!PracticeHost.parked() || PracticeHost.serverSide() || minecraft.level == null || !CrystalVisualConfig.practiceServerWindow()) {
            shownSince = 0L;
            return;
        }
        long now = System.nanoTime();
        if (shownSince == 0L) {
            shownSince = now;
        }
        float enter = HubMotion.easeOutCubic(HubMotion.progress(now - shownSince, ENTER_NANOS));
        Font font = minecraft.font;
        boolean spanish = CrystalUi.spanish();
        Identifier picture = ServerView.texture();
        boolean viewWanted = CrystalVisualConfig.practiceServerView() && ServerView.unavailableReason(spanish) == null;
        ServerWindowLayout layout = ServerWindowLayout.fit(graphics.guiWidth(), graphics.guiHeight(), viewWanted, ServerView.aspect());
        List<Row> rows = rows(minecraft, font, layout, spanish);
        int used = Math.min(rows.size(), layout.bodyLines);
        int top = layout.view.isEmpty() ? layout.header.bottom() + 2 : layout.body.y();
        int bottom = top + used * ServerWindowLayout.LINE + ServerWindowLayout.PADDING;
        if (used == 0 && !layout.view.isEmpty()) {
            bottom = layout.view.bottom() + ServerWindowLayout.PADDING;
        }
        Rect card = new Rect(layout.card.x(), layout.card.y(), layout.card.width(), Math.min(layout.card.height(), bottom - layout.card.y()));

        HubSkin skin = HubSkin.current();
        double seconds = now / 1_000_000_000.0D;
        Component alert = ServerMonitor.alert();
        float alarm = alert == null ? 0.0F : 0.5F + 0.5F * (float) Math.sin(seconds * 6.0D);
        HubDraw.isolate(graphics);
        // Slides in from the left edge the first time it shows over this practice.
        int shift = Math.round((1.0F - enter) * -(card.right() + 4));
        Rect shown = new Rect(card.x() + shift, card.y(), card.width(), card.height());
        drawCard(graphics, shown, skin, enter, alarm);
        drawHeader(graphics, font, layout.header.offset(shift, 0), skin, enter, seconds, spanish);
        if (!layout.view.isEmpty()) {
            drawView(graphics, font, layout.view.offset(shift, 0), picture, skin, enter, seconds, spanish);
        }
        int y = top;
        for (int index = rows.size() - used; index < rows.size(); index++) {
            drawRow(graphics, font, rows.get(index), layout.body.x() + shift, y, layout.body.width(), skin, enter, seconds);
            y += ServerWindowLayout.LINE;
        }
        HubDraw.isolate(graphics);
    }

    // ------------------------------------------------------------------------------------------
    // What the body shows, most wanted first when it does not all fit
    // ------------------------------------------------------------------------------------------

    /**
     * The rows, in the order they show, top to bottom. When they do not all fit, the window keeps the
     * last ones, so the order below puts what matters most at the bottom of each kind and the chat,
     * the most there is of, at the very bottom where it is cut from the top like Minecraft's chat.
     */
    private static List<Row> rows(Minecraft minecraft, Font font, ServerWindowLayout layout, boolean spanish) {
        int width = layout.body.width();
        int budget = layout.bodyLines;
        List<Row> head = new ArrayList<>();
        Component alert = ServerMonitor.alert();
        if (alert != null) {
            List<FormattedCharSequence> lines = font.split(alert, width - 12);
            for (int index = 0; index < Math.min(2, lines.size()); index++) {
                head.add(new Row(lines.get(index), 0xFFFFD6DC, -1.0F, 0, index == 0));
            }
            head.add(Row.text(FormattedCharSequence.forward(HubDraw.fit(font, (spanish ? "Esc · Volver a " : "Esc · Back to ")
                    + PracticeHost.serverName(), width), net.minecraft.network.chat.Style.EMPTY), CrystalTheme.TEXT_MUTED));
        }
        Screen menu = PracticeHost.serverScreen();
        if (menu != null && !menu.getTitle().getString().isEmpty()) {
            head.add(Row.text(first(font, Component.literal(spanish ? "Menú abierto: " : "Menu open: ").withStyle(ChatFormatting.GRAY)
                    .append(menu.getTitle()), width), 0xFFFFFFFF));
        }
        Component title = ServerMonitor.title();
        if (title != null) {
            head.add(Row.text(first(font, title, width), 0xFFFFFFFF));
            Component subtitle = ServerMonitor.subtitle();
            if (subtitle != null) {
                head.add(Row.text(first(font, subtitle, width), 0xFFE0D8EA));
            }
        }
        Component actionBar = ServerMonitor.actionBar();
        if (actionBar != null) {
            head.add(Row.text(first(font, actionBar, width), 0xFFFFFFFF));
        }
        Collection<LerpingBossEvent> bars = PracticeHost.serverBossBars();
        int barsShown = 0;
        for (LerpingBossEvent bar : bars) {
            if (barsShown++ >= 2) {
                break;
            }
            head.add(new Row(first(font, bar.getName(), width), 0xFFFFFFFF, bar.getProgress(), barColor(bar.getColor().name()), false));
        }

        List<Row> sidebar = sidebar(minecraft, font, width);
        List<Row> chat = new ArrayList<>();
        long now = Util.getMillis();
        for (ServerMonitor.Line line : ServerMonitor.lastChat(Math.max(0, budget))) {
            long age = now - line.at();
            int alpha = age < CHAT_FRESH_MILLIS ? Math.round(255 * age / (float) CHAT_FRESH_MILLIS) : age > CHAT_OLD_MILLIS ? 150 : 255;
            for (FormattedCharSequence wrapped : font.split(line.text(), width)) {
                chat.add(Row.text(wrapped, CrystalTheme.withAlpha(0xFFFFFF, Math.max(40, alpha))));
            }
        }
        // What fits: the head first, then at least three lines of chat, then the sidebar, then more chat.
        int room = Math.max(0, budget - head.size());
        int chatFirst = Math.min(chat.size(), Math.min(3, room));
        int sidebarShown = Math.min(sidebar.size(), room - chatFirst);
        int chatShown = Math.min(chat.size(), room - sidebarShown);
        List<Row> rows = new ArrayList<>(head);
        rows.addAll(sidebar.subList(0, Math.max(0, sidebarShown)));
        rows.addAll(chat.subList(chat.size() - Math.max(0, chatShown), chat.size()));
        return rows;
    }

    /** The server's sidebar as Minecraft draws it on the right: title, then each line and its score. */
    private static List<Row> sidebar(Minecraft minecraft, Font font, int width) {
        List<Row> rows = new ArrayList<>();
        ClientLevel level = PracticeHost.serverLevel();
        LocalPlayer player = PracticeHost.serverPlayer();
        if (level == null || player == null) {
            return rows;
        }
        Scoreboard scoreboard = level.getScoreboard();
        Objective objective = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
        if (objective == null) {
            return rows;
        }
        rows.add(Row.text(first(font, objective.getDisplayName(), width), 0xFFFFFFFF));
        Objective shown = objective;
        List<PlayerScoreEntry> entries = scoreboard.listPlayerScores(objective).stream()
                .filter(entry -> !entry.isHidden())
                .sorted(SIDEBAR_ORDER)
                .limit(15)
                .toList();
        for (PlayerScoreEntry entry : entries) {
            Component name = PlayerTeam.formatNameForTeam(scoreboard.getPlayersTeam(entry.owner()), entry.ownerName());
            Component score = entry.formatValue(shown.numberFormatOrDefault(StyledFormat.SIDEBAR_DEFAULT));
            int scoreWidth = font.width(score);
            Component line = scoreWidth > 0 && font.width(name) + scoreWidth + 6 <= width
                    ? name.copy().append(Component.literal(" ").append(score).withStyle(ChatFormatting.RED))
                    : name;
            rows.add(Row.text(first(font, line, width), 0xFFFFFFFF));
        }
        return rows;
    }

    /** The colour Minecraft paints each boss bar colour with. */
    private static int barColor(String color) {
        return switch (color) {
            case "PINK" -> 0xFFEC00B8;
            case "BLUE" -> 0xFF00B7EC;
            case "RED" -> 0xFFEC3500;
            case "GREEN" -> 0xFF1DEC00;
            case "YELLOW" -> 0xFFE9EC00;
            case "PURPLE" -> 0xFF7B00EC;
            default -> 0xFFECECEC;
        };
    }

    private static FormattedCharSequence first(Font font, Component text, int width) {
        List<FormattedCharSequence> lines = font.split(text, width);
        return lines.isEmpty() ? FormattedCharSequence.EMPTY : lines.get(0);
    }

    // ------------------------------------------------------------------------------------------
    // Drawing
    // ------------------------------------------------------------------------------------------

    /** Dark glass on black metal; crimson pulses along its edge while an alert is up. */
    private static void drawCard(GuiGraphicsExtractor graphics, Rect card, HubSkin skin, float enter, float alarm) {
        int edge = alarm > 0.0F ? CrystalTheme.lerp(skin.border, 0xFFFF4A5E, 0.4F + 0.6F * alarm) : skin.border;
        if (alarm > 0.0F) {
            HubDraw.halo(graphics, card.x(), card.y(), card.width(), card.height(), 0xFFFF3B4E, 4, 0.45F * alarm * enter);
        } else {
            HubDraw.halo(graphics, card.x(), card.y(), card.width(), card.height(), skin.accent, 3, 0.18F * enter);
        }
        HubDraw.glass(graphics, card.x(), card.y(), card.width(), card.height(), CrystalTheme.withAlpha(skin.panelTop, 205),
                CrystalTheme.withAlpha(skin.panelBottom, 215), edge, enter);
        graphics.fill(card.x() + 2, card.y() + 1, card.right() - 2, card.y() + 2, CrystalTheme.fade(0x60BDB8CF, enter));
        int corner = CrystalTheme.fade(alarm > 0.0F ? 0xFFFF6B7C : skin.accentBright, 0.85F * enter);
        graphics.fill(card.x(), card.y(), card.x() + 6, card.y() + 1, corner);
        graphics.fill(card.x(), card.y(), card.x() + 1, card.y() + 6, corner);
        graphics.fill(card.right() - 6, card.bottom() - 1, card.right(), card.bottom(), corner);
        graphics.fill(card.right() - 1, card.bottom() - 6, card.right(), card.bottom(), corner);
    }

    /** The spyglass, the server's name, and its latency or that the connection is gone. */
    private static void drawHeader(GuiGraphicsExtractor graphics, Font font, Rect header, HubSkin skin, float enter, double seconds,
            boolean spanish) {
        float bob = (float) Math.sin(seconds * 2.2D) * 0.6F;
        HubDraw.icon(graphics, SPYGLASS, header.x() + 5.0F, header.y() + header.height() / 2.0F + bob, 11.0F,
                (float) Math.sin(seconds * 1.3D) * 0.12F, CrystalTheme.fade(0xFFFFFFFF, enter));
        String state;
        int stateColor;
        if (PracticeHost.serverLost()) {
            state = spanish ? "desconectado" : "disconnected";
            stateColor = CrystalTheme.DANGER;
        } else {
            int ping = PracticeHost.serverPing();
            state = ping < 0 ? "" : ping + " ms";
            stateColor = ping > 300 ? CrystalTheme.DANGER : skin.muted;
        }
        int stateWidth = state.isEmpty() ? 0 : font.width(state) + 9;
        int textY = header.y() + (header.height() - 8) / 2;
        CrystalUi.label(graphics, font, HubDraw.fit(font, PracticeHost.serverName(), header.width() - 14 - stateWidth),
                header.x() + 13, textY, CrystalTheme.fade(skin.title, enter), true);
        if (!state.isEmpty()) {
            int stateX = header.right() - font.width(state);
            float pulse = 0.55F + 0.45F * (float) Math.sin(seconds * 2.6D);
            int dot = PracticeHost.serverLost() ? CrystalTheme.DANGER : 0xFF6BE39A;
            graphics.fill(stateX - 7, textY + 2, stateX - 3, textY + 6, CrystalTheme.fade(CrystalTheme.withAlpha(dot, Math.round(255 * pulse)), enter));
            CrystalUi.label(graphics, font, state, stateX, textY, CrystalTheme.fade(stateColor, enter));
        }
        graphics.fill(header.x(), header.bottom() + 1, header.right(), header.bottom() + 2,
                CrystalTheme.fade(CrystalTheme.withAlpha(skin.border, 80), enter));
    }

    /** The server's world, live, with a small "live" mark that breathes; or a word while it starts. */
    private static void drawView(GuiGraphicsExtractor graphics, Font font, Rect view, Identifier picture, HubSkin skin, float enter,
            double seconds, boolean spanish) {
        graphics.fill(view.x() - 1, view.y() - 1, view.right() + 1, view.bottom() + 1, CrystalTheme.fade(0xFF05020A, enter));
        if (picture == null) {
            String waiting = spanish ? "Preparando la vista…" : "Getting the view ready…";
            CrystalUi.centered(graphics, font, HubDraw.fit(font, waiting, view.width() - 6), view.centerX(), view.centerY() - 4,
                    CrystalTheme.fade(skin.muted, enter));
            return;
        }
        // Opaque: the frame's alpha means nothing once copied. Render targets keep their first row at
        // the bottom, so the picture is read from its last row up and stands the right way.
        graphics.blit(RenderPipelines.GUI_OPAQUE_TEXTURED_BACKGROUND, picture, view.x(), view.y(), 0.0F, view.height(), view.width(),
                view.height(), view.width(), -view.height(), view.width(), view.height());
        CrystalUi.outline(graphics, view.x() - 1, view.y() - 1, view.width() + 2, view.height() + 2,
                CrystalTheme.fade(CrystalTheme.withAlpha(skin.border, 160), enter));
        String live = spanish ? "EN VIVO" : "LIVE";
        int tagWidth = font.width(live) + 9;
        int tagX = view.x() + 3;
        int tagY = view.y() + 3;
        graphics.fill(tagX, tagY, tagX + tagWidth, tagY + 10, CrystalTheme.fade(0xA005020A, enter));
        float breathe = 0.5F + 0.5F * (float) Math.sin(seconds * 3.0D);
        graphics.fill(tagX + 2, tagY + 3, tagX + 5, tagY + 6, CrystalTheme.fade(CrystalTheme.lerp(skin.accent, skin.accentBright, breathe), enter));
        CrystalUi.label(graphics, font, live, tagX + 7, tagY + 1, CrystalTheme.fade(0xFFF4EEFA, enter));
    }

    private static void drawRow(GuiGraphicsExtractor graphics, Font font, Row row, int x, int y, int width, HubSkin skin, float enter,
            double seconds) {
        if (row.alert()) {
            float swing = (float) Math.sin(seconds * 9.0D) * 0.35F;
            HubDraw.icon(graphics, BELL, x + 4.0F, y + 4.0F, 9.0F, swing, CrystalTheme.fade(0xFFFFFFFF, enter));
            HubDraw.line(graphics, font, row.text(), x + 11, y, CrystalTheme.fade(row.color(), enter));
            return;
        }
        if (row.progress() >= 0.0F) {
            HubDraw.line(graphics, font, row.text(), x, y - 1, CrystalTheme.fade(row.color(), enter));
            graphics.fill(x, y + 8, x + width, y + 9, CrystalTheme.fade(0x80301638, enter));
            graphics.fill(x, y + 8, x + Math.round(width * Math.min(1.0F, row.progress())), y + 9,
                    CrystalTheme.fade(row.barColor(), enter));
            return;
        }
        HubDraw.line(graphics, font, row.text(), x, y, CrystalTheme.fade(row.color(), enter));
    }
}
