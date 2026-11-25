package zombie.plz;

import java.io.File;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import zombie.ZomboidFileSystem;
import zombie.characters.IsoPlayer;
import zombie.debug.DebugLog;
import zombie.network.GameClient;
import zombie.network.ZomboidNetData;

public final class PLZJoinWatch {
    public static final String FILE = "plz-joinfreeze.txt";
    public static final String REPORTED_FILE = "plz-joinfreeze-reported.txt";
    private static final long[] REPORT_AT_S = {15, 30, 60, 120, 240, 480};
    private static final int FRAMES = 40;
    private static final double MB = 1024.0 * 1024.0;
    private static Thread watcher;

    private PLZJoinWatch() {
    }

    public static synchronized void arm() {
        if (watcher != null && watcher.isAlive()) {
            return;
        }
        Thread game = Thread.currentThread();
        long start = System.currentTimeMillis();
        watcher = new Thread(() -> watch(game, start), "PLZJoinWatch");
        watcher.setDaemon(true);
        watcher.start();
    }

    public static File file(String name) {
        return new File(ZomboidFileSystem.instance.getCacheDirSub(name));
    }

    private static boolean waiting() {
        IsoPlayer player = IsoPlayer.getInstance();
        return player != null && player.onlineId == -1 && GameClient.connection != null;
    }

    // Runs off the game thread because the game thread is the one that may be stuck; it must never throw.
    private static void watch(Thread game, long start) {
        StringBuilder all = new StringBuilder();
        int next = 0;
        long[] lastCpu = {-1L};
        List<String> lastStack = new ArrayList<>();
        try {
            while (waiting()) {
                Thread.sleep(500L);
                long seconds = (System.currentTimeMillis() - start) / 1000L;
                if (next < REPORT_AT_S.length && seconds >= REPORT_AT_S[next]) {
                    next++;
                    emit(all, report(game, seconds, next, lastCpu, lastStack));
                }
            }
            if (next > 0) {
                long seconds = (System.currentTimeMillis() - start) / 1000L;
                emit(all, GameClient.connection == null
                    ? "PLZJoinWatch: connection closed while still waiting, after " + seconds + "s\n"
                    : "PLZJoinWatch: player-connect answered after " + seconds + "s\n");
            }
        } catch (Throwable ignored) {
        }
    }

    private static void emit(StringBuilder all, String text) {
        all.append(text);
        DebugLog.log(text.stripTrailing());
        try {
            Files.writeString(file(FILE).toPath(), all.toString(), StandardCharsets.UTF_8);
        } catch (Throwable ignored) {
        }
    }

    private static String report(Thread game, long seconds, int n, long[] lastCpu, List<String> lastStack) {
        StringBuilder sb = new StringBuilder();
        sb.append("PLZJoinWatch: still waiting for player-connect after ").append(seconds).append("s (report ").append(n).append(")\n");
        if (n == 1) {
            sb.append("  started ").append(new java.util.Date(System.currentTimeMillis() - seconds * 1000L)).append('\n');
        }
        sb.append("  queue ").append(queue("MainLoopNetData")).append(" | delayed ").append(queue("LoadingMainLoopNetData")).append('\n');
        sb.append("  ").append(memory()).append('\n');
        thread(sb, game, lastCpu, lastStack);
        return sb.toString();
    }

    private static String queue(String fieldName) {
        try {
            Field f = GameClient.class.getDeclaredField(fieldName);
            f.setAccessible(true);
            Object[] items = ((ArrayList<?>)f.get(null)).toArray();
            List<String> names = new ArrayList<>();
            for (Object o : items) {
                if (o instanceof ZomboidNetData data && data.type != null) {
                    names.add(data.type.name());
                }
            }
            return describe(items.length, names);
        } catch (Throwable t) {
            return "unreadable(" + t.getClass().getSimpleName() + ")";
        }
    }

    static String describe(int total, List<String> names) {
        Map<String, Integer> types = new LinkedHashMap<>();
        for (String name : names) {
            types.merge(name, 1, Integer::sum);
        }
        StringBuilder sb = new StringBuilder().append(total);
        types.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue()).limit(8)
            .forEach(e -> sb.append(' ').append(e.getKey()).append(':').append(e.getValue()));
        return sb.toString();
    }

    private static String memory() {
        StringBuilder sb = new StringBuilder();
        try {
            MemoryUsage heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
            sb.append("heap used=").append(mb(heap.getUsed())).append(" committed=").append(mb(heap.getCommitted()))
                .append(" max=").append(mb(heap.getMax()));
            for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
                sb.append(" | ").append(gc.getName().replace(' ', '_')).append('=').append(gc.getCollectionCount())
                    .append('/').append(gc.getCollectionTime()).append("ms");
            }
            if (ManagementFactory.getOperatingSystemMXBean() instanceof com.sun.management.OperatingSystemMXBean os) {
                sb.append(" | ramFree=").append(mb(os.getFreeMemorySize())).append('/').append(mb(os.getTotalMemorySize()))
                    .append(" commitFree=").append(mb(os.getFreeSwapSpaceSize())).append('/').append(mb(os.getTotalSwapSpaceSize()))
                    .append(" processCommit=").append(mb(os.getCommittedVirtualMemorySize()))
                    .append(" cpuProcess=").append(Math.round(os.getProcessCpuLoad() * 1000.0) / 10.0).append('%')
                    .append(" cpuSystem=").append(Math.round(os.getCpuLoad() * 1000.0) / 10.0).append('%');
            }
        } catch (Throwable t) {
            sb.append(" memory unreadable(").append(t.getClass().getSimpleName()).append(')');
        }
        return sb.toString();
    }

    private static void thread(StringBuilder sb, Thread game, long[] lastCpu, List<String> lastStack) {
        try {
            ThreadMXBean mx = ManagementFactory.getThreadMXBean();
            ThreadInfo info = mx.getThreadInfo(new long[]{game.threadId()}, true, true)[0];
            if (info == null) {
                sb.append("  game thread gone\n");
                return;
            }
            long cpu = mx.isThreadCpuTimeSupported() ? mx.getThreadCpuTime(game.threadId()) / 1_000_000L : -1L;
            sb.append("  game thread ").append(info.getThreadState()).append(" cpu=").append(cpu).append("ms");
            if (lastCpu[0] >= 0 && cpu >= 0) {
                sb.append(" (+").append(cpu - lastCpu[0]).append("ms since last report)");
            }
            lastCpu[0] = cpu;
            if (info.getLockName() != null) {
                sb.append(" lock=").append(info.getLockName());
                if (info.getLockOwnerName() != null) {
                    sb.append(" held by ").append(info.getLockOwnerName());
                }
            }
            sb.append('\n');

            List<String> frames = new ArrayList<>();
            for (StackTraceElement e : info.getStackTrace()) {
                if (frames.size() >= FRAMES) {
                    break;
                }
                frames.add(e.toString());
            }
            if (!lastStack.isEmpty() && frames.equals(lastStack)) {
                sb.append("    same stack as the previous report\n");
                return;
            }
            lastStack.clear();
            lastStack.addAll(frames);
            for (String f : frames) {
                sb.append("    at ").append(f).append('\n');
            }
        } catch (Throwable t) {
            sb.append("  game thread unreadable(").append(t.getClass().getSimpleName()).append(")\n");
        }
    }

    private static String mb(long bytes) {
        return bytes < 0 ? "?" : Math.round(bytes / MB) + "MB";
    }

    static String[] lines(String text) {
        return Arrays.stream(text.split("\r?\n")).filter(s -> !s.isBlank()).toArray(String[]::new);
    }
}
