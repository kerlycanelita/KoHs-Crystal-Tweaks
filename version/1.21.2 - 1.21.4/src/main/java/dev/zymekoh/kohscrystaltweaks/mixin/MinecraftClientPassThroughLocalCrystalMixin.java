package dev.zymekoh.kohscrystaltweaks.mixin;

import dev.zymekoh.kohscrystaltweaks.core.CrystalPredictor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecraftClient.class)
public abstract class MinecraftClientPassThroughLocalCrystalMixin {
    @Shadow
    public HitResult crosshairTarget;

    @Inject(method = "doAttack", at = @At("HEAD"))
    private void kct$passThroughAttack(CallbackInfoReturnable<Boolean> cir) {
        if (this.crosshairTarget == null || this.crosshairTarget.getType() != HitResult.Type.ENTITY) {
            return;
        }

        Entity target = ((EntityHitResult) this.crosshairTarget).getEntity();

        if (!(target instanceof EndCrystalEntity) || !CrystalPredictor.isLocalCrystalEntity(target)) {
            return;
        }

        CrystalPredictor.onLocalCrystalAttack(target);
        this.crosshairTarget = CrystalPredictor.raycastIgnoringLocal(1.0f);
    }

    @Inject(method = "doItemUse", at = @At("HEAD"))
    private void kct$passThroughUse(CallbackInfo ci) {
        if (this.crosshairTarget == null || this.crosshairTarget.getType() != HitResult.Type.ENTITY) {
            return;
        }

        Entity target = ((EntityHitResult) this.crosshairTarget).getEntity();
        if (!(target instanceof EndCrystalEntity) || !CrystalPredictor.isLocalCrystalEntity(target)) {
            return;
        }

        this.crosshairTarget = CrystalPredictor.raycastIgnoringLocal(1.0f);
    }
}
