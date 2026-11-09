package zombie.plz;

import fmod.javafmod;
import fmod.javafmodJNI;
import java.io.File;
import java.util.HashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import zombie.ZomboidFileSystem;
import zombie.core.PerformanceSettings;
import zombie.core.SpriteRenderer;
import zombie.core.fonts.AngelCodeFont;
import zombie.core.math.PZMath;
import zombie.core.textures.Texture;
import zombie.core.textures.VideoTexture;
import zombie.iso.IsoCamera;
import zombie.iso.IsoCell;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoWorld;
import zombie.iso.fboRenderChunk.FBORenderCutaways;
import zombie.ui.TextManager;
import zombie.ui.UIFont;

public final class PLZCinema {
    private PLZCinema() {
    }

    public static String cinemaCaptureStats() {
        PLZLiveVideo.Capture c = PLZLiveVideo.current();
        if (c == null) {
            return "off";
        }
        return String.format(
            "size=%dx%d frames=%d dropped=%d gpuMs=%.3f linked=%b audio=%d",
            c.width, c.height, c.frames, c.dropped, c.gpuMsAvg, PLZLiveLink.connected(), PLZLiveLink.sentAudio
        );
    }

    public static boolean cinemaMediaExists(String file) {
        return new File(ZomboidFileSystem.instance.getMediaPath("videos/" + file)).isFile();
    }

    private static final float[][] CAPTION_OUTLINE = {{-1.0F, 0.0F}, {1.0F, 0.0F}, {0.0F, -1.0F}, {0.0F, 1.0F}};

    public static void cinemaDrawCaption(UIFont uiFont, String text, double cx, double cy, double angle, double alpha) {
        AngelCodeFont font = TextManager.instance.getFontFromEnum(uiFont);
        if (font == null || text == null || text.isEmpty()) {
            return;
        }

        String[] lines = text.split("\n");
        float lineH = font.getLineHeight();
        float ux = (float)Math.cos(angle);
        float uy = (float)Math.sin(angle);
        float vx = -uy;
        float vy = ux;
        float ox = (float)cx;
        float oy = (float)cy;
        float a = (float)alpha;
        for (float[] shift : CAPTION_OUTLINE) {
            drawCaptionGlyphs(font, lines, lineH, ox + shift[0], oy + shift[1], ux, uy, vx, vy, 0.0F, a);
        }

        drawCaptionGlyphs(font, lines, lineH, ox, oy, ux, uy, vx, vy, 1.0F, a);
    }

    private static void drawCaptionGlyphs(
        AngelCodeFont font, String[] lines, float lineH, float ox, float oy, float ux, float uy, float vx, float vy, float shade, float a
    ) {
        for (int n = 0; n < lines.length; n++) {
            String line = lines[n];
            float pen = -font.getWidth(line) / 2.0F;
            float lineTop = n * lineH;
            AngelCodeFont.CharDef last = null;
            for (int i = 0; i < line.length(); i++) {
                int id = line.charAt(i);
                if (id >= font.chars.length) {
                    id = 63;
                }

                AngelCodeFont.CharDef def = font.chars[id];
                if (def == null) {
                    continue;
                }

                if (last != null) {
                    pen += last.getKerning(id);
                }

                last = def;
                Texture tex = def.image;
                if (tex != null && def.width > 0 && def.height > 0) {
                    float x0 = pen + def.xoffset;
                    float y0 = lineTop + def.yoffset;
                    float x1 = x0 + def.width;
                    float y1 = y0 + def.height;
                    SpriteRenderer.instance.renderPoly(
                        tex,
                        ox + ux * x0 + vx * y0, oy + uy * x0 + vy * y0,
                        ox + ux * x1 + vx * y0, oy + uy * x1 + vy * y0,
                        ox + ux * x1 + vx * y1, oy + uy * x1 + vy * y1,
                        ox + ux * x0 + vx * y1, oy + uy * x0 + vy * y1,
                        shade, shade, shade, a,
                        tex.getXStart(), tex.getYStart(),
                        tex.getXEnd(), tex.getYStart(),
                        tex.getXEnd(), tex.getYEnd(),
                        tex.getXStart(), tex.getYEnd()
                    );
                }

                pen += def.xadvance;
            }
        }
    }

