package zombie.plz;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import zombie.characters.IsoPlayer;
import zombie.characters.NetworkPlayerAI;
import zombie.core.math.PZMath;

public final class PLZArenaInterp {
    public static final long MIN_DELAY_MS = 40L;
    public static final long MAX_DELAY_MS = 120L;
    public static final long EXTRAPOLATE_MS = 100L;
    public static final long STALE_MS = 2000L;
    public static final float SNAP_TILES = 3.0F;
    public static final float MOVING_SPEED = 0.05F;
    public static final float STUCK_FACING_RADS = (float)(Math.PI / 4);
    public static final long STUCK_FACING_MS = 1200L;

    private static final HashMap<Short, Track> TRACKS = new HashMap<>();
    private static long lastPruneMs;
    public static long starvedSamples;

    private PLZArenaInterp() {
    }

    public static final class Track {
        static final int SIZE = 32;
        final long[] stamp = new long[SIZE];
        final float[] x = new float[SIZE];
        final float[] y = new float[SIZE];
        final float[] z = new float[SIZE];
        final float[] angle = new float[SIZE];
        final boolean[] moving = new boolean[SIZE];
        int head = -1;
        int count;
        int lastRaw;
        long lastUnwrapped;
        boolean unwrapInit;
        double offset;
        boolean offsetInit;
        double interval = 50.0;
        double jitter;
        long lastArrivalMs;
        boolean snapFacing = true;
        long facingOffSinceMs = -1L;
        boolean placed;

        public float outX;
        public float outY;
        public float outZ;
        public float outAngle;
        public float outSpeed;
        public float outVX;
        public float outVY;
        public boolean outMoving;
        public long outStamp;

        long unwrap(int raw) {
            if (!this.unwrapInit) {
                this.unwrapInit = true;
                this.lastRaw = raw;
                this.lastUnwrapped = raw & 0xFFFFFFFFL;
                return this.lastUnwrapped;
            }

            this.lastUnwrapped += raw - this.lastRaw;
            this.lastRaw = raw;
            return this.lastUnwrapped;
        }

        public boolean push(int rawStamp, long arrivalMs, float px, float py, float pz, float pangle, boolean pmoving) {
            long s = rawStamp == 0 ? (this.offsetInit ? arrivalMs - (long)this.offset : arrivalMs) : this.unwrap(rawStamp);
            if (this.count > 0
                && (s < this.stamp[this.head] - PLZArenaHistory.RESET_BACK_MS || s > this.stamp[this.head] + PLZArenaHistory.RESET_AHEAD_MS)) {
                this.count = 0;
                this.head = -1;
                this.offsetInit = false;
                this.snapFacing = true;
            }

            if (this.count > 0 && s <= this.stamp[this.head]) {
                return false;
            }

            double sample = arrivalMs - s;
            if (!this.offsetInit || sample < this.offset) {
                this.offset = sample;
                this.offsetInit = true;
            } else {
                this.offset += (sample - this.offset) * 0.02;
            }

            if (this.count > 0) {
                long dt = s - this.stamp[this.head];
                if (dt <= 250L) {
                    this.interval = this.interval * 0.9 + dt * 0.1;
                }
            }

            this.jitter = this.jitter * 0.9 + Math.abs(sample - this.offset) * 0.1;
            this.head = (this.head + 1) % SIZE;
            this.stamp[this.head] = s;
            this.x[this.head] = px;
            this.y[this.head] = py;
            this.z[this.head] = pz;
            this.angle[this.head] = pangle;
            this.moving[this.head] = pmoving;
            this.count = Math.min(this.count + 1, SIZE);
            this.lastArrivalMs = arrivalMs;
            return true;
        }

        public long delayMs() {
            long d = (long)(2.0 * this.interval + this.jitter);
            return Math.max(MIN_DELAY_MS, Math.min(MAX_DELAY_MS, d));
        }

        public boolean active(long nowMs) {
            return this.count > 0 && nowMs - this.lastArrivalMs < STALE_MS;
        }

        public boolean shouldSnapFacing(float bodyAngle, float targetAngle, long nowMs) {
            if (this.snapFacing) {
                this.snapFacing = false;
                this.facingOffSinceMs = -1L;
                return true;
            }

            if (Math.abs(angleDiff(bodyAngle, targetAngle)) <= STUCK_FACING_RADS) {
                this.facingOffSinceMs = -1L;
                return false;
            }

            if (this.facingOffSinceMs < 0L) {
                this.facingOffSinceMs = nowMs;
                return false;
            }

            if (nowMs - this.facingOffSinceMs < STUCK_FACING_MS) {
                return false;
            }

            this.facingOffSinceMs = -1L;
            return true;
        }

