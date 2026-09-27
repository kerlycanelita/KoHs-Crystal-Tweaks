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
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;

/**
 * Runs Crystal Practice inside a practice world's integrated server: the rules, the arena, the
 * player's kit and the bot.
 *
 * <p>It binds only to a singleplayer server whose save folder is one of the practice worlds', so no
 * other world, and never a multiplayer server, is ever touched. Everything here runs on the server
 * thread through Fabric's server events.</p>
 *
 * <p>Each death starts a new round: the arena is rebuilt, craters and all, and both sides come back
 * with a full kit.</p>
 */
public final class PracticeSession {
    /** Save-folder name of the flat practice world; natural ones add their biome after an underscore. */
    public static final String LEVEL_ID = "crystal_tweaks_practice";
    /** Layers of ground under the floor, the floor included. */
    public static final int LAYERS = 200;
    private static final int BOT_RESPAWN_TICKS = 60;
    private static final int UPKEEP_TICKS = 100;

    private static boolean initialized;
    private static PracticeSession active;
    /** Read by the game thread, which never touches {@link #active} itself. */
    private static volatile boolean running;

    private final MinecraftServer server;
    private final ServerLevel level;
    private final PracticeSettings settings;
    private final KitLayout kit;
    private final PracticeArena arena;
    private PracticeBot bot;
    private int botRespawn = -1;
    private int kills;
    private int deaths;
    private int upkeepTimer;

    private PracticeSession(MinecraftServer server, PracticeSettings settings, KitLayout kit) {
        this.server = server;
        this.level = server.overworld();
        this.settings = settings;
        this.kit = kit;
        int floor = this.level.getMinY() + LAYERS - 1;
        this.arena = new PracticeArena(this.level, settings.worldType, settings.biome, floor, this.level.getSeed());
    }

    public static void initialize() {
        if (initialized) {
            return;
        }
        initialized = true;
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            String folder = practiceFolder(server);
            if (folder != null) {
                PracticeSettings chosen = CrystalVisualConfig.practice();
                active = new PracticeSession(server, groundOf(folder, chosen), CrystalVisualConfig.practiceKit(chosen.preset));
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

    /** True while a practice world's integrated server is up. Any thread. */
    public static boolean running() {
        return running;
    }

    /** Save-folder name of the practice world for these settings. */
    public static String levelId(PracticeSettings.WorldType type, PracticeSettings.Biome biome) {
        return type == PracticeSettings.WorldType.NATURAL ? LEVEL_ID + "_" + biome.id : LEVEL_ID;
    }

    /**
     * The folder name when this is the integrated server of a practice world, else {@code null}.
     * Only folders this mod creates count: the flat one and one per natural biome.
     */
    static String practiceFolder(MinecraftServer server) {
        if (!(server instanceof IntegratedServer)) {
            return null;
        }
        Path folder = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().getFileName();
        if (folder == null) {
            return null;
        }
        String name = folder.toString();
        if (name.equals(LEVEL_ID)) {
            return name;
        }
        for (PracticeSettings.Biome biome : PracticeSettings.Biome.values()) {
            if (name.equals(LEVEL_ID + "_" + biome.id)) {
                return name;
            }
        }
        return null;
    }

    /**
     * The ground follows the world, not the settings: a natural world opened from the world list is
     * always its own biome, and the flat one is the netherite flat or the hole arena.
     */
    private static PracticeSettings groundOf(String folder, PracticeSettings chosen) {
        if (folder.equals(LEVEL_ID)) {
            PracticeSettings.WorldType type = chosen.worldType == PracticeSettings.WorldType.NATURAL
                    ? PracticeSettings.WorldType.FLAT
                    : chosen.worldType;
            return chosen.on(type, chosen.biome);
        }
        String suffix = folder.substring(LEVEL_ID.length() + 1);
        return chosen.on(PracticeSettings.WorldType.NATURAL, PracticeSettings.Biome.parse(suffix));
    }

    /** Rules for fast, clean practice, and an arena rebuilt from scratch. */
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
        // Blasts in the natural arena would otherwise leave dirt and sand all over the fight.
        rules.set(GameRules.BLOCK_DROPS, false, this.server);
        // Always noon, with time stopped: the best light to read crystals and anchors by. Through the
        // command because 26.1 moved the day time into world clocks and took the setter away.
        this.server.getCommands().performPrefixedCommand(this.server.createCommandSourceStack().withSuppressedOutput(),
                "time set 6000");
        newRound(true);
    }

    /** Clears the last round's leftovers and rebuilds the ground. */
    private void newRound(boolean removeBot) {
        List<Entity> leftovers = new ArrayList<>();
        for (Entity entity : this.level.getAllEntities()) {
            if (entity instanceof EndCrystal || entity instanceof ItemEntity || entity instanceof ExperienceOrb
                    || entity instanceof Projectile
                    // removeTag says whether it had the tag, and the tag API itself was renamed in 26.1.
                    || (removeBot && entity instanceof Mannequin && entity.removeTag(PracticeBot.TAG))) {
                leftovers.add(entity);
            }
        }
        leftovers.forEach(Entity::discard);
        if (removeBot) {
            this.bot = null;
            this.botRespawn = -1;
        }
        long started = System.nanoTime();
        this.arena.rebuild();
        CrystalTweaksClient.LOGGER.debug("Crystal Practice rebuilt its {} arena in {} ms", this.settings.worldType,
                (System.nanoTime() - started) / 1_000_000L);
    }

    private void join(ServerPlayer player) {
        setUpPlayer(player);
        boolean spanish = CrystalUi.spanish();
        String ground = this.settings.worldType == PracticeSettings.WorldType.NATURAL
                ? this.settings.worldType.label(spanish) + " · " + this.settings.biome.label(spanish)
                : this.settings.worldType.label(spanish);
        player.sendSystemMessage(Component.literal(spanish ? "Práctica de cristales" : "Crystal Practice")
                .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD)
                .append(Component.literal(" · " + ground + " · " + this.settings.preset.label(spanish))
                        .withStyle(ChatFormatting.GRAY)));
        player.sendSystemMessage(Component.literal(this.settings.describe(spanish)).withStyle(ChatFormatting.GRAY));
        player.sendSystemMessage(Component.literal(this.settings.bot
                ? (spanish
                        ? "Bot " + this.settings.difficulty.label(true) + " con tu mismo kit. Cristales, obsidiana, anclas y piedra luminosa se reponen solos; cada muerte empieza una ronda nueva."
                        : this.settings.difficulty.label(false) + " bot with your same kit. Crystals, obsidian, anchors and glowstone refill by themselves; every death starts a new round.")
                : (spanish
                        ? "Sin bot: practica colocando y rompiendo. Lo que se gasta se repone solo."
                        : "No bot: practise placing and breaking. What you use refills by itself."))
                .withStyle(ChatFormatting.GRAY));
        if (this.settings.bot && this.bot == null) {
            spawnBot(player);
        }
    }

