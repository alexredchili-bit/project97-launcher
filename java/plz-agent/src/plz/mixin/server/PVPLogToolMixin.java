package plz.mixin.server;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zombie.characters.IsoPlayer;
import zombie.network.PVPLogTool;
import zombie.plz.PLZConductWatch;

@Mixin(PVPLogTool.class)
abstract class PVPLogToolMixin {
    // PLZ: vanilla's copy of this lands in pvp.txt, which ZLogger empties once it passes 10MB.
    @Inject(method = "logKill", at = @At("TAIL"))
    private static void plz$recordKill(IsoPlayer wielder, IsoPlayer target, CallbackInfo ci) {
        PLZConductWatch.onKill(wielder, target);
    }

    // PLZ: the durable copy. Zombie and animal hits reach here too and are dropped inside the hook.
    @Inject(method = "logCombat", at = @At("TAIL"))
    private static void plz$recordCombat(IsoPlayer wielder, IsoPlayer target, String weapon, float damage, CallbackInfo ci) {
        PLZConductWatch.onCombat(wielder.getUsername(), target.getUsername(), weapon, damage, wielder.getX(), wielder.getY(), wielder.getZ());
    }
}
