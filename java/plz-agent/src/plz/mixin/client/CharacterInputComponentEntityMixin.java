package plz.mixin.client;

import org.lwjgl.util.vector.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import zombie.characters.CharacterInputComponentEntity;
import zombie.characters.IsoGameCharacter;
import zombie.characters.IsoPlayer;
import zombie.characters.NetworkPlayerAI;
import zombie.characters.component.CharacterInputComponent;
import zombie.core.skinnedmodel.animation.AnimationMultiTrack;
import zombie.core.skinnedmodel.animation.AnimationPlayer;
import zombie.core.skinnedmodel.animation.AnimationTrack;
import zombie.core.skinnedmodel.model.Model;
import zombie.iso.Vector3;
import zombie.plz.PLZArmClearance;
import zombie.plz.PLZBoneScale;
import zombie.plz.PLZGrappleEdit;
import zombie.plz.PLZGrappleIK;
import zombie.plz.PLZVoice;
import zombie.plz.PLZVoiceChannels;
import zombie.plz.access.PLZAnimationPlayerAccess;
import zombie.plz.access.PLZBaseGrappleableAccess;
import zombie.plz.access.PLZCharacterInputComponentAccess;
import zombie.plz.access.PLZNetworkPlayerAIAccess;

@Mixin(CharacterInputComponentEntity.class)
public interface CharacterInputComponentEntityMixin {
    default boolean isAllowAttack() {
        CharacterInputComponent c = ((CharacterInputComponentEntity) (Object) this).getCharacterInputComponent();
        return c != null && ((PLZCharacterInputComponentAccess) c).isAllowAttack();
    }

    default void setAllowAttack(boolean allowAttack) {
        CharacterInputComponent c = ((CharacterInputComponentEntity) (Object) this).getCharacterInputComponent();
        if (c != null) {
            ((PLZCharacterInputComponentAccess) c).setAllowAttack(allowAttack);
        }
    }

    default void setPositionUpdatePeriod(long ms) {
        if (this instanceof IsoPlayer player) {
            NetworkPlayerAI networkAI = player.getNetworkCharacterAI();
            if (networkAI != null) {
                ((PLZNetworkPlayerAIAccess) networkAI).setMinUpdatePeriod(ms);
            }
        }
    }

    default boolean isMoveForwardKeyDown() {
        CharacterInputComponent c = ((CharacterInputComponentEntity) (Object) this).getCharacterInputComponent();
        return c != null && c.isForwardKeyDown();
    }

    default boolean isMoveBackwardKeyDown() {
        CharacterInputComponent c = ((CharacterInputComponentEntity) (Object) this).getCharacterInputComponent();
        return c != null && c.isBackwardKeyDown();
    }

    default boolean isMoveLeftKeyDown() {
        CharacterInputComponent c = ((CharacterInputComponentEntity) (Object) this).getCharacterInputComponent();
        return c != null && c.isLeftKeyDown();
    }

    default boolean isMoveRightKeyDown() {
        CharacterInputComponent c = ((CharacterInputComponentEntity) (Object) this).getCharacterInputComponent();
        return c != null && c.isRightKeyDown();
    }

    default void forcePositionUpdate() {
        if (this instanceof IsoPlayer player) {
            NetworkPlayerAI networkAI = player.getNetworkCharacterAI();
            if (networkAI != null) {
                networkAI.needToUpdate();
            }
        }
    }

    default void setVoiceMode(int mode) {
        if (this instanceof IsoPlayer player) {
            PLZVoice.setMode(player.getIndex(), mode);
            PLZVoiceChannels.republish();
        }
    }

    default int getVoiceMode() {
        if (this instanceof IsoPlayer player) {
            return PLZVoice.getMode(player.getIndex());
        }
        return PLZVoice.MODE_NORMAL;
    }

    // Lua cannot reach zombie.plz; the receiver is ignored, and only scalars cross because Kahlua never marshals a table into a collection.
    default void plzGrappleSet(String node, float forward, float yaw, String behaviour) {
        PLZGrappleEdit.set(node, forward, yaw, behaviour);
    }

    default void plzGrappleSetPhase(String node, float phase) {
        PLZGrappleEdit.setPhase(node, phase);
    }

    default void plzGrappleSetSide(String node, float side) {
        PLZGrappleEdit.setSide(node, side);
    }

