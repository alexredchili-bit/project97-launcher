package plz.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import zombie.characters.IsoGameCharacter;
import zombie.iso.IsoGridSquare;
import zombie.iso.objects.IsoWindow;
import zombie.plz.PLZGatedGrid;

@Mixin(IsoWindow.class)
abstract class IsoWindowMixin {
    // Every non-door route into a building climbs through here and never reaches testCollideAdjacent; testSquare is the destination.
    @Inject(
        method = "canClimbThroughHelper",
        at = @At(value = "INVOKE", target = "Lzombie/characters/IsoGameCharacter;canClimbDownSheetRope(Lzombie/iso/IsoGridSquare;)Z"),
        cancellable = true
    )
    private static void plz$sealedHomeRefusesClimb(
        IsoGameCharacter chr, IsoGridSquare sq, IsoGridSquare oppositeSq, boolean north, CallbackInfoReturnable<Boolean> cir,
        @Local(ordinal = 2) IsoGridSquare testSquare
    ) {
        if (PLZGatedGrid.refusesEntry(testSquare, chr)) {
            cir.setReturnValue(false);
        }
    }
}
