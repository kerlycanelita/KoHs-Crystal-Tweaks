package com.zymekoh.crystaltweaks.client.practice;

import com.zymekoh.crystaltweaks.CrystalTweaksClient;
import com.zymekoh.crystaltweaks.client.CrystalUi;
import com.zymekoh.crystaltweaks.mixin.client.BossHealthOverlayAccessor;
import com.zymekoh.crystaltweaks.mixin.client.ClientCommonPacketListenerAccessor;
import com.zymekoh.crystaltweaks.mixin.client.MinecraftPracticeAccessor;
import com.zymekoh.crystaltweaks.practice.PracticeSession;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.util.Util;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.chat.report.ReportingContext;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundClientTickEndPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.HitResult;

/**
 * Crystal Practice over a server, without leaving it.
 *
 * <p>The practice world opens as an ordinary singleplayer world, so everything in it, the fight,
 * the inventory, the chat and its commands, the pause, is Minecraft's own. The server the player
 * was on is not closed: it is parked behind the practice world with its world, its player and its
 * connection, and keeps running exactly as it would with a menu open and the player standing still.
 * Its packets are handled as they arrive and its world ticks at the server's own rate, so it keeps
 * answering, moving nothing, and sends nothing a Vanilla client in a menu would not.</p>
 *
 * <p>Minecraft holds one world at a time in its fields. While a packet of the parked server is
 * handled, or its world ticks, those fields are switched to the parked server's and switched back
 * after, so Minecraft's own code runs on the right world. What that code would do to the screen,
 * the sounds, the particles or the renderer, all of which belong to the practice world while it is
 * on show, is kept for the parked server instead: a screen it opens waits for the player's return,
 * its sounds and particles are not played over the practice world.</p>
 *
 * <p>Game thread only.</p>
 */
public final class PracticeHost {
    /** Minecraft's fields that belong to one world at a time. */
    private static class Slot {
        ClientLevel level;
        LocalPlayer player;
        MultiPlayerGameMode gameMode;
        Entity camera;
        HitResult hitResult;
        Entity crosshairPickEntity;
        int rightClickDelay;
        int missTime;
        boolean localServer;
        IntegratedServer singleplayerServer;
        ReportingContext reporting;
        Screen screen;
        Map<UUID, LerpingBossEvent> bossEvents;

        void takeFrom(Minecraft minecraft) {
            MinecraftPracticeAccessor fields = (MinecraftPracticeAccessor) minecraft;
            this.level = minecraft.level;
            this.player = minecraft.player;
            this.gameMode = minecraft.gameMode;
            this.camera = PracticeGlue.camera(minecraft).entity();
            this.hitResult = minecraft.hitResult;
            this.crosshairPickEntity = minecraft.crosshairPickEntity;
            this.rightClickDelay = fields.crystalTweaks$rightClickDelay();
            this.missTime = fields.crystalTweaks$missTime();
            this.localServer = fields.crystalTweaks$localServer();
            this.singleplayerServer = fields.crystalTweaks$singleplayerServer();
            this.reporting = fields.crystalTweaks$reportingContext();
            this.screen = PracticeGlue.rawScreen(minecraft);
            this.bossEvents = ((BossHealthOverlayAccessor) PracticeGlue.bossOverlay(minecraft)).crystalTweaks$events();
        }

        void putInto(Minecraft minecraft) {
            MinecraftPracticeAccessor fields = (MinecraftPracticeAccessor) minecraft;
            minecraft.level = this.level;
            minecraft.player = this.player;
            minecraft.gameMode = this.gameMode;
            PracticeGlue.camera(minecraft).setEntity(this.camera);
            minecraft.hitResult = this.hitResult;
            minecraft.crosshairPickEntity = this.crosshairPickEntity;
            fields.crystalTweaks$setRightClickDelay(this.rightClickDelay);
            fields.crystalTweaks$setMissTime(this.missTime);
            fields.crystalTweaks$setLocalServer(this.localServer);
            fields.crystalTweaks$setSingleplayerServer(this.singleplayerServer);
            fields.crystalTweaks$setReportingContext(this.reporting);
            PracticeGlue.setRawScreen(minecraft, this.screen);
            ((BossHealthOverlayAccessor) PracticeGlue.bossOverlay(minecraft)).crystalTweaks$setEvents(this.bossEvents);
        }
    }

