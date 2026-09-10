package com.zymekoh.crystaltweaks.client.compat;

import com.zymekoh.crystaltweaks.CrystalTweaksClient;
import com.zymekoh.crystaltweaks.client.CrystalPlacementFeedback;
import com.zymekoh.crystaltweaks.core.CrystalBreakPrediction;
import com.zymekoh.crystaltweaks.core.CrystalOptimizerGuard;
import com.zymekoh.crystaltweaks.core.GhostCrystalTracker;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.Minecraft;

/**
 * Decides once per launch whether another mod is already optimizing crystal interaction.
 *
 * <p>Two signals are used. A mod that names itself a crystal optimizer is taken at its word. Beyond
 * that, the Conflict Monitor's own scan is consulted: a mod whose Mixins land on the same
 * interaction path this mod uses is optimizing it too, whatever it calls itself.</p>
 *
 * <p>The scan reads jars and parses class files, so it runs off the render thread. Until it
 * finishes, the fast metadata check has already yielded to known/named optimizers.</p>
 */
public final class OptimizerConflictDetector {
    private OptimizerConflictDetector() {
    }

    public static void detectInBackground() {
        // Metadata is already in memory. Resolve named optimizers synchronously before gameplay.
        try {
            if (detectNamedOptimizer()) return;
        } catch (Exception exception) {
            CrystalOptimizerGuard.reportScanFailure();
            CrystalTweaksClient.LOGGER.warn("Optimizer metadata check failed; interaction helpers remain disabled", exception);
            return;
        }
        Thread worker = new Thread(OptimizerConflictDetector::detect, "Crystal Tweaks optimizer scan");
        worker.setDaemon(true);
        worker.start();
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
                boolean touchesInteraction = entry.points().stream()
                        .anyMatch(point -> point.area() == ConflictScanner.ConflictArea.NETWORK_OBSERVER);
                if (touchesInteraction) {
                    standDown(entry.modName(), "its Mixins land on the same interaction path");
                    return;
                }
            }
            if (report.failedMods() > 0) {
                Minecraft.getInstance().execute(CrystalOptimizerGuard::reportScanFailure);
                CrystalTweaksClient.LOGGER.warn("Compatibility scan was incomplete; interaction helpers remain disabled");
                return;
            }
            Minecraft.getInstance().execute(CrystalOptimizerGuard::completeScan);
        } catch (Exception exception) {
            Minecraft.getInstance().execute(CrystalOptimizerGuard::reportScanFailure);
            CrystalTweaksClient.LOGGER.warn("Compatibility scan failed; interaction helpers remain disabled", exception);
        }
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
