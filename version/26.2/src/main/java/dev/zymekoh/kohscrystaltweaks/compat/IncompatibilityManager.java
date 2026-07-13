package dev.zymekoh.kohscrystaltweaks.compat;

import dev.zymekoh.kohscrystaltweaks.KoHsCrystalTweaks;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

/**
 * Detects incompatible crystal optimizers before KoHs gameplay mixins are applied.
 *
 * <p>The scanner is deliberately conservative: an unknown mod is blocked only when it directly
 * targets KoHs code or when a crystal-related mixin touches the same target class and critical
 * method as a KoHs mixin.</p>
 */
public final class IncompatibilityManager {
    private static final Set<String> KNOWN_CRYSTAL_OPTIMIZERS = Set.of("marlowcrystal");

    private static volatile boolean initialized;
    private static List<Conflict> conflicts = List.of();

    private IncompatibilityManager() {
    }

    public static synchronized void initialize() {
        if (initialized) {
            return;
        }

        FabricLoader loader = FabricLoader.getInstance();
        Map<String, Conflict> detected = new LinkedHashMap<>();

        for (String modId : KNOWN_CRYSTAL_OPTIMIZERS) {
            loader.getModContainer(modId).ifPresent(container -> detected.put(modId,
                    conflictFor(container, ConflictType.KNOWN_CRYSTAL_OPTIMIZER, List.of())));
        }

        try {
            ModContainer ownContainer = loader.getModContainer(KoHsCrystalTweaks.MOD_ID).orElse(null);
            if (ownContainer != null) {
                for (Conflict conflict : MixinConflictScanner.scan(loader, ownContainer,
                        KNOWN_CRYSTAL_OPTIMIZERS)) {
                    detected.merge(conflict.modId(), conflict, IncompatibilityManager::mergeConflicts);
                }
            } else {
                KoHsCrystalTweaks.LOGGER.warn("Unable to locate the KoHs mod container; generic mixin conflict scanning was skipped");
            }
        } catch (RuntimeException exception) {
            // Fail open for heuristic scanning. Explicit known conflicts above remain blocked.
            KoHsCrystalTweaks.LOGGER.warn("Generic mixin conflict scanning failed; known incompatibility checks remain active", exception);
        }

        conflicts = List.copyOf(detected.values());
        initialized = true;

        for (Conflict conflict : conflicts) {
            KoHsCrystalTweaks.LOGGER.error(
                    "Startup blocked by incompatible mod: {} ({}) {} [{}]",
                    conflict.modName(), conflict.modId(), conflict.version(), conflict.type());
            for (ConflictPoint point : conflict.points()) {
                KoHsCrystalTweaks.LOGGER.error("Conflicting mixin point: {}#{} via {}",
                        point.targetClass(), point.targetMethod(), point.mixinClass());
            }
        }
    }

    public static boolean isBlocked() {
        initialize();
        return !conflicts.isEmpty();
    }

    public static List<Conflict> getConflicts() {
        initialize();
        return conflicts;
    }

    private static Conflict conflictFor(ModContainer container, ConflictType type, List<ConflictPoint> points) {
        return new Conflict(
                container.getMetadata().getId(),
                container.getMetadata().getName(),
                container.getMetadata().getVersion().getFriendlyString(),
                type,
                List.copyOf(points));
    }

    private static Conflict mergeConflicts(Conflict first, Conflict second) {
        List<ConflictPoint> points = new ArrayList<>(first.points());
        for (ConflictPoint point : second.points()) {
            if (!points.contains(point)) {
                points.add(point);
            }
        }
        ConflictType strongest = first.type().ordinal() >= second.type().ordinal()
                ? first.type() : second.type();
        return new Conflict(first.modId(), first.modName(), first.version(), strongest, List.copyOf(points));
    }

    public enum ConflictType {
        KNOWN_CRYSTAL_OPTIMIZER,
        MIXIN_OVERLAP,
        DIRECT_KOHS_MUTATION
    }

    public record Conflict(
            String modId,
            String modName,
            String version,
            ConflictType type,
            List<ConflictPoint> points
    ) {
    }

    public record ConflictPoint(String targetClass, String targetMethod, String mixinClass) {
    }
}
