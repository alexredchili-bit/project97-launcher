package plz.mixin.client;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import zombie.characters.IsoGameCharacter;
import zombie.core.skinnedmodel.advancedanimation.AnimatedModel;
import zombie.plz.PLZSceneTools;
import zombie.plz.access.PLZSceneCharacterAccess;
import zombie.plz.access.PLZSceneObjectAccess;

@Mixin(targets = "zombie.vehicles.UI3DScene$SceneCharacter")
abstract class UI3DScene_SceneCharacterMixin implements PLZSceneCharacterAccess {
    @Shadow
    @Final
    AnimatedModel animatedModel;

    @Shadow
    boolean clearDepthBuffer;

    @Unique
    private String plz$armsRoot;

    @Unique
    private IsoGameCharacter plz$poseSource;

    @Unique
    private float plz$poseWeight;

    @Unique
    private final Vector3f plz$look = new Vector3f();

    @Unique
    private final Vector3f plz$lookAnchor = new Vector3f();

    @Unique
    private boolean plz$following;

    @Unique
    private Matrix4f plz$aimHeld;

    @Shadow
    abstract Matrix4f getBoneMatrix(String boneName, Matrix4f mat);

    @Inject(
        method = "renderMain",
        at = @At(value = "INVOKE", target = "Lzombie/core/skinnedmodel/advancedanimation/AnimatedModel;update()V", shift = At.Shift.AFTER)
    )
    private void plz$mirrorPose(CallbackInfoReturnable<Object> cir) {
        if (this.plz$poseSource != null) {
            PLZSceneTools.copyPose(this.plz$poseSource.getAnimationPlayer(), this.animatedModel.getAnimationPlayer(), this.plz$poseWeight);
        }
    }

    @Inject(method = "getLocalTransform", at = @At("HEAD"), cancellable = true)
    private void plz$solvedTransform(Matrix4f transform, CallbackInfoReturnable<Matrix4f> cir) {
        Matrix4f solved = ((PLZSceneObjectAccess)this).plzGetTransform();
        if (solved != null) {
            cir.setReturnValue(transform.set(solved));
        }
    }

    @Override
    public AnimatedModel plzAnimatedModel() {
        return this.animatedModel;
    }

    @Override
    public Matrix4f plzBoneMatrix(String boneName, Matrix4f mat) {
        return this.getBoneMatrix(boneName, mat);
    }

    @Override
    public void plzSetClearDepthBuffer(boolean clear) {
        this.clearDepthBuffer = clear;
    }

    @Override
    public String plzArmsRoot() {
        return this.plz$armsRoot;
    }

    @Override
    public void plzSetArmsRoot(String root) {
        this.plz$armsRoot = root;
    }

    @Override
    public void plzSetPoseSource(IsoGameCharacter source) {
        this.plz$poseSource = source;
    }

    @Override
    public void plzSetPoseWeight(float weight) {
        this.plz$poseWeight = weight;
    }

    @Override
    public Vector3f plzLook() {
        return this.plz$look;
    }

    @Override
    public Vector3f plzLookAnchor() {
        return this.plz$lookAnchor;
    }

    @Override
    public boolean plzFollowing() {
        return this.plz$following;
    }

    @Override
    public void plzSetFollowing(boolean following) {
        this.plz$following = following;
    }

    @Override
    public Matrix4f plzAimHeld() {
        return this.plz$aimHeld;
    }

    @Override
    public void plzSetAimHeld(Matrix4f aimHeld) {
        this.plz$aimHeld = aimHeld;
    }
}
