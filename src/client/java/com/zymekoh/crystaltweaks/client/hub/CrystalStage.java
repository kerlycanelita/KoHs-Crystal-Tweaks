package com.zymekoh.crystaltweaks.client.hub;

import com.zymekoh.crystaltweaks.client.AfterglowTimeline;
import com.zymekoh.crystaltweaks.client.CrystalAfterglow;
import com.zymekoh.crystaltweaks.client.CrystalAfterglowState;
import com.zymekoh.crystaltweaks.client.CrystalAppearance;
import com.zymekoh.crystaltweaks.client.CrystalAppearanceAccess;
import com.zymekoh.crystaltweaks.client.CrystalGlowAccess;
import com.zymekoh.crystaltweaks.client.CrystalGlowMath;
import com.zymekoh.crystaltweaks.client.CrystalGlowRenderer;
import com.zymekoh.crystaltweaks.client.CrystalTheme;
import com.zymekoh.crystaltweaks.client.CrystalUi;
import com.zymekoh.crystaltweaks.client.CrystalVisualConfig;
import com.zymekoh.crystaltweaks.client.hub.HubLayout.Rect;
import com.zymekoh.crystaltweaks.client.sound.CrystalSoundManager;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.entity.EndCrystalRenderer;
import net.minecraft.client.renderer.entity.state.EndCrystalRenderState;
import net.minecraft.world.entity.EntityType;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The crystal in the middle of the hub: the real End Crystal model, in the colours, glow and flash
 * the player has chosen, turning over an obsidian pedestal ringed in its own light.
 *
 * <p>A left click (or the attack key) blows it up with the chosen sound and flash; a right click
 * (or the use key) brings it straight back, as a placement would. It also slides and grows between
 * places on the screen (the middle of the intro, the stage, a corner while a drawer is open) by
 * following a target on a short spring, so it never jumps.</p>
 */
public final class CrystalStage {
    private static final long APPEAR_NANOS = 460_000_000L;
    private static final long EXPLODE_NANOS = 280_000_000L;
    private static final long RESPAWN_NANOS = 2_600_000_000L;
    private static final float TILT = 0.18F;

    private enum Phase { APPEARING, VISIBLE, EXPLODING, HIDDEN }

    private final EndCrystalRenderState state = new EndCrystalRenderState();
    private final AfterglowTimeline<CrystalAfterglowState> flashes = new AfterglowTimeline<>(4);
    private final long bornAt = System.nanoTime();
    private Phase phase = Phase.APPEARING;
    private long phaseAt = System.nanoTime();
    private long respawnAt;
    private float x;
    private float y;
    private float size;
    private Rect target = Rect.EMPTY;
    private boolean placed;
    private float hover;
    private boolean groundLight = true;

    public CrystalStage() {
        prepare(this.state);
    }

    static void prepare(EndCrystalRenderState renderState) {
        renderState.entityType = EntityType.END_CRYSTAL;
        renderState.showsBottom = false;
        renderState.boundingBoxWidth = 2.0F;
        renderState.boundingBoxHeight = 2.0F;
        renderState.eyeHeight = 1.0F;
        renderState.outlineColor = 0;
        // The GUI has no world light to sample: full block and sky light, as inventory items get.
        renderState.lightCoords = 15728880;
    }

    /** Where the crystal should go; it glides there unless {@code instant}. */
    public void setTarget(Rect rect, boolean instant) {
        this.target = rect;
        if (instant || !this.placed) {
            this.x = rect.centerX();
            this.y = rect.centerY();
            this.size = rect.width();
            this.placed = true;
        }
    }

    public boolean contains(double mouseX, double mouseY) {
        float half = this.size / 2.0F;
        return this.size > 8.0F && mouseX >= this.x - half && mouseX < this.x + half && mouseY >= this.y - half
                && mouseY < this.y + half;
    }

    public int centerX() {
        return Math.round(this.x);
    }

    public int centerY() {
        return Math.round(this.y);
    }

    public float size() {
        return this.size;
    }

