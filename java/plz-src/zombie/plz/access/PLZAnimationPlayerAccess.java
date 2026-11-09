package zombie.plz.access;

import zombie.plz.PLZArmClearance;
import zombie.plz.PLZGrappleIK;

public interface PLZAnimationPlayerAccess {
    void plzSetPreviewKey(String key);

    void plzSetPreviewFemale(boolean female);

    PLZArmClearance.State plzArmState();

    PLZGrappleIK.Pose plzIkPose();
}
