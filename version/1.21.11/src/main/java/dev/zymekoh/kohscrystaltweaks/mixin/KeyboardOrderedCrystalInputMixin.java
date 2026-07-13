package dev.zymekoh.kohscrystaltweaks.mixin;

import dev.zymekoh.kohscrystaltweaks.core.OrderedCrystalInput;
import net.minecraft.client.Keyboard;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.util.InputUtil;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Keyboard.class)
public abstract class KeyboardOrderedCrystalInputMixin {
    @Shadow
    @Final
    private MinecraftClient client;

    @Inject(method = "onKey", at = @At("TAIL"))
    private void kct$recordOrderedKeyInput(long window, int action, KeyInput input, CallbackInfo ci) {
        if (action != InputUtil.GLFW_PRESS
                || this.client.player == null
                || this.client.world == null
                || this.client.currentScreen != null
                || this.client.getOverlay() != null) {
            return;
        }

        int selectedSlot = this.client.player.getInventory().getSelectedSlot();
        for (int slot = 0; slot < this.client.options.hotbarKeys.length; slot++) {
            if (this.client.options.hotbarKeys[slot].matchesKey(input)) {
                OrderedCrystalInput.recordSlotChange(selectedSlot, slot);
                selectedSlot = slot;
            }
        }

        OrderedCrystalInput.recordInput(
                this.client.options.attackKey.matchesKey(input),
                this.client.options.useKey.matchesKey(input),
                selectedSlot);
    }
}
