package com.zymekoh.crystaltweaks.client.practice;

import com.zymekoh.crystaltweaks.practice.PracticeSession;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.PacketListener;

/**
 * 1.21.11 has no packet queue to share between two worlds, so there the practice world still leaves
 * the server first, as before 2.7.0; only the practice panel on Escape comes along. See the 26.x
 * copy for practice over a server.
 */
public final class PracticeHost {
    public static final boolean OVER_SERVERS = false;

    private PracticeHost() {
    }

    public static void initialize() {
    }

    public static boolean parked() {
        return false;
    }

    public static boolean serverSide() {
        return false;
    }

    public static String serverName() {
        return "";
    }

    public static boolean serverLost() {
        return false;
    }

    /** No server is ever parked here, so there is no ping to practise at. */
    public static int parkedPing() {
        return 0;
    }

    static LocalPlayer serverPlayer() {
        return null;
    }

    /** With no parked server, every connection is the player's own. */
    public static boolean ownsListener(PacketListener listener) {
        return false;
    }

    public static boolean canPark(Minecraft minecraft) {
        return false;
    }

    public static void park(Minecraft minecraft) {
    }

    public static void abandon(Minecraft minecraft) {
    }

    public static void closePracticeWorld(Minecraft minecraft) {
    }

    public static void leave() {
    }

    public static void shutdown() {
    }

    /** Escape in a practice world opens the practice panel, which pauses it as the pause menu would. */
    public static boolean openPanel() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!PracticeSession.running() || minecraft.level == null || minecraft.screen != null) {
            return false;
        }
        minecraft.setScreen(new PracticeSetupScreen(null, true));
        return true;
    }
}
