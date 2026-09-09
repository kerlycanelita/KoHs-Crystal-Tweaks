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
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** Depth-tested additive halo and local surface spill, not duplicate models or world lighting edits. */
public final class CrystalGlowRenderer {
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
        if (sampledThisTick++ >= 8) return List.of();
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

    public static void submit(EndCrystalRenderState state, PoseStack poses,
            SubmitNodeCollector collector, CameraRenderState camera) {
        submit(state, poses, collector, camera, 1F);
    }

    public static void submit(EndCrystalRenderState state, PoseStack poses,
            SubmitNodeCollector collector, CameraRenderState camera, float opacity) {
        CrystalAppearance look = CrystalAppearanceAccess.of(state);
        if (opacity <= 0) return;
        if (look.glowPowerPercent <= 0 || state.distanceToCameraSq > 4096) return;
        float power = CrystalGlowMath.power(look.glowPowerPercent);
        float centerY = 2F + EndCrystalRenderer.getY(state.ageInTicks * look.floatingSpeedPercent / 100F);
        int color = look.haloColor();
        // Vanilla dragon-ray material: additive blending, depth test on, depth writes off.
        poses.pushPose();
        poses.translate(0, centerY, 0);
        poses.mulPose(camera.orientation);
        collector.submitCustomGeometry(poses, RenderTypes.dragonRays(), (pose, buffer) -> {
            Matrix4f matrix = pose.pose();
            float radius = CrystalGlowMath.radius(power);
            // Preserve the original halo's gain at 100%; extra passes avoid byte-alpha overflow.
            disk(buffer, matrix, color, radius, 0.42F * power * opacity);
            disk(buffer, matrix, color, 0.54F, 0.7F * power * opacity);
            disk(buffer, matrix, CrystalGlowMath.hotColor(color), 0.25F, 0.9F * power * opacity);
            // Stable facet rays, with bright narrow spines and a wider colored skirt.
            double turn = state.ageInTicks * look.rotationSpeedPercent / 100F * 0.002;
            for (int ray = 0; ray < 16; ray++) {
                double angle = turn + ray * Math.PI * 2 / 16;
                float length = radius * (0.64F + (ray * 7 % 5) * 0.09F);
                streak(buffer, matrix, color, angle, length, 0.12F, power * opacity * 0.22F);
                streak(buffer, matrix, CrystalGlowMath.hotColor(color), angle, length * 0.82F,
                        0.026F, power * opacity * 0.32F);
            }
        });
        poses.popPose();
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

    private static void disk(VertexConsumer buffer, Matrix4f matrix, int color, float radius, float gain) {
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

    private static void streak(VertexConsumer b, Matrix4f m, int c, double angle,
            float length, float width, float gain) {
        float dx = (float) Math.cos(angle), dy = (float) Math.sin(angle);
        float lx = -dy * width, ly = dx * width;
        triangle(b,m,c, 0,0,gain, lx,ly,0, dx*length,dy*length,0);
        triangle(b,m,c, 0,0,gain, dx*length,dy*length,0, -lx,-ly,0);
    }

    private static float falloff(float radius) { float f = 1 - radius * radius; return f*f*f; }

    private static void triangle(VertexConsumer b, Matrix4f m, int c,
            float ax,float ay,float aa, float bx,float by,float ba, float cx,float cy,float ca) {
        vertex(b,m,c,ax,ay,0,aa); vertex(b,m,c,bx,by,0,ba); vertex(b,m,c,cx,cy,0,ca);
        // Opposite winding supports the GUI's mirrored pose without disabling the depth test.
        vertex(b,m,c,cx,cy,0,ca); vertex(b,m,c,bx,by,0,ba); vertex(b,m,c,ax,ay,0,aa);
    }

    private static void surfaceVertex(VertexConsumer b, Matrix4f m, int c, float power,
            float x, float y, float z, float lightY) {
        float distance = (float) Math.sqrt(x*x + z*z + (lightY-y)*(lightY-y));
        vertex(b,m,c,x,y,z,power * falloff(Math.min(1, distance / 3.2F)));
    }

    private static void vertex(VertexConsumer b, Matrix4f m, int c, float x, float y, float z, float alpha) {
        b.addVertex(m,x,y,z).setColor((c >> 16)&255,(c >> 8)&255,c&255,CrystalGlowMath.alpha(alpha));
    }
}
