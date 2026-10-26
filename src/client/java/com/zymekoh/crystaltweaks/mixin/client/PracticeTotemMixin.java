package com.zymekoh.crystaltweaks.mixin.client;

import com.zymekoh.crystaltweaks.practice.PracticeSession;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Crystal Practice's round report counts the totems popped: the practice world's server says so with
 * the entity event a totem plays. Only read, on the integrated server of a practice world.
 */
@Mixin(ServerLevel.class)
public abstract class PracticeTotemMixin {
    @Inject(method = "broadcastEntityEvent", at = @At("HEAD"))
    private void crystalTweaks$countTotem(Entity entity, byte event, CallbackInfo callback) {
        if (PracticeSession.running()) {
            PracticeSession.entityEvent((ServerLevel) (Object) this, entity, event);
        }
    }
}
