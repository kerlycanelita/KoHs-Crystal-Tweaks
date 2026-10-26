package com.zymekoh.crystaltweaks.mixin.client;

import com.zymekoh.crystaltweaks.client.practice.ServerView;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Block and light changes in the world of the server parked behind Crystal Practice reach the live
 * view of that world instead of the practice world's renderer, which draws another world; with no
 * view they are dropped, and the practice world's renderer rebuilds everything on the way back.
 */
@Mixin(LevelExtractor.class)
public abstract class PracticeViewExtractorMixin {
    @Inject(method = "blockChanged", at = @At("HEAD"), cancellable = true)
    private void crystalTweaks$blockChanged(BlockPos pos, int updateFlags, CallbackInfo callback) {
        if (ServerView.divert((LevelExtractor) (Object) this)) {
            LevelExtractor view = ServerView.extractor();
            if (view != null) {
                view.blockChanged(pos, updateFlags);
            }
            callback.cancel();
        }
    }

    @Inject(method = "setBlockDirty(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/state/BlockState;)V",
            at = @At("HEAD"), cancellable = true)
    private void crystalTweaks$blockDirty(BlockPos pos, BlockState oldState, BlockState newState, CallbackInfo callback) {
        if (ServerView.divert((LevelExtractor) (Object) this)) {
            LevelExtractor view = ServerView.extractor();
            if (view != null) {
                view.setBlockDirty(pos, oldState, newState);
            }
            callback.cancel();
        }
    }

    @Inject(method = "setSectionDirtyWithNeighbors", at = @At("HEAD"), cancellable = true)
    private void crystalTweaks$sectionAndNeighbors(int sectionX, int sectionY, int sectionZ, CallbackInfo callback) {
        if (ServerView.divert((LevelExtractor) (Object) this)) {
            LevelExtractor view = ServerView.extractor();
            if (view != null) {
                view.setSectionDirtyWithNeighbors(sectionX, sectionY, sectionZ);
            }
            callback.cancel();
        }
    }

    @Inject(method = "setSectionRangeDirty", at = @At("HEAD"), cancellable = true)
    private void crystalTweaks$sectionRange(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, CallbackInfo callback) {
        if (ServerView.divert((LevelExtractor) (Object) this)) {
            LevelExtractor view = ServerView.extractor();
            if (view != null) {
                view.setSectionRangeDirty(minX, minY, minZ, maxX, maxY, maxZ);
            }
            callback.cancel();
        }
    }

    @Inject(method = "setSectionDirty(III)V", at = @At("HEAD"), cancellable = true)
    private void crystalTweaks$section(int sectionX, int sectionY, int sectionZ, CallbackInfo callback) {
        if (ServerView.divert((LevelExtractor) (Object) this)) {
            LevelExtractor view = ServerView.extractor();
            if (view != null) {
                view.setSectionDirty(sectionX, sectionY, sectionZ);
            }
            callback.cancel();
        }
    }
}
