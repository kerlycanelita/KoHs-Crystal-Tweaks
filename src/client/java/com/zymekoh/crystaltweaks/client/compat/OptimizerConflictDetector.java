package com.zymekoh.crystaltweaks.client.compat;

import com.zymekoh.crystaltweaks.CrystalTweaksClient;
import com.zymekoh.crystaltweaks.client.CrystalPlacementFeedback;
import com.zymekoh.crystaltweaks.core.CrystalBreakPrediction;
import com.zymekoh.crystaltweaks.core.CrystalOptimizerGuard;
import com.zymekoh.crystaltweaks.core.GhostCrystalTracker;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.Minecraft;

/**
 * Decides once per launch whether another mod is already optimizing crystal interaction.
 *
 * <p>Two signals are used. A mod that names itself a crystal optimizer is taken at its word. Beyond
 * that, the Conflict Monitor's own scan is consulted: a mod whose Mixins land on the same
 * interaction path <em>and</em> whose own name or Mixin is about crystals is optimizing them too,
 * whatever it calls itself.</p>
 *
 * <p>The scan reads jars and parses class files, so it runs off the render thread. Until it
 * finishes, the fast metadata check has already yielded to known/named optimizers.</p>
 *
 * <p>Every path out of here that is not a positive finding leaves the optimizations running. A mod
 * that cannot be read has not been shown to conflict, and turning the feature off on that basis is
 * indistinguishable, to the player, from the mod being broken.</p>
 */
public final class OptimizerConflictDetector {
    private OptimizerConflictDetector() {
    }

    public static void detectInBackground() {
        // Metadata is already in memory. Resolve named optimizers synchronously before gameplay.
        try {
            if (detectNamedOptimizer()) return;
        } catch (Exception exception) {
            // A metadata read that throws says nothing about whether a rival optimizer is present, so
            // the deeper scan below still gets its turn instead of the mod giving up here.
            CrystalTweaksClient.LOGGER.warn("Optimizer metadata check failed; falling back to the Mixin scan", exception);
        }
        Thread worker = new Thread(OptimizerConflictDetector::detect, "Crystal Tweaks optimizer scan");
        worker.setDaemon(true);
        worker.start();
    }

    /** Runs the whole detection again, for the re-scan control in the settings screen. */
    public static void rescan() {
        CrystalOptimizerGuard.clearConflict();
        detectInBackground();
    }

    private static boolean detectNamedOptimizer() {
        for (ModContainer container : FabricLoader.getInstance().getAllMods()) {
            String id = container.getMetadata().getId();
            if (CrystalTweaksClient.MOD_ID.equals(id)) {
                continue;
            }
            String name = container.getMetadata().getName();
            if (CrystalOptimizerGuard.looksLikeOptimizer(id, name)) {
                standDown(name.isBlank() ? id : name, "it names itself a crystal optimizer");
                return true;
            }
        }
        return false;
    }

    private static void detect() {
        try {
            ConflictScanner.ConflictReport report = ConflictScanner.scan();
            for (ConflictScanner.ConflictEntry entry : report.conflicts()) {
                if (optimizesCrystals(entry)) {
                    standDown(entry.modName(), "its Mixins land on the same crystal interaction path");
                    return;
                }
            }
            if (report.failedMods() > 0) {
                Minecraft.getInstance().execute(CrystalOptimizerGuard::reportScanIncomplete);
                CrystalTweaksClient.LOGGER.warn(
                        "Compatibility scan could not read {} mod(s); no crystal optimizer was found in the rest, "
                                + "so interaction helpers stay active",
                        report.failedMods());
                return;
            }
            Minecraft.getInstance().execute(CrystalOptimizerGuard::completeScan);
        } catch (Exception exception) {
            Minecraft.getInstance().execute(CrystalOptimizerGuard::reportScanIncomplete);
            CrystalTweaksClient.LOGGER.warn(
                    "Compatibility scan failed; no conflict was proven, so interaction helpers stay active",
                    exception);
        }
    }

    /**
     * Collects the overlap and asks the guard whether it is another crystal optimizer. The decision
     * itself lives in {@link CrystalOptimizerGuard} so it can be exercised without a game client.
     */
    private static boolean optimizesCrystals(ConflictScanner.ConflictEntry entry) {
        List<String> networkMixins = entry.points().stream()
                .filter(point -> point.area() == ConflictScanner.ConflictArea.NETWORK_OBSERVER)
                .map(ConflictScanner.ConflictPoint::foreignMixinClass)
                .toList();
        return CrystalOptimizerGuard.overlapOptimizesCrystals(
                entry.modId(), entry.modName(), networkMixins);
    }

    private static void standDown(String modName, String reason) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!minecraft.isSameThread()) {
            minecraft.execute(() -> standDown(modName, reason));
            return;
        }

        CrystalOptimizerGuard.reportConflict(modName);
        // Serialize shutdown with client prediction updates so no in-flight task can repopulate
        // the cleared state after the background scan finishes.
        CrystalBreakPrediction.reset();
        GhostCrystalTracker.reset();
        CrystalPlacementFeedback.resetPredictionState();
        CrystalTweaksClient.LOGGER.info(
                "Local crystal optimizations disabled: {} is already optimizing crystals because {}",
                modName,
                reason);
    }
}
