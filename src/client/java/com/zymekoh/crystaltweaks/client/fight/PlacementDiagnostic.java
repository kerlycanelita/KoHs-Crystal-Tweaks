package com.zymekoh.crystaltweaks.client.fight;

import com.zymekoh.crystaltweaks.client.CrystalVisualConfig;
import com.zymekoh.crystaltweaks.core.CrystalBreakPrediction;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Why a crystal did not appear where you clicked. The reasons are the ones Vanilla checks before it
 * places a crystal, read from your own world the moment your click leaves; the last is the one only
 * the server knows, when nothing came back in time. It reads what is already on your screen, says
 * it under the crosshair, and sends nothing. Client thread only.
 */
public final class PlacementDiagnostic {
    public enum Reason {
        NOT_OBSIDIAN("Not obsidian or bedrock", "No es obsidiana ni bedrock"),
        BLOCK_ABOVE("A block is in the way above", "Hay un bloque encima"),
        CRYSTAL("A crystal is already there", "Ya hay un cristal ahí"),
        ITEM("A dropped item is in the way", "Un ítem tirado estorba"),
        ORB("Experience is in the way", "La experiencia estorba"),
        PLAYER("A player is in the way, maybe you", "Un jugador estorba, quizá tú"),
        ENTITY("Something is in the way", "Algo estorba"),
        SPENT("Your click hit the crystal you were breaking", "Tu clic dio en el cristal que rompías"),
        SERVER("The server did not place it", "El servidor no lo colocó");

        private final String english;
        private final String spanish;

        Reason(String english, String spanish) {
            this.english = english;
            this.spanish = spanish;
        }

        public String text(boolean inSpanish) {
            return inSpanish ? this.spanish : this.english;
        }

        /** The one you can fix from the settings: Paced placement keeps such a click for the obsidian. */
        public String hint(boolean inSpanish) {
            return this == SPENT ? (inSpanish ? "Colocar a tiempo lo guarda para la obsidiana" : "Paced placement keeps it for the obsidian") : "";
        }
    }

    /** How long a reason stays under the crosshair. */
    public static final long SHOW_NANOS = 1_600_000_000L;

    private static Reason shown;
    private static long shownAt;
    private static BlockPos waiting;
    private static long waitingDeadline;

    private PlacementDiagnostic() {
    }

    /** Your crystal placement just left for the server, on the obsidian at {@code base}. */
    public static void placementSent(ClientLevel level, BlockPos base, long now) {
        if (!CrystalVisualConfig.placementDiagnostic()) {
            return;
        }
        Reason reason = check(level, base);
        if (reason != null) {
            show(reason, now);
            waiting = null;
            return;
        }
        // Your world says it fits: if no crystal comes back in twice the round trip, the server said no.
        waiting = base.immutable();
        waitingDeadline = now + Math.max(500_000_000L, 2L * CrystalBreakPrediction.predictionWindowNanos());
    }

    /** Vanilla's own checks, in its order, on this client's world. Null when the crystal fits. */
    static Reason check(ClientLevel level, BlockPos base) {
        BlockState state = level.getBlockState(base);
        if (!state.is(Blocks.OBSIDIAN) && !state.is(Blocks.BEDROCK)) {
            return Reason.NOT_OBSIDIAN;
        }
        BlockPos above = base.above();
        if (!level.isEmptyBlock(above)) {
            return Reason.BLOCK_ABOVE;
        }
        AABB room = new AABB(above.getX(), above.getY(), above.getZ(), above.getX() + 1.0D, above.getY() + 2.0D, above.getZ() + 1.0D);
        Reason found = null;
        for (Entity entity : level.getEntities(null, room)) {
            if (entity instanceof EndCrystal crystal) {
                // A crystal you just broke is gone on the server already: it is not what stops this one.
                if (crystal.isRemoved() || CrystalBreakPrediction.isHidden(crystal)) {
                    continue;
                }
                return Reason.CRYSTAL;
            }
            Reason reason = entity instanceof ItemEntity ? Reason.ITEM : entity instanceof ExperienceOrb ? Reason.ORB
                    : entity instanceof Player ? Reason.PLAYER : Reason.ENTITY;
            if (found == null || reason == Reason.PLAYER) {
                found = reason;
            }
        }
        return found;
    }

    /** A crystal appeared on {@code base}: the placement waiting for it went through. */
    public static void crystalLoaded(BlockPos base) {
        if (waiting != null && waiting.equals(base)) {
            waiting = null;
        }
    }

    /** A Use click was spent on the crystal you were breaking, so nothing could be placed with it. */
    public static void spentOnBreaking(long now) {
        if (CrystalVisualConfig.placementDiagnostic()) {
            show(Reason.SPENT, now);
        }
    }

    public static void tick(long now) {
        if (waiting != null && now > waitingDeadline) {
            waiting = null;
            show(Reason.SERVER, now);
        }
    }

    private static void show(Reason reason, long now) {
        shown = reason;
        shownAt = now;
    }

    /** The reason on show, or null. */
    public static Reason shown(long now) {
        return shown != null && now - shownAt < SHOW_NANOS ? shown : null;
    }

    /** How long the reason on show has been up, in nanoseconds. */
    public static long age(long now) {
        return now - shownAt;
    }

    public static void reset() {
        shown = null;
        waiting = null;
    }
}
