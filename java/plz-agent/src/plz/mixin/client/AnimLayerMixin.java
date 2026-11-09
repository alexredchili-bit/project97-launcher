package plz.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zombie.characters.IsoGameCharacter;
import zombie.core.skinnedmodel.BaseGrappleable;
import zombie.core.skinnedmodel.IGrappleable;
import zombie.core.skinnedmodel.advancedanimation.AdvancedAnimator;
import zombie.core.skinnedmodel.advancedanimation.AnimEvent;
import zombie.core.skinnedmodel.advancedanimation.AnimLayer;
import zombie.core.skinnedmodel.advancedanimation.IAnimatable;
import zombie.core.skinnedmodel.advancedanimation.LiveAnimNode;
import zombie.core.skinnedmodel.animation.AnimationTrack;
import zombie.iso.Vector2;
import zombie.iso.Vector3;
import zombie.plz.PLZAnimalForm;
import zombie.plz.PLZGrappleEdit;
import zombie.plz.access.PLZBaseGrappleableAccess;

@Mixin(AnimLayer.class)
abstract class AnimLayerMixin {
    @Shadow
    private AnimLayer parentLayer;

    @Shadow
    private IAnimatable character;

    @Unique
    private float plz$attachFraction;

    @Unique
    private float plz$rightX;

    @Unique
    private float plz$rightY;

    @Unique
    private Vector3 plz$anchorPos;

    @Unique
    private Vector3 plz$offsetPos;

    @Unique
    private Vector2 plz$forwardDir;

