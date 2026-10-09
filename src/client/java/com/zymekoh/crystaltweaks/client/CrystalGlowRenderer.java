package com.zymekoh.crystaltweaks.client;

import com.google.common.collect.MapMaker;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.EndCrystalRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Depth-tested additive halo and local surface spill, not duplicate models or world lighting edits. */
public final class CrystalGlowRenderer {
    /** How far behind a live crystal's centre its halo is drawn, in blocks: just past the frames. */
    private static final float BEHIND_MODEL = 0.9F;
    /** Half the width of the square of blocks the spill is sampled from, around the crystal. */
    private static final float SPILL_REACH = 2.5F;
    /**
     * The disc's tessellation by how large the light is on screen. The finest is the one every halo
     * used to get; a halo far away covers a few dozen pixels, where fewer rings and corners draw the
     * same soft falloff. Its outer corners carry almost no light, so the polygon never shows.
     */
    private static final DiskDetail[] DISK_DETAIL = {
            new DiskDetail(10, 40), new DiskDetail(8, 32), new DiskDetail(6, 24)};
    /** Apparent distances, in blocks at a 70 degree field of view, where the next coarser disc starts. */
    private static final float[] DETAIL_DISTANCE = {16F, 32F};
    /** 1 / tan(35 degrees): the projection's vertical scale at a 70 degree field of view. */
    private static final float REFERENCE_SCALE = 1.4281480F;

    public record Surface(float x0, float y, float z0, float x1, float z1) { }
    private record DiskDetail(int rings, int segments, float[] cos, float[] sin) {
        DiskDetail(int rings, int segments) {
            this(rings, segments, trigTable(segments, false), trigTable(segments, true));
        }
    }
    private record Cached(long at, Vec3 position, List<Surface> surfaces) { }
    // Weak keys compared by identity: Entity.hashCode() reads the entity id, which from 26.2 throws
    // for a crystal that was never added to a level, as client-side stand-ins are.
    private static final Map<EndCrystal, Cached> CACHE = new MapMaker().weakKeys().makeMap();
    private static long budgetTick = Long.MIN_VALUE;
    private static int sampledThisTick;

    private CrystalGlowRenderer() { }

