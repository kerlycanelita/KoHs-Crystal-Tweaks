package com.zymekoh.crystaltweaks.client.benchmark;

import java.util.List;

/**
 * One finished benchmark: what was measured and under which conditions. Saved as it is, so two runs
 * taken with different optimizers can be compared later without re-measuring either.
 *
 * <p>Times are milliseconds. "Network" is when the server's answer reached this computer, read on
 * the network thread; "shown" is when the game thread acted on it, which is what the player sees.</p>
 */
public record BenchmarkRun(
        long timestamp,
        String minecraftVersion,
        String modVersion,
        String environment,
        String serverBrand,
        String optimizer,
        List<String> detectedOptimizers,
        String herziumOrder,
        long durationMillis,
        int placements,
        int attacks,
        int confirmedBreaks,
        int predictedByCrystalTweaks,
        int removedEarlyByOtherMod,
        int unconfirmedAttacks,
        int unmatchedPlacements,
        long debounceRefusals,
        double reportedPingMillis,
        BenchmarkStats placeToSpawnNetwork,
        BenchmarkStats placeToSpawnShown,
        BenchmarkStats attackToExplosionNetwork,
        BenchmarkStats attackToRemovalNetwork,
        BenchmarkStats attackToRemovalShown,
        BenchmarkStats attackToGone,
        BenchmarkStats replaceGap,
        BenchmarkStats cycle,
        BenchmarkStats swapToPlace,
        BenchmarkStats frameTime,
        double averageFps,
        double onePercentLowFps,
        // When the explosion's sound and burst played on this client; absent in runs saved before 2.4.0.
        BenchmarkStats attackToExplosionHeard
) {
    /** A run long enough to say something: a handful of breaks and placements each. */
    public boolean meaningful() {
        return this.confirmedBreaks >= 3 && this.placeToSpawnShown != null && this.placeToSpawnShown.count() >= 3;
    }

    /** Crystals per second one base could cycle at, from the median place-to-place time. */
    public double crystalsPerSecond() {
        return this.cycle != null && this.cycle.present() && this.cycle.median() > 0 ? 1000.0D / this.cycle.median() : 0.0D;
    }
}
