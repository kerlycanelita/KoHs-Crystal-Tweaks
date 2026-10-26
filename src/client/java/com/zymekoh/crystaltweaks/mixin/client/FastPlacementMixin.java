package com.zymekoh.crystaltweaks.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.zymekoh.crystaltweaks.core.FastPlacement;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Placement at the server's pace (see {@link FastPlacement}): a Use click on a crystal you just broke
 * waits for the server's removal instead of being spent on the dying crystal, then comes back through
 * this same loop as an ordinary click. Only the click loop is touched; a held Use key repeats as
 * Vanilla repeats it.
 */
@Mixin(Minecraft.class)
public abstract class FastPlacementMixin {
    @Shadow
    private int rightClickDelay;

    @WrapOperation(method = "handleKeybinds", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;startUseItem()V",
            ordinal = 0))
    private void crystalTweaks$useAtServerPace(Minecraft minecraft, Operation<Void> original) {
        if (FastPlacement.holdUse(minecraft)) {
            // As if the click had been spent: a held key waits the same four ticks before it repeats.
            this.rightClickDelay = 4;
            return;
        }
        original.call(minecraft);
    }
}
