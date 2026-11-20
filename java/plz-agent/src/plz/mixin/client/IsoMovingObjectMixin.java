package plz.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import zombie.characters.IsoGameCharacter;
import zombie.core.skinnedmodel.BaseGrappleable;
import zombie.iso.IsoMovingObject;
import zombie.plz.access.PLZBaseGrappleableAccess;

@Mixin(IsoMovingObject.class)
abstract class IsoMovingObjectMixin {
    // An attachment sits closer than two bodies' widths; separating them shoves and bumps the anchor every frame.
    @WrapOperation(
        method = "separate",
        at = @At(value = "INVOKE", target = "Lzombie/iso/IsoMovingObject;isSolidForSeparate()Z", ordinal = 1)
    )
    private boolean plz$ignoreAttachedPartner(IsoMovingObject other, Operation<Boolean> original) {
        return original.call(other) && !plz$linked((IsoMovingObject) (Object) this, other);
    }

    @Unique
    private static boolean plz$linked(IsoMovingObject a, IsoMovingObject b) {
        return a instanceof IsoGameCharacter ca && b instanceof IsoGameCharacter cb
            && (plz$anchorOf(ca) == cb || plz$anchorOf(cb) == ca);
    }

    @Unique
    private static Object plz$anchorOf(IsoGameCharacter character) {
        BaseGrappleable grappleable = character.getWrappedGrappleable();
        return grappleable == null ? null : ((PLZBaseGrappleableAccess) grappleable).plzGetAnchor();
    }
}
