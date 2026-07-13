package dev.zymekoh.kohscrystaltweaks.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import dev.zymekoh.kohscrystaltweaks.core.OrderedCrystalInput;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public abstract class KeyboardOrderedCrystalInputMixin {
    @Shadow
    @Final
    private Minecraft minecraft;

    @Inject(method = "keyPress", at = @At("TAIL"))
    private void kct$recordOrderedKeyInput(long window, int action, KeyEvent input, CallbackInfo ci) {
        if (action != InputConstants.PRESS
                || this.minecraft.player == null
                || this.minecraft.level == null
                || this.minecraft.gui.screen() != null
                || this.minecraft.gui.overlay() != null) {
            return;
        }

        int selectedSlot = this.minecraft.player.getInventory().getSelectedSlot();
        for (int slot = 0; slot < this.minecraft.options.keyHotbarSlots.length; slot++) {
            if (this.minecraft.options.keyHotbarSlots[slot].matches(input)) {
                OrderedCrystalInput.recordSlotChange(selectedSlot, slot);
                selectedSlot = slot;
            }
        }

        OrderedCrystalInput.recordInput(
                this.minecraft.options.keyAttack.matches(input),
                this.minecraft.options.keyUse.matches(input),
                selectedSlot);
    }
}