    private static final HashMap<String, PLZVideoTextureAudioSlot> audioSlots = new HashMap<>();

    public static int cinemaAudioLoad(String slot, String file, double mode) {
        cinemaAudioRelease(slot);
        PLZVideoTextureAudioSlot s = new PLZVideoTextureAudioSlot();
        s.sound = javafmod.FMOD_System_CreateSound(ZomboidFileSystem.instance.getMediaPath("videos/" + file), (long)mode);
        if (s.sound == 0L) {
            return -1;
        }

        audioSlots.put(slot, s);
        return 0;
    }

    private static int cinemaAudioLoadPath(String slot, String path, double mode) {
        cinemaAudioRelease(slot);
        if (path == null || !new File(path).isFile()) {
            return -3;
        }

        PLZVideoTextureAudioSlot s = new PLZVideoTextureAudioSlot();
        s.sound = javafmod.FMOD_System_CreateSound(path, (long)mode);
        if (s.sound == 0L) {
            return -1;
        }

        audioSlots.put(slot, s);
        return 0;
    }

    public static int cinemaAudioLoadSound(String slot, String soundName, double mode) {
        return cinemaAudioLoadPath(slot, zombie.plz.PLZStation.clipPath(soundName), mode);
    }

    public static int cinemaAudioLoadUpload(String slot, String sha, double mode) {
        return cinemaAudioLoadPath(slot, zombie.plz.PLZStation.uploadPath(sha), mode);
    }

    public static boolean cinemaUploadExists(String sha) {
        String path = zombie.plz.PLZStation.uploadPath(sha);
        return path != null && new File(path).isFile();
    }

    public static double cinemaAudioLengthMs(String slot) {
        PLZVideoTextureAudioSlot s = audioSlots.get(slot);
        return s == null ? -1.0 : (double)javafmod.FMOD_Sound_GetLength(s.sound, 1L);
    }

    public static boolean cinemaAudioIsPlaying(String slot) {
        PLZVideoTextureAudioSlot s = audioSlots.get(slot);
        return s != null && s.channel != 0L && javafmod.FMOD_Channel_IsPlaying(s.channel);
    }

    public static int cinemaAudioPreroll(String slot, double targetMs, double volume) {
        PLZVideoTextureAudioSlot s = audioSlots.get(slot);
        if (s == null) {
            return -1;
        }

        if (s.channel != 0L) {
            javafmod.FMOD_Channel_Stop(s.channel);
        }

        s.channel = javafmod.FMOD_System_PlaySound(s.sound, true);
        if (s.channel == 0L) {
            return -2;
        }

        // default 128 loses every real voice to game sounds (priority 3-4) once 64 are playing
        javafmod.FMOD_Channel_SetPriority(s.channel, 0);
        long t0 = System.nanoTime();
        int result = javafmod.FMOD_Channel_SetPosition(s.channel, (long)targetMs);
        s.seekMicros = (System.nanoTime() - t0) / 1000L;
        javafmod.FMOD_Channel_SetVolume(s.channel, (float)volume);
        return result;
    }

    public static void cinemaAudioUnpause(String slot) {
        PLZVideoTextureAudioSlot s = audioSlots.get(slot);
        if (s != null && s.channel != 0L) {
            javafmod.FMOD_Channel_SetPaused(s.channel, false);
        }
    }

    public static void cinemaAudioVolume(String slot, double volume) {
        PLZVideoTextureAudioSlot s = audioSlots.get(slot);
        if (s != null && s.channel != 0L) {
            javafmod.FMOD_Channel_SetVolume(s.channel, (float)volume);
        }
    }

