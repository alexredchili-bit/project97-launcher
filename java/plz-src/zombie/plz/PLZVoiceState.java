package zombie.plz;

import java.util.ArrayList;
import java.util.HashMap;
import se.krka.kahlua.vm.KahluaTable;
import zombie.characters.IsoPlayer;
import zombie.core.raknet.VoiceManagerData;
import zombie.debug.DebugType;
import zombie.input.GameKeyboard;
import zombie.vehicles.BaseVehicle;

public final class PLZVoiceState {
    // A voice channel sits at priority 0, so every idle one held open takes a real FMOD voice from game sound.
    public static final long VOICE_IDLE_CLOSE_MS = 5000L;
    public static final String MEGAPHONE_STATIC = "RadioStatic";

    public static volatile boolean privateCall;
    public static volatile String radioPttBinding;
    public static volatile boolean stationReceiversDirty;
    public static volatile long stationRepublishMs;

    // Written on the voice thread and read from Lua on the main one, hence the locks.
    public static final ArrayList<Short> RADIO_ONLY_PEERS = new ArrayList<>();
    public static final HashMap<Short, Integer> FRAME_COUNTS = new HashMap<>();
    public static final HashMap<Short, Long> LAST_FRAME_MS = new HashMap<>();
    // The megaphone hiss handle per speaker on the listener's machine, so it can be stopped when the voice stops.
    public static final HashMap<Short, Long> MEGAPHONE_LOOPS = new HashMap<>();

    private static long muteLogMs;

    private PLZVoiceState() {
    }

    // Two people in one car are in earshot whatever their independently-timed routing entries say.
    public static boolean sharesVehicle(IsoPlayer me, IsoPlayer them) {
        if (me == null || them == null) {
            return false;
        }
        BaseVehicle mine = me.getVehicle();
        return mine != null && mine == them.getVehicle();
    }

    public static int speakerMode(VoiceManagerData speaker, float maxDistance) {
        synchronized (speaker.radioData) {
            if (speaker.radioData.isEmpty()) {
                return PLZVoice.MODE_NORMAL;
            }
            return PLZVoice.bucketMode(speaker.radioData.get(0).distance, maxDistance);
        }
    }

    // isPlaying is asked first because playSoundImpl would start a second copy every frame otherwise.
    public static void startMegaphoneStatic(IsoPlayer speaker) {
        if (speaker == null) {
            return;
        }

        Short id = speaker.getOnlineID();
        if (MEGAPHONE_LOOPS.containsKey(id)) {
            return;
        }
        if (speaker.getEmitter() == null || speaker.getEmitter().isPlaying(MEGAPHONE_STATIC)) {
            return;
        }

        // playSoundImpl, not playSound: a networked hiss would have every listener broadcasting it to the others.
        long handle = speaker.getEmitter().playSoundImpl(MEGAPHONE_STATIC, null);
        if (handle > 0L) {
            MEGAPHONE_LOOPS.put(id, handle);
        }
    }

    // Called for everybody: stopping something this machine never started is free, and it is what ends a hiss.
    public static void stopMegaphoneStatic(IsoPlayer speaker) {
        if (speaker == null) {
            return;
        }

        Long handle = MEGAPHONE_LOOPS.remove(speaker.getOnlineID());
        if (handle == null) {
            return;
        }
        if (speaker.getEmitter() != null) {
            speaker.getEmitter().stopOrTriggerSound(handle);
        }
    }

    public static boolean isRadioPttDown() {
        String binding = radioPttBinding;
        if (binding == null || binding.isEmpty()) {
            return false;
        }
        return GameKeyboard.isKeyDown(binding);
    }

    public static boolean configBool(KahluaTable cfg, String key, boolean fallback) {
        Object value = cfg.rawget(key);
        return value instanceof Boolean b ? b : fallback;
    }

    public static float configFloat(KahluaTable cfg, String key, float fallback) {
        Object value = cfg.rawget(key);
        return value instanceof Double d ? d.floatValue() : fallback;
    }

    public static void countFrame(short onlineId) {
        synchronized (FRAME_COUNTS) {
            Integer seen = FRAME_COUNTS.get(onlineId);
            FRAME_COUNTS.put(onlineId, seen == null ? 1 : seen + 1);
            LAST_FRAME_MS.put(onlineId, System.currentTimeMillis());
        }
    }

    public static boolean voiceIdle(short onlineId) {
        synchronized (FRAME_COUNTS) {
            Long last = LAST_FRAME_MS.get(onlineId);
            return last == null || System.currentTimeMillis() - last >= VOICE_IDLE_CLOSE_MS;
        }
    }

    // Names WHICH test killed the frame, rather than leaving it to be inferred backwards from other logs.
    public static void logMute(IsoPlayer me, IsoPlayer player, VoiceManagerData speaker, float range) {
        long now = System.currentTimeMillis();
        if (now < muteLogMs) {
            return;
        }

        muteLogMs = now + 3000L;

        String why;
        int myFreq = PLZVoice.entryChannel(me.getZi());
        int theirFreq = Integer.MIN_VALUE;
        float theirRange = -1.0F;

        synchronized (speaker.radioData) {
            if (speaker.radioData.isEmpty()) {
                why = "speaker has published no routing entry yet";
            } else {
                theirFreq = speaker.radioData.get(0).freq;
                theirRange = speaker.radioData.get(0).distance;
                if (!(theirRange > 0.0F)) {
                    why = "speaker publishes range 0 (private call, or no range)";
                } else if (PLZVoice.isFloorsEnabled() && myFreq != theirFreq) {
                    why = "floor mismatch";
                } else {
                    why = "routing distance past the gate";
                }
            }
        }

        DebugType.Multiplayer
            .warn(
                String.format(
                    "PLZ voice MUTED \"%s\" -> \"%s\": %s (live range=%.1f, myFreq=%d, theirFreq=%d, theirRange=%.1f)",
                    player.getUsername(), me.getUsername(), why, range, myFreq, theirFreq, theirRange
                )
            );
    }
}
