package com.zymekoh.crystaltweaks.client.hub;

import com.zymekoh.crystaltweaks.client.AfterglowTimeline;
import com.zymekoh.crystaltweaks.client.CrystalAfterglow;
import com.zymekoh.crystaltweaks.client.CrystalAfterglowState;
import com.zymekoh.crystaltweaks.client.CrystalAppearance;
import com.zymekoh.crystaltweaks.client.CrystalAppearanceAccess;
import com.zymekoh.crystaltweaks.client.CrystalGlowAccess;
import com.zymekoh.crystaltweaks.client.CrystalGlowMath;
import com.zymekoh.crystaltweaks.client.CrystalGlowRenderer;
import com.zymekoh.crystaltweaks.client.CrystalGlowStyle;
import com.zymekoh.crystaltweaks.client.CrystalGlowStyle.Settings;
import com.zymekoh.crystaltweaks.client.CrystalPose;
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
 * the player has chosen, floating over an obsidian floor before a castle wall (see {@link
 * CurtainWall}).
 *
 * <p>It is drawn at its real size: a block of the wall is a block of the crystal, so a bigger
 * crystal looks bigger against the stones. Only when it and its light would not fit the page does
 * the view draw back, the wall's blocks shrinking with it, so nothing is ever cut off.</p>
 *
 * <p>A left click (or the attack key) blows it up with the chosen sound and flash; a right click
 * (or the use key) brings it straight back, as a placement would. It grows into its place and
 * shrinks out of it by following a target on a short spring, so it never jumps.</p>
 */
public final class CrystalStage {
    private static final long APPEAR_NANOS = 460_000_000L;
    private static final long EXPLODE_NANOS = 280_000_000L;
    private static final long RESPAWN_NANOS = 2_600_000_000L;
    private static final float TILT = CurtainWall.TILT;
    /** Blocks across and up the page shows at the closest, when everything fits. */
    private static final float BLOCKS_ACROSS = 4.0F;
    private static final float BLOCKS_UP = 4.6F;
    /** Room kept clear round the crystal's light at the page's edges, in pixels. */
    private static final float MARGIN = 4.0F;

    private enum Phase { APPEARING, VISIBLE, EXPLODING, HIDDEN }

    private final EndCrystalRenderState state = new EndCrystalRenderState();
    private final AfterglowTimeline<CrystalAfterglowState> flashes = new AfterglowTimeline<>(4);
    private final long bornAt = System.nanoTime();
    private Phase phase = Phase.APPEARING;
    private long phaseAt = System.nanoTime();
    private long respawnAt;
    /** Where the crystal's middle is this frame. */
    private float x;
    private float y;
    /** How far the crystal has grown into its place: the target's width, eased. */
    private float size;
    private Rect target = Rect.EMPTY;
    /** The page the crystal stands in: the last target that had room. */
    private Rect scene = Rect.EMPTY;
    private boolean placed;
    /** Pixels to a block, gliding as the crystal or its light grows past what the page holds. */
    private float zoom;
    /** How far the crystal reaches out from its middle this frame, in pixels, for clicks. */
    private float reach;
    /** How far the crystal and its light reach round its middle, in blocks: to each side, and up. */
    private float reachSide;
    private float reachUp;
    /** Whether the castle wall and its floor stand behind the crystal. */
    private boolean scenery;
    private float hover;
    private boolean groundLight = true;
    /** The dark disc under the crystal; off where it floats over something else, as over the grimoire's sigil. */
    private boolean pedestal = true;

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

    /**
     * The page the crystal stands in; it grows into it unless {@code instant}. An empty target
     * shrinks it away where it stands.
     */
    public void setTarget(Rect rect, boolean instant) {
        this.target = rect;
        if (!rect.isEmpty()) {
            if (!rect.equals(this.scene)) {
                // A new page: the view settles on it at once rather than drifting from the old one.
                this.zoom = 0.0F;
            }
            this.scene = rect;
        }
        if (instant || !this.placed) {
            this.size = rect.width();
            this.placed = true;
        }
    }

    /** The castle wall, its floor and the sky behind the crystal. */
    public void setScenery(boolean scenery) {
        this.scenery = scenery;
    }

    public boolean contains(double mouseX, double mouseY) {
        return this.reach > 6.0F && Math.abs(mouseX - this.x) <= this.reach && Math.abs(mouseY - this.y) <= this.reach;
    }

    public int centerX() {
        return Math.round(this.x);
    }

    public int centerY() {
        return Math.round(this.y);
    }

    /** How wide the crystal is on screen this frame, with a little of its light. */
    public float size() {
        return this.reach * 2.5F;
    }

