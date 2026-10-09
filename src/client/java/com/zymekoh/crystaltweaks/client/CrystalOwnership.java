package com.zymekoh.crystaltweaks.client;

import com.google.common.collect.MapMaker;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;

/**
 * Local attribution heuristic, NOT server-provided ownership or a combat target selector.
 *
 * <p>The rules are {@link OwnershipLedger}'s: a crystal is yours when it appears on a base you
 * clicked, no sooner than an answer can travel, and the server acknowledges that click right after
 * it. This class only feeds the ledger from the game: the placements you send, the crystals that
 * load, and the server's acknowledgements.</p>
 *
 * <p>Nothing here predicts, cancels or sends anything. It reads packets the server sends every
 * client anyway and only decides which colour profile the renderer uses, which is why it keeps
 * running while another optimizer owns the interaction path.</p>
 */
public final class CrystalOwnership {
    // Weak keys compared by identity: Entity.hashCode() reads the entity id, which from 26.2 throws
    // for a crystal that was never added to a level, as client-side stand-ins are.
    private static final Map<EndCrystal, Boolean> OWN = new MapMaker().weakKeys().makeMap();
    private static final OwnershipLedger<EndCrystal> LEDGER = new OwnershipLedger<>(OWN);
    private static Object world;

    private CrystalOwnership() { }

    public static void observeWorld(Object currentWorld) {
        if (world != currentWorld) {
            reset();
            world = currentWorld;
        }
    }

    /** A crystal placement left for the server: the base clicked and the placement's sequence number. */
    public static void record(BlockPos base, int sequence, long now) {
        LEDGER.clicked(base.asLong(), sequence, now);
    }

    public static void loaded(EndCrystal crystal) {
        observeWorld(crystal.level());
        BlockPos base = BlockPos.containing(crystal.getX(), crystal.getY() - 1, crystal.getZ());
        LEDGER.appeared(crystal, base.asLong(), System.nanoTime());
    }

    /** The server acknowledged every placement up to this sequence number. Call on the game thread. */
    public static void acknowledged(int sequence) {
        LEDGER.acknowledged(sequence, System.nanoTime());
    }

    /** A stand-in drawn for a placement of the player's own, such as a ghost crystal. */
    public static void markOwn(EndCrystal crystal) {
        OWN.put(crystal, Boolean.TRUE);
    }

    public static boolean useOtherProfile(EndCrystal crystal) {
        return CrystalVisualConfig.enemyCustomEnabled() && !Boolean.TRUE.equals(OWN.get(crystal));
    }

    public static void reset() {
        LEDGER.clear();
        OWN.clear();
        world = null;
    }
}
