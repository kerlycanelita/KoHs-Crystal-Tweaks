package com.zymekoh.crystaltweaks.mixin.client;

import com.zymekoh.crystaltweaks.client.CrystalVisualConfig;
import net.minecraft.client.renderer.entity.EndCrystalRenderer;
import net.minecraft.client.renderer.entity.state.EndCrystalRenderState;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Raises how brightly a crystal is drawn, without touching the light in the world.
 *
 * <p>Vanilla already stores the light a crystal is drawn with on its render state. Sliding this up
 * blends that value towards full brightness, so the crystal reads as glowing while every block
 * around it keeps exactly the light the server gave it.</p>
 */
@Mixin(EndCrystalRenderer.class)
public abstract class EndCrystalGlowMixin {
    /** The packed light coordinates Vanilla uses for a fully lit entity. */
    private static final int FULL_BRIGHT = 15728880;

    @Inject(
            method = "extractRenderState(Lnet/minecraft/world/entity/boss/enderdragon/EndCrystal;"
                    + "Lnet/minecraft/client/renderer/entity/state/EndCrystalRenderState;F)V",
            at = @At("TAIL")
    )
    private void crystalTweaks$applyGlowPower(
            EndCrystal crystal,
            EndCrystalRenderState state,
            float partialTick,
            CallbackInfo callback
    ) {
        int percent = CrystalVisualConfig.glowPowerPercent();
        if (percent <= 0) {
            return;
        }
        if (percent >= 100) {
            state.lightCoords = FULL_BRIGHT;
            return;
        }

        // Blend block and sky light separately: they are packed into the same int and mixing them
        // as one number would brighten the sky channel from a torch.
        int block = state.lightCoords & 0xFFFF;
        int sky = state.lightCoords >> 16 & 0xFFFF;
        int targetBlock = FULL_BRIGHT & 0xFFFF;
        int targetSky = FULL_BRIGHT >> 16 & 0xFFFF;
        int blendedBlock = block + (targetBlock - block) * percent / 100;
        int blendedSky = sky + (targetSky - sky) * percent / 100;
        state.lightCoords = blendedSky << 16 | blendedBlock;
    }
}