    @Shadow
    private void setGrappleablePosAndRotation(IGrappleable grappleable, Vector3 position, Vector2 rotationForward, boolean useDeferredOffset) {
        throw new AssertionError();
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void plz$init(CallbackInfo ci) {
        this.plz$anchorPos = new Vector3();
        this.plz$offsetPos = new Vector3();
        this.plz$forwardDir = new Vector2();
    }

    @WrapOperation(
        method = "invokeAnimEvent(Lzombie/core/skinnedmodel/advancedanimation/LiveAnimNode;Lzombie/core/skinnedmodel/animation/AnimationTrack;Lzombie/core/skinnedmodel/advancedanimation/AnimEvent;)V",
        at = @At(
            value = "INVOKE",
            target = "Lzombie/core/skinnedmodel/advancedanimation/AdvancedAnimator;OnAnimEvent(Lzombie/core/skinnedmodel/advancedanimation/AnimLayer;Lzombie/core/skinnedmodel/animation/AnimationTrack;Lzombie/core/skinnedmodel/advancedanimation/AnimEvent;)V"
        )
    )
    private void plz$silenceAnimalForm(
        AdvancedAnimator animator, AnimLayer layer, AnimationTrack track, AnimEvent animEvent, Operation<Void> original
    ) {
        if (PLZAnimalForm.silences(this.character, animEvent.eventName)) {
            return;
        }

        original.call(animator, layer, track, animEvent);
    }

    @Inject(
        method = "updateInternal",
        at = @At(
            value = "INVOKE",
            target = "Lzombie/core/skinnedmodel/advancedanimation/IAnimatable;getGrappleable()Lzombie/core/skinnedmodel/IGrappleable;",
            shift = At.Shift.AFTER
        )
    )
    private void plz$captureAttachFraction(float deltaT, CallbackInfo ci) {
        this.plz$attachFraction = this.parentLayer == null ? this.plz$attachedClipFraction() : -1.0F;
    }

    @WrapOperation(
        method = "updateInternal",
        at = @At(
            value = "INVOKE",
            target = "Lzombie/core/skinnedmodel/advancedanimation/AnimLayer;updateInternalGrapple(Lzombie/core/skinnedmodel/IGrappleable;Lzombie/core/skinnedmodel/advancedanimation/LiveAnimNode;)V"
        )
    )
    private void plz$syncAttachedClipAfterGrapple(
        AnimLayer layer, IGrappleable thisGrappleable, LiveAnimNode liveAnimNode, Operation<Void> original
    ) {
        original.call(layer, thisGrappleable, liveAnimNode);
        if (this.plz$attachFraction >= 0.0F) {
            this.plz$syncAttachedClip(liveAnimNode, this.plz$attachFraction);
        }
    }

    // Every exit, the empty-layer one included: an attachment is placed even when no node plays.
    @Inject(method = "updateInternal", at = @At("RETURN"))
    private void plz$updateAttachmentAtEnd(float deltaT, CallbackInfo ci) {
        if (this.parentLayer == null) {
            this.plz$updateAttachment();
        }
    }

    // The holder's right, taken before yaw turns the vector, so side never turns the held character.
    @WrapOperation(
        method = "calculateOffsetsForGrappleable",
        at = @At(value = "INVOKE", target = "Lzombie/iso/Vector2;rotate(F)V")
    )
    private void plz$captureHolderRight(Vector2 relativeToForward, float radians, Operation<Void> original) {
        this.plz$rightX = -relativeToForward.y;
        this.plz$rightY = relativeToForward.x;
        original.call(relativeToForward, radians);
    }

    @Inject(method = "calculateOffsetsForGrappleable", at = @At("TAIL"))
    private void plz$applyHolderSide(
        IGrappleable relativeTo, float offsetForwardDistance, float offsetRotYaw, Vector2 relativeToForward, Vector3 offsetPos, CallbackInfo ci
    ) {
        float side = PLZGrappleEdit.sideFor(relativeTo);
        offsetPos.x = offsetPos.x + this.plz$rightX * side;
        offsetPos.y = offsetPos.y + this.plz$rightY * side;
    }

    @Unique
    private BaseGrappleable plz$attachedSelf() {
        if (!(this.character instanceof IsoGameCharacter self)) {
            return null;
        }

        BaseGrappleable mine = self.getWrappedGrappleable();
        if (mine == null || ((PLZBaseGrappleableAccess) mine).plzGetAnchor() == null) {
            return null;
        }

        // A real grapple owns the position; an attachment never fights it.
        return self.isGrappling() || self.isBeingGrappled() ? null : mine;
    }

    @Unique
    private static boolean plz$anchorUsable(IGrappleable anchor) {
        if (anchor instanceof IsoGameCharacter a) {
            return !a.isDead() && a.getCurrentSquare() != null;
        }
        return anchor != null;
    }

    @Unique
    private static String plz$anchorNode(IGrappleable anchor) {
        return anchor.isGrappling() ? anchor.getSharedGrappleAnimNode() : "";
    }

    @Unique
    private static float plz$anchorFraction(IGrappleable anchor) {
        return anchor.isGrappling() ? anchor.getSharedGrappleAnimFraction() : PLZGrappleEdit.fraction(0.0F);
    }

    @Unique
    private float plz$attachedClipFraction() {
        BaseGrappleable mine = this.plz$attachedSelf();
        IGrappleable anchor = mine == null ? null : ((PLZBaseGrappleableAccess) mine).plzGetAnchor();
        if (!plz$anchorUsable(anchor)) {
            return -1.0F;
        }

        String key = PLZGrappleEdit.resolveAttach(plz$anchorNode(anchor), ((PLZBaseGrappleableAccess) mine).plzGetAttachSlot());
        return PLZGrappleEdit.phase(key, plz$anchorFraction(anchor));
    }

    @Unique
    private void plz$syncAttachedClip(LiveAnimNode liveAnimNode, float fraction) {
        if (!PLZGrappleEdit.isAttachSyncedNode(liveAnimNode.getName())) {
            return;
        }

        for (int trackIdx = 0, count = liveAnimNode.getPlayingTrackCount(); trackIdx < count; trackIdx++) {
            AnimationTrack track = liveAnimNode.getPlayingTrackAt(trackIdx);
            if (track.isPlaying && track.isPrimary) {
                track.moveCurrentTimeValueTo(fraction * track.getDuration());
                ((LiveAnimNodeMixin) liveAnimNode).plz$setNodeAnimTime(track.getCurrentTimeValue());
            }
        }
    }

    @Unique
    private void plz$updateAttachment() {
        BaseGrappleable mine = this.plz$attachedSelf();
        IGrappleable anchor = mine == null ? null : ((PLZBaseGrappleableAccess) mine).plzGetAnchor();
        if (!plz$anchorUsable(anchor)) {
            return;
        }

        String key = PLZGrappleEdit.resolveAttach(plz$anchorNode(anchor), ((PLZBaseGrappleableAccess) mine).plzGetAttachSlot());
        float fraction = plz$anchorFraction(anchor);
        float forward = PLZGrappleEdit.attachForward(key, fraction);
        float yaw = PLZGrappleEdit.yaw(key, 0.0F, fraction);
        float side = PLZGrappleEdit.attachSide(key, fraction);

        Vector3 anchorPos = this.plz$anchorPos;
        Vector3 offsetPos = this.plz$offsetPos;
        Vector2 forwardDir = this.plz$forwardDir;
        anchor.getAnimForwardDirection(forwardDir);
        float rightX = -forwardDir.y;
        float rightY = forwardDir.x;
        forwardDir.rotate((float) (Math.PI / 180.0) * yaw);
        anchor.getPosition(anchorPos);
        offsetPos.x = anchorPos.x + forwardDir.x * forward + rightX * side;
        offsetPos.y = anchorPos.y + forwardDir.y * forward + rightY * side;
        offsetPos.z = anchorPos.z;
        this.setGrappleablePosAndRotation((IGrappleable) this.character, offsetPos, forwardDir, false);
    }
}
