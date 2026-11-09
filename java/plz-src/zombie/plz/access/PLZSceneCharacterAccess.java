package zombie.plz.access;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import zombie.characters.IsoGameCharacter;
import zombie.core.skinnedmodel.advancedanimation.AnimatedModel;

public interface PLZSceneCharacterAccess {
    AnimatedModel plzAnimatedModel();

    Matrix4f plzBoneMatrix(String boneName, Matrix4f mat);

    void plzSetClearDepthBuffer(boolean clear);

    String plzArmsRoot();

    void plzSetArmsRoot(String root);

    void plzSetPoseSource(IsoGameCharacter source);

    void plzSetPoseWeight(float weight);

    Vector3f plzLook();

    Vector3f plzLookAnchor();

    boolean plzFollowing();

    void plzSetFollowing(boolean following);

    Matrix4f plzAimHeld();

    void plzSetAimHeld(Matrix4f aimHeld);
}
