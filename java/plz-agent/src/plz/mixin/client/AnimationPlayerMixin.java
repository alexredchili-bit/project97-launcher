package plz.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import org.lwjgl.util.vector.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zombie.characters.IsoGameCharacter;
import zombie.characters.IsoPlayer;
import zombie.core.skinnedmodel.animation.AnimationPlayer;
import zombie.core.skinnedmodel.animation.AnimatorsBoneTransform;
import zombie.core.skinnedmodel.model.SkinningBone;
import zombie.core.skinnedmodel.model.SkinningData;
import zombie.iso.Vector2;
import zombie.plz.PLZAnimalForm;
import zombie.plz.PLZArmClearance;
import zombie.plz.PLZBoneScale;
import zombie.plz.PLZGrappleEdit;
import zombie.plz.PLZGrappleIK;
import zombie.plz.access.PLZAnimationPlayerAccess;

@Mixin(AnimationPlayer.class)
abstract class AnimationPlayerMixin implements PLZAnimationPlayerAccess {
    @Shadow
    public AnimatorsBoneTransform[] boneTransforms;

    @Shadow
    private Matrix4f[] modelTransforms;

    @Shadow
    private SkinningData skinningData;

    @Shadow
    private IsoGameCharacter character;

    // boneTransforms stay vanilla so the twist, turn flip and blending never see the resize.
    @Unique
    private PLZBoneScale.Layout plz$layout;

    @Unique
    private Matrix4f[] plz$local;

    @Unique
    private String plz$previewKey;

    @Unique
    private String plz$rigUser;

    @Unique
    private int plz$rigVersion;

    @Unique
    private PLZBoneScale.Rig plz$rig;

    @Unique
    private boolean plz$previewFemale;

    @Unique
    private PLZArmClearance.Layout plz$armLayout;

    @Unique
    private PLZArmClearance.State plz$armState;

    @Unique
    private PLZGrappleIK.Rig plz$ikRig;

    @Unique
    private PLZGrappleIK.Pose plz$ikPose;

    @Shadow
    public abstract Matrix4f getBindPoseBoneModelTransform(int boneIdx, Matrix4f modelTransform);

    @Shadow
    public abstract float getRenderedAngle();

    @Inject(method = "<init>", at = @At("RETURN"))
    private void plz$init(CallbackInfo ci) {
        this.plz$rigVersion = -1;
        this.plz$armState = new PLZArmClearance.State();
        this.plz$ikPose = new PLZGrappleIK.Pose();
    }

    @Inject(method = "onReleased", at = @At("RETURN"))
    private void plz$resetOnRelease(CallbackInfo ci) {
        this.plz$previewKey = null;
        this.plz$previewFemale = false;
        this.plz$rigUser = null;
        this.plz$rigVersion = -1;
        this.plz$rig = null;
    }

    @Override
    public void plzSetPreviewKey(String key) {
        this.plz$previewKey = key;
    }

    @Override
    public void plzSetPreviewFemale(boolean female) {
        this.plz$previewFemale = female;
    }

    @Override
    public PLZArmClearance.State plzArmState() {
        return this.plz$armState;
    }

    @Override
    public PLZGrappleIK.Pose plzIkPose() {
        return this.plz$ikPose;
    }

    @Inject(method = "updateModelTransformsInternal", at = @At("RETURN"))
    private void plz$resizeAndGrappleIK(CallbackInfo ci) {
        boolean ik = PLZGrappleIK.wants(this.character) && this.plz$ikBind();
        if (ik) {
            this.plz$ikPose.captureVanilla(this.modelTransforms);
        }

        this.plz$applyBoneScale();
        if (ik) {
            this.plz$applyGrappleIK();
        }
    }

    @ModifyReturnValue(method = "getDeferredMovement(Lzombie/iso/Vector2;Z)Lzombie/iso/Vector2;", at = @At("RETURN"))
    private Vector2 plz$matchStride(Vector2 result, @Local(argsOnly = true) boolean reset) {
        PLZAnimalForm.matchStride(this.character, result, reset);
        return result;
    }