        int index(int back) {
            return ((this.head - back) % SIZE + SIZE) % SIZE;
        }

        public void sample(long nowMs) {
            long render = nowMs - (long)this.offset - this.delayMs();
            this.outStamp = Math.min(render, this.stamp[this.head] + EXTRAPOLATE_MS);
            if (render > this.stamp[this.head] + EXTRAPOLATE_MS) {
                starvedSamples++;
            }
            int newest = this.head;
            if (this.count == 1 || render >= this.stamp[newest]) {
                this.extrapolate(render);
                return;
            }

            for (int back = 0; back < this.count - 1; back++) {
                int b = this.index(back);
                int a = this.index(back + 1);
                if (this.stamp[a] <= render) {
                    float t = (float)(render - this.stamp[a]) / (float)Math.max(1L, this.stamp[b] - this.stamp[a]);
                    this.outX = this.x[a] + (this.x[b] - this.x[a]) * t;
                    this.outY = this.y[a] + (this.y[b] - this.y[a]) * t;
                    this.outZ = this.z[a] + (this.z[b] - this.z[a]) * t;
                    this.outAngle = lerpAngle(this.angle[a], this.angle[b], t);
                    this.outSpeed = speed(this.x[a], this.y[a], this.x[b], this.y[b], this.stamp[b] - this.stamp[a]);
                    this.velocity(a, b);
                    this.outMoving = (this.moving[a] || this.moving[b]) && this.outSpeed > MOVING_SPEED;
                    return;
                }
            }

            int oldest = this.index(this.count - 1);
            this.copy(oldest);
            this.outSpeed = 0.0F;
            this.outVX = 0.0F;
            this.outVY = 0.0F;
            this.outMoving = false;
        }

        private void extrapolate(long render) {
            int n = this.head;
            this.copy(n);
            this.outSpeed = 0.0F;
            this.outVX = 0.0F;
            this.outVY = 0.0F;
            this.outMoving = false;
            if (this.count < 2 || !this.moving[n]) {
                return;
            }

            int p = this.index(1);
            long span = this.stamp[n] - this.stamp[p];
            if (span <= 0L) {
                return;
            }

            long ahead = Math.min(render - this.stamp[n], EXTRAPOLATE_MS);
            float t = (float)ahead / (float)span;
            this.outX = this.x[n] + (this.x[n] - this.x[p]) * t;
            this.outY = this.y[n] + (this.y[n] - this.y[p]) * t;
            this.outSpeed = speed(this.x[p], this.y[p], this.x[n], this.y[n], span);
            this.velocity(p, n);
            this.outMoving = this.outSpeed > MOVING_SPEED;
        }

        private void velocity(int from, int to) {
            long ms = this.stamp[to] - this.stamp[from];
            if (ms <= 0L) {
                this.outVX = 0.0F;
                this.outVY = 0.0F;
                return;
            }
            this.outVX = (this.x[to] - this.x[from]) * 1000.0F / ms;
            this.outVY = (this.y[to] - this.y[from]) * 1000.0F / ms;
        }

        private void copy(int i) {
            this.outX = this.x[i];
            this.outY = this.y[i];
            this.outZ = this.z[i];
            this.outAngle = this.angle[i];
        }
    }

    static float speed(float ax, float ay, float bx, float by, long ms) {
        if (ms <= 0L) {
            return 0.0F;
        }

        float dx = bx - ax;
        float dy = by - ay;
        return (float)Math.sqrt(dx * dx + dy * dy) * 1000.0F / ms;
    }

    static float angleDiff(float a, float b) {
        float twoPi = (float)(Math.PI * 2);
        float d = (b - a) % twoPi;
        if (d > Math.PI) {
            d -= twoPi;
        } else if (d < -Math.PI) {
            d += twoPi;
        }

        return d;
    }

    static float lerpAngle(float a, float b, float t) {
        return a + angleDiff(a, b) * t;
    }

    static long nowMs() {
        return System.nanoTime() / 1000000L;
    }

    static boolean localInArena() {
        return PLZArena.inZone(IsoPlayer.getInstance());
    }

    // Only the holder: the held player is placed by the grapple, and a second source would fight it.
    static boolean drawsPair(IsoPlayer remote) {
        return PLZArena.inPair(remote) && PLZArena.holdsPlayer(remote);
    }