    /** Whether the ground under the crystal catches its light, as the reflections would in a world. */
    public void setGroundLight(boolean groundLight) {
        this.groundLight = groundLight;
    }

    public void setPedestal(boolean pedestal) {
        this.pedestal = pedestal;
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
            this.size = HubMotion.damp(this.size, this.target.width(), frameMillis, 90.0F);
        }
        Rect area = this.scene;
        if (area.isEmpty() || alpha <= 0.02F) {
            this.reach = 0.0F;
            return;
        }
        float growth = HubMotion.clamp01(this.size / Math.max(1, area.width()));
        update(now);
        this.state.ageInTicks = (now - this.bornAt) / 50_000_000.0F;
        float blocks = CrystalPose.size(look);
        reach(look, blocks);
        // As close as the page allows; drawn back only as far as the crystal and its light need.
        float fit = Math.min(Math.min(area.width() / BLOCKS_ACROSS, area.height() / BLOCKS_UP),
                Math.min((area.width() / 2.0F - MARGIN) / this.reachSide,
                        (area.height() - MARGIN) / (CurtainWall.BELOW + CurtainWall.COS_TILT + this.reachUp)));
        fit = Math.max(2.0F, fit);
        this.zoom = this.zoom <= 0.0F ? fit : HubMotion.damp(this.zoom, fit, frameMillis, 140.0F);
        float zoomed = this.zoom;
        float feetY = area.bottom() - CurtainWall.BELOW * zoomed;
        this.x = area.centerX();
        this.y = feetY - CurtainWall.COS_TILT * zoomed;
        float shown = zoomed * growth;
        this.reach = Math.max(0.0F, 0.95F * blocks * shown);
        if (this.scenery) {
            CurtainWall.draw(graphics, area, this.x, feetY, zoomed, blocks, look, now / 1_000_000_000.0D, alpha);
        }
        if (growth < 0.02F) {
            return;
        }
        int spread = Math.round(2.6F * Math.min(1.6F, blocks) * shown);
        boolean hovered = contains(mouseX, mouseY);
        this.hover = HubMotion.damp(this.hover, hovered ? 1.0F : 0.0F, frameMillis, 80.0F);
        CrystalAppearance appearance = look.copy();
        if (this.phase == Phase.EXPLODING || this.phase == Phase.HIDDEN) {
            // The flash fades at full size on its own; the shrinking model must not carry a halo.
            appearance.glowEnabled = false;
        }
        if ((Object) this.state instanceof CrystalAppearanceAccess access) {
            access.crystalTweaks$appearance(appearance);
        }
        if ((Object) this.state instanceof CrystalGlowAccess glow) {
            glow.crystalTweaks$surfaces(this.groundLight
                    ? List.of(new CrystalGlowRenderer.Surface(-1.4F, -0.02F, -1.4F, 1.4F, 1.4F)) : List.of());
        }
        HubDraw.isolate(graphics);
        if (this.pedestal) {
            drawPedestal(graphics, now, Math.round(this.x), Math.round(feetY), spread, look, alpha);
        }
        drawMotes(graphics, now, Math.round(this.x), Math.round(this.y), spread, look, alpha);
        HubDraw.isolate(graphics);
        // The picture the model is drawn into is centred a block above its feet and as tall as it
        // needs to be to reach both edges of the page; the page itself clips it.
        float middle = feetY - zoomed;
        int half = Math.round(Math.max(middle - area.y(), area.bottom() - middle)) + 1;
        int top = Math.round(middle) - half;
        int bottom = Math.round(middle) + half;
        graphics.enableScissor(area.x(), area.y(), area.right(), area.bottom());
        Quaternionf rotation = new Quaternionf().rotateZ((float) Math.PI).rotateX(TILT);
        float scale = modelScale(now);
        if (scale > 0.01F) {
            // Lifted a block, so its middle sits at the picture's middle at any scale: it grows and
            // shrinks about its middle, its feet on the floor once it is whole.
            graphics.entity(this.state, shown * scale, new Vector3f(0.0F, 1.0F, 0.0F), rotation, new Quaternionf(),
                    area.x(), top, area.right(), bottom);
        }
        for (AfterglowTimeline.Sample<CrystalAfterglowState> tail : this.flashes.samples(now)) {
            CrystalAfterglowState light = tail.value();
            light.opacity = tail.opacity();
            light.progress = tail.progress();
            CrystalAppearance flash = CrystalAppearanceAccess.of(light);
            // At the crystal's own scale when it fits, smaller when it would not.
            float room = Math.min(area.width() / 2.0F, Math.min(middle - area.y(), area.bottom() - middle)) - 2.0F;
            float flashScale = Math.max(1.0F, Math.min(shown, room / Math.max(0.1F, flash.flashRadius())));
            float centre = (2.0F + EndCrystalRenderer.getY(light.ageInTicks * flash.floatingSpeedPercent / 100.0F))
                    * (float) Math.cos(TILT);
            float raise = centre + shown / flashScale * (1.0F - centre);
            graphics.entity(light, flashScale, new Vector3f(0.0F, raise, 0.0F), rotation, new Quaternionf(), area.x(), top,
                    area.right(), bottom);
        }
        graphics.disableScissor();
        HubDraw.isolate(graphics);
        drawBurst(graphics, now, Math.round(this.x), Math.round(this.y), spread, look.haloColor());
        HubDraw.isolate(graphics);
        if (showClickHint && this.hover > 0.05F && idle() && spread >= 60) {
            String hint = CrystalUi.spanish() ? "Clic: explotar · Clic derecho: colocar" : "Click: explode · Right click: place";
            if (font.width(hint) > spread + 30) {
                hint = CrystalUi.spanish() ? "Clic: explotar" : "Click: explode";
            }
            CrystalUi.centered(graphics, font, hint, Math.round(this.x), Math.round(this.y + spread * 0.4F),
                    CrystalTheme.withAlpha(0xF3D2FF, Math.round(220 * this.hover * alpha)));
        }
    }

    /**
     * How far the crystal and its light reach round its middle, in blocks, at {@code blocks} times
     * its size: the model turning on a corner and bobbing, and whatever the glow's style draws.
     */
    private void reach(CrystalAppearance look, float blocks) {
        // The frames turning on a corner, and the bob up and down.
        float side = 0.9F;
        float up = 1.3F;
        if (look.glowActive()) {
            if (CrystalVisualConfig.oldGlow()) {
                float halo = CrystalGlowMath.radius(CrystalGlowMath.power(look.glowPowerPercent));
                side = Math.max(side, halo);
                up = Math.max(up, halo + 0.4F);
            } else {
                CrystalGlowStyle style = CrystalVisualConfig.glowStyle();
                // The sheets and the aura spread a quarter of a face past the frames.
                side = 1.15F;
                up = 1.55F;
                if (style == CrystalGlowStyle.LIGHT) {
                    float orb = (0.5F + 0.11F * Math.min(3.0F, look.coreGlowPercent / 100.0F)) * Settings.HALO.factor();
                    side = Math.max(side, orb);
                    up = Math.max(up, orb + 0.4F);
                } else if (style == CrystalGlowStyle.LAYERS) {
                    side = 1.0F + 0.14F * Settings.AURA_REACH.factor() + 0.15F;
                    up = side + 0.4F;
                }
                if (style.animated() && look.styleMotion && !look.converted()) {
                    switch (style) {
                        case HEARTBEAT -> {
                            float ring = 0.65F + 0.9F * Settings.RINGS.factor();
                            side = Math.max(side, ring);
                            up = Math.max(up, ring + 0.4F);
                        }
                        case AURORA -> {
                            side = Math.max(side, 1.35F);
                            up = Math.max(up, 2.35F * Settings.AURORA_HEIGHT.factor() - 0.9F + 0.4F);
                        }
                        case ORBIT -> {
                            side = Math.max(side, 1.05F);
                            up = Math.max(up, 1.45F);
                        }
                        case FLAMES -> {
                            side = Math.max(side, 1.35F);
                            up = Math.max(up, 2.7F * Settings.FLAME_HEIGHT.factor() - 0.7F + 0.4F);
                        }
                        case SIGIL -> side = Math.max(side, 1.2F * Settings.SIGIL_SIZE.factor());
                        default -> {
                        }
                    }
                }
            }
        }
        this.reachSide = Math.max(0.3F, side * blocks);
        this.reachUp = Math.max(0.3F, up * blocks);
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

    /** Smaller when the glow is on, so its aura, its style's light or the old halo at any power, fits the box. */
    static float glowScale(CrystalAppearance look) {
        if (!look.glowActive()) {
            return 0.43F;
        }
        if (CrystalVisualConfig.oldGlow()) {
            return Math.min(0.30F, 0.47F / CrystalGlowMath.radius(CrystalGlowMath.power(look.glowPowerPercent)));
        }
        if (!look.styleMotion || look.converted()) {
            return 0.38F;
        }
        // The styles with light round the crystal reach past its aura: the heartbeat's rings furthest.
        return switch (CrystalVisualConfig.glowStyle()) {
            case HEARTBEAT, AURORA, FLAMES -> 0.30F;
            case ORBIT, SIGIL -> 0.33F;
            default -> 0.38F;
        };
    }
}
