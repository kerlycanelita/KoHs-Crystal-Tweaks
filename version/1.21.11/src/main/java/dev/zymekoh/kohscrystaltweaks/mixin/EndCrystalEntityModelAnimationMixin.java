package dev.zymekoh.kohscrystaltweaks.mixin;

import dev.zymekoh.kohscrystaltweaks.config.KoHsCrystalTweaksConfig;
import dev.zymekoh.kohscrystaltweaks.core.CrystalTint;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.EndCrystalEntityRenderer;
import net.minecraft.client.render.entity.model.EndCrystalEntityModel;
import net.minecraft.client.render.entity.state.EndCrystalEntityRenderState;
import net.minecraft.util.math.RotationAxis;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EndCrystalEntityModel.class)
public abstract class EndCrystalEntityModelAnimationMixin {
    @Unique
    private static final float KCT_SINE_45 = (float) Math.sin(Math.PI / 4.0);

    @Shadow
    public ModelPart base;

    @Shadow
    public ModelPart outerGlass;

    @Shadow
    public ModelPart innerGlass;

    @Shadow
    public ModelPart cube;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void kct$registerTintedParts(ModelPart root, CallbackInfo ci) {
        CrystalTint.register(this.outerGlass, this.innerGlass, this.cube);
    }

    @Inject(
            method = "setAngles(Lnet/minecraft/client/render/entity/state/EndCrystalEntityRenderState;)V",
            at = @At("TAIL"))
    private void kct$applyCrystalAnimationTweaks(EndCrystalEntityRenderState state, CallbackInfo ci) {
        KoHsCrystalTweaksConfig config = KoHsCrystalTweaksConfig.get();
        if (!config.staticCrystalEnabled
                && config.crystalFlotationEnabled
                && Math.abs(config.crystalSpinSpeed - 1.0f) < 0.0001f) {
            return;
        }

        this.base.resetTransform();
        this.outerGlass.resetTransform();
        this.innerGlass.resetTransform();
        this.cube.resetTransform();
        this.base.visible = state.baseVisible;

        float spinAge = config.staticCrystalEnabled ? 0.0f : state.age * config.crystalSpinSpeed;
        float floatAge = (config.staticCrystalEnabled || !config.crystalFlotationEnabled) ? 0.0f : state.age;
        float rotationDegrees = spinAge * 3.0f;
        float offset = EndCrystalEntityRenderer.getYOffset(floatAge) * 16.0f;

        // Keep the vanilla hierarchy intact: only the outer glass moves vertically,
        // and the child parts inherit that placement from the model tree.
        this.outerGlass.originY += offset / 2.0f;

        this.outerGlass.rotate(
                RotationAxis.POSITIVE_Y.rotationDegrees(rotationDegrees)
                        .rotateAxis((float) (Math.PI / 3.0), KCT_SINE_45, 0.0f, KCT_SINE_45));
        this.innerGlass.rotate(kct$tiltQuaternion(rotationDegrees));
        this.cube.rotate(kct$tiltQuaternion(rotationDegrees));
    }


    @Unique
    private static Quaternionf kct$tiltQuaternion(float rotationDegrees) {
        return new Quaternionf()
                .setAngleAxis((float) (Math.PI / 3.0), KCT_SINE_45, 0.0f, KCT_SINE_45)
                .rotateY(rotationDegrees * (float) (Math.PI / 180.0));
    }
}
