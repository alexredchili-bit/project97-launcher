package plz.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import zombie.characters.IsoPlayer;
import zombie.input.AimingReticle;
import zombie.plz.PLZHighFX;

@Mixin(AimingReticle.class)
abstract class AimingReticleMixin {
    @ModifyReturnValue(method = "getXA", at = @At("RETURN"))
    private static int plz$aimX(int raw, @Local(argsOnly = true) int playerIndex, @Local IsoPlayer player) {
        return PLZHighFX.aimX(player, playerIndex, raw);
    }

    @ModifyReturnValue(method = "getYA", at = @At("RETURN"))
    private static int plz$aimY(int raw, @Local(argsOnly = true) int playerIndex, @Local IsoPlayer player) {
        return PLZHighFX.aimY(player, playerIndex, raw);
    }
}
