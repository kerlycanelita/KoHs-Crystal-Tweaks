package com.zymekoh.crystaltweaks.client.fight;

import com.zymekoh.crystaltweaks.client.CrystalUi;
import com.zymekoh.crystaltweaks.client.CrystalVisualConfig;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

/**
 * What happened in the last seconds before you died, in crystal PvP terms: the blasts round you and
 * whose crystals they were, the totems popped on both sides, and how fast your health went. Shown on
 * the death screen, or in your chat when the server skips that screen.
 *
 * <p>Everything here is what this client already saw: explosions, crystals vanishing, totem
 * animations and its own health. Nothing is sent and nothing is asked of the server. Client thread
 * only.</p>
 */
public final class DeathRecap {
    /** How far back the recap looks. */
    private static final long WINDOW_NANOS = 8_000_000_000L;
    /** Blasts further than this did not reach you. */
    private static final double REACH = 12.0D;
    /** A respawn anchor explodes with power 5, a crystal with 6. */
    private static final float ANCHOR_RADIUS = 5.0F;
    private static final float CRYSTAL_RADIUS = 6.0F;
    /** With no death screen after this long, the server respawned you at once: the recap goes to the chat. */
    private static final long SCREEN_WAIT_NANOS = 1_500_000_000L;

    /** A crystal gone from your world: whose it was, for the blast that follows it. */
    private record Gone(long at, double x, double y, double z, boolean own) {
    }

    /** 0 another blast, 1 an anchor, 2 an enemy crystal, 3 your own crystal, 4 a crystal of no known owner. */
    private record Blast(long at, double distance, int kind) {
    }

    private record Hit(long at, float amount) {
    }

    private static final ArrayDeque<Gone> GONE = new ArrayDeque<>();
    private static final ArrayDeque<Blast> BLASTS = new ArrayDeque<>();
    private static final ArrayDeque<Hit> HITS = new ArrayDeque<>();
    private static final ArrayDeque<Long> OWN_POPS = new ArrayDeque<>();
    private static final ArrayDeque<Long> OTHER_POPS = new ArrayDeque<>();
    private static float lastHealth = -1.0F;
    private static long diedAt = -1L;
    private static boolean shownOnScreen;
    private static List<String> recap = List.of();

    private DeathRecap() {
    }

    /** A crystal left your world, {@code own} when it was yours. */
    public static void crystalGone(double x, double y, double z, boolean own, long now) {
        GONE.addLast(new Gone(now, x, y, z, own));
        while (GONE.size() > 32) {
            GONE.removeFirst();
        }
    }

    /** The server's explosion, as it is handled. */
    public static void explosion(LocalPlayer player, double x, double y, double z, float radius, long now) {
        if (player == null) {
            return;
        }
        double distance = Math.sqrt(player.distanceToSqr(x, y, z));
        if (distance > REACH) {
            return;
        }
        int kind = 0;
        if (Math.abs(radius - ANCHOR_RADIUS) < 0.01F) {
            kind = 1;
        } else if (Math.abs(radius - CRYSTAL_RADIUS) < 0.01F) {
            kind = 4;
            for (Iterator<Gone> iterator = GONE.descendingIterator(); iterator.hasNext(); ) {
                Gone gone = iterator.next();
                double dx = gone.x() - x;
                double dy = gone.y() - y;
                double dz = gone.z() - z;
                if (now - gone.at() < 1_500_000_000L && dx * dx + dy * dy + dz * dz <= 2.25D) {
                    kind = gone.own() ? 3 : 2;
                    break;
                }
            }
        }
        BLASTS.addLast(new Blast(now, distance, kind));
        while (BLASTS.size() > 48) {
            BLASTS.removeFirst();
        }
    }

    /** A totem just saved someone: you, or another player in view. */
    public static void totemPopped(boolean self, long now) {
        ArrayDeque<Long> pops = self ? OWN_POPS : OTHER_POPS;
        pops.addLast(now);
        while (pops.size() > 32) {
            pops.removeFirst();
        }
    }

    /** Each client tick: your health, and the moment you die. */
    public static void tick(LocalPlayer player, long now) {
        if (player == null) {
            lastHealth = -1.0F;
            return;
        }
        track(player, now);
        if (player.isDeadOrDying()) {
            if (diedAt < 0L) {
                died(now);
            } else if (!shownOnScreen && now - diedAt > SCREEN_WAIT_NANOS && !recap.isEmpty()) {
                // No death screen: the server respawned you at once.
                shownOnScreen = true;
                toChat(player);
            }
        } else if (diedAt >= 0L) {
            // Alive again: a server that respawns at once never showed a death screen.
            if (!shownOnScreen && !recap.isEmpty()) {
                shownOnScreen = true;
                toChat(player);
            }
            diedAt = -1L;
        }
    }

    /** What your health lost since the last look. */
    private static void track(LocalPlayer player, long now) {
        float health = player.getHealth() + player.getAbsorptionAmount();
        if (lastHealth >= 0.0F && health < lastHealth - 0.01F) {
            HITS.addLast(new Hit(now, lastHealth - health));
            while (HITS.size() > 64) {
                HITS.removeFirst();
            }
            if (diedAt >= 0L && now - diedAt < SCREEN_WAIT_NANOS && CrystalVisualConfig.deathRecap()) {
                // The killing hit can land after the death screen opened: the recap counts it too.
                recap = build(now, CrystalUi.spanish());
            }
        }
        lastHealth = health;
    }

