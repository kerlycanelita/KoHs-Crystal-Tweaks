package dev.zymekoh.kohscrystaltweaks.mixin;

import dev.zymekoh.kohscrystaltweaks.core.CrystalPredictor;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class MinecraftPassThroughLocalCrystalMixin {
    @Shadow
    public HitResult hitResult;

    @Inject(method = "startAttack", at = @At("HEAD"))
    private void kct$attackThroughPrediction(CallbackInfoReturnable<Boolean> callback) {
        Entity localCrystal = localCrystalUnderCrosshair();
        if (localCrystal == null) {
            return;
        }

        CrystalPredictor.onLocalCrystalAttack(localCrystal);
        this.hitResult = CrystalPredictor.raycastIgnoringLocal(1.0F);
    }

    @Inject(method = "startUseItem", at = @At("HEAD"))
    private void kct$useThroughPrediction(CallbackInfo callback) {
        if (localCrystalUnderCrosshair() != null) {
            this.hitResult = CrystalPredictor.raycastIgnoringLocal(1.0F);
        }
    }

    private Entity localCrystalUnderCrosshair() {
        if (!(this.hitResult instanceof EntityHitResult entityHit)) {
            return null;
        }
        Entity entity = entityHit.getEntity();
        return CrystalPredictor.isLocalCrystalEntity(entity) ? entity : null;
    }
}
