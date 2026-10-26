package com.zymekoh.crystaltweaks.client.hub;

import com.zymekoh.crystaltweaks.client.CrystalAppearance;
import com.zymekoh.crystaltweaks.client.CrystalTheme;
import com.zymekoh.crystaltweaks.client.CrystalVisualConfig;
import com.zymekoh.crystaltweaks.client.hub.HubLayout.Rect;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

/**
 * The short scene each option plays over the live crystal when it is switched on, so the player
 * sees what they just turned on: light blooming out of the crystal for the glow, streaks for the
 * blur, an obsidian block and a shield ring under it for Don't break obsidian, a clock sweeping for
 * the debounce, notes rising for the sound, the crystal shattering into something else for the
 * Converter. About a second each, drawn in light over the preview, never over the controls.
 */
public final class OptionFx {
    public enum Effect { GLOW, BLUR, OWN_COLOR, SOUND, SHIELD, DEBOUNCE, CONVERT, OLD_GLOW, HERZIUM, QUALITY,
            CORE, BLAST, GHOST, REFUSED, PACE }

    private static final long NANOS = 1_050_000_000L;
    private static final Identifier OBSIDIAN = HubDraw.vanilla("textures/block/obsidian.png");
    private static final Identifier NOTE = HubDraw.vanilla("textures/particle/note.png");
    private static final Identifier CLOCK = HubDraw.vanilla("textures/item/clock_00.png");
    private static final Identifier[] EXPLOSION = new Identifier[16];

    static {
        for (int frame = 0; frame < EXPLOSION.length; frame++) {
            EXPLOSION[frame] = HubDraw.vanilla("textures/particle/explosion_" + frame + ".png");
        }
    }
    private static final int[] NOTE_COLORS = {0xFF77D700, 0xFF00C7FF, 0xFF9B4BFF, 0xFFFF4FC8, 0xFFFFC800};

    /** The stand-ins of the ghost crystals and the crystal that lands at the server's pace: the real model. */
    private final MiniCrystal[] ghosts = {new MiniCrystal(0.1F), new MiniCrystal(0.4F), new MiniCrystal(0.7F)};
    private final MiniCrystal landing = new MiniCrystal(0.5F);
    private Effect effect;
    private long startedAt;
    private int color = 0xFFC880FF;

    /** Plays {@code effect} from now, in {@code color} where it has one, with its sound. */
    public void play(Effect effect, int color) {
        this.effect = effect;
        this.startedAt = System.nanoTime();
        this.color = color | 0xFF000000;
        SoundEvent sound = switch (effect) {
            case GLOW, OLD_GLOW -> SoundEvents.AMETHYST_BLOCK_RESONATE;
            case BLUR, QUALITY -> SoundEvents.AMETHYST_BLOCK_CHIME;
            case OWN_COLOR -> SoundEvents.AMETHYST_CLUSTER_PLACE;
            case SOUND -> SoundEvents.NOTE_BLOCK_CHIME.value();
            case SHIELD -> SoundEvents.ANVIL_LAND;
            case DEBOUNCE -> SoundEvents.UI_BUTTON_CLICK.value();
            case CONVERT -> SoundEvents.ENDER_EYE_DEATH;
            case HERZIUM -> SoundEvents.BEACON_POWER_SELECT;
            case CORE -> SoundEvents.GLASS_BREAK;
            case BLAST -> SoundEvents.GENERIC_EXPLODE.value();
            case GHOST -> SoundEvents.ENDER_EYE_LAUNCH;
            case REFUSED -> SoundEvents.NOTE_BLOCK_BASS.value();
            case PACE -> SoundEvents.END_PORTAL_FRAME_FILL;
        };
        float pitch = switch (effect) {
            case SHIELD -> 1.8F;
            case REFUSED -> 0.6F;
            case CORE, PACE -> 1.5F;
            default -> 1.3F;
        };
        float volume = switch (effect) {
            case SHIELD -> 0.25F;
            case BLAST -> 0.18F;
            default -> 0.5F;
        };
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
    }

    public boolean playing() {
        return this.effect != null && System.nanoTime() - this.startedAt < NANOS;
    }

