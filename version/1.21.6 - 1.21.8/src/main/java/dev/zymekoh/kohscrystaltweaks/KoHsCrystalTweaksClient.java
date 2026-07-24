package dev.zymekoh.kohscrystaltweaks;

import dev.zymekoh.kohscrystaltweaks.compat.IncompatibilityManager;
import dev.zymekoh.kohscrystaltweaks.config.KoHsCrystalTweaksConfig;
import dev.zymekoh.kohscrystaltweaks.core.CrystalPredictor;
import dev.zymekoh.kohscrystaltweaks.core.SafeCrystalGuard;
import dev.zymekoh.kohscrystaltweaks.gui.IncompatibilityScreen;
import dev.zymekoh.kohscrystaltweaks.sound.CrystalSoundManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

public final class KoHsCrystalTweaksClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        IncompatibilityManager.initialize();
        if (IncompatibilityManager.isBlocked()) {
            IncompatibilityScreen.registerBlocker();
            return;
        }

        KoHsCrystalTweaksConfig config = KoHsCrystalTweaksConfig.get();
        SafeCrystalGuard.initialize();
        CrystalPredictor.setEnabled(config.clientSideCrystalsEnabled);

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            CrystalPredictor.reset();
            CrystalSoundManager.resetTracking();
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            CrystalPredictor.reset();
            CrystalSoundManager.resetTracking();
        });

        ClientEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (CrystalPredictor.isEnabled()) {
                CrystalPredictor.onEntityLoaded(entity);
            }
        });

        ClientEntityEvents.ENTITY_UNLOAD.register((entity, world) -> {
            CrystalSoundManager.onEntityUnloaded(entity);
            if (CrystalPredictor.isEnabled()) {
                CrystalPredictor.onEntityUnloaded(entity);
            }
        });

        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            CrystalPredictor.clientTick();
            CrystalSoundManager.tick();
        });

        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            CrystalPredictor.clearAll();
            CrystalSoundManager.cleanup();
        });

        // Initialize sound system
        CrystalSoundManager.init();
    }
}
