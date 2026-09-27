package com.zymekoh.crystaltweaks.practice;

import com.zymekoh.crystaltweaks.CrystalTweaksClient;
import com.zymekoh.crystaltweaks.client.CrystalUi;
import com.zymekoh.crystaltweaks.client.CrystalVisualConfig;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Runs Crystal Practice inside the practice world's integrated server: the rules, the arena, the
 * player's kit and the bot.
 *
 * <p>It binds only to a singleplayer server whose save folder is the practice world's, so no other
 * world, and never a multiplayer server, is ever touched. Everything here runs on the server thread
 * through Fabric's server events.</p>
 */
public final class PracticeSession {
    /** Save-folder name of the practice world, which is how the server recognises it. */
    public static final String LEVEL_ID = "crystal_tweaks_practice";
    /** Layers of ground under the obsidian: bedrock, stone, dirt and the obsidian floor itself. */
    public static final int LAYERS = 200;
    /** How far around the spawn the arena is swept clean on each visit. */
    private static final int ARENA_RADIUS = 24;
    private static final int BOT_RESPAWN_TICKS = 60;

    private static boolean initialized;
    private static PracticeSession active;
    /** Read by the game thread, which never touches {@link #active} itself. */
    private static volatile boolean running;

    private final MinecraftServer server;
    private final ServerLevel level;
    private final PracticeSettings settings;
    private final int floorY;
    private PracticeBot bot;
    private int botRespawn = -1;
    private int kills;
    private int deaths;
    private int restockTimer;

    private PracticeSession(MinecraftServer server, PracticeSettings settings) {
        this.server = server;
        this.level = server.overworld();
        this.settings = settings;
        this.floorY = this.level.getMinY() + LAYERS - 1;
    }