    @Unique
    private void plz$applyBoneScale() {
        if (!PLZBoneScale.isActive()) {
            this.plz$armState.clearAngles();
            return;
        }

        String user;
        if (this.character instanceof IsoPlayer player) {
            user = player.getUsername();
        } else if (this.character == null && this.plz$previewKey != null) {
            user = this.plz$previewKey;
        } else {
            return;
        }

        int version = PLZBoneScale.version();
        if (user != this.plz$rigUser || version != this.plz$rigVersion) {
            this.plz$rig = PLZBoneScale.rigFor(user);
            this.plz$rigUser = user;
            this.plz$rigVersion = version;
        }

        PLZBoneScale.Rig rig = this.plz$rig;
        if (rig == null || this.skinningData == null || this.boneTransforms == null) {
            this.plz$armState.clearAngles();
            return;
        }

        int count = this.modelTransforms.length;
        if (this.boneTransforms.length < count) {
            return;
        }

        if (this.plz$layout == null || !this.plz$layout.isFor(this.skinningData, count)) {
            String[] names = new String[count];
            int[] parents = new int[count];
            boolean hasBind = this.skinningData.bindPose != null && this.skinningData.bindPose.size() >= count;
            Matrix4f[] bind = hasBind ? new Matrix4f[count] : null;
            this.plz$local = new Matrix4f[count];
            for (int boneIdx = 0; boneIdx < count; boneIdx++) {
                SkinningBone bone = this.skinningData.getBoneAt(boneIdx);
                names[boneIdx] = bone == null ? null : bone.name;
                parents[boneIdx] = bone == null || bone.parent == null ? -1 : bone.parent.index;
                if (hasBind) {
                    bind[boneIdx] = this.getBindPoseBoneModelTransform(boneIdx, new Matrix4f());
                }
                this.plz$local[boneIdx] = new Matrix4f();
            }
            this.plz$layout = new PLZBoneScale.Layout(this.skinningData, names, parents, bind);
        }

        for (int boneIdx = 0; boneIdx < count; boneIdx++) {
            this.boneTransforms[boneIdx].getMatrix(this.plz$local[boneIdx]);
        }

        boolean clear = PLZArmClearance.wants(rig) && this.plz$armBind(count);
        if (clear) {
            this.plz$armState.captureVanilla(this.modelTransforms);
        }

        PLZBoneScale.compose(this.plz$layout, rig, this.plz$local, this.modelTransforms);
        if (clear) {
            PLZArmClearance.apply(this.plz$armState, this.modelTransforms);
        } else {
            this.plz$armState.clearAngles();
        }
    }

    @Unique
    private boolean plz$armBind(int count) {
        boolean female = this.character != null ? this.character.isFemale() : this.plz$previewFemale;
        if (this.plz$armLayout == null || !this.plz$armLayout.isFor(this.skinningData, count, female)) {
            String[] names = new String[count];
            int[] parents = new int[count];
            boolean hasOffsets = this.skinningData.boneOffset != null && this.skinningData.boneOffset.size() >= count;
            Matrix4f[] offsets = hasOffsets ? new Matrix4f[count] : null;
            for (int boneIdx = 0; boneIdx < count; boneIdx++) {
                SkinningBone bone = this.skinningData.getBoneAt(boneIdx);
                names[boneIdx] = bone == null ? null : bone.name;
                parents[boneIdx] = bone == null || bone.parent == null ? -1 : bone.parent.index;
                if (hasOffsets) {
                    offsets[boneIdx] = this.skinningData.boneOffset.get(boneIdx);
                }
            }
            this.plz$armLayout = new PLZArmClearance.Layout(this.skinningData, names, parents, offsets, female);
            this.plz$armState.bind(this.plz$armLayout);
        }
        return this.plz$armLayout.ok();
    }

    @Unique
    private boolean plz$ikBind() {
        if (this.skinningData == null || this.modelTransforms == null) {
            return false;
        }

        int count = this.modelTransforms.length;
        if (this.plz$ikRig == null || !this.plz$ikRig.isFor(this.skinningData, count)) {
            String[] names = new String[count];
            int[] parents = new int[count];
            for (int boneIdx = 0; boneIdx < count; boneIdx++) {
                SkinningBone bone = this.skinningData.getBoneAt(boneIdx);
                names[boneIdx] = bone == null ? null : bone.name;
                parents[boneIdx] = bone == null || bone.parent == null ? -1 : bone.parent.index;
            }
            this.plz$ikRig = new PLZGrappleIK.Rig(this.skinningData, names, parents);
        }

        this.plz$ikPose.bind(this.plz$ikRig);
        return true;
    }

    @Unique
    private void plz$applyGrappleIK() {
        IsoGameCharacter chr = this.character;
        long now = System.currentTimeMillis();
        this.plz$ikPose.captureDrawn(this.modelTransforms, chr.getX(), chr.getY(), chr.getZ(), this.getRenderedAngle(), now);

        IsoGameCharacter partner = PLZGrappleIK.partnerOf(chr);
        AnimationPlayer other = partner == null ? null : partner.getAnimationPlayer();
        if (other == null || other == (Object) this) {
            return;
        }

        PLZGrappleIK.Pose otherPose = ((PLZAnimationPlayerAccess) (Object) other).plzIkPose();
        if (!otherPose.fresh(now)) {
            return;
        }

        boolean holder = chr.isGrappling();
        float[] cfg = PLZGrappleIK.nodeConfig(holder ? chr.getSharedGrappleAnimNode() : partner.getSharedGrappleAnimNode());
        if (cfg[holder ? PLZGrappleIK.CFG_HOLDER : PLZGrappleIK.CFG_HELD] <= 0.0F) {
            return;
        }

        float spacing = PLZGrappleEdit.spacing(holder ? chr : partner);
        PLZGrappleIK.apply(this.plz$ikPose, otherPose, cfg[PLZGrappleIK.CFG_NEAR], cfg[PLZGrappleIK.CFG_FAR], holder,
            cfg[PLZGrappleIK.CFG_GRIPS] > 0.0F, spacing, cfg[PLZGrappleIK.CFG_POSED_HELD] > 0.0F, this.modelTransforms, now);
    }
}
