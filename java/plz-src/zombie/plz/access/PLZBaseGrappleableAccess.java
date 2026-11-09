package zombie.plz.access;

import zombie.core.skinnedmodel.IGrappleable;

public interface PLZBaseGrappleableAccess {
    void plzAttach(IGrappleable anchor, String slot);

    void plzDetach();

    IGrappleable plzGetAnchor();

    String plzGetAttachSlot();
}
