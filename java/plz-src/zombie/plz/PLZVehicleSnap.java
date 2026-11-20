package zombie.plz;

import zombie.characters.IsoGameCharacter;
import zombie.characters.IsoPlayer;
import zombie.core.physics.Transform;
import zombie.core.physics.WorldSimulation;
import zombie.iso.IsoChunk;
import zombie.iso.IsoWorld;
import zombie.network.GameClient;
import zombie.vehicles.BaseVehicle;
import zombie.vehicles.VehicleManager;

// "bullet" = the physics body itself moved; every other source is a Java call that wrote the position.
public final class PLZVehicleSnap {
    public static final float MIN_TILES = 4.0F;
    public static final int RING = 8;
    public static final int MAX_TEXT = 400;

    private static final long[] AT_MS = new long[RING];
    private static final String[] SOURCE = new String[RING];
    private static final int[] FROM_X = new int[RING];
    private static final int[] FROM_Y = new int[RING];
    private static final int[] TO_X = new int[RING];
    private static final int[] TO_Y = new int[RING];
    private static final String[] STATE = new String[RING];
    private static int next;
    private static int count;

    private PLZVehicleSnap() {
    }

    public static void onWrite(BaseVehicle vehicle, String source, String hookedMethod, float toX, float toY) {
        if (!GameClient.client || !isLocallyDriven(vehicle) || !PLZFixes.on(PLZFixes.VEHICLE_SNAP_WATCH)) {
            return;
        }

        float fromX = vehicle.getX();
        float fromY = vehicle.getY();
        if (isSnap(fromX, fromY, toX, toY)) {
            String label = hookedMethod == null ? source : source + "<" + callerOf(hookedMethod);
            record(vehicle, label, fromX, fromY, toX, toY);
        }
    }

    public static void onWriteById(int vehicleId, String source, float[] physics) {
        if (!GameClient.client || physics == null || physics.length < 2) {
            return;
        }

        onWrite(VehicleManager.instance.getVehicleByID((short)vehicleId), source, null, physics[0], physics[1]);
    }

    public static void onBulletCopy(Transform target, Transform fresh) {
        if (!GameClient.client || target == null || fresh == null) {
            return;
        }

        BaseVehicle vehicle = localDrivenVehicle();
        if (vehicle == null || vehicle.jniTransform != target || !PLZFixes.on(PLZFixes.VEHICLE_SNAP_WATCH)) {
            return;
        }

        float offX = WorldSimulation.instance.offsetX;
        float offY = WorldSimulation.instance.offsetY;
        float fromX = target.origin.x + offX;
        float fromY = target.origin.z + offY;
        float toX = fresh.origin.x + offX;
        float toY = fresh.origin.z + offY;
        if (isSnap(fromX, fromY, toX, toY)) {
            record(vehicle, "bullet", fromX, fromY, toX, toY);
        }
    }

    /** Recent snaps plus the car's state right now, for the jump row PLZVehTel is about to send. */
    public static String describe(long maxAgeMs) {
        BaseVehicle vehicle = localDrivenVehicle();
        String now = vehicle == null ? "now: no car" : "now: " + stateOf(vehicle);
        String recent = recentAt(System.currentTimeMillis(), maxAgeMs);
        return clip(recent.isEmpty() ? now + "; no tagged write" : recent + "; " + now);
    }

    public static boolean isSnap(float fromX, float fromY, float toX, float toY) {
        float dx = toX - fromX;
        float dy = toY - fromY;
        return dx * dx + dy * dy >= MIN_TILES * MIN_TILES;
    }

    static synchronized void noteAt(long atMs, String source, float fromX, float fromY, float toX, float toY, String state) {
        AT_MS[next] = atMs;
        SOURCE[next] = source;
        FROM_X[next] = (int)Math.floor(fromX);
        FROM_Y[next] = (int)Math.floor(fromY);
        TO_X[next] = (int)Math.floor(toX);
        TO_Y[next] = (int)Math.floor(toY);
        STATE[next] = state;
        next = (next + 1) % RING;
        if (count < RING) {
            count++;
        }
    }

