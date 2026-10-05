package com.zymekoh.crystaltweaks.client;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * The icon another mod ships with, as its own fabric.mod.json names it, read once from its JAR and
 * kept as a texture for the settings screen: the optimizer window shows the detected optimizer by
 * its own picture. A mod without an icon, or one that cannot be read, simply has none.
 */
public final class ModIcons {
    /** A loaded icon and its size in pixels. */
    public record Icon(Identifier texture, int width, int height) {
    }

    private static final Map<String, Optional<Icon>> LOADED = new HashMap<>();

    private ModIcons() {
    }

    /** The mod's icon, loading it the first time. Call on the render thread. */
    public static Optional<Icon> of(String modId) {
        if (modId == null || modId.isBlank()) {
            return Optional.empty();
        }
        return LOADED.computeIfAbsent(modId, ModIcons::load);
    }

    private static Optional<Icon> load(String modId) {
        try {
            Optional<ModContainer> container = FabricLoader.getInstance().getModContainer(modId);
            if (container.isEmpty()) {
                return Optional.empty();
            }
            Optional<String> iconPath = container.get().getMetadata().getIconPath(128);
            if (iconPath.isEmpty()) {
                return Optional.empty();
            }
            Optional<Path> file = container.get().findPath(iconPath.get());
            if (file.isEmpty()) {
                return Optional.empty();
            }
            NativeImage image;
            try (InputStream input = Files.newInputStream(file.get())) {
                image = NativeImage.read(input);
            }
            int width = image.getWidth();
            int height = image.getHeight();
            String safe = modId.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_");
            Identifier texture = Identifier.fromNamespaceAndPath("crystal_tweaks", "mod_icon/" + safe);
            Minecraft.getInstance().getTextureManager().register(texture,
                    new DynamicTexture(() -> "Crystal Tweaks: icon of " + modId, image));
            return Optional.of(new Icon(texture, width, height));
        } catch (Exception | LinkageError exception) {
            return Optional.empty();
        }
    }
}
