package dev.zymekoh.kohscrystaltweaks.compat;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.zymekoh.kohscrystaltweaks.KoHsCrystalTweaks;
import dev.zymekoh.kohscrystaltweaks.compat.IncompatibilityManager.Conflict;
import dev.zymekoh.kohscrystaltweaks.compat.IncompatibilityManager.ConflictPoint;
import dev.zymekoh.kohscrystaltweaks.compat.IncompatibilityManager.ConflictType;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

final class MixinConflictScanner {
    private static final String KOHS_PACKAGE = "dev.zymekoh.kohscrystaltweaks.";
    private static final int MAX_POINTS_PER_MOD = 8;

    private static final Set<String> CRITICAL_KOHS_MIXINS = Set.of(
            "ClientConnectionMixin",
            "ClientPlayerInteractionManagerLocalCrystalMixin",
            "EndCrystalEntityModelAnimationMixin",
            "EndCrystalEntityRendererSeamlessMixin",
            "KeyboardOrderedCrystalInputMixin",
            "MinecraftClientPassThroughLocalCrystalMixin",
            "MouseOrderedCrystalInputMixin",
            "SafeCrystalMixin"
    );

    private MixinConflictScanner() {
    }

    static List<Conflict> scan(FabricLoader loader, ModContainer ownContainer, Set<String> knownConflictIds) {
        List<MixinSignature> ownSignatures = readMixinSignatures(ownContainer).stream()
                .filter(signature -> CRITICAL_KOHS_MIXINS.contains(simpleName(signature.mixinClass())))
                .toList();

        if (ownSignatures.isEmpty()) {
            KoHsCrystalTweaks.LOGGER.warn("No critical KoHs mixin signatures were found; generic overlap scanning was skipped");
            return List.of();
        }

        Map<String, MutableConflict> conflicts = new LinkedHashMap<>();
        for (ModContainer container : loader.getAllMods()) {
            String modId = container.getMetadata().getId();
            if (modId.equals(KoHsCrystalTweaks.MOD_ID) || knownConflictIds.contains(modId)) {
                continue;
            }

            try {
                List<MixinSignature> candidateSignatures = readMixinSignatures(container);
                inspectCandidate(container, candidateSignatures, ownSignatures, conflicts);
            } catch (RuntimeException exception) {
                KoHsCrystalTweaks.LOGGER.debug("Could not inspect mixins from mod {}", modId, exception);
            }
        }

        return conflicts.values().stream().map(MutableConflict::toConflict).toList();
    }

    private static void inspectCandidate(
            ModContainer container,
            List<MixinSignature> candidates,
            List<MixinSignature> ownSignatures,
            Map<String, MutableConflict> conflicts
    ) {
        boolean metadataMentionsCrystals = mentionsCrystals(
                container.getMetadata().getId(),
                container.getMetadata().getName(),
                container.getMetadata().getDescription());

        for (MixinSignature candidate : candidates) {
            for (String target : candidate.targetClasses()) {
                if (target.startsWith(KOHS_PACKAGE)) {
                    Set<String> methods = candidate.targetMethods().isEmpty()
                            ? Set.of("<class>") : candidate.targetMethods();
                    for (String method : methods) {
                        addConflict(conflicts, container, ConflictType.DIRECT_KOHS_MUTATION,
                                new ConflictPoint(displayClass(target), method, candidate.mixinClass()));
                    }
                    continue;
                }

            }
        }

        for (ConflictPoint point : findCriticalOverlaps(ownSignatures, candidates, metadataMentionsCrystals)) {
            addConflict(conflicts, container, ConflictType.MIXIN_OVERLAP, point);
        }
    }

    static Set<ConflictPoint> findCriticalOverlaps(
            List<MixinSignature> ownSignatures,
            List<MixinSignature> candidateSignatures,
            boolean metadataMentionsCrystals
    ) {
        Set<ConflictPoint> overlaps = new LinkedHashSet<>();
        for (MixinSignature candidate : candidateSignatures) {
            for (String target : candidate.targetClasses()) {
                boolean crystalRelated = metadataMentionsCrystals
                        || mentionsCrystals(candidate.mixinClass(), target);
                if (!crystalRelated || candidate.targetMethods().isEmpty()) {
                    continue;
                }
                for (MixinSignature own : ownSignatures) {
                    if (!own.targetClasses().contains(target)) {
                        continue;
                    }
                    for (String method : candidate.targetMethods()) {
                        if (own.targetMethods().contains(method)) {
                            overlaps.add(new ConflictPoint(displayClass(target), method, candidate.mixinClass()));
                        }
                    }
                }
            }
        }
        return overlaps;
    }

    private static void addConflict(
            Map<String, MutableConflict> conflicts,
            ModContainer container,
            ConflictType type,
            ConflictPoint point
    ) {
        MutableConflict conflict = conflicts.computeIfAbsent(container.getMetadata().getId(), ignored ->
                new MutableConflict(
                        container.getMetadata().getId(),
                        container.getMetadata().getName(),
                        container.getMetadata().getVersion().getFriendlyString()));
        conflict.type = conflict.type == null || type.ordinal() > conflict.type.ordinal() ? type : conflict.type;
        if (conflict.points.size() < MAX_POINTS_PER_MOD) {
            conflict.points.add(point);
        }
    }

