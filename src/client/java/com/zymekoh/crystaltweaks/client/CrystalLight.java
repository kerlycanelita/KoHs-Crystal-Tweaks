package com.zymekoh.crystaltweaks.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
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
        if (glow && style == CrystalGlowStyle.LAYERS && !converted) {
            collector.submitCustomGeometry(poses, CrystalGlowMaterial.layers(TEXTURE),
                    new Layers(shape, look, state.ageInTicks, power, near ? quality.auraCopies : 0, quality.innerFrame));
        }
        boolean sheets = glow && (style == CrystalGlowStyle.LIGHT || converted);
        if (sheets || blurSteps > 0) {
            RenderType material = CrystalGlowTiles.material(shape);
            if (material != null) {
                Vector3f right = orientation.transform(new Vector3f(1.0F, 0.0F, 0.0F));
                Vector3f up = orientation.transform(new Vector3f(0.0F, 1.0F, 0.0F));
                collector.submitCustomGeometry(poses, material, new Sheets(shape, look, state.ageInTicks, power, sheets,
                        look.glowActive(), blurSteps, quality.innerFrame, quality.farFaces && near, state.lightCoords,
                        right, up, !converted));
            }
        }
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
     */
    private record Sheets(CrystalShape shape, CrystalAppearance look, float age, float power, boolean lit, boolean glowing,
            int blurSteps, boolean innerFrame, boolean farFaces, int lightCoords, Vector3f right, Vector3f up, boolean framed)
            implements SubmitNodeCollector.CustomGeometryRenderer {
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
            float radius = CrystalPose.size(this.look) * ((this.framed ? 0.5F : 1.05F) + 0.11F * Math.min(3.0F, coreGlow));
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
            boolean innerFrame) implements SubmitNodeCollector.CustomGeometryRenderer {
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
            float auraPower = Math.min(1.5F, this.power);
            for (int copy = 0; copy < this.auraCopies; copy++) {
                // Fewer copies carry the same light, spread over the same reach.
                int slot = this.auraCopies == AURA_SCALE.length ? copy : copy * 2 + 1;
                float light = AURA_LIGHT[slot] * auraPower * AURA_SCALE.length / this.auraCopies;
                boxes(buffer, base, world, AURA_SCALE[slot], aura, aura, aura, light, light * Math.min(2.0F, coreGlow));
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
}
