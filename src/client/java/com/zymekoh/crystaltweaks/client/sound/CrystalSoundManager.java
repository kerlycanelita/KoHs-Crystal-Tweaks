package com.zymekoh.crystaltweaks.client.sound;

import com.mojang.blaze3d.audio.SoundBuffer;
import com.zymekoh.crystaltweaks.CrystalTweaksClient;
import com.zymekoh.crystaltweaks.client.CrystalVisualConfig;
import com.zymekoh.crystaltweaks.mixin.client.SoundManagerAccessor;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.concurrent.CompletionException;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.ConstantFloat;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.phys.AABB;
import org.lwjgl.BufferUtils;
import org.lwjgl.stb.STBVorbis;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

public final class CrystalSoundManager {
    private static final float MAX_DURATION = 5.0F;
    private static final long RECENT_CRYSTAL_NANOS = 750_000_000L;
    private static final double CRYSTAL_SOUND_RADIUS = 2.5D;
    private static final Identifier EXPLOSION_EVENT_ID =
            Identifier.withDefaultNamespace("entity.generic.explode");
    private static final Identifier RUNTIME_EVENT_ID = Identifier.fromNamespaceAndPath(
            CrystalTweaksClient.MOD_ID,
            "crystal_explosion");
    private static final Identifier RUNTIME_SOUND_ID = Identifier.fromNamespaceAndPath(
            CrystalTweaksClient.MOD_ID,
            "runtime/explosion");
    private static final Identifier RUNTIME_LOCATION = Sound.SOUND_LISTER.idToFile(RUNTIME_SOUND_ID);
    private static final String EXPLOSION_SUBTITLE = "subtitles.entity.generic.explode";
    private static final ArrayDeque<RecentCrystal> RECENT_CRYSTALS = new ArrayDeque<>();

    private static DecodedAudio decodedAudio;
    private static String loadedFileName = "";
    private static String lastError = "";
    private static float loadedDuration;
    private static boolean soundManagerReady;

    private CrystalSoundManager() {
    }

    public static void initialize() {
        reloadFromConfig();
    }

    public static void tick() {
        pruneRecentCrystals(System.nanoTime());
    }

    public static void resetTracking() {
        RECENT_CRYSTALS.clear();
    }

    public static void cleanup() {
        clearLoadedSound();
        RECENT_CRYSTALS.clear();
        soundManagerReady = false;
    }

    public static void onEntityUnloaded(Entity entity) {
        if (!(entity instanceof EndCrystal)) {
            return;
        }
        long now = System.nanoTime();
        pruneRecentCrystals(now);
        RECENT_CRYSTALS.addLast(new RecentCrystal(entity.getX(), entity.getY(), entity.getZ(), now));
    }

    public static void reloadFromConfig() {
        if (!CrystalVisualConfig.customSoundEnabled()
                || CrystalVisualConfig.customSoundFileName().isBlank()) {
            clearLoadedSound();
            applyRuntimeOverridesToCurrentManager();
            return;
        }

        Path path = CrystalVisualConfig.customSoundPath();
        if (path == null || Files.notExists(path)) {
            clearLoadedSound();
            lastError = "File not found";
            applyRuntimeOverridesToCurrentManager();
            return;
        }

        loadSound(path, CrystalVisualConfig.customSoundFileName());
        applyRuntimeOverridesToCurrentManager();
    }

    public static String importFile(Path sourceFile) {
        String name = sourceFile.getFileName().toString();
        String extension = extension(name).toLowerCase();
        if (!extension.equals("wav") && !extension.equals("ogg") && !extension.equals("mp3")) {
            return "Unsupported format. Use WAV, OGG, or MP3.";
        }

        try {
            Path directory = CrystalVisualConfig.soundsDirectory();
            Files.createDirectories(directory);
            Path destination = directory.resolve(name);
            Files.copy(sourceFile, destination, StandardCopyOption.REPLACE_EXISTING);

            String error = loadSound(destination, name);
            if (!error.isEmpty()) {
                return error;
            }

            CrystalVisualConfig.setCustomSoundFileName(name);
            CrystalVisualConfig.save();
            applyRuntimeOverridesToCurrentManager();
            return "";
        } catch (Exception exception) {
            CrystalTweaksClient.LOGGER.error("Failed to import custom crystal sound", exception);
            return "Import failed: " + exception.getMessage();
        }
    }

