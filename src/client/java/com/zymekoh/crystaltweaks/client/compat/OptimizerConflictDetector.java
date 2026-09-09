package com.zymekoh.crystaltweaks.client.compat;

import com.zymekoh.crystaltweaks.CrystalTweaksClient;
import com.zymekoh.crystaltweaks.core.CrystalOptimizerGuard;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

/**
 * Decides once per launch whether another mod is already optimizing crystal interaction.
 *
 * <p>Two signals are used. A mod that names itself a crystal optimizer is taken at its word. Beyond
 * that, the Conflict Monitor's own scan is consulted: a mod whose Mixins land on the same
 * interaction path this mod uses is optimizing it too, whatever it calls itself.</p>
 *
 * <p>The scan reads jars and parses class files, so it runs off the render thread. Until it
 * finishes the optimizations stay on; it completes long before a server is joined.</p>
 */
public final class OptimizerConflictDetector {
    private OptimizerConflictDetector() {
    }

    public static void detectInBackground() {
        Thread worker = new Thread(OptimizerConflictDetector::detect, "Crystal Tweaks optimizer scan");
        worker.setDaemon(true);
        worker.start();
    }

    private static void detect() {
        try {
            for (ModContainer container : FabricLoader.getInstance().getAllMods()) {
                String id = container.getMetadata().getId();
                if (CrystalTweaksClient.MOD_ID.equals(id)) {
                    continue;
                }
                String name = container.getMetadata().getName();
                if (CrystalOptimizerGuard.looksLikeOptimizer(id, name)) {
                    standDown(name.isBlank() ? id : name, "it names itself a crystal optimizer");
                    return;
                }
            }

            ConflictScanner.ConflictReport report = ConflictScanner.scan();
            for (ConflictScanner.ConflictEntry entry : report.conflicts()) {
                boolean touchesInteraction = entry.points().stream()
                        .anyMatch(point -> point.area() == ConflictScanner.ConflictArea.NETWORK_OBSERVER);
                if (touchesInteraction) {
                    standDown(entry.modName(), "its Mixins land on the same interaction path");
                    return;
                }
            }
        } catch (Exception exception) {
            // A failed scan must not take the mod down with it; staying on matches earlier releases.
            CrystalTweaksClient.LOGGER.warn("Could not check for other crystal optimizers", exception);
        }
    }

    private static void standDown(String modName, String reason) {
        CrystalOptimizerGuard.reportConflict(modName);
        CrystalTweaksClient.LOGGER.info(
                "Local crystal optimizations disabled: {} is already optimizing crystals because {}",
                modName,
                reason);
    }
}
