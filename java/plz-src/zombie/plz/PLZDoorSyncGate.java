package zombie.plz;

import zombie.debug.DebugLog;

/** What the server does with a door sync a client sent: apply it, drop it, or hold the lock where it is. */
public final class PLZDoorSyncGate {
    // Clients re-send a garage door's lock for every panel they are told about, so two opposite
    // changes in flight keep flipping the door for as long as anyone else's echo is honoured.
    public static final long LOCK_FLIP_HOLD_MS = 2000L;
    public static final long REPORT_MS = 300000L;

    private static long dropped;
    private static long held;
    private static long windowStartMs = System.currentTimeMillis();

    private PLZDoorSyncGate() {
    }

    public static boolean changesNothing(
        boolean open, boolean locked, boolean lockedByKey, int health,
        boolean wantOpen, boolean wantLocked, boolean wantLockedByKey, int wantHealth
    ) {
        return open == wantOpen && locked == wantLocked && lockedByKey == wantLockedByKey && health == wantHealth;
    }

    // A panel named in someone else's packet takes its lock from the key flag alone.
    public static boolean partChangesNothing(
        boolean partOpen, boolean partLocked, boolean partLockedByKey, boolean wantOpen, boolean wantLockedByKey
    ) {
        return partOpen == wantOpen && partLocked == wantLockedByKey && partLockedByKey == wantLockedByKey;
    }

    public static boolean holdsLockFlip(long lastFlipMs, long lastFlipBy, long nowMs, long sender) {
        if (lastFlipMs == 0L) {
            return false;
        }

        if (sender != -1L && sender == lastFlipBy) {
            return false;
        }

        return nowMs - lastFlipMs < LOCK_FLIP_HOLD_MS;
    }

    public static void countDropped() {
        dropped++;
        maybeReport();
    }

    public static void countHeld() {
        held++;
        maybeReport();
    }

    private static void maybeReport() {
        long now = System.currentTimeMillis();
        if (now - windowStartMs < REPORT_MS) {
            return;
        }

        DebugLog.log("PLZDoorSync: " + (now - windowStartMs) / 1000L + "s dropped " + dropped + " unchanged, held " + held + " lock flips");
        windowStartMs = now;
        dropped = 0L;
        held = 0L;
    }
}
