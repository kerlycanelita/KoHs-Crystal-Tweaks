package com.zymekoh.crystaltweaks.client.hub;

import com.zymekoh.crystaltweaks.client.CrystalAppearance;
import com.zymekoh.crystaltweaks.client.CrystalTheme;
import com.zymekoh.crystaltweaks.client.CrystalUi;
import com.zymekoh.crystaltweaks.client.hub.HubLayout.Rect;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;

/**
 * A small scene under "Coming soon": an enemy crystal swinging a netherite pickaxe at a block. The
 * obsidian cracks stage by stage and breaks; bedrock takes its place, and breaks too, as it never
 * would in the game; then obsidian again, each block lasting between four and eight seconds. A click
 * on the block lends a hand and makes it go faster. It counts the blocks it has mined.
 */
final class MiningGame {
    private static final Identifier OBSIDIAN = HubDraw.vanilla("textures/block/obsidian.png");
    private static final Identifier BEDROCK = HubDraw.vanilla("textures/block/bedrock.png");
    private static final Identifier PICKAXE = HubDraw.vanilla("textures/item/netherite_pickaxe.png");
    private static final long SWING_NANOS = 520_000_000L;
    private static final long POP_NANOS = 260_000_000L;
    private static final long DEBRIS_NANOS = 700_000_000L;

    private final MiniCrystal crystal = new MiniCrystal(0.3F);
    private boolean bedrock;
    private long blockStartedAt = System.nanoTime();
    private long blockDuration = durationFor(0);
    private float bonus;
    private int mined;
    /** Long enough ago that the first block is already in place and nothing is flying. */
    private long brokenAt = System.nanoTime() - 10_000_000_000L;
    private boolean brokenWasBedrock;
    private long lastHitAt;
    private Rect block = Rect.EMPTY;

    private static long durationFor(int index) {
        // Four to eight seconds, different every block but the same on every run.
        return 4_000_000_000L + Math.round(HubMotion.hash(index * 7919L + 13L) * 4_000_000_000.0D);
    }

    private float progress(long now) {
        return HubMotion.clamp01((now - this.blockStartedAt) / (float) this.blockDuration + this.bonus);
    }

    boolean mouseClicked(double mouseX, double mouseY) {
        if (!this.block.contains(mouseX, mouseY)) {
            return false;
        }
        this.bonus += 0.12F;
        this.lastHitAt = System.nanoTime();
        play(SoundEvents.STONE_HIT, 0.9F + HubMotion.hash(this.lastHitAt) * 0.3F, 0.3F);
        return true;
    }

