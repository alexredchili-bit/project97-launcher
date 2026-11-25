package zombie.plz;

import java.util.HashMap;
import zombie.iso.IsoWorld;
import zombie.iso.LosUtil;

public final class PLZArenaHistory {
    public static final int OK = 0;
    public static final int NO_TARGET = 1;
    public static final int STALE = 2;
    public static final int FUTURE = 3;
    public static final int MISSED = 4;
    public static final int BLOCKED = 5;

    public static final long FUTURE_SLACK_MS = 150L;
    public static final long STALE_SLACK_MS = 250L;
    public static final float HIT_RADIUS = 0.6F;
    public static final float HIT_BELOW = 0.1F;
    public static final float HIT_ABOVE = 1.0F;
    public static final long RESET_BACK_MS = 2000L;
    public static final long RESET_AHEAD_MS = 60000L;

    public interface Los {
        boolean clear(float x0, float y0, float z0, float x1, float y1, float z1);
    }

    public static final Los WORLD_LOS = (x0, y0, z0, x1, y1, z1) -> {
        LosUtil.TestResults result = LosUtil.lineClear(
            IsoWorld.instance.currentCell, (int)x0, (int)y0, (int)z0, (int)x1, (int)y1, (int)z1, false
        );
        return result == LosUtil.TestResults.Clear || result == LosUtil.TestResults.ClearThroughOpenDoor;
    };

    static final class Ring {
        static final int SIZE = 64;
        final long[] stamp = new long[SIZE];
        final float[] x = new float[SIZE];
        final float[] y = new float[SIZE];
        final float[] z = new float[SIZE];
        int head = -1;
        int count;
        int lastRaw;
        long lastUnwrapped;
        boolean init;

        long unwrap(int raw) {
            if (!this.init) {
                this.init = true;
                this.lastRaw = raw;
                this.lastUnwrapped = raw & 0xFFFFFFFFL;
                return this.lastUnwrapped;
            }

            this.lastUnwrapped += raw - this.lastRaw;
            this.lastRaw = raw;
            return this.lastUnwrapped;
        }

        long query(double seen) {
            return this.lastUnwrapped + ((int)(long)seen - this.lastRaw);
        }

        int index(int back) {
            return ((this.head - back) % SIZE + SIZE) % SIZE;
        }

        void add(int raw, float px, float py, float pz) {
            long s = this.unwrap(raw);
            if (this.count > 0 && (s < this.stamp[this.head] - RESET_BACK_MS || s > this.stamp[this.head] + RESET_AHEAD_MS)) {
                this.count = 0;
                this.head = -1;
            }

            if (this.count > 0 && s <= this.stamp[this.head]) {
                return;
            }

            this.head = (this.head + 1) % SIZE;
            this.stamp[this.head] = s;
            this.x[this.head] = px;
            this.y[this.head] = py;
            this.z[this.head] = pz;
            this.count = Math.min(this.count + 1, SIZE);
        }

        boolean at(long s, float[] out) {
            if (this.count == 0) {
                return false;
            }

            int newest = this.head;
            if (s >= this.stamp[newest]) {
                out[0] = this.x[newest];
                out[1] = this.y[newest];
                out[2] = this.z[newest];
                return true;
            }

            for (int back = 0; back < this.count - 1; back++) {
                int b = this.index(back);
                int a = this.index(back + 1);
                if (this.stamp[a] <= s) {
                    float t = (float)(s - this.stamp[a]) / (float)Math.max(1L, this.stamp[b] - this.stamp[a]);
                    out[0] = this.x[a] + (this.x[b] - this.x[a]) * t;
                    out[1] = this.y[a] + (this.y[b] - this.y[a]) * t;
                    out[2] = this.z[a] + (this.z[b] - this.z[a]) * t;
                    return true;
                }
            }

            return false;
        }
    }

    private static final HashMap<Short, Ring> RINGS = new HashMap<>();

    private PLZArenaHistory() {
    }

    public static void record(short playerId, int rawStamp, float x, float y, float z) {
        if (rawStamp == 0) {
            return;
        }

        RINGS.computeIfAbsent(playerId, k -> new Ring()).add(rawStamp, x, y, z);
    }

    public static void forget(short playerId) {
        RINGS.remove(playerId);
    }

    public static void reset() {
        RINGS.clear();
    }

    public static int trackedCount() {
        return RINGS.size();
    }

    public static int check(short targetId, double seenStamp, float ix, float iy, float iz, float ox, float oy, float oz, long shooterRttMs, Los los) {
        Ring ring = RINGS.get(targetId);
        if (ring == null || ring.count == 0) {
            return NO_TARGET;
        }

        long seen = ring.query(seenStamp);
        long newest = ring.stamp[ring.head];
        if (seen > newest + FUTURE_SLACK_MS) {
            return FUTURE;
        }

        long oldest = ring.stamp[ring.index(ring.count - 1)];
        if (seen < oldest || newest - seen > Math.max(0L, shooterRttMs) + PLZArenaInterp.MAX_DELAY_MS + STALE_SLACK_MS) {
            return STALE;
        }

        float[] pos = new float[3];
        if (!ring.at(seen, pos)) {
            return STALE;
        }

        float dx = ix - pos[0];
        float dy = iy - pos[1];
        if (dx * dx + dy * dy > HIT_RADIUS * HIT_RADIUS || iz < pos[2] - HIT_BELOW || iz > pos[2] + HIT_ABOVE) {
            return MISSED;
        }

        if (los != null && !los.clear(ox, oy, oz, ix, iy, iz)) {
            return BLOCKED;
        }

        return OK;
    }
}