    default void plzGrappleSetSpacing(String node, float gain) {
        PLZGrappleEdit.setSpacing(node, gain);
    }

    default float plzGrappleGetSpacing(String node) {
        return PLZGrappleEdit.spacingOf(node);
    }

    // kind is "forward", "yaw" or "side"; fraction is a point in the clip, 0..1.
    default void plzGrappleAddKey(String node, String kind, float fraction, float value) {
        PLZGrappleEdit.addKey(node, kind, fraction, value);
    }

    default void plzGrappleClearKeys(String node) {
        PLZGrappleEdit.clearKeys(node);
    }

    default void plzGrappleClear(String node) {
        PLZGrappleEdit.clear(node);
    }

    default void plzGrappleClearAll() {
        PLZGrappleEdit.clearAll();
    }

    // Hold every grapple at one point in its clip. Anything below zero releases the hold.
    default void plzGrappleSetForcedFraction(float fraction) {
        PLZGrappleEdit.setForcedFraction(fraction);
    }

    default float plzGrappleGetForcedFraction() {
        return PLZGrappleEdit.getForcedFraction();
    }

    default boolean plzGrappleHas(String node) {
        return PLZGrappleEdit.has(node);
    }

    // NaN when the node carries no override, which is how the editor tells unset from zero.
    default float plzGrappleGetForward(String node) {
        return PLZGrappleEdit.forwardOf(node);
    }

    default float plzGrappleGetYaw(String node) {
        return PLZGrappleEdit.yawOf(node);
    }

    default float plzGrappleGetSide(String node) {
        return PLZGrappleEdit.sideOf(node);
    }

    default float plzGrappleGetPhase(String node) {
        return PLZGrappleEdit.phaseOf(node);
    }

    default String plzGrappleGetBehaviour(String node) {
        return PLZGrappleEdit.behaviourOf(node);
    }

    default int plzGrappleKeyCount(String node, String kind) {
        return PLZGrappleEdit.keyCountOf(node, kind);
    }

    default float plzGrappleKeyTime(String node, String kind, int index) {
        return PLZGrappleEdit.keyTimeAt(node, kind, index);
    }

    default float plzGrappleKeyValue(String node, String kind, int index) {
        return PLZGrappleEdit.keyValueAt(node, kind, index);
    }

    // Unlike the registry calls above, the receiver matters: it is the character being attached.
    default void plzAttachTo(IsoGameCharacter anchor, String slot) {
        if (this instanceof IsoGameCharacter self) {
            ((PLZBaseGrappleableAccess) self.getWrappedGrappleable()).plzAttach(anchor, slot);
        }
    }

    default void plzDetach() {
        if (this instanceof IsoGameCharacter self) {
            ((PLZBaseGrappleableAccess) self.getWrappedGrappleable()).plzDetach();
        }
    }

    default IsoGameCharacter plzGetAnchor() {
        return this instanceof IsoGameCharacter self && ((PLZBaseGrappleableAccess) self.getWrappedGrappleable()).plzGetAnchor() instanceof IsoGameCharacter anchor ? anchor : null;
    }

    default String plzGetAttachSlot() {
        return this instanceof IsoGameCharacter self ? ((PLZBaseGrappleableAccess) self.getWrappedGrappleable()).plzGetAttachSlot() : "";
    }

    // The registry key this attachment reads right now, which is what the editor must write.
    default String plzGetAttachKey() {
        IsoGameCharacter anchor = this.plzGetAnchor();
        if (anchor == null) {
            return "";
        }
        String anchorNode = anchor.isGrappling() ? anchor.getSharedGrappleAnimNode() : "";
        return PLZGrappleEdit.attachKey(anchorNode, this.plzGetAttachSlot());
    }

    // Keyed by account, not character: a client knows a player by username long before it has their IsoPlayer.
    default void plzBoneScaleSet(String username, String group, float value) {
        PLZBoneScale.set(username, group, value);
    }

    default void plzBoneScaleSetAxes(String username, String group, float x, float y, float z) {
        PLZBoneScale.setAxes(username, group, x, y, z);
    }

    default float plzBoneScaleGetAxis(String username, String group, int axis) {
        return PLZBoneScale.getAxis(username, group, axis);
    }

    default void plzBoneScaleSetOverall(String username, float value) {
        PLZBoneScale.setOverall(username, value);
    }

    default float plzBoneScaleGetOverall(String username) {
        return PLZBoneScale.getOverall(username);
    }

