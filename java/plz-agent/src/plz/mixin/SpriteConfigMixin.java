package plz.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zombie.entity.components.spriteconfig.SpriteConfig;
import zombie.entity.components.spriteconfig.SpriteConfigManager;
import zombie.plz.PLZFixes;

@Mixin(SpriteConfig.class)
abstract class SpriteConfigMixin {
    @Shadow
    private SpriteConfigManager.TileInfo tileInfo;

    @Shadow
    private SpriteConfigManager.FaceInfo faceInfo;

    @Shadow
    private SpriteConfigManager.ObjectInfo objectInfo;

    // PLZ: receiveSyncEntity re-runs this every sync, and a door or window in a non-default state cannot resolve its face.
    @Inject(method = "onAddedToOwner", at = @At("HEAD"), cancellable = true)
    private void plz$keepResolvedInfo(CallbackInfo ci) {
        if (PLZFixes.on(PLZFixes.SPRITE_CONFIG_IDEMPOTENT) && this.objectInfo != null && this.faceInfo != null && this.tileInfo != null) {
            PLZFixes.hit(PLZFixes.SPRITE_CONFIG_IDEMPOTENT);
            ci.cancel();
        }
    }
}
