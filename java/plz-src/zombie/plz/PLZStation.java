package zombie.plz;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import se.krka.kahlua.vm.KahluaTable;
import zombie.ZomboidFileSystem;
import zombie.audio.GameSoundClip;
import zombie.characters.IsoPlayer;
import zombie.inventory.types.Radio;
import zombie.iso.IsoObject;
import zombie.radio.ZomboidRadio;
import zombie.radio.devices.DeviceData;
import zombie.radio.devices.WaveSignalDevice;
import zombie.scripting.ScriptManager;
import zombie.scripting.objects.GameSoundScript;
import zombie.vehicles.BaseVehicle;
import zombie.vehicles.VehiclePart;

/** Player-run radio stations. See .claude/PLZ-RADIO-STATION.md in the RPMods repo. */
public final class PLZStation {
    public static final float TRANSMIT_RANGE = 32000.0F;
    public static final int KIND_WORLD = 0;
    public static final int KIND_VEHICLE = 1;
    public static final int KIND_HELD = 2;
    private static final int MAX_SCAN_ROWS = 8;

    private static volatile int[] frequencies = new int[0];

    private PLZStation() {
    }

    public static int[] parseInts(String csv) {
        if (csv == null || csv.isBlank()) {
            return new int[0];
        }

        String[] parts = csv.split(",");
        int[] out = new int[parts.length];
        int n = 0;
        for (String part : parts) {
            try {
                out[n++] = Integer.parseInt(part.trim());
            } catch (NumberFormatException e) {
            }
        }

        out = Arrays.copyOf(out, n);
        Arrays.sort(out);
        return out;
    }

    public static void setFrequencies(String csv) {
        frequencies = parseInts(csv);
    }

    public static boolean hasStations() {
        return frequencies.length > 0;
    }

    public static boolean isStation(int freq) {
        int[] set = frequencies;
        return set.length > 0 && Arrays.binarySearch(set, freq) >= 0;
    }

    public static String frequencyList() {
        return Arrays.toString(frequencies);
    }

    // ------------------------------------------------------------------ client: listening and transmitting

    private static final Map<Integer, short[]> onAir = new ConcurrentHashMap<>();
    private static volatile int transmitFreq;
    private static final Map<Integer, Long> lastVoiceMs = new ConcurrentHashMap<>();

    public static final class Receiver {
        public DeviceData device;
        public int freq;
        public float x;
        public float y;
        public float z;
        public float gain;
        public float range = 30.0F;
        public int level = LEVEL_FULL;
        public boolean flat;
    }

    public static final int LEVEL_FRINGE = 1;
    public static final int LEVEL_FULL = 2;
    public static final float FRINGE_STATIC = 0.25F;

    private static volatile Receiver[] receivers = new Receiver[0];

    public static void setOnAir(int freq, String csvOnlineIds) {
        int[] ids = parseInts(csvOnlineIds);
        if (ids.length == 0) {
            onAir.remove(freq);
            return;
        }

        short[] out = new short[ids.length];
        for (int i = 0; i < ids.length; i++) {
            out[i] = (short)ids[i];
        }

        onAir.put(freq, out);
    }

    public static boolean isOnAir(int freq, short onlineId) {
        short[] ids = onAir.get(freq);
        if (ids == null) {
            return false;
        }

        for (short id : ids) {
            if (id == onlineId) {
                return true;
            }
        }

        return false;
    }

    public static void setTransmit(int freq) {
        transmitFreq = isStation(freq) ? freq : 0;
    }

    public static int transmitFreq() {
        return transmitFreq;
    }

    /** True when the set of receiving devices changed, so the routing table is worth republishing. */
    public static boolean setReceivers(List<Receiver> list) {
        Receiver[] next = list.toArray(new Receiver[0]);
        Receiver[] prev = receivers;
        receivers = next;
        if (prev.length != next.length) {
            return true;
        }

        for (int i = 0; i < next.length; i++) {
            if (prev[i].device != next[i].device || prev[i].freq != next[i].freq) {
                return true;
            }
        }

        return false;
    }

    public static Receiver[] receivers() {
        return receivers;
    }

    public static Receiver receiverFor(DeviceData device) {
        if (device == null) {
            return null;
        }

        for (Receiver r : receivers) {
            if (r.device == device && device.getChannel() == r.freq) {
                return r;
            }
        }

        return null;
    }

    public static void noteVoice(int freq) {
        lastVoiceMs.put(freq, System.currentTimeMillis());
    }

    public static double voiceAgeMs(int freq) {
        Long at = lastVoiceMs.get(freq);
        return at == null ? -1.0 : (double)(System.currentTimeMillis() - at);
    }

    /** A 2D voice falls off by hand over the same range the music uses, so it does not need a 3D channel. */
    public static float voiceFalloff(Receiver r, float listenerX, float listenerY) {
        if (r.flat) {
            return 1.0F;
        }

        float maxTiles = Math.max(2.0F, r.range);
        float dx = r.x - listenerX;
        float dy = r.y - listenerY;
        float distance = (float)Math.sqrt(dx * dx + dy * dy);
        float near = Math.max(1.0F, maxTiles * 0.15F);
        if (distance <= near) {
            return 1.0F;
        }

        if (distance >= maxTiles) {
            return 0.0F;
        }

        float t = 1.0F - (distance - near) / (maxTiles - near);
        return t * t;
    }