    /** Whether the ground under the crystal catches its light, as the reflections would in a world. */
    public void setGroundLight(boolean groundLight) {
        this.groundLight = groundLight;
    }

    public void appear(long now) {
        this.phase = Phase.APPEARING;
        this.phaseAt = now;
        this.respawnAt = 0L;
    }

    public void explode(long now, CrystalAppearance look, boolean withSound) {
        if (withSound) {
            CrystalSoundManager.playPreviewExplosion();
        }
        if (look.flashActive()) {
            CrystalAfterglowState light = CrystalAfterglow.snapshot(this.state);
            ((CrystalAppearanceAccess) light).crystalTweaks$appearance(look.copy());
            this.flashes.start(UUID.randomUUID(), light, now, look.flashDurationNanos());
        }
        this.phase = Phase.EXPLODING;
        this.phaseAt = now;
        this.respawnAt = now + RESPAWN_NANOS;
    }

    /** A click on the crystal: attack blows it up, use brings it back. */
    public void click(long now, boolean attack, CrystalAppearance look) {
        update(now);
        if (attack) {
            if (this.phase == Phase.VISIBLE || this.phase == Phase.APPEARING) {
                explode(now, look, true);
            }
        } else {
            appear(now);
        }
    }

    /** Clears any flash still fading: a new style is about to be shown. */
    public void clearFlashes() {
        this.flashes.clear();
    }

    public boolean idle() {
        return this.phase == Phase.VISIBLE || this.phase == Phase.APPEARING;
    }

    private void update(long now) {
        long elapsed = now - this.phaseAt;
        if (this.phase == Phase.APPEARING && elapsed >= APPEAR_NANOS) {
            this.phase = Phase.VISIBLE;
            this.phaseAt = now;
        } else if (this.phase == Phase.EXPLODING && elapsed >= EXPLODE_NANOS) {
            this.phase = Phase.HIDDEN;
            this.phaseAt = now;
        } else if (this.phase == Phase.HIDDEN && now >= this.respawnAt) {
            appear(now);
        }
    }

    private float modelScale(long now) {
        return switch (this.phase) {
            case APPEARING -> HubMotion.easeOutBack(HubMotion.progress(now - this.phaseAt, APPEAR_NANOS));
            case VISIBLE -> 1.0F;
            case EXPLODING -> 1.0F - HubMotion.easeOutCubic(HubMotion.progress(now - this.phaseAt, EXPLODE_NANOS));
            case HIDDEN -> 0.0F;
        };
    }

