package zombie.plz;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.ConcurrentHashMap;
import zombie.GameSounds;
import zombie.ZomboidFileSystem;
import zombie.audio.GameSound;
import zombie.audio.GameSoundClip;
import zombie.debug.DebugLog;
import zombie.network.GameServer;

/** Uploaded songs as New Music tracks. See .claude/PLZ-UPLOAD-MEDIA.md in the RPMods repo. */
public final class PLZUploadSound {
    public static final String SOUND_PREFIX = "PLZUp_";
    public static final String MARKER = "plzupload:";
    static final int SILENCE_RATE = 2000;
    static final long MAX_SILENCE_MS = 15L * 60L * 1000L;
    private static final float DISTANCE_MAX = 500.0F;

    private static final ConcurrentHashMap<String, Entry> ENTRIES = new ConcurrentHashMap<>();
    private static int registered;
    private static int silenced;

    private PLZUploadSound() {
    }

    static final class Entry {
        final long durationMs;
        final boolean revoked;

        Entry(long durationMs, boolean revoked) {
            this.durationMs = durationMs;
            this.revoked = revoked;
        }
    }

    public static String soundName(String sha) {
        return PLZStation.isSha(sha) ? SOUND_PREFIX + sha : null;
    }

    public static boolean isMarker(String file) {
        return file != null && file.startsWith(MARKER);
    }

    public static String shaOfFile(String file) {
        if (!isMarker(file)) {
            return null;
        }

        String sha = file.substring(MARKER.length());
        return PLZStation.isSha(sha) ? sha : null;
    }

    /** Lua-facing through VideoTexture. Idempotent; a later call updates the length or the revoke. */
    public static boolean register(String sha, double durationMs, boolean revoked) {
        if (!PLZStation.isSha(sha)) {
            return false;
        }

        long ms = Double.isNaN(durationMs) || durationMs < 0.0 ? 0L : (long)Math.min(durationMs, (double)MAX_SILENCE_MS);
        ENTRIES.put(sha, new Entry(ms, revoked));
        if (!GameServer.server) {
            ensureSound(sha);
        }

        return true;
    }

    public static boolean isRegistered(String sha) {
        return sha != null && ENTRIES.containsKey(sha);
    }

    private static void ensureSound(String sha) {
        String name = SOUND_PREFIX + sha;
        if (GameSounds.isKnownSound(name)) {
            GameSound known = GameSounds.getSound(name);
            if (known != null && !known.clips.isEmpty() && isMarker(known.clips.get(0).file)) {
                return;
            }
        }

        GameSound sound = new GameSound();
        sound.name = name;
        sound.category = "Music";
        sound.master = GameSound.MasterVolume.Music;
        GameSoundClip clip = new GameSoundClip(sound);
        clip.file = MARKER + sha;
        clip.distanceMax = DISTANCE_MAX;
        clip.initFlags = (short)(clip.initFlags | GameSoundClip.INIT_FLAG_DISTANCE_MAX);
        int priority = PLZSoundPriority.musicPriority();
        if (priority > clip.priority) {
            clip.priority = priority;
        }

        sound.clips.add(clip);
        GameSounds.addSound(sound);
        registered++;
    }

    /** What FMOD opens for an upload clip: the mirrored file, or silence of the song's length. */
    public static String pathFor(GameSoundClip clip) {
        String sha = clip == null ? null : shaOfFile(clip.file);
        if (sha == null) {
            return null;
        }

        String upload = PLZStation.uploadPath(sha);
        String cache = ZomboidFileSystem.instance.getCacheDir();
        File silenceDir = new File(cache + File.separator + "Lua" + File.separator + "PLZRadio" + File.separator + "silence");
        return resolve(ENTRIES.get(sha), upload == null ? null : new File(upload), silenceDir);
    }

    static String resolve(Entry entry, File upload, File silenceDir) {
        boolean revoked = entry != null && entry.revoked;
        if (!revoked && upload != null && upload.isFile()) {
            return upload.getAbsolutePath();
        }

        // A missing or withdrawn song still runs its full length, so the device advances on time for every listener.
        long ms = entry == null ? 0L : entry.durationMs;
        if (ms <= 0L) {
            return null;
        }

        File silence = silenceFile(silenceDir, ms);
        if (silence == null) {
            return null;
        }

        silenced++;
        return silence.getAbsolutePath();
    }

    static File silenceFile(File dir, long durationMs) {
        long seconds = Math.max(1L, Math.min(MAX_SILENCE_MS, durationMs + 999L) / 1000L);
        File file = new File(dir, "silence_" + seconds + ".wav");
        if (file.isFile() && file.length() == 44L + seconds * SILENCE_RATE) {
            return file;
        }

        try {
            dir.mkdirs();
            File tmp = new File(dir, file.getName() + ".tmp");
            try (OutputStream out = new FileOutputStream(tmp)) {
                writeSilence(out, seconds * SILENCE_RATE);
            }

            if (file.exists() && !file.delete()) {
                return null;
            }

            return tmp.renameTo(file) ? file : null;
        } catch (IOException var7) {
            DebugLog.log("PLZUploadSound: cannot write silence " + file + ": " + var7.getMessage());
            return null;
        }
    }

    static void writeSilence(OutputStream out, long samples) throws IOException {
        byte[] header = new byte[44];
        putAscii(header, 0, "RIFF");
        putInt(header, 4, (int)(36L + samples));
        putAscii(header, 8, "WAVE");
        putAscii(header, 12, "fmt ");
        putInt(header, 16, 16);
        putShort(header, 20, 1);
        putShort(header, 22, 1);
        putInt(header, 24, SILENCE_RATE);
        putInt(header, 28, SILENCE_RATE);
        putShort(header, 32, 1);
        putShort(header, 34, 8);
        putAscii(header, 36, "data");
        putInt(header, 40, (int)samples);
        out.write(header);
        byte[] block = new byte[4096];
        java.util.Arrays.fill(block, (byte)0x80);
        long left = samples;
        while (left > 0L) {
            int n = (int)Math.min(left, (long)block.length);
            out.write(block, 0, n);
            left -= n;
        }
    }

    private static void putAscii(byte[] b, int at, String s) {
        for (int i = 0; i < s.length(); i++) {
            b[at + i] = (byte)s.charAt(i);
        }
    }

    private static void putInt(byte[] b, int at, int v) {
        b[at] = (byte)v;
        b[at + 1] = (byte)(v >> 8);
        b[at + 2] = (byte)(v >> 16);
        b[at + 3] = (byte)(v >> 24);
    }

    private static void putShort(byte[] b, int at, int v) {
        b[at] = (byte)v;
        b[at + 1] = (byte)(v >> 8);
    }

    public static int getRegistered() {
        return registered;
    }

    public static int getSilenced() {
        return silenced;
    }
}
