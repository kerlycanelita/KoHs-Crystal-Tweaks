package com.zymekoh.crystaltweaks.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EndCrystalRenderer;
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

    public record Surface(float x0, float y, float z0, float x1, float z1) { }
    private record Cached(long at, Vec3 position, List<Surface> surfaces) { }
    private static final Map<EndCrystal, Cached> CACHE = new WeakHashMap<>();
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
     * The halo of a crystal still in the world. It always wears the burst; only the death flash
     * takes a shape.
     */
    public static void submit(EndCrystalRenderState state, PoseStack poses,
            SubmitNodeCollector collector, CameraRenderState camera) {
        submit(state, poses, collector, camera, 1F, 0F, CrystalFlashStyle.EXPLOSION, null, List.of(), true);
    }

    /**
     * The flash a destroyed crystal leaves behind, in the shape the player selected.
     *
     * @param progress how far the flash has faded, 0 at the explosion and 1 when it is gone
     * @param origin   world position of the blast, or {@code null} in the settings preview
     * @param targets  visible players the lightning style may point at
     */
    public static void submitFlash(EndCrystalRenderState state, PoseStack poses,
            SubmitNodeCollector collector, CameraRenderState camera, float opacity, float progress,
            Vec3 origin, List<Vec3> targets) {
        submit(state, poses, collector, camera, opacity, progress, CrystalVisualConfig.flashStyle(),
                origin, targets, false);
    }

    /**
     * @param behindModel draw the light just behind the crystal's model rather than through its
     *                    centre; only for a live crystal, since a flash has no model left to hide it
     */
    private static void submit(EndCrystalRenderState state, PoseStack poses,
            SubmitNodeCollector collector, CameraRenderState camera, float opacity, float progress,
            CrystalFlashStyle style, Vec3 origin, List<Vec3> targets, boolean behindModel) {
        CrystalAppearance look = CrystalAppearanceAccess.of(state);
        if (opacity <= 0) return;
        if (look.glowPowerPercent <= 0 || state.distanceToCameraSq > 4096) return;
        float power = CrystalGlowMath.power(look.glowPowerPercent);
        float centerY = 2F + EndCrystalRenderer.getY(state.ageInTicks * look.floatingSpeedPercent / 100F);
        int color = look.haloColor();
        // Vanilla dragon-ray material: additive blending, depth test on, depth writes off.
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
        // The size slider only moves the shaped styles; the burst keeps the proportions it had.
        float shapeScale = style.scalable() ? look.flashScalePercent / 100F : 1F;
        if (style == CrystalFlashStyle.MY_HEAD) {
            // The head is a textured quad, so it needs its own material rather than the ray buffer.
            Identifier skin = CrystalFlashShapes.playerSkin();
            if (skin != null) {
                float headRadius = CrystalGlowMath.radius(power) * shapeScale;
                // Pale, not tinted: a washed-out colour on a see-through face, a white veil over it
                // and a whiter halo behind, so the skin reads as a ghost of itself.
                int pale = CrystalFlashShapes.paleTint(color);
                collector.submitCustomGeometry(poses, RenderTypes.entityTranslucent(skin),
                        (pose, buffer) -> CrystalFlashShapes.submitPlayerHead(
                                new CrystalGlowBuffer(buffer, centerY, upX, upY), pose.pose(), pale, headRadius,
                                power * opacity));
                collector.submitCustomGeometry(poses, RenderTypes.dragonRays(), (pose, buffer) -> {
                    CrystalGlowBuffer glow = new CrystalGlowBuffer(buffer, centerY, upX, upY);
                    disk(glow, pose.pose(), pale, headRadius * 1.4F, 0.4F * power * opacity);
                    CrystalFlashShapes.submitHeadVeil(glow, pose.pose(), 0xFFFFFFFF, headRadius,
                            power * opacity);
                });
            }
            poses.popPose();
            submitReflections(state, look, poses, collector, color, power, opacity, centerY);
            return;
        }
        collector.submitCustomGeometry(poses, RenderTypes.dragonRays(), (pose, buffer) -> {
            CrystalGlowBuffer glow = new CrystalGlowBuffer(buffer, centerY, upX, upY);
            Matrix4f matrix = pose.pose();
            float radius = CrystalGlowMath.radius(power) * shapeScale;
            if (style != CrystalFlashStyle.EXPLOSION) {
                CrystalFlashShapes.submit(style, glow, matrix, camera.orientation, color,
                        CrystalGlowMath.hotColor(color), radius, power * opacity, origin, targets,
                        progress);
                return;
            }
            // Preserve the original halo's gain at 100%; extra passes avoid byte-alpha overflow.
            disk(glow, matrix, color, radius, 0.42F * power * opacity);
            disk(glow, matrix, color, 0.54F, 0.7F * power * opacity);
            disk(glow, matrix, CrystalGlowMath.hotColor(color), 0.25F, 0.9F * power * opacity);
            // Stable facet rays, with bright narrow spines and a wider colored skirt.
            double turn = state.ageInTicks * look.rotationSpeedPercent / 100F * 0.002;
            for (int ray = 0; ray < 16; ray++) {
                double angle = turn + ray * Math.PI * 2 / 16;
                float length = radius * (0.64F + (ray * 7 % 5) * 0.09F);
                streak(glow, matrix, color, angle, length, 0.12F, power * opacity * 0.22F);
                streak(glow, matrix, CrystalGlowMath.hotColor(color), angle, length * 0.82F,
                        0.026F, power * opacity * 0.32F);
            }
        });
        poses.popPose();
        submitReflections(state, look, poses, collector, color, power, opacity, centerY);
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

    private static boolean translationOnly(Matrix4f pose) {
        return Math.abs(pose.m00() - 1F) < 1.0E-4F && Math.abs(pose.m11() - 1F) < 1.0E-4F
                && Math.abs(pose.m22() - 1F) < 1.0E-4F
                && Math.abs(pose.m01()) < 1.0E-4F && Math.abs(pose.m02()) < 1.0E-4F
                && Math.abs(pose.m10()) < 1.0E-4F && Math.abs(pose.m12()) < 1.0E-4F
                && Math.abs(pose.m20()) < 1.0E-4F && Math.abs(pose.m21()) < 1.0E-4F;
    }

    /** Coloured spill on the block tops under the crystal; shared by every flash style. */
    private static void submitReflections(EndCrystalRenderState state, CrystalAppearance look,
            PoseStack poses, SubmitNodeCollector collector, int color, float power, float opacity,
            float centerY) {
        if (look.glowReflectionsPercent <= 0 || !((Object) state instanceof CrystalGlowAccess access)) return;
        List<Surface> surfaces = access.crystalTweaks$surfaces();
        if (surfaces.isEmpty()) return;
        float strength = power * CrystalGlowMath.power(look.glowReflectionsPercent) * 0.65F * opacity;
        int reflectionPasses = Math.max(1, (int) Math.ceil(strength));
        collector.submitCustomGeometry(poses, RenderTypes.dragonRays(), (pose, buffer) -> {
            for (int pass = 0; pass < reflectionPasses; pass++) for (Surface surface : surfaces) {
                for (int ix = 0; ix < 4; ix++) for (int iz = 0; iz < 4; iz++) {
                    float x0 = surface.x0 + (surface.x1 - surface.x0) * ix / 4;
                    float x1 = surface.x0 + (surface.x1 - surface.x0) * (ix + 1) / 4;
                    float z0 = surface.z0 + (surface.z1 - surface.z0) * iz / 4;
                    float z1 = surface.z0 + (surface.z1 - surface.z0) * (iz + 1) / 4;
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

    private static void disk(CrystalGlowBuffer buffer, Matrix4f matrix, int color, float radius, float gain) {
        int rings = 10, segments = 40, passes = Math.max(1, (int) Math.ceil(gain));
        for (int pass = 0; pass < passes; pass++) for (int ring = 0; ring < rings; ring++) {
            float r0 = radius * ring / rings, r1 = radius * (ring + 1) / rings;
            float a0 = gain / passes * falloff(ring / (float) rings);
            float a1 = gain / passes * falloff((ring + 1F) / rings);
            for (int segment = 0; segment < segments; segment++) {
                double t0 = Math.PI * 2 * segment / segments, t1 = Math.PI * 2 * (segment + 1) / segments;
                float x0 = (float) Math.cos(t0), y0 = (float) Math.sin(t0);
                float x1 = (float) Math.cos(t1), y1 = (float) Math.sin(t1);
                triangle(buffer,matrix,color,x0*r0,y0*r0,a0,x0*r1,y0*r1,a1,x1*r1,y1*r1,a1);
                triangle(buffer,matrix,color,x0*r0,y0*r0,a0,x1*r1,y1*r1,a1,x1*r0,y1*r0,a0);
            }
        }
    }

    private static void streak(CrystalGlowBuffer b, Matrix4f m, int c, double angle,
            float length, float width, float gain) {
        float dx = (float) Math.cos(angle), dy = (float) Math.sin(angle);
        float lx = -dy * width, ly = dx * width;
        triangle(b,m,c, 0,0,gain, lx,ly,0, dx*length,dy*length,0);
        triangle(b,m,c, 0,0,gain, dx*length,dy*length,0, -lx,-ly,0);
    }

    private static float falloff(float radius) { float f = 1 - radius * radius; return f*f*f; }

    private static void triangle(CrystalGlowBuffer b, Matrix4f m, int c,
            float ax,float ay,float aa, float bx,float by,float ba, float cx,float cy,float ca) {
        b.vertex(m,c,ax,ay,aa); b.vertex(m,c,bx,by,ba); b.vertex(m,c,cx,cy,ca);
        // Opposite winding supports the GUI's mirrored pose without disabling the depth test.
        b.vertex(m,c,cx,cy,ca); b.vertex(m,c,bx,by,ba); b.vertex(m,c,ax,ay,aa);
    }

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
