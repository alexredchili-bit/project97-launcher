package plz.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zombie.core.skinnedmodel.BaseGrappleable;
import zombie.core.skinnedmodel.advancedanimation.GrappleOffsetBehaviour;
import zombie.core.skinnedmodel.IGrappleable;
import zombie.core.skinnedmodel.IGrappleableWrapper;
import zombie.plz.PLZGrappleEdit;
import zombie.plz.access.PLZBaseGrappleableAccess;

@Mixin(BaseGrappleable.class)
abstract class BaseGrappleableMixin implements PLZBaseGrappleableAccess {
    @Shadow
    private IGrappleable parentGrappleable;

    @Shadow
    private boolean doContinueGrapple;

    @Shadow
    private boolean isGrappling;

    @Shadow
    private String sharedGrappleType;

    @Shadow
    private String sharedGrappleAnimNode;

    @Shadow
    private float grappleOffsetForward;

    @Shadow
    private float grappleOffsetYaw;

    @Unique
    private volatile IGrappleable plz$anchor;

    @Unique
    private volatile String plz$attachSlot;

    @Shadow
    public abstract boolean isBeingGrappled();

    @Shadow
    public abstract boolean isGrappling();

    @Shadow
    public abstract IGrappleable getGrappledBy();

    @Shadow
    public abstract float getSharedGrappleAnimFraction();

    @Inject(method = "<init>*", at = @At("RETURN"))
    private void plz$init(CallbackInfo ci) {
        this.plz$attachSlot = "";
    }

    // Placed every frame from the anchor's rendered position, never its network one, which keeps it from jittering.
    @Override
    public void plzAttach(IGrappleable anchor, String slot) {
        boolean selfOrCycle = anchor == null
            || anchor == (Object) this
            || anchor == this.parentGrappleable
            || plz$anchorOf(anchor) == this.parentGrappleable;
        if (selfOrCycle || slot == null || slot.trim().isEmpty()) {
            this.plzDetach();
            return;
        }

        this.plz$attachSlot = slot.trim();
        this.plz$anchor = anchor;
    }

    @Override
    public void plzDetach() {
        this.plz$anchor = null;
        this.plz$attachSlot = "";
    }

    @Override
    public IGrappleable plzGetAnchor() {
        return this.plz$anchor;
    }

    @Override
    public String plzGetAttachSlot() {
        return this.plz$attachSlot;
    }

    @Unique
    private static IGrappleable plz$anchorOf(IGrappleable grappleable) {
        if (grappleable instanceof IGrappleableWrapper wrapper && wrapper.getWrappedGrappleable() instanceof BaseGrappleable base) {
            return ((PLZBaseGrappleableAccess) base).plzGetAnchor();
        }
        return grappleable instanceof BaseGrappleable base ? ((PLZBaseGrappleableAccess) base).plzGetAnchor() : null;
    }

    @ModifyExpressionValue(
        method = "Grappled",
        at = @At(value = "INVOKE", target = "Lzombie/core/skinnedmodel/BaseGrappleable;canBeGrappled()Z")
    )
    private boolean plz$forcedGrappleType(boolean canBeGrappled, @Local(argsOnly = true) String grappleType) {
        return canBeGrappled || "PLZEscort".equals(grappleType);
    }

    @ModifyReturnValue(method = "getSharedGrappleAnimFraction", at = @At("RETURN"))
    private float plz$editedFraction(float sharedGrappleFraction) {
        float fraction = PLZGrappleEdit.fraction(sharedGrappleFraction);
        if (!this.isBeingGrappled()) {
            return fraction;
        }

        // The phase is stored against the GRAPPLER node, because that is the node the editor edits.
        IGrappleable grappler = this.getGrappledBy();
        return grappler == null ? fraction : PLZGrappleEdit.phase(grappler.getSharedGrappleAnimNode(), fraction);
    }

    // AnimLayer has already stamped the node value this frame; the registry gets the last word so the editor moves it live.
    @ModifyReturnValue(method = "getGrapplePosOffsetForward", at = @At(value = "RETURN", ordinal = 1))
    private float plz$editedForward(float original) {
        if (!this.isGrappling()) {
            return original;
        }

        return PLZGrappleEdit.spaced(
            (BaseGrappleable) (Object) this,
            PLZGrappleEdit.forward(this.sharedGrappleAnimNode, this.grappleOffsetForward, this.getSharedGrappleAnimFraction())
        );
    }

    @ModifyReturnValue(method = "getGrappleRotOffsetYaw", at = @At(value = "RETURN", ordinal = 1))
    private float plz$editedYaw(float original) {
        if (!this.isGrappling()) {
            return original;
        }

        return PLZGrappleEdit.yaw(this.sharedGrappleAnimNode, this.grappleOffsetYaw, this.getSharedGrappleAnimFraction());
    }

    @WrapOperation(
        method = "getGrappleOffsetBehaviour",
        at = @At(value = "INVOKE", target = "Lzombie/core/skinnedmodel/IGrappleable;getGrappleOffsetBehaviour()Lzombie/core/skinnedmodel/advancedanimation/GrappleOffsetBehaviour;")
    )
    private GrappleOffsetBehaviour plz$inheritedBehaviour(IGrappleable grappler, Operation<GrappleOffsetBehaviour> original) {
        GrappleOffsetBehaviour inherited = grappler == null ? null : original.call(grappler);
        return inherited == null ? GrappleOffsetBehaviour.NONE : inherited;
    }

    // Never null out of here: AnimLayer switches on it inside the render loop.
    @ModifyReturnValue(method = "getGrappleOffsetBehaviour", at = @At(value = "RETURN", ordinal = 1))
    private GrappleOffsetBehaviour plz$editedBehaviour(GrappleOffsetBehaviour current) {
        if (current == null) {
            current = GrappleOffsetBehaviour.NONE;
        }

        return this.isGrappling() ? PLZGrappleEdit.behaviour(this.sharedGrappleAnimNode, current) : current;
    }

    // An escort is only ever ended by PLZ; writing the current value back makes the vanilla body a no-op.
    @ModifyVariable(method = "setDoContinueGrapple", at = @At("HEAD"), argsOnly = true)
    private boolean plz$holdEscort(boolean doContinueGrapple) {
        if (!doContinueGrapple && this.isGrappling && "PLZEscort".equals(this.sharedGrappleType)) {
            return this.doContinueGrapple;
        }

        return doContinueGrapple;
    }
}
