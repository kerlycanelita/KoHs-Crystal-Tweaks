package com.zymekoh.crystaltweaks.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.zymekoh.crystaltweaks.client.CrystalGlowStyle.Settings;
import net.minecraft.client.model.object.crystal.EndCrystalModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.EndCrystalRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The glow in the crystal's own layers, and the motion blur behind them.
 *
 * <p><b>Light</b>, the glow by default: every face of the three boxes wears a sheet of soft light
 * baked from its own texture (see {@link CrystalGlowTiles}), in its layer's colour, laid over the
 * scene rather than added to it. <b>Layers</b>, the glow of 2.5.0: the boxes drawn again as added
 * light, with larger copies for an aura. The <b>motion blur</b> is the same soft sheets where the
 * boxes were a moment ago, fading as they go back; it does not need the glow.</p>
 *
 * <p>The other styles wear one of those two and add light of their own round the crystal, moving
 * with its clock: a heartbeat's rings, an aurora, sparks in orbit, flames, a sigil on the ground
 * (see {@link Extras}). That light is drawn only up to the blur's range.</p>
 *
 * <p>All of it is geometry built here from the model's boxes and sent in one batch per material,
 * however many crystals there are: one draw for every sheet in view, not one per crystal and pass.
 * Only light is drawn, on this screen: the crystal, its hitbox and every packet stay as they are.</p>
 */
