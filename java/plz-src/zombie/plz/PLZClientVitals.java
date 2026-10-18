package zombie.plz;

import java.io.File;
import java.io.RandomAccessFile;
import java.lang.management.BufferPoolMXBean;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.lang.management.MemoryUsage;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import zombie.core.logger.LoggerManager;
import zombie.core.textures.Texture;
import zombie.core.textures.TextureID;

public final class PLZClientVitals {
    private static final String[] KEYS = {
        "heapUsedMB", "heapCommittedMB", "heapMaxMB", "heapAfterGcMB", "nonHeapUsedMB",
        "directMB", "directCount", "mappedMB",
        "gcCycles", "gcMajorCycles", "gcPauses", "gcPauseMs", "gcCycleMs",
        "processCommitMB", "ramTotalMB", "ramFreeMB", "swapTotalMB", "swapFreeMB",
        "cpuProcessPct", "cpuSystemPct", "threads", "uptimeS", "textureMB", "textureIds",
    };
    private static final double MB = 1024.0 * 1024.0;
    private static final int TAIL_BYTES = 256 * 1024;
    private static final int LINE_MAX = 220;
    private static final Pattern WIN_PATH = Pattern.compile("[A-Za-z]:[\\\\/][^\\s\"'<>|]*");
    private static final Pattern NIX_PATH = Pattern.compile("(?<![\\w.])/(?:home|Users|mnt|media|tmp)/[^\\s\"'<>|]*");

    private static final Map<String, Integer> INDEX = new HashMap<>();
    private static final double[] values = new double[KEYS.length];
    private static volatile double[] published = new double[KEYS.length];
    private static volatile boolean available = true;
    private static volatile String failure = "";
    private static volatile String environment = "";
    private static volatile String crashEvidence = "";
    private static volatile long intervalMs = 10000L;
    private static volatile boolean running;
    private static Thread worker;

    private static List<GarbageCollectorMXBean> collectors;
    private static List<MemoryPoolMXBean> heapPools;
    private static List<BufferPoolMXBean> buffers;
    private static long lastCycles = -1;
    private static long lastMajor;
    private static long lastPauses;
    private static long lastPauseMs;
    private static long lastCycleMs;

    static {
        for (int i = 0; i < KEYS.length; i++) {
            INDEX.put(KEYS[i], i);
        }
    }

    private PLZClientVitals() {
    }

    public static boolean isAvailable() {
        return available;
    }

    public static String getFailure() {
        return failure;
    }

    public static String keys() {
        return String.join(",", KEYS);
    }

    public static double get(String key) {
        Integer i = INDEX.get(key);
        return i == null ? -1.0 : published[i];
    }

    public static String getEnvironment() {
        return environment;
    }

    public static String getCrashEvidence() {
        return crashEvidence;
    }

    // The OS and GC queries cost 25-35 ms per sample on Windows, so they never run on the game thread.
    public static synchronized void start(int ms) {
        intervalMs = Math.max(1000L, ms);
        running = true;
        if (worker != null && worker.isAlive()) {
            return;
        }
        worker = new Thread(PLZClientVitals::run, "PLZVitals");
        worker.setDaemon(true);
        worker.setPriority(Thread.MIN_PRIORITY);
        worker.start();
    }

    public static synchronized void stop() {
        running = false;
        if (worker != null) {
            worker.interrupt();
            worker = null;
        }
    }

