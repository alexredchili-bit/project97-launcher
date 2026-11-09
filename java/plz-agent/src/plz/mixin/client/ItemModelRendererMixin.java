package plz.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import zombie.core.skinnedmodel.model.ItemModelRenderer;
import zombie.core.skinnedmodel.shader.Shader;
import zombie.iso.IsoMovingObject;
import zombie.plz.PLZWorldItemLight;

@Mixin(ItemModelRenderer.class)
abstract class ItemModelRendererMixin {
    @Shadow
    @Final
    public Vector3f angle;

    @WrapOperation(
        method = "renderModel",
        at = @At(value = "INVOKE", target = "Lzombie/core/skinnedmodel/shader/Shader;setAmbient(FFF)V", ordinal = 0)
    )
    private void plz$ambientGain(Shader effect, float r, float g, float b, Operation<Void> original) {
        float gain = PLZWorldItemLight.getGain();
        original.call(effect, r * gain, g * gain, b * gain);
    }

    @WrapOperation(method = "renderModel", at = @At(value = "INVOKE", target = "Lorg/joml/Vector3f;rotateY(F)Lorg/joml/Vector3f;"))
    private Vector3f plz$orientLight(Vector3f pos, float radians, Operation<Vector3f> original) {
        PLZWorldItemLight.orientLight(pos, this.angle.x, this.angle.y, this.angle.z);
        return pos;
    }

    @WrapOperation(
        method = "renderModel",
        at = @At(
            value = "INVOKE",
            target = "Lzombie/core/skinnedmodel/shader/Shader;setLight(IFFFFFFFFFFFLzombie/iso/IsoMovingObject;)V",
            ordinal = 1
        )
    )
    private void plz$keyLightGain(
        Shader effect,
        int index,
        float x,
        float y,
        float z,
        float r,
        float g,
        float b,
        float rad,
        float animPlayerAngle,
        float offsetX,
        float offsetY,
        float offsetZ,
        IsoMovingObject instObject,
        Operation<Void> original
    ) {
        float gain = PLZWorldItemLight.getGain();
        original.call(effect, index, x, y, z, r * gain, g * gain, b * gain, rad, animPlayerAngle, offsetX, offsetY, offsetZ, instObject);
    }
}