public final class CrystalLight {
    static final Identifier TEXTURE = Identifier.withDefaultNamespace("textures/entity/end_crystal/end_crystal.png");
    private static final int FULL_BRIGHT = 0xF000F0;
    /** A sheet stands this far off its face, in the box's own units, where a box runs a quarter each way. */
    private static final float OFFSET = 0.006F;
    /** How far back the blur reaches at 100%, in ticks of the crystal's clock: 24 degrees at Vanilla speed. */
    private static final float BLUR_TICKS = 8.0F;
    /** Blocks within which the faces turned away are lit too, in the Quality mode. */
    private static final float FAR_FACE_RANGE = 16.0F;
    /** The Layers style's aura: each copy's size, and its share of the light. */
    private static final float[] AURA_SCALE = {1.035F, 1.07F, 1.105F, 1.14F};
    private static final float[] AURA_LIGHT = {0.30F, 0.20F, 0.12F, 0.06F};
    /** The most passes of the core's own light in the Layers style, at full power and full core. */
    private static final float MAX_CORE_PASSES = 6.0F;
    /** A sheet's corners across its face, counter-clockwise seen from outside when the texture is not mirrored. */
    private static final int[][] CORNERS = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};
    // The light is built on the render thread only, one crystal at a time.
    private static final Matrix4f OUTER = new Matrix4f();
    private static final Matrix4f INNER = new Matrix4f();
    private static final Matrix4f CORE = new Matrix4f();
    private static final Matrix4f PLACED = new Matrix4f();
    private static final Matrix4f TURN = new Matrix4f();
    private static final Vector3f POINT = new Vector3f();
    private static final Vector3f NORMAL = new Vector3f();
    private static final Vector3f CENTRE = new Vector3f();
    private static final Vector3f AXIS_RIGHT = new Vector3f();
    private static final Vector3f AXIS_UP = new Vector3f();
    /** A heartbeat, in ticks of the crystal's clock: two beats and a rest. */
    private static final int SEGMENTS = 24;
    private static final float[] COS = new float[SEGMENTS + 1];
    private static final float[] SIN = new float[SEGMENTS + 1];

    static {
        for (int index = 0; index <= SEGMENTS; index++) {
            double angle = index * Math.PI * 2.0D / SEGMENTS;
            COS[index] = (float) Math.cos(angle);
            SIN[index] = (float) Math.sin(angle);
        }
    }

    private CrystalLight() {
    }

    /**
     * @param orientation the camera's, which the core's round light is turned to face
     * @param converted   the crystal is drawn as something else: only the round light is its, the
     *                    frames' light and their blur have the crystal's shape
     */
    public static void submit(EndCrystalModel model, EndCrystalRenderState state, PoseStack poses,
            SubmitNodeCollector collector, Quaternionf orientation, boolean converted) {
        CrystalAppearance look = CrystalAppearanceAccess.of(state);
        CrystalGlowQuality quality = CrystalVisualConfig.glowQuality();
        double distanceSq = state.distanceToCameraSq;
        if (distanceSq > quality.lightRange * quality.lightRange) {
            return;
        }
        boolean glow = look.glowActive() && !CrystalVisualConfig.oldGlow();
        boolean moving = look.rotationSpeedPercent > 0 || look.floatingSpeedPercent > 0;
        int blurSteps = !converted && look.motionBlurPercent > 0 && moving && distanceSq < quality.blurRange * quality.blurRange
                ? quality.blurSteps : 0;
        if (!glow && blurSteps == 0) {
            return;
        }
        CrystalShape shape = CrystalShape.of(model);
        CrystalGlowStyle style = CrystalVisualConfig.glowStyle();
        float power = CrystalGlowMath.power(look.glowPowerPercent);
        boolean near = distanceSq < FAR_FACE_RANGE * FAR_FACE_RANGE;
        if (glow && style.layered() && !converted) {
            collector.submitCustomGeometry(poses, CrystalGlowMaterial.layers(TEXTURE),
                    new Layers(shape, look, state.ageInTicks, power, near ? quality.auraCopies : 0, quality.innerFrame,
                            style == CrystalGlowStyle.LAYERS ? Settings.AURA_REACH.factor() : 1.0F,
                            style == CrystalGlowStyle.LAYERS ? Settings.AURA_LIGHT.factor() : 1.0F));
        }
        boolean sheets = glow && (!style.layered() || converted);
        boolean extras = glow && style.animated() && look.styleMotion && !converted
                && distanceSq < quality.blurRange * quality.blurRange;
        if (!sheets && blurSteps == 0 && !extras) {
            return;
        }
        Vector3f right = orientation.transform(new Vector3f(1.0F, 0.0F, 0.0F));
        Vector3f up = orientation.transform(new Vector3f(0.0F, 1.0F, 0.0F));
        if (sheets || blurSteps > 0) {
            RenderType material = CrystalGlowTiles.material(shape);
            if (material != null) {
                // The heartbeat is the Light style's own light, throbbing; Light itself may breathe.
                float shown = converted ? power
                        : style == CrystalGlowStyle.HEARTBEAT ? power * beat(state.ageInTicks)
                        : style == CrystalGlowStyle.LIGHT ? power * breath(state.ageInTicks) : power;
                float halo = style == CrystalGlowStyle.LIGHT ? Settings.HALO.factor() : 1.0F;
                collector.submitCustomGeometry(poses, material, new Sheets(shape, look, state.ageInTicks, shown, sheets,
                        look.glowActive(), blurSteps, quality.innerFrame, quality.farFaces && near, state.lightCoords,
                        right, up, !converted, halo));
            }
        }
        if (extras) {
            collector.submitCustomGeometry(poses, CrystalGlowMaterial.glow(), new Extras(style, look, state.ageInTicks, power, right, up));
        }
    }

    /** A colour pushed to full saturation, its brightest channel kept: thin added light stays coloured. */
    private static int vivid(int rgb) {
        int red = rgb >> 16 & 255, green = rgb >> 8 & 255, blue = rgb & 255;
        int peak = Math.max(red, Math.max(green, blue));
        return 0xFF000000 | push(red, peak) << 16 | push(green, peak) << 8 | push(blue, peak);
    }

    private static int push(int channel, int peak) {
        return Math.max(0, Math.round(peak - (peak - channel) * 1.5F));
    }

    /** How long one heartbeat takes, in ticks of the crystal's clock: its rhythm. */
    private static float beatTicks() {
        return 1200.0F / Settings.RHYTHM.value();
    }

    /** How strong the heartbeat's light is at this moment: two quick beats, then a rest. */
    static float beat(float ageInTicks) {
        float ticks = beatTicks();
        float phase = (ageInTicks % ticks + ticks) % ticks / ticks;
        float swell = 0.6F * (bump(phase, 0.04F) + 0.75F * bump(phase, 0.24F)) - 0.45F;
        return 1.0F + swell * Settings.THROB.factor();
    }

    /** How strong the Light style's light is at this moment: rising and falling slowly when it breathes. */
    private static float breath(float ageInTicks) {
        float depth = Settings.BREATH.factor();
        if (depth <= 0.0F) {
            return 1.0F;
        }
        return 1.0F - depth * 0.45F * (0.5F + 0.5F * (float) Math.cos(ageInTicks * Math.PI * 2.0D / 64.0D));
    }

    private static float bump(float phase, float centre) {
        float distance = (phase - centre) / 0.06F;
        return (float) Math.exp(-distance * distance);
    }

    /**
     * The colour a layer shines in: its own, and an unpainted layer's the glow colour, so a crystal
     * left in its texture still glows in it.
     */
    private static int shine(int layer, CrystalAppearance look) {
        return (layer & 0xFFFFFF) == 0xFFFFFF ? look.haloColor() : layer;
    }

    /**
     * The soft sheets: the glow of the Light style, and the motion blur of every style.
     *
     * @param lit        whether the glow itself is drawn here, or only the blur
     * @param glowing    whether the crystal's glow is on at all: without it the blur is a plain smear
     *                   in the world's own light, not a trail of light
     * @param lightCoords the light the crystal stands in, for that smear
     * @param framed     whether the frames are there to wear their sheets; not when the crystal is
     *                   drawn as something else
     * @param halo       how far the core's round light reaches, 1 as made
     */
    private record Sheets(CrystalShape shape, CrystalAppearance look, float age, float power, boolean lit, boolean glowing,
            int blurSteps, boolean innerFrame, boolean farFaces, int lightCoords, Vector3f right, Vector3f up, boolean framed,
            float halo) implements SubmitNodeCollector.CustomGeometryRenderer {
        @Override
        public void render(PoseStack.Pose pose, VertexConsumer buffer) {
            Matrix4f base = pose.pose();
            // In a level the pose is a plain translation from the camera; anything else is a settings preview.
            boolean world = CrystalGlowRenderer.translationOnly(base);
            int outer = shine(this.look.outerColor, this.look);
            int inner = shine(this.look.innerColor, this.look);
            int core = shine(this.look.coreColor, this.look);
            float strength = Math.min(1.0F, this.power);
            float glass = 0.30F + 0.70F * strength;
            if (this.lit) {
                // The frames are thin lines of light and wear sheets; the core is a solid box, whose
                // sheets would be plates, so its light is a round one about it.
                // Past 100% the light cannot grow brighter than its colour: it grows denser.
                float more = Math.min(1.0F, (this.power - 1.0F) / 2.0F);
                if (this.framed) {
                    boxes(buffer, base, world, this.age, outer, inner, core, glass, 0.0F);
                    if (more > 0.0F) {
                        boxes(buffer, base, world, this.age, outer, inner, core, more * 0.8F, 0.0F);
                    }
                }
                // Something drawn in the crystal's place has no core: the light is the glow's own colour.
                orb(buffer, base, world, this.framed ? core : this.look.haloColor(), glass);
            }
            if (this.blurSteps > 0) {
                float span = BLUR_TICKS * this.look.motionBlurPercent / 100.0F;
                float trail = this.glowing ? 0.75F * glass : 0.5F;
                if (!this.glowing) {
                    // A smear of the crystal as the world lights it, not light of its own.
                    float seen = 0.25F + 0.75F * Math.max(this.lightCoords >> 4 & 15, this.lightCoords >> 20 & 15) / 15.0F;
                    outer = dim(outer, seen);
                    inner = dim(inner, seen);
                    core = dim(core, seen);
                }
                for (int step = 1; step <= this.blurSteps; step++) {
                    float fade = trail * (1.0F - (step - 0.5F) / this.blurSteps);
                    boxes(buffer, base, world, this.age - span * step / this.blurSteps, outer, inner, core, fade, fade);
                }
            }
        }

        /**
         * The core's light: a soft round glow about it, facing the camera, in its own colour. The
         * core hides its middle, so what shows is light coming off the core between the frames.
         */
        private void orb(VertexConsumer buffer, Matrix4f base, boolean world, int colour, float glass) {
            float coreGlow = this.look.coreGlowPercent / 100.0F;
            int alpha = Math.round(255.0F * Math.min(1.0F, 0.85F * glass * Math.min(1.0F, coreGlow) + 0.25F * Math.max(0.0F, coreGlow - 1.0F)));
            if (alpha <= 0) {
                return;
            }
            // Out to where the core's corners turn, and wider as its glow is raised: never past the outer frame's.
            // About a stand-in it has to reach past what hides it, which is as wide as the outer frame.
            float radius = CrystalPose.size(this.look) * ((this.framed ? 0.5F : 1.05F) + 0.11F * Math.min(3.0F, coreGlow)) * this.halo;
            CrystalPose.matrices(this.age, this.look, this.shape.innerScale, this.shape.coreScale, OUTER, INNER, CORE);
            base.mul(CORE, PLACED).transformPosition(0.0F, 0.0F, 0.0F, POINT);
            // The camera's own axes, carried into this pose: a level's is a plain translation.
            base.transformDirection(this.right.x, this.right.y, this.right.z, NORMAL).mul(radius);
            float rx = NORMAL.x, ry = NORMAL.y, rz = NORMAL.z;
            base.transformDirection(this.up.x, this.up.y, this.up.z, NORMAL).mul(radius);
            float ux = NORMAL.x, uy = NORMAL.y, uz = NORMAL.z;
            int red = colour >> 16 & 255, green = colour >> 8 & 255, blue = colour & 255;
            for (int side = 0; side < (world ? 1 : 2); side++) {
                for (int corner = 0; corner < 4; corner++) {
                    int[] at = CORNERS[side == 0 ? corner : 3 - corner];
                    float s = at[0] * 2.0F - 1.0F;
                    float t = at[1] * 2.0F - 1.0F;
                    buffer.addVertex(POINT.x + s * rx + t * ux, POINT.y + s * ry + t * uy, POINT.z + s * rz + t * uz)
                            .setColor(red, green, blue, alpha)
                            .setUv(CrystalGlowTiles.orbU(at[0]), CrystalGlowTiles.orbV(at[1]))
                            .setLight(FULL_BRIGHT)
                            .setNormal(0.0F, 1.0F, 0.0F);
                }
            }
        }

        private void boxes(VertexConsumer buffer, Matrix4f base, boolean world, float at, int outer, int inner, int core,
                float glassAlpha, float coreAlpha) {
            CrystalPose.matrices(at, this.look, this.shape.innerScale, this.shape.coreScale, OUTER, INNER, CORE);
            if (glassAlpha > 0.0F) {
                faces(buffer, base.mul(OUTER, PLACED), this.shape.glass, false, outer, glassAlpha, world);
                if (this.innerFrame) {
                    faces(buffer, base.mul(INNER, PLACED), this.shape.glass, false, inner, glassAlpha, world);
                }
            }
            if (coreAlpha > 0.0F) {
                faces(buffer, base.mul(CORE, PLACED), this.shape.core, true, core, coreAlpha, world);
            }
        }

        private void faces(VertexConsumer buffer, Matrix4f placed, CrystalShape.Face[] faces, boolean core, int colour,
                float alpha, boolean world) {
            float border = core ? CrystalGlowTiles.CORE_BORDER : CrystalGlowTiles.GLASS_BORDER;
            int red = colour >> 16 & 255, green = colour >> 8 & 255, blue = colour & 255;
            for (int index = 0; index < faces.length; index++) {
                CrystalShape.Face face = faces[index];
                placed.transformPosition(face.ox() + 0.5F * (face.ux() + face.vx()), face.oy() + 0.5F * (face.uy() + face.vy()),
                        face.oz() + 0.5F * (face.uz() + face.vz()), POINT);
                placed.transformDirection(face.nx(), face.ny(), face.nz(), NORMAL).normalize();
                // How squarely the face is seen. In a level the camera is the origin; a settings
                // preview is seen from far up its own z axis, looking down it.
                float cosine = world ? -NORMAL.dot(POINT) / Math.max(1.0E-4F, POINT.length()) : NORMAL.z;
                float squarely = cosine;
                boolean fromOutside = cosine > 0.0F;
                float share = 1.0F;
                if (squarely <= 0.0F) {
                    if (!this.farFaces) {
                        continue;
                    }
                    // A face turned away, seen through the frames: its light from behind, fainter.
                    squarely = -cosine;
                    share = 0.55F;
                }
                // A sheet seen along its own plane is a line of light sticking out of the box: it goes out as it turns.
                float turned = Math.max(0.0F, Math.min(1.0F, (squarely - 0.08F) / 0.42F));
                int shown = Math.round(255.0F * alpha * share * turned * turned * (3.0F - 2.0F * turned));
                if (shown <= 0) {
                    continue;
                }
                if (world) {
                    sheet(buffer, placed, face, core, index, border, red, green, blue, shown, fromOutside != face.flipped());
                } else {
                    // A preview's pose may be mirrored: both ways round, and the material keeps the one facing out.
                    sheet(buffer, placed, face, core, index, border, red, green, blue, shown, true);
                    sheet(buffer, placed, face, core, index, border, red, green, blue, shown, false);
                }
            }
        }

        private static void sheet(VertexConsumer buffer, Matrix4f placed, CrystalShape.Face face, boolean core, int index,
                float border, int red, int green, int blue, int alpha, boolean forward) {
            for (int corner = 0; corner < 4; corner++) {
                int[] at = CORNERS[forward ? corner : 3 - corner];
                float s = at[0] == 0 ? -border : 1.0F + border;
                float t = at[1] == 0 ? -border : 1.0F + border;
                buffer.addVertex(placed, face.ox() + s * face.ux() + t * face.vx() + OFFSET * face.nx(),
                                face.oy() + s * face.uy() + t * face.vy() + OFFSET * face.ny(),
                                face.oz() + s * face.uz() + t * face.vz() + OFFSET * face.nz())
                        .setColor(red, green, blue, alpha)
                        .setUv(CrystalGlowTiles.u(core, index, s), CrystalGlowTiles.v(core, t))
                        .setLight(FULL_BRIGHT)
                        .setNormal(NORMAL.x, NORMAL.y, NORMAL.z);
            }
        }

        private static int dim(int colour, float by) {
            return 0xFF000000 | Math.round((colour >> 16 & 255) * by) << 16 | Math.round((colour >> 8 & 255) * by) << 8
                    | Math.round((colour & 255) * by);
        }
    }

    /**
     * The Layers style: the boxes again as added light in their own colours, and larger copies in
     * the glow colour whose light fades outward.
     */
    private record Layers(CrystalShape shape, CrystalAppearance look, float age, float power, int auraCopies,
            boolean innerFrame, float auraReach, float auraGain) implements SubmitNodeCollector.CustomGeometryRenderer {
        @Override
        public void render(PoseStack.Pose pose, VertexConsumer buffer) {
            Matrix4f base = pose.pose();
            // In a level the pose is a plain translation from the camera; anything else is a settings preview.
            boolean world = CrystalGlowRenderer.translationOnly(base);
            int outer = shine(this.look.outerColor, this.look);
            int inner = shine(this.look.innerColor, this.look);
            int core = CrystalGlowMath.hotColor(shine(this.look.coreColor, this.look));
            // A pass adds at most its full colour; above 100% the light takes more passes.
            int passes = Math.max(1, (int) Math.ceil(this.power));
            for (int pass = 0; pass < passes; pass++) {
                boxes(buffer, base, world, 1.0F, outer, inner, core, this.power / passes, 0.0F);
            }
            // The core's light has its own control: none at 0%, the frames' share at 100%, more passes past it.
            float coreGlow = this.look.coreGlowPercent / 100.0F;
            float coreLight = Math.min(MAX_CORE_PASSES, this.power * coreGlow);
            int corePasses = (int) Math.ceil(coreLight);
            for (int pass = 0; pass < corePasses; pass++) {
                boxes(buffer, base, world, 1.0F, outer, inner, core, 0.0F, coreLight / corePasses);
            }
            int aura = this.look.haloColor();
            float auraPower = Math.min(1.5F, this.power) * this.auraGain;
            for (int copy = 0; copy < this.auraCopies; copy++) {
                // Fewer copies carry the same light, spread over the same reach.
                int slot = this.auraCopies == AURA_SCALE.length ? copy : copy * 2 + 1;
                float light = AURA_LIGHT[slot] * auraPower * AURA_SCALE.length / this.auraCopies;
                if (light <= 0.0F) {
                    continue;
                }
                float scale = 1.0F + (AURA_SCALE[slot] - 1.0F) * this.auraReach;
                boxes(buffer, base, world, scale, aura, aura, aura, light, light * Math.min(2.0F, coreGlow));
            }
        }

        /**
         * @param frameGain how much light the two frames add
         * @param coreGain  how much the core adds
         */
        private void boxes(VertexConsumer buffer, Matrix4f base, boolean world, float scale, int outer, int inner, int core,
                float frameGain, float coreGain) {
            CrystalPose.matrices(this.age, this.look, this.shape.innerScale, this.shape.coreScale, OUTER, INNER, CORE);
            if (scale != 1.0F) {
                // The copies grow about the layers' shared centre, wherever they float.
                OUTER.scale(scale);
                TURN.rotation(CrystalPose.nestedTurn(this.age, this.look));
                INNER.set(OUTER).mul(TURN).scale(this.shape.innerScale);
                CORE.set(INNER).mul(TURN).scale(this.shape.coreScale);
            }
            box(buffer, base.mul(OUTER, PLACED), this.shape.glass, outer, frameGain, world);
            if (this.innerFrame) {
                box(buffer, base.mul(INNER, PLACED), this.shape.glass, inner, frameGain, world);
            }
            box(buffer, base.mul(CORE, PLACED), this.shape.core, core, coreGain, world);
        }

        private static void box(VertexConsumer buffer, Matrix4f placed, CrystalShape.Face[] faces, int colour, float gain,
                boolean world) {
            float light = Math.max(0.0F, Math.min(1.0F, gain));
            int alpha = Math.round(255.0F * light);
            if (alpha <= 0) {
                return;
            }
            // Premultiplied: the material adds the colour as it comes.
            int red = Math.round((colour >> 16 & 255) * light), green = Math.round((colour >> 8 & 255) * light),
                    blue = Math.round((colour & 255) * light);
            for (CrystalShape.Face face : faces) {
                placed.transformPosition(face.ox() + 0.5F * (face.ux() + face.vx()), face.oy() + 0.5F * (face.uy() + face.vy()),
                        face.oz() + 0.5F * (face.uz() + face.vz()), POINT);
                placed.transformDirection(face.nx(), face.ny(), face.nz(), NORMAL).normalize();
                // Lifted off the face, towards whoever looks at it. In the face's own plane the light
                // and the crystal's texture fight for every pixel, another way on every frame: the
                // light flickered in stripes. A level's camera is the origin; a preview is seen from
                // up its z axis.
                float lift = (world ? -NORMAL.dot(POINT) : NORMAL.z) >= 0.0F ? OFFSET : -OFFSET;
                for (int[] at : CORNERS) {
                    buffer.addVertex(placed, face.ox() + at[0] * face.ux() + at[1] * face.vx() + lift * face.nx(),
                                    face.oy() + at[0] * face.uy() + at[1] * face.vy() + lift * face.ny(),
                                    face.oz() + at[0] * face.uz() + at[1] * face.vz() + lift * face.nz())
                            .setColor(red, green, blue, alpha)
                            .setUv(at[0] == 0 ? face.u0() : face.u1(), at[1] == 0 ? face.v0() : face.v1())
                            .setOverlay(OverlayTexture.NO_OVERLAY)
                            .setLight(FULL_BRIGHT)
                            .setNormal(NORMAL.x, NORMAL.y, NORMAL.z);
                }
            }
        }
    }

    /**
     * The newer styles' own light round the crystal, added to the scene in the glow's colours: a
     * heartbeat's rings, an aurora's curtains, sparks in orbit, flames, a sigil on the ground, bolts,
     * wings, a crown of thorns, a vortex, rising souls. All of it moves with the crystal's clock, so a
     * still crystal holds its light still too.
     *
     * <p>Rings, sparks, flames, bolts, wings, the vortex and the souls face the camera; the curtains,
     * the sigil and the crown stand in the world and
     * go out both ways round. A face-on piece in a level is wound to face the camera and sent once,
     * as the halo is; a preview's pose may be mirrored, and there both windings go out.</p>
     */
    private record Extras(CrystalGlowStyle style, CrystalAppearance look, float age, float power, Vector3f right, Vector3f up)
            implements SubmitNodeCollector.CustomGeometryRenderer {
        @Override
        public void render(PoseStack.Pose pose, VertexConsumer buffer) {
            Matrix4f base = pose.pose();
            boolean world = CrystalGlowRenderer.translationOnly(base);
            float size = CrystalPose.size(this.look);
            float centre = CrystalPose.centreHeight(this.age, this.look);
            int colour = this.look.haloColor();
            int hot = CrystalGlowMath.hotColor(colour);
            // Light added on the dark washes out toward grey: these pieces are thin, so they take the
            // glow's colours at full saturation, the outer frame's as the deeper one.
            int deep = vivid(shine(this.look.outerColor, this.look));
            colour = vivid(colour);
            // Past 100% the light grows denser, not much brighter: it is already the glow's colour.
            float gain = Math.min(1.0F, this.power) + 0.3F * Math.max(0.0F, this.power - 1.0F);
            switch (this.style) {
                case HEARTBEAT -> heartbeat(buffer, base, world, centre, size, colour, deep, gain);
                case AURORA -> aurora(buffer, base, world, centre, size, colour, deep, gain);
                case ORBIT -> orbit(buffer, base, world, centre, size, colour, deep, gain);
                case FLAMES -> flames(buffer, base, world, centre, size, colour, deep, gain);
                case SIGIL -> sigil(buffer, base, size, colour, hot, gain);
                case STORM -> storm(buffer, base, world, centre, size, colour, hot, gain);
                case WINGS -> wings(buffer, base, world, centre, size, colour, deep, gain);
                case CROWN -> crown(buffer, base, world, centre, size, colour, hot, gain);
                case VORTEX -> vortex(buffer, base, world, centre, size, colour, deep, hot, gain);
                case SOULS -> souls(buffer, base, world, centre, size, colour, deep, hot, gain);
                default -> {
                }
            }
        }

        /** Each beat sends a soft ring out from the crystal, the second fainter. */
        private void heartbeat(VertexConsumer buffer, Matrix4f base, boolean world, float centre, float size, int colour,
                int deep, float gain) {
            float reach = Settings.RINGS.factor();
            if (reach <= 0.0F) {
                return;
            }
            float ticks = beatTicks();
            float phase = (this.age % ticks + ticks) % ticks / ticks;
            facing(base, 0.0F, centre, 0.0F);
            for (int ring = 0; ring < 2; ring++) {
                float local = (phase - 0.04F - ring * 0.2F) / 0.55F;
                if (local <= 0.0F || local >= 1.0F) {
                    continue;
                }
                float fading = (1.0F - local) * (1.0F - local);
                ring(buffer, world, centre, size * (0.5F + 0.9F * local * reach), size * (0.05F + 0.1F * local), ring == 0 ? colour : deep,
                        gain * (ring == 0 ? 0.95F : 0.7F) * fading);
            }
        }

        /** Curtains of light round the crystal, waving up and down, running through the glow's colours. */
        private void aurora(VertexConsumer buffer, Matrix4f base, boolean world, float centre, float size, int colour,
                int deep, float gain) {
            float time = this.age * Settings.AURORA_SPEED.factor();
            float tall = size * Settings.AURORA_HEIGHT.factor();
            float spin = time * 0.012F;
            float bottom = centre - size * 0.9F;
            for (int index = 0; index < SEGMENTS; index++) {
                float a0 = spin + index * (float) (Math.PI * 2.0D / SEGMENTS);
                float a1 = a0 + (float) (Math.PI * 2.0D / SEGMENTS);
                float r0 = size * (1.2F + 0.08F * (float) Math.sin(time * 0.1F + index));
                float r1 = size * (1.2F + 0.08F * (float) Math.sin(time * 0.1F + index + 1));
                float h0 = curtain(time, index, tall);
                float h1 = curtain(time, index + 1, tall);
                // A strip seen edge-on stacks its light into a thin bright line: it fades as it turns away.
                float middle = (a0 + a1) / 2.0F;
                base.transformPosition((float) Math.cos(middle) * r0, bottom + h0 / 2.0F, (float) Math.sin(middle) * r0, POINT);
                base.transformDirection((float) Math.cos(middle), 0.0F, (float) Math.sin(middle), NORMAL).normalize();
                float facing = world ? Math.abs(NORMAL.dot(POINT)) / Math.max(1.0E-4F, POINT.length()) : Math.abs(NORMAL.z);
                float turned = Math.max(0.0F, Math.min(1.0F, (facing - 0.12F) / 0.5F));
                float faceOn = turned * turned * (3.0F - 2.0F * turned);
                float l0 = Math.min(1.0F, gain * 1.4F) * shimmer(time, index) * faceOn;
                float l1 = Math.min(1.0F, gain * 1.4F) * shimmer(time, index + 1) * faceOn;
                int shade = CrystalTheme.lerp(deep, colour, 0.5F + 0.5F * (float) Math.sin(time * 0.05F + index * 0.4F));
                float x0 = (float) Math.cos(a0) * r0, z0 = (float) Math.sin(a0) * r0;
                float x1 = (float) Math.cos(a1) * r1, z1 = (float) Math.sin(a1) * r1;
                // Dark at the foot and at the top, brightest a third of the way up: no edge shows
                // wherever the curtain ends, however the crystal floats.
                float w0 = bottom + h0 * 0.32F;
                float w1 = bottom + h1 * 0.32F;
                standing(buffer, base, world, shade, x0, bottom, z0, 0.0F, x1, bottom, z1, 0.0F, x1, w1, z1, l1);
                standing(buffer, base, world, shade, x0, bottom, z0, 0.0F, x1, w1, z1, l1, x0, w0, z0, l0);
                standing(buffer, base, world, shade, x0, w0, z0, l0, x1, w1, z1, l1, x1, bottom + h1, z1, 0.0F);
                standing(buffer, base, world, shade, x0, w0, z0, l0, x1, bottom + h1, z1, 0.0F, x0, bottom + h0, z0, 0.0F);
            }
        }

        private static float curtain(float time, int index, float size) {
            return size * (1.6F + 0.5F * (float) Math.sin(time * 0.07F + index * 0.55F)
                    + 0.25F * (float) Math.sin(time * 0.13F - index * 1.3F));
        }

        /** Streaks: each strip of curtain brightens and dims on its own, as an aurora's rays do. */
        private static float shimmer(float time, int index) {
            float wave = 0.5F + 0.5F * (float) Math.sin(time * 0.09F + index * 0.8F);
            return 0.15F + 0.85F * wave * wave;
        }

        /** Two sparks on each of its tilted orbits, each with a trail of fainter, smaller ones. */
        private void orbit(VertexConsumer buffer, Matrix4f base, boolean world, float centre, float size, int colour,
                int deep, float gain) {
            float radius = size * 0.95F;
            int paths = Settings.ORBITS.value();
            int trails = Settings.TRAIL.value();
            float speed = Settings.ORBIT_SPEED.factor();
            for (int path = 0; path < paths; path++) {
                float tilt = 0.55F;
                float node = path * (float) (Math.PI * 2.0D / paths);
                float way = path % 2 == 0 ? 1.0F : -1.0F;
                for (int spark = 0; spark < 2; spark++) {
                    float angle = this.age * (0.1F + 0.03F * path) * speed * way + spark * (float) Math.PI + path * 1.3F;
                    for (int trail = trails; trail >= 0; trail--) {
                        float behind = trail / (float) (trails + 1);
                        float along = angle - trail * 0.16F * way;
                        float flatX = (float) Math.cos(along);
                        float flatZ = (float) Math.sin(along);
                        float y = -flatZ * (float) Math.sin(tilt);
                        float z = flatZ * (float) Math.cos(tilt);
                        float x = flatX * (float) Math.cos(node) + z * (float) Math.sin(node);
                        float turnedZ = -flatX * (float) Math.sin(node) + z * (float) Math.cos(node);
                        facing(base, x * radius, centre + y * radius, turnedZ * radius);
                        disc(buffer, world, centre + y * radius, size * 0.1F * (1.0F - 0.8F * behind), trail == 0 ? colour : deep,
                                Math.min(1.0F, gain * (trail == 0 ? 1.3F : 0.8F * (1.0F - behind))));
                    }
                }
            }
        }

        /** Tongues of flame rising behind the crystal, flickering, hot at the root. */
        private void flames(VertexConsumer buffer, Matrix4f base, boolean world, float centre, float size, int colour,
                int deep, float gain) {
            // Behind the model, away from the camera, so the frames do not cut the flames into pieces.
            facingBehind(base, centre, 0.9F * size);
            float height = centre;
            int tongues = Settings.TONGUES.value();
            float half = Math.max(1.0F, (tongues - 1) / 2.0F);
            float time = this.age * Settings.FLAME_SPEED.factor();
            float climb = Settings.FLAME_HEIGHT.factor();
            for (int tongue = 0; tongue < tongues; tongue++) {
                float root = (tongue - half) / half * 1.05F * size;
                float flicker = 0.5F + 0.5F * (float) Math.sin(time * 0.21F * (1.0F + 0.13F * tongue) + tongue * 2.3F);
                // The middle ones climb highest, past the top of the crystal.
                float reach = size * climb * (2.0F + 0.7F * flicker) * (1.0F - 0.35F * Math.abs(tongue - half) / half);
                float start = -0.7F * size;
                float lastS = root;
                float lastT = start;
                float lastWidth = size * 0.17F;
                // The root starts dark and catches within a sixth of the way: no straight line at its foot.
                float lastLight = 0.0F;
                for (int step = 1; step <= 6; step++) {
                    float f = step / 6.0F;
                    float s = root * (1.0F - 0.45F * f) + (float) Math.sin(time * 0.33F + tongue * 1.9F + f * 4.0F) * 0.1F * size * f;
                    float t = start + reach * f;
                    float width = size * 0.17F * (1.0F - f);
                    float light = Math.min(1.0F, gain * 1.6F) * (1.0F - f) * Math.min(1.0F, f * 6.0F);
                    int shade = step <= 3 ? colour : deep;
                    // Lit along its middle, dark at its edges.
                    flatTriangle(buffer, world, height, shade, lastS - lastWidth, lastT, 0.0F, lastS, lastT, lastLight, s, t, light);
                    flatTriangle(buffer, world, height, shade, lastS - lastWidth, lastT, 0.0F, s, t, light, s - width, t, 0.0F);
                    flatTriangle(buffer, world, height, shade, lastS, lastT, lastLight, lastS + lastWidth, lastT, 0.0F, s + width, t, 0.0F);
                    flatTriangle(buffer, world, height, shade, lastS, lastT, lastLight, s + width, t, 0.0F, s, t, light);
                    lastS = s;
                    lastT = t;
                    lastWidth = width;
                    lastLight = light;
                }
            }
        }

        /** A sigil of light on the ground under the crystal: two rings, a star and runes, turning and pulsing. */
        private void sigil(VertexConsumer buffer, Matrix4f base, float crystal, int colour, int hot, float gain) {
            float size = crystal * Settings.SIGIL_SIZE.factor();
            float spin = Settings.SIGIL_SPIN.factor();
            int points = Settings.POINTS.value();
            // A star drawn in one line: each point to the one this many along, never back to a polygon.
            int skip = points % 2 == 1 ? (points - 1) / 2 : points / 2 - 1;
            float light = gain * (0.65F + 0.35F * (float) Math.sin(this.age * 0.12F));
            float y = 0.02F;
            groundRing(buffer, base, y, size * 1.15F, size * 0.05F, colour, light * 0.8F);
            groundRing(buffer, base, y, size * 0.88F, size * 0.035F, hot, light * 0.7F);
            float turn = this.age * 0.02F * spin;
            float star = size * 0.86F;
            for (int point = 0; point < points; point++) {
                float a0 = turn + point * (float) (Math.PI * 2.0D / points);
                float a1 = turn + (point + skip) * (float) (Math.PI * 2.0D / points);
                groundLine(buffer, base, y, (float) Math.cos(a0) * star, (float) Math.sin(a0) * star, (float) Math.cos(a1) * star,
                        (float) Math.sin(a1) * star, size * 0.025F, colour, light * 0.6F);
            }
            for (int rune = 0; rune < 10; rune++) {
                float angle = -this.age * 0.03F * spin + rune * (float) (Math.PI * 2.0D / 10.0D);
                float cos = (float) Math.cos(angle);
                float sin = (float) Math.sin(angle);
                groundLine(buffer, base, y, cos * size * 0.94F, sin * size * 0.94F, cos * size * 1.09F, sin * size * 1.09F, size * 0.02F,
                        hot, light * 0.5F);
            }
        }

        /** Bolts of light out of the crystal: each strikes on a beat of its own, forks once, flickers and dies. */
        private void storm(VertexConsumer buffer, Matrix4f base, boolean world, float centre, float size, int colour, int hot,
                float gain) {
            int bolts = Settings.BOLTS.value();
            float rate = Settings.STRIKES.factor();
            float reach = Settings.BOLT_REACH.factor();
            facing(base, 0.0F, centre, 0.0F);
            for (int bolt = 0; bolt < bolts; bolt++) {
                // About every 18 ticks at 100%, each bolt out of step with the others.
                float clock = this.age * rate / 18.0F + bolt * 0.618034F;
                int strike = (int) Math.floor(clock);
                float phase = clock - strike;
                if (phase > 0.6F) {
                    continue;
                }
                float fade = 1.0F - phase / 0.6F;
                float flicker = 0.75F + 0.25F * Math.abs((float) Math.sin(phase * 40.0F));
                float light = Math.min(1.0F, gain * 2.2F) * fade * (0.4F + 0.6F * fade) * flicker;
                int seed = bolt * 7919 + strike;
                float angle = noise(seed, 1) * (float) (Math.PI * 2.0D);
                float length = size * reach * (0.8F + 0.6F * noise(seed, 2));
                float from = size * 0.42F;
                if (phase < 0.1F) {
                    // The crystal lights up as it strikes.
                    disc(buffer, world, centre, size * 0.5F, colour, light * 0.35F);
                }
                jag(buffer, world, centre, (float) Math.cos(angle) * from, (float) Math.sin(angle) * from, angle, length, size,
                        seed, 7, colour, hot, light, size * 0.08F, true);
            }
        }

        /**
         * A jagged bolt of light from (s0, t0) along {@code angle}: a hot core in a wider glow, off to
         * either side at every joint but straight at its tip, and once, halfway, a shorter fork.
         */
        private void jag(VertexConsumer buffer, boolean world, float height, float s0, float t0, float angle, float length,
                float size, int seed, int steps, int colour, int hot, float light, float width, boolean fork) {
            float cos = (float) Math.cos(angle);
            float sin = (float) Math.sin(angle);
            RIBBON_S[0] = s0;
            RIBBON_T[0] = t0;
            RIBBON_W[0] = width;
            RIBBON_L[0] = light;
            for (int step = 1; step <= steps; step++) {
                float f = step / (float) steps;
                float along = length * f;
                float side = step == steps ? 0.0F : (noise(seed, step + 10) - 0.5F) * size * 0.28F;
                RIBBON_S[step] = s0 + cos * along - sin * side;
                RIBBON_T[step] = t0 + sin * along + cos * side;
                // Thinning and going out toward its tip: it ends in nothing, never in a cut.
                RIBBON_W[step] = width * (1.0F - 0.8F * f);
                RIBBON_L[step] = light * (1.0F - ease(f));
            }
            // Where the fork leaves, kept before a fork's own points take the scratch.
            int middle = steps / 2;
            float forkS = RIBBON_S[middle];
            float forkT = RIBBON_T[middle];
            float forkLight = RIBBON_L[middle];
            ribbon(buffer, world, height, colour, steps + 1, 3.0F, 0.7F);
            ribbon(buffer, world, height, hot, steps + 1, 1.0F, 1.0F);
            if (fork) {
                float turn = noise(seed, 3) < 0.5F ? 0.65F : -0.65F;
                jag(buffer, world, height, forkS, forkT, angle + turn, length * 0.45F, size, seed * 31 + 7, 4, colour, hot,
                        forkLight * 0.8F, width * 0.7F, false);
            }
        }

        /**
         * Two wings of light behind the crystal, beating together: a bright leading edge arching from
         * each shoulder out to its tip, feathers hanging from it, the longest at the tip.
         */
        private void wings(VertexConsumer buffer, Matrix4f base, boolean world, float centre, float size, int colour, int deep,
                float gain) {
            float span = size * Settings.SPAN.factor();
            int feathers = Settings.FEATHERS.value();
            float lift = 0.25F * (float) Math.sin(this.age * 0.09F * Settings.WING_BEAT.factor());
            float cos = (float) Math.cos(lift);
            float sin = (float) Math.sin(lift);
            float bright = Math.min(1.0F, gain * 1.9F);
            float shoulderS = 0.22F * size;
            float shoulderT = 0.45F * size;
            facingBehind(base, centre, 0.8F * size);
            for (int side = -1; side <= 1; side += 2) {
                // The edge, worked out for the right wing, raised by the beat about the shoulder, mirrored for the left.
                for (int step = 0; step <= 8; step++) {
                    float u = step / 8.0F;
                    float ex = bezier(0.45F, 1.2F, u) * span;
                    float ey = bezier(0.8F, 0.45F, u) * span;
                    RIBBON_S[step] = side * (shoulderS + ex * cos - ey * sin);
                    RIBBON_T[step] = shoulderT + ex * sin + ey * cos;
                    RIBBON_W[step] = size * 0.045F * (1.0F - 0.85F * u);
                    RIBBON_L[step] = bright * (1.0F - ease(u));
                }
                ribbon(buffer, world, centre, colour, 9, 1.0F, 1.0F);
                for (int feather = 0; feather < feathers; feather++) {
                    float u = (feather + 0.5F) / feathers;
                    float ex = bezier(0.45F, 1.2F, u) * span;
                    float ey = bezier(0.8F, 0.45F, u) * span;
                    // Hanging down from the edge, swept further out toward the tip.
                    float theta = 0.2F + 0.7F * u;
                    float dx = (float) Math.sin(theta);
                    float dy = -(float) Math.cos(theta);
                    feather(buffer, world, centre, side * (shoulderS + ex * cos - ey * sin), shoulderT + ex * sin + ey * cos,
                            side * (dx * cos - dy * sin), dx * sin + dy * cos, span * (0.35F + 0.45F * u), size * (0.13F + 0.05F * u),
                            u < 0.5F ? deep : colour, bright * (0.7F + 0.3F * u));
                }
            }
        }

        /** A curve from 0 through {@code middle} to {@code end}, {@code u} of the way along. */
        private static float bezier(float middle, float end, float u) {
            return 2.0F * (1.0F - u) * u * middle + u * u * end;
        }

        /** A feather of light from (s0, t0) along (ds, dt): widest a third of the way, drooping a little, tapering to its tip. */
        private void feather(VertexConsumer buffer, boolean world, float height, float s0, float t0, float ds, float dt, float length,
                float width, int colour, float light) {
            float lastS = s0;
            float lastT = t0;
            float lastWidth = width * (float) Math.sin(Math.PI * 0.2D);
            float lastLight = 0.0F;
            for (int step = 1; step <= 5; step++) {
                float f = step / 5.0F;
                float s = s0 + ds * length * f;
                float t = t0 + dt * length * f - length * 0.12F * f * f;
                float stepWidth = width * (float) Math.sin(Math.PI * (0.2F + 0.8F * f));
                float stepLight = light * Math.min(1.0F, f * 5.0F) * (1.0F - 0.6F * f);
                band(buffer, world, height, colour, lastS, lastT, lastWidth, lastLight, s, t, stepWidth, stepLight, -dt, ds);
                lastS = s;
                lastT = t;
                lastWidth = stepWidth;
                lastLight = stepLight;
            }
        }

        /** A crown of light floating above the crystal: a level band, thorns standing up from it, a gem on each tall one. */
        private void crown(VertexConsumer buffer, Matrix4f base, boolean world, float centre, float size, int colour, int hot,
                float gain) {
            int thorns = Settings.THORNS.value();
            float turn = this.age * 0.025F * Settings.CROWN_SPIN.factor();
            float y = centre + size * (0.62F + 0.5F * Settings.CROWN_HEIGHT.factor());
            float radius = size * 0.5F;
            float light = Math.min(1.0F, gain * 1.8F) * (0.8F + 0.2F * (float) Math.sin(this.age * 0.1F));
            groundRing(buffer, base, y, radius, size * 0.05F, colour, light);
            float half = (float) (Math.PI / thorns) * 0.55F;
            for (int thorn = 0; thorn < thorns; thorn++) {
                float angle = turn + thorn * (float) (Math.PI * 2.0D / thorns);
                boolean tall = thorn % 2 == 0;
                float top = y + size * (tall ? 0.5F : 0.32F);
                float cos = (float) Math.cos(angle);
                float sin = (float) Math.sin(angle);
                float tipX = cos * radius * 1.08F;
                float tipZ = sin * radius * 1.08F;
                int shade = colour;
                // Two blades crossed, round the band and across it, so a thorn seen edge on still shows.
                standing(buffer, base, world, shade, (float) Math.cos(angle - half) * radius, y, (float) Math.sin(angle - half) * radius,
                        light, (float) Math.cos(angle + half) * radius, y, (float) Math.sin(angle + half) * radius, light, tipX, top, tipZ, 0.0F);
                standing(buffer, base, world, shade, cos * radius * 0.88F, y, sin * radius * 0.88F, light, cos * radius * 1.12F, y,
                        sin * radius * 1.12F, light, tipX, top, tipZ, 0.0F);
                if (tall) {
                    facing(base, tipX, top, tipZ);
                    disc(buffer, world, top, size * 0.05F, hot, light);
                }
            }
        }

        /** A spiral of the void behind the crystal: its arms wind in toward it, motes of light falling down them. */
        private void vortex(VertexConsumer buffer, Matrix4f base, boolean world, float centre, float size, int colour, int deep,
                int hot, float gain) {
            int arms = Settings.ARMS.value();
            float spin = -this.age * 0.035F * Settings.VORTEX_SPIN.factor();
            float pull = Settings.PULL.factor();
            float bright = Math.min(1.0F, gain * 2.0F);
            facingBehind(base, centre, 0.6F * size);
            for (int arm = 0; arm < arms; arm++) {
                float start = spin + arm * (float) (Math.PI * 2.0D / arms);
                for (int step = 0; step <= 16; step++) {
                    float u = step / 16.0F;
                    RIBBON_S[step] = spiralS(start, u, size);
                    RIBBON_T[step] = spiralT(start, u, size);
                    // Born out of nothing far out, brightest near the crystal, gone in its heart: no end is cut.
                    float born = ease(u / 0.3F);
                    RIBBON_W[step] = size * (0.22F - 0.15F * u) * (0.3F + 0.7F * born);
                    RIBBON_L[step] = bright * born * (0.25F + 0.75F * u) * (1.0F - u * u * u);
                }
                // A wide glow, and a bright core along it.
                ribbon(buffer, world, centre, deep, 17, 1.0F, 0.55F);
                ribbon(buffer, world, centre, colour, 17, 0.35F, 1.0F);
                if (pull > 0.0F) {
                    for (int mote = 0; mote < 3; mote++) {
                        float u = (this.age * 0.008F * pull + mote / 3.0F + arm * 0.37F) % 1.0F;
                        // Born with the arm, out of nothing: no spark pops up at its far end.
                        float light = bright * ease(u / 0.3F) * (1.0F - u * u);
                        disc(buffer, world, centre, spiralS(start, u, size), spiralT(start, u, size), size * 0.08F * (1.3F - 0.6F * u),
                                hot, light);
                    }
                }
            }
        }

        /** Where an arm of the vortex is, {@code u} of the way in from its outer end. */
        private static float spiralS(float start, float u, float size) {
            return (float) Math.cos(start + u * 2.6F) * spiralRadius(u, size);
        }

        private static float spiralT(float start, float u, float size) {
            return (float) Math.sin(start + u * 2.6F) * spiralRadius(u, size);
        }

        private static float spiralRadius(float u, float size) {
            return size * (1.55F - 1.3F * u);
        }

        /** Souls of light rising round the crystal on a slow spiral, swaying, each trailing a tail, fading as they go. */
        private void souls(VertexConsumer buffer, Matrix4f base, boolean world, float centre, float size, int colour, int deep,
                int hot, float gain) {
            int souls = Settings.SOUL_COUNT.value();
            float rise = Settings.RISE.factor();
            float sway = Settings.SWAY.factor();
            for (int soul = 0; soul < souls; soul++) {
                // 0 when it leaves the ground, 1 when it is gone, well above the crystal.
                float life = (this.age * 0.006F * rise + soul / (float) souls) % 1.0F;
                float angle = soul * 2.39996F + life * 2.6F;
                float radius = size * (0.95F - 0.25F * life);
                float y = size * (0.1F + 2.4F * life);
                float drift = sway * size * 0.12F * (float) Math.sin(this.age * 0.07F + soul * 1.7F);
                float light = Math.min(1.0F, gain * 2.0F) * Math.min(1.0F, life / 0.15F) * Math.min(1.0F, (1.0F - life) / 0.35F);
                facing(base, (float) Math.cos(angle) * radius, y, (float) Math.sin(angle) * radius);
                disc(buffer, world, y, drift, 0.0F, size * 0.36F, colour, light * 0.45F);
                disc(buffer, world, y, drift, 0.0F, size * 0.16F, hot, light);
                float lastS = drift;
                float lastT = 0.0F;
                float lastWidth = size * 0.12F;
                float lastLight = light * 0.8F;
                for (int step = 1; step <= 5; step++) {
                    float f = step / 5.0F;
                    float s = drift * (1.0F - f) + sway * size * 0.08F * (float) Math.sin(this.age * 0.15F + soul + f * 3.0F);
                    float t = -size * 0.75F * f;
                    float stepWidth = size * 0.12F * (1.0F - f);
                    float stepLight = light * 0.8F * (1.0F - f);
                    // The tail hangs down: its width runs level.
                    band(buffer, world, y, deep, lastS, lastT, lastWidth, lastLight, s, t, stepWidth, stepLight, 1.0F, 0.0F);
                    lastS = s;
                    lastT = t;
                    lastWidth = stepWidth;
                    lastLight = stepLight;
                }
            }
        }

        /** A ribbon's points, sideways directions, widths and light: the render thread's alone, made once. */
        private static final int RIBBON_MAX = 24;
        private static final float[] RIBBON_S = new float[RIBBON_MAX];
        private static final float[] RIBBON_T = new float[RIBBON_MAX];
        private static final float[] RIBBON_W = new float[RIBBON_MAX];
        private static final float[] RIBBON_L = new float[RIBBON_MAX];
        private static final float[] RIBBON_NS = new float[RIBBON_MAX];
        private static final float[] RIBBON_NT = new float[RIBBON_MAX];

        /** 0 to 1 over 0 to 1, flat at both ends: light that comes and goes without an edge. */
        private static float ease(float x) {
            float t = Math.max(0.0F, Math.min(1.0F, x));
            return t * t * (3.0F - 2.0F * t);
        }

        /** A steady number in [0, 1) for two whole numbers: the same pair, the same number, every frame. */
        private static float noise(int a, int b) {
            int hash = a * 0x27D4EB2D ^ b * 0x165667B1;
            hash ^= hash >>> 15;
            hash *= 0x85EBCA77;
            hash ^= hash >>> 13;
            return (hash & 0xFFFFFF) / (float) 0x1000000;
        }

        // ------------------------------------------------------------------------------------------
        // Pieces facing the camera, about a point set with facing()
        // ------------------------------------------------------------------------------------------

        /** Places the pieces that follow about the local point (x, y, z), turned to the camera. */
        private void facing(Matrix4f base, float x, float y, float z) {
            base.transformPosition(x, y, z, CENTRE);
            base.transformDirection(this.right.x, this.right.y, this.right.z, AXIS_RIGHT);
            base.transformDirection(this.up.x, this.up.y, this.up.z, AXIS_UP);
        }

        /**
         * Places the pieces that follow {@code distance} blocks behind the crystal's middle, away from
         * the camera: straight back along the ground, or a camera looking down would sink them into it.
         */
        private void facingBehind(Matrix4f base, float centre, float distance) {
            float awayX = this.up.y * this.right.z - this.up.z * this.right.y;
            float awayZ = this.up.x * this.right.y - this.up.y * this.right.x;
            float flat = (float) Math.sqrt(awayX * awayX + awayZ * awayZ);
            float back = flat < 1.0E-3F ? 0.0F : distance / flat;
            facing(base, awayX * back, centre, awayZ * back);
        }

        /**
         * A band of light from (s0, t0) to (s1, t1), {@code w0} and {@code w1} wide each way along the
         * unit (ps, pt): lit along its middle, dark at its edges.
         */
        private void band(VertexConsumer buffer, boolean world, float height, int colour, float s0, float t0, float w0, float l0,
                float s1, float t1, float w1, float l1, float ps, float pt) {
            flatTriangle(buffer, world, height, colour, s0 + ps * w0, t0 + pt * w0, 0.0F, s0, t0, l0, s1, t1, l1);
            flatTriangle(buffer, world, height, colour, s0 + ps * w0, t0 + pt * w0, 0.0F, s1, t1, l1, s1 + ps * w1, t1 + pt * w1, 0.0F);
            flatTriangle(buffer, world, height, colour, s0, t0, l0, s0 - ps * w0, t0 - pt * w0, 0.0F, s1 - ps * w1, t1 - pt * w1, 0.0F);
            flatTriangle(buffer, world, height, colour, s0, t0, l0, s1 - ps * w1, t1 - pt * w1, 0.0F, s1, t1, l1);
        }

        /**
         * A band of light through the first {@code count} points set in the ribbon's arrays, lit along
         * its middle, dark at its edges, {@code widthScale} and {@code lightScale} times as set. Each
         * point has one sideways direction, its bend's, shared by the pieces on either side of it: the
         * band shows no seam where it turns.
         */
        private void ribbon(VertexConsumer buffer, boolean world, float height, int colour, int count, float widthScale, float lightScale) {
            for (int i = 0; i < count; i++) {
                int before = Math.max(0, i - 1);
                int after = Math.min(count - 1, i + 1);
                float ds = RIBBON_S[after] - RIBBON_S[before];
                float dt = RIBBON_T[after] - RIBBON_T[before];
                float length = Math.max(1.0E-4F, (float) Math.sqrt(ds * ds + dt * dt));
                RIBBON_NS[i] = -dt / length;
                RIBBON_NT[i] = ds / length;
            }
            for (int i = 0; i + 1 < count; i++) {
                float s0 = RIBBON_S[i];
                float t0 = RIBBON_T[i];
                float s1 = RIBBON_S[i + 1];
                float t1 = RIBBON_T[i + 1];
                float l0 = RIBBON_L[i] * lightScale;
                float l1 = RIBBON_L[i + 1] * lightScale;
                float a0 = RIBBON_NS[i] * RIBBON_W[i] * widthScale;
                float b0 = RIBBON_NT[i] * RIBBON_W[i] * widthScale;
                float a1 = RIBBON_NS[i + 1] * RIBBON_W[i + 1] * widthScale;
                float b1 = RIBBON_NT[i + 1] * RIBBON_W[i + 1] * widthScale;
                flatTriangle(buffer, world, height, colour, s0 + a0, t0 + b0, 0.0F, s0, t0, l0, s1, t1, l1);
                flatTriangle(buffer, world, height, colour, s0 + a0, t0 + b0, 0.0F, s1, t1, l1, s1 + a1, t1 + b1, 0.0F);
                flatTriangle(buffer, world, height, colour, s0, t0, l0, s0 - a0, t0 - b0, 0.0F, s1 - a1, t1 - b1, 0.0F);
                flatTriangle(buffer, world, height, colour, s0, t0, l0, s1 - a1, t1 - b1, 0.0F, s1, t1, l1);
            }
        }

        /** A soft ring: dark at both edges, lit along its middle. */
        private void ring(VertexConsumer buffer, boolean world, float height, float radius, float width, int colour, float light) {
            float inner = radius - width;
            float outer = radius + width;
            for (int index = 0; index < SEGMENTS; index++) {
                float c0 = COS[index], s0 = SIN[index], c1 = COS[index + 1], s1 = SIN[index + 1];
                flatTriangle(buffer, world, height, colour, inner * c0, inner * s0, 0.0F, radius * c0, radius * s0, light, radius * c1, radius * s1, light);
                flatTriangle(buffer, world, height, colour, inner * c0, inner * s0, 0.0F, radius * c1, radius * s1, light, inner * c1, inner * s1, 0.0F);
                flatTriangle(buffer, world, height, colour, radius * c0, radius * s0, light, outer * c0, outer * s0, 0.0F, outer * c1, outer * s1, 0.0F);
                flatTriangle(buffer, world, height, colour, radius * c0, radius * s0, light, outer * c1, outer * s1, 0.0F, radius * c1, radius * s1, light);
            }
        }

        /** A soft round spark: lit in the middle, dark at the rim. */
        private void disc(VertexConsumer buffer, boolean world, float height, float radius, int colour, float light) {
            disc(buffer, world, height, 0.0F, 0.0F, radius, colour, light);
        }

        /** A soft round spark at (s, t) from the point set last. */
        private void disc(VertexConsumer buffer, boolean world, float height, float s, float t, float radius, int colour, float light) {
            for (int index = 0; index < SEGMENTS; index += 3) {
                int next = Math.min(SEGMENTS, index + 3);
                flatTriangle(buffer, world, height, colour, s, t, light, s + radius * COS[index], t + radius * SIN[index], 0.0F,
                        s + radius * COS[next], t + radius * SIN[next], 0.0F);
            }
        }

        private void flatTriangle(VertexConsumer buffer, boolean world, float height, int colour, float as, float at, float aa,
                float bs, float bt, float ba, float cs, float ct, float ca) {
            boolean counterClockwise = (bs - as) * (ct - at) - (bt - at) * (cs - as) >= 0.0F;
            if (!world || counterClockwise) {
                flat(buffer, world, height, colour, as, at, aa);
                flat(buffer, world, height, colour, bs, bt, ba);
                flat(buffer, world, height, colour, cs, ct, ca);
            }
            if (!world || !counterClockwise) {
                flat(buffer, world, height, colour, cs, ct, ca);
                flat(buffer, world, height, colour, bs, bt, ba);
                flat(buffer, world, height, colour, as, at, aa);
            }
        }

        /** A vertex (s, t) blocks along the camera's right and up from the point set last. */
        private void flat(VertexConsumer buffer, boolean world, float height, int colour, float s, float t, float light) {
            float fade = world ? groundFade(height + s * this.right.y + t * this.up.y) : 1.0F;
            buffer.addVertex(CENTRE.x + s * AXIS_RIGHT.x + t * AXIS_UP.x, CENTRE.y + s * AXIS_RIGHT.y + t * AXIS_UP.y,
                            CENTRE.z + s * AXIS_RIGHT.z + t * AXIS_UP.z)
                    .setColor(colour >> 16 & 255, colour >> 8 & 255, colour & 255, CrystalGlowMath.alpha(light * fade));
        }

        // ------------------------------------------------------------------------------------------
        // Pieces standing in the world, in the crystal's own blocks from its feet
        // ------------------------------------------------------------------------------------------

        private static void standing(VertexConsumer buffer, Matrix4f base, boolean world, int colour, float ax, float ay, float az,
                float aa, float bx, float by, float bz, float ba, float cx, float cy, float cz, float ca) {
            // Seen from either side: both windings, and the material keeps the one facing the camera.
            placed(buffer, base, world, colour, ax, ay, az, aa);
            placed(buffer, base, world, colour, bx, by, bz, ba);
            placed(buffer, base, world, colour, cx, cy, cz, ca);
            placed(buffer, base, world, colour, cx, cy, cz, ca);
            placed(buffer, base, world, colour, bx, by, bz, ba);
            placed(buffer, base, world, colour, ax, ay, az, aa);
        }

        private static void placed(VertexConsumer buffer, Matrix4f base, boolean world, int colour, float x, float y, float z, float light) {
            buffer.addVertex(base, x, y, z).setColor(colour >> 16 & 255, colour >> 8 & 255, colour & 255,
                    CrystalGlowMath.alpha(light * (world ? groundFade(y) : 1.0F)));
        }

        /** A soft ring lying on the ground. */
        private static void groundRing(VertexConsumer buffer, Matrix4f base, float y, float radius, float width, int colour, float light) {
            float inner = radius - width;
            float outer = radius + width;
            for (int index = 0; index < SEGMENTS; index++) {
                float c0 = COS[index], s0 = SIN[index], c1 = COS[index + 1], s1 = SIN[index + 1];
                lying(buffer, base, colour, y, inner * c0, inner * s0, 0.0F, radius * c0, radius * s0, light, radius * c1, radius * s1, light);
                lying(buffer, base, colour, y, inner * c0, inner * s0, 0.0F, radius * c1, radius * s1, light, inner * c1, inner * s1, 0.0F);
                lying(buffer, base, colour, y, radius * c0, radius * s0, light, outer * c0, outer * s0, 0.0F, outer * c1, outer * s1, 0.0F);
                lying(buffer, base, colour, y, radius * c0, radius * s0, light, outer * c1, outer * s1, 0.0F, radius * c1, radius * s1, light);
            }
        }

        /** A soft line lying on the ground, from (x0, z0) to (x1, z1). */
        private static void groundLine(VertexConsumer buffer, Matrix4f base, float y, float x0, float z0, float x1, float z1,
                float width, int colour, float light) {
            float dx = x1 - x0;
            float dz = z1 - z0;
            float length = (float) Math.sqrt(dx * dx + dz * dz);
            if (length < 1.0E-4F) {
                return;
            }
            float px = -dz / length * width;
            float pz = dx / length * width;
            lying(buffer, base, colour, y, x0 + px, z0 + pz, 0.0F, x0, z0, light, x1, z1, light);
            lying(buffer, base, colour, y, x0 + px, z0 + pz, 0.0F, x1, z1, light, x1 + px, z1 + pz, 0.0F);
            lying(buffer, base, colour, y, x0, z0, light, x0 - px, z0 - pz, 0.0F, x1 - px, z1 - pz, 0.0F);
            lying(buffer, base, colour, y, x0, z0, light, x1 - px, z1 - pz, 0.0F, x1, z1, light);
        }

        private static void lying(VertexConsumer buffer, Matrix4f base, int colour, float y, float ax, float az, float aa,
                float bx, float bz, float ba, float cx, float cz, float ca) {
            // Lies flat: it is seen from above in a level, and both ways round in a mirrored preview.
            standing(buffer, base, false, colour, ax, y, az, aa, bx, y, bz, ba, cx, y, cz, ca);
        }

        /** How much light a point keeps at {@code height} blocks above the crystal's feet: none at the ground. */
        private static float groundFade(float height) {
            float t = height / CrystalGlowBuffer.GROUND_FADE;
            if (t >= 1.0F) {
                return 1.0F;
            }
            if (t <= 0.0F) {
                return 0.0F;
            }
            return t * t * (3.0F - 2.0F * t);
        }
    }
}