    public static void onSoundManagerApply(SoundManager soundManager) {
        soundManagerReady = true;
        applyConfiguredOverrides(soundManager);
    }

    public static SoundInstance replaceCrystalExplosion(SoundInstance original) {
        if (!CrystalVisualConfig.customSoundEnabled()
                || decodedAudio == null
                || !EXPLOSION_EVENT_ID.equals(original.getIdentifier())
                || !isCrystalExplosionAt(original.getX(), original.getY(), original.getZ())) {
            return original;
        }

        return new SimpleSoundInstance(
                RUNTIME_EVENT_ID,
                original.getSource(),
                4.0F * CrystalVisualConfig.soundVolume(),
                CrystalVisualConfig.soundSpeed(),
                RandomSource.create(),
                false,
                0,
                SoundInstance.Attenuation.LINEAR,
                original.getX(),
                original.getY(),
                original.getZ(),
                false);
    }

    public static void playPreviewExplosion() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.getSoundManager() == null) {
            return;
        }

        boolean useCustom = CrystalVisualConfig.customSoundEnabled() && decodedAudio != null;
        Identifier eventId = useCustom ? RUNTIME_EVENT_ID : EXPLOSION_EVENT_ID;
        float volume = useCustom ? CrystalVisualConfig.soundVolume() : 1.0F;
        float speed = useCustom ? CrystalVisualConfig.soundSpeed() : 1.0F;
        minecraft.getSoundManager().play(new SimpleSoundInstance(
                eventId,
                SoundSource.MASTER,
                volume,
                speed,
                RandomSource.create(),
                false,
                0,
                SoundInstance.Attenuation.NONE,
                0.0D,
                0.0D,
                0.0D,
                true));
    }

    public static boolean isRuntimeLocation(Identifier id) {
        return RUNTIME_LOCATION.equals(id);
    }

    public static SoundBuffer createStaticSoundFor(Identifier id) {
        if (!isRuntimeLocation(id) || decodedAudio == null) {
            throw new CompletionException(new IllegalStateException("Custom sound is not loaded"));
        }
        return new SoundBuffer(decodedAudio.copyPcm(), decodedAudio.format());
    }

    public static String loadedFileName() {
        return loadedFileName;
    }

    public static float loadedDuration() {
        return loadedDuration;
    }

    public static String lastError() {
        return lastError;
    }

    private static void applyRuntimeOverridesToCurrentManager() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!soundManagerReady || minecraft == null || minecraft.getSoundManager() == null) {
            return;
        }
        applyConfiguredOverrides(minecraft.getSoundManager());
        minecraft.getSoundManager().reload();
    }

    private static void applyConfiguredOverrides(SoundManager soundManager) {
        SoundManagerAccessor accessor = (SoundManagerAccessor) soundManager;
        Map<Identifier, WeighedSoundEvents> sounds = accessor.crystalTweaks$getSounds();
        sounds.remove(RUNTIME_EVENT_ID);
        if (!CrystalVisualConfig.customSoundEnabled() || decodedAudio == null) {
            return;
        }

        Sound sound = new Sound(
                RUNTIME_SOUND_ID,
                ConstantFloat.of(1.0F),
                ConstantFloat.of(1.0F),
                1,
                Sound.Type.FILE,
                false,
                true,
                16);
        WeighedSoundEvents event = new WeighedSoundEvents(RUNTIME_EVENT_ID, EXPLOSION_SUBTITLE);
        event.addSound(sound);
        sounds.put(RUNTIME_EVENT_ID, event);

        SoundEngine engine = accessor.crystalTweaks$getSoundEngine();
        sound.preloadIfRequired(engine);
    }

    private static boolean isCrystalExplosionAt(double x, double y, double z) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null) {
            AABB area = new AABB(
                    x - CRYSTAL_SOUND_RADIUS,
                    y - CRYSTAL_SOUND_RADIUS,
                    z - CRYSTAL_SOUND_RADIUS,
                    x + CRYSTAL_SOUND_RADIUS,
                    y + CRYSTAL_SOUND_RADIUS,
                    z + CRYSTAL_SOUND_RADIUS);
            if (!minecraft.level.getEntitiesOfClass(EndCrystal.class, area).isEmpty()) {
                return true;
            }
        }

        long now = System.nanoTime();
        pruneRecentCrystals(now);
        double radiusSquared = CRYSTAL_SOUND_RADIUS * CRYSTAL_SOUND_RADIUS;
        for (RecentCrystal crystal : RECENT_CRYSTALS) {
            double deltaX = crystal.x - x;
            double deltaY = crystal.y - y;
            double deltaZ = crystal.z - z;
            if (deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ <= radiusSquared) {
                return true;
            }
        }
        return false;
    }

    private static void pruneRecentCrystals(long now) {
        while (!RECENT_CRYSTALS.isEmpty()
                && now - RECENT_CRYSTALS.peekFirst().removedAtNanos > RECENT_CRYSTAL_NANOS) {
            RECENT_CRYSTALS.removeFirst();
        }
    }

    private static String loadSound(Path file, String name) {
        clearLoadedSound();
        try {
            DecodedAudio audio = switch (extension(name).toLowerCase()) {
                case "wav" -> decodeWav(file);
                case "ogg" -> decodeOgg(file);
                case "mp3" -> decodeMp3(file);
                default -> throw new IOException("Unsupported audio format");
            };
            if (audio.duration() > MAX_DURATION) {
                lastError = String.format("Too long: %.1fs (max %.0fs)", audio.duration(), MAX_DURATION);
                return lastError;
            }
            decodedAudio = audio;
            loadedFileName = name;
            loadedDuration = audio.duration();
            lastError = "";
            return "";
        } catch (Exception exception) {
            CrystalTweaksClient.LOGGER.error("Could not load custom crystal sound", exception);
            clearLoadedSound();
            lastError = "Load error: " + exception.getMessage();
            return lastError;
        }
    }

    private static void clearLoadedSound() {
        decodedAudio = null;
        loadedFileName = "";
        loadedDuration = 0.0F;
        lastError = "";
    }

    private static DecodedAudio decodeWav(Path file) throws Exception {
        try (AudioInputStream original = AudioSystem.getAudioInputStream(file.toFile())) {
            AudioFormat sourceFormat = original.getFormat();
            AudioFormat pcmFormat = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    sourceFormat.getSampleRate(),
                    16,
                    sourceFormat.getChannels(),
                    sourceFormat.getChannels() * 2,
                    sourceFormat.getSampleRate(),
                    false);

            AudioInputStream pcmStream = sourceFormat.getEncoding() != AudioFormat.Encoding.PCM_SIGNED
                    || sourceFormat.getSampleSizeInBits() != 16
                    || sourceFormat.isBigEndian()
                    ? AudioSystem.getAudioInputStream(pcmFormat, original)
                    : original;
            byte[] pcm = pcmStream.readAllBytes();
            if (pcmStream != original) {
                pcmStream.close();
            }

            int sampleRate = (int) pcmFormat.getSampleRate();
            byte[] mono = mixToMonoPcm16(pcm, pcmFormat.getChannels());
            float duration = mono.length / 2.0F / sampleRate;
            return new DecodedAudio(toDirectBuffer(mono), monoFormat(sampleRate), duration);
        }
    }

    private static DecodedAudio decodeOgg(Path file) throws Exception {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer channelsBuffer = stack.mallocInt(1);
            IntBuffer sampleRateBuffer = stack.mallocInt(1);
            ShortBuffer decoded = STBVorbis.stb_vorbis_decode_filename(
                    file.toAbsolutePath().toString(),
                    channelsBuffer,
                    sampleRateBuffer);
            if (decoded == null) {
                throw new IOException("Failed to decode OGG file");
            }
            try {
                int sampleRate = sampleRateBuffer.get(0);
                byte[] mono = mixShortBufferToMono(decoded, channelsBuffer.get(0));
                float duration = mono.length / 2.0F / sampleRate;
                return new DecodedAudio(toDirectBuffer(mono), monoFormat(sampleRate), duration);
            } finally {
                MemoryUtil.memFree(decoded);
            }
        }
    }

    private static DecodedAudio decodeMp3(Path file) throws Exception {
        javazoom.jl.decoder.Bitstream bitstream =
                new javazoom.jl.decoder.Bitstream(new FileInputStream(file.toFile()));
        javazoom.jl.decoder.Decoder decoder = new javazoom.jl.decoder.Decoder();
        ByteArrayOutputStream outputBytes = new ByteArrayOutputStream();
        int sampleRate = -1;
        int channels = -1;

        try {
            while (true) {
                javazoom.jl.decoder.Header header = bitstream.readFrame();
                if (header == null) {
                    break;
                }
                if (sampleRate == -1) {
                    sampleRate = header.frequency();
                    channels = header.mode() == javazoom.jl.decoder.Header.SINGLE_CHANNEL ? 1 : 2;
                }
                javazoom.jl.decoder.SampleBuffer decoded =
                        (javazoom.jl.decoder.SampleBuffer) decoder.decodeFrame(header, bitstream);
                short[] samples = decoded.getBuffer();
                int length = decoded.getBufferLength();
                if (channels == 2) {
                    for (int index = 0; index < length; index += 2) {
                        short left = samples[index];
                        short right = index + 1 < length ? samples[index + 1] : 0;
                        writeSample(outputBytes, (short) ((left + right) / 2));
                    }
                } else {
                    for (int index = 0; index < length; index++) {
                        writeSample(outputBytes, samples[index]);
                    }
                }
                bitstream.closeFrame();
            }
        } finally {
            bitstream.close();
        }

        byte[] pcm = outputBytes.toByteArray();
        if (sampleRate < 0 || pcm.length == 0) {
            throw new IOException("MP3 file is empty or unreadable");
        }
        return new DecodedAudio(
                toDirectBuffer(pcm),
                monoFormat(sampleRate),
                pcm.length / 2.0F / sampleRate);
    }

    private static void writeSample(ByteArrayOutputStream output, short sample) {
        output.write(sample & 0xFF);
        output.write(sample >> 8 & 0xFF);
    }

    private static byte[] mixToMonoPcm16(byte[] pcm, int channels) {
        if (channels <= 1) {
            return pcm;
        }
        byte[] mono = new byte[pcm.length / channels];
        int frameSize = channels * 2;
        int outputIndex = 0;
        for (int sourceIndex = 0; sourceIndex + frameSize <= pcm.length; sourceIndex += frameSize) {
            int mixed = 0;
            for (int channel = 0; channel < channels; channel++) {
                int base = sourceIndex + channel * 2;
                mixed += (short) ((pcm[base] & 0xFF) | pcm[base + 1] << 8);
            }
            short average = (short) (mixed / channels);
            mono[outputIndex++] = (byte) (average & 0xFF);
            mono[outputIndex++] = (byte) (average >> 8 & 0xFF);
        }
        return mono;
    }

    private static byte[] mixShortBufferToMono(ShortBuffer decoded, int channels) {
        if (channels <= 1) {
            byte[] pcm = new byte[decoded.remaining() * 2];
            int outputIndex = 0;
            while (decoded.hasRemaining()) {
                short sample = decoded.get();
                pcm[outputIndex++] = (byte) (sample & 0xFF);
                pcm[outputIndex++] = (byte) (sample >> 8 & 0xFF);
            }
            return pcm;
        }

        byte[] mono = new byte[decoded.remaining() / channels * 2];
        int outputIndex = 0;
        while (decoded.remaining() >= channels) {
            int mixed = 0;
            for (int channel = 0; channel < channels; channel++) {
                mixed += decoded.get();
            }
            short average = (short) (mixed / channels);
            mono[outputIndex++] = (byte) (average & 0xFF);
            mono[outputIndex++] = (byte) (average >> 8 & 0xFF);
        }
        return mono;
    }

    private static AudioFormat monoFormat(int sampleRate) {
        return new AudioFormat(sampleRate, 16, 1, true, false);
    }

    private static ByteBuffer toDirectBuffer(byte[] pcm) {
        ByteBuffer buffer = BufferUtils.createByteBuffer(pcm.length);
        buffer.put(pcm);
        buffer.flip();
        return buffer;
    }

    private static String extension(String name) {
        int separator = name.lastIndexOf('.');
        return separator < 0 ? "" : name.substring(separator + 1);
    }

    private record RecentCrystal(double x, double y, double z, long removedAtNanos) {
    }

    private record DecodedAudio(ByteBuffer pcmData, AudioFormat format, float duration) {
        private ByteBuffer copyPcm() {
            ByteBuffer copy = this.pcmData.duplicate();
            copy.position(0);
            return copy;
        }
    }
}
