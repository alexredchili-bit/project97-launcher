package zombie.plz;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import zombie.core.raknet.RakVoice;
import zombie.core.raknet.UdpConnection;

/** Server half of radio stations: who may transmit on a station frequency. */
public final class PLZStationClamp {
    private static final int MAX_CACHED_TABLES = 256;

    private static final Map<Integer, Set<String>> hosts = new HashMap<>();

    private static final class Cached {
        UdpConnection connection;
        boolean canHearAll;
        int[] raw;
        int size;
    }

    private static final Map<Long, Cached> tables = new LinkedHashMap<Long, Cached>(16, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, Cached> eldest) {
            return this.size() > MAX_CACHED_TABLES;
        }
    };

    public static volatile long clamped;

    private PLZStationClamp() {
    }

    public static void setFrequencies(String csv) {
        PLZStation.setFrequencies(csv);
        repushAll();
    }

    public static void setHosts(int freq, String csvUsernames) {
        Set<String> set = new HashSet<>();
        if (csvUsernames != null) {
            for (String name : csvUsernames.split(",")) {
                if (!name.isBlank()) {
                    set.add(name.trim());
                }
            }
        }

        synchronized (hosts) {
            if (set.isEmpty()) {
                hosts.remove(freq);
            } else {
                hosts.put(freq, set);
            }
        }

        repushAll();
    }

    public static boolean isHost(int freq, String username) {
        if (username == null) {
            return false;
        }

        synchronized (hosts) {
            Set<String> set = hosts.get(freq);
            return set != null && set.contains(username);
        }
    }

    public static String hostList() {
        synchronized (hosts) {
            return hosts.toString();
        }
    }

    /** Zeroes the range of every station-frequency entry the sender is not a host of. Mutates data. */
    public static void clamp(UdpConnection connection, boolean canHearAll, int[] data, int size) {
        if (connection == null || data == null || !PLZStation.hasStations()) {
            return;
        }

        int count = Math.min(size, data.length) / 4;
        boolean touches = false;
        for (int i = 0; i < count; i++) {
            if (PLZStation.isStation(data[i * 4])) {
                touches = true;
                break;
            }
        }

        long guid = connection.getConnectedGUID();
        if (!touches) {
            synchronized (tables) {
                tables.remove(guid);
            }
            return;
        }

        Cached cached = new Cached();
        cached.connection = connection;
        cached.canHearAll = canHearAll;
        cached.raw = data.clone();
        cached.size = size;
        synchronized (tables) {
            tables.put(guid, cached);
        }

        String username = connection.players[0] != null ? connection.players[0].getUsername() : null;
        clamped += clampData(data, size, username);
    }

    /** The clamp itself, without a connection: how many entries it zeroed. */
    public static int clampData(int[] data, int size, String username) {
        int count = Math.min(size, data.length) / 4;
        int zeroed = 0;
        for (int i = 0; i < count; i++) {
            if (data[i * 4 + 1] > 0 && PLZStation.isStation(data[i * 4]) && !isHost(data[i * 4], username)) {
                data[i * 4 + 1] = 0;
                zeroed++;
            }
        }

        return zeroed;
    }

    public static void forget(long guid) {
        synchronized (tables) {
            tables.remove(guid);
        }
    }

    // Native routing keeps the last table it was handed, so a revoked host must be re-pushed, not waited on.
    private static void repushAll() {
        Cached[] snapshot;
        synchronized (tables) {
            snapshot = tables.values().toArray(new Cached[0]);
        }

        for (Cached c : snapshot) {
            if (c.connection == null || !c.connection.isFullyConnected()) {
                continue;
            }

            int[] copy = c.raw.clone();
            clamp(c.connection, c.canHearAll, copy, c.size);
            if (PLZFixes.on(PLZFixes.VOICE_ROUTING_SLACK)) {
                PLZVoiceRouting.publish(c.connection, c.canHearAll, copy, c.size);
            } else {
                RakVoice.SetChannelsRouting(c.connection.getConnectedGUID(), c.canHearAll, copy, (short)c.size);
            }
        }
    }
}
