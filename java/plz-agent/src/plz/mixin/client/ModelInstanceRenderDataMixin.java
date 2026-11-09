package plz.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zombie.core.skinnedmodel.advancedanimation.AnimatedModel;
import zombie.core.skinnedmodel.model.ModelInstance;
import zombie.core.skinnedmodel.model.ModelInstanceRenderData;
import zombie.core.skinnedmodel.model.VehicleSubModelInstance;
import zombie.core.textures.Texture;
import zombie.plz.PLZWornOffsetRender;
import zombie.plz.access.PLZVehicleSubModelInstanceAccess;
import zombie.util.Type;

@Mixin(ModelInstanceRenderData.class)
abstract class ModelInstanceRenderDataMixin extends AnimatedModel.AnimatedModelInstanceRenderData {
    @Unique
    private PLZWornOffsetRender plz$worn;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void plz$init(CallbackInfo ci) {
        this.plz$worn = new PLZWornOffsetRender();
    }

    @Inject(
        method = "initModel",
        at = @At(
            value = "FIELD",
            target = "Lzombie/core/skinnedmodel/model/ModelInstanceRenderData;tex:Lzombie/core/textures/Texture;",
            opcode = org.objectweb.asm.Opcodes.PUTFIELD,
            shift = At.Shift.AFTER
        )
    )
    private void plz$plateTexture(ModelInstance modelInstance, AnimatedModel.AnimatedModelInstanceRenderData parent, CallbackInfo ci) {
        VehicleSubModelInstance sub = Type.tryCastTo(modelInstance, VehicleSubModelInstance.class);
        if (sub != null) {
            Texture tex = ((PLZVehicleSubModelInstanceAccess)(Object)sub).plzPlateTexture();
            if (tex != null) {
                sub.tex = tex;
                this.tex = tex;
            }
        }
    }

    @Inject(method = "initModel", at = @At("TAIL"))
    private void plz$wornOffset(ModelInstance modelInstance, AnimatedModel.AnimatedModelInstanceRenderData parent, CallbackInfo ci) {
        this.plz$worn.applySkinned(this);
    }

    @Override
    public AnimatedModel.AnimatedModelInstanceRenderData transformToParent(AnimatedModel.AnimatedModelInstanceRenderData parentData) {
        AnimatedModel.AnimatedModelInstanceRenderData result = super.transformToParent(parentData);
        this.plz$worn.applyStatic(this, parentData);
        return result;
    }
}