    /**
     * Draws the crystal, its pedestal, its motes and any flash still fading.
     *
     * @param alpha how much of the pedestal and particles show, for entrances; the model itself
     *              grows in with {@link #appear} rather than fading
     */
    public void render(GuiGraphicsExtractor graphics, Font font, long now, float frameMillis, CrystalAppearance look,
            float alpha, int mouseX, int mouseY, boolean showClickHint) {
        if (this.placed) {
            // An empty target is a place too: the crystal shrinks into it (a page that fills the stage).
            this.x = HubMotion.damp(this.x, this.target.centerX(), frameMillis, 90.0F);
            this.y = HubMotion.damp(this.y, this.target.centerY(), frameMillis, 90.0F);
            this.size = HubMotion.damp(this.size, this.target.width(), frameMillis, 90.0F);
        }
        if (this.size < 6.0F || alpha <= 0.02F) {
            return;
        }
        update(now);
        int renderSize = Math.round(this.size);
        int left = Math.round(this.x - renderSize / 2.0F);
        int top = Math.round(this.y - renderSize / 2.0F);
        boolean hovered = contains(mouseX, mouseY);
        this.hover = HubMotion.damp(this.hover, hovered ? 1.0F : 0.0F, frameMillis, 80.0F);
        this.state.ageInTicks = (now - this.bornAt) / 50_000_000.0F;
        CrystalAppearance shown = look.copy();
        if (this.phase == Phase.EXPLODING || this.phase == Phase.HIDDEN) {
            // The flash fades at full size on its own; the shrinking model must not carry a halo.
            shown.glowEnabled = false;
        }
        if ((Object) this.state instanceof CrystalAppearanceAccess access) {
            access.crystalTweaks$appearance(shown);
        }
        if ((Object) this.state instanceof CrystalGlowAccess glow) {
            glow.crystalTweaks$surfaces(this.groundLight
                    ? List.of(new CrystalGlowRenderer.Surface(-1.4F, -0.02F, -1.4F, 1.4F, 1.4F)) : List.of());
        }
        HubDraw.isolate(graphics);
        drawPedestal(graphics, now, Math.round(this.x), top + Math.round(renderSize * 0.86F), renderSize, look, alpha);
        drawMotes(graphics, now, Math.round(this.x), Math.round(this.y), renderSize, look, alpha);
        HubDraw.isolate(graphics);
        Quaternionf rotation = new Quaternionf().rotateZ((float) Math.PI).rotateX(TILT);
        float scale = modelScale(now);
        float crystalScale = renderSize * glowScale(look);
        if (scale > 0.01F) {
            graphics.entity(this.state, crystalScale * scale, new Vector3f(0.0F, 1.0F, 0.0F), rotation, new Quaternionf(),
                    left, top, left + renderSize, top + renderSize);
        }
        for (AfterglowTimeline.Sample<CrystalAfterglowState> tail : this.flashes.samples(now)) {
            CrystalAfterglowState light = tail.value();
            light.opacity = tail.opacity();
            light.progress = tail.progress();
            CrystalAppearance flash = CrystalAppearanceAccess.of(light);
            float flashScale = renderSize * flashScale(flash);
            float centre = (2.0F + EndCrystalRenderer.getY(light.ageInTicks * flash.floatingSpeedPercent / 100.0F))
                    * (float) Math.cos(TILT);
            float lift = centre + crystalScale / flashScale * (1.0F - centre);
            graphics.entity(light, flashScale, new Vector3f(0.0F, lift, 0.0F), rotation, new Quaternionf(), left, top,
                    left + renderSize, top + renderSize);
        }
        HubDraw.isolate(graphics);
        drawBurst(graphics, now, Math.round(this.x), Math.round(this.y), renderSize, look.haloColor());
        HubDraw.isolate(graphics);
        if (showClickHint && this.hover > 0.05F && idle() && renderSize >= 60) {
            String hint = CrystalUi.spanish() ? "Clic: explotar · Clic derecho: colocar" : "Click: explode · Right click: place";
            if (font.width(hint) > renderSize + 30) {
                hint = CrystalUi.spanish() ? "Clic: explotar" : "Click: explode";
            }
            CrystalUi.centered(graphics, font, hint, Math.round(this.x), top + renderSize - 14,
                    CrystalTheme.withAlpha(0xF3D2FF, Math.round(220 * this.hover * alpha)));
        }
    }

    private void drawPedestal(GuiGraphicsExtractor graphics, long now, int centerX, int centerY, int renderSize,
            CrystalAppearance look, float alpha) {
        int radiusX = Math.max(6, Math.round(renderSize * 0.3F));
        int radiusY = Math.max(2, Math.round(renderSize * 0.065F));
        CrystalUi.ellipse(graphics, centerX, centerY + 2, radiusX + 2, radiusY + 1, CrystalTheme.fade(0x66000000, alpha));
        CrystalUi.ellipse(graphics, centerX, centerY, radiusX, radiusY, CrystalTheme.fade(0xE0140A1E, alpha));
        CrystalUi.ellipse(graphics, centerX, centerY - 1, Math.max(1, radiusX - 2), Math.max(1, radiusY - 1),
                CrystalTheme.fade(0xE0231233, alpha));
        float pulse = HubMotion.breathe(now / 1_000_000_000.0D, 2.85D);
        int glowAlpha = look.glowActive() ? Math.round(110 + 90 * pulse) : 60;
        CrystalUi.ellipseRim(graphics, centerX, centerY, radiusX, radiusY,
                CrystalTheme.fade(CrystalTheme.withAlpha(look.haloColor(), glowAlpha), alpha));
        if (look.glowActive()) {
            CrystalUi.ellipse(graphics, centerX, centerY - 1, Math.round(radiusX * 0.55F), Math.max(1, radiusY / 2),
                    CrystalTheme.fade(CrystalTheme.withAlpha(look.haloColor(), Math.round(40 + 40 * pulse)), alpha));
        }
    }

