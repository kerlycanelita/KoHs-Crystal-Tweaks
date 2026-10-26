package com.zymekoh.crystaltweaks.practice;

import com.zymekoh.crystaltweaks.CrystalTweaksClient;
import com.zymekoh.crystaltweaks.client.CrystalUi;
import com.zymekoh.crystaltweaks.client.CrystalVisualConfig;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
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
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
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
 * with a full kit. /rtp moves the round somewhere else in the world, /spawn brings it back, /kit
 * hands the kit out again and /bot changes the bot; the practice panel does the same with buttons.</p>
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
    /** Save folder of the practice world running, for the game thread; empty when none is. */
    private static volatile String runningFolder = "";

    private final MinecraftServer server;
    private final ServerLevel level;
    private final String folder;
    private PracticeSettings settings;
    private KitLayout kit;
    private final PracticeArena arena;
    /** Where the round starts when nobody has moved it: the arena's home, or dry ground in a normal world. */
    private int homeX;
    private int homeZ;
    private PracticeBot bot;
    private int botRespawn = -1;
    private int kills;
    private int deaths;
    private int upkeepTimer;
    /** The round being played, and the last one, whose blasts are still marked. */
    private PracticeRound round;
    private PracticeRound lastRound;
    private int rounds;
    /** The drill under way: when it started, in ticks, and how far it got; -1 before it starts. */
    private PracticeDrill drill = PracticeDrill.NONE;
    private long drillStart = -1L;
    private int drillCount;
    private int drillPops;
    private int damageMapTimer;
    /** The health, absorption included, of the entity being hurt right now, before the hit. */
    private LivingEntity hurting;
    private float healthBefore;

    private PracticeSession(MinecraftServer server, String folder, PracticeSettings settings, KitLayout kit) {
        this.server = server;
        this.level = server.overworld();
        this.folder = folder;
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
                active = new PracticeSession(server, folder, groundOf(folder, chosen), CrystalVisualConfig.practiceKit(chosen.preset));
                active.prepare();
                runningFolder = folder;
                running = true;
            }
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            if (active != null && active.server == server) {
                running = false;
                runningFolder = "";
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
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (active != null && entity.level() == active.level) {
                active.beforeDamage(entity);
            }
            return true;
        });
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damage, blocked) -> {
            if (active != null && entity.level() == active.level) {
                active.damaged(entity, source);
            }
        });
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (active != null && level == active.level && entity instanceof EndCrystal crystal) {
                active.crystalPlaced(crystal);
            }
        });
        AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            if (active != null && level == active.level && entity instanceof EndCrystal crystal && player instanceof ServerPlayer) {
                active.crystalHit(crystal);
            }
            return InteractionResult.PASS;
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
        CommandRegistrationCallback.EVENT.register((dispatcher, registries, environment) -> PracticeCommands.register(dispatcher));
    }

    /** An entity event the practice world's server broadcast: a totem's, for the round's count. Server thread. */
    public static void entityEvent(ServerLevel level, Entity entity, byte event) {
        PracticeSession session = active;
        if (session != null && level == session.level && event == 35) {
            session.totemPopped(entity);
        }
    }

    /** The session of this server, or {@code null} when it is not a practice world's. Server thread. */
    static PracticeSession of(MinecraftServer server) {
        PracticeSession session = active;
        return session != null && session.server == server ? session : null;
    }

    /**
     * The practice settings changed in the panel: the bot comes back with them at once, and the kit
     * with the next round or /kit. Any thread.
     */
    public static void settingsChanged() {
        PracticeSession session = active;
        if (session != null) {
            session.server.execute(session::reload);
        }
    }

    private void reload() {
        if (active != this) {
            return;
        }
        PracticeSettings chosen = CrystalVisualConfig.practice();
        this.settings = groundOf(this.folder, chosen);
        this.kit = CrystalVisualConfig.practiceKit(chosen.preset);
        ServerPlayer target = target();
        if (this.bot != null && !this.bot.removed()) {
            this.bot.discard();
        }
        this.bot = null;
        this.botRespawn = -1;
        if (this.settings.bot && target != null) {
            spawnBot(target);
        }
    }

    /** True while a practice world's integrated server is up. Any thread. */
    public static boolean running() {
        return running;
    }

    /** Save folder of the practice world that is up, or empty. Any thread. */
    public static String runningFolder() {
        return runningFolder;
    }

    /** Save-folder name of the practice world for these settings. */
    public static String levelId(PracticeSettings.WorldType type, PracticeSettings.Biome biome) {
        return switch (type) {
            case NATURAL -> LEVEL_ID + "_" + biome.id;
            case SURVIVAL -> LEVEL_ID + NORMAL_WORLD_SUFFIX;
            default -> LEVEL_ID;
        };
    }

    /** Suffix of the normal world's save folder. */
    private static final String NORMAL_WORLD_SUFFIX = "_world";

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
        if (name.equals(LEVEL_ID) || name.equals(LEVEL_ID + NORMAL_WORLD_SUFFIX)) {
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
                    || chosen.worldType == PracticeSettings.WorldType.SURVIVAL
                    ? PracticeSettings.WorldType.FLAT
                    : chosen.worldType;
            return chosen.on(type, chosen.biome);
        }
        if (folder.equals(LEVEL_ID + NORMAL_WORLD_SUFFIX)) {
            return chosen.on(PracticeSettings.WorldType.SURVIVAL, chosen.biome);
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
        if (this.arena.normalWorld()) {
            // A normal world's centre may be sea: the round starts on the nearest dry ground.
            int[] dry = dryGround(0, 0, 96, 40);
            if (dry != null) {
                this.homeX = dry[0];
                this.homeZ = dry[1];
            }
        }
        this.arena.moveTo(this.homeX, this.homeZ);
        newRound(true);
    }

    // ------------------------------------------------------------------------------------------
    // Rounds, drills and the damage map
    // ------------------------------------------------------------------------------------------

    private long tick() {
        return this.server.getTickCount();
    }

    private void startRound() {
        this.round = new PracticeRound(++this.rounds, tick());
        PracticeDrill chosen = PracticeDrill.parse(CrystalVisualConfig.practiceDrill());
        if (chosen != this.drill) {
            this.drill = chosen;
            this.drillStart = -1L;
            this.drillCount = 0;
        }
        if (this.drill == PracticeDrill.TOTEMS || this.drill == PracticeDrill.SURVIVE) {
            this.drillStart = tick();
            this.drillCount = 0;
            this.drillPops = 0;
        }
    }

    /** The round is over: its report, its map, and the next one. */
    private void endRound(boolean won) {
        if (this.round != null) {
            this.round.finish(target(), tick(), won, CrystalVisualConfig.practiceRoundReport(), CrystalVisualConfig.practiceRoundMap(),
                    CrystalUi.spanish());
            this.lastRound = this.round;
        }
        startRound();
    }

    private boolean isBot(Entity entity) {
        return this.bot != null && this.bot.body() == entity;
    }

    private void crystalPlaced(EndCrystal crystal) {
        ServerPlayer player = target();
        if (this.round == null || player == null) {
            return;
        }
        // Whoever stands nearer placed it: the bot puts its crystals by itself, the player by the player.
        double toPlayer = player.distanceToSqr(crystal);
        double toBot = this.bot != null && this.bot.alive() ? this.bot.body().distanceToSqr(crystal) : Double.MAX_VALUE;
        if (toPlayer <= toBot) {
            this.round.crystalPlaced(crystal.getId(), tick());
        }
    }

    private void crystalHit(EndCrystal crystal) {
        if (this.round != null) {
            this.round.crystalBroken(crystal.getId(), tick());
        }
        if (this.drill == PracticeDrill.SPEED) {
            if (this.drillStart < 0L) {
                this.drillStart = tick();
                this.drillCount = 0;
            }
            if (++this.drillCount >= PracticeDrill.SPEED_GOAL) {
                double seconds = (tick() - this.drillStart) / 20.0D;
                boolean spanish = CrystalUi.spanish();
                drillDone(String.format(java.util.Locale.ROOT, spanish ? "%d cristales en %.1f s · %.2f por segundo" : "%d crystals in %.1f s · %.2f a second",
                        PracticeDrill.SPEED_GOAL, seconds, PracticeDrill.SPEED_GOAL / Math.max(0.05D, seconds)));
                this.drillStart = -1L;
                this.drillCount = 0;
            }
        }
    }

    private void beforeDamage(LivingEntity entity) {
        this.hurting = entity;
        this.healthBefore = entity.getHealth() + entity.getAbsorptionAmount();
    }

    /** A hit landed: the health it really took, after armour and protection; a totem's save took all there was. */
    private void damaged(LivingEntity entity, DamageSource source) {
        if (entity != this.hurting) {
            return;
        }
        this.hurting = null;
        float after = entity.getHealth() + entity.getAbsorptionAmount();
        float damage = after > this.healthBefore ? this.healthBefore : this.healthBefore - after;
        if (this.round == null || damage <= 0.0F || !source.is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION)) {
            return;
        }
        ServerPlayer player = target();
        if (entity == player) {
            this.round.blast(source.getSourcePosition(), true, damage);
        } else if (isBot(entity)) {
            this.round.blast(source.getSourcePosition(), false, damage);
        }
    }

    private void totemPopped(Entity entity) {
        if (this.round == null) {
            return;
        }
        if (entity == target()) {
            this.round.ownPops++;
            if (this.drill == PracticeDrill.SURVIVE) {
                this.drillPops++;
            }
        } else if (isBot(entity)) {
            this.round.botPops++;
            if (this.drill == PracticeDrill.TOTEMS && this.drillStart >= 0L && ++this.drillCount >= PracticeDrill.TOTEM_GOAL) {
                double seconds = (tick() - this.drillStart) / 20.0D;
                boolean spanish = CrystalUi.spanish();
                drillDone(String.format(java.util.Locale.ROOT, spanish ? "%d tótems en %.1f s" : "%d totems in %.1f s",
                        PracticeDrill.TOTEM_GOAL, seconds));
                this.drillStart = tick();
                this.drillCount = 0;
            }
        }
    }

    private void drillDone(String result) {
        ServerPlayer player = target();
        if (player == null) {
            return;
        }
        boolean spanish = CrystalUi.spanish();
        player.sendSystemMessage(Component.literal((spanish ? "Ejercicio · " : "Drill · ") + this.drill.label(spanish) + ": ")
                .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD)
                .append(Component.literal(result).withStyle(ChatFormatting.WHITE)));
        announce((spanish ? "¡Hecho! " : "Done! ") + result);
    }

    /** Each tick: the drill's clock on the action bar, the survival drill's end, the maps. */
    private void drillsAndMaps(ServerPlayer target) {
        PracticeDrill chosen = PracticeDrill.parse(CrystalVisualConfig.practiceDrill());
        if (chosen != this.drill) {
            this.drill = chosen;
            this.drillStart = chosen == PracticeDrill.TOTEMS || chosen == PracticeDrill.SURVIVE ? tick() : -1L;
            this.drillCount = 0;
            this.drillPops = 0;
        }
        long now = tick();
        if (this.drill != PracticeDrill.NONE && target != null && now % 10L == 0L) {
            boolean spanish = CrystalUi.spanish();
            double seconds = this.drillStart < 0L ? 0.0D : (now - this.drillStart) / 20.0D;
            String progress = switch (this.drill) {
                case SPEED -> this.drillCount + "/" + PracticeDrill.SPEED_GOAL;
                case TOTEMS -> this.drillCount + "/" + PracticeDrill.TOTEM_GOAL;
                default -> String.format(java.util.Locale.ROOT, "%.0f/%d s", seconds, PracticeDrill.SURVIVE_SECONDS);
            };
            if (this.drill == PracticeDrill.SURVIVE && seconds >= PracticeDrill.SURVIVE_SECONDS) {
                drillDone(spanish ? "aguantaste 60 s · gastaste " + this.drillPops + " tótems"
                        : "you held out 60 s · used " + this.drillPops + " totems");
                this.drillStart = now;
                this.drillPops = 0;
            } else if (!(this.drill == PracticeDrill.SPEED && this.drillStart < 0L)) {
                target.connection.send(new ClientboundSetActionBarTextPacket(Component.literal(
                        this.drill.label(spanish) + " · " + progress + String.format(java.util.Locale.ROOT, " · %.1f s", seconds))
                        .withStyle(ChatFormatting.LIGHT_PURPLE)));
            }
        }
        if (this.lastRound != null && !this.lastRound.showMap(this.level, target)) {
            this.lastRound = null;
        }
        if (CrystalVisualConfig.practiceDamageMap() && ++this.damageMapTimer >= DamageMap.PERIOD) {
            this.damageMapTimer = 0;
            if (this.bot != null && this.bot.alive()) {
                DamageMap.show(this.level, target, this.bot.body(), this.settings);
            }
        }
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
        if (this.round == null) {
            startRound();
        }
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
                        ? "Bot " + this.settings.difficulty.label(true) + " · " + this.settings.style.label(true) + " con tu mismo kit. Cristales, obsidiana, anclas y piedra luminosa se reponen solos; cada muerte empieza una ronda nueva."
                        : this.settings.difficulty.label(false) + " · " + this.settings.style.label(false) + " bot with your same kit. Crystals, obsidian, anchors and glowstone refill by themselves; every death starts a new round.")
                : (spanish
                        ? "Sin bot: practica colocando y rompiendo. Lo que se gasta se repone solo."
                        : "No bot: practise placing and breaking. What you use refills by itself."))
                .withStyle(ChatFormatting.GRAY));
        player.sendSystemMessage(Component.literal(spanish
                        ? "Esc abre el panel. Comandos: /rtp, /spawn, /kit y /bot."
                        : "Esc opens the panel. Commands: /rtp, /spawn, /kit and /bot.")
                .withStyle(ChatFormatting.DARK_PURPLE));
        if (this.settings.bot && this.bot == null) {
            spawnBot(player);
        }
    }

    private void respawn(ServerPlayer player) {
        newRound(true);
        startRound();
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
        double x = this.arena.centerX() + 0.5D;
        double z = this.arena.centerZ() + 8.5D;
        for (int attempt = 0; attempt < 12; attempt++) {
            double angle = this.level.getRandom().nextDouble() * Math.PI * 2.0D;
            double candidateX = near == null ? x : near.getX() + Math.cos(angle) * 9.0D;
            double candidateZ = near == null ? z : near.getZ() + Math.sin(angle) * 9.0D;
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
        this.bot = PracticeBot.spawn(this.level, this.settings, this.kit, this.arena, x, y, z, yaw);
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
                endRound(true);
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
        drillsAndMaps(target);
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
        if (this.drill == PracticeDrill.SURVIVE && this.drillStart >= 0L) {
            boolean spanish = CrystalUi.spanish();
            double seconds = (tick() - this.drillStart) / 20.0D;
            drillDone(String.format(java.util.Locale.ROOT, spanish ? "caíste a los %.1f s" : "you fell at %.1f s", seconds));
        }
        if (this.round != null) {
            this.round.finish(player, tick(), false, CrystalVisualConfig.practiceRoundReport(), CrystalVisualConfig.practiceRoundMap(),
                    CrystalUi.spanish());
            this.lastRound = this.round;
            this.round = null;
        }
    }

    private String score(boolean spanish) {
        return spanish ? "Bajas " + this.kills + " · Muertes " + this.deaths : "Kills " + this.kills + " · Deaths " + this.deaths;
    }

    private void announce(String text) {
        for (ServerPlayer player : this.server.getPlayerList().getPlayers()) {
            player.connection.send(new ClientboundSetActionBarTextPacket(Component.literal(text).withStyle(ChatFormatting.GOLD)));
        }
    }

    // ------------------------------------------------------------------------------------------
    // Commands, and the panel's buttons that run them
    // ------------------------------------------------------------------------------------------

    /**
     * Moves the round to a random spot of the world: a new arena there on the flat grounds, the
     * ground as generated in a normal world. Returns where, or {@code null} when nowhere fit.
     */
    int[] randomTeleport(ServerPlayer player) {
        int radius = this.arena.normalWorld() ? 2000 : 1500;
        int[] spot = null;
        for (int attempt = 0; attempt < 8 && spot == null; attempt++) {
            int x = this.level.getRandom().nextInt(radius * 2 + 1) - radius;
            int z = this.level.getRandom().nextInt(radius * 2 + 1) - radius;
            spot = this.arena.normalWorld() ? dryGround(x, z, 24, 8) : new int[] {x, z};
        }
        if (spot == null) {
            return null;
        }
        moveRound(player, spot[0], spot[1]);
        return spot;
    }

    /** The round back to where it started. */
    void backToSpawn(ServerPlayer player) {
        moveRound(player, this.homeX, this.homeZ);
    }

    private void moveRound(ServerPlayer player, int x, int z) {
        this.arena.moveTo(x, z);
        newRound(true);
        setUpPlayer(player);
        if (this.settings.bot) {
            spawnBot(player);
        }
    }

    /** The kit again, for the player and the bot, as at the start of a round. */
    void reequip(ServerPlayer player) {
        PracticeKit.equipPlayer(player, this.settings, this.kit);
        if (this.settings.bot) {
            if (this.bot != null && !this.bot.removed()) {
                this.bot.discard();
            }
            this.bot = null;
            this.botRespawn = -1;
            spawnBot(player);
        }
    }

    /** The bot comes back next to the player. */
    void botHere(ServerPlayer player) {
        if (this.bot != null && !this.bot.removed()) {
            this.bot.discard();
        }
        this.bot = null;
        this.botRespawn = -1;
        if (this.settings.bot) {
            spawnBot(player);
        }
    }

    /**
     * Dry, solid ground within {@code radius} of a column, searched in rings outward, a column every
     * {@code step} blocks. Loads the chunks it looks at.
     */
    private int[] dryGround(int x, int z, int radius, int step) {
        for (int ring = 0; ring <= radius; ring += step) {
            for (int dx = -ring; dx <= ring; dx += Math.max(1, step)) {
                for (int dz = -ring; dz <= ring; dz += Math.max(1, step)) {
                    if (ring > 0 && Math.abs(dx) != ring && Math.abs(dz) != ring) {
                        continue;
                    }
                    int columnX = x + dx;
                    int columnZ = z + dz;
                    int y = this.arena.groundY(columnX, columnZ);
                    if (y > this.level.getMinY() && PracticeArena.safeColumn(this.level, columnX, y, columnZ)) {
                        return new int[] {columnX, columnZ};
                    }
                }
            }
        }
        return null;
    }

    private void stop() {
        if (this.bot != null) {
            // Not saved with the world: next visit starts with a fresh bot on the current settings.
            this.bot.discard();
            this.bot = null;
        }
    }
}