    static List<MixinSignature> readMixinSignatures(ModContainer container) {
        List<String> mixinConfigs = readMixinConfigs(container);
        List<MixinSignature> signatures = new ArrayList<>();

        for (String configPath : mixinConfigs) {
            try {
                Optional<Path> path = container.findPath(stripLeadingSlash(configPath));
                if (path.isEmpty()) {
                    continue;
                }
                JsonObject config = readJson(path.get());
                String packageName = getString(config, "package").orElse("");
                for (String mixinName : readMixinClassNames(config)) {
                    String className = mixinName.contains(".") || packageName.isBlank()
                            ? mixinName : packageName + "." + mixinName;
                    readClass(container, className).ifPresent(bytes ->
                            signatures.addAll(inspectMixinClass(className, bytes)));
                }
            } catch (IOException | RuntimeException exception) {
                KoHsCrystalTweaks.LOGGER.debug("Could not read mixin config {} from {}",
                        configPath, container.getMetadata().getId(), exception);
            }
        }
        return signatures;
    }

    private static List<String> readMixinConfigs(ModContainer container) {
        Optional<Path> metadataPath = container.findPath("fabric.mod.json");
        if (metadataPath.isEmpty()) {
            return List.of();
        }

        try {
            JsonObject metadata = readJson(metadataPath.get());
            JsonElement mixins = metadata.get("mixins");
            if (mixins == null || !mixins.isJsonArray()) {
                return List.of();
            }

            List<String> configs = new ArrayList<>();
            for (JsonElement element : mixins.getAsJsonArray()) {
                if (element.isJsonPrimitive()) {
                    configs.add(element.getAsString());
                } else if (element.isJsonObject()) {
                    JsonObject object = element.getAsJsonObject();
                    String environment = getString(object, "environment").orElse("*");
                    if (!environment.equalsIgnoreCase("server")) {
                        getString(object, "config").ifPresent(configs::add);
                    }
                }
            }
            return configs;
        } catch (IOException | RuntimeException exception) {
            KoHsCrystalTweaks.LOGGER.debug("Could not read fabric.mod.json from {}",
                    container.getMetadata().getId(), exception);
            return List.of();
        }
    }

    private static Collection<String> readMixinClassNames(JsonObject config) {
        Set<String> classNames = new LinkedHashSet<>();
        addStrings(config.getAsJsonArray("mixins"), classNames);
        addStrings(config.getAsJsonArray("client"), classNames);
        return classNames;
    }

    private static void addStrings(JsonArray array, Collection<String> destination) {
        if (array == null) {
            return;
        }
        for (JsonElement element : array) {
            if (element.isJsonPrimitive()) {
                destination.add(element.getAsString());
            }
        }
    }

    private static Optional<byte[]> readClass(ModContainer container, String className) {
        String resourcePath = className.replace('.', '/') + ".class";
        Optional<Path> path = container.findPath(resourcePath);
        if (path.isPresent()) {
            try {
                return Optional.of(Files.readAllBytes(path.get()));
            } catch (IOException exception) {
                KoHsCrystalTweaks.LOGGER.debug("Could not read {} from {}", resourcePath,
                        container.getMetadata().getId(), exception);
            }
        }

        try (InputStream stream = MixinConflictScanner.class.getClassLoader().getResourceAsStream(resourcePath)) {
            return stream == null ? Optional.empty() : Optional.of(stream.readAllBytes());
        } catch (IOException exception) {
            return Optional.empty();
        }
    }

    static List<MixinSignature> inspectMixinClass(String mixinClass, byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

        AnnotationNode mixin = findAnnotation(node.visibleAnnotations, "Lorg/spongepowered/asm/mixin/Mixin;");
        if (mixin == null) {
            mixin = findAnnotation(node.invisibleAnnotations, "Lorg/spongepowered/asm/mixin/Mixin;");
        }
        if (mixin == null) {
            return List.of();
        }

        Set<String> targets = readMixinTargets(mixin);
        if (targets.isEmpty()) {
            return List.of();
        }

        Set<String> methods = new LinkedHashSet<>();
        for (MethodNode method : node.methods) {
            for (AnnotationNode annotation : allAnnotations(method)) {
                if (isInjectionAnnotation(annotation.desc)) {
                    readInjectionSelectors(annotation).stream()
                            .map(MixinConflictScanner::normalizeMethodSelector)
                            .filter(value -> !value.isBlank())
                            .forEach(methods::add);
                } else if (annotation.desc.equals("Lorg/spongepowered/asm/mixin/Overwrite;")) {
                    methods.add(method.name);
                }
            }
        }

        return List.of(new MixinSignature(Set.copyOf(targets), Set.copyOf(methods), mixinClass));
    }