    public static int cinemaAudioRate(String slot, double rate) {
        PLZVideoTextureAudioSlot s = audioSlots.get(slot);
        if (s == null || s.channel == 0L) {
            return -1;
        }

        return javafmod.FMOD_Channel_SetPitch(s.channel, (float)rate);
    }

    public static int cinemaAudioSeek(String slot, double ms) {
        PLZVideoTextureAudioSlot s = audioSlots.get(slot);
        if (s == null || s.channel == 0L) {
            return -1;
        }

        javafmod.FMOD_Channel_SetPitch(s.channel, 1.0F);
        long t0 = System.nanoTime();
        int result = javafmod.FMOD_Channel_SetPosition(s.channel, (long)ms);
        s.seekMicros = (System.nanoTime() - t0) / 1000L;
        return result;
    }

    public static long cinemaAudioSeekMicros(String slot) {
        PLZVideoTextureAudioSlot s = audioSlots.get(slot);
        return s == null ? -1L : s.seekMicros;
    }

    public static double cinemaAudioPosMs(String slot) {
        PLZVideoTextureAudioSlot s = audioSlots.get(slot);
        return s == null || s.channel == 0L ? -1.0 : (double)javafmod.FMOD_Channel_GetPosition(s.channel, 1);
    }

    public static double cinemaAudioAudibility(String slot) {
        PLZVideoTextureAudioSlot s = audioSlots.get(slot);
        return s == null || s.channel == 0L ? -1.0 : (double)javafmod.FMOD_Channel_GetAudibility(s.channel);
    }

    public static boolean cinemaAudioIsVirtual(String slot) {
        PLZVideoTextureAudioSlot s = audioSlots.get(slot);
        return s != null && s.channel != 0L && javafmod.FMOD_Channel_IsVirtual(s.channel);
    }

    public static void cinemaAudioRelease(String slot) {
        PLZVideoTextureAudioSlot s = audioSlots.remove(slot);
        if (s != null) {
            if (s.channel != 0L) {
                javafmod.FMOD_Channel_Stop(s.channel);
            }

            javafmod.FMOD_Sound_Release(s.sound);
        }
    }

    public static int cinemaAudioPlace(String slot, float sx, float sy, float sz, float minDist, float maxDist, float occlusion, float level) {
        PLZVideoTextureAudioSlot s = audioSlots.get(slot);
        if (s == null || s.channel == 0L) {
            return -1;
        }

        javafmod.FMOD_Channel_Set3DMinMaxDistance(s.channel, minDist, maxDist);
        javafmod.FMOD_Channel_Set3DOcclusion(s.channel, occlusion, occlusion);
        // films are mono, so Set3DSpread is a no-op; the javafmod wrapper wants a SWIG handle, the JNI native takes the raw one
        javafmodJNI.FMOD_Channel_Set3DLevel(s.channel, level);
        // world coords with z * 3: the core listener follows SoundListener's Studio listener
        return javafmod.FMOD_Channel_Set3DAttributes(s.channel, sx, sy, sz * 3.0F, 0.0F, 0.0F, 0.0F);
    }

    static final float LEVEL_UNITS = 2.44949F;
    static final ConcurrentLinkedQueue<PLZVideoTextureScreenDrawer> screenDrawerPool = new ConcurrentLinkedQueue<>();
    private static int cinemaLastHidden;

    public static int cinemaLastHidden() {
        return cinemaLastHidden;
    }

