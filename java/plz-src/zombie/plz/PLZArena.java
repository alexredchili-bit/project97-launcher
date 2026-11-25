package zombie.plz;

import java.util.HashMap;
import zombie.characters.IsoPlayer;
import zombie.characters.animals.IsoAnimal;
import zombie.core.skinnedmodel.BaseGrappleable;
import zombie.core.skinnedmodel.IGrappleable;

public final class PLZArena {
    public static final long SEND_MS = 33L;
    public static final float MOVE_EPSILON = 0.02F;
    public static final float TURN_EPSILON = 0.035F;
    public static final int OUTSIDE_EVERY = 4;

    public static long sentReliable;
    public static long sentUnreliable;
    public static long relayedSkipped;
    public static long pushedSnapshots;
    public static long reasonFlags;
    public static long reasonTurning;
    public static long reasonDirection;
    public static long reasonTimer;
    public static long reasonSquare;
    public static long reasonForced;

    private static final int STRIDE = 6;
    private static volatile float[] zones = new float[0];
    private static final HashMap<Short, int[]> OUTSIDE_TICKS = new HashMap<>();

    private PLZArena() {
    }

    public static int setZones(String csv) {
        if (csv == null || csv.trim().isEmpty()) {
            zones = new float[0];
            return 0;
        }

        String[] rects = csv.split(";");
        float[] parsed = new float[rects.length * STRIDE];
        int count = 0;
        for (String rect : rects) {
            String[] parts = rect.trim().split(",");
            if (parts.length != STRIDE) {
                continue;
            }

            float[] v = new float[STRIDE];
            boolean ok = true;
            for (int i = 0; i < STRIDE; i++) {
                try {
                    v[i] = Float.parseFloat(parts[i].trim());
                } catch (NumberFormatException e) {
                    ok = false;
                    break;
                }
            }

            if (!ok) {
                continue;
            }

            int o = count * STRIDE;
            parsed[o] = Math.min(v[0], v[2]);
            parsed[o + 1] = Math.min(v[1], v[3]);
            parsed[o + 2] = Math.max(v[0], v[2]) + 1.0F;
            parsed[o + 3] = Math.max(v[1], v[3]) + 1.0F;
            parsed[o + 4] = Math.min(v[4], v[5]);
            parsed[o + 5] = Math.max(v[4], v[5]) + 1.0F;
            count++;
        }

        float[] published = new float[count * STRIDE];
        System.arraycopy(parsed, 0, published, 0, published.length);
        zones = published;
        return count;
    }

    public static int zoneCount() {
        return zones.length / STRIDE;
    }

    public static boolean contains(float x, float y, float z) {
        float[] z6 = zones;
        for (int o = 0; o < z6.length; o += STRIDE) {
            if (x >= z6[o] && x < z6[o + 2] && y >= z6[o + 1] && y < z6[o + 3] && z >= z6[o + 4] && z < z6[o + 5]) {
                return true;
            }
        }

        return false;
    }

    public static boolean isFast(IsoPlayer player) {
        return inZone(player) || inPair(player);
    }

    public static boolean inZone(IsoPlayer player) {
        return zones.length != 0
            && player != null
            && !player.isDead()
            && player.getVehicle() == null
            && contains(player.getX(), player.getY(), player.getZ());
    }

    // Carries, escorts, drags and hand-holding are all one player grappling another.
    public static boolean inPair(IsoPlayer player) {
        return player != null && !player.isDead() && player.getVehicle() == null && (holdsPlayer(player) || heldByPlayer(player));
    }

    public static boolean holdsPlayer(IsoPlayer player) {
        return player != null && !(player instanceof IsoAnimal) && player.isGrappling() && playerOf(player.getGrapplingTarget()) != null;
    }

    public static boolean heldByPlayer(IsoPlayer player) {
        return player != null && !(player instanceof IsoAnimal) && player.isBeingGrappled() && playerOf(player.getGrappledBy()) != null;
    }

    public static String describe(IsoPlayer player) {
        if (player == null) {
            return "none";
        }

        return "zone=" + inZone(player)
            + " pair=" + inPair(player)
            + " holds=" + holdsPlayer(player)
            + " held=" + heldByPlayer(player)
            + " grappling=" + player.isGrappling()
            + " grappled=" + player.isBeingGrappled()
            + " by=" + (player.getGrappledBy() == null ? "null" : player.getGrappledBy().getClass().getSimpleName())
            + " target=" + (player.getGrapplingTarget() == null ? "null" : player.getGrapplingTarget().getClass().getSimpleName())
            + " sentMoved=" + sentMoved;
    }

    public static long sentMoved;

    static IsoPlayer playerOf(IGrappleable grappleable) {
        IGrappleable owner = grappleable instanceof BaseGrappleable base ? base.getParentGrappleable() : grappleable;
        return owner instanceof IsoPlayer player && !(owner instanceof IsoAnimal) ? player : null;
    }

    public static boolean moved(float lastX, float lastY, float lastAngle, float x, float y, float angle) {
        if (Math.abs(x - lastX) > MOVE_EPSILON || Math.abs(y - lastY) > MOVE_EPSILON) {
            return true;
        }

        float turn = Math.abs(angle - lastAngle) % (float)(Math.PI * 2);
        if (turn > Math.PI) {
            turn = (float)(Math.PI * 2) - turn;
        }

        return turn > TURN_EPSILON;
    }

    public static boolean outsideTick(short playerId) {
        int[] tick = OUTSIDE_TICKS.computeIfAbsent(playerId, k -> new int[1]);
        boolean send = tick[0] % OUTSIDE_EVERY == 0;
        tick[0]++;
        return send;
    }

    public static void forget(short playerId) {
        OUTSIDE_TICKS.remove(playerId);
    }

    public static void countSent(boolean unreliable) {
        if (unreliable) {
            sentUnreliable++;
        } else {
            sentReliable++;
        }
    }

    public static void countReasons(boolean flags, boolean turning, boolean direction, boolean timer, boolean square, boolean forced) {
        if (flags) {
            reasonFlags++;
        }
        if (turning) {
            reasonTurning++;
        }
        if (direction) {
            reasonDirection++;
        }
        if (timer) {
            reasonTimer++;
        }
        if (square) {
            reasonSquare++;
        }
        if (forced) {
            reasonForced++;
        }
    }

    public static void resetCounters() {
        sentReliable = 0L;
        sentUnreliable = 0L;
        relayedSkipped = 0L;
        sentMoved = 0L;
        pushedSnapshots = 0L;
        reasonFlags = 0L;
        reasonTurning = 0L;
        reasonDirection = 0L;
        reasonTimer = 0L;
        reasonSquare = 0L;
        reasonForced = 0L;
    }

    public static String status() {
        return "zones=" + zoneCount()
            + " history=" + PLZArenaHistory.trackedCount()
            + " interp=" + PLZArenaInterp.trackedCount()
            + " sentReliable=" + sentReliable
            + " sentUnreliable=" + sentUnreliable
            + " relayedSkipped=" + relayedSkipped
            + " pushed=" + pushedSnapshots
            + " why=flags:" + reasonFlags + ",turn:" + reasonTurning + ",dir:" + reasonDirection
            + ",timer:" + reasonTimer + ",square:" + reasonSquare + ",forced:" + reasonForced
            + " " + PLZArenaInterp.describe();
    }
}
