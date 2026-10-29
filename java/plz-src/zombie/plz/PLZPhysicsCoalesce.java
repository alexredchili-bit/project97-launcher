package zombie.plz;

import gnu.trove.set.hash.TLongHashSet;
import java.util.List;
import zombie.network.IZomboidPacket;
import zombie.network.ZomboidNetData;

/** Which queued vehicle physics packets an over-budget server cycle can skip. See java-patch/README.md, "vehiclePhysicsCoalesce". */
public final class PLZPhysicsCoalesce {
    private PLZPhysicsCoalesce() {
    }

    /** Stale flags for {@code queue[from..]}: true where a later packet from the same sender carries the same car. */
    public static boolean[] stale(List<IZomboidPacket> queue, int from) {
        int count = Math.max(0, queue.size() - from);
        long[] senders = new long[count];
        int[] vehicles = new int[count];
        for (int i = 0; i < count; i++) {
            IZomboidPacket packet = queue.get(from + i);
            if (packet instanceof ZomboidNetData data && data.buffer.bb.limit() >= 2) {
                senders[i] = data.connection;
                vehicles[i] = data.buffer.bb.getShort(0) & 0xFFFF;
            } else {
                vehicles[i] = -1;
            }
        }

        return stale(senders, vehicles);
    }

    static boolean[] stale(long[] senders, int[] vehicles) {
        boolean[] stale = new boolean[vehicles.length];
        TLongHashSet seen = new TLongHashSet();
        for (int i = vehicles.length - 1; i >= 0; i--) {
            if (vehicles[i] < 0) {
                continue;
            }

            long key = senders[i] * 65537L + vehicles[i];
            stale[i] = !seen.add(key);
        }

        return stale;
    }
}
