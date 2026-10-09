package com.zymekoh.crystaltweaks.client;

import com.zymekoh.crystaltweaks.client.benchmark.BenchmarkHooks;
import com.zymekoh.crystaltweaks.client.compat.OptimizerConflictDetector;
import com.zymekoh.crystaltweaks.client.practice.PracticeWorld;
import com.zymekoh.crystaltweaks.client.sound.CrystalSoundManager;
import com.zymekoh.crystaltweaks.core.CrystalBreakPrediction;
import com.zymekoh.crystaltweaks.core.CrystalOptimizerGuard;
import com.zymekoh.crystaltweaks.core.GhostCrystalTracker;
import com.zymekoh.crystaltweaks.core.ObsidianDebounce;
import com.zymekoh.crystaltweaks.core.ObsidianPlacementGuard;
import com.zymekoh.crystaltweaks.practice.PracticeSession;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

public final class CrystalVisualInitializer implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        CrystalVisualConfig.load();
        OptimizerConflictDetector.detectInBackground();
        ObsidianPlacementGuard.initialize();
        ObsidianDebounce.initialize();
        CrystalPlacementFeedback.initialize();
        CrystalSoundManager.initialize();
        GhostCrystalRenderer.initialize();
        BenchmarkHooks.initialize();
        PracticeSession.initialize();
        PracticeWorld.initialize();

        ClientEntityEvents.ENTITY_LOAD.register((entity, level) -> CrystalBreakPrediction.track(entity));
        ClientEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
            CrystalAfterglow.onRemoved(entity);
            CrystalSoundManager.onEntityUnloaded(entity);
            // The server confirmed the break, so the local hide mark is no longer needed.
            if (CrystalOptimizerGuard.optimizationsAllowed()) {
                CrystalBreakPrediction.forget(entity);
            }
        });
        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            CrystalSoundManager.tick();
            if (CrystalOptimizerGuard.optimizationsAllowed()) {
                long now = System.nanoTime();
                CrystalBreakPrediction.cleanup(now);
                GhostCrystalTracker.cleanup(now);
            }
        });
        ClientPlayConnectionEvents.JOIN.register((listener, sender, client) -> {
            CrystalAfterglow.reset();
            CrystalSoundManager.resetTracking();
            CrystalBreakPrediction.reset();
            GhostCrystalTracker.reset();
        });
        ClientPlayConnectionEvents.DISCONNECT.register((listener, client) -> {
            CrystalAfterglow.reset();
            CrystalSoundManager.resetTracking();
            CrystalBreakPrediction.reset();
            GhostCrystalTracker.reset();
            // On the client thread, where the stand-ins are drawn from.
            client.execute(CrystalConverter::forget);
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> CrystalSoundManager.cleanup());
    }
}