    public static int cinemaDrawScreen(VideoTexture tex, float x, float y, int z, int facing, float width, float lift, float offset, float bias, float border, float frame) {
        IsoCell cell = IsoWorld.instance.currentCell;
        if (cell == null || !PerformanceSettings.fboRenderChunk) {
            return -1;
        }

        int playerIndex = IsoCamera.frameState.playerIndex;
        boolean north = facing == 0;
        int cutBit = north ? 1 : 2;
        float start = north ? x : y;
        int first = PZMath.fastfloor(start + 0.001F);
        int last = (int)Math.ceil(start + width - 0.001F) - 1;
        int line = PZMath.fastfloor(north ? y : x);
        for (int i = first; i <= last; i++) {
            IsoGridSquare square = cell.getGridSquare(north ? i : line, north ? line : i, z);
            if (square == null) {
                cinemaLastHidden = 1;
                return 0;
            }

            if ((square.getPlayerCutawayFlag(playerIndex, 0L) & cutBit) != 0) {
                cinemaLastHidden = 2;
                return 0;
            }

            if (!FBORenderCutaways.getInstance().shouldRenderBuildingSquare(playerIndex, square)) {
                cinemaLastHidden = 3;
                return 0;
            }
        }

        cinemaLastHidden = 0;
        float aspect = tex != null && tex.getWidth() > 0 ? (float)tex.getHeight() / tex.getWidth() : 0.5625F;
        float height = width * aspect / LEVEL_UNITS;
        PLZVideoTextureScreenDrawer drawer = screenDrawerPool.poll();
        if (drawer == null) {
            drawer = new PLZVideoTextureScreenDrawer();
        }

        drawer.tex = tex != null && tex.isValid() ? tex : null;
        drawer.level = z;
        drawer.frame = frame;
        float b = border / LEVEL_UNITS;
        if (north) {
            drawer.setQuad(0, x - border, y + offset * 0.5F, x + width + border, y + offset * 0.5F, z + lift - b, z + lift + height + b, bias);
            drawer.setQuad(1, x, y + offset, x + width, y + offset, z + lift, z + lift + height, bias);
        } else {
            drawer.setQuad(0, x + offset * 0.5F, y + width + border, x + offset * 0.5F, y - border, z + lift - b, z + lift + height + b, bias);
            drawer.setQuad(1, x + offset, y + width, x + offset, y, z + lift, z + lift + height, bias);
        }

        SpriteRenderer.instance.drawGeneric(drawer);
        return 1;
    }

    public static int cinemaDrawFree(VideoTexture tex, float lx, float ly, float rx, float ry, int z, float lift, float offset, float bias, float border, float frame) {
        IsoCell cell = IsoWorld.instance.currentCell;
        if (cell == null || !PerformanceSettings.fboRenderChunk) {
            return -1;
        }

        float width = (float)Math.hypot(rx - lx, ry - ly);
        if (width < 0.01F) {
            return -1;
        }

        int playerIndex = IsoCamera.frameState.playerIndex;
        IsoGridSquare square = cell.getGridSquare(PZMath.fastfloor((lx + rx) / 2.0F), PZMath.fastfloor((ly + ry) / 2.0F), z);
        if (square == null) {
            cinemaLastHidden = 1;
            return 0;
        }

        if (!FBORenderCutaways.getInstance().shouldRenderBuildingSquare(playerIndex, square)) {
            cinemaLastHidden = 3;
            return 0;
        }

        cinemaLastHidden = 0;
        float aspect = tex != null && tex.getWidth() > 0 ? (float)tex.getHeight() / tex.getWidth() : 0.5625F;
        float height = width * aspect / LEVEL_UNITS;
        PLZVideoTextureScreenDrawer drawer = screenDrawerPool.poll();
        if (drawer == null) {
            drawer = new PLZVideoTextureScreenDrawer();
        }

        drawer.tex = tex != null && tex.isValid() ? tex : null;
        drawer.level = z;
        drawer.frame = frame;
        float dx = (rx - lx) / width;
        float dy = (ry - ly) / width;
        float backX = dy * offset * 0.5F;
        float backY = -dx * offset * 0.5F;
        float b = border / LEVEL_UNITS;
        drawer.setQuad(0, lx - dx * border + backX, ly - dy * border + backY, rx + dx * border + backX, ry + dy * border + backY, z + lift - b, z + lift + height + b, bias);
        drawer.setQuad(1, lx, ly, rx, ry, z + lift, z + lift + height, bias);
        SpriteRenderer.instance.drawGeneric(drawer);
        return 1;
    }
}
