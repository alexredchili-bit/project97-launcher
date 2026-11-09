package plz.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zombie.core.skinnedmodel.ModelManager;
import zombie.core.skinnedmodel.model.Model;
import zombie.core.skinnedmodel.model.ModelMesh;
import zombie.plz.PLZModelMeshReload;
import zombie.plz.access.PLZModelManagerAccess;

@Mixin(ModelManager.class)
abstract class ModelManagerMixin implements PLZModelManagerAccess {
    @Shadow
    public Model maleModel;

    @Shadow
    public Model femaleModel;

    @Shadow
    private boolean created;

    @Shadow
    private ModelMesh animModel;

    @Shadow
    private boolean isLoadingAnimationMeshes() {
        throw new AssertionError();
    }

    @Override
    public boolean plzIsLoadingAnimationMeshes() {
        return this.isLoadingAnimationMeshes();
    }

    // Core.ResetLua re-runs loadModAnimations once the server's mods are loaded, but nothing rebuilds the body meshes.
    @Inject(method = "setActiveAnimations", at = @At("TAIL"))
    private void plz$reloadBodyModels(CallbackInfo ci) {
        PLZModelMeshReload.reloadBodyModels(this.created, this.maleModel, this.femaleModel, this.animModel);
    }

    @Inject(method = "initAnimationMeshes", at = @At(value = "RETURN", ordinal = 0))
    private void plz$waitForAnimationMeshesOnReload(boolean bReloading, CallbackInfo ci) {
        if (bReloading) {
            PLZModelMeshReload.waitForAnimationMeshes(this);
        }
    }
}