    private static void run() {
        if (environment.isEmpty()) {
            environment = buildEnvironment();
            crashEvidence = buildCrashEvidence();
        }
        while (running && available) {
            collect();
            try {
                Thread.sleep(intervalMs);
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    private static void set(String key, double value) {
        values[INDEX.get(key)] = Math.round(value * 10.0) / 10.0;
    }

    private static void init() {
        if (collectors != null) {
            return;
        }

        collectors = ManagementFactory.getGarbageCollectorMXBeans();
        heapPools = new ArrayList<>();
        for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
            if (pool.getType() == MemoryType.HEAP) {
                heapPools.add(pool);
            }
        }
        buffers = ManagementFactory.getPlatformMXBeans(BufferPoolMXBean.class);
    }

    private static void collect() {
        if (!available) {
            return;
        }

        try {
            init();
            MemoryUsage heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
            MemoryUsage nonHeap = ManagementFactory.getMemoryMXBean().getNonHeapMemoryUsage();
            set("heapUsedMB", heap.getUsed() / MB);
            set("heapCommittedMB", heap.getCommitted() / MB);
            set("heapMaxMB", Runtime.getRuntime().maxMemory() / MB);
            set("nonHeapUsedMB", nonHeap.getUsed() / MB);

            long afterGc = 0;
            for (MemoryPoolMXBean pool : heapPools) {
                MemoryUsage usage = pool.getCollectionUsage();
                if (usage != null) {
                    afterGc += usage.getUsed();
                }
            }
            set("heapAfterGcMB", afterGc / MB);

            for (BufferPoolMXBean pool : buffers) {
                if ("direct".equals(pool.getName())) {
                    set("directMB", pool.getMemoryUsed() / MB);
                    set("directCount", pool.getCount());
                } else if ("mapped".equals(pool.getName())) {
                    set("mappedMB", pool.getMemoryUsed() / MB);
                }
            }

            long cycles = 0;
            long major = 0;
            long pauses = 0;
            long pauseMs = 0;
            long cycleMs = 0;
            for (GarbageCollectorMXBean gc : collectors) {
                long count = Math.max(0L, gc.getCollectionCount());
                long time = Math.max(0L, gc.getCollectionTime());
                String name = gc.getName();
                if (name.contains("Pauses")) {
                    pauses += count;
                    pauseMs += time;
                } else {
                    cycles += count;
                    cycleMs += time;
                    if (name.contains("Major") || name.contains("Old")) {
                        major += count;
                    }
                }
            }
            if (lastCycles >= 0) {
                set("gcCycles", cycles - lastCycles);
                set("gcMajorCycles", major - lastMajor);
                set("gcPauses", pauses - lastPauses);
                set("gcPauseMs", pauseMs - lastPauseMs);
                set("gcCycleMs", cycleMs - lastCycleMs);
            }
            lastCycles = cycles;
            lastMajor = major;
            lastPauses = pauses;
            lastPauseMs = pauseMs;
            lastCycleMs = cycleMs;

            if (ManagementFactory.getOperatingSystemMXBean() instanceof com.sun.management.OperatingSystemMXBean os) {
                set("processCommitMB", os.getCommittedVirtualMemorySize() / MB);
                set("ramTotalMB", os.getTotalMemorySize() / MB);
                set("ramFreeMB", os.getFreeMemorySize() / MB);
                set("swapTotalMB", os.getTotalSwapSpaceSize() / MB);
                set("swapFreeMB", os.getFreeSwapSpaceSize() / MB);
                set("cpuProcessPct", Math.max(0.0, os.getProcessCpuLoad()) * 100.0);
                set("cpuSystemPct", Math.max(0.0, os.getCpuLoad()) * 100.0);
            }

            set("threads", ManagementFactory.getThreadMXBean().getThreadCount());
            set("uptimeS", ManagementFactory.getRuntimeMXBean().getUptime() / 1000.0);
            set("textureMB", TextureID.totalMemUsed / MB);
            set("textureIds", Texture.totalTextureID);
            published = values.clone();
        } catch (Throwable t) {
            available = false;
            failure = t.getClass().getSimpleName() + ": " + t.getMessage();
        }
    }

    private static String buildEnvironment() {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("java=").append(System.getProperty("java.version"))
                .append(" vm=").append(System.getProperty("java.vm.name"))
                .append(" os=").append(System.getProperty("os.name")).append(' ').append(System.getProperty("os.version"))
                .append(' ').append(System.getProperty("os.arch"))
                .append(" cores=").append(Runtime.getRuntime().availableProcessors())
                .append(" heapMaxMB=").append(Math.round(Runtime.getRuntime().maxMemory() / MB));
            if (ManagementFactory.getOperatingSystemMXBean() instanceof com.sun.management.OperatingSystemMXBean os) {
                sb.append(" ramMB=").append(Math.round(os.getTotalMemorySize() / MB));
            }
            sb.append(" gc=");
            for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
                sb.append(gc.getName().replace(' ', '_')).append(',');
            }
            sb.append(" args=");
            for (String arg : ManagementFactory.getRuntimeMXBean().getInputArguments()) {
                if (arg.startsWith("-Xm") || arg.startsWith("-Xss") || arg.startsWith("-XX:")) {
                    sb.append(arg).append(',');
                }
            }
            String card = firstMatch(currentDebugLog(), "GraphicsCard:");
            if (card != null) {
                sb.append(" gpu=").append(card.substring(card.indexOf("GraphicsCard:") + 13).trim().replace(' ', '_'));
            }
            return clean(sb.toString(), 1500);
        } catch (Throwable t) {
            return "environment failed: " + t.getClass().getSimpleName();
        }
    }

