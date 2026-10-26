package com.zymekoh.crystaltweaks.mixin.client;

import com.zymekoh.crystaltweaks.client.fight.DeathRecap;
import com.zymekoh.crystaltweaks.client.practice.PracticeHost;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A totem's animation, for the death recap: whose it was. Reads the packet and returns; nothing is changed. */
@Mixin(ClientPacketListener.class)
public abstract class FightEventsMixin {
    /** The entity event Minecraft plays a totem's animation for. */
    private static final byte TOTEM = 35;

    @Inject(method = "handleEntityEvent", at = @At("HEAD"))
    private void crystalTweaks$totemPopped(ClientboundEntityEventPacket packet, CallbackInfo callback) {
        Minecraft minecraft = Minecraft.getInstance();
        // The handler runs twice, first on the network thread, which only reschedules it.
        if (packet.getEventId() != TOTEM || !minecraft.isSameThread() || PracticeHost.serverSide() || minecraft.level == null) {
            return;
        }
        Entity entity = packet.getEntity(minecraft.level);
        if (entity != null) {
            DeathRecap.totemPopped(entity == minecraft.player, System.nanoTime());
        }
    }
}
