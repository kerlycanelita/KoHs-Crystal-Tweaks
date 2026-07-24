package dev.zymekoh.kohscrystaltweaks.mixin;

import dev.zymekoh.kohscrystaltweaks.core.KctEndCrystalRenderState;
import net.minecraft.client.render.entity.state.EndCrystalEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(EndCrystalEntityRenderState.class)
public class EndCrystalEntityRenderStateMixin implements KctEndCrystalRenderState {
    @Unique
    private boolean kct$hidden;

    @Override
    public boolean kct$isHidden() {
        return this.kct$hidden;
    }

    @Override
    public void kct$setHidden(boolean hidden) {
        this.kct$hidden = hidden;
    }
}
