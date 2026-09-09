package com.zymekoh.crystaltweaks.mixin.client;

import com.mojang.math.Axis;
import com.zymekoh.crystaltweaks.client.CrystalAppearance;
import com.zymekoh.crystaltweaks.client.CrystalAppearanceAccess;
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
public abstract class CrystalAnimationMixin implements CrystalAppearanceAccess {
    @Unique private CrystalAppearance crystalTweaks$appearance;

    @Override public CrystalAppearance crystalTweaks$appearance() { return crystalTweaks$appearance; }
    @Override public void crystalTweaks$appearance(CrystalAppearance appearance) { crystalTweaks$appearance = appearance; }
    @Unique
    private static final float CRYSTAL_TWEAKS_SINE_45 = (float) Math.sin(Math.PI / 4.0D);

    @Shadow
    public ModelPart base;

    @Shadow
    public ModelPart outerGlass;

    @Shadow
    public ModelPart innerGlass;

    @Shadow
    public ModelPart cube;

    @Inject(method = "setupAnim", at = @At("TAIL"))
    private void crystalTweaks$applyAnimationSpeeds(
            EndCrystalRenderState state,
            CallbackInfo callback
    ) {
        this.crystalTweaks$appearance = CrystalAppearanceAccess.of(state);
        int rotationPercent = this.crystalTweaks$appearance.rotationSpeedPercent;
        int floatingPercent = this.crystalTweaks$appearance.floatingSpeedPercent;
        if (rotationPercent == 100 && floatingPercent == 100) {
            return;
        }

        this.base.resetPose();
        this.outerGlass.resetPose();
        this.innerGlass.resetPose();
        this.cube.resetPose();
        this.base.visible = state.showsBottom;

        float rotationAge = state.ageInTicks * rotationPercent / 100.0F;
        float floatingAge = state.ageInTicks * floatingPercent / 100.0F;
        float rotationDegrees = rotationAge * 3.0F;
        float offset = EndCrystalRenderer.getY(floatingAge) * 16.0F;

        this.outerGlass.y += offset / 2.0F;
        this.outerGlass.rotateBy(
                Axis.YP.rotationDegrees(rotationDegrees)
                        .rotateAxis(
                                (float) (Math.PI / 3.0D),
                                CRYSTAL_TWEAKS_SINE_45,
                                0.0F,
                                CRYSTAL_TWEAKS_SINE_45));
        this.innerGlass.rotateBy(crystalTweaks$tilt(rotationDegrees));
        this.cube.rotateBy(crystalTweaks$tilt(rotationDegrees));
    }

    @Unique
    private static Quaternionf crystalTweaks$tilt(float rotationDegrees) {
        return new Quaternionf()
                .setAngleAxis(
                        (float) (Math.PI / 3.0D),
                        CRYSTAL_TWEAKS_SINE_45,
                        0.0F,
                        CRYSTAL_TWEAKS_SINE_45)
                .rotateY(rotationDegrees * (float) (Math.PI / 180.0D));
    }
}
