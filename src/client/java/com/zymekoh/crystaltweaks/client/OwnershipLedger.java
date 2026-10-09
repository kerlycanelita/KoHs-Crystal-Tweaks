package com.zymekoh.crystaltweaks.client;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Whose crystal is whose, from what this client sent and what the server answered. No game classes:
 * bases and crystals are whatever the caller hands in, so the rules can be tested on a timeline.
 *
 * <p>The server never says who placed a crystal. It does acknowledge every placement the client
 * sends, by its sequence number, at the end of the tick that handled it, and a crystal that
 * placement made reaches the client just before that acknowledgement. So a crystal is the player's
 * when it appears on a base they clicked and it is the last one to have appeared there when the
 * server acknowledges the click. Until then every crystal on that base is taken to be theirs: the
 * player's own crystal is never drawn as someone else's, and a crystal the acknowledgement passes
 * over turns out to have been someone else's on the same base.</p>
 *
 * <p>Not every server answers in that order. Some, and proxies in front of them, acknowledge a
 * click well before the crystal it made arrives, by more than a tick. There the click is already
 * answered when its crystal shows, so an answered click still claims the first crystal to appear
 * on its base since it was sent.</p>
 *
 * <p>Before this, the oldest click on a base simply claimed the next crystal to appear there. An
 * enemy crystal that landed on the base while the click was still travelling took the claim, and
 * the player's own crystal, arriving after it, was drawn as the enemy's. A click the server refused
 * stayed claimable for three seconds, by anyone's crystal.</p>
 *
 * @param <T> the caller's handle for a crystal
 */
public final class OwnershipLedger<T> {
    /** How long a click stays claimable while the server has not answered it. */
    static final long WINDOW_NANOS = 3_000_000_000L;
    /**
     * How long after an acknowledgement its crystal may still show. Vanilla sends the crystal first;
     * this is for a server, or a proxy, that acknowledges the click before the crystal is sent.
     */
    static final long LATE_NANOS = 1_500_000_000L;
    private static final int MAX_CLICKS = 48;

    private static final class Click {
        final long base;
        final int sequence;
        final long sentAt;
        long acknowledgedAt;

        Click(long base, int sequence, long sentAt) {
            this.base = base;
            this.sequence = sequence;
            this.sentAt = sentAt;
        }
    }

    private static final class Arrival<T> {
        final T crystal;
        final long base;
        final long at;

        Arrival(T crystal, long base, long at) {
            this.crystal = crystal;
            this.base = base;
            this.at = at;
        }
    }

    private final Map<T, Boolean> own;
    /** Clicks the server has not answered yet, oldest first. */
    private final ArrayDeque<Click> clicks = new ArrayDeque<>();
    /** Clicks the server answered before any crystal showed. */
    private final ArrayDeque<Click> answered = new ArrayDeque<>();
    /** When a crystal last appeared on each base, anyone's. */
    private final Map<Long, Long> lastSeen = new HashMap<>();
    /** Crystals taken to be the player's, waiting for the acknowledgement that says so. */
    private final ArrayDeque<Arrival<T>> unconfirmed = new ArrayDeque<>();

    /** @param own where the verdicts go; the caller chooses how crystals are held and forgotten */
    public OwnershipLedger(Map<T, Boolean> own) {
        this.own = own;
    }

    /** The player clicked a base with a crystal in hand, and the placement left with this sequence number. */
    public void clicked(long base, int sequence, long now) {
        settle(now);
        while (this.clicks.size() >= MAX_CLICKS) {
            this.clicks.pollFirst();
        }
        this.clicks.addLast(new Click(base, sequence, now));
    }

    /** A crystal appeared on a base. Returns whether it is taken to be the player's. */
    public boolean appeared(T crystal, long base, long now) {
        settle(now);
        Long seen = this.lastSeen.put(base, now);
        // A click on this base still waits for its answer: the crystal is taken to be that answer.
        for (Click click : this.clicks) {
            if (click.base == base) {
                this.unconfirmed.addLast(new Arrival<>(crystal, base, now));
                this.own.put(crystal, true);
                return true;
            }
        }
        // A click the server answered before any crystal showed: the first crystal on its base
        // since it was sent is its crystal. One that follows another crystal there is not.
        for (Iterator<Click> iterator = this.answered.iterator(); iterator.hasNext();) {
            Click click = iterator.next();
            if (click.base == base && (seen == null || seen < click.sentAt)) {
                iterator.remove();
                this.own.put(crystal, true);
                return true;
            }
        }
        this.own.put(crystal, false);
        return false;
    }

    /** The server acknowledged every placement up to this sequence number. */
    public void acknowledged(int sequence, long now) {
        for (Iterator<Click> iterator = this.clicks.iterator(); iterator.hasNext();) {
            Click click = iterator.next();
            if (click.sequence > sequence) {
                continue;
            }
            iterator.remove();
            // Its crystal, if the placement went through, is the last to have shown on its base since
            // the click: the server sends it as it handles the click, and this at the end of that tick.
            Arrival<T> made = null;
            for (Arrival<T> arrival : this.unconfirmed) {
                if (arrival.base == click.base && arrival.at >= click.sentAt) {
                    made = arrival;
                }
            }
            if (made != null) {
                this.unconfirmed.remove(made);
            } else {
                click.acknowledgedAt = now;
                this.answered.addLast(click);
            }
            // What this acknowledgement passed over on its base, with no other click of the player's
            // left to answer it, was someone else's crystal there.
            for (Iterator<Arrival<T>> passed = this.unconfirmed.iterator(); passed.hasNext();) {
                Arrival<T> arrival = passed.next();
                if (arrival.base == click.base && !awaited(arrival)) {
                    passed.remove();
                    this.own.put(arrival.crystal, false);
                }
            }
        }
        settle(now);
    }

    private boolean awaited(Arrival<T> arrival) {
        for (Click click : this.clicks) {
            if (click.base == arrival.base && click.sentAt <= arrival.at) {
                return true;
            }
        }
        return false;
    }

    /** Forgets what can no longer be matched. Cheap; call it as time passes. */
    public void settle(long now) {
        while (!this.answered.isEmpty() && now - this.answered.peekFirst().acknowledgedAt > LATE_NANOS) {
            this.answered.pollFirst();
        }
        // A click the server never answered: its crystal keeps the benefit of the doubt.
        while (!this.clicks.isEmpty() && now - this.clicks.peekFirst().sentAt > WINDOW_NANOS) {
            this.clicks.pollFirst();
        }
        while (!this.unconfirmed.isEmpty() && now - this.unconfirmed.peekFirst().at > WINDOW_NANOS) {
            this.unconfirmed.pollFirst();
        }
        if (this.lastSeen.size() > MAX_CLICKS) {
            this.lastSeen.values().removeIf(at -> now - at > WINDOW_NANOS);
        }
    }

    public void clear() {
        this.clicks.clear();
        this.answered.clear();
        this.unconfirmed.clear();
        this.lastSeen.clear();
    }
}
