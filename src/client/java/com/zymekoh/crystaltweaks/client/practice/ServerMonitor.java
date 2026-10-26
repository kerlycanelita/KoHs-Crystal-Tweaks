package com.zymekoh.crystaltweaks.client.practice;

import com.zymekoh.crystaltweaks.client.CrystalUi;
import com.zymekoh.crystaltweaks.client.CrystalVisualConfig;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundClearTitlesPacket;
import net.minecraft.network.protocol.game.ClientboundDisguisedChatPacket;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerChatPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetHealthPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Util;

/**
 * What the server parked behind the practice world shows and says, kept for the window in the
 * top-left corner: its chat, its titles and action bar, and what just happened there that the player
 * may want to go back for, such as a teleport into a match, a respawn, a menu, a title, a hit or their
 * name in the chat.
 *
 * <p>Packets are read before Minecraft handles them and never changed. Titles and the action bar are
 * the only ones kept off the practice world's own HUD, where the practice shows its kills and deaths:
 * they only draw on screen and answer nothing, so leaving them to this window changes nothing the
 * server sees. The chat still reaches the practice chat as it did.</p>
 *
 * <p>Game thread only.</p>
 */
public final class ServerMonitor {
    /** A chat line, and when it came. */
    public record Line(Component text, long at) {
    }

    static final int CHAT_KEPT = 40;
    /** How long a title, an action bar text and an alert stay up, as Minecraft's defaults roughly do. */
    static final long TITLE_MILLIS = 5_000L;
    static final long ACTION_BAR_MILLIS = 3_000L;
    static final long ALERT_MILLIS = 8_000L;
    /** One chime per this long at most, however much happens. */
    private static final long CHIME_GAP_MILLIS = 2_500L;
    /** A move of more than this many blocks in one packet is a teleport worth telling. */
    private static final double TELEPORT_BLOCKS = 6.0D;

