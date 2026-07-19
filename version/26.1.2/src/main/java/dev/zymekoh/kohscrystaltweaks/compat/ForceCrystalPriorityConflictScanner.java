package dev.zymekoh.kohscrystaltweaks.compat;

import dev.zymekoh.kohscrystaltweaks.KoHsCrystalTweaks;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

/** Detects exact mixin overlaps with the optional explicit hotbar keybinding hook. */
public final class ForceCrystalPriorityConflictScanner {
    private static final String FEATURE_MIXIN = "KeyMappingForceCrystalPvpPriorityMixin";
    private static final String FEATURE_CLASS =
            "dev.zymekoh.kohscrystaltweaks.core.ForceCrystalPvpPriority";
    private static final String FEATURE_MIXIN_CLASS =
            "dev.zymekoh.kohscrystaltweaks.mixin.KeyMappingForceCrystalPvpPriorityMixin";
    private static final String FEATURE_ACCESSOR_CLASS =
            "dev.zymekoh.kohscrystaltweaks.mixin.KeyMappingAccessor";
    private static final int MAX_POINTS_PER_MOD = 8;

    private ForceCrystalPriorityConflictScanner() {
    }

    /**
     * Scans installed client mods. Failures are intentionally fail-open because this feature is optional
     * and the warning must never become a false startup blocker.
     */
    public static List<Conflict> scanInstalledMods() {
        FabricLoader loader = FabricLoader.getInstance();
        ModContainer ownContainer = loader.getModContainer(KoHsCrystalTweaks.MOD_ID).orElse(null);
        if (ownContainer == null) {
            KoHsCrystalTweaks.LOGGER.warn(
                    "Force Crystal PvP Priority conflict scan skipped: KoHs mod container was not found");
            return List.of();
        }

        List<MixinConflictScanner.MixinSignature> featureSignatures;
        try {
            featureSignatures = MixinConflictScanner.readMixinSignatures(ownContainer).stream()
                    .filter(signature -> FEATURE_MIXIN.equals(simpleName(signature.mixinClass())))
                    .toList();
        } catch (RuntimeException exception) {
            KoHsCrystalTweaks.LOGGER.warn(
                    "Force Crystal PvP Priority conflict scan skipped: its own mixin signature could not be read",
                    exception);
            return List.of();
        }
        if (featureSignatures.isEmpty()) {
            KoHsCrystalTweaks.LOGGER.warn(
                    "Force Crystal PvP Priority conflict scan skipped: its mixin signature was not found");
            return List.of();
        }

        Map<String, Conflict> conflicts = new LinkedHashMap<>();
        for (ModContainer container : loader.getAllMods()) {
            String modId = container.getMetadata().getId();
            if (isInfrastructureMod(modId) || KoHsCrystalTweaks.MOD_ID.equals(modId)) {
                continue;
            }

            try {
                Set<ConflictPoint> points = findPriorityOverlaps(
                        featureSignatures,
                        MixinConflictScanner.readMixinSignatures(container),
                        mentionsHotbarPriority(
                                container.getMetadata().getId(),
                                container.getMetadata().getName(),
                                container.getMetadata().getDescription()));
                if (!points.isEmpty()) {
                    conflicts.put(modId, new Conflict(
                            modId,
                            container.getMetadata().getName(),
                            container.getMetadata().getVersion().getFriendlyString(),
                            List.copyOf(points)));
                }
            } catch (RuntimeException exception) {
                KoHsCrystalTweaks.LOGGER.debug(
                        "Could not inspect Force Crystal PvP Priority overlaps from mod {}", modId, exception);
            }
        }

        return List.copyOf(conflicts.values());
    }

    static Set<ConflictPoint> findPriorityOverlaps(
            List<MixinConflictScanner.MixinSignature> featureSignatures,
            List<MixinConflictScanner.MixinSignature> candidateSignatures
    ) {
        return findPriorityOverlaps(featureSignatures, candidateSignatures, false);
    }

    private static Set<ConflictPoint> findPriorityOverlaps(
            List<MixinConflictScanner.MixinSignature> featureSignatures,
            List<MixinConflictScanner.MixinSignature> candidateSignatures,
            boolean metadataEvidence
    ) {
        Set<ConflictPoint> points = new LinkedHashSet<>();
        for (MixinConflictScanner.MixinSignature candidate : candidateSignatures) {
            for (String candidateTarget : candidate.targetClasses()) {
                if (isFeatureInternalTarget(candidateTarget)) {
                    Set<String> methods = candidate.targetMethods().isEmpty()
                            ? Set.of("<class>") : candidate.targetMethods();
                    for (String method : methods) {
                        addPoint(points, candidateTarget, method, candidate.mixinClass());
                    }
                    continue;
                }

                if (!metadataEvidence && !mentionsHotbarPriority(candidate.mixinClass())) {
                    continue;
                }
                for (MixinConflictScanner.MixinSignature feature : featureSignatures) {
                    if (!feature.targetClasses().contains(candidateTarget)) {
                        continue;
                    }
                    for (String method : candidate.targetMethods()) {
                        if (feature.targetMethods().contains(method)) {
                            addPoint(points, "net.minecraft.client.KeyMapping", "click",
                                    candidate.mixinClass());
                        }
                    }
                }
            }
        }
        return points;
    }

    private static void addPoint(Set<ConflictPoint> points, String targetClass, String targetMethod,
            String mixinClass) {
        if (points.size() < MAX_POINTS_PER_MOD) {
            points.add(new ConflictPoint(targetClass, targetMethod, mixinClass));
        }
    }

    private static boolean isInfrastructureMod(String modId) {
        return modId.equals("minecraft")
                || modId.equals("java")
                || modId.equals("fabricloader")
                || modId.equals("fabric-api")
                || modId.startsWith("fabric-");
    }

    static boolean isFeatureInternalTarget(String className) {
        return className.equals(FEATURE_CLASS)
                || className.startsWith(FEATURE_CLASS + "$")
                || className.equals(FEATURE_MIXIN_CLASS)
                || className.startsWith(FEATURE_MIXIN_CLASS + "$")
                || className.equals(FEATURE_ACCESSOR_CLASS)
                || className.startsWith(FEATURE_ACCESSOR_CLASS + "$");
    }

    private static boolean mentionsHotbarPriority(String... values) {
        for (String value : values) {
            if (value == null) {
                continue;
            }
            String normalized = value.toLowerCase(Locale.ROOT)
                    .replace("_", "")
                    .replace("-", "")
                    .replace(" ", "");
            if (normalized.contains("hotbar")
                    || normalized.contains("selectedslot")
                    || normalized.contains("inventory")
                    || normalized.contains("crystal")
                    || normalized.contains("obsidian")
                    || normalized.contains("priority")
                    || normalized.contains("quickselect")
                    || normalized.contains("slotswitch")
                    || normalized.contains("itemswitch")) {
                return true;
            }
        }
        return false;
    }

    private static String simpleName(String className) {
        int separator = className.lastIndexOf('.');
        return separator < 0 ? className : className.substring(separator + 1);
    }

    public record Conflict(String modId, String modName, String version, List<ConflictPoint> points) {
        public Conflict {
            points = List.copyOf(new ArrayList<>(points));
        }
    }

    public record ConflictPoint(String targetClass, String targetMethod, String mixinClass) {
    }
}
