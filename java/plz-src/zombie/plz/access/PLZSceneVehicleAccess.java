package zombie.plz.access;

import org.joml.Vector3f;
import zombie.scripting.objects.VehicleScript;

public interface PLZSceneVehicleAccess {
    VehicleScript plzScript();

    Vector3f plzPaint();

    int plzSkinIndex();

    void plzSetSkinIndex(int skinIndex);

    int plzClampedSkinIndex();

    void plzResetModels();

    void plzSetVisibleParts(String spec);
}