    private static boolean playingTape(VehiclePart part) {
        if (!part.hasModData()) {
            return false;
        }

        Object state = part.getModData().rawget("nm_device_state");
        return state instanceof KahluaTable table && Boolean.TRUE.equals(table.rawget("isPlaying"));
    }

    /** Turned-on non-TV devices tuned to a station within range of the listener, nearest first. */
    public static List<Object[]> scan(IsoPlayer player, float range) {
        List<Object[]> rows = new ArrayList<>();
        if (player == null || frequencies.length == 0) {
            return rows;
        }

        float px = player.getX();
        float py = player.getY();
        float rangeSq = range * range;
        BaseVehicle seat = player.getVehicle();

        Radio held = player.getEquipedRadio();
        if (held != null) {
            DeviceData dd = held.getDeviceData();
            if (dd != null && dd.getIsTurnedOn() && isStation(dd.getChannel()) && dd.getDeviceVolume() > 0.0F) {
                rows.add(new Object[]{dd, KIND_HELD, px, py, player.getZ(), 0.0F, true});
            }
        }

        ArrayList<WaveSignalDevice> devices = ZomboidRadio.getInstance().getDevices();
        for (int i = 0; i < devices.size(); i++) {
            WaveSignalDevice wsd = devices.get(i);
            DeviceData dd = wsd == null ? null : wsd.getDeviceData();
            if (dd == null || !dd.getIsTurnedOn() || dd.isTelevision() || !isStation(dd.getChannel()) || dd.getDeviceVolume() <= 0.0F) {
                continue;
            }

            IsoObject source = dd.getIsoObject();
            if (source == null) {
                continue;
            }

            boolean vehicle = wsd instanceof VehiclePart;
            if (vehicle && playingTape((VehiclePart)wsd)) {
                continue;
            }

            float x = source.getX() + (vehicle ? 0.0F : 0.5F);
            float y = source.getY() + (vehicle ? 0.0F : 0.5F);
            float dx = x - px;
            float dy = y - py;
            float dsq = dx * dx + dy * dy;
            if (dsq > rangeSq) {
                continue;
            }

            boolean flat = vehicle && seat != null && seat == ((VehiclePart)wsd).getVehicle();
            rows.add(new Object[]{dd, vehicle ? KIND_VEHICLE : KIND_WORLD, x, y, source.getZ(), (float)Math.sqrt(dsq), flat});
        }

        rows.sort((a, b) -> Float.compare((Float)a[5], (Float)b[5]));
        return rows.size() > MAX_SCAN_ROWS ? new ArrayList<>(rows.subList(0, MAX_SCAN_ROWS)) : rows;
    }

    // ------------------------------------------------------------------ both: the file behind a sound name

    private static volatile Map<String, String> clipFiles;

    private static Map<String, String> clipFiles() {
        Map<String, String> map = clipFiles;
        if (map != null) {
            return map;
        }

        map = new HashMap<>();
        for (GameSoundScript script : ScriptManager.instance.getAllGameSounds()) {
            String file = null;
            for (GameSoundClip clip : script.gameSound.clips) {
                if (clip.file != null && !clip.file.isEmpty()) {
                    file = clip.file;
                    break;
                }
            }

            if (file != null) {
                map.put(script.gameSound.name, file);
                if (script.getModule() != null) {
                    map.put(script.getModule().getName() + "." + script.gameSound.name, file);
                }
            }
        }

        clipFiles = map;
        return map;
    }

    public static void forgetClips() {
        clipFiles = null;
    }

    /** Absolute path of a sound script's first file clip, or null. Never takes a path from Lua. */
    public static String clipPath(String soundName) {
        if (soundName == null || soundName.isEmpty()) {
            return null;
        }

        String rel = clipFiles().get(soundName);
        if (rel == null) {
            return null;
        }

        String abs = ZomboidFileSystem.instance.getAbsolutePath(rel);
        if (abs == null) {
            abs = ZomboidFileSystem.instance.getString(rel);
        }

        return abs != null && new File(abs).isFile() ? abs : null;
    }

    private static final Map<String, Long> lengths = new ConcurrentHashMap<>();

    /** Track length in ms read from the file, -1 when unknown. Cached per sound name. */
    public static long trackMs(String soundName) {
        if (soundName == null) {
            return -1L;
        }

        Long hit = lengths.get(soundName);
        if (hit != null) {
            return hit;
        }

        String path = clipPath(soundName);
        long ms = path == null ? -1L : PLZOggLength.lengthMs(new File(path));
        lengths.put(soundName, ms);
        return ms;
    }

    public static boolean isSha(String sha) {
        if (sha == null || sha.length() != 64) {
            return false;
        }

        for (int i = 0; i < 64; i++) {
            char c = sha.charAt(i);
            if (!(c >= '0' && c <= '9' || c >= 'a' && c <= 'f')) {
                return false;
            }
        }

        return true;
    }

    /** Where the launcher mirrors approved uploads. */
    public static String uploadPath(String sha) {
        if (!isSha(sha)) {
            return null;
        }

        return ZomboidFileSystem.instance.getCacheDir() + File.separator + "Lua" + File.separator + "PLZRadio"
            + File.separator + "tracks" + File.separator + "plzradio_" + sha + ".ogg";
    }
}
