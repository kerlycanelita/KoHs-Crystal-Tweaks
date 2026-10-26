package com.zymekoh.crystaltweaks.mixin.client;

import com.zymekoh.crystaltweaks.client.practice.PracticeHost;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.TransferState;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A transfer the parked server orders while the player practises does not start over the practice world. */
@Mixin(ConnectScreen.class)
public abstract class PracticeConnectScreenMixin {
    @Inject(method = "startConnecting", at = @At("HEAD"), cancellable = true)
    private static void crystalTweaks$noTransferWhileAway(Screen parent, Minecraft minecraft, ServerAddress address, ServerData data,
            boolean quickPlay, TransferState transfer, CallbackInfo callback) {
        if (PracticeHost.serverTransfers()) {
            callback.cancel();
        }
    }
}
