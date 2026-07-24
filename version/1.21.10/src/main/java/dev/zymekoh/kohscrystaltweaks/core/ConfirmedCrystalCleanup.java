package dev.zymekoh.kohscrystaltweaks.core;

import dev.zymekoh.kohscrystaltweaks.config.KoHsCrystalTweaksConfig;
import dev.zymekoh.kohscrystaltweaks.mixin.PlayerInteractEntityC2SPacketAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;

/**
 * Performs optional client-only cleanup after Minecraft has sent a real vanilla crystal attack.
 *
 * <p>This observer never creates, cancels, reorders, retries, or edits a packet. It also never
 * changes the crosshair or selected hotbar slot.</p>
 */
public final class ConfirmedCrystalCleanup {
    private static final MinecraftClient CLIENT = MinecraftClient.getInstance();

    private ConfirmedCrystalCleanup() {
    }

    public static void afterVanillaPacket(PlayerInteractEntityC2SPacket packet) {
        if (!KoHsCrystalTweaksConfig.isConfirmedCrystalCleanupEnabled() || CLIENT.world == null) {
            return;
        }

        int entityId = ((PlayerInteractEntityC2SPacketAccessor) packet).kct$getEntityId();
        Entity target = CLIENT.world.getEntityById(entityId);
        if (!(target instanceof EndCrystalEntity crystal) || !crystal.isAlive()) {
            return;
        }

        packet.handle(new PlayerInteractEntityC2SPacket.Handler() {
            @Override
            public void interact(Hand hand) {
            }

            @Override
            public void interactAt(Hand hand, Vec3d position) {
            }

            @Override
            public void attack() {
                if (crystal.isAlive()) {
                    crystal.discard();
                }
            }
        });
    }
}