    /** The recap the death screen shows now; empty when there is none. */
    public static List<String> screenLines() {
        return recap;
    }

    private static void died(long now) {
        diedAt = now;
        shownOnScreen = false;
        recap = CrystalVisualConfig.deathRecap() ? build(now, CrystalUi.spanish()) : List.of();
    }

    /**
     * The death screen is up: it shows the recap, and the chat does not need it. The screen may open
     * before the tick has seen the death; then the last hit is counted and the recap built here.
     */
    public static List<String> forDeathScreen(LocalPlayer player, long now) {
        if (diedAt < 0L) {
            if (player != null) {
                track(player, now);
            }
            died(now);
        }
        shownOnScreen = true;
        return recap;
    }

    private static void toChat(LocalPlayer player) {
        boolean spanish = CrystalUi.spanish();
        Component message = Component.literal(spanish ? "Resumen de cristales" : "Crystal recap").withStyle(ChatFormatting.LIGHT_PURPLE);
        for (String line : recap) {
            message = message.copy().append(Component.literal("\n · " + line).withStyle(ChatFormatting.GRAY));
        }
        player.sendSystemMessage(message);
    }

    static List<String> build(long now, boolean spanish) {
        long from = now - WINDOW_NANOS;
        int[] kinds = new int[5];
        double nearest = Double.MAX_VALUE;
        Blast last = null;
        for (Blast blast : BLASTS) {
            if (blast.at() >= from) {
                kinds[blast.kind()]++;
                nearest = Math.min(nearest, blast.distance());
                last = blast;
            }
        }
        float lost = 0.0F;
        float lastSecond = 0.0F;
        for (Hit hit : HITS) {
            if (hit.at() >= from) {
                lost += hit.amount();
                if (hit.at() >= now - 1_000_000_000L) {
                    lastSecond += hit.amount();
                }
            }
        }
        int ownPops = count(OWN_POPS, from);
        int otherPops = count(OTHER_POPS, from);
        List<String> lines = new ArrayList<>();
        int crystals = kinds[2] + kinds[3] + kinds[4];
        if (crystals + kinds[1] + kinds[0] > 0) {
            StringBuilder blasts = new StringBuilder();
            if (crystals > 0) {
                blasts.append(spanish ? crystals + (crystals == 1 ? " cristal" : " cristales") : crystals + (crystals == 1 ? " crystal" : " crystals"));
                List<String> owners = new ArrayList<>();
                if (kinds[2] > 0) {
                    owners.add(kinds[2] + (spanish ? " ajeno" + (kinds[2] == 1 ? "" : "s") : " enemy"));
                }
                if (kinds[3] > 0) {
                    owners.add(kinds[3] + (spanish ? " tuyo" + (kinds[3] == 1 ? "" : "s") : " yours"));
                }
                if (!owners.isEmpty()) {
                    blasts.append(" (").append(String.join(", ", owners)).append(')');
                }
            }
            if (kinds[1] > 0) {
                blasts.append(blasts.length() > 0 ? ", " : "").append(kinds[1]).append(spanish ? (kinds[1] == 1 ? " ancla" : " anclas") : (kinds[1] == 1 ? " anchor" : " anchors"));
            }
            if (kinds[0] > 0) {
                blasts.append(blasts.length() > 0 ? ", " : "").append(kinds[0]).append(spanish ? " otras" : " other");
            }
            lines.add((spanish ? "Explosiones: " : "Blasts: ") + blasts + String.format(Locale.ROOT, spanish ? " · la más cerca a %.1f bloques" : " · nearest %.1f blocks", nearest));
        }
        if (ownPops + otherPops > 0) {
            lines.add(spanish ? "Tótems: reventaste " + ownPops + " · los demás " + otherPops : "Totems: you popped " + ownPops + " · others " + otherPops);
        }
        if (lost > 0.0F) {
            lines.add(String.format(Locale.ROOT, spanish ? "Daño: %.0f en 8 s, %.0f en el último segundo" : "Damage: %.0f in 8 s, %.0f in the last second",
                    lost, lastSecond));
        }
        if (last != null && last.kind() >= 2) {
            String whose = switch (last.kind()) {
                case 2 -> spanish ? "un cristal ajeno" : "an enemy crystal";
                case 3 -> spanish ? "tu propio cristal" : "your own crystal";
                default -> spanish ? "un cristal" : "a crystal";
            };
            lines.add(String.format(Locale.ROOT, spanish ? "Lo último: %s a %.1f bloques" : "Last blast: %s, %.1f blocks away", whose, last.distance()));
        }
        return lines;
    }

    private static int count(ArrayDeque<Long> times, long from) {
        int count = 0;
        for (long at : times) {
            if (at >= from) {
                count++;
            }
        }
        return count;
    }

    public static void reset() {
        GONE.clear();
        BLASTS.clear();
        HITS.clear();
        OWN_POPS.clear();
        OTHER_POPS.clear();
        lastHealth = -1.0F;
        diedAt = -1L;
        recap = List.of();
    }
}
