package com.zymekoh.crystaltweaks.mixin.client;

import com.zymekoh.crystaltweaks.client.practice.PracticeHost;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Crystal Practice over a server (see {@link PracticeHost}). Every frame keeps the parked server
 * answered; what the parked server's own code asks of Minecraft, a new world, a camera, a
 * disconnection, goes to the parked server and not to the practice world on show. Nothing here acts
 * while no server is parked, and none of it touches what the server is sent.
 */
@Mixin(Minecraft.class)
public abstract class PracticeMinecraftMixin {
    @Inject(method = "runTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;renderFrame(Z)V"))
    private void crystalTweaks$ownFrame(boolean advanceGameTime, CallbackInfo callback) {
        PracticeHost.normalFrame();
    }

    @Inject(method = "renderFrame", at = @At("HEAD"))
    private void crystalTweaks$keepServerAnswered(boolean advanceGameTime, CallbackInfo callback) {
        PracticeHost.onFrame();
    }

    @Inject(method = "setLevel", at = @At("HEAD"), cancellable = true)
    private void crystalTweaks$serverLevel(ClientLevel level, CallbackInfo callback) {
        if (PracticeHost.serverSetsLevel(level)) {
            callback.cancel();
        }
    }

    @Inject(method = "setCameraEntity", at = @At("HEAD"), cancellable = true)
    private void crystalTweaks$serverCamera(Entity entity, CallbackInfo callback) {
        if (PracticeHost.serverSetsCamera(entity)) {
            callback.cancel();
        }
    }

    @Inject(method = "disconnect(Lnet/minecraft/client/gui/screens/Screen;ZZ)V", at = @At("HEAD"), cancellable = true)
    private void crystalTweaks$serverDisconnect(Screen screen, boolean keepResourcePacks, boolean stopSound, CallbackInfo callback) {
        if (PracticeHost.serverDisconnects(screen, keepResourcePacks)) {
            callback.cancel();
        }
    }

    @Inject(method = "clearClientLevel", at = @At("HEAD"), cancellable = true)
    private void crystalTweaks$serverBetweenWorlds(Screen screen, CallbackInfo callback) {
        if (PracticeHost.serverClearsLevel(screen)) {
            callback.cancel();
        }
    }

    @Inject(method = "disconnectFromWorld", at = @At("HEAD"), cancellable = true)
    private void crystalTweaks$leavePractice(Component message, CallbackInfo callback) {
        if (PracticeHost.practiceQuits()) {
            callback.cancel();
        }
    }

    @Inject(method = "pauseGame", at = @At("HEAD"), cancellable = true)
    private void crystalTweaks$practicePanel(boolean suppressPauseMenu, CallbackInfo callback) {
        if (PracticeHost.openPanel()) {
            callback.cancel();
        }
    }
}
