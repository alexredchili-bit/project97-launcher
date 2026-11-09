package plz.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.ByteBuffer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import plz.agent.LuaPublic;
import zombie.ZomboidFileSystem;
import zombie.core.SpriteRenderer;
import zombie.core.opengl.RenderThread;
import zombie.core.textures.Texture;
import zombie.core.textures.VideoTexture;
import zombie.plz.PLZCinema;
import zombie.plz.PLZLiveAudio;
import zombie.plz.PLZLiveLink;
import zombie.plz.PLZLiveVideo;
import zombie.ui.UIFont;

@Mixin(VideoTexture.class)
abstract class VideoTextureMixin {
    @Shadow
    protected boolean useAsync;

    @Shadow
    protected int binkId;

    @Unique
    private PLZLiveVideo.Feed plz$liveFeed;

    @Unique
    private volatile boolean plz$liveShown;

    @Unique
    private int plz$clockShown;

    @Unique
    private int plz$clockDecoded;

    @Unique
    private boolean plz$cinemaClosing;

    @Shadow
    public abstract void Close();

    @Shadow
    private void processFrame(int id) {
        throw new AssertionError();
    }

    @Shadow
    private boolean processFrameAsyncWait(int id, int timeout) {
        throw new AssertionError();
    }

    @Shadow
    private void nextFrame(int id) {
        throw new AssertionError();
    }

    @Shadow
    private long getCurrentFrameData(int id) {
        throw new AssertionError();
    }

    @Inject(method = "<init>*", at = @At("RETURN"))
    private void plz$initClock(CallbackInfo ci) {
        this.plz$clockShown = -1;
        this.plz$clockDecoded = -1;
    }

    @ModifyReturnValue(method = "isValid", at = @At("RETURN"))
    private boolean plz$validWhileLive(boolean valid) {
        return valid || this.plz$liveShown;
    }

    @Unique
    private VideoTexture plz$self() {
        return (VideoTexture) (Object) this;
    }

    // The constructors are private, and only the merged method runs inside VideoTexture to reach them.
    @Unique
    private static VideoTexture plz$construct(String filename, int width, int height, boolean useAsync) {
        try {
            MethodHandle ctor = MethodHandles.lookup()
                .findConstructor(VideoTexture.class, MethodType.methodType(void.class, String.class, int.class, int.class, boolean.class));
            return (VideoTexture) ctor.invoke(filename, width, height, useAsync);
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable t) {
            throw new IllegalStateException(t);
        }
    }

    @Unique
    @LuaPublic
    private static VideoTexture cinemaLiveOpen(String key) {
        VideoTexture videoTexture = plz$construct("live:" + key, 480, 270, false);
        ((VideoTextureMixin) (Object) videoTexture).plz$liveFeed = PLZLiveVideo.openFeed(key);
        return videoTexture;
    }

    public long cinemaLivePump() {
        PLZLiveVideo.Feed feed = this.plz$liveFeed;
        if (feed == null || this.plz$cinemaClosing) {
            return -1L;
        }

        ByteBuffer frame = feed.takePending();
        if (frame != null) {
            int w = feed.getWidth();
            int h = feed.getHeight();
            RenderThread.queueInvokeOnRenderContext(() -> {
                GL13.glActiveTexture(33984);
                GL11.glBindTexture(3553, Texture.lastTextureID = this.plz$self().getID());
                GL11.glTexParameteri(3553, 10241, 9729);
                GL11.glTexParameteri(3553, 10240, 9729);
                GL11.glTexParameteri(3553, 10242, 33071);
                GL11.glTexParameteri(3553, 10243, 33071);
                GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 4);
                GL11.glTexImage2D(3553, 0, 6408, w, h, 0, 6408, 5121, frame);
                SpriteRenderer.ringBuffer.restoreBoundTextures = true;
                feed.recycle(frame);
                this.plz$liveShown = true;
            });
        }