    public static List<Surface> surfaces(EndCrystal crystal) {
        long tick = crystal.level().getGameTime();
        if (budgetTick != tick) { budgetTick = tick; sampledThisTick = 0; }
        long now = System.nanoTime();
        Cached cached = CACHE.get(crystal);
        if (cached != null && now - cached.at < 250_000_000L
                && cached.position.distanceToSqr(crystal.position()) < 0.0001) return cached.surfaces;
        // Bound terrain queries across all crystals. Never retain expired light on a removed surface.
        // A crystal that loses the budget reuses its last result rather than reporting no spill at
        // all: during a spam fight the budget runs out most ticks, and the empty answer was being
        // snapshotted into death flashes, so the same explosion lit the ground only sometimes.
        if (sampledThisTick++ >= 8) return cached != null ? cached.surfaces : List.of();
        List<Surface> surfaces = new ArrayList<>();
        Vec3 origin = crystal.position();
        Vec3 light = origin.add(0, 1, 0);
        BlockPos base = crystal.blockPosition().below();
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
            for (int dy = 1; dy >= -2; dy--) {
                BlockPos pos = base.offset(dx, dy, dz);
                if (!crystal.level().isLoaded(pos)) continue;
                var shape = crystal.level().getBlockState(pos).getShape(crystal.level(), pos);
                if (shape.isEmpty()) continue;
                // Individual boxes keep spill off gaps in stairs and non-full blocks.
                for (var box : shape.toAabbs().stream().limit(4).toList()) {
                    double y = pos.getY() + box.maxY + 0.006;
                    Vec3 target = new Vec3(pos.getX() + (box.minX + box.maxX) / 2,
                            y, pos.getZ() + (box.minZ + box.maxZ) / 2);
                    if (target.y >= light.y || target.distanceToSqr(light) > 10) continue;
                    if (crystal.level().clip(new ClipContext(light, target, ClipContext.Block.COLLIDER,
                            ClipContext.Fluid.NONE, crystal)).getType() != HitResult.Type.MISS) continue;
                    surfaces.add(new Surface((float) (pos.getX() + box.minX - origin.x),
                            (float) (y - origin.y), (float) (pos.getZ() + box.minZ - origin.z),
                            (float) (pos.getX() + box.maxX - origin.x),
                            (float) (pos.getZ() + box.maxZ - origin.z)));
                }
                if (crystal.level().getBlockState(pos).isFaceSturdy(crystal.level(), pos, Direction.UP)) break;
            }
        }
        List<Surface> result = List.copyOf(surfaces);
        CACHE.put(crystal, new Cached(now, origin, result));
        return result;
    }

    /**
     * The light a crystal still in the world casts on the ground and, with Old KoHs Crystal Glow,
     * the halo behind it that the glow used to be: it always wears the burst; only the death flash
     * takes a shape. The glow itself, in the crystal's layers, is {@link CrystalLayerGlow}. The glow
     * switch turns all of it off.
     */
    public static void submit(EndCrystalRenderState state, PoseStack poses,
            SubmitNodeCollector collector, CameraRenderState camera) {
        CrystalAppearance look = CrystalAppearanceAccess.of(state);
        float reach = CrystalVisualConfig.glowQuality().lightRange;
        if (!look.glowActive() || state.distanceToCameraSq > reach * reach) return;
        float power = CrystalGlowMath.power(look.glowPowerPercent);
        if (CrystalVisualConfig.oldGlow()) {
            // The halo grows and shrinks with the crystal it surrounds.
            float size = CrystalPose.size(look);
            draw(state, look, poses, collector, camera, CrystalFlashStyle.EXPLOSION, CrystalGlowMath.radius(power) * size,
                    size, power, 0F, null, List.of(), true);
        }
        submitReflections(state, look, poses, collector, camera, look.haloColor(), power, centerHeight(state, look));
    }

    /**
     * The flash a destroyed crystal leaves behind, in the shape the player selected.
     *
     * <p>It has its own switch, size, opacity and duration, so turning the glow off leaves it alone.
     * Only its light on the ground belongs to the glow: that is the reflections setting, and it is
     * drawn only while the glow is on.</p>
     *
     * @param opacity  how much of the flash is left, 1 at the explosion and 0 when it is gone
     * @param progress how far the flash has faded, 0 at the explosion and 1 when it is gone
     * @param origin   world position of the blast, or {@code null} in the settings preview
     * @param targets  visible players the lightning style may point at
     */
    public static void submitFlash(EndCrystalRenderState state, PoseStack poses,
            SubmitNodeCollector collector, CameraRenderState camera, float opacity, float progress,
            Vec3 origin, List<Vec3> targets) {
        CrystalAppearance look = CrystalAppearanceAccess.of(state);
        if (opacity <= 0 || !look.flashActive() || state.distanceToCameraSq > 4096) return;
        float gain = look.flashGain() * opacity;
        draw(state, look, poses, collector, camera, CrystalVisualConfig.flashStyle(), look.flashRadius(),
                look.flashScale(), gain, progress, origin, targets, false);
        if (look.glowActive()) {
            submitReflections(state, look, poses, collector, camera, look.haloColor(), gain, centerHeight(state, look));
        }
    }

    private static float centerHeight(EndCrystalRenderState state, CrystalAppearance look) {
        return CrystalPose.centreHeight(state.ageInTicks, look);
    }

    /**
     * @param radius      outer radius of the light, in blocks
     * @param detailScale scale of the burst's fixed inner discs and rays, 1 for the live halo
     * @param gain        alpha gain, already multiplied by whatever fade applies
     * @param behindModel draw the light just behind the crystal's model rather than through its
     *                    centre; only for a live crystal, since a flash has no model left to hide it
     */
    private static void draw(EndCrystalRenderState state, CrystalAppearance look, PoseStack poses,
            SubmitNodeCollector collector, CameraRenderState camera, CrystalFlashStyle style, float radius,
            float detailScale, float gain, float progress, Vec3 origin, List<Vec3> targets, boolean behindModel) {
        float centerY = centerHeight(state, look);
        int color = look.haloColor();
        // In the world the pose is a plain translation; anything else is the settings preview.
        boolean world = translationOnly(poses.last().pose());
        // The live halo is drawn coarser in the lighter quality modes; a flash is one disc for a moment.
        int coarser = behindModel ? CrystalVisualConfig.glowQuality().haloCoarser : 0;
        DiskDetail detail = world
                ? DISK_DETAIL[Math.min(DISK_DETAIL.length - 1, detailLevel(state.distanceToCameraSq, camera) + coarser)]
                : DISK_DETAIL[0];
        // Additive, depth-tested, writing no depth: see CrystalGlowMaterial.
        poses.pushPose();
        poses.translate(0, centerY, 0);
        float sizeScale = behindModel ? moveBehindModel(poses, camera.orientation) : 1F;
        poses.mulPose(camera.orientation);
        if (sizeScale != 1F) {
            poses.scale(sizeScale, sizeScale, sizeScale);
        }
        // How high each billboard axis climbs, so every vertex can fade out before the ground.
        float upX = camera.orientation.transform(new Vector3f(1F, 0F, 0F)).y * sizeScale;
        float upY = camera.orientation.transform(new Vector3f(0F, 1F, 0F)).y * sizeScale;
        if (style == CrystalFlashStyle.MY_HEAD) {
            // The head is a textured quad, so it needs its own material rather than the ray buffer.
            Identifier skin = CrystalFlashShapes.playerSkin();
            if (skin != null) {
                // Pale, not tinted: a washed-out colour on a see-through face, a white veil over it
                // and a whiter halo behind, so the skin reads as a ghost of itself.
                int pale = CrystalFlashShapes.paleTint(color);
                collector.submitCustomGeometry(poses, RenderTypes.entityTranslucent(skin),
                        (pose, buffer) -> CrystalFlashShapes.submitPlayerHead(
                                new CrystalGlowBuffer(buffer, centerY, upX, upY, !world), pose.pose(), pale, radius, gain));
                collector.submitCustomGeometry(poses, CrystalGlowMaterial.glow(), (pose, buffer) -> {
                    CrystalGlowBuffer glow = new CrystalGlowBuffer(buffer, centerY, upX, upY, !world);
                    disk(glow, pose.pose(), detail, pale, radius * 1.4F, 0.4F * gain);
                    CrystalFlashShapes.submitHeadVeil(glow, pose.pose(), 0xFFFFFFFF, radius, gain);
                });
            }
            poses.popPose();
            return;
        }
        collector.submitCustomGeometry(poses, CrystalGlowMaterial.glow(), (pose, buffer) -> {
            CrystalGlowBuffer glow = new CrystalGlowBuffer(buffer, centerY, upX, upY, !world);
            Matrix4f matrix = pose.pose();
            if (style != CrystalFlashStyle.EXPLOSION) {
                CrystalFlashShapes.submit(style, glow, matrix, camera.orientation, color,
                        CrystalGlowMath.hotColor(color), radius, gain, origin, targets, progress);
                return;
            }
            // Preserve the original halo's gain at 100%; extra passes avoid byte-alpha overflow.
            disk(glow, matrix, detail, color, radius, 0.42F * gain);
            disk(glow, matrix, detail, color, 0.54F * detailScale, 0.7F * gain);
            // The hot middle is the core's light: around a live crystal it follows the Core control,
            // gone at 0%, as it always was at 100%, wider and denser past it. A flash has no core.
            float coreGlow = behindModel ? look.coreGlowPercent / 100F : 1F;
            if (coreGlow > 0F) {
                disk(glow, matrix, detail, CrystalGlowMath.hotColor(color),
                        0.25F * detailScale * (1F + 0.35F * Math.max(0F, coreGlow - 1F)), 0.9F * gain * coreGlow);
            }
            // Stable facet rays, with bright narrow spines and a wider colored skirt.
            double turn = state.ageInTicks * look.rotationSpeedPercent / 100F * 0.002;
            for (int ray = 0; ray < 16; ray++) {
                double angle = turn + ray * Math.PI * 2 / 16;
                float length = radius * (0.64F + (ray * 7 % 5) * 0.09F);
                streak(glow, matrix, color, angle, length, 0.12F * detailScale, gain * 0.22F);
                streak(glow, matrix, CrystalGlowMath.hotColor(color), angle, length * 0.82F,
                        0.026F * detailScale, gain * 0.32F);
            }
        });
        poses.popPose();
    }

    /**
     * Moves the halo's plane from the crystal's centre to just behind the model, away from the
     * viewer and level with the ground, and returns the scale that keeps it the same size on screen.
     *
     * <p>Through the centre, the turning frames were half in front of the light and half behind it.
     * The front half cut it into hard-edged pieces that changed every frame, which at high power
     * read as the whole glow distorting. Behind the model the crystal is drawn over its own light,
     * and the glass shows the light through it, the same way on every frame.</p>
     */
    private static float moveBehindModel(PoseStack poses, Quaternionf orientation) {
        Matrix4f pose = poses.last().pose();
        boolean perspective = translationOnly(pose);
        // The world's camera looks down its own -Z, and there the pose is a plain translation, so
        // that is also the direction here. The settings preview is an orthographic view that looks
        // down +Z instead, and its pose carries the preview's rotation and mirror, undone below.
        Vector3f forward = orientation.transform(new Vector3f(0F, 0F, perspective ? -1F : 1F));
        Vector3f local = new Matrix3f(pose).invert().transform(new Vector3f(forward));
        float length = local.length();
        if (!Float.isFinite(length) || length < 1.0E-6F) {
            return 1F;
        }
        // Level with the ground: looking down, a plane pushed straight along the view would sink
        // into the block under the crystal and fade away.
        float x = local.x / length * BEHIND_MODEL;
        float z = local.z / length * BEHIND_MODEL;
        float depth = pose.m30() * forward.x + pose.m31() * forward.y + pose.m32() * forward.z;
        poses.translate(x, 0F, z);
        if (!perspective) {
            return 1F; // The preview is orthographic: a halo further back is not smaller.
        }
        // Further away is smaller; grow it back so it is exactly as large as the crystal's own light.
        float nearest = Math.max(0.5F, depth);
        return (nearest + x * forward.x + z * forward.z) / nearest;
    }

    /**
     * Which disc a halo gets: its distance, corrected for the field of view, so a zoom that makes a
     * far crystal large on screen also gives it the fine disc back.
     */
    private static int detailLevel(double distanceSq, CameraRenderState camera) {
        Matrix4f projection = camera.projectionMatrix;
        float scale = projection == null || !(projection.m11() > 0F) ? REFERENCE_SCALE : projection.m11();
        float apparent = (float) Math.sqrt(distanceSq) * REFERENCE_SCALE / scale;
        int level = 0;
        while (level < DETAIL_DISTANCE.length && apparent >= DETAIL_DISTANCE[level]) {
            level++;
        }
        return level;
    }

    static boolean translationOnly(Matrix4f pose) {
        return Math.abs(pose.m00() - 1F) < 1.0E-4F && Math.abs(pose.m11() - 1F) < 1.0E-4F
                && Math.abs(pose.m22() - 1F) < 1.0E-4F
                && Math.abs(pose.m01()) < 1.0E-4F && Math.abs(pose.m02()) < 1.0E-4F
                && Math.abs(pose.m10()) < 1.0E-4F && Math.abs(pose.m12()) < 1.0E-4F
                && Math.abs(pose.m20()) < 1.0E-4F && Math.abs(pose.m21()) < 1.0E-4F;
    }

    /**
     * Coloured spill on the block tops under the crystal, for the live glow and for the flash.
     *
     * @param power the light's strength: the glow power, or the flash's gain as it fades
     */
    private static void submitReflections(EndCrystalRenderState state, CrystalAppearance look,
            PoseStack poses, SubmitNodeCollector collector, CameraRenderState camera, int color, float power,
            float centerY) {
        if (look.glowReflectionsPercent <= 0 || !((Object) state instanceof CrystalGlowAccess access)) return;
        List<Surface> surfaces = access.crystalTweaks$surfaces();
        if (surfaces.isEmpty()) return;
        float strength = power * CrystalGlowMath.power(look.glowReflectionsPercent) * 0.65F;
        if (strength <= 0F) return;
        int reflectionPasses = Math.max(1, (int) Math.ceil(strength));
        // Each block top is split so the falloff is sampled every quarter block; far away, where a
        // block is a few pixels wide, every half block draws the same pool.
        boolean far = translationOnly(poses.last().pose()) && detailLevel(state.distanceToCameraSq, camera) > 0;
        int fine = CrystalVisualConfig.glowQuality().spillCells;
        int cells = far ? Math.min(2, fine) : fine;
        collector.submitCustomGeometry(poses, CrystalGlowMaterial.glow(), (pose, buffer) -> {
            for (int pass = 0; pass < reflectionPasses; pass++) for (Surface surface : surfaces) {
                for (int ix = 0; ix < cells; ix++) for (int iz = 0; iz < cells; iz++) {
                    float x0 = surface.x0 + (surface.x1 - surface.x0) * ix / cells;
                    float x1 = surface.x0 + (surface.x1 - surface.x0) * (ix + 1) / cells;
                    float z0 = surface.z0 + (surface.z1 - surface.z0) * iz / cells;
                    float z1 = surface.z0 + (surface.z1 - surface.z0) * (iz + 1) / cells;
                    surfaceVertex(buffer,pose.pose(),color,strength / reflectionPasses,x0,surface.y,z0,centerY);
                    surfaceVertex(buffer,pose.pose(),color,strength / reflectionPasses,x0,surface.y,z1,centerY);
                    surfaceVertex(buffer,pose.pose(),color,strength / reflectionPasses,x1,surface.y,z1,centerY);
                    surfaceVertex(buffer,pose.pose(),color,strength / reflectionPasses,x0,surface.y,z0,centerY);
                    surfaceVertex(buffer,pose.pose(),color,strength / reflectionPasses,x1,surface.y,z1,centerY);
                    surfaceVertex(buffer,pose.pose(),color,strength / reflectionPasses,x1,surface.y,z0,centerY);
                }
            }
        });
    }

    private static void disk(CrystalGlowBuffer buffer, Matrix4f matrix, DiskDetail detail, int color, float radius,
            float gain) {
        int passes = Math.max(1, (int) Math.ceil(gain));
        int rings = detail.rings(), segments = detail.segments();
        float[] cos = detail.cos(), sin = detail.sin();
        for (int pass = 0; pass < passes; pass++) for (int ring = 0; ring < rings; ring++) {
            float r0 = radius * ring / rings, r1 = radius * (ring + 1) / rings;
            float a0 = gain / passes * falloff(ring / (float) rings);
            float a1 = gain / passes * falloff((ring + 1F) / rings);
            for (int segment = 0; segment < segments; segment++) {
                float x0 = cos[segment], y0 = sin[segment];
                float x1 = cos[segment + 1], y1 = sin[segment + 1];
                if (ring == 0) {
                    // The innermost ring meets at the centre: its second triangle would have no area.
                    buffer.triangle(matrix,color,0F,0F,a0,x0*r1,y0*r1,a1,x1*r1,y1*r1,a1);
                    continue;
                }
                buffer.triangle(matrix,color,x0*r0,y0*r0,a0,x0*r1,y0*r1,a1,x1*r1,y1*r1,a1);
                buffer.triangle(matrix,color,x0*r0,y0*r0,a0,x1*r1,y1*r1,a1,x1*r0,y1*r0,a0);
            }
        }
    }

    /**
     * The disc's corners, computed once instead of twice per segment, ring and pass: about 1,600
     * {@code cos}/{@code sin} calls per disc per frame. Each entry is the exact float the loop used
     * to compute, so the image is unchanged.
     */
    private static float[] trigTable(int segments, boolean sine) {
        float[] table = new float[segments + 1];
        for (int segment = 0; segment <= segments; segment++) {
            double angle = Math.PI * 2 * segment / segments;
            table[segment] = (float) (sine ? Math.sin(angle) : Math.cos(angle));
        }
        return table;
    }

    private static void streak(CrystalGlowBuffer b, Matrix4f m, int c, double angle,
            float length, float width, float gain) {
        float dx = (float) Math.cos(angle), dy = (float) Math.sin(angle);
        float lx = -dy * width, ly = dx * width;
        b.triangle(m,c, 0,0,gain, lx,ly,0, dx*length,dy*length,0);
        b.triangle(m,c, 0,0,gain, dx*length,dy*length,0, -lx,-ly,0);
    }

    private static float falloff(float radius) { float f = 1 - radius * radius; return f*f*f; }

    private static void surfaceVertex(VertexConsumer b, Matrix4f m, int c, float power,
            float x, float y, float z, float lightY) {
        float distance = (float) Math.sqrt(x*x + z*z + (lightY-y)*(lightY-y));
        float alpha = power * falloff(Math.min(1, distance / 3.2F)) * spillEdge(x, z);
        b.addVertex(m,x,y,z).setColor((c >> 16)&255,(c >> 8)&255,c&255,CrystalGlowMath.alpha(alpha));
    }

    /**
     * The spill only covers the blocks within {@link #SPILL_REACH} of the crystal. Its light used
     * to stop dead at that border: at high power the blocks there were still lit and the pool
     * ended in a hard square. It now fades to nothing over the last block.
     */
    private static float spillEdge(float x, float z) {
        float t = Math.max(0F, Math.min(1F, SPILL_REACH - Math.max(Math.abs(x), Math.abs(z))));
        return t * t * (3F - 2F * t);
    }
}
