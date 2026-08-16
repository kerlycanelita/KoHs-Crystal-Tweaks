package com.zymekoh.crystaltweaks.client.compat;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zymekoh.crystaltweaks.CrystalTweaksClient;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.Person;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/**
 * Performs a read-only, local inspection of statically declared Fabric Mixin
 * configurations. A result is only produced when another mod targets the same
 * normalized Minecraft class and method as Crystal Tweaks.
 */
public final class ConflictScanner {
    private static final String MIXIN_DESCRIPTOR = "Lorg/spongepowered/asm/mixin/Mixin;";
    private static final String OVERWRITE_DESCRIPTOR = "Lorg/spongepowered/asm/mixin/Overwrite;";
    private static final int MAX_POINTS_PER_MOD = 12;
    private static final Set<String> INJECTOR_ANNOTATIONS = Set.of(
            "org.spongepowered.asm.mixin.injection.Inject",
            "org.spongepowered.asm.mixin.injection.Redirect",
            "org.spongepowered.asm.mixin.injection.ModifyArg",
            "org.spongepowered.asm.mixin.injection.ModifyArgs",
            "org.spongepowered.asm.mixin.injection.ModifyConstant",
            "org.spongepowered.asm.mixin.injection.ModifyVariable",
            "com.llamalad7.mixinextras.injector.ModifyExpressionValue",
            "com.llamalad7.mixinextras.injector.ModifyReceiver",
            "com.llamalad7.mixinextras.injector.ModifyReturnValue",
            "com.llamalad7.mixinextras.injector.WrapWithCondition",
            "com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation",
            "com.llamalad7.mixinextras.injector.v2.WrapWithCondition");
    private static final Set<String> RUNTIME_MODS = Set.of(
            "java",
            "minecraft",
            "fabricloader",
            "fabric-api",
            "mixinextras",
            "modmenu");

    private ConflictScanner() {
    }

    public static ConflictReport scan() {
        FabricLoader loader = FabricLoader.getInstance();
        Optional<ModContainer> ownContainer = loader.getModContainer(CrystalTweaksClient.MOD_ID);
        if (ownContainer.isEmpty()) {
            CrystalTweaksClient.LOGGER.warn("Conflict Monitor could not locate the Crystal Tweaks container");
            return new ConflictReport(List.of(), 0, 0, 1);
        }

        ScanResult ownResult;
        try {
            ownResult = scanContainer(ownContainer.get());
        } catch (Exception exception) {
            CrystalTweaksClient.LOGGER.warn("Conflict Monitor could not inspect Crystal Tweaks mixins", exception);
            return new ConflictReport(List.of(), 0, 0, 1);
        }

        Map<TargetMethod, List<MixinPoint>> ownByMethod = new HashMap<>();
        for (MixinPoint point : ownResult.points()) {
            ownByMethod.computeIfAbsent(point.targetMethod(), ignored -> new ArrayList<>()).add(point);
        }

        List<ModContainer> containers = loader.getAllMods().stream()
                .filter(container -> shouldInspect(container.getMetadata().getId()))
                .sorted(Comparator.comparing(
                        container -> container.getMetadata().getName().toLowerCase(Locale.ROOT)))
                .toList();
        List<ConflictEntry> conflicts = new ArrayList<>();
        int scannedMixins = 0;
        int failedMods = 0;

        for (ModContainer container : containers) {
            try {
                ScanResult result = scanContainer(container);
                scannedMixins += result.declaredMixins();
                List<ConflictPoint> matches = findMatches(result.points(), ownByMethod);
                if (!matches.isEmpty()) {
                    conflicts.add(toEntry(container, matches));
                }
            } catch (Exception exception) {
                failedMods++;
                CrystalTweaksClient.LOGGER.warn(
                        "Conflict Monitor could not inspect mod {}",
                        container.getMetadata().getId(),
                        exception);
            }
        }

        CrystalTweaksClient.LOGGER.info(
                "Conflict Monitor inspected {} mods and {} declared mixins; {} possible overlaps found",
                containers.size(),
                scannedMixins,
                conflicts.size());
        return new ConflictReport(List.copyOf(conflicts), containers.size(), scannedMixins, failedMods);
    }

