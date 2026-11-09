package plz.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zombie.iso.RoomDef;
import zombie.plz.PLZRoomInvalidation;

@Mixin(RoomDef.class)
abstract class RoomDefMixin {
    @Inject(method = "setInvalidateCacheForAllChunks(IJ)V", at = @At("HEAD"))
    private void plz$noteInvalidation(int playerIndex, long dirtyFlags, CallbackInfo ci) {
        PLZRoomInvalidation.invalidated();
    }
}