    private static void drawMotes(GuiGraphicsExtractor graphics, long now, int centerX, int centerY, int renderSize,
            CrystalAppearance look, float alpha) {
        double seconds = now / 1_000_000_000.0D;
        int count = Math.max(10, Math.min(26, renderSize / 5));
        int tint = look.haloColor();
        for (int index = 0; index < count; index++) {
            double angle = seconds * (0.55D + index % 3 * 0.12D) + index * 2.399963229728653D;
            float radius = renderSize * (0.24F + index % 5 * 0.035F);
            int x = centerX + Math.round((float) Math.cos(angle) * radius);
            int y = centerY + Math.round((float) Math.sin(angle * 1.13D) * radius * 0.72F);
            int size = index % 7 == 0 ? 2 : 1;
            int moteAlpha = Math.round((92 + index % 5 * 16) * alpha);
            graphics.fill(x - 1, y - 1, x + size + 1, y + size + 1, CrystalTheme.withAlpha(tint, moteAlpha / 4));
            graphics.fill(x, y, x + size, y + size,
                    CrystalTheme.withAlpha(index % 3 == 0 ? 0xF4E8FF : CrystalTheme.lerp(tint, 0xFFFFFFFF, 0.4F), moteAlpha));
        }
    }

    /** Sparks flying out as it explodes, or drawing in as it appears. */
    private void drawBurst(GuiGraphicsExtractor graphics, long now, int centerX, int centerY, int renderSize, int tint) {
        if (this.phase != Phase.EXPLODING && this.phase != Phase.APPEARING) {
            return;
        }
        boolean exploding = this.phase == Phase.EXPLODING;
        float value = HubMotion.progress(now - this.phaseAt, exploding ? EXPLODE_NANOS : APPEAR_NANOS);
        int count = exploding ? 34 : 22;
        for (int index = 0; index < count; index++) {
            double angle = index * 2.399963229728653D;
            float distance = exploding
                    ? value * renderSize * (0.24F + index % 4 * 0.055F)
                    : (1.0F - value) * renderSize * (0.20F + index % 3 * 0.045F);
            int x = centerX + Math.round((float) Math.cos(angle) * distance);
            int y = centerY + Math.round((float) Math.sin(angle) * distance);
            int alpha = exploding
                    ? Math.round(220.0F * (1.0F - value))
                    : Math.round(170.0F * (1.0F - Math.abs(value * 2.0F - 1.0F)));
            int color = CrystalTheme.withAlpha(index % 3 == 0 ? 0xF5E8FF : tint, alpha);
            int size = index % 7 == 0 ? 3 : index % 3 == 0 ? 2 : 1;
            graphics.fill(x - 1, y - 1, x + size + 1, y + size + 1, CrystalTheme.withAlpha(tint, alpha / 4));
            graphics.fill(x, y, x + size, y + size, color);
        }
    }

    /** Smaller when the glow is on, so its aura, or the old halo at any power, fits the box. */
    static float glowScale(CrystalAppearance look) {
        if (!look.glowActive()) {
            return 0.43F;
        }
        return !CrystalVisualConfig.oldGlow() ? 0.38F
                : Math.min(0.30F, 0.47F / CrystalGlowMath.radius(CrystalGlowMath.power(look.glowPowerPercent)));
    }

    /** Small enough that the whole flash fits the box, at any size up to 300%. */
    static float flashScale(CrystalAppearance look) {
        return Math.min(0.30F, 0.47F / look.flashRadius());
    }
}