    /** Draws the scene over the crystal at ({@code centerX}, {@code centerY}), inside {@code area}. */
    public void render(GuiGraphicsExtractor graphics, Rect area, int centerX, int centerY, float crystalSize, HubSkin skin) {
        if (!playing() || area.isEmpty()) {
            return;
        }
        float t = HubMotion.progress(System.nanoTime() - this.startedAt, NANOS);
        float out = HubMotion.easeOutCubic(t);
        float fade = 1.0F - t;
        float size = Math.max(24.0F, crystalSize);
        HubDraw.isolate(graphics);
        graphics.enableScissor(area.x(), area.y(), area.right(), area.bottom());
        switch (this.effect) {
            case GLOW, OLD_GLOW -> {
                // Light blooming out of the crystal: two rings and a soft disc, in the glow's colour.
                HubDraw.glowDisc(graphics, centerX, centerY, Math.round(size * (0.4F + 0.8F * out)), this.color, 0.7F * fade);
                HubDraw.isolate(graphics);
                HubDraw.ring(graphics, centerX, centerY, size * (0.3F + 1.2F * out), 2, CrystalTheme.withAlpha(this.color, Math.round(220 * fade)));
                HubDraw.isolate(graphics);
                HubDraw.ring(graphics, centerX, centerY, size * (0.2F + 0.8F * out), 1, CrystalTheme.withAlpha(0xFFFFFF, Math.round(160 * fade)));
                if (this.effect == Effect.OLD_GLOW) {
                    for (int ray = 0; ray < 8; ray++) {
                        double angle = ray * Math.PI / 4.0D + t * 0.6D;
                        float inner = size * 0.35F;
                        float outer = size * (0.5F + 0.9F * out);
                        HubDraw.line(graphics, centerX + (float) Math.cos(angle) * inner, centerY + (float) Math.sin(angle) * inner,
                                centerX + (float) Math.cos(angle) * outer, centerY + (float) Math.sin(angle) * outer, 1.5F,
                                CrystalTheme.withAlpha(this.color, Math.round(200 * fade)));
                    }
                }
            }
            case BLUR, QUALITY -> {
                // Streaks round the crystal, the trail its turning leaves.
                for (int streak = 0; streak < 6; streak++) {
                    double angle = streak * Math.PI / 3.0D + out * 2.4D;
                    float radius = size * (0.55F + 0.1F * streak % 2);
                    for (int step = 0; step < 6; step++) {
                        double a = angle - step * 0.12D;
                        HubDraw.sparkle(graphics, Math.round(centerX + (float) Math.cos(a) * radius), Math.round(centerY + (float) Math.sin(a) * radius * 0.6F),
                                Math.max(1, 3 - step / 2), CrystalTheme.withAlpha(skin.accentBright, Math.round((200 - step * 30) * fade)));
                    }
                }
            }
            case OWN_COLOR -> {
                // A burst of shards in the chosen colour.
                for (int shard = 0; shard < 10; shard++) {
                    double angle = shard * Math.PI / 5.0D + 0.3D;
                    float distance = size * (0.2F + 1.0F * out);
                    HubDraw.shard(graphics, centerX + (float) Math.cos(angle) * distance, centerY + (float) Math.sin(angle) * distance,
                            2.5F, 6.0F * fade + 1.0F, (float) angle, CrystalTheme.withAlpha(this.color, Math.round(230 * fade)));
                }
                HubDraw.isolate(graphics);
                HubDraw.ring(graphics, centerX, centerY, size * (0.2F + 0.9F * out), 2, CrystalTheme.withAlpha(this.color, Math.round(200 * fade)));
            }
            case SOUND -> {
                // Notes rising out of the crystal in the note block's colours.
                for (int note = 0; note < 5; note++) {
                    float delay = note * 0.12F;
                    float local = Math.max(0.0F, Math.min(1.0F, (t - delay) / 0.7F));
                    if (local <= 0.0F || local >= 1.0F) {
                        continue;
                    }
                    float x = centerX + (note - 2) * size * 0.22F + (float) Math.sin(local * 6.0F + note) * 4.0F;
                    float y = centerY - size * 0.2F - local * size * 0.9F;
                    HubDraw.texture(graphics, NOTE, 8, 8, x, y, 9.0F, 9.0F, 0.0F,
                            CrystalTheme.withAlpha(NOTE_COLORS[note], Math.round(255 * (1.0F - local))));
                }
            }
            case SHIELD -> {
                // An obsidian block rises under the crystal and a shield ring locks round it.
                float rise = HubMotion.easeOutBack(Math.min(1.0F, t * 2.2F));
                HubDraw.isoBlock(graphics, OBSIDIAN, OBSIDIAN, centerX, centerY + size * 0.75F + (1.0F - rise) * 10.0F, size * 0.42F, -1,
                        Math.min(1.0F, fade * 1.6F));
                HubDraw.isolate(graphics);
                float lock = HubMotion.easeOutCubic(Math.min(1.0F, Math.max(0.0F, (t - 0.25F) / 0.4F)));
                HubDraw.ring(graphics, centerX, centerY + size * 0.35F, size * (1.2F - 0.45F * lock), 2,
                        CrystalTheme.withAlpha(skin.accentBright, Math.round(230 * fade)));
            }
            case DEBOUNCE -> {
                // A clock and a hand sweeping once round the obsidian's time.
                HubDraw.icon(graphics, CLOCK, centerX, centerY + size * 0.75F, 14.0F, 0.0F, CrystalTheme.withAlpha(0xFFFFFF, Math.round(255 * fade)));
                float sweep = out * (float) Math.PI * 2.0F;
                float radius = size * 0.7F;
                for (int step = 0; step < 18; step++) {
                    float a = sweep * step / 17.0F - (float) Math.PI / 2.0F;
                    HubDraw.sparkle(graphics, Math.round(centerX + (float) Math.cos(a) * radius), Math.round(centerY + (float) Math.sin(a) * radius),
                            1, CrystalTheme.withAlpha(skin.accentBright, Math.round(220 * fade)));
                }
            }
            case CONVERT -> {
                // The crystal breaks into shards that whirl in and close on what it becomes.
                for (int shard = 0; shard < 12; shard++) {
                    double angle = shard * Math.PI / 6.0D + out * 3.0D;
                    float distance = size * (1.1F - 0.9F * out);
                    HubDraw.shard(graphics, centerX + (float) Math.cos(angle) * distance, centerY + (float) Math.sin(angle) * distance,
                            2.0F, 5.0F, (float) angle, CrystalTheme.withAlpha(skin.accentBright, Math.round(230 * fade)));
                }
            }
            case CORE -> {
                // The crystal you hit gone at once: a white flash, its shards flung out ahead of the server.
                HubDraw.glowDisc(graphics, centerX, centerY, Math.round(size * 0.7F), 0xFFFFFFFF, 0.85F * Math.max(0.0F, 1.0F - t * 4.0F));
                HubDraw.isolate(graphics);
                float burst = HubMotion.easeOutCubic(Math.min(1.0F, t * 1.7F));
                for (int shard = 0; shard < 14; shard++) {
                    double angle = shard * Math.PI / 7.0D + 0.2D;
                    float distance = size * (0.15F + 1.35F * burst);
                    HubDraw.shard(graphics, centerX + (float) Math.cos(angle) * distance, centerY + (float) Math.sin(angle) * distance,
                            2.0F, 5.0F * fade + 1.0F, (float) angle + t * 3.0F, CrystalTheme.withAlpha(this.color, Math.round(230 * fade)));
                }
                HubDraw.isolate(graphics);
                HubDraw.ring(graphics, centerX, centerY, size * (0.25F + 1.2F * out), 1, CrystalTheme.withAlpha(0xFFFFFF, Math.round(190 * fade)));
            }
            case BLAST -> {
                // The game's own explosion where the crystal stands, at the hit, and a second one behind it.
                int frame = (int) (t * 1.6F * EXPLOSION.length);
                if (frame < EXPLOSION.length) {
                    HubDraw.texture(graphics, EXPLOSION[frame], 32, 32, centerX, centerY, size * 1.7F, size * 1.7F, 0.0F, 0xFFFFFFFF);
                }
                int late = (int) ((t - 0.15F) * 1.6F * EXPLOSION.length);
                if (t >= 0.15F && late < EXPLOSION.length) {
                    HubDraw.isolate(graphics);
                    HubDraw.texture(graphics, EXPLOSION[late], 32, 32, centerX - size * 0.45F, centerY - size * 0.3F, size, size, 0.0F,
                            0xFFFFFFFF);
                }
            }
            case GHOST -> {
                // Pale stand-ins breathing in round the crystal, then giving way to it, as the real one does.
                if (t < 0.92F) {
                    CrystalAppearance look = HubIcons.ghostLook();
                    long now = System.nanoTime();
                    for (int ghost = 0; ghost < this.ghosts.length; ghost++) {
                        double angle = ghost * Math.PI * 2.0D / 3.0D - Math.PI / 2.0D + t * 1.2D;
                        float distance = size * (1.05F - 0.95F * out);
                        float ghostX = centerX + (float) Math.cos(angle) * distance;
                        float ghostY = centerY + (float) Math.sin(angle) * distance * 0.7F;
                        int box = Math.max(6, Math.round(size * 0.7F * (1.0F - 0.5F * out)));
                        HubDraw.glowDisc(graphics, Math.round(ghostX), Math.round(ghostY), box / 2 + 4, 0xFFC880FF, 0.4F * fade);
                        HubDraw.isolate(graphics);
                        this.ghosts[ghost].draw(graphics, Math.round(ghostX - box / 2.0F), Math.round(ghostY - box / 2.0F), box, look, now, 0.25F,
                                1.0F);
                        HubDraw.isolate(graphics);
                    }
                }
            }
            case REFUSED -> {
                // The server kept the crystal: it comes back ringed in crimson.
                HubDraw.glowDisc(graphics, centerX, centerY, Math.round(size * 0.5F), 0xFFD11F4A, 0.45F * fade);
                for (int pulse = 0; pulse < 2; pulse++) {
                    float local = Math.max(0.0F, Math.min(1.0F, (t - pulse * 0.22F) / 0.6F));
                    if (local <= 0.0F || local >= 1.0F) {
                        continue;
                    }
                    HubDraw.isolate(graphics);
                    HubDraw.ring(graphics, centerX, centerY, size * (1.25F - 0.7F * HubMotion.easeOutCubic(local)), 2,
                            CrystalTheme.withAlpha(0xFFFF315C, Math.round(230 * (1.0F - local))));
                }
            }
            case PACE -> {
                // The click waits for the server: its answer sweeps across, and a crystal lands on obsidian with it.
                int sweep = Math.round(area.x() + (area.width() + 30) * Math.min(1.0F, t * 1.6F) - 15);
                graphics.fill(sweep, area.y() + 4, sweep + 2, area.bottom() - 4, CrystalTheme.withAlpha(skin.accentBright, Math.round(200 * fade)));
                float side = centerX + size * 0.95F;
                HubDraw.isoBlock(graphics, OBSIDIAN, OBSIDIAN, side, centerY + size * 0.75F, size * 0.42F, -1, Math.min(1.0F, fade * 1.6F));
                HubDraw.isolate(graphics);
                float land = Math.max(0.0F, Math.min(1.0F, (t - 0.25F) / 0.35F));
                if (t < 0.92F) {
                    int box = Math.round(size * 0.6F);
                    float landingY = centerY + size * 0.28F - (1.0F - land * land) * size * 0.9F;
                    this.landing.draw(graphics, Math.round(side - box / 2.0F), Math.round(landingY - box / 2.0F), box,
                            MiniCrystal.spinning(CrystalVisualConfig.visuals(false)), System.nanoTime(), 0.25F, 1.0F);
                }
                if (land >= 1.0F) {
                    float ring = Math.min(1.0F, (t - 0.6F) / 0.3F);
                    HubDraw.isolate(graphics);
                    HubDraw.ring(graphics, side, centerY + size * 0.55F, size * (0.3F + 0.6F * ring), 1,
                            CrystalTheme.withAlpha(this.color, Math.round(220 * (1.0F - ring))));
                }
            }
            case HERZIUM -> {
                // Herzium's speed waves across the preview.
                for (int wave = 0; wave < 3; wave++) {
                    float x = area.x() + (area.width() + 40) * Math.min(1.0F, Math.max(0.0F, t * 1.4F - wave * 0.15F)) - 20;
                    graphics.fill(Math.round(x), area.y() + 4, Math.round(x) + 2, area.bottom() - 4,
                            CrystalTheme.withAlpha(0xFF52F2FF, Math.round(170 * fade)));
                }
            }
        }
        graphics.disableScissor();
        HubDraw.isolate(graphics);
    }
}
