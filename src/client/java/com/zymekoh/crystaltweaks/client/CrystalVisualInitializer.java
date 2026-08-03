package com.zymekoh.crystaltweaks.client;

import com.zymekoh.crystaltweaks.client.sound.CrystalSoundManager;
import com.zymekoh.crystaltweaks.core.ObsidianPlacementGuard;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

public final class CrystalVisualInitializer implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        CrystalVisualConfig.load();
        ObsidianPlacementGuard.initialize();
        CrystalSoundManager.initialize();

        ClientEntityEvents.ENTITY_UNLOAD.register((entity, level) ->
                CrystalSoundManager.onEntityUnloaded(entity));
        ClientTickEvents.START_CLIENT_TICK.register(client -> CrystalSoundManager.tick());
        ClientPlayConnectionEvents.JOIN.register((listener, sender, client) ->
                CrystalSoundManager.resetTracking());
        ClientPlayConnectionEvents.DISCONNECT.register((listener, client) ->
                CrystalSoundManager.resetTracking());
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> CrystalSoundManager.cleanup());
    }
}
