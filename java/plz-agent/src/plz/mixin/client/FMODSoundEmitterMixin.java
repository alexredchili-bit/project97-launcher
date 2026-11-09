package plz.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import fmod.fmod.FMODManager;
import fmod.fmod.FMODSoundEmitter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import zombie.audio.GameSoundClip;
import zombie.iso.IsoObject;
import zombie.plz.PLZMusicStream;
import zombie.plz.access.PLZFMODFileSoundAccess;

@Mixin(FMODSoundEmitter.class)
abstract class FMODSoundEmitterMixin {
    @WrapOperation(
        method = "addSound",
        at = @At(value = "INVOKE", target = "Lfmod/fmod/FMODManager;loadSound(Ljava/lang/String;)J")
    )
    private long plz$openStream(
        FMODManager manager,
        String file,
        Operation<Long> original,
        @Local(argsOnly = true) GameSoundClip clip,
        @Share("plzStreamed") LocalBooleanRef streamed
    ) {
        String streamPath = PLZMusicStream.streamPath(clip);
        long sound = streamPath == null ? 0L : PLZMusicStream.open(streamPath, clip.gameSound.isLooped());
        streamed.set(sound != 0L);
        return sound != 0L ? sound : original.call(manager, file);
    }

    @Inject(
        method = "addSound",
        at = @At(value = "INVOKE", target = "Lfmod/javafmod;FMOD_Channel_SetVolume(JF)I", ordinal = 0),
        cancellable = true
    )
    private void plz$attachStream(
        GameSoundClip clip,
        float volume,
        IsoObject parent,
        CallbackInfoReturnable<?> cir,
        @Local(ordinal = 0) long sound,
        @Local(ordinal = 1) long channel,
        @Share("plzStreamed") LocalBooleanRef streamed
    ) {
        if (!streamed.get()) {
            return;
        }

        if (channel == 0L) {
            PLZMusicStream.release(sound);
            cir.setReturnValue(null);
            return;
        }

        PLZMusicStream.attach(sound, channel);
    }

    @Inject(method = "addSound", at = @At("RETURN"))
    private void plz$markStreamed(
        GameSoundClip clip,
        float volume,
        IsoObject parent,
        CallbackInfoReturnable<?> cir,
        @Share("plzStreamed") LocalBooleanRef streamed
    ) {
        if (cir.getReturnValue() instanceof PLZFMODFileSoundAccess fileSound) {
            fileSound.plzSetStreamed(streamed.get());
        }
    }
}