    private static boolean shouldInspect(String modId) {
        return !CrystalTweaksClient.MOD_ID.equals(modId)
                && !RUNTIME_MODS.contains(modId)
                && !modId.startsWith("fabric-");
    }

    private static ScanResult scanContainer(ModContainer container) throws IOException {
        Optional<Path> manifestPath = container.findPath("fabric.mod.json");
        if (manifestPath.isEmpty()) {
            return new ScanResult(List.of(), 0);
        }

        JsonObject manifest;
        try (Reader reader = Files.newBufferedReader(manifestPath.get())) {
            manifest = JsonParser.parseReader(reader).getAsJsonObject();
        }

        List<String> configs = mixinConfigs(manifest.get("mixins"));
        List<MixinPoint> points = new ArrayList<>();
        int declaredMixins = 0;
        for (String configPath : configs) {
            Optional<Path> resolvedConfig = container.findPath(configPath);
            if (resolvedConfig.isEmpty()) {
                continue;
            }
            JsonObject config;
            try (Reader reader = Files.newBufferedReader(resolvedConfig.get())) {
                config = JsonParser.parseReader(reader).getAsJsonObject();
            }
            String packageName = string(config, "package");
            List<String> mixins = new ArrayList<>();
            addStrings(config.get("mixins"), mixins);
            addStrings(config.get("client"), mixins);
            declaredMixins += mixins.size();
            for (String mixinName : mixins) {
                String className = qualify(packageName, mixinName);
                Optional<Path> classPath = container.findPath(className.replace('.', '/') + ".class");
                if (classPath.isEmpty()) {
                    continue;
                }
                try (InputStream stream = Files.newInputStream(classPath.get())) {
                    new ClassReader(stream).accept(
                            new MixinClassVisitor(className, points),
                            ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                }
            }
        }
        return new ScanResult(List.copyOf(points), declaredMixins);
    }

    private static List<String> mixinConfigs(JsonElement element) {
        List<String> configs = new ArrayList<>();
        if (element == null || element.isJsonNull()) {
            return configs;
        }
        List<JsonElement> values = new ArrayList<>();
        if (element.isJsonArray()) {
            for (JsonElement value : element.getAsJsonArray()) {
                values.add(value);
            }
        } else {
            values.add(element);
        }
        for (JsonElement value : values) {
            if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
                configs.add(value.getAsString());
            } else if (value.isJsonObject()) {
                String config = string(value.getAsJsonObject(), "config");
                if (!config.isBlank()) {
                    configs.add(config);
                }
            }
        }
        return configs;
    }

