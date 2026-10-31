package zombie.plz;

import fmod.javafmod;
import java.io.File;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;
import zombie.ZomboidFileSystem;
import zombie.audio.GameSoundClip;
import zombie.debug.DebugLog;

public final class PLZMusicStream {
    private static final long FMOD_LOOP_NORMAL = 0x2L;
    private static final long FMOD_3D = 0x10L;
    private static final long FMOD_CREATESTREAM = 0x80L;

    // Short clips replay often, so a disk open per play would cost more than the PCM they keep.
    private static final long MIN_STREAM_BYTES = 1024L * 1024L;

    private static final long ORPHAN_GRACE_MS = 10000L;

    private static final String NOT_STREAMED = "";

    private static final WeakHashMap<GameSoundClip, String> PATH_BY_CLIP = new WeakHashMap<>();
    private static final HashMap<Long, Open> OPEN = new HashMap<>();

    private static int opened;
    private static int failed;
    private static int orphansReleased;

    private PLZMusicStream() {
    }

    private static final class Open {
        long channel;
        final long sinceMs;

        Open(long sinceMs) {
            this.sinceMs = sinceMs;
        }
    }

    public static synchronized String streamPath(GameSoundClip clip) {
        // Uploads must stream whatever the kill switch says: the engine refuses to load a file from outside a mod.
        if (clip != null && PLZUploadSound.isMarker(clip.file)) {
            return PLZUploadSound.pathFor(clip);
        }

        if (clip == null || clip.file == null || clip.gameSound == null || !PLZFixes.on(PLZFixes.SOUND_MUSIC_STREAM)) {
            return null;
        }

        String path = PATH_BY_CLIP.get(clip);
        if (path == null) {
            path = decide(clip);
            PATH_BY_CLIP.put(clip, path);
        }

        return path == NOT_STREAMED ? null : path;
    }

    private static String decide(GameSoundClip clip) {
        try {
            if (!PLZSoundPriority.isMusic(clip.gameSound)) {
                return NOT_STREAMED;
            }

            String path = ZomboidFileSystem.instance.getAbsolutePath(clip.file);
            if (path == null || new File(path).length() < MIN_STREAM_BYTES) {
                return NOT_STREAMED;
            }

            return path;
        } catch (Throwable var2) {
            return NOT_STREAMED;
        }
    }

    // One stream handle plays on one channel only; a second play steals the first, so each play opens its own.
    public static synchronized long open(String path, boolean looped) {
        long now = System.currentTimeMillis();
        sweep(now);
        long mode = FMOD_3D | FMOD_CREATESTREAM | (looped ? FMOD_LOOP_NORMAL : 0L);
        long sound = javafmod.FMOD_System_CreateSound(path, mode);
        if (sound == 0L) {
            if (failed++ == 0) {
                DebugLog.log("PLZMusicStream: FMOD refused a stream, falling back to a decoded sample: " + path);
            }

            return 0L;
        }

        OPEN.put(sound, new Open(now));
        opened++;
        PLZFixes.hit(PLZFixes.SOUND_MUSIC_STREAM);
        return sound;
    }

    public static synchronized void attach(long sound, long channel) {
        Open entry = OPEN.get(sound);
        if (entry != null) {
            entry.channel = channel;
        }
    }

    public static synchronized void release(long sound) {
        if (sound != 0L && OPEN.remove(sound) != null) {
            javafmod.FMOD_Sound_Release(sound);
        }
    }

    // An emitter dropped without a stop would hold its stream forever; its channel has finished by then.
    private static void sweep(long now) {
        Iterator<Map.Entry<Long, Open>> it = OPEN.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Long, Open> entry = it.next();
            Open open = entry.getValue();
            if (now - open.sinceMs < ORPHAN_GRACE_MS) {
                continue;
            }

            if (open.channel == 0L || !javafmod.FMOD_Channel_IsPlaying(open.channel)) {
                it.remove();
                javafmod.FMOD_Sound_Release(entry.getKey());
                orphansReleased++;
            }
        }
    }

    public static synchronized int getOpen() {
        return OPEN.size();
    }

    public static synchronized int getOpened() {
        return opened;
    }

    public static synchronized int getFailed() {
        return failed;
    }

    public static synchronized int getOrphansReleased() {
        return orphansReleased;
    }
}
