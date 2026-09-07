package com.zymekoh.crystaltweaks.client;

import com.zymekoh.crystaltweaks.core.GhostCrystalSupport;
import com.zymekoh.crystaltweaks.core.GhostCrystalTracker;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.phys.Vec3;

/**
 * Draws a stand-in crystal over the gap between placing one and the server sending the real one.
 *
 * <p>Purely a drawing pass. The stand-in is a throwaway {@link EndCrystal} object that is never
 * added to any level, so Vanilla cannot see it: it is not pickable, has no collision, appears in no
 * entity query, and no packet can ever refer to it. When the server's crystal arrives the entry is
 * dropped and the real entity is what stays on screen.</p>
 *
 * <p>This is the same shape as the visual crystal prediction that PvP servers already accept: it
 * compensates for latency on an action the player has already performed, and grants nothing a
 * zero-latency player would not already have.</p>
 */
public final class GhostCrystalRenderer {
    private static EndCrystal template;
    private static boolean initialized;

    private GhostCrystalRenderer() {
    }

    public static void initialize() {
        if (initialized) {
            return;
        }
        initialized = true;
        GhostCrystalSupport.markAvailable();
        LevelRenderEvents.COLLECT_SUBMITS.register(GhostCrystalRenderer::collectSubmits);
    }

    private static void collectSubmits(LevelRenderContext context) {
        if (!CrystalVisualConfig.ghostCrystals() || GhostCrystalTracker.isEmpty()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }

        // Use the camera this render pass was built with, not the live one, so the stand-in lands
        // in exactly the same frame of reference as the real entities around it.
        CameraRenderState camera = context.levelState().cameraRenderState;
        Vec3 cameraPosition = camera.pos;
        float partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        EntityRenderDispatcher dispatcher = minecraft.getEntityRenderDispatcher();

        if (template == null || template.level() != minecraft.level) {
            template = new EndCrystal(minecraft.level, 0.0D, 0.0D, 0.0D);
            template.setShowBottom(false);
        }

        // Vanilla animates a crystal from its own age, so borrow the level's clock to keep the
        // stand-in spinning in step with the crystals already in the world.
        template.time = (int) (minecraft.level.getGameTime() % Integer.MAX_VALUE);

        GhostCrystalTracker.forEachPending(base -> {
            BlockPos above = base.above();
            double x = above.getX() + 0.5D;
            double y = above.getY();
            double z = above.getZ() + 0.5D;
            template.setPos(x, y, z);
            template.xOld = x;
            template.yOld = y;
            template.zOld = z;

            EntityRenderState state = dispatcher.extractEntity(template, partialTick);
            dispatcher.submit(
                    state,
                    camera,
                    x - cameraPosition.x,
                    y - cameraPosition.y,
                    z - cameraPosition.z,
                    context.poseStack(),
                    context.submitNodeCollector());
        });
    }
}
