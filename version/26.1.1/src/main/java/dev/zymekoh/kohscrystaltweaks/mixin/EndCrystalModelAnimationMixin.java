package dev.zymekoh.kohscrystaltweaks.mixin;

import com.mojang.math.Axis;
import dev.zymekoh.kohscrystaltweaks.config.KoHsCrystalTweaksConfig;
import dev.zymekoh.kohscrystaltweaks.core.CrystalTint;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.object.crystal.EndCrystalModel;
import net.minecraft.client.renderer.entity.EndCrystalRenderer;
import net.minecraft.client.renderer.entity.state.EndCrystalRenderState;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EndCrystalModel.class)
public abstract class EndCrystalModelAnimationMixin {
    @Unique
    private static final float KCT_SINE_45 = (float) Math.sin(Math.PI / 4.0D);

    @Shadow
    public ModelPart base;

    @Shadow
    public ModelPart outerGlass;

    @Shadow
    public ModelPart innerGlass;

    @Shadow
    public ModelPart cube;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void kct$registerTintedParts(ModelPart root, CallbackInfo callback) {
        CrystalTint.register(this.outerGlass, this.innerGlass, this.cube);
    }

    @Inject(
            method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/EndCrystalRenderState;)V",
            at = @At("TAIL"))
    private void kct$applyCrystalAnimationTweaks(EndCrystalRenderState state, CallbackInfo callback) {
        KoHsCrystalTweaksConfig config = KoHsCrystalTweaksConfig.get();
        if (!config.staticCrystalEnabled
                && config.crystalFlotationEnabled
                && Math.abs(config.crystalSpinSpeed - 1.0F) < 0.0001F) {
            return;
        }

        this.base.resetPose();
        this.outerGlass.resetPose();
        this.innerGlass.resetPose();
        this.cube.resetPose();
        this.base.visible = state.showsBottom;

        float spinAge = config.staticCrystalEnabled ? 0.0F : state.ageInTicks * config.crystalSpinSpeed;
        float floatAge = (config.staticCrystalEnabled || !config.crystalFlotationEnabled)
                ? 0.0F
                : state.ageInTicks;
        float rotationDegrees = spinAge * 3.0F;
        float offset = EndCrystalRenderer.getY(floatAge) * 16.0F;

        this.outerGlass.y += offset / 2.0F;
        this.outerGlass.rotateBy(
                Axis.YP.rotationDegrees(rotationDegrees)
                        .rotateAxis((float) (Math.PI / 3.0D), KCT_SINE_45, 0.0F, KCT_SINE_45));
        this.innerGlass.rotateBy(kct$tiltQuaternion(rotationDegrees));
        this.cube.rotateBy(kct$tiltQuaternion(rotationDegrees));
    }

    @Unique
    private static Quaternionf kct$tiltQuaternion(float rotationDegrees) {
        return new Quaternionf()
                .setAngleAxis((float) (Math.PI / 3.0D), KCT_SINE_45, 0.0F, KCT_SINE_45)
                .rotateY(rotationDegrees * (float) (Math.PI / 180.0D));
    }
}
