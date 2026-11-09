package plz.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zombie.audio.BaseSoundEmitter;
import zombie.plz.PLZStation;
import zombie.radio.devices.DeviceData;

@Mixin(DeviceData.class)
abstract class DeviceDataMixin {
    @Unique
    private PLZStation.Receiver plz$station;

    @Shadow
    protected BaseSoundEmitter emitter;

    @Shadow
    protected long radioLoopSound;

    @Shadow
    protected float deviceVolume;

    @Shadow
    private void setSoundVolume(long eventInstance, float volume) {
        throw new AssertionError();
    }

    @Inject(
        method = "updateEmitter",
        at = @At(value = "INVOKE", target = "Lzombie/radio/devices/DeviceData;setEmitterAndPos()V", ordinal = 1, shift = At.Shift.AFTER),
        cancellable = true
    )
    private void plz$stationReception(CallbackInfo ci) {
        PLZStation.Receiver plzStation = this.emitter == null ? null : PLZStation.receiverFor((DeviceData) (Object) this);
        this.plz$station = plzStation;
        if (plzStation != null && plzStation.level >= PLZStation.LEVEL_FULL) {
            if (this.radioLoopSound > 0L) {
                this.emitter.stopSound(this.radioLoopSound);
                this.radioLoopSound = 0L;
            }

            this.emitter.tick();
            ci.cancel();
        }
    }

    @Inject(
        method = "updateEmitter",
        at = @At(value = "INVOKE", target = "Lzombie/audio/BaseSoundEmitter;tick()V", ordinal = 1)
    )
    private void plz$fringeStatic(CallbackInfo ci) {
        if (this.plz$station != null && this.radioLoopSound != 0L) {
            this.setSoundVolume(this.radioLoopSound, this.deviceVolume * PLZStation.FRINGE_STATIC);
        }
    }
}