    private void respawn(ServerPlayer player) {
        newRound(true);
        setUpPlayer(player);
        if (this.settings.bot) {
            spawnBot(player);
        }
    }

    private void setUpPlayer(ServerPlayer player) {
        Vec3 spawn = this.arena.spawn();
        player.teleportTo(this.level, spawn.x, spawn.y, spawn.z, Set.of(), 0.0F, 0.0F, true);
        player.setGameMode(GameType.SURVIVAL);
        player.removeAllEffects();
        player.setHealth(player.getMaxHealth());
        player.getFoodData().setFoodLevel(20);
        PracticeKit.equipPlayer(player, this.settings, this.kit);
    }

    /** Somewhere open about nine blocks from the player, on the arena's ground. */
    private void spawnBot(ServerPlayer near) {
        double x = 0.5D;
        double z = 8.5D;
        for (int attempt = 0; attempt < 12; attempt++) {
            double angle = this.level.getRandom().nextDouble() * Math.PI * 2.0D;
            double candidateX = near == null ? 0.5D : near.getX() + Math.cos(angle) * 9.0D;
            double candidateZ = near == null ? 8.5D : near.getZ() + Math.sin(angle) * 9.0D;
            int blockX = (int) Math.floor(candidateX);
            int blockZ = (int) Math.floor(candidateZ);
            if (this.arena.standable(blockX, blockZ)) {
                x = blockX + 0.5D;
                z = blockZ + 0.5D;
                break;
            }
        }
        int blockX = (int) Math.floor(x);
        int blockZ = (int) Math.floor(z);
        double y = this.arena.groundY(blockX, blockZ) + 1.0D;
        float yaw = near == null ? 0.0F : (float) Math.toDegrees(Math.atan2(near.getZ() - z, near.getX() - x)) - 90.0F;
        this.bot = PracticeBot.spawn(this.level, this.settings, this.kit, x, y, z, yaw);
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
        if (++this.upkeepTimer >= UPKEEP_TICKS) {
            this.upkeepTimer = 0;
            if (target != null) {
                PracticeKit.restock(target, this.kit);
            }
            // Dropped items would pile up over a long session: five seconds is enough to pick one up.
            List<ItemEntity> old = new ArrayList<>();
            for (Entity entity : this.level.getAllEntities()) {
                if (entity instanceof ItemEntity item && item.getAge() > 100) {
                    old.add(item);
                }
            }
            old.forEach(Entity::discard);
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
