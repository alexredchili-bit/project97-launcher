package plz.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import zombie.characters.IsoGameCharacter;
import zombie.characters.IsoPlayer;
import zombie.inventory.types.HandWeapon;
import zombie.iso.IsoGridSquare;
import zombie.iso.areas.IsoRoom;
import zombie.iso.objects.IsoTree;
import zombie.plz.PLZTreeGate;

@Mixin(IsoTree.class)
abstract class IsoTreeMixin {
    @Shadow
    private boolean isWithinDistanceSquared(float distance) {
        throw new AssertionError();
    }

    @Inject(method = "WeaponHit", at = @At("HEAD"), cancellable = true)
    private void plz$loggingZoneGate(IsoGameCharacter owner, HandWeapon weapon, CallbackInfo ci) {
        if (PLZTreeGate.refusesFell((IsoTree) (Object) this, owner)) {
            ci.cancel();
        }
    }

    public boolean plzAdoptIntoErosion(int species, int stage, int maxStage) {
        return PLZTreeGate.adoptIntoErosion((IsoTree) (Object) this, species, stage, maxStage);
    }

    // isInARoom() is also true for an IsoRegions player room, which has no IsoRoom.
    @Inject(method = "isPlayerInsideARoom", at = @At("HEAD"), cancellable = true)
    private void plz$regionRoomWithoutIsoRoom(IsoPlayer player, CallbackInfoReturnable<Boolean> cir) {
        if (!player.isInARoom()) {
            return;
        }

        IsoGridSquare playerSquare = player.getSquare();
        IsoRoom room = playerSquare == null ? null : playerSquare.getRoom();
        if (room == null) {
            cir.setReturnValue(this.isWithinDistanceSquared(24.0F));
        }
    }
}
