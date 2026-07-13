package dev.zymekoh.kohscrystaltweaks;

import dev.zymekoh.kohscrystaltweaks.config.KoHsCrystalTweaksConfig;
import dev.zymekoh.kohscrystaltweaks.core.CrystalPlacementFix;
import dev.zymekoh.kohscrystaltweaks.core.CrystalPredictor;
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

        ClientPlayConnectionEvents.JOIN.register((listener, sender, client) -> {
            CrystalPredictor.reset();
            CrystalPlacementFix.reset();
        });
        ClientPlayConnectionEvents.DISCONNECT.register((listener, client) -> {
            CrystalPredictor.reset();
            CrystalPlacementFix.reset();
        });

        ClientEntityEvents.ENTITY_LOAD.register((entity, level) -> CrystalPredictor.onEntityLoaded(entity));
        ClientEntityEvents.ENTITY_UNLOAD.register((entity, level) -> CrystalPredictor.onEntityUnloaded(entity));
        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            CrystalPredictor.clientTick();
            CrystalPlacementFix.clientTick();
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            CrystalPredictor.clearAll();
            CrystalPlacementFix.reset();
        });
    }
}
