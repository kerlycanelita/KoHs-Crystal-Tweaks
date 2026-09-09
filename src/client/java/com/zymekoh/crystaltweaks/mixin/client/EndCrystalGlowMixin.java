package com.zymekoh.crystaltweaks.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.zymekoh.crystaltweaks.client.CrystalVisualConfig;
import net.minecraft.client.model.object.crystal.EndCrystalModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EndCrystalRenderer;
import net.minecraft.client.renderer.entity.state.EndCrystalRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws a second, translucent pass over the crystal so it reads as glowing.
 *
 * <p>Raising the light the crystal is drawn with is not enough: that value tops out at full
 * brightness, which in daylight is where the crystal already is, so the slider would do nothing
 * outside a dark room.</p>
 *
 * <p>The glowing outline Vanilla uses for the Glowing effect is deliberately not used either. That
 * outline draws through terrain, which would turn a visual setting into seeing crystals through
 * walls. This pass is depth tested like any other model, so a block still hides it.</p>
 */
@Mixin(EndCrystalRenderer.class)
public abstract class EndCrystalGlowMixin {
    @Unique
    private static final Identifier CRYSTAL_TWEAKS$TEXTURE =
            Identifier.withDefaultNamespace("textures/entity/end_crystal/end_crystal.png");

    /** The packed light coordinates Vanilla uses for a fully lit entity. */
    @Unique
    private static final int CRYSTAL_TWEAKS$FULL_BRIGHT = 15728880;

    @Shadow
    @org.spongepowered.asm.mixin.Final
    private EndCrystalModel model;

    @Inject(
            method = "submit(Lnet/minecraft/client/renderer/entity/state/EndCrystalRenderState;"
                    + "Lcom/mojang/blaze3d/vertex/PoseStack;"
                    + "Lnet/minecraft/client/renderer/SubmitNodeCollector;"
                    + "Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At("TAIL")
    )
    private void crystalTweaks$submitGlow(
            EndCrystalRenderState state,
            PoseStack poseStack,
            SubmitNodeCollector collector,
            CameraRenderState camera,
            CallbackInfo callback
    ) {
        int percent = CrystalVisualConfig.glowPowerPercent();
        if (percent <= 0) {
            return;
        }

        int rgb = CrystalVisualConfig.customGlowColor()
                ? CrystalVisualConfig.glowColor() & 0xFFFFFF
                : 0xFFFFFF;
        // The pass is additive-looking rather than additive, so the slider drives its opacity.
        int alpha = Math.max(1, Math.min(255, percent * 255 / 100));
        int color = alpha << 24 | rgb;

        collector.submitModel(
                this.model,
                state,
                poseStack,
                RenderTypes.entityTranslucentEmissive(CRYSTAL_TWEAKS$TEXTURE),
                CRYSTAL_TWEAKS$FULL_BRIGHT,
                OverlayTexture.NO_OVERLAY,
                color,
                null);
    }
}
