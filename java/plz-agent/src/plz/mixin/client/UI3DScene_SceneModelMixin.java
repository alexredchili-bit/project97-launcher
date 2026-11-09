package plz.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import zombie.core.skinnedmodel.model.Model;
import zombie.plz.access.PLZSceneModelAccess;
import zombie.scripting.objects.ModelScript;

@Mixin(targets = "zombie.vehicles.UI3DScene$SceneModel")
abstract class UI3DScene_SceneModelMixin implements PLZSceneModelAccess {
    @Shadow
    ModelScript modelScript;

    @Shadow
    Model model;

    @Override
    public ModelScript plzModelScript() {
        return this.modelScript;
    }

    @Override
    public Model plzModel() {
        return this.model;
    }
}
