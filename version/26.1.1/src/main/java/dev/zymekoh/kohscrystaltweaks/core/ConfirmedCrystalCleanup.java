package dev.zymekoh.kohscrystaltweaks.core;

import dev.zymekoh.kohscrystaltweaks.config.KoHsCrystalTweaksConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;

/**
 * Optional visual cleanup performed only after vanilla has already created a
 * real attack packet for a server-provided crystal entity ID.
 */
public final class ConfirmedCrystalCleanup {
    private ConfirmedCrystalCleanup() {
    }

    public static void observeOutgoing(Packet<?> packet) {
        if (!isEnabled()
                || !(packet instanceof ServerboundInteractPacket interaction)
                || interaction.hand() != null) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }

        Entity entity = minecraft.level.getEntity(interaction.entityId());
        if (entity instanceof EndCrystal crystal
                && crystal.level() == minecraft.level
                && crystal.isAlive()) {
            crystal.setRemoved(Entity.RemovalReason.KILLED);
        }
    }

    private static boolean isEnabled() {
        return KoHsCrystalTweaksConfig.isConfirmedCrystalCleanupEnabled();
    }
}
