package dev.zymekoh.kohscrystaltweaks.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import dev.zymekoh.kohscrystaltweaks.core.ForceCrystalPvpPriority;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyMapping.class)
public abstract class KeyMappingForceCrystalPvpPriorityMixin {
    @Inject(method = "click", at = @At("TAIL"))
    private static void kct$selectPrioritySlotFromBoundInput(InputConstants.Key input, CallbackInfo callback) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.screen != null || minecraft.getOverlay() != null) {
            return;
        }

        int selectedSlot = -1;
        for (int slot = 0; slot < minecraft.options.keyHotbarSlots.length; slot++) {
            KeyMapping hotbarKey = minecraft.options.keyHotbarSlots[slot];
            InputConstants.Key boundKey = ((KeyMappingAccessor) (Object) hotbarKey).kct$getBoundKey();
            if (boundKey.equals(input)) {
                // Keep the same final-slot ordering vanilla uses when bindings overlap.
                selectedSlot = slot;
            }
        }
        if (selectedSlot >= 0) {
            ForceCrystalPvpPriority.selectExplicitHotbarSlot(minecraft, selectedSlot);
        }
    }
}
