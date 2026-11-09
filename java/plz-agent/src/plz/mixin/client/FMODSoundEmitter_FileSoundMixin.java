package plz.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zombie.plz.PLZMusicStream;
import zombie.plz.access.PLZFMODFileSoundAccess;

@Mixin(targets = "fmod.fmod.FMODSoundEmitter$FileSound")
abstract class FMODSoundEmitter_FileSoundMixin implements PLZFMODFileSoundAccess {
    @Shadow
    private long sound;

    @Unique
    private boolean plz$streamed;

    @Override
    public void plzSetStreamed(boolean streamed) {
        this.plz$streamed = streamed;
    }

    @Inject(
        method = "stop",
        at = @At(value = "INVOKE", target = "Lfmod/javafmod;FMOD_Channel_Stop(J)I", shift = At.Shift.AFTER)
    )
    private void plz$releaseStream(boolean bReleaseEvent, boolean bImmediate, CallbackInfo ci) {
        if (this.plz$streamed) {
            PLZMusicStream.release(this.sound);
            this.plz$streamed = false;
        }
    }
}