    private static void play(net.minecraft.sounds.SoundEvent sound, float pitch, float volume) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
    }

    void render(GuiGraphicsExtractor graphics, Font font, Rect area, long now, int mouseX, int mouseY, CrystalAppearance look,
            boolean spanish, float alpha) {
        if (area.height() < 30 || alpha <= 0.02F) {
            this.block = Rect.EMPTY;
            return;
        }
        if (progress(now) >= 1.0F) {
            this.brokenAt = now;
            this.brokenWasBedrock = this.bedrock;
            this.bedrock = !this.bedrock;
            this.mined++;
            this.blockStartedAt = now;
            this.blockDuration = durationFor(this.mined);
            this.bonus = 0.0F;
            play(SoundEvents.STONE_BREAK, 0.8F + HubMotion.hash(now) * 0.3F, 0.35F);
        }
        HubSkin skin = HubSkin.current();
        int footer = 11;
        float cube = Math.max(8.0F, Math.min((area.height() - footer) * 0.28F, area.width() * 0.12F));
        float blockX = area.centerX() + cube * 1.3F;
        float blockY = area.y() + (area.height() - footer) / 2.0F + cube * 0.1F;
        int halfWidth = Math.round(cube * 0.87F);
        this.block = new Rect(Math.round(blockX) - halfWidth, Math.round(blockY - cube), halfWidth * 2, Math.round(cube * 2));
        // The block, popping in after the last one broke.
        float pop = HubMotion.easeOutBack(HubMotion.progress(now - this.brokenAt, POP_NANOS));
        float progress = progress(now);
        int crack = Math.min(9, (int) Math.floor(progress * 10.0F));
        boolean hovered = this.block.contains(mouseX, mouseY);
        HubDraw.isolate(graphics);
        CrystalUi.ellipse(graphics, Math.round(blockX), Math.round(blockY + cube * 1.05F), Math.round(cube * 1.1F),
                Math.max(2, Math.round(cube * 0.22F)), CrystalTheme.fade(0x70000000, alpha));
        Identifier texture = this.bedrock ? BEDROCK : OBSIDIAN;
        float shake = now - this.lastHitAt < 120_000_000L ? (float) Math.sin(now / 9_000_000.0D) * 1.2F : 0.0F;
        HubDraw.isoBlock(graphics, texture, texture, blockX + shake, blockY, cube * pop, progress > 0.02F ? crack : -1, alpha);
        if (hovered) {
            HubDraw.halo(graphics, this.block.x(), this.block.y(), this.block.width(), this.block.height(),
                    skin.accentBright, 2, 0.35F * alpha);
        }
        drawDebris(graphics, now, blockX, blockY, cube, alpha);
        HubDraw.isolate(graphics);
        // The crystal and its pickaxe, left of the block.
        float bob = (float) Math.sin(now / 1_000_000_000.0D * 2.0D) * cube * 0.06F;
        int crystalSize = Math.round(cube * 2.4F);
        int crystalX = Math.round(blockX - cube * 3.9F);
        int crystalY = Math.round(blockY - crystalSize * 0.62F + bob);
        this.crystal.draw(graphics, crystalX, crystalY, crystalSize, look, now, 0.18F, 1.0F);
        drawPickaxe(graphics, now, crystalX + crystalSize * 0.78F, crystalY + crystalSize * 0.46F, cube, blockX, blockY, alpha);
        // The count, and how to help.
        String count = (spanish ? "Bloques minados: " : "Blocks mined: ") + this.mined;
        String help = spanish ? "Haz clic en el bloque para ayudar" : "Click the block to help";
        int textY = area.bottom() - 9;
        CrystalUi.centered(graphics, font, count, area.centerX(), textY, CrystalTheme.fade(skin.muted, alpha));
        if (hovered && font.width(help) < area.width()) {
            CrystalUi.centered(graphics, font, help, area.centerX(), Math.round(blockY - cube * 1.35F) - 9,
                    CrystalTheme.fade(skin.accentBright, alpha));
        }
    }

    /**
     * The pickaxe swings from raised to the block and back, quick on the way down, slow on the way
     * up; each stroke ends with chips flying off the block.
     */
    private void drawPickaxe(GuiGraphicsExtractor graphics, long now, float pivotX, float pivotY, float cube, float blockX,
            float blockY, float alpha) {
        float phase = ((now % SWING_NANOS) / (float) SWING_NANOS);
        float swing = phase < 0.3F ? HubMotion.easeInCubic(phase / 0.3F) : 1.0F - HubMotion.easeInOutSine((phase - 0.3F) / 0.7F);
        float angle = (float) Math.toRadians(-70.0D + 95.0D * swing);
        float length = cube * 1.15F;
        float headX = pivotX + (float) Math.cos(angle) * length * 0.55F;
        float headY = pivotY + (float) Math.sin(angle) * length * 0.55F;
        // The item texture points up-right; turn it to the swing.
        HubDraw.icon(graphics, PICKAXE, headX, headY, length, angle + (float) Math.toRadians(45.0D), CrystalTheme.fade(0xFFFFFFFF, alpha));
        if (phase >= 0.28F && phase < 0.42F) {
            float hit = (phase - 0.28F) / 0.14F;
            for (int index = 0; index < 5; index++) {
                float spread = HubMotion.hash(index * 31L + now / SWING_NANOS);
                float dx = (spread - 0.5F) * cube * 1.2F * hit;
                float dy = -cube * 0.6F * hit + cube * 0.9F * hit * hit;
                int x = Math.round(blockX - cube * 0.7F + dx);
                int y = Math.round(blockY - cube * 0.3F + dy);
                int color = this.bedrock ? 0xFF8A8A8A : 0xFF3B1F5A;
                graphics.fill(x, y, x + 2, y + 2, CrystalTheme.fade(color, alpha * (1.0F - hit)));
            }
        }
    }

    private void drawDebris(GuiGraphicsExtractor graphics, long now, float blockX, float blockY, float cube, float alpha) {
        float t = HubMotion.progress(now - this.brokenAt, DEBRIS_NANOS);
        if (t >= 1.0F) {
            return;
        }
        int base = this.brokenWasBedrock ? 0xFF6E6E6E : 0xFF2A1640;
        int light = this.brokenWasBedrock ? 0xFFB0B0B0 : 0xFF6A3F9A;
        for (int index = 0; index < 18; index++) {
            float angle = HubMotion.hash(index * 97L + this.mined) * (float) Math.PI * 2.0F;
            float speed = cube * (0.8F + HubMotion.hash(index * 13L + this.mined) * 1.4F);
            float x = blockX + (float) Math.cos(angle) * speed * t;
            float y = blockY - cube * 0.4F + (float) Math.sin(angle) * speed * t * 0.7F + cube * 2.2F * t * t;
            float size = Math.max(1.0F, cube * 0.16F * (1.0F - t * 0.6F));
            HubDraw.shard(graphics, x, y, size, size, angle + t * 6.0F,
                    CrystalTheme.fade(index % 3 == 0 ? light : base, alpha * (1.0F - t)));
        }
    }
}
