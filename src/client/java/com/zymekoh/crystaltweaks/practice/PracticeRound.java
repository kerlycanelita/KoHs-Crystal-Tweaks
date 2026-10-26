package com.zymekoh.crystaltweaks.practice;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * One round of Crystal Practice, counted on the practice world's server: the crystals you placed and
 * broke, how fast each went from placed to broken, what the blasts did to each side, and the totems
 * popped. When the round ends you get the report in the chat and, for ten seconds, every blast that
 * hurt someone marked where it went off: violet where it hurt the bot, crimson where it hurt you.
 * Server thread only.
 */
final class PracticeRound {
    /** How long the marks of the last round stay up, in ticks. */
    private static final int MAP_TICKS = 200;
    private static final int MAX_MARKS = 32;
    private static final int AGAINST_BOT = 0xC880FF;
    private static final int AGAINST_YOU = 0xFF315C;

    private record Mark(Vec3 at, boolean againstYou, float damage) {
    }

    final int number;
    private final long startTick;
    int placed;
    int broken;
    int botPops;
    int ownPops;
    float dealt;
    float taken;
    float best;
    private long speedTicks;
    private int speedCount;
    /** Your crystals in the world, by entity id, and the tick each appeared on. */
    private final Map<Integer, Long> yours = new HashMap<>();
    private final List<Mark> marks = new ArrayList<>();
    private int mapTicks;

    PracticeRound(int number, long startTick) {
        this.number = number;
        this.startTick = startTick;
    }

    void crystalPlaced(int id, long tick) {
        this.placed++;
        if (this.yours.size() < 256) {
            this.yours.put(id, tick);
        }
    }

    void crystalBroken(int id, long tick) {
        this.broken++;
        Long placedAt = this.yours.remove(id);
        if (placedAt != null) {
            this.speedTicks += tick - placedAt;
            this.speedCount++;
        }
    }

    /** A blast hurt someone: {@code you} when it was the player, else the bot. */
    void blast(Vec3 at, boolean you, float damage) {
        if (you) {
            this.taken += damage;
        } else {
            this.dealt += damage;
            this.best = Math.max(this.best, damage);
        }
        if (at != null && this.marks.size() < MAX_MARKS) {
            this.marks.add(new Mark(at, you, damage));
        }
    }

    /** The report, in the chat, and the marks, from now on. */
    void finish(ServerPlayer player, long tick, boolean won, boolean report, boolean map, boolean spanish) {
        this.mapTicks = map ? MAP_TICKS : 0;
        if (!report || player == null) {
            return;
        }
        long seconds = Math.max(0L, (tick - this.startTick) / 20L);
        String time = String.format(Locale.ROOT, "%d:%02d", seconds / 60L, seconds % 60L);
        String head = spanish
                ? "Ronda " + this.number + " · " + (won ? "ganada" : "perdida") + " en " + time
                : "Round " + this.number + " · " + (won ? "won" : "lost") + " in " + time;
        String crystals = spanish
                ? "Cristales: " + this.placed + " puestos, " + this.broken + " rotos"
                : "Crystals: " + this.placed + " placed, " + this.broken + " broken";
        if (this.speedCount > 0) {
            crystals += String.format(Locale.ROOT, spanish ? " · de poner a romper %.2f s" : " · place to break %.2f s",
                    this.speedTicks / (double) this.speedCount / 20.0D);
        }
        String damage = String.format(Locale.ROOT, spanish ? "Daño: hiciste %.0f · recibiste %.0f · mejor explosión %.1f"
                : "Damage: dealt %.0f · taken %.0f · best blast %.1f", this.dealt, this.taken, this.best);
        String totems = spanish
                ? "Tótems: el bot reventó " + this.botPops + " · tú " + this.ownPops
                : "Totems: the bot popped " + this.botPops + " · you " + this.ownPops;
        Component message = Component.literal(head).withStyle(won ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.RED, ChatFormatting.BOLD);
        for (String line : new String[] {crystals, damage, totems}) {
            message = message.copy().append(Component.literal("\n · " + line).withStyle(ChatFormatting.GRAY));
        }
        player.sendSystemMessage(message);
    }

    /** Each tick after the round: its blasts, marked for the player, a few times a second. */
    boolean showMap(ServerLevel level, ServerPlayer player) {
        if (this.mapTicks <= 0 || player == null) {
            return false;
        }
        this.mapTicks--;
        if (this.mapTicks % 5 != 0) {
            return true;
        }
        for (Mark mark : this.marks) {
            float size = Math.min(3.0F, 1.0F + mark.damage() / 8.0F);
            level.sendParticles(player, new DustParticleOptions(mark.againstYou() ? AGAINST_YOU : AGAINST_BOT, size), true, true,
                    mark.at().x, mark.at().y + 0.3D, mark.at().z, 4, 0.12D, 0.12D, 0.12D, 0.0D);
            level.sendParticles(player, ParticleTypes.END_ROD, true, true, mark.at().x, mark.at().y + 0.8D, mark.at().z, 1,
                    0.0D, 0.25D, 0.0D, 0.0D);
        }
        return true;
    }
}