    private static String buildCrashEvidence() {
        StringBuilder sb = new StringBuilder();
        try {
            File previous = previousDebugLog();
            if (previous == null) {
                sb.append("prevLog=none");
            } else {
                String tail = readTail(previous);
                sb.append("prevLog ended ").append(new java.util.Date(previous.lastModified()))
                    .append(" oom=").append(count(tail, "OutOfMemoryError"))
                    .append(" heapSpace=").append(count(tail, "Java heap space"))
                    .append(" glOom=").append(count(tail, "GL_OUT_OF_MEMORY"))
                    .append(" accessViolation=").append(count(tail, "EXCEPTION_ACCESS_VIOLATION"))
                    .append(" exceptions=").append(count(tail, "Exception thrown"))
                    .append('\n');
                String[] lines = tail.split("\r?\n");
                int shown = 0;
                for (int i = lines.length - 1; i >= 0 && shown < 4; i--) {
                    if (lines[i].contains("ERROR") || lines[i].contains("OutOfMemory") || lines[i].contains("Exception")) {
                        sb.append("  err: ").append(trim(lines[i])).append('\n');
                        shown++;
                    }
                }
                for (int i = lines.length - 1; i >= 0; i--) {
                    if (!lines[i].isBlank()) {
                        sb.append("  last: ").append(trim(lines[i])).append('\n');
                        break;
                    }
                }
            }

            File hsErr = newestHsErr();
            if (hsErr != null) {
                sb.append("hs_err ").append(new java.util.Date(hsErr.lastModified())).append('\n');
                String head = readHead(hsErr, 8 * 1024);
                int shown = 0;
                for (String line : head.split("\r?\n")) {
                    if (shown >= 10) {
                        break;
                    }
                    if (line.startsWith("# ") && line.trim().length() > 2 && !line.contains("http")) {
                        sb.append("  ").append(trim(line)).append('\n');
                        shown++;
                    }
                }
            }
        } catch (Throwable t) {
            sb.append("evidence failed: ").append(t.getClass().getSimpleName());
        }
        return clean(sb.toString(), 3000);
    }

    private static File currentDebugLog() {
        File[] files = new File(LoggerManager.getLogsDir()).listFiles((dir, name) -> name.endsWith("_DebugLog.txt"));
        return newest(files);
    }

    private static File previousDebugLog() {
        File[] dirs = new File(LoggerManager.getLogsDir()).listFiles((dir, name) -> name.startsWith("logs_"));
        if (dirs == null) {
            return null;
        }
        java.util.Arrays.sort(dirs, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        // The game leaves empty logs_ folders behind on some launches, so the newest one can hold nothing.
        for (File dir : dirs) {
            File log = newest(dir.listFiles((d, name) -> name.endsWith("_DebugLog.txt")));
            if (log != null) {
                return log;
            }
        }
        return null;
    }

    private static File newestHsErr() {
        File[] files = new File(System.getProperty("user.dir")).listFiles((dir, name) -> name.startsWith("hs_err_pid") && name.endsWith(".log"));
        return newest(files);
    }

    private static File newest(File[] files) {
        if (files == null) {
            return null;
        }
        File best = null;
        for (File f : files) {
            if (best == null || f.lastModified() > best.lastModified()) {
                best = f;
            }
        }
        return best;
    }

    private static String readTail(File file) throws Exception {
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            long start = Math.max(0L, raf.length() - TAIL_BYTES);
            byte[] buf = new byte[(int)(raf.length() - start)];
            raf.seek(start);
            raf.readFully(buf);
            return new String(buf, StandardCharsets.UTF_8);
        }
    }

    private static String readHead(File file, int bytes) throws Exception {
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            byte[] buf = new byte[(int)Math.min(bytes, raf.length())];
            raf.readFully(buf);
            return new String(buf, StandardCharsets.UTF_8);
        }
    }

    private static String firstMatch(File file, String needle) throws Exception {
        if (file == null) {
            return null;
        }
        for (String line : readHead(file, TAIL_BYTES).split("\r?\n")) {
            if (line.contains(needle)) {
                return line;
            }
        }
        return null;
    }

    private static int count(String text, String needle) {
        int n = 0;
        for (int i = text.indexOf(needle); i >= 0; i = text.indexOf(needle, i + needle.length())) {
            n++;
        }
        return n;
    }

    private static String trim(String line) {
        String s = line.trim();
        return s.length() > LINE_MAX ? s.substring(0, LINE_MAX) + "..." : s;
    }

    private static String clean(String text, int max) {
        String s = WIN_PATH.matcher(text).replaceAll("<path>");
        s = NIX_PATH.matcher(s).replaceAll("<path>");
        String user = System.getProperty("user.name");
        if (user != null && user.length() > 2) {
            s = s.replace(user, "<user>");
        }
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }
}
