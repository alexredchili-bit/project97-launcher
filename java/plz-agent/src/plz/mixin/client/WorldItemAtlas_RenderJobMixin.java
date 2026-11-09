package plz.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalFloatRef;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import zombie.core.skinnedmodel.shader.Shader;
import zombie.iso.IsoMovingObject;
import zombie.plz.PLZWorldItemLight;

@Mixin(targets = "zombie.core.skinnedmodel.model.WorldItemAtlas$RenderJob")
abstract class WorldItemAtlas_RenderJobMixin {
    @WrapOperation(
        method = "renderModel",
        at = @At(value = "INVOKE", target = "Lzombie/core/skinnedmodel/shader/Shader;setAmbient(FFF)V")
    )
    private void plz$scaleAmbient(
        Shader effect, float r, float g, float b, Operation<Void> original, @Share("plzGain") LocalFloatRef plzGain
    ) {
        float gain = PLZWorldItemLight.getGain();
        plzGain.set(gain);
        original.call(effect, r * gain, g * gain, b * gain);
    }

    @ModifyExpressionValue(
        method = "renderModel",
        at = @At(value = "FIELD", target = "Lzombie/core/skinnedmodel/model/WorldItemAtlas$ItemParams;angle:Lorg/joml/Vector3f;")
    )
    private Vector3f plz$captureAngle(Vector3f angle, @Share("plzAngle") LocalRef<Vector3f> plzAngle) {
        plzAngle.set(angle);
        return angle;
    }

    @WrapOperation(
        method = "renderModel",
        at = @At(value = "INVOKE", target = "Lorg/joml/Vector3f;rotateY(F)Lorg/joml/Vector3f;")
    )
    private Vector3f plz$orientLight(Vector3f xyz, float radians, Operation<Vector3f> original, @Share("plzAngle") LocalRef<Vector3f> plzAngle) {
        Vector3f angle = plzAngle.get();
        PLZWorldItemLight.orientLight(xyz, angle.x, angle.y, angle.z);
        return xyz;
    }

    @WrapOperation(
        method = "renderModel",
        at = @At(
            value = "INVOKE",
            target = "Lzombie/core/skinnedmodel/shader/Shader;setLight(IFFFFFFFFFFFLzombie/iso/IsoMovingObject;)V",
            ordinal = 1
        )
    )
    private void plz$scaleKeyLight(
        Shader effect,
        int index,
        float x,
        float y,
        float z,
        float r,
        float g,
        float b,
        float radius,
        float angleOffset,
        float dx,
        float dy,
        float dz,
        IsoMovingObject object,
        Operation<Void> original,
        @Share("plzGain") LocalFloatRef plzGain
    ) {
        float gain = plzGain.get();
        original.call(effect, index, x, y, z, r * gain, g * gain, b * gain, radius, angleOffset, dx, dy, dz, object);
    }
}
