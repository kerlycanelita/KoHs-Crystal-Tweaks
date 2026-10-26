package com.zymekoh.crystaltweaks.client.practice;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;

/** 26.2 and 26.3: the screen lives in the GUI, the boss bars and the chat in its HUD; see the 26.1 copy. */
final class PracticeGlue {
    private PracticeGlue() {
    }

    static Screen rawScreen(Minecraft minecraft) {
        return ((PracticeGuiAccess) minecraft.gui).crystalTweaks$screen();
    }

    static void setRawScreen(Minecraft minecraft, Screen screen) {
        ((PracticeGuiAccess) minecraft.gui).crystalTweaks$setScreen(screen);
    }

    static void setScreen(Minecraft minecraft, Screen screen) {
        minecraft.gui.setScreen(screen);
    }

    static Camera camera(Minecraft minecraft) {
        return minecraft.gameRenderer.mainCamera();
    }

    static BossHealthOverlay bossOverlay(Minecraft minecraft) {
        return minecraft.gui.hud.getBossOverlay();
    }

    static void chat(Minecraft minecraft, Component message) {
        minecraft.gui.hud.getChat().addClientSystemMessage(message);
    }

    /**
     * Reports every chunk {@code level} already holds to the renderer that is taking it back. Since
     * 26.2 the renderer learns which chunks are loaded only as they arrive, and forgets them all when
     * it changes world, so a world it returns to would draw entities over no blocks at all. The chunk
     * cache's own lists of arrivals are refilled with everything it holds, as if it had just come in.
     */
    static void announceChunks(ClientLevel level, int centerX, int centerZ) {
        ClientChunkCache cache = level.getChunkSource();
        LongOpenHashSet loaded = cache.addedLoadedChunks();
        LongOpenHashSet empty = cache.addedEmptySections();
        // What came and went while it was away means nothing to a renderer starting over.
        loaded.clear();
        empty.clear();
        cache.removedLoadedChunks().clear();
        cache.removedEmptySections().clear();
        // The cache never holds chunks further than 32 chunks of view distance and its margin of 3.
        int radius = 35;
        for (int z = centerZ - radius; z <= centerZ + radius; z++) {
            for (int x = centerX - radius; x <= centerX + radius; x++) {
                LevelChunk chunk = cache.getChunk(x, z, ChunkStatus.FULL, false);
                if (chunk == null) {
                    continue;
                }
                loaded.add(ChunkPos.pack(x, z));
                LevelChunkSection[] sections = chunk.getSections();
                for (int index = 0; index < sections.length; index++) {
                    if (sections[index].hasOnlyAir()) {
                        empty.add(SectionPos.asLong(x, chunk.getSectionYFromSectionIndex(index), z));
                    }
                }
            }
        }
    }
}
