package zombie.plz.access;

import zombie.core.skinnedmodel.model.Model;
import zombie.scripting.objects.ModelScript;

public interface PLZSceneModelAccess {
    ModelScript plzModelScript();

    Model plzModel();
}