    /** The server behind the practice world. */
    private static final class Parked extends Slot {
        Connection connection;
        String name = "";
        /** Real time, in milliseconds, its ticks have been run up to. */
        double tickedUpTo;
        boolean ended;
        Screen endScreen;
        boolean keepResourcePacks = true;
        final List<Component> notices = new ArrayList<>();
    }

    /**
     * What the parked server's screen is while the player is away: a menu that is not there, as a
     * player standing in a menu would have. Never shown, never ticked.
     */
    static final class AwayScreen extends Screen {
        AwayScreen() {
            super(Component.empty());
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }
    }

    private static final Slot LIVE = new Slot();
    private static Parked parked;
    /** True while the parked server's fields are in Minecraft's. */
    private static boolean serverSide;
    /** True when the next frame is runTick's own, which has already handled the packets. */
    private static boolean normalFrame;
    /** The parked server's ping, as its player list last said, in milliseconds; 0 with none parked. Any thread. */
    private static volatile int serverPing;

    /** Practice opens over a server without leaving it here; not on 1.21.11. */
    public static final boolean OVER_SERVERS = true;

    private PracticeHost() {
    }

    /** The window in the top-left corner that shows the parked server, drawn with the HUD. */
    public static void initialize() {
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("crystal_tweaks", "practice_server"),
                (graphics, deltaTracker) -> ServerWindow.draw(graphics));
    }

    // ------------------------------------------------------------------------------------------
    // State
    // ------------------------------------------------------------------------------------------

    /** A server is parked behind the practice world. */
    public static boolean parked() {
        return parked != null;
    }

    /** The parked server's own code is running right now. */
    public static boolean serverSide() {
        return serverSide;
    }

    /** The parked server's ping in milliseconds, for a practice played at it; 0 with none parked. Any thread. */
    public static int parkedPing() {
        return serverPing;
    }

    /** The parked server's name, or empty when none is parked. */
    public static String serverName() {
        return parked == null ? "" : parked.name;
    }

    /** The parked server closed the connection while the player practised. */
    public static boolean serverLost() {
        return parked != null && parked.ended;
    }

    /** The parked server's world, or null between two of a network's servers or with none parked. */
    static ClientLevel serverLevel() {
        return parked == null ? null : parked.level;
    }

    static LocalPlayer serverPlayer() {
        return parked == null ? null : parked.player;
    }

    /** The entity the parked server's view follows: its player, or what it spectates. */
    static Entity serverCamera() {
        return parked == null ? null : parked.camera != null ? parked.camera : parked.player;
    }

    /** The parked server's boss bars, which never show over the practice world. */
    static Collection<LerpingBossEvent> serverBossBars() {
        return parked == null || parked.bossEvents == null ? List.of() : parked.bossEvents.values();
    }

    /** The menu the parked server has open on the player, or null. */
    static Screen serverScreen() {
        return parked == null || parked.screen instanceof AwayScreen ? null : parked.screen;
    }

    /** The parked player's latency as the server reports it in the player list, or -1. */
    static int serverPing() {
        if (parked == null || parked.player == null) {
            return -1;
        }
        PlayerInfo info = parked.player.connection.getPlayerInfo(parked.player.getUUID());
        return info == null ? -1 : info.getLatency();
    }

    /** Whether the practice world can open over the world on screen without leaving it: a real server. */
    public static boolean canPark(Minecraft minecraft) {
        return parked == null && minecraft.level != null && minecraft.player != null && minecraft.getConnection() != null
                && !minecraft.isLocalServer();
    }

    // ------------------------------------------------------------------------------------------
    // Going in and out
    // ------------------------------------------------------------------------------------------

    /**
     * Takes the server off the screen without closing anything, before the practice world opens. The
     * connection stays exactly as it is; only Minecraft's fields stop pointing at its world.
     */
    public static void park(Minecraft minecraft) {
        Parked server = new Parked();
        server.takeFrom(minecraft);
        ClientPacketListener connection = minecraft.getConnection();
        server.connection = connection.getConnection();
        ServerData data = minecraft.getCurrentServer();
        server.name = data == null ? "" : data.name;
        // A player in a menu, standing still: no screen of its own yet, no keys held.
        server.screen = new AwayScreen();
        neutral(server.player);
        server.tickedUpTo = Util.getMillis();
        // The practice world starts with its own, empty boss bars.
        ((BossHealthOverlayAccessor) PracticeGlue.bossOverlay(minecraft)).crystalTweaks$setEvents(new LinkedHashMap<>());
        minecraft.level = null;
        minecraft.player = null;
        minecraft.gameMode = null;
        minecraft.hitResult = null;
        minecraft.crosshairPickEntity = null;
        // As between two worlds: sounds stopped, renderer and particles let go of the server's world.
        ((MinecraftPracticeAccessor) minecraft).crystalTweaks$updateLevelInEngines(null);
        parked = server;
        ServerMonitor.reset();
        CrystalTweaksClient.LOGGER.info("Crystal Practice opened over {}; the connection stays open", server.name);
    }

    /** The practice world never opened: the server comes back as it was. */
    public static void abandon(Minecraft minecraft) {
        if (parked != null && minecraft.level == null && minecraft.getSingleplayerServer() == null) {
            restore(minecraft);
        }
    }

    /** Closes the practice world, as Save and Quit does, keeping the parked server, its pack and the chat. */
    public static void closePracticeWorld(Minecraft minecraft) {
        if (minecraft.level != null) {
            minecraft.level.disconnect(ClientLevel.DEFAULT_QUIT_MESSAGE);
        }
        // Without a level, disconnect leaves the HUD alone: the chat, the tab list and the parked
        // server's boss bars stay.
        minecraft.level = null;
        minecraft.disconnect(new GenericMessageScreen(Component.translatable("menu.savingLevel")), true);
    }

    /** Leave practice: back to the server, where the player was, or to the screen it left with. */
    public static void leave() {
        Minecraft minecraft = Minecraft.getInstance();
        if (parked == null) {
            return;
        }
        closePracticeWorld(minecraft);
        restore(minecraft);
    }

    private static void restore(Minecraft minecraft) {
        Parked server = parked;
        parked = null;
        serverPing = 0;
        ServerView.close();
        ServerMonitor.reset();
        if (server.ended) {
            if (!server.keepResourcePacks) {
                minecraft.clearDownloadedResourcePacks();
            }
            PracticeGlue.setScreen(minecraft, server.endScreen != null ? server.endScreen : new JoinMultiplayerScreen(new TitleScreen()));
            return;
        }
        MinecraftPracticeAccessor fields = (MinecraftPracticeAccessor) minecraft;
        // The renderer, the particles and the camera take the server's world back, as on a respawn.
        minecraft.setLevel(server.level);
        if (server.player != null) {
            PracticeGlue.announceChunks(server.level, server.player.getBlockX() >> 4, server.player.getBlockZ() >> 4);
        }
        minecraft.player = server.player;
        minecraft.gameMode = server.gameMode;
        minecraft.hitResult = server.hitResult;
        minecraft.crosshairPickEntity = server.crosshairPickEntity;
        fields.crystalTweaks$setRightClickDelay(server.rightClickDelay);
        fields.crystalTweaks$setMissTime(server.missTime);
        fields.crystalTweaks$setLocalServer(false);
        fields.crystalTweaks$setSingleplayerServer(null);
        fields.crystalTweaks$setReportingContext(server.reporting);
        ((BossHealthOverlayAccessor) PracticeGlue.bossOverlay(minecraft)).crystalTweaks$setEvents(server.bossEvents);
        minecraft.setCameraEntity(server.camera != null ? server.camera : server.player);
        if (server.player != null) {
            server.player.input = new KeyboardInput(minecraft.options);
        }
        // Closing the practice world stopped the social online mode; the key pair is still the one the server knows.
        minecraft.getPlayerSocialManager().startOnlineMode();
        PracticeGlue.setScreen(minecraft, server.screen instanceof AwayScreen ? null : server.screen);
        // The server's ticks up to now were sent by the practice's own clock, also while the practice
        // world saved and closed. Minecraft's clock stood still meanwhile and would owe them again, all
        // in one frame: a burst an anticheat reads as a sped-up game. It counts from now instead.
        if (minecraft.getDeltaTracker() instanceof DeltaTracker.Timer timer) {
            timer.advanceGameTime(Util.getMillis());
        }
        CrystalTweaksClient.LOGGER.info("Crystal Practice closed; back on {}", server.name);
    }

    /** The game is closing: the parked server is told goodbye like any other. */
    public static void shutdown() {
        ServerView.close();
        Parked server = parked;
        if (server != null && !server.ended && server.connection != null) {
            server.connection.disconnect(Component.translatable("disconnect.quitting"));
        }
    }

    // ------------------------------------------------------------------------------------------
    // Keeping the parked server alive
    // ------------------------------------------------------------------------------------------

    /** The next frame is runTick's own: its packets have already been handled. */
    public static void normalFrame() {
        normalFrame = true;
    }

    /**
     * Every frame, including the ones loading and saving screens draw on their own: the parked
     * server's packets are handled if nothing else has, and its world ticks as many times as the
     * time that passed asks, ten at most, as Minecraft itself catches up.
     */
    public static void onFrame() {
        boolean own = normalFrame;
        normalFrame = false;
        Parked server = parked;
        if (server == null || server.ended || serverSide) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (!own) {
            minecraft.packetProcessor().processQueuedPackets();
            if (parked != server || server.ended) {
                return;
            }
        }
        tickDue(minecraft, server);
        LocalPlayer player = server.player;
        if (player != null) {
            PlayerInfo info = player.connection.getPlayerInfo(player.getUUID());
            serverPing = info == null ? 0 : Math.max(0, info.getLatency());
        }
        ServerMonitor.chime(minecraft);
        ServerMonitor.returnIfDue(minecraft);
        if (parked == server && !server.ended && minecraft.level != null) {
            ServerView.render(minecraft);
        }
    }

    /** The parked server's ticks that the time passed asks for. */
    private static void tickDue(Minecraft minecraft, Parked server) {
        long now = Util.getMillis();
        double perTick = server.level != null
                ? Math.max(50.0F, server.level.tickRateManager().millisecondsPerTick())
                : 50.0D;
        int due = (int) ((now - server.tickedUpTo) / perTick);
        if (due <= 0) {
            return;
        }
        // Like the game's own clock: what does not fit in ten ticks is dropped, not owed.
        server.tickedUpTo += due * perTick;
        try {
            for (int tick = 0; tick < Math.min(10, due) && parked == server && !server.ended; tick++) {
                asServer(PracticeHost::serverTick);
            }
        } catch (RuntimeException failure) {
            // The parked world broke: the practice world on show must not go down with it.
            CrystalTweaksClient.LOGGER.error("The server parked behind Crystal Practice failed to tick", failure);
            server.ended = true;
            if (server.connection != null) {
                server.connection.disconnect(Component.translatable("disconnect.lost"));
            }
            PracticeGlue.chat(minecraft, Component.literal("[Crystal Practice] ").withStyle(ChatFormatting.LIGHT_PURPLE)
                    .append(Component.literal(CrystalUi.spanish()
                            ? "El servidor de detrás dejó de responder y se desconectó. Sigues en la práctica."
                            : "The server behind stopped working and was disconnected. You are still practising.")
                            .withStyle(ChatFormatting.RED)));
        }
    }

    /**
     * One tick of the parked server's world, in the order Minecraft ticks a world while a menu is
     * open: the connection, the screen, the entities (the player sends its position and its input),
     * the world, and the end of the tick.
     */
    private static void serverTick() {
        Minecraft minecraft = Minecraft.getInstance();
        Screen screen = PracticeGlue.rawScreen(minecraft);
        if (minecraft.level == null || minecraft.gameMode == null) {
            // Between two of a network's servers the screen holds the connection and ticks it.
            if (screen != null && !(screen instanceof AwayScreen)) {
                screen.tick();
            }
            return;
        }
        ClientLevel level = minecraft.level;
        level.tickRateManager().tick();
        MinecraftPracticeAccessor fields = (MinecraftPracticeAccessor) minecraft;
        if (fields.crystalTweaks$rightClickDelay() > 0) {
            fields.crystalTweaks$setRightClickDelay(fields.crystalTweaks$rightClickDelay() - 1);
        }
        minecraft.gameMode.tick();
        if (parked.ended || minecraft.level != level) {
            return;
        }
        screen = PracticeGlue.rawScreen(minecraft);
        if (screen != null && !(screen instanceof AwayScreen)) {
            screen.tick();
            if (parked.ended || minecraft.level != level) {
                return;
            }
        }
        level.tickEntities();
        level.tickBlockEntities();
        level.tick(() -> true);
        ClientPacketListener connection = minecraft.getConnection();
        if (connection != null) {
            connection.send(ServerboundClientTickEndPacket.INSTANCE);
        }
        // The light the world's packets brought, as each frame does for the world on screen.
        level.update();
        ServerView.tick();
    }

    /** Whether a packet listener is the parked server's: its play, or its configuration between servers. */
    public static boolean ownsListener(PacketListener listener) {
        return parked != null && listener instanceof ClientCommonPacketListenerImpl common
                && ((ClientCommonPacketListenerAccessor) common).crystalTweaks$connection() == parked.connection;
    }

    /**
     * One packet of the parked server, handled by Minecraft on the server's world. The window in the
     * corner reads it first and keeps the titles and the action bar, which only draw on the HUD.
     */
    public static void serverPacket(Packet<?> packet, Runnable handle) {
        Minecraft minecraft = Minecraft.getInstance();
        if (ServerMonitor.before(packet, minecraft.player)) {
            return;
        }
        handle.run();
        ServerMonitor.after(packet, minecraft.player);
    }

    /** Runs the parked server's code with Minecraft's fields switched to its world, and back after. */
    public static void asServer(Runnable action) {
        Parked server = parked;
        if (serverSide || server == null) {
            action.run();
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        LIVE.takeFrom(minecraft);
        server.putInto(minecraft);
        serverSide = true;
        try {
            action.run();
        } finally {
            serverSide = false;
            server.takeFrom(minecraft);
            LIVE.putInto(minecraft);
            if (server.player != null) {
                // A respawn or a change of dimension brings a new player with the keyboard wired in.
                neutral(server.player);
            }
            if (!server.notices.isEmpty()) {
                for (Component notice : server.notices) {
                    PracticeGlue.chat(minecraft, notice);
                }
                server.notices.clear();
            }
        }
    }

    /** No keys reach the parked player: a player in a menu holds none. */
    private static void neutral(LocalPlayer player) {
        if (player != null && player.input.getClass() != ClientInput.class) {
            player.input = new ClientInput();
        }
    }

    // ------------------------------------------------------------------------------------------
    // What the parked server's code would do to the practice world, kept for the server instead.
    // Each returns true when it took the call, which Minecraft then skips.
    // ------------------------------------------------------------------------------------------

    /** A screen the server opens waits for the player's return; closing one leaves the player away. */
    public static boolean serverSetsScreen(Screen next) {
        if (!serverSide) {
            return false;
        }
        Minecraft minecraft = Minecraft.getInstance();
        Screen current = PracticeGlue.rawScreen(minecraft);
        if (current != null && !(current instanceof AwayScreen)) {
            current.removed();
        }
        if (next == null && minecraft.level != null && minecraft.player != null && minecraft.player.isDeadOrDying()) {
            // As Minecraft does with no screen to go back to and a dead player.
            if (minecraft.player.shouldShowDeathScreen()) {
                next = new DeathScreen(null, minecraft.level.getLevelData().isHardcore(), minecraft.player);
            } else {
                minecraft.player.respawn();
            }
        }
        if (next instanceof DeathScreen) {
            notice(CrystalUi.spanish()
                    ? "Moriste en " + parked.name + ". Al salir de la práctica verás la pantalla de muerte."
                    : "You died on " + parked.name + ". You will see the death screen when you leave practice.", ChatFormatting.RED);
        }
        if (next == null) {
            next = new AwayScreen();
        }
        PracticeGlue.setRawScreen(minecraft, next);
        if (!(next instanceof AwayScreen)) {
            next.added();
            next.init(minecraft.getWindow().getGuiScaledWidth(), minecraft.getWindow().getGuiScaledHeight());
        }
        return true;
    }

    /** The server's new world goes in its own slot: the renderer keeps the practice world. */
    public static boolean serverSetsLevel(ClientLevel level) {
        if (!serverSide) {
            return false;
        }
        Minecraft.getInstance().level = level;
        return true;
    }

    /** The server's camera, without the practice world's post effects. */
    public static boolean serverSetsCamera(Entity entity) {
        if (!serverSide) {
            return false;
        }
        PracticeGlue.camera(Minecraft.getInstance()).setEntity(entity);
        return true;
    }

    /** The server closed the connection, or it was lost: the practice goes on, the player is told. */
    public static boolean serverDisconnects(Screen screen, boolean keepResourcePacks) {
        if (!serverSide) {
            return false;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ClientPacketListener connection = minecraft.getConnection();
        if (connection != null) {
            connection.close();
        }
        parked.ended = true;
        parked.endScreen = screen;
        parked.keepResourcePacks = keepResourcePacks;
        minecraft.level = null;
        minecraft.player = null;
        minecraft.gameMode = null;
        ServerMonitor.alert(CrystalUi.spanish() ? "Se cerró la conexión" : "The connection closed", null);
        notice(CrystalUi.spanish()
                ? "Se cerró la conexión con " + parked.name + ". Sigues en la práctica; al salir verás el motivo."
                : "The connection to " + parked.name + " closed. You are still practising; you will see why when you leave.",
                ChatFormatting.RED);
        return true;
    }

    /** A network moving the player between its servers: the server's world goes, its connection stays. */
    public static boolean serverClearsLevel(Screen screen) {
        if (!serverSide) {
            return false;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ClientPacketListener connection = minecraft.getConnection();
        if (connection != null) {
            connection.clearLevel();
        }
        minecraft.gameMode = null;
        serverSetsScreen(screen);
        minecraft.level = null;
        minecraft.player = null;
        return true;
    }

    /** A transfer to another server while away: the player reconnects by hand afterwards. */
    public static boolean serverTransfers() {
        if (!serverSide) {
            return false;
        }
        if (parked != null) {
            parked.ended = true;
            notice(CrystalUi.spanish()
                    ? parked.name + " te mandó a otro servidor. Al salir de la práctica, vuelve a entrar desde la lista."
                    : parked.name + " sent you to another server. When you leave practice, join again from the list.",
                    ChatFormatting.GOLD);
        }
        return true;
    }

    /** Save and Quit, or any other way out of the practice world, is Leave practice while a server is parked. */
    public static boolean practiceQuits() {
        if (parked == null || serverSide) {
            return false;
        }
        leave();
        return true;
    }

    /** Escape in a practice world opens the practice panel, which pauses it as the pause menu would. */
    public static boolean openPanel() {
        Minecraft minecraft = Minecraft.getInstance();
        if (serverSide || !PracticeSession.running() || minecraft.level == null || PracticeGlue.rawScreen(minecraft) != null) {
            return false;
        }
        PracticeGlue.setScreen(minecraft, new PracticeSetupScreen(null, true));
        return true;
    }

    private static void notice(String text, ChatFormatting color) {
        if (parked != null) {
            parked.notices.add(Component.literal("[Crystal Practice] ").withStyle(ChatFormatting.LIGHT_PURPLE)
                    .append(Component.literal(text).withStyle(color)));
        }
    }
}
