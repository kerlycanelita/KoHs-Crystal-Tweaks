package com.zymekoh.crystaltweaks.mixin.client;

import java.util.Map;
import java.util.UUID;
import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.client.gui.components.LerpingBossEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The boss bars on show, kept apart for the practice world and for the server parked behind it. */
@Mixin(BossHealthOverlay.class)
public interface BossHealthOverlayAccessor {
    @Accessor("events")
    Map<UUID, LerpingBossEvent> crystalTweaks$events();

    @Mutable
    @Accessor("events")
    void crystalTweaks$setEvents(Map<UUID, LerpingBossEvent> events);
}
