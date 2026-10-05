package com.zymekoh.crystaltweaks.core;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;

/**
 * The explosion of a crystal the player just broke, heard and seen at the hit instead of a round
 * trip later.
 *
 * <p>The break prediction already hides the crystal the moment it is hit; until now its explosion
 * still waited for the server's packet, so at 100 ms of ping the crystal vanished in silence and
 * blew up a tenth of a second after. Here the game's own explosion sound and burst play at the
 * prediction, exactly as the packet would play them, and when the server's explosion arrives for
 * that crystal its sound and burst are skipped so nothing plays twice. Everything else the packet
 * carries, the knockback above all, and the block debris, still comes from the server, untouched.</p>
 *
 * <p>Nothing is sent, changed or delayed: this is sound and particles on this client only. It runs
 * under the same guard as the break prediction, so a hit the server is not expected to accept
 * never sounds, and it stands down with the rest when another optimizer is installed.</p>
 */
public final class InstantExplosion {
    /** An explosion already played, waiting for the server's copy. */
    private record Played(double x, double y, double z, long at) {
    }

    /** How close the server's explosion must be to the crystal we predicted, squared: 1.5 blocks. */
    private static final double SAME_SPOT_SQUARED = 2.25D;
    private static final int MAX_TRACKED = 16;

    /** Client thread only. */
    private static final Deque<Played> PLAYED = new ArrayDeque<>();

    private InstantExplosion() {
    }

    /** At the predicted break: the game's explosion sound and burst, where the crystal stands. */
    public static void play(EndCrystal crystal, long now) {
        if (!CrystalOptimizerGuard.optimizationsAllowed()) {
            return;
        }
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        prune(now);
        if (PLAYED.size() >= MAX_TRACKED) {
            PLAYED.pollFirst();
        }
        double x = crystal.getX();
        double y = crystal.getY();
        double z = crystal.getZ();
        // As ClientPacketListener.handleExplosion plays a crystal's explosion: same sound, volume and
        // pitch spread, same burst. The custom sound, when chosen, takes its place there too.
        level.playLocalSound(x, y, z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 4.0F,
                (1.0F + (level.getRandom().nextFloat() - level.getRandom().nextFloat()) * 0.2F) * 0.7F, false);
        level.addParticle(ParticleTypes.EXPLOSION_EMITTER, x, y, z, 1.0D, 0.0D, 0.0D);
        PLAYED.addLast(new Played(x, y, z, now));
        com.zymekoh.crystaltweaks.client.benchmark.CrystalBenchmark.onExplosionHeard(x, y, z);
    }

    /**
     * The server's explosion has arrived: true when it is one this client already played, in which
     * case its sound and burst are skipped. Each prediction answers for one explosion only.
     */
    public static boolean alreadyPlayed(double x, double y, double z) {
        long now = System.nanoTime();
        prune(now);
        for (Iterator<Played> iterator = PLAYED.iterator(); iterator.hasNext(); ) {
            Played played = iterator.next();
            double dx = played.x() - x;
            double dy = played.y() - y;
            double dz = played.z() - z;
            if (dx * dx + dy * dy + dz * dz <= SAME_SPOT_SQUARED) {
                iterator.remove();
                return true;
            }
        }
        return false;
    }

    /** A prediction the server never confirmed is forgotten with the break prediction's window. */
    private static void prune(long now) {
        long window = CrystalBreakPrediction.predictionWindowNanos();
        while (!PLAYED.isEmpty() && now - PLAYED.peekFirst().at() > window) {
            PLAYED.pollFirst();
        }
    }

    public static void reset() {
        PLAYED.clear();
    }
}
