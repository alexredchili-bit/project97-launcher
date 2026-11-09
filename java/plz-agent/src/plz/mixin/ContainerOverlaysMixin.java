package plz.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zombie.iso.ContainerOverlays;
import zombie.iso.IsoObject;
import zombie.plz.PLZShelfOverlay;

@Mixin(ContainerOverlays.class)
abstract class ContainerOverlaysMixin {
    // A shelf a shopkeeper has dressed keeps its look; vanilla re-picks it on every item move and chunk load.
    @Inject(method = "updateContainerOverlaySprite", at = @At("HEAD"), cancellable = true)
    private void plz$keepDressedShelf(IsoObject obj, CallbackInfo ci) {
        if (PLZShelfOverlay.isManaged(obj)) {
            ci.cancel();
        }
    }
}
