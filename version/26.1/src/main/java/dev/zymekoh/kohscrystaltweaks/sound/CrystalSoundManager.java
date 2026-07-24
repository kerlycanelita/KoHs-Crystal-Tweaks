package dev.zymekoh.kohscrystaltweaks.sound;

import dev.zymekoh.kohscrystaltweaks.KoHsCrystalTweaks;
import dev.zymekoh.kohscrystaltweaks.config.KoHsCrystalTweaksConfig;
import dev.zymekoh.kohscrystaltweaks.core.CrystalPredictor;
import dev.zymekoh.kohscrystaltweaks.mixin.SoundManagerAccessor;
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
import com.mojang.blaze3d.audio.SoundBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.Identifier;
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
    private static final float MAX_DURATION = 5.0f;
    private static final long RECENT_CRYSTAL_NANOS = 750_000_000L;
    private static final double CRYSTAL_SOUND_RADIUS = 2.5;
    private static final Identifier EXPLOSION_EVENT_ID = Identifier.withDefaultNamespace("entity.generic.explode");
    private static final String EXPLOSION_SUBTITLE = "subtitles.entity.generic.explode";
    private static final Identifier RUNTIME_EXPLOSION_EVENT_ID =
            Identifier.fromNamespaceAndPath(KoHsCrystalTweaks.MOD_ID, "crystal_explosion");
    private static final Identifier RUNTIME_EXPLOSION_SOUND_ID =
            Identifier.fromNamespaceAndPath(KoHsCrystalTweaks.MOD_ID, "runtime/explosion");
    private static final Identifier RUNTIME_EXPLOSION_LOCATION =
            Sound.SOUND_LISTER.idToFile(RUNTIME_EXPLOSION_SOUND_ID);

    private static DecodedAudio decodedAudio;
    private static String loadedFileName = "";
    private static String lastError = "";
    private static float loadedDuration = 0f;
    private static final ArrayDeque<RecentCrystal> RECENT_CRYSTALS = new ArrayDeque<>();
    private static boolean soundManagerReady;

    private CrystalSoundManager() {}

    public static void init() {
        reloadFromConfig();
    }

    public static void tick() {
        pruneRecentCrystals(System.nanoTime());
    }

    public static void cleanup() {
        decodedAudio = null;
        loadedFileName = "";
        loadedDuration = 0f;
        lastError = "";
        RECENT_CRYSTALS.clear();
        soundManagerReady = false;
    }

    public static void resetTracking() {
        RECENT_CRYSTALS.clear();
    }

    public static void onEntityUnloaded(Entity entity) {
        if (!(entity instanceof EndCrystal) || CrystalPredictor.isLocalCrystalEntity(entity)) {
            return;
        }
        long now = System.nanoTime();
        pruneRecentCrystals(now);
        RECENT_CRYSTALS.addLast(new RecentCrystal(entity.getX(), entity.getY(), entity.getZ(), now));
    }

    public static void reloadFromConfig() {
        KoHsCrystalTweaksConfig cfg = KoHsCrystalTweaksConfig.get();
        if (!cfg.customSoundEnabled || cfg.customSoundFileName.isEmpty()) {
            clearLoadedSound();
            applyRuntimeOverridesToCurrentManager();
            return;
        }

        Path path = KoHsCrystalTweaksConfig.getCustomSoundPath();
        if (path == null || !Files.exists(path)) {
            clearLoadedSound();
            lastError = "File not found";
            applyRuntimeOverridesToCurrentManager();
            return;
        }

        loadSound(path, cfg.customSoundFileName);
        applyRuntimeOverridesToCurrentManager();
    }

    public static String importFile(Path sourceFile) {
        String name = sourceFile.getFileName().toString();
        String ext = getExtension(name).toLowerCase();

        if (!ext.equals("wav") && !ext.equals("ogg") && !ext.equals("mp3")) {
            return "Unsupported format. Use WAV, OGG, or MP3.";
        }

        try {
            Path dir = KoHsCrystalTweaksConfig.getSoundsDir();
            Files.createDirectories(dir);
            Path dest = dir.resolve(name);
            Files.copy(sourceFile, dest, StandardCopyOption.REPLACE_EXISTING);

            String err = loadSound(dest, name);
            if (!err.isEmpty()) {
                return err;
            }

            KoHsCrystalTweaksConfig.get().customSoundFileName = name;
            KoHsCrystalTweaksConfig.save();
            applyRuntimeOverridesToCurrentManager();
            return "";
        } catch (Exception e) {
            KoHsCrystalTweaks.LOGGER.error("[{}] Failed to import sound: {}", KoHsCrystalTweaks.MOD_ID, e.toString());
            return "Import failed: " + e.getMessage();
        }
    }

    public static void onSoundManagerApply(SoundManager soundManager) {
        soundManagerReady = true;
        applyConfiguredOverrides(soundManager);
    }

    public static SoundInstance replaceCrystalExplosion(SoundInstance original) {
        KoHsCrystalTweaksConfig cfg = KoHsCrystalTweaksConfig.get();
        if (!cfg.customSoundEnabled
                || decodedAudio == null
                || !EXPLOSION_EVENT_ID.equals(original.getIdentifier())
                || !isCrystalExplosionAt(original.getX(), original.getY(), original.getZ())) {
            return original;
        }

        return new SimpleSoundInstance(
                RUNTIME_EXPLOSION_EVENT_ID,
                original.getSource(),
                4.0F * cfg.soundVolume,
                cfg.soundSpeed,
                RandomSource.create(),
                false,
                0,
                SoundInstance.Attenuation.LINEAR,
                original.getX(),
                original.getY(),
                original.getZ(),
                false);
    }

    public static boolean isRuntimeLocation(Identifier id) {
        return id.equals(RUNTIME_EXPLOSION_LOCATION);
    }

    public static SoundBuffer createStaticSoundFor(Identifier id) {
        if (!isRuntimeLocation(id) || decodedAudio == null) {
            throw new CompletionException(new IllegalStateException("Custom sound is not loaded"));
        }

        ByteBuffer sample = decodedAudio.copyPcm();
        return new SoundBuffer(sample, decodedAudio.format());
    }

    public static String getLoadedFileName() {
        return loadedFileName;
    }

    public static float getLoadedDuration() {
        return loadedDuration;
    }

    public static String getLastError() {
        return lastError;
    }

    private static void applyRuntimeOverridesToCurrentManager() {
        Minecraft client = Minecraft.getInstance();
        if (client == null || !soundManagerReady) {
            return;
        }

        SoundManager soundManager = client.getSoundManager();
        if (soundManager == null) {
            return;
        }

        applyConfiguredOverrides(soundManager);
        soundManager.reload();
    }

    private static void applyConfiguredOverrides(SoundManager soundManager) {
        Map<Identifier, WeighedSoundEvents> sounds = ((SoundManagerAccessor) soundManager).kct$getSounds();
        sounds.remove(RUNTIME_EXPLOSION_EVENT_ID);

        KoHsCrystalTweaksConfig cfg = KoHsCrystalTweaksConfig.get();
        if (!cfg.customSoundEnabled || decodedAudio == null) {
            return;
        }

        Sound explosionSound = createRuntimeSound(RUNTIME_EXPLOSION_SOUND_ID);
        WeighedSoundEvents explosionSet = new WeighedSoundEvents(RUNTIME_EXPLOSION_EVENT_ID, EXPLOSION_SUBTITLE);
        explosionSet.addSound(explosionSound);
        sounds.put(RUNTIME_EXPLOSION_EVENT_ID, explosionSet);

        SoundEngine soundSystem = ((SoundManagerAccessor) soundManager).kct$getSoundSystem();
        explosionSound.preloadIfRequired(soundSystem);
    }

    private static Sound createRuntimeSound(Identifier id) {
        return new Sound(
                id,
                ConstantFloat.of(1.0F),
                ConstantFloat.of(1.0F),
                1,
                Sound.Type.FILE,
                false,
                true,
                16);
    }

    private static boolean isCrystalExplosionAt(double x, double y, double z) {
        Minecraft client = Minecraft.getInstance();
        if (client.level != null) {
            AABB area = new AABB(
                    x - CRYSTAL_SOUND_RADIUS,
                    y - CRYSTAL_SOUND_RADIUS,
                    z - CRYSTAL_SOUND_RADIUS,
                    x + CRYSTAL_SOUND_RADIUS,
                    y + CRYSTAL_SOUND_RADIUS,
                    z + CRYSTAL_SOUND_RADIUS);
            if (!client.level.getEntitiesOfClass(
                    EndCrystal.class,
                    area,
                    crystal -> !CrystalPredictor.isLocalCrystalEntity(crystal)).isEmpty()) {
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
            String ext = getExtension(name).toLowerCase();
            DecodedAudio audio = switch (ext) {
                case "wav" -> decodeWav(file);
                case "ogg" -> decodeOgg(file);
                case "mp3" -> decodeMp3(file);
                default -> throw new IOException("Unsupported format: " + ext);
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
        } catch (Exception e) {
            KoHsCrystalTweaks.LOGGER.error("[{}] Sound load error: {}", KoHsCrystalTweaks.MOD_ID, e.toString());
            clearLoadedSound();
            lastError = "Load error: " + e.getMessage();
            return lastError;
        }
    }

    private static void clearLoadedSound() {
        decodedAudio = null;
        loadedFileName = "";
        loadedDuration = 0f;
        lastError = "";
    }

    private static DecodedAudio decodeWav(Path file) throws Exception {
        AudioInputStream original = AudioSystem.getAudioInputStream(file.toFile());
        AudioFormat srcFmt = original.getFormat();

        AudioFormat pcmFmt = new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                srcFmt.getSampleRate(),
                16,
                srcFmt.getChannels(),
                srcFmt.getChannels() * 2,
                srcFmt.getSampleRate(),
                false);

        AudioInputStream pcmStream;
        if (srcFmt.getEncoding() != AudioFormat.Encoding.PCM_SIGNED
                || srcFmt.getSampleSizeInBits() != 16
                || srcFmt.isBigEndian()) {
            pcmStream = AudioSystem.getAudioInputStream(pcmFmt, original);
        } else {
            pcmStream = original;
        }

        byte[] pcm = pcmStream.readAllBytes();
        pcmStream.close();
        original.close();

        int channels = pcmFmt.getChannels();
        int sampleRate = (int) pcmFmt.getSampleRate();
        byte[] mono = mixToMonoPcm16(pcm, channels);
        float duration = (mono.length / 2.0f) / sampleRate;
        return new DecodedAudio(toDirectBuffer(mono), monoFormat(sampleRate), duration);
    }

    private static DecodedAudio decodeOgg(Path file) throws Exception {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer channelsBuf = stack.mallocInt(1);
            IntBuffer sampleRateBuf = stack.mallocInt(1);

            ShortBuffer decoded = STBVorbis.stb_vorbis_decode_filename(
                    file.toAbsolutePath().toString(), channelsBuf, sampleRateBuf);

            if (decoded == null) {
                throw new IOException("Failed to decode OGG file");
            }

            try {
                int channels = channelsBuf.get(0);
                int sampleRate = sampleRateBuf.get(0);
                byte[] mono = mixShortBufferToMono(decoded, channels);
                float duration = (mono.length / 2.0f) / sampleRate;
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

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int sampleRate = -1;
        int channels = -1;

        while (true) {
            javazoom.jl.decoder.Header header = bitstream.readFrame();
            if (header == null) {
                break;
            }

            if (sampleRate == -1) {
                sampleRate = header.frequency();
                channels = header.mode() == javazoom.jl.decoder.Header.SINGLE_CHANNEL ? 1 : 2;
            }

            javazoom.jl.decoder.SampleBuffer output =
                    (javazoom.jl.decoder.SampleBuffer) decoder.decodeFrame(header, bitstream);
            short[] samples = output.getBuffer();
            int len = output.getBufferLength();

            if (channels == 2) {
                for (int i = 0; i < len; i += 2) {
                    short left = samples[i];
                    short right = (i + 1 < len) ? samples[i + 1] : 0;
                    short mixed = (short) ((left + right) / 2);
                    baos.write(mixed & 0xFF);
                    baos.write((mixed >> 8) & 0xFF);
                }
            } else {
                for (int i = 0; i < len; i++) {
                    short s = samples[i];
                    baos.write(s & 0xFF);
                    baos.write((s >> 8) & 0xFF);
                }
            }
            bitstream.closeFrame();
        }
        bitstream.close();

        byte[] pcm = baos.toByteArray();
        if (sampleRate == -1 || pcm.length == 0) {
            throw new IOException("MP3 file is empty or unreadable");
        }

        float duration = (pcm.length / 2.0f) / sampleRate;
        return new DecodedAudio(toDirectBuffer(pcm), monoFormat(sampleRate), duration);
    }

    private static byte[] mixToMonoPcm16(byte[] pcm, int channels) {
        if (channels <= 1) {
            return pcm;
        }

        byte[] mono = new byte[pcm.length / channels];
        int frameSize = channels * 2;
        int outIndex = 0;

        for (int src = 0; src + frameSize <= pcm.length; src += frameSize) {
            int mixed = 0;
            for (int channel = 0; channel < channels; channel++) {
                int base = src + (channel * 2);
                short sample = (short) ((pcm[base] & 0xFF) | (pcm[base + 1] << 8));
                mixed += sample;
            }

            short avg = (short) (mixed / channels);
            mono[outIndex++] = (byte) (avg & 0xFF);
            mono[outIndex++] = (byte) ((avg >> 8) & 0xFF);
        }

        return mono;
    }

    private static byte[] mixShortBufferToMono(ShortBuffer decoded, int channels) {
        if (channels <= 1) {
            byte[] pcm = new byte[decoded.remaining() * 2];
            int outIndex = 0;
            while (decoded.hasRemaining()) {
                short sample = decoded.get();
                pcm[outIndex++] = (byte) (sample & 0xFF);
                pcm[outIndex++] = (byte) ((sample >> 8) & 0xFF);
            }
            return pcm;
        }

        int frames = decoded.remaining() / channels;
        byte[] mono = new byte[frames * 2];
        int outIndex = 0;

        while (decoded.remaining() >= channels) {
            int mixed = 0;
            for (int channel = 0; channel < channels; channel++) {
                mixed += decoded.get();
            }

            short avg = (short) (mixed / channels);
            mono[outIndex++] = (byte) (avg & 0xFF);
            mono[outIndex++] = (byte) ((avg >> 8) & 0xFF);
        }

        return mono;
    }

    private static AudioFormat monoFormat(int sampleRate) {
        return new AudioFormat(sampleRate, 16, 1, true, false);
    }

    private static ByteBuffer toDirectBuffer(byte[] pcm) {
        ByteBuffer buf = BufferUtils.createByteBuffer(pcm.length);
        buf.put(pcm);
        buf.flip();
        return buf;
    }

    private static String getExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot + 1) : "";
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