    public static void initialize() {
        if (initialized) {
            return;
        }
        initialized = true;
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            if (isPracticeWorld(server)) {
                active = new PracticeSession(server, CrystalVisualConfig.practice());
                active.prepare();
                running = true;
            }
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            if (active != null && active.server == server) {
                running = false;
                active.stop();
                active = null;
            }
        });
        ServerPlayConnectionEvents.JOIN.register((listener, sender, server) -> {
            if (active != null && active.server == server) {
                active.join(listener.player);
            }
        });
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            if (active != null && active.server == newPlayer.level().getServer()) {
                active.respawn(newPlayer);
            }
        });
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (active != null && entity.level() == active.level && entity instanceof ServerPlayer player) {
                active.playerDied(player);
            }
        });
        ServerTickEvents.START_SERVER_TICK.register(server -> {
            if (active != null && active.server == server) {
                active.beforeTick();
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (active != null && active.server == server) {
                active.afterTick();
            }
        });
    }

    /** True while the practice world's integrated server is up. Any thread. */
    public static boolean running() {
        return running;
    }

    /** True for the integrated server of the practice world, and only for it. */
    static boolean isPracticeWorld(MinecraftServer server) {
        if (!(server instanceof IntegratedServer)) {
            return false;
        }
        Path folder = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().getFileName();
        return folder != null && folder.toString().equals(LEVEL_ID);
    }

    /** Rules for fast, clean practice, and an arena swept of the last visit's leftovers. */
    private void prepare() {
        GameRules rules = this.level.getGameRules();
        rules.set(GameRules.KEEP_INVENTORY, true, this.server);
        rules.set(GameRules.IMMEDIATE_RESPAWN, true, this.server);
        rules.set(GameRules.ADVANCE_TIME, false, this.server);
        rules.set(GameRules.ADVANCE_WEATHER, false, this.server);
        rules.set(GameRules.SPAWN_MOBS, false, this.server);
        rules.set(GameRules.SPAWN_MONSTERS, false, this.server);
        rules.set(GameRules.SPAWN_PHANTOMS, false, this.server);
        rules.set(GameRules.SPAWN_PATROLS, false, this.server);
        rules.set(GameRules.SPAWN_WANDERING_TRADERS, false, this.server);
        rules.set(GameRules.SHOW_ADVANCEMENT_MESSAGES, false, this.server);
        rules.set(GameRules.RESPAWN_RADIUS, 0, this.server);
        clearLeftovers();
        resetArena();
    }

    private void clearLeftovers() {
        List<Entity> leftovers = new ArrayList<>();
        for (Entity entity : this.level.getAllEntities()) {
            if (entity instanceof EndCrystal || entity instanceof ItemEntity || entity instanceof ExperienceOrb
                    || (entity instanceof Mannequin && entity.removeTag(PracticeBot.TAG))) {
                leftovers.add(entity);
            }
        }
        leftovers.forEach(Entity::discard);
    }

    /**
     * Restores the obsidian floor and clears what was built on it last time, around the spawn.
     * About seventeen thousand block reads, once per visit; only changed blocks are written.
     */
    private void resetArena() {
        BlockState obsidian = Blocks.OBSIDIAN.defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();
        for (int x = -ARENA_RADIUS; x <= ARENA_RADIUS; x++) {
            for (int z = -ARENA_RADIUS; z <= ARENA_RADIUS; z++) {
                position.set(x, this.floorY, z);
                if (!this.level.getBlockState(position).is(Blocks.OBSIDIAN)) {
                    this.level.setBlock(position, obsidian, 2);
                }
                for (int y = this.floorY + 1; y <= this.floorY + 6; y++) {
                    position.set(x, y, z);
                    if (!this.level.getBlockState(position).isAir()) {
                        this.level.setBlock(position, air, 2);
                    }
                }
            }
        }
    }

    private void join(ServerPlayer player) {
        setUpPlayer(player);
        boolean spanish = CrystalUi.spanish();
        player.sendSystemMessage(Component.literal(spanish ? "Práctica de cristales" : "Crystal Practice")
                .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD)
                .append(Component.literal(" · " + this.settings.describe(spanish)).withStyle(ChatFormatting.GRAY)));
        player.sendSystemMessage(Component.literal(this.settings.bot
                ? (spanish
                        ? "Bot " + this.settings.difficulty.label(true) + " con tu mismo equipo. Cristales y obsidiana se reponen solos."
                        : this.settings.difficulty.label(false) + " bot with your same gear. Crystals and obsidian refill by themselves.")
                : (spanish
                        ? "Sin bot: practica colocando y rompiendo. Cristales y obsidiana se reponen solos."
                        : "No bot: practise placing and breaking. Crystals and obsidian refill by themselves."))
                .withStyle(ChatFormatting.GRAY));
        if (this.settings.bot && this.bot == null) {
            spawnBot(player);
        }
    }

    private void respawn(ServerPlayer player) {
        setUpPlayer(player);
    }

    private void setUpPlayer(ServerPlayer player) {
        player.teleportTo(this.level, 0.5D, this.floorY + 1.0D, 0.5D, Set.of(), 0.0F, 0.0F, true);
        player.setGameMode(GameType.SURVIVAL);
        player.removeAllEffects();
        player.setHealth(player.getMaxHealth());
        player.getFoodData().setFoodLevel(20);
        PracticeKit.equipPlayer(player, this.settings);
    }

    private void spawnBot(ServerPlayer near) {
        double angle = this.level.getRandom().nextDouble() * Math.PI * 2.0D;
        double x = near == null ? 0.5D : near.getX() + Math.cos(angle) * 9.0D;
        double z = near == null ? 8.5D : near.getZ() + Math.sin(angle) * 9.0D;
        x = Math.max(-ARENA_RADIUS + 2, Math.min(ARENA_RADIUS - 2, x));
        z = Math.max(-ARENA_RADIUS + 2, Math.min(ARENA_RADIUS - 2, z));
        this.bot = PracticeBot.spawn(this.level, this.settings, x, this.floorY + 1.0D, z, 0.0F);
        if (this.bot == null) {
            CrystalTweaksClient.LOGGER.warn("Crystal Practice could not create its bot");
        }
    }

    private ServerPlayer target() {
        for (ServerPlayer player : this.server.getPlayerList().getPlayers()) {
            if (player.level() == this.level && player.isAlive() && !player.isSpectator() && !player.isCreative()) {
                return player;
            }
        }
        return null;
    }

    private void beforeTick() {
        if (this.bot != null) {
            this.bot.steer(target());
        }
    }

    private void afterTick() {
        ServerPlayer target = target();
        if (this.bot != null) {
            if (this.bot.alive()) {
                this.bot.act(target);
            } else if (this.botRespawn < 0) {
                this.kills++;
                this.botRespawn = BOT_RESPAWN_TICKS;
                announce(CrystalUi.spanish() ? "¡Bot eliminado! " + score(true) : "Bot eliminated! " + score(false));
            }
        }
        if (this.botRespawn >= 0 && --this.botRespawn < 0) {
            if (this.bot != null && !this.bot.removed()) {
                this.bot.discard();
            }
            this.bot = null;
            if (this.settings.bot && target != null) {
                spawnBot(target);
            }
        }
        if (++this.restockTimer >= 100) {
            this.restockTimer = 0;
            if (target != null) {
                PracticeKit.restock(target);
            }
        }
    }

    private void playerDied(ServerPlayer player) {
        this.deaths++;
        String message = CrystalUi.spanish() ? "Has caído. " + score(true) : "You died. " + score(false);
        player.sendSystemMessage(Component.literal(message).withStyle(ChatFormatting.RED));
    }

    private String score(boolean spanish) {
        return spanish ? "Bajas " + this.kills + " · Muertes " + this.deaths : "Kills " + this.kills + " · Deaths " + this.deaths;
    }

    private void announce(String text) {
        for (ServerPlayer player : this.server.getPlayerList().getPlayers()) {
            player.connection.send(new ClientboundSetActionBarTextPacket(Component.literal(text).withStyle(ChatFormatting.GOLD)));
        }
    }

    private void stop() {
        if (this.bot != null) {
            // Not saved with the world: next visit starts with a fresh bot on the current settings.
            this.bot.discard();
            this.bot = null;
        }
    }
}
