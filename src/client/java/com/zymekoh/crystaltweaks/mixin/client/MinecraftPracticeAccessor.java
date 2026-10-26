package com.zymekoh.crystaltweaks.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.chat.report.ReportingContext;
import net.minecraft.client.server.IntegratedServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** The per-world fields of Minecraft that Crystal Practice switches while a server is parked. */
@Mixin(Minecraft.class)
public interface MinecraftPracticeAccessor {
    @Accessor("rightClickDelay")
    int crystalTweaks$rightClickDelay();

    @Accessor("rightClickDelay")
    void crystalTweaks$setRightClickDelay(int delay);

    @Accessor("missTime")
    int crystalTweaks$missTime();

    @Accessor("missTime")
    void crystalTweaks$setMissTime(int time);

    @Accessor("isLocalServer")
    boolean crystalTweaks$localServer();

    @Accessor("isLocalServer")
    void crystalTweaks$setLocalServer(boolean local);

    @Accessor("singleplayerServer")
    IntegratedServer crystalTweaks$singleplayerServer();

    @Accessor("singleplayerServer")
    void crystalTweaks$setSingleplayerServer(IntegratedServer server);

    @Accessor("reportingContext")
    ReportingContext crystalTweaks$reportingContext();

    @Accessor("reportingContext")
    void crystalTweaks$setReportingContext(ReportingContext context);

    @Invoker("updateLevelInEngines")
    void crystalTweaks$updateLevelInEngines(ClientLevel level);
}