        return feed.decoded;
    }

    public long cinemaLiveAgeMs() {
        return this.plz$liveFeed == null || this.plz$liveFeed.lastFrameMs == 0L ? -1L : System.currentTimeMillis() - this.plz$liveFeed.lastFrameMs;
    }

    @Unique
    @LuaPublic
    private static void cinemaLiveAudioPlace(String stream, float x, float y, float z, float range, float volume) {
        PLZLiveAudio.place(stream, x, y, z, range, volume);
    }

    @Unique
    @LuaPublic
    private static String cinemaLiveStatus(String key) {
        return PLZLiveLink.status(key);
    }

    @Unique
    @LuaPublic
    private static boolean cinemaLiveLinked() {
        return PLZLiveLink.connected();
    }

    @Unique
    @LuaPublic
    private static void cinemaCaptureStart(String token, int width, int height, int fps) {
        PLZLiveVideo.captureStart(token, width, height, fps);
    }

    @Unique
    @LuaPublic
    private static void cinemaCaptureStop() {
        PLZLiveVideo.captureStop();
    }

    @Unique
    @LuaPublic
    private static boolean cinemaCaptureTick() {
        return PLZLiveVideo.captureTick();
    }

    @Unique
    @LuaPublic
    private static String cinemaCaptureStats() {
        return PLZCinema.cinemaCaptureStats();
    }

    public int renderFrameAt(int target, int lastFrame, int maxDecode) {
        if (this.binkId < 0 || this.plz$cinemaClosing) {
            return -1;
        }

        if (target > lastFrame) {
            target = lastFrame;
        }

        if (target <= this.plz$clockShown) {
            return this.plz$clockShown;
        }

        int decoded = 0;
        if (this.plz$clockDecoded < 0) {
            if (this.useAsync) {
                this.processFrameAsyncWait(this.binkId, -1);
            } else {
                this.processFrame(this.binkId);
            }

            this.plz$clockDecoded = 0;
            decoded++;
        }

        while (this.plz$clockDecoded < target && decoded < maxDecode) {
            this.nextFrame(this.binkId);
            this.processFrame(this.binkId);
            this.plz$clockDecoded++;
            decoded++;
        }

        long frameData = this.getCurrentFrameData(this.binkId);
        RenderThread.queueInvokeOnRenderContext(() -> {
            VideoTexture self = this.plz$self();
            GL13.glActiveTexture(33984);
            GL11.glBindTexture(3553, Texture.lastTextureID = self.getID());
            GL11.glTexParameteri(3553, 10241, 9729);
            GL11.glTexParameteri(3553, 10240, 9729);
            GL11.glTexParameteri(3553, 10242, 33071);
            GL11.glTexParameteri(3553, 10243, 33071);
            GL11.glTexImage2D(3553, 0, 6408, self.getWidth(), self.getHeight(), 0, 6408, 5121, frameData);
            SpriteRenderer.ringBuffer.restoreBoundTextures = true;
        });
        this.plz$clockShown = this.plz$clockDecoded;
        return this.plz$clockShown;
    }

    @Unique
    @LuaPublic
    private static VideoTexture cinemaOpen(String filename, int width, int height) {
        VideoTexture videoTexture = plz$construct(ZomboidFileSystem.instance.getMediaPath("videos/" + filename), width, height, true);
        if (videoTexture.LoadVideoFile()) {
            return videoTexture;
        }

        RenderThread.queueInvokeOnRenderContext(videoTexture::destroy);
        return null;
    }

    public void cinemaClose() {
        if (this.plz$cinemaClosing) {
            return;
        }

        this.plz$cinemaClosing = true;
        if (this.plz$liveFeed != null) {
            this.plz$liveFeed.stop();
        }
        // queued uploads still read Bink's frame buffer, so free it after them on the same queue
        RenderThread.queueInvokeOnRenderContext(() -> {
            this.Close();
            VideoTexture self = this.plz$self();
            if (!self.isDestroyed()) {
                self.destroy();
            }
        });
    }

    @Unique
    @LuaPublic
    private static boolean cinemaMediaExists(String file) {
        return PLZCinema.cinemaMediaExists(file);
    }

    @Unique
    @LuaPublic
    private static void cinemaDrawCaption(UIFont uiFont, String text, double cx, double cy, double angle, double alpha) {
        PLZCinema.cinemaDrawCaption(uiFont, text, cx, cy, angle, alpha);
    }

    @Unique
    @LuaPublic
    private static int cinemaAudioLoad(String slot, String file, double mode) {
        return PLZCinema.cinemaAudioLoad(slot, file, mode);
    }

    @Unique
    @LuaPublic
    private static int cinemaAudioLoadSound(String slot, String soundName, double mode) {
        return PLZCinema.cinemaAudioLoadSound(slot, soundName, mode);
    }

    @Unique
    @LuaPublic
    private static int cinemaAudioLoadUpload(String slot, String sha, double mode) {
        return PLZCinema.cinemaAudioLoadUpload(slot, sha, mode);
    }

    @Unique
    @LuaPublic
    private static boolean cinemaUploadExists(String sha) {
        return PLZCinema.cinemaUploadExists(sha);
    }

    @Unique
    @LuaPublic
    private static boolean plzUploadRegister(String sha, double durationMs, boolean revoked) {
        return zombie.plz.PLZUploadSound.register(sha, durationMs, revoked);
    }

    @Unique
    @LuaPublic
    private static double cinemaAudioLengthMs(String slot) {
        return PLZCinema.cinemaAudioLengthMs(slot);
    }

    @Unique
    @LuaPublic
    private static boolean cinemaAudioIsPlaying(String slot) {
        return PLZCinema.cinemaAudioIsPlaying(slot);
    }

    @Unique
    @LuaPublic
    private static int cinemaAudioPreroll(String slot, double targetMs, double volume) {
        return PLZCinema.cinemaAudioPreroll(slot, targetMs, volume);
    }

    @Unique
    @LuaPublic
    private static void cinemaAudioUnpause(String slot) {
        PLZCinema.cinemaAudioUnpause(slot);
    }

    @Unique
    @LuaPublic
    private static void cinemaAudioVolume(String slot, double volume) {
        PLZCinema.cinemaAudioVolume(slot, volume);
    }

    @Unique
    @LuaPublic
    private static int cinemaAudioRate(String slot, double rate) {
        return PLZCinema.cinemaAudioRate(slot, rate);
    }

    @Unique
    @LuaPublic
    private static int cinemaAudioSeek(String slot, double ms) {
        return PLZCinema.cinemaAudioSeek(slot, ms);
    }

    @Unique
    @LuaPublic
    private static long cinemaAudioSeekMicros(String slot) {
        return PLZCinema.cinemaAudioSeekMicros(slot);
    }

    @Unique
    @LuaPublic
    private static double cinemaAudioPosMs(String slot) {
        return PLZCinema.cinemaAudioPosMs(slot);
    }

    @Unique
    @LuaPublic
    private static double cinemaAudioAudibility(String slot) {
        return PLZCinema.cinemaAudioAudibility(slot);
    }

    @Unique
    @LuaPublic
    private static boolean cinemaAudioIsVirtual(String slot) {
        return PLZCinema.cinemaAudioIsVirtual(slot);
    }

    @Unique
    @LuaPublic
    private static void cinemaAudioRelease(String slot) {
        PLZCinema.cinemaAudioRelease(slot);
    }

    @Unique
    @LuaPublic
    private static int cinemaAudioPlace(String slot, float sx, float sy, float sz, float minDist, float maxDist, float occlusion, float level) {
        return PLZCinema.cinemaAudioPlace(slot, sx, sy, sz, minDist, maxDist, occlusion, level);
    }

    @Unique
    @LuaPublic
    private static int cinemaLastHidden() {
        return PLZCinema.cinemaLastHidden();
    }

    @Unique
    @LuaPublic
    private static int cinemaDrawScreen(
        VideoTexture tex, float x, float y, int z, int facing, float width, float lift, float offset, float bias, float border, float frame
    ) {
        return PLZCinema.cinemaDrawScreen(tex, x, y, z, facing, width, lift, offset, bias, border, frame);
    }

    @Unique
    @LuaPublic
    private static int cinemaDrawFree(
        VideoTexture tex, float lx, float ly, float rx, float ry, int z, float lift, float offset, float bias, float border, float frame
    ) {
        return PLZCinema.cinemaDrawFree(tex, lx, ly, rx, ry, z, lift, offset, bias, border, frame);
    }
}
