package plz.mixin.client;

import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import zombie.core.textures.ImageData;
import zombie.core.utils.WrappedBuffer;

@Mixin(ImageData.class)
abstract class ImageDataMixin {
    // Vanilla leaks this; a scoreboard refresh misses per player and every leak counts toward TextureIDAssetManager's 50 MB load gate.
    @Inject(method = "createSteamAvatar", at = @At(value = "RETURN", ordinal = 0))
    private static void plz$disposeFailedAvatar(long steamID, CallbackInfoReturnable<ImageData> cir, @Local WrappedBuffer data) {
        data.dispose();
    }
}
