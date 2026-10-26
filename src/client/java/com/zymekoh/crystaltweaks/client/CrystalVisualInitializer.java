package com.zymekoh.crystaltweaks.client;

import com.zymekoh.crystaltweaks.client.benchmark.BenchmarkHooks;
import com.zymekoh.crystaltweaks.client.fight.DeathRecap;
import com.zymekoh.crystaltweaks.client.fight.FightHud;
import com.zymekoh.crystaltweaks.client.practice.PracticeHost;
import com.zymekoh.crystaltweaks.client.practice.PracticeWorld;
import com.zymekoh.crystaltweaks.client.sound.CrystalSoundManager;
import com.zymekoh.crystaltweaks.core.CrystalBreakPrediction;
import com.zymekoh.crystaltweaks.core.FastPlacement;
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
        ObsidianPlacementGuard.initialize();
        ObsidianDebounce.initialize();
        CrystalPlacementFeedback.initialize();
        CrystalSoundManager.initialize();
        GhostCrystalRenderer.initialize();
        BenchmarkHooks.initialize();
        FightHud.initialize();
        PracticeSession.initialize();
        PracticeWorld.initialize();
        PracticeHost.initialize();

        ClientEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (!PracticeHost.serverSide()) {
                CrystalBreakPrediction.track(entity);
            }
        });
        ClientEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
            if (PracticeHost.serverSide()) {
                // The server parked behind Crystal Practice: its crystals are not on show.
                return;
            }
            CrystalAfterglow.onRemoved(entity);
            CrystalSoundManager.onEntityUnloaded(entity);
            if (entity instanceof net.minecraft.world.entity.boss.enderdragon.EndCrystal crystal) {
                // Whose crystal it was, for the blast that follows it in the death recap.
                DeathRecap.crystalGone(crystal.getX(), crystal.getY(), crystal.getZ(), !CrystalOwnership.useOtherProfile(crystal),
                        System.nanoTime());
            }
            // The server confirmed the break: the hide mark goes, and a click held for it is let through.
            CrystalBreakPrediction.forget(entity);
        });
        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            CrystalSoundManager.tick();
            long now = System.nanoTime();
            CrystalBreakPrediction.cleanup(now);
            GhostCrystalTracker.cleanup(now);
            FastPlacement.tick(now);
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
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            PracticeHost.shutdown();
            CrystalSoundManager.cleanup();
        });
    }
}
