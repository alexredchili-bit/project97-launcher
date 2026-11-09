package plz.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zombie.worldMap.WorldMapRenderer;

@Mixin(WorldMapRenderer.class)
abstract class WorldMapRendererMixin {
    @Unique
    private WorldMapRenderer.WorldMapBooleanOption plz$isometricNoTilt;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void plz$addNoTiltOption(CallbackInfo ci) {
        this.plz$isometricNoTilt = ((WorldMapRenderer) (Object) this).new WorldMapBooleanOption("IsometricNoTilt", false);
    }

    @ModifyArg(
        method = "calcMatrices",
        at = @At(value = "INVOKE", target = "Lorg/joml/Matrix4f;rotateXYZ(FFF)Lorg/joml/Matrix4f;"),
        index = 0
    )
    private float plz$tilt(float tilt) {
        return this.plz$isometricNoTilt.getValue() ? 0.0F : tilt;
    }
}
