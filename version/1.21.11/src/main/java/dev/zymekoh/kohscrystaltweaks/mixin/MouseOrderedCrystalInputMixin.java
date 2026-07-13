package dev.zymekoh.kohscrystaltweaks.mixin;

import dev.zymekoh.kohscrystaltweaks.core.OrderedCrystalInput;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import net.minecraft.client.gui.Click;
import net.minecraft.client.input.MouseInput;
import net.minecraft.client.util.InputUtil;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mouse.class)
public abstract class MouseOrderedCrystalInputMixin {
    @Shadow
    @Final
    private MinecraftClient client;

    @Unique
    private int kct$slotBeforeScroll = -1;

    @Inject(method = "onMouseButton", at = @At("TAIL"))
    private void kct$recordOrderedMouseInput(long window, MouseInput input, int action, CallbackInfo ci) {
        if (action != InputUtil.GLFW_PRESS
                || this.client.player == null
                || this.client.world == null
                || this.client.currentScreen != null
                || this.client.getOverlay() != null) {
            return;
        }

        Click click = new Click(0.0, 0.0, input);
        OrderedCrystalInput.recordInput(
                this.client.options.attackKey.matchesMouse(click),
                this.client.options.useKey.matchesMouse(click),
                this.client.player.getInventory().getSelectedSlot());
    }

    @Inject(method = "onMouseScroll", at = @At("HEAD"))
    private void kct$captureSlotBeforeScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        this.kct$slotBeforeScroll = this.client.player == null
                ? -1 : this.client.player.getInventory().getSelectedSlot();
    }

    @Inject(method = "onMouseScroll", at = @At("RETURN"))
    private void kct$recordScrollSlotChange(long window, double horizontal, double vertical, CallbackInfo ci) {
        if (this.kct$slotBeforeScroll < 0 || this.client.player == null) {
            this.kct$slotBeforeScroll = -1;
            return;
        }

        int selectedSlot = this.client.player.getInventory().getSelectedSlot();
        OrderedCrystalInput.recordSlotChange(this.kct$slotBeforeScroll, selectedSlot);
        this.kct$slotBeforeScroll = -1;
    }
}
