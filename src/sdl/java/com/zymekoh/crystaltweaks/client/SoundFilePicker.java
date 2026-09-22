package com.zymekoh.crystaltweaks.client;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;

/**
 * Names an audio file without a native dialog, for the targets that no longer ship one.
 *
 * <p>Minecraft 26.3 moved its windowing from GLFW to SDL and dropped {@code lwjgl-tinyfd}, so the
 * operating system's file chooser is simply not on the classpath any more. Rather than lose the
 * feature, the player drops the file into the mod's own sounds folder and it is picked up from
 * there. Everything downstream is unchanged: the same import, the same five-second limit, the same
 * formats.</p>
 *
 * <p>The folder is created if it does not exist, so the path the message names is always a real
 * place the player can open.</p>
 */
public final class SoundFilePicker {
    private SoundFilePicker() {
    }

    public static void choose(boolean spanish, Consumer<Path> chosen, Consumer<String> status) {
        Thread picker = new Thread(() -> {
            Path directory = CrystalVisualConfig.soundsDirectory();
            Path found = null;
            String message;
            try {
                Files.createDirectories(directory);
                found = firstAudioFile(directory);
            } catch (Exception exception) {
                found = null;
            }
            if (found == null) {
                message = spanish
                        ? "Pon un .wav, .ogg o .mp3 en " + directory + " y vuelve a pulsar"
                        : "Put a .wav, .ogg or .mp3 in " + directory + " and press again";
            } else {
                message = "";
            }
            Path selected = found;
            String line = message;
            Minecraft.getInstance().execute(() -> {
                if (selected != null) {
                    chosen.accept(selected);
                } else {
                    status.accept(line);
                }
            });
        }, "Crystal Tweaks sound picker");
        picker.setDaemon(true);
        picker.start();
    }

    /** Newest audio file in the folder, so dropping a new one in replaces the previous choice. */
    private static Path firstAudioFile(Path directory) throws Exception {
        try (var entries = Files.list(directory)) {
            return entries
                    .filter(Files::isRegularFile)
                    .filter(path -> {
                        String name = path.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
                        return name.endsWith(".wav") || name.endsWith(".ogg") || name.endsWith(".mp3");
                    })
                    .max((left, right) -> {
                        try {
                            return Files.getLastModifiedTime(left).compareTo(Files.getLastModifiedTime(right));
                        } catch (Exception comparisonFailed) {
                            return 0;
                        }
                    })
                    .orElse(null);
        }
    }
}
