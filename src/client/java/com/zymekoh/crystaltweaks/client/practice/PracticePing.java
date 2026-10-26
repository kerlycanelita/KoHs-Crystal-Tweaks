package com.zymekoh.crystaltweaks.client.practice;

import com.zymekoh.crystaltweaks.client.CrystalVisualConfig;
import com.zymekoh.crystaltweaks.practice.PracticeSession;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.PacketListener;

/**
 * Crystal Practice at a server's ping. A practice world is a server in this same game, answering at
 * once; crystals there feel faster than on any real server, and what is trained is the wrong timing.
 * With a ping chosen, every packet between the practice world and its server waits half of it each
 * way, so a crystal appears and breaks a round trip late, as it does online. "Server's" takes the
 * ping of the server parked behind the practice. Any thread.
 */
public final class PracticePing {
    private PracticePing() {
    }

    /** The round trip the practice world has now, in milliseconds. */
    public static int millis() {
        int chosen = CrystalVisualConfig.practicePing();
        return chosen < 0 ? PracticeHost.parkedPing() : chosen;
    }

    /**
     * How long a packet on this connection waits: half the round trip on the practice world's own, in
     * play only, none elsewhere. Joining changes protocol on the way, and a packet held across that
     * change would reach a pipeline that no longer speaks it.
     */
    public static long oneWayNanos(Connection connection) {
        if (!PracticeSession.running() || !connection.isMemoryConnection()) {
            return 0L;
        }
        PacketListener listener = connection.getPacketListener();
        if (listener == null || listener.protocol() != ConnectionProtocol.PLAY) {
            return 0L;
        }
        int ping = millis();
        return ping <= 0 ? 0L : ping * 500_000L;
    }
}
