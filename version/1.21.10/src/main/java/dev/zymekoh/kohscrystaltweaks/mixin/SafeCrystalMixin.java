package dev.zymekoh.kohscrystaltweaks.mixin;

import dev.zymekoh.kohscrystaltweaks.config.KoHsCrystalTweaksConfig;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientPlayerInteractionManager.class)
public abstract class SafeCrystalMixin {
    @Inject(
            method = "attackBlock(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/util/math/Direction;)Z",
            at = @At("HEAD"),
            cancellable = true)
    private void kct$blockObsidianBreakWithCrystal(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        if (!KoHsCrystalTweaksConfig.get().safeCrystalEnabled) {
            return;
        }

        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null || !player.getMainHandStack().isOf(Items.END_CRYSTAL)) {
            return;
        }

        Block block = player.getEntityWorld().getBlockState(pos).getBlock();
        if (block == Blocks.OBSIDIAN) {
            cir.setReturnValue(false);
        }
    }

    @Inject(
            method = "updateBlockBreakingProgress(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/util/math/Direction;)Z",
            at = @At("HEAD"),
            cancellable = true)
    private void kct$blockObsidianProgressWithCrystal(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        if (!KoHsCrystalTweaksConfig.get().safeCrystalEnabled) {
            return;
        }

        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null || !player.getMainHandStack().isOf(Items.END_CRYSTAL)) {
            return;
        }

        Block block = player.getEntityWorld().getBlockState(pos).getBlock();
        if (block == Blocks.OBSIDIAN) {
            cir.setReturnValue(false);
        }
    }
}