    static synchronized String recentAt(long nowMs, long maxAgeMs) {
        StringBuilder out = new StringBuilder();
        for (int i = count; i >= 1; i--) {
            int slot = (next - i + RING) % RING;
            long age = nowMs - AT_MS[slot];
            if (age < 0L || age > maxAgeMs) {
                continue;
            }

            if (out.length() > 0) {
                out.append("; ");
            }

            int dx = TO_X[slot] - FROM_X[slot];
            int dy = TO_Y[slot] - FROM_Y[slot];
            out.append(SOURCE[slot])
                .append(' ').append(FROM_X[slot]).append(',').append(FROM_Y[slot])
                .append("->").append(TO_X[slot]).append(',').append(TO_Y[slot])
                .append(" d=").append((int)Math.sqrt((double)dx * dx + (double)dy * dy))
                .append(" -").append(age).append("ms ")
                .append(STATE[slot]);
        }

        return out.toString();
    }

    static synchronized void clear() {
        next = 0;
        count = 0;
    }

    static String clip(String text) {
        String flat = text.replace('\n', ' ').replace('\r', ' ');
        return flat.length() <= MAX_TEXT ? flat : flat.substring(0, MAX_TEXT - 3) + "...";
    }

    private static void record(BaseVehicle vehicle, String source, float fromX, float fromY, float toX, float toY) {
        PLZFixes.hit(PLZFixes.VEHICLE_SNAP_WATCH);
        noteAt(System.currentTimeMillis(), source, fromX, fromY, toX, toY, stateOf(vehicle));
    }

    private static BaseVehicle localDrivenVehicle() {
        IsoPlayer player = IsoPlayer.players[0];
        BaseVehicle vehicle = player == null ? null : player.getVehicle();
        return isLocallyDriven(vehicle) ? vehicle : null;
    }

    private static boolean isLocallyDriven(BaseVehicle vehicle) {
        if (vehicle == null) {
            return false;
        }

        IsoGameCharacter driver = vehicle.getDriver();
        return driver instanceof IsoPlayer player && player.isLocalPlayer();
    }

    private static String stateOf(BaseVehicle vehicle) {
        return "auth=" + authOf(vehicle)
            + " phys=" + (vehicle.isPhysicsActive() ? 1 : 0)
            + " chunks=" + (hasChunksAllAround(vehicle) ? 1 : 0)
            + " sq=" + (vehicle.getCurrentSquare() == null ? 0 : 1)
            + " kmh=" + (int)vehicle.getCurrentSpeedKmHour();
    }

    private static String authOf(BaseVehicle vehicle) {
        for (BaseVehicle.Authorization auth : BaseVehicle.Authorization.values()) {
            if (vehicle.isNetPlayerAuthorization(auth)) {
                return auth.name();
            }
        }

        return "?";
    }

    // Mirrors BaseVehicle.hasChunksAllAround, which gates the car's physics and is private.
    private static boolean hasChunksAllAround(BaseVehicle vehicle) {
        int wx = (int)Math.floor(vehicle.jniTransform.origin.x + WorldSimulation.instance.offsetX) / 8;
        int wy = (int)Math.floor(vehicle.jniTransform.origin.z + WorldSimulation.instance.offsetY) / 8;
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (IsoWorld.instance.getMetaGrid().isValidChunk(wx + dx, wy + dy)) {
                    IsoChunk chunk = IsoWorld.instance.currentCell.getChunk(wx + dx, wy + dy);
                    if (chunk == null || !chunk.loaded) {
                        return false;
                    }
                }
            }
        }

        return true;
    }

    private static String callerOf(String hookedMethod) {
        return StackWalker.getInstance().walk(frames -> {
            boolean seen = false;
            for (StackWalker.StackFrame frame : (Iterable<StackWalker.StackFrame>)frames::iterator) {
                if (seen) {
                    String cls = frame.getClassName();
                    String simple = cls.substring(cls.lastIndexOf('.') + 1);
                    int line = frame.getLineNumber();
                    return simple + "." + frame.getMethodName() + (line > 0 ? ":" + line : "");
                }
                seen = hookedMethod.equals(frame.getMethodName());
            }
            return "?";
        });
    }
}
