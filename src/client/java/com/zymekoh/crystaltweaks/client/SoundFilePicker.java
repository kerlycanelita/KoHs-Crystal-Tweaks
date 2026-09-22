package com.zymekoh.crystaltweaks.client;

import java.nio.file.Path;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

/**
 * Asks the operating system for an audio file, using the native dialog LWJGL ships with the game.
 *
 * <p>Minecraft 26.3 moved its windowing from GLFW to SDL and dropped {@code lwjgl-tinyfd} along the
 * way, so that target compiles the copy of this class under {@code src/sdl} instead. Both versions
 * offer the same two callbacks and deliver both on the client thread; only the way the player names
 * a file differs.</p>
 */
public final class SoundFilePicker {
    private SoundFilePicker() {
    }

    /**
     * @param chosen receives the selected file on the client thread
     * @param status receives a line for the settings screen when no file could be chosen
     */
    public static void choose(boolean spanish, Consumer<Path> chosen, Consumer<String> status) {
        Thread picker = new Thread(() -> {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                PointerBuffer patterns = stack.mallocPointer(3);
                patterns.put(stack.UTF8("*.wav"));
                patterns.put(stack.UTF8("*.ogg"));
                patterns.put(stack.UTF8("*.mp3"));
                patterns.flip();
                String selected = TinyFileDialogs.tinyfd_openFileDialog(
                        spanish ? "Seleccionar sonido" : "Select sound",
                        null,
                        patterns,
                        "Audio (*.wav, *.ogg, *.mp3)",
                        false);
                if (selected != null) {
                    Minecraft.getInstance().execute(() -> chosen.accept(Path.of(selected)));
                }
            } catch (RuntimeException exception) {
                Minecraft.getInstance().execute(() -> status.accept(spanish
                        ? "No se pudo abrir el selector"
                        : "Could not open file picker"));
            }
        }, "Crystal Tweaks sound picker");
        picker.setDaemon(true);
        picker.start();
    }
}
