package dev.zymekoh.kohscrystaltweaks;

import dev.zymekoh.kohscrystaltweaks.config.KoHsCrystalTweaksConfig;
import dev.zymekoh.kohscrystaltweaks.core.CrystalPlacementFix;
import dev.zymekoh.kohscrystaltweaks.core.CrystalPredictor;
import dev.zymekoh.kohscrystaltweaks.marlow.MarlowOptimizerCompat;
import dev.zymekoh.kohscrystaltweaks.sound.CrystalSoundManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

public final class KoHsCrystalTweaksClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        KoHsCrystalTweaksConfig config = KoHsCrystalTweaksConfig.get();
        CrystalPredictor.setEnabled(config.clientSideCrystalsEnabled);
        MarlowOptimizerCompat.initClient();

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            CrystalPredictor.reset();
            CrystalPlacementFix.reset();
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            CrystalPredictor.reset();
            CrystalPlacementFix.reset();
        });

        ClientEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (CrystalPredictor.isEnabled()) {
                CrystalPredictor.onEntityLoaded(entity);
            }
        });

        ClientEntityEvents.ENTITY_UNLOAD.register((entity, world) -> {
            if (CrystalPredictor.isEnabled()) {
                CrystalPredictor.onEntityUnloaded(entity);
            }
        });

        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            CrystalPredictor.clientTick();
            CrystalPlacementFix.clientTick();
            CrystalSoundManager.tick();
        });

        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            CrystalPredictor.clearAll();
            CrystalPlacementFix.reset();
            CrystalSoundManager.cleanup();
        });

        // Initialize sound system
        CrystalSoundManager.init();
    }
}