    /**
     * A title that starts a match: a fight, go or start word in English or Spanish, or a countdown's
     * single digit. Matched on whole words, so a welcome or a queue message does not count.
     */
    private static final Pattern MATCH_START = Pattern.compile(
            "\\b(fight|go|start|starts|started|begin|begins|pelea|peleen|lucha|luchen|comienza|empieza)\\b|^\\W*[1-5]\\W*$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private static final ArrayDeque<Line> CHAT = new ArrayDeque<>();
    private static Component title;
    private static Component subtitle;
    private static long titleAt;
    private static Component actionBar;
    private static long actionBarAt;
    private static Component alert;
    private static long alertAt;
    /** A title outranks what comes with it: a match that starts teleports and titles at once. */
    private static boolean alertIsTitle;
    private static boolean chimeDue;
    /** The parked server's match is starting: back to it as soon as the practice world can close. */
    private static boolean returnDue;
    private static long lastChime;
    private static float lastHealth = -1.0F;
    private static double beforeX;
    private static double beforeY;
    private static double beforeZ;

    private ServerMonitor() {
    }

    /** A new server parked, or none: nothing from the last one carries over. */
    static void reset() {
        CHAT.clear();
        title = null;
        subtitle = null;
        actionBar = null;
        alert = null;
        chimeDue = false;
        returnDue = false;
        lastHealth = -1.0F;
    }

    // ------------------------------------------------------------------------------------------
    // Reading the parked server's packets
    // ------------------------------------------------------------------------------------------

    /**
     * Before Minecraft handles a packet of the parked server, with its world in Minecraft's fields.
     * Returns true for the packets that only draw a title or the action bar: they stay here, and
     * Minecraft skips them.
     */
    static boolean before(Packet<?> packet, LocalPlayer player) {
        long now = Util.getMillis();
        switch (packet) {
            case ClientboundSetTitleTextPacket titleText -> {
                title = titleText.text();
                titleAt = now;
                alert(titleText.text(), true);
                if (MATCH_START.matcher(titleText.text().getString()).find()) {
                    returnDue = true;
                }
                return true;
            }
            case ClientboundSetSubtitleTextPacket subtitleText -> {
                subtitle = subtitleText.text();
                titleAt = Math.max(titleAt, now - TITLE_MILLIS / 2);
                return true;
            }
            case ClientboundSetActionBarTextPacket actionBarText -> {
                actionBar = actionBarText.text();
                actionBarAt = now;
                return true;
            }
            case ClientboundSetTitlesAnimationPacket ignored -> {
                return true;
            }
            case ClientboundClearTitlesPacket ignored -> {
                title = null;
                subtitle = null;
                return true;
            }
            case ClientboundSystemChatPacket system -> {
                if (system.overlay()) {
                    actionBar = system.content();
                    actionBarAt = now;
                    return true;
                }
                chat(system.content(), player, false);
                return false;
            }
            case ClientboundPlayerChatPacket chat -> {
                Component content = chat.unsignedContent() != null ? chat.unsignedContent() : Component.literal(chat.body().content());
                chat(chat.chatType().decorate(content), player, player != null && player.getUUID().equals(chat.sender()));
                return false;
            }
            case ClientboundDisguisedChatPacket disguised -> {
                chat(disguised.chatType().decorate(disguised.message()), player, false);
                return false;
            }
            case ClientboundOpenScreenPacket open -> {
                alert(spanish() ? "Te abrió un menú: " : "It opened a menu: ", open.getTitle());
                return false;
            }
            case ClientboundRespawnPacket ignored -> {
                lastHealth = -1.0F;
                alert(spanish() ? "Reapareciste o cambiaste de mundo" : "You respawned or changed worlds", null);
                return false;
            }
            case ClientboundSetHealthPacket health -> {
                if (lastHealth >= 0.0F && health.getHealth() < lastHealth - 0.01F) {
                    alert(spanish() ? "Te hicieron daño" : "You took damage", null);
                }
                lastHealth = health.getHealth();
                return false;
            }
            case ClientboundPlayerPositionPacket ignored -> {
                if (player != null) {
                    beforeX = player.getX();
                    beforeY = player.getY();
                    beforeZ = player.getZ();
                }
                return false;
            }
            default -> {
                return false;
            }
        }
    }

    /** After Minecraft handled a packet of the parked server: a teleport shows only once it happened. */
    static void after(Packet<?> packet, LocalPlayer player) {
        if (packet instanceof ClientboundPlayerPositionPacket && player != null) {
            double dx = player.getX() - beforeX;
            double dy = player.getY() - beforeY;
            double dz = player.getZ() - beforeZ;
            if (dx * dx + dy * dy + dz * dz > TELEPORT_BLOCKS * TELEPORT_BLOCKS) {
                alert(spanish() ? "Te teletransportaron" : "You were teleported", null);
            }
        }
    }

    private static void chat(Component text, LocalPlayer player, boolean own) {
        CHAT.addLast(new Line(text, Util.getMillis()));
        while (CHAT.size() > CHAT_KEPT) {
            CHAT.removeFirst();
        }
        if (!own && player != null) {
            String name = player.getGameProfile().name().toLowerCase(Locale.ROOT);
            if (name.length() >= 3 && text.getString().toLowerCase(Locale.ROOT).contains(name)) {
                alert(spanish() ? "Te nombraron en el chat" : "Someone said your name", null);
            }
        }
    }

    /** Something worth going back for: the window flashes, and a chime sounds if the player wants one. */
    static void alert(String text, Component detail) {
        Component message = Component.literal(text);
        alert(detail == null ? message : message.copy().append(detail), false);
    }

    private static void alert(Component message, boolean isTitle) {
        long now = Util.getMillis();
        chimeDue = true;
        if (alert != null && alertIsTitle && !isTitle && now - alertAt < TITLE_MILLIS) {
            // The title that came with it says more: it stays.
            return;
        }
        alert = message;
        alertAt = now;
        alertIsTitle = isTitle;
    }

    /**
     * Back to the parked server when its match is starting and the player asked for it. Outside the
     * parked server's code, on the next task of the game thread, where the practice world can close.
     */
    static void returnIfDue(Minecraft minecraft) {
        if (!returnDue || PracticeHost.serverSide()) {
            return;
        }
        returnDue = false;
        if (!CrystalVisualConfig.practiceAutoReturn()) {
            return;
        }
        minecraft.execute(() -> {
            if (!PracticeHost.parked()) {
                return;
            }
            PracticeHost.leave();
            PracticeGlue.chat(minecraft, Component.literal(spanish()
                    ? "Crystal Practice: empezó tu partida, de vuelta en el servidor."
                    : "Crystal Practice: your match started, back on the server."));
        });
    }

    /** The chime for the last alert, played outside the parked server's code, where sounds are kept back. */
    static void chime(Minecraft minecraft) {
        if (!chimeDue || PracticeHost.serverSide()) {
            return;
        }
        chimeDue = false;
        long now = Util.getMillis();
        if (!CrystalVisualConfig.practiceServerAlerts() || now - lastChime < CHIME_GAP_MILLIS) {
            return;
        }
        lastChime = now;
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING.value(), 1.6F, 0.9F));
    }

    // ------------------------------------------------------------------------------------------
    // For the window
    // ------------------------------------------------------------------------------------------

    /** The most recent chat lines, oldest first, at most {@code count}. */
    public static List<Line> lastChat(int count) {
        List<Line> lines = new ArrayList<>(Math.min(count, CHAT.size()));
        Iterator<Line> newest = CHAT.descendingIterator();
        while (newest.hasNext() && lines.size() < count) {
            lines.add(0, newest.next());
        }
        return lines;
    }

    public static Component title() {
        return title != null && Util.getMillis() - titleAt < TITLE_MILLIS ? title : null;
    }

    public static Component subtitle() {
        return subtitle != null && Util.getMillis() - titleAt < TITLE_MILLIS ? subtitle : null;
    }

    /** How far through its time on screen the title is, 0 to 1. */
    public static float titleAge() {
        return Math.min(1.0F, (Util.getMillis() - titleAt) / (float) TITLE_MILLIS);
    }

    public static Component actionBar() {
        return actionBar != null && Util.getMillis() - actionBarAt < ACTION_BAR_MILLIS ? actionBar : null;
    }

    public static Component alert() {
        return alert != null && Util.getMillis() - alertAt < ALERT_MILLIS ? alert : null;
    }

    /** Milliseconds since the last alert. */
    public static long alertAge() {
        return Util.getMillis() - alertAt;
    }

    private static boolean spanish() {
        return CrystalUi.spanish();
    }
}