    private static Set<String> readMixinTargets(AnnotationNode annotation) {
        Set<String> targets = new LinkedHashSet<>();
        Object classTargets = annotationValue(annotation, "value");
        if (classTargets instanceof List<?> list) {
            for (Object value : list) {
                if (value instanceof Type type) {
                    targets.add(type.getClassName());
                }
            }
        } else if (classTargets instanceof Type type) {
            targets.add(type.getClassName());
        }

        for (String target : readAnnotationStrings(annotation, "targets")) {
            targets.add(target.replace('/', '.'));
        }
        return targets;
    }

    private static List<AnnotationNode> allAnnotations(MethodNode method) {
        List<AnnotationNode> annotations = new ArrayList<>();
        if (method.visibleAnnotations != null) {
            annotations.addAll(method.visibleAnnotations);
        }
        if (method.invisibleAnnotations != null) {
            annotations.addAll(method.invisibleAnnotations);
        }
        return annotations;
    }

    private static AnnotationNode findAnnotation(List<AnnotationNode> annotations, String descriptor) {
        if (annotations == null) {
            return null;
        }
        for (AnnotationNode annotation : annotations) {
            if (annotation.desc.equals(descriptor)) {
                return annotation;
            }
        }
        return null;
    }

    private static List<String> readAnnotationStrings(AnnotationNode annotation, String key) {
        Object value = annotationValue(annotation, key);
        if (value instanceof String string) {
            return List.of(string);
        }
        if (value instanceof List<?> list) {
            List<String> strings = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof String string) {
                    strings.add(string);
                }
            }
            return strings;
        }
        return List.of();
    }

    private static List<String> readInjectionSelectors(AnnotationNode annotation) {
        List<String> selectors = new ArrayList<>(readAnnotationStrings(annotation, "method"));
        collectNestedAnnotationStrings(annotationValue(annotation, "target"), "value", selectors);
        return selectors;
    }

    private static void collectNestedAnnotationStrings(Object value, String key, Collection<String> destination) {
        if (value instanceof AnnotationNode nested) {
            destination.addAll(readAnnotationStrings(nested, key));
        } else if (value instanceof List<?> list) {
            for (Object item : list) {
                collectNestedAnnotationStrings(item, key, destination);
            }
        }
    }

    private static boolean isInjectionAnnotation(String descriptor) {
        return descriptor.startsWith("Lorg/spongepowered/asm/mixin/injection/")
                || descriptor.startsWith("Lcom/llamalad7/mixinextras/injector/");
    }

    private static Object annotationValue(AnnotationNode annotation, String key) {
        if (annotation.values == null) {
            return null;
        }
        for (int index = 0; index + 1 < annotation.values.size(); index += 2) {
            if (key.equals(annotation.values.get(index))) {
                return annotation.values.get(index + 1);
            }
        }
        return null;
    }

    static String normalizeMethodSelector(String selector) {
        String normalized = selector.trim().replace('/', '.');
        int ownerEnd = normalized.indexOf(';');
        if (normalized.startsWith("L") && ownerEnd >= 0) {
            normalized = normalized.substring(ownerEnd + 1);
        }
        int doubleColon = normalized.lastIndexOf("::");
        if (doubleColon >= 0) {
            normalized = normalized.substring(doubleColon + 2);
        }
        int descriptor = normalized.indexOf('(');
        if (descriptor >= 0) {
            normalized = normalized.substring(0, descriptor);
        }
        int quantifier = normalized.indexOf('{');
        if (quantifier >= 0) {
            normalized = normalized.substring(0, quantifier);
        }
        return normalized;
    }

    private static JsonObject readJson(Path path) throws IOException {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private static Optional<String> getString(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element != null && element.isJsonPrimitive()
                ? Optional.of(element.getAsString()) : Optional.empty();
    }

    private static boolean mentionsCrystals(String... values) {
        for (String value : values) {
            if (value != null) {
                String lower = value.toLowerCase(Locale.ROOT);
                if (lower.contains("crystal") || lower.contains("end_crystal") || lower.contains("endcrystal")) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String simpleName(String className) {
        int separator = className.lastIndexOf('.');
        return separator < 0 ? className : className.substring(separator + 1);
    }

    private static String displayClass(String className) {
        int separator = className.lastIndexOf('.');
        return separator < 0 ? className : className.substring(separator + 1);
    }

    private static String stripLeadingSlash(String path) {
        return path.startsWith("/") ? path.substring(1) : path;
    }

    record MixinSignature(Set<String> targetClasses, Set<String> targetMethods, String mixinClass) {
    }

    private static final class MutableConflict {
        private final String modId;
        private final String modName;
        private final String version;
        private final Set<ConflictPoint> points = new LinkedHashSet<>();
        private ConflictType type;

        private MutableConflict(String modId, String modName, String version) {
            this.modId = modId;
            this.modName = modName;
            this.version = version;
        }

        private Conflict toConflict() {
            return new Conflict(modId, modName, version, type, List.copyOf(points));
        }
    }
}
