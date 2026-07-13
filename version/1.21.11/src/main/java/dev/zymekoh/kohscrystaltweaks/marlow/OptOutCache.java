package dev.zymekoh.kohscrystaltweaks.marlow;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

public final class OptOutCache {
    private static final int MAX_SERVERS = 10;

    private final Deque<String> optedOutServers = new ArrayDeque<>();
    private final Set<String> optedOutIndex = new HashSet<>();
    private final Deque<String> notifiedServers = new ArrayDeque<>();
    private final Set<String> notifiedIndex = new HashSet<>();
    private volatile boolean optedOut;

    public boolean isOptedOut() {
        return this.optedOut;
    }

    public void setOptedOut(boolean optedOut) {
        this.optedOut = optedOut;
    }

    public void markOptedOut(String serverKey) {
        remember(serverKey, this.optedOutServers, this.optedOutIndex);
        this.optedOut = true;
    }

    public boolean isServerOptedOut(String serverKey) {
        if (serverKey == null) {
            return false;
        }
        synchronized (this.optedOutServers) {
            return this.optedOutIndex.contains(serverKey);
        }
    }

    public boolean hasNotified(String serverKey) {
        if (serverKey == null) {
            return false;
        }
        synchronized (this.notifiedServers) {
            return this.notifiedIndex.contains(serverKey);
        }
    }

    public void markNotified(String serverKey) {
        remember(serverKey, this.notifiedServers, this.notifiedIndex);
    }

    public void clearCurrentSession() {
        this.optedOut = false;
    }

    private static void remember(String serverKey, Deque<String> order, Set<String> index) {
        if (serverKey == null) {
            return;
        }

        synchronized (order) {
            if (!index.add(serverKey)) {
                return;
            }

            order.addLast(serverKey);
            while (order.size() > MAX_SERVERS) {
                String removed = order.removeFirst();
                index.remove(removed);
            }
        }
    }
}
