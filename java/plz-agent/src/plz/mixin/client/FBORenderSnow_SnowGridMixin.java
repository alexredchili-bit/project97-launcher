package plz.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import zombie.erosion.utils.Noise2D;
import zombie.plz.PLZSnowClear;

@Mixin(targets = "zombie.iso.fboRenderChunk.FBORenderSnow$SnowGrid")
abstract class FBORenderSnow_SnowGridMixin {
    @Shadow
    public int worldX;

    @Shadow
    public int worldY;

    // Infinity fails the vanilla "noise <= frac" test, which skips the tile exactly as the shadow's continue did.
    @WrapOperation(method = "init", at = @At(value = "INVOKE", target = "Lzombie/erosion/utils/Noise2D;layeredNoise(FF)F"))
    private float plz$noSnowOnClearedTile(
        Noise2D noise, float nx, float ny, Operation<Float> original,
        @Local(name = "level") int level, @Local(name = "x") int x, @Local(name = "y") int y
    ) {
        if (level == 0 && PLZSnowClear.isCleared(this.worldX + x, this.worldY + y)) {
            return Float.POSITIVE_INFINITY;
        }

        return original.call(noise, nx, ny);
    }
}
