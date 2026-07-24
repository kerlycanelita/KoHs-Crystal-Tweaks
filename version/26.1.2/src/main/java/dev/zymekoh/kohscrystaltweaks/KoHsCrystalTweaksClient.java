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
        CrystalPredictor.setEnabled(config.clientSideCrystalsEnabled);
        SafeCrystalGuard.init();

        ClientPlayConnectionEvents.JOIN.register((listener, sender, client) -> {
            CrystalPredictor.reset();
            CrystalSoundManager.resetTracking();
        });
        ClientPlayConnectionEvents.DISCONNECT.register((listener, client) -> {
            CrystalPredictor.reset();
            CrystalSoundManager.resetTracking();
        });

        ClientEntityEvents.ENTITY_LOAD.register((entity, level) -> CrystalPredictor.onEntityLoaded(entity));
        ClientEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
            CrystalSoundManager.onEntityUnloaded(entity);
        });
        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            CrystalPredictor.clientTick();
            CrystalSoundManager.tick();
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            CrystalPredictor.clearAll();
            CrystalSoundManager.cleanup();
        });
        CrystalSoundManager.init();
    }
}
