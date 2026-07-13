package dev.zymekoh.kohscrystaltweaks.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiPlayerGameMode.class)
public abstract class SafeCrystalMixin {
    @Shadow
    public abstract void stopDestroyBlock();

    @Inject(method = "startDestroyBlock", at = @At("HEAD"), cancellable = true)
    private void kct$protectCrystalBaseOnStart(
            BlockPos position,
            Direction direction,
            CallbackInfoReturnable<Boolean> callback) {
        if (shouldProtect(position)) {
            callback.setReturnValue(false);
        }
    }

    @Inject(method = "continueDestroyBlock", at = @At("HEAD"), cancellable = true)
    private void kct$protectCrystalBaseWhileBreaking(
            BlockPos position,
            Direction direction,
            CallbackInfoReturnable<Boolean> callback) {
        if (shouldProtect(position)) {
            stopDestroyBlock();
            callback.setReturnValue(false);
        }
    }

    private static boolean shouldProtect(BlockPos position) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) {
            return false;
        }

        boolean holdingCrystal = player.getMainHandItem().is(Items.END_CRYSTAL)
                || player.getOffhandItem().is(Items.END_CRYSTAL);
        if (!holdingCrystal) {
            return false;
        }

        BlockState state = minecraft.level.getBlockState(position);
        return state.is(Blocks.OBSIDIAN) || state.is(Blocks.CRYING_OBSIDIAN);
    }
}
