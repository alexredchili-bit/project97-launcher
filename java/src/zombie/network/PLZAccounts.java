package zombie.network;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import zombie.core.Core;
import zombie.core.secure.PZcrypt;
import zombie.debug.DebugType;
import zombie.debug.LogSeverity;

public final class PLZAccounts {
    private static final String CREDENTIAL_PREFIX = "PLZ-Steam-";
    private static final ConcurrentHashMap<String, FutureTask<String>> credentials = new ConcurrentHashMap<>();
    private static final ExecutorService hasher = Executors.newSingleThreadExecutor(PLZAccounts::hasherThread);

    private PLZAccounts() {
    }

    public static List<String> boundUsernames(String steamId) {
        List<String> names = new ArrayList<>(1);
        if (steamId == null || steamId.isEmpty()) {
            return names;
        }

        Connection conn = ServerWorldDatabase.instance == null ? null : ServerWorldDatabase.instance.conn;
        if (conn == null) {
            return names;
        }

        try (PreparedStatement stat = conn.prepareStatement(
                "SELECT username FROM whitelist WHERE steamid = ? AND world = ?")) {
            stat.setString(1, steamId);
            stat.setString(2, Core.gameSaveWorld);
            try (ResultSet rs = stat.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString("username");
                    if (name != null && !name.isEmpty()) {
                        names.add(name);
                    }
                }
            }
        } catch (Exception e) {
            DebugType.Multiplayer.printException(e, "PLZAccounts.boundUsernames failed", LogSeverity.Error);
        }

        return names;
    }

    /**
     * The SteamID an account name belongs to, or null when this server has never seen it.
     *
     * The inverse of boundUsernames, and the only way to answer "who is this player really" for
     * somebody who is offline: the Lua global getSteamIDFromUsername needs a live client and
     * returns null for everybody else.
     */
    public static String steamIdForUsername(String username) {
        if (username == null || username.isEmpty()) {
            return null;
        }

        Connection conn = ServerWorldDatabase.instance == null ? null : ServerWorldDatabase.instance.conn;
        if (conn == null) {
            return null;
        }

        try (PreparedStatement stat = conn.prepareStatement(
                "SELECT steamid FROM whitelist WHERE username = ? AND world = ?")) {
            stat.setString(1, username);
            stat.setString(2, Core.gameSaveWorld);
            try (ResultSet rs = stat.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                String id = rs.getString("steamid");
                return id == null || id.isEmpty() ? null : id;
            }
        } catch (Exception e) {
            DebugType.Multiplayer.printException(e, "PLZAccounts.steamIdForUsername failed", LogSeverity.Error);
            return null;
        }
    }

    public static void adoptCredential(String username, String credential) {
        Connection conn = ServerWorldDatabase.instance.conn;
        if (conn == null) {
            return;
        }

        try (PreparedStatement stat = conn.prepareStatement(
                "UPDATE whitelist SET password = ? WHERE username = ? AND world = ? AND password <> ?")) {
            stat.setString(1, credential);
            stat.setString(2, username);
            stat.setString(3, Core.gameSaveWorld);
            stat.setString(4, credential);
            if (stat.executeUpdate() > 0) {
                DebugType.General.println(
                    "PLZ: adopted pre-existing account \"" + username + "\" onto its SteamID binding");
            }
        } catch (Exception e) {
            DebugType.Multiplayer.printException(e, "PLZAccounts.adoptCredential failed", LogSeverity.Error);
        }
    }

    public static String credentialFor(String steamId) {
        FutureTask<String> task = credentialTask(steamId);
        task.run();
        try {
            return task.get();
        } catch (Exception e) {
            credentials.remove(steamId, task);
            DebugType.Multiplayer.printException(e, "PLZAccounts.credentialFor cache miss path failed", LogSeverity.Error);
            return computeCredential(steamId);
        }
    }

    // BCrypt cost 12 is ~180 ms; the Steam initiate callback lands ~1.4 s before LoginPacket, off the main loop.
    public static void prefetchCredential(String steamId) {
        if (steamId == null || steamId.isEmpty() || credentials.containsKey(steamId)) {
            return;
        }

        hasher.execute(credentialTask(steamId));
    }

    static boolean isCached(String steamId) {
        FutureTask<String> task = credentials.get(steamId);
        return task != null && task.isDone();
    }

    static String computeCredential(String steamId) {
        return PZcrypt.hash(ServerWorldDatabase.encrypt(CREDENTIAL_PREFIX + steamId));
    }

    private static FutureTask<String> credentialTask(String steamId) {
        FutureTask<String> fresh = new FutureTask<>(() -> computeCredential(steamId));
        FutureTask<String> existing = credentials.putIfAbsent(steamId, fresh);
        return existing != null ? existing : fresh;
    }

    private static Thread hasherThread(Runnable r) {
        Thread t = new Thread(r, "PLZAccounts-hasher");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    }
}
