package com.zymekoh.crystaltweaks.mixin.client;

import com.zymekoh.crystaltweaks.client.CrystalAppearance;
import com.zymekoh.crystaltweaks.client.CrystalAppearanceAccess;
import com.zymekoh.crystaltweaks.client.CrystalPose;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.object.crystal.EndCrystalModel;
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

    @Shadow
    public ModelPart base;

    @Shadow
    public ModelPart outerGlass;

    @Shadow
    public ModelPart innerGlass;

    @Shadow
    public ModelPart cube;

    /**
     * Poses the layers with the player's rotation speed, floating speed and size. The pose itself
     * is {@link CrystalPose}'s, the same one the glow is built from. The bedrock base keeps its own
     * place and size: it is the ground the crystal floats over, not part of the crystal.
     */
    @Inject(method = "setupAnim", at = @At("TAIL"))
    private void crystalTweaks$applyPose(
            EndCrystalRenderState state,
            CallbackInfo callback
    ) {
        CrystalAppearance look = CrystalAppearanceAccess.of(state);
        this.crystalTweaks$appearance = look;
        if (!CrystalPose.custom(look)) {
            return;
        }

        this.base.resetPose();
        this.outerGlass.resetPose();
        this.innerGlass.resetPose();
        this.cube.resetPose();
        this.base.visible = state.showsBottom;

        float size = CrystalPose.size(look);
        this.outerGlass.y = CrystalPose.modelCentre(state.ageInTicks, look);
        this.outerGlass.xScale *= size;
        this.outerGlass.yScale *= size;
        this.outerGlass.zScale *= size;
        this.outerGlass.rotateBy(CrystalPose.outerTurn(state.ageInTicks, look));
        Quaternionf nested = CrystalPose.nestedTurn(state.ageInTicks, look);
        this.innerGlass.rotateBy(nested);
        this.cube.rotateBy(nested);
    }
}
