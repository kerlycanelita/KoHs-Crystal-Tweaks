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
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mouse.class)
public abstract class MouseOrderedCrystalInputMixin {
    @Shadow
    @Final
    private MinecraftClient client;

    @Inject(method = "onMouseButton", at = @At("TAIL"))
    private void kct$recordOrderedMouseInput(long window, MouseInput input, int action, CallbackInfo ci) {
        if (action == InputUtil.GLFW_RELEASE
                || this.client.player == null
                || this.client.world == null
                || this.client.currentScreen != null
                || this.client.getOverlay() != null) {
            return;
        }

        Click click = new Click(0.0, 0.0, input);
        OrderedCrystalInput.record(
                this.client.options.attackKey.matchesMouse(click),
                this.client.options.useKey.matchesMouse(click));
    }
}
