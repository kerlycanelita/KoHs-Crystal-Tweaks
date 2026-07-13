package dev.zymekoh.kohscrystaltweaks.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import dev.zymekoh.kohscrystaltweaks.core.OrderedCrystalInput;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class MouseOrderedCrystalInputMixin {
    @Shadow
    @Final
    private Minecraft minecraft;

    @Unique
    private int kct$slotBeforeScroll = -1;

    @Inject(method = "onButton", at = @At("TAIL"))
    private void kct$recordOrderedMouseInput(long window, MouseButtonInfo input, int action, CallbackInfo ci) {
        if (action != InputConstants.PRESS
                || this.minecraft.player == null
                || this.minecraft.level == null
                || this.minecraft.screen != null
                || this.minecraft.getOverlay() != null) {
            return;
        }

        MouseButtonEvent click = new MouseButtonEvent(0.0D, 0.0D, input);
        OrderedCrystalInput.recordInput(
                this.minecraft.options.keyAttack.matchesMouse(click),
                this.minecraft.options.keyUse.matchesMouse(click),
                this.minecraft.player.getInventory().getSelectedSlot());
    }

    @Inject(method = "onScroll", at = @At("HEAD"))
    private void kct$captureSlotBeforeScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        this.kct$slotBeforeScroll = this.minecraft.player == null
                ? -1 : this.minecraft.player.getInventory().getSelectedSlot();
    }

    @Inject(method = "onScroll", at = @At("RETURN"))
    private void kct$recordScrollSlotChange(long window, double horizontal, double vertical, CallbackInfo ci) {
        if (this.kct$slotBeforeScroll < 0 || this.minecraft.player == null) {
            this.kct$slotBeforeScroll = -1;
            return;
        }

        int selectedSlot = this.minecraft.player.getInventory().getSelectedSlot();
        OrderedCrystalInput.recordSlotChange(this.kct$slotBeforeScroll, selectedSlot);
        this.kct$slotBeforeScroll = -1;
    }
}
