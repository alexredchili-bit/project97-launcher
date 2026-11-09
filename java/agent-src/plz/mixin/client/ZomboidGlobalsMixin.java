package plz.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zombie.ZomboidGlobals;
import zombie.plz.PLZJavaGuard;
import zombie.plz.PLZPatchStamp;

@Mixin(ZomboidGlobals.class)
public abstract class ZomboidGlobalsMixin {
    @Inject(method = "<clinit>", at = @At("HEAD"))
    private static void plz$stamp(CallbackInfo ci) {
        PLZPatchStamp.record("zombie.ZomboidGlobals");
        PLZJavaGuard.warm();
    }
}