    public static void push(IsoPlayer remote, int rawStamp, float x, float y, float z, float angle, boolean moving) {
        if (remote == null || remote.isLocalPlayer()) {
            return;
        }

        long now = nowMs();
        TRACKS.computeIfAbsent(remote.getOnlineID(), k -> new Track()).push(rawStamp, now, x, y, z, angle, moving);
        PLZArena.pushedSnapshots++;
        if (now - lastPruneMs > 5000L) {
            lastPruneMs = now;
            Iterator<Map.Entry<Short, Track>> it = TRACKS.entrySet().iterator();
            while (it.hasNext()) {
                if (!it.next().getValue().active(now)) {
                    it.remove();
                }
            }
        }
    }

    public static boolean owns(IsoPlayer remote, float packetX, float packetY, float packetZ) {
        return remote != null && (drawsPair(remote) || PLZArena.contains(packetX, packetY, packetZ) && localInArena());
    }

    public static boolean target(IsoPlayer remote, NetworkPlayerAI ai) {
        // Animals run updateRemotePlayer too and their online ids overlap players'.
        if (remote == null || remote.isAnimal() || remote.isSeatedInVehicle()) {
            return false;
        }

        boolean pair = drawsPair(remote);
        if (!pair && !localInArena()) {
            return false;
        }

        Track track = TRACKS.get(remote.getOnlineID());
        long now = nowMs();
        if (track == null || !track.active(now)) {
            return false;
        }

        track.sample(now);
        if (!pair && !PLZArena.contains(track.outX, track.outY, track.outZ)) {
            return false;
        }

        float dx = track.outX - remote.getX();
        float dy = track.outY - remote.getY();
        boolean floorChanged = PZMath.fastfloor(track.outZ) != PZMath.fastfloor(remote.getZ());
        if (floorChanged || dx * dx + dy * dy > SNAP_TILES * SNAP_TILES) {
            remote.removeFromSquare();
            remote.setForceX(track.outX);
            remote.setForceY(track.outY);
            remote.setZ(track.outZ);
            remote.setLastZ(track.outZ);
            remote.ensureOnTile();
            remote.setMovingSquareNow();
            track.snapFacing = true;
        }

        ai.targetX = track.outX;
        ai.targetY = track.outY;
        ai.targetZ = (byte)PZMath.fastfloor(track.outZ);
        return true;
    }

    public static void place(IsoPlayer remote, float targetX, float targetY) {
        Track track = TRACKS.get(remote.getOnlineID());
        remote.moveUnmodded(targetX - remote.getNextX(), targetY - remote.getNextY());
        if (track != null) {
            track.placed = true;
        }
    }

    public static boolean isMoving(IsoPlayer remote) {
        Track track = TRACKS.get(remote.getOnlineID());
        return track != null && track.outMoving;
    }

    public static double seenStamp(IsoPlayer remote) {
        Track track = remote == null ? null : TRACKS.get(remote.getOnlineID());
        return track == null || track.count == 0 || track.outStamp == 0L ? -1.0 : track.outStamp;
    }

    public static Track track(IsoPlayer remote) {
        return remote == null ? null : TRACKS.get(remote.getOnlineID());
    }

    public static void face(IsoPlayer remote) {
        Track track = TRACKS.get(remote.getOnlineID());
        if (track == null || !track.placed) {
            return;
        }

        track.placed = false;
        float fx = (float)Math.cos(track.outAngle);
        float fy = (float)Math.sin(track.outAngle);
        if (track.shouldSnapFacing(remote.getAnimAngleRadians(), track.outAngle, nowMs())) {
            remote.setTargetAndCurrentDirection(fx, fy);
        } else {
            remote.setForwardDirection(fx, fy);
        }
    }

    public static String describe() {
        StringBuilder sb = new StringBuilder("tracks=");
        long now = nowMs();
        int active = 0;
        for (Track track : TRACKS.values()) {
            if (!track.active(now)) {
                continue;
            }
            sb.append(active == 0 ? "" : "|")
                .append("delay:").append(track.delayMs())
                .append(",interval:").append((long)track.interval)
                .append(",jitter:").append((long)track.jitter);
            active++;
        }
        if (active == 0) {
            sb.append("none");
        }
        return sb.append(" starved=").append(starvedSamples).toString();
    }

    public static int trackedCount() {
        return TRACKS.size();
    }

    public static void reset() {
        TRACKS.clear();
    }
}