    default void plzBoneScaleNudge(String username, float x, float y, float z) {
        PLZBoneScale.setNudge(username, x, y, z);
    }

    default float plzBoneScaleGet(String username, String group) {
        return PLZBoneScale.get(username, group);
    }

    default void plzBoneScaleClear(String username) {
        PLZBoneScale.clear(username);
    }

    default void plzBoneScaleClearAll() {
        PLZBoneScale.clearAll();
    }

    default boolean plzBoneScaleAllowed(String username) {
        return PLZBoneScale.isAllowed(username);
    }

    default void plzBoneScaleSetHipSpacing(String username, float value) {
        PLZBoneScale.setHipSpacing(username, value);
    }

    default float plzBoneScaleGetHipSpacing(String username) {
        return PLZBoneScale.getHipSpacing(username);
    }

    default String plzBoneScalePreviewKey() {
        return PLZBoneScale.PREVIEW_KEY;
    }

    default boolean plzBoneScaleBindPreview(Object ui3dModel, String key) {
        return PLZBoneScale.bindPreview(ui3dModel, key);
    }

    default void plzGrappleIkSetEnabled(boolean on) {
        PLZGrappleIK.setEnabled(on);
    }

    default boolean plzGrappleIkIsEnabled() {
        return PLZGrappleIK.isEnabled();
    }

    default void plzGrappleIkNode(String node, boolean holder, boolean held, float near, float far) {
        PLZGrappleIK.setNode(node, holder, held, near, far);
    }

    default void plzGrappleIkGrips(String node, boolean grips) {
        PLZGrappleIK.setGrips(node, grips);
    }

    default void plzGrappleIkPosedHeld(String node, boolean posed) {
        PLZGrappleIK.setPosedHeld(node, posed);
    }

    default void plzGrappleIkClearNodes() {
        PLZGrappleIK.clearNodes();
    }

    default void plzGrappleIkFreeze(float fraction) {
        PLZGrappleIK.setFreezeAt(fraction);
    }

    // Last pass on this character: side 0 left, 1 right; field per PLZGrappleIK.INFO_*.
    default float plzGrappleIkInfo(int side, int field) {
        return this instanceof IsoGameCharacter character && character.getAnimationPlayer() != null
            ? ((PLZAnimationPlayerAccess) (Object) character.getAnimationPlayer()).plzIkPose().info(side, field)
            : 0.0F;
    }

    default String plzGrappleIkAnchor(int side) {
        return this instanceof IsoGameCharacter character && character.getAnimationPlayer() != null
            ? ((PLZAnimationPlayerAccess) (Object) character.getAnimationPlayer()).plzIkPose().anchor(side)
            : null;
    }

    default void plzArmClearanceSetEnabled(boolean on) {
        PLZArmClearance.setEnabled(on);
    }

    default boolean plzArmClearanceIsEnabled() {
        return PLZArmClearance.isEnabled();
    }

    default String plzArmClearanceStatus() {
        return this instanceof IsoGameCharacter character && character.getAnimationPlayer() != null
            ? ((PLZAnimationPlayerAccess) (Object) character.getAnimationPlayer()).plzArmState().status()
            : "no animation player";
    }

    // Radians the arm was last turned out at the shoulder; side 0 left, 1 right.
    default float plzArmClearanceAngle(int side) {
        return this instanceof IsoGameCharacter character && character.getAnimationPlayer() != null
            ? ((PLZAnimationPlayerAccess) (Object) character.getAnimationPlayer()).plzArmState().angle(side)
            : 0.0F;
    }

    default void plzArmClearanceSetElbowReturn(boolean on) {
        PLZArmClearance.setElbowReturn(on);
    }

    default boolean plzArmClearanceIsElbowReturn() {
        return PLZArmClearance.isElbowReturn();
    }

    // Radians the forearm was last turned back at the elbow; side 0 left, 1 right.
    default float plzArmClearanceElbowAngle(int side) {
        return this instanceof IsoGameCharacter character && character.getAnimationPlayer() != null
            ? ((PLZAnimationPlayerAccess) (Object) character.getAnimationPlayer()).plzArmState().elbowAngle(side)
            : 0.0F;
    }

