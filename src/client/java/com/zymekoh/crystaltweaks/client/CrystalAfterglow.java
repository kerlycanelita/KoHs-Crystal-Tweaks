package com.zymekoh.crystaltweaks.client;

import com.mojang.blaze3d.vertex.PoseStack;
import java.lang.ref.WeakReference;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.EndCrystalRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.phys.Vec3;

/** Observes disappearances, retaining light data only. Never delays/removes/resurrects an entity. */
public final class CrystalAfterglow {
    private record Light(Vec3 origin, CrystalAfterglowState state) { }
    private record Seen(WeakReference<EndCrystal> entity, Light light, long at) { }
    private static final Map<UUID, Seen> SEEN = new LinkedHashMap<>();
    private static final AfterglowTimeline<Light> FADING = new AfterglowTimeline<>(24);
    private static Object world;

    private CrystalAfterglow() { }

    public static void observe(EndCrystal entity, EndCrystalRenderState state) {
        if (world != entity.level()) { reset(); world = entity.level(); }
        // Ignore synthetic placement previews supplied by any renderer.
        if (entity.level().getEntity(entity.getId()) != entity) return;
        UUID id = entity.getUUID();
        FADING.cancel(id); // A refused local prediction has returned: no overlapping death effect.
        CrystalAppearance appearance = CrystalAppearanceAccess.of(state);
        if (appearance.glowPowerPercent <= 0 || state.distanceToCameraSq > 4096) {
            SEEN.remove(id);
            return;
        }
        long now = System.nanoTime();
        SEEN.entrySet().removeIf(entry -> now - entry.getValue().at > 2_000_000_000L);
        if (!SEEN.containsKey(id) && SEEN.size() >= 128) SEEN.remove(SEEN.keySet().iterator().next());
        CrystalAfterglowState light = snapshot(state);
        SEEN.put(id, new Seen(new WeakReference<>(entity), new Light(new Vec3(state.x, state.y, state.z), light), now));
    }

    public static CrystalAfterglowState snapshot(EndCrystalRenderState source) {
        CrystalAfterglowState result = new CrystalAfterglowState();
        result.entityType = source.entityType;
        result.showsBottom = false;
        result.ageInTicks = source.ageInTicks;
        result.boundingBoxWidth = source.boundingBoxWidth;
        result.boundingBoxHeight = source.boundingBoxHeight;
        result.eyeHeight = source.eyeHeight;
        result.lightCoords = source.lightCoords;
        ((CrystalAppearanceAccess) result).crystalTweaks$appearance(CrystalAppearanceAccess.of(source).copy());
        ((CrystalGlowAccess) result).crystalTweaks$surfaces(((CrystalGlowAccess) source).crystalTweaks$surfaces());
        return result;
    }

    public static void onRemoved(Entity entity) {
        if (!(entity instanceof EndCrystal)) return;
        Entity.RemovalReason reason = entity.getRemovalReason();
        if (reason == Entity.RemovalReason.KILLED || reason == Entity.RemovalReason.DISCARDED) {
            onHidden((EndCrystal) entity);
        } else {
            SEEN.remove(entity.getUUID());
            FADING.cancel(entity.getUUID());
        }
    }

    public static void onHidden(EndCrystal entity) {
        Seen seen = SEEN.remove(entity.getUUID());
        long now = System.nanoTime();
        if (seen != null && now - seen.at < 500_000_000L) FADING.start(entity.getUUID(), seen.light, now);
    }

    public static void submit(PoseStack poses, SubmitNodeCollector collector, CameraRenderState camera) {
        if (world != Minecraft.getInstance().level) { reset(); world = Minecraft.getInstance().level; }
        if (world == null) return;
        // Also supports optimizers that mark an entity removed before Fabric's unload event.
        for (Seen seen : List.copyOf(SEEN.values())) {
            EndCrystal entity = seen.entity.get();
            if (entity != null && entity.isRemoved()) onRemoved(entity);
        }
        long now = System.nanoTime();
        SEEN.entrySet().removeIf(entry -> entry.getValue().entity.get() == null || now - entry.getValue().at > 2_000_000_000L);
        for (AfterglowTimeline.Sample<Light> sample : FADING.samples(now)) {
            Light light = sample.value();
            if (light.origin.distanceToSqr(camera.pos) > 4096) continue;
            poses.pushPose();
            poses.translate(light.origin.x - camera.pos.x, light.origin.y - camera.pos.y, light.origin.z - camera.pos.z);
            // A destroyed/unloaded block must not leave a glowing rectangle floating in air. The
            // filtered list is used for this frame only: writing it back tore the spill apart as the
            // blast's block updates arrived, and a chunk that reloaded mid-fade never got its light
            // back because the surface had already been dropped from the stored snapshot.
            List<CrystalGlowRenderer.Surface> stored = light.state.crystalTweaks$surfaces();
            light.state.crystalTweaks$surfaces(stored.stream()
                    .filter(surface -> survivingSurface(light.origin, surface)).toList());
            CrystalGlowRenderer.submitFlash(
                    light.state, poses, collector, camera, sample.opacity(), light.origin);
            light.state.crystalTweaks$surfaces(stored);
            poses.popPose();
        }
    }

    public static void reset() { SEEN.clear(); FADING.clear(); world = null; }

    private static boolean survivingSurface(Vec3 origin, CrystalGlowRenderer.Surface surface) {
        var level = Minecraft.getInstance().level;
        if (level == null) return false;
        double y = origin.y + surface.y() - 0.006;
        BlockPos pos = BlockPos.containing(origin.x + (surface.x0() + surface.x1()) / 2,
                y - 0.001, origin.z + (surface.z0() + surface.z1()) / 2);
        if (!level.isLoaded(pos)) return false;
        var shape = level.getBlockState(pos).getShape(level, pos);
        return shape.toAabbs().stream().anyMatch(box -> Math.abs(pos.getY() + box.maxY - y) < 0.001
                && pos.getX() + box.minX <= origin.x + surface.x0() + 0.001
                && pos.getX() + box.maxX >= origin.x + surface.x1() - 0.001
                && pos.getZ() + box.minZ <= origin.z + surface.z0() + 0.001
                && pos.getZ() + box.maxZ >= origin.z + surface.z1() - 0.001);
    }
}
