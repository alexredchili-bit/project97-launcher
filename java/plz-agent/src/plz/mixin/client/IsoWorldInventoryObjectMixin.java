package plz.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import plz.agent.LuaPublic;
import zombie.core.opengl.Shader;
import zombie.core.textures.ColorInfo;
import zombie.iso.objects.IsoWorldInventoryObject;
import zombie.plz.PLZFixes;
import zombie.plz.PLZWorldItemLight;
import zombie.plz.PLZWorldItemSpriteGuard;

@Mixin(IsoWorldInventoryObject.class)
abstract class IsoWorldInventoryObjectMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void plz$restoreNullSprite(
        float x, float y, float z, ColorInfo col, boolean bDoChild, boolean bWallLightingPass, Shader shader, CallbackInfo ci
    ) {
        IsoWorldInventoryObject self = (IsoWorldInventoryObject) (Object) this;
        if (self.sprite == null && PLZFixes.on(PLZFixes.WORLD_ITEM_SPRITE_GUARD)) {
            PLZWorldItemSpriteGuard.restoreSprite(self);
        }
    }

    @Unique
    @LuaPublic
    private static void plzSetModelLightGain(float gain) {
        PLZWorldItemLight.setGain(gain);
    }

    @Unique
    @LuaPublic
    private static float plzGetModelLightGain() {
        return PLZWorldItemLight.getGain();
    }
}