    private static void addStrings(JsonElement element, List<String> destination) {
        if (element == null || !element.isJsonArray()) {
            return;
        }
        for (JsonElement value : element.getAsJsonArray()) {
            if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
                destination.add(value.getAsString());
            }
        }
    }

    private static String string(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
                ? value.getAsString()
                : "";
    }

    private static String qualify(String packageName, String className) {
        if (packageName.isBlank() || className.startsWith(packageName + ".")) {
            return className;
        }
        return packageName + "." + className;
    }

    private static List<ConflictPoint> findMatches(
            List<MixinPoint> foreignPoints,
            Map<TargetMethod, List<MixinPoint>> ownByMethod
    ) {
        Map<String, ConflictPoint> unique = new LinkedHashMap<>();
        for (MixinPoint foreign : foreignPoints) {
            List<MixinPoint> ownPoints = ownByMethod.get(foreign.targetMethod());
            if (ownPoints == null) {
                continue;
            }
            for (MixinPoint own : ownPoints) {
                if (!descriptorsOverlap(own.methodDescriptor(), foreign.methodDescriptor())) {
                    continue;
                }
                String key = foreign.targetClass() + '#' + foreign.methodName() + ':'
                        + foreign.mixinClass();
                unique.putIfAbsent(key, new ConflictPoint(
                        foreign.targetClass(),
                        displayMethod(own, foreign),
                        foreign.mixinClass(),
                        own.mixinClass(),
                        areaFor(foreign.targetClass(), foreign.methodName(), own.mixinClass())));
                if (unique.size() >= MAX_POINTS_PER_MOD) {
                    return List.copyOf(unique.values());
                }
            }
        }
        return List.copyOf(unique.values());
    }

    private static boolean descriptorsOverlap(String first, String second) {
        return first.isBlank() || second.isBlank() || first.equals(second);
    }

    private static String displayMethod(MixinPoint own, MixinPoint foreign) {
        String descriptor = !foreign.methodDescriptor().isBlank()
                ? foreign.methodDescriptor()
                : own.methodDescriptor();
        return foreign.methodName() + descriptor;
    }

    private static ConflictArea areaFor(String targetClass, String methodName, String ownMixinClass) {
        String lowerClass = targetClass.toLowerCase(Locale.ROOT);
        String lowerMethod = methodName.toLowerCase(Locale.ROOT);
        String lowerMixin = ownMixinClass.toLowerCase(Locale.ROOT);
        if (lowerClass.contains("connection")
                || lowerClass.contains("packet")
                || lowerMixin.contains("attackpacket")) {
            return ConflictArea.NETWORK_OBSERVER;
        }
        if (lowerClass.contains("sound")
                || lowerMethod.contains("sound")
                || lowerMethod.equals("play")
                || lowerMixin.contains("sound")) {
            return ConflictArea.SOUND;
        }
        if (lowerClass.contains("crystal")
                || lowerClass.contains("model")
                || lowerClass.contains("render")
                || lowerMixin.contains("crystalanimation")
                || lowerMixin.contains("model")) {
            return ConflictArea.CRYSTAL_RENDERING;
        }
        return ConflictArea.GENERAL;
    }

    private static ConflictEntry toEntry(ModContainer container, List<ConflictPoint> points) {
        String authors = container.getMetadata().getAuthors().stream()
                .map(Person::getName)
                .filter(name -> !name.isBlank())
                .collect(Collectors.joining(", "));
        if (authors.isBlank()) {
            authors = "Unknown";
        }
        return new ConflictEntry(
                container.getMetadata().getId(),
                container.getMetadata().getName(),
                container.getMetadata().getVersion().getFriendlyString(),
                authors,
                container.getMetadata().getIconPath(64).orElse(""),
                points);
    }

    private static String annotationClass(String descriptor) {
        return descriptor.length() > 2 && descriptor.charAt(0) == 'L'
                ? descriptor.substring(1, descriptor.length() - 1).replace('/', '.')
                : descriptor;
    }

    private static TargetMethod normalizeTargetMethod(String targetClass, String selector) {
        String normalizedClass = normalizeClass(targetClass);
        String value = selector.trim();
        if (value.startsWith("L")) {
            int ownerEnd = value.indexOf(';');
            if (ownerEnd >= 0 && ownerEnd + 1 < value.length()) {
                value = value.substring(ownerEnd + 1);
            }
        }
        int quantifier = value.indexOf('{');
        if (quantifier >= 0) {
            value = value.substring(0, quantifier);
        }
        int descriptorStart = value.indexOf('(');
        String name = descriptorStart >= 0 ? value.substring(0, descriptorStart) : value;
        String descriptor = descriptorStart >= 0 ? value.substring(descriptorStart) : "";
        int namespaceEnd = Math.max(name.lastIndexOf('.'), name.lastIndexOf('/'));
        if (namespaceEnd >= 0) {
            name = name.substring(namespaceEnd + 1);
        }
        return new TargetMethod(normalizedClass, name, descriptor);
    }

    private static String normalizeClass(String targetClass) {
        String value = targetClass.trim();
        if (value.startsWith("L") && value.endsWith(";")) {
            value = value.substring(1, value.length() - 1);
        }
        return value.replace('/', '.');
    }

    public record ConflictReport(
            List<ConflictEntry> conflicts,
            int scannedMods,
            int scannedMixins,
            int failedMods
    ) {
    }

    public record ConflictEntry(
            String modId,
            String modName,
            String version,
            String authors,
            String iconPath,
            List<ConflictPoint> points
    ) {
    }

    public record ConflictPoint(
            String targetClass,
            String targetMethod,
            String foreignMixinClass,
            String ownMixinClass,
            ConflictArea area
    ) {
    }

    public enum ConflictArea {
        NETWORK_OBSERVER,
        CRYSTAL_RENDERING,
        SOUND,
        GENERAL
    }

    private record ScanResult(List<MixinPoint> points, int declaredMixins) {
    }

    private record TargetMethod(String targetClass, String methodName, String methodDescriptor) {
    }

    private record MixinPoint(
            String targetClass,
            String methodName,
            String methodDescriptor,
            String mixinClass
    ) {
        private TargetMethod targetMethod() {
            return new TargetMethod(this.targetClass, this.methodName, "");
        }
    }

    private static final class MixinClassVisitor extends ClassVisitor {
        private final String mixinClass;
        private final List<MixinPoint> destination;
        private final Set<String> targets = new LinkedHashSet<>();
        private final List<TargetMethod> methods = new ArrayList<>();

        private MixinClassVisitor(String mixinClass, List<MixinPoint> destination) {
            super(Opcodes.ASM9);
            this.mixinClass = mixinClass;
            this.destination = destination;
        }

        @Override
        public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
            if (!MIXIN_DESCRIPTOR.equals(descriptor)) {
                return null;
            }
            return new AnnotationVisitor(Opcodes.ASM9) {
                @Override
                public void visit(String name, Object value) {
                    addTarget(name, value);
                }

                @Override
                public AnnotationVisitor visitArray(String name) {
                    if (!"value".equals(name) && !"targets".equals(name)) {
                        return null;
                    }
                    return new AnnotationVisitor(Opcodes.ASM9) {
                        @Override
                        public void visit(String ignored, Object value) {
                            addTarget(name, value);
                        }
                    };
                }

                private void addTarget(String name, Object value) {
                    if ("value".equals(name) && value instanceof Type type) {
                        targets.add(type.getClassName());
                    } else if ("targets".equals(name) && value instanceof String string) {
                        targets.add(normalizeClass(string));
                    }
                }
            };
        }

        @Override
        public MethodVisitor visitMethod(
                int access,
                String name,
                String descriptor,
                String signature,
                String[] exceptions
        ) {
            return new MethodVisitor(Opcodes.ASM9) {
                @Override
                public AnnotationVisitor visitAnnotation(String annotationDescriptor, boolean visible) {
                    if (OVERWRITE_DESCRIPTOR.equals(annotationDescriptor)) {
                        methods.add(new TargetMethod("", name, descriptor));
                        return null;
                    }
                    if (!INJECTOR_ANNOTATIONS.contains(annotationClass(annotationDescriptor))) {
                        return null;
                    }
                    return new AnnotationVisitor(Opcodes.ASM9) {
                        @Override
                        public void visit(String property, Object value) {
                            if ("method".equals(property) && value instanceof String selector) {
                                methods.add(normalizeTargetMethod("", selector));
                            }
                        }

                        @Override
                        public AnnotationVisitor visitArray(String property) {
                            if (!"method".equals(property)) {
                                return null;
                            }
                            return new AnnotationVisitor(Opcodes.ASM9) {
                                @Override
                                public void visit(String ignored, Object value) {
                                    if (value instanceof String selector) {
                                        methods.add(normalizeTargetMethod("", selector));
                                    }
                                }
                            };
                        }
                    };
                }
            };
        }

        @Override
        public void visitEnd() {
            for (String target : targets) {
                String normalizedTarget = normalizeClass(target);
                for (TargetMethod method : methods) {
                    if (!normalizedTarget.isBlank() && !method.methodName().isBlank()) {
                        destination.add(new MixinPoint(
                                normalizedTarget,
                                method.methodName(),
                                method.methodDescriptor(),
                                this.mixinClass));
                    }
                }
            }
        }
    }
}