    // World position of a bone on axis 0/1/2; drawn = the PLZ-resized pose, otherwise the vanilla animated one. NaN if unknown.
    default float plzBoneWorld(String boneName, int axis, boolean drawn) {
        if (!(this instanceof IsoGameCharacter character)) {
            return Float.NaN;
        }

        AnimationPlayer player = character.getAnimationPlayer();
        if (player == null || !player.isReady()) {
            return Float.NaN;
        }

        int boneIdx = player.getSkinningBoneIndex(boneName, -1);
        if (boneIdx < 0) {
            return Float.NaN;
        }

        Vector3 pos = new Vector3();
        if (drawn) {
            Model.boneToWorldCoords(character, boneIdx, pos);
        } else {
            Matrix4f model = player.getBoneModelTransform(boneIdx, new Matrix4f());
            pos.set(model.m03, model.m13, model.m23);
            Model.vectorToWorldCoords(character, pos);
        }

        return axis == 0 ? pos.x : (axis == 1 ? pos.y : pos.z);
    }

    // How far (0-1) the newest track whose clip name contains this text is through its clip; -1 if none plays.
    default float plzAnimTrackFraction(String clipContains) {
        if (!(this instanceof IsoGameCharacter character) || clipContains == null) {
            return -1.0F;
        }

        AnimationPlayer player = character.getAnimationPlayer();
        if (player == null || !player.isReady()) {
            return -1.0F;
        }

        AnimationMultiTrack multiTrack = player.getMultiTrack();
        if (multiTrack == null) {
            return -1.0F;
        }

        for (int i = multiTrack.getTrackCount() - 1; i >= 0; i--) {
            AnimationTrack track = multiTrack.getTrackAt(i);
            if (track != null && track.hasClip() && track.getName().contains(clipContains)) {
                return track.getCurrentTimeFraction();
            }
        }

        return -1.0F;
    }

    // Also the patch probe: an unpatched client throws here rather than answering.
    default int plzBoneScaleGroupCount() {
        return PLZBoneScale.groupCount();
    }

    default String plzBoneScaleGroupAt(int index) {
        return PLZBoneScale.groupAt(index);
    }

    default float plzBoneScaleMinScale() {
        return PLZBoneScale.MIN_SCALE;
    }

    default float plzBoneScaleMaxScale() {
        return PLZBoneScale.MAX_SCALE;
    }

    default float plzBoneScaleMaxNudge() {
        return PLZBoneScale.MAX_NUDGE;
    }

    // Scale and offset are separate calls: different sliders edit them, and a six-float signature is a marshalling risk.
    default void plzBoneScaleSetPropScale(String username, String prop, float x, float y, float z) {
        PLZBoneScale.setPropScale(username, prop, x, y, z);
    }

    default void plzBoneScaleSetPropOffset(String username, String prop, float x, float y, float z) {
        PLZBoneScale.setPropOffset(username, prop, x, y, z);
    }

    default float plzBoneScaleGetPropScale(String username, String prop, int axis) {
        return PLZBoneScale.getPropScale(username, prop, axis);
    }

    default float plzBoneScaleGetPropOffset(String username, String prop, int axis) {
        return PLZBoneScale.getPropOffset(username, prop, axis);
    }

    default int plzBoneScalePropCount() {
        return PLZBoneScale.propCount();
    }

    default String plzBoneScalePropAt(int index) {
        return PLZBoneScale.propAt(index);
    }

    default float plzBoneScaleMaxPropOffset() {
        return PLZBoneScale.MAX_PROP_OFFSET;
    }

    // Keyed by body location, not by item; see PLZBoneScale.LOCATIONS.
    default void plzBoneScaleSetWornOffset(String username, String location, float x, float y, float z) {
        PLZBoneScale.setWornOffset(username, location, x, y, z);
    }

    default float plzBoneScaleGetWornOffset(String username, String location, int axis) {
        return PLZBoneScale.getWornOffset(username, location, axis);
    }

    default void plzBoneScaleSetWornScale(String username, String location, float x, float y, float z) {
        PLZBoneScale.setWornScale(username, location, x, y, z);
    }

    default float plzBoneScaleGetWornScale(String username, String location, int axis) {
        return PLZBoneScale.getWornScale(username, location, axis);
    }

    default int plzBoneScaleLocationCount() {
        return PLZBoneScale.locationCount();
    }

    default String plzBoneScaleLocationAt(int index) {
        return PLZBoneScale.locationAt(index);
    }

    default float plzBoneScaleMaxWornOffset() {
        return PLZBoneScale.MAX_WORN_OFFSET;
    }
}
