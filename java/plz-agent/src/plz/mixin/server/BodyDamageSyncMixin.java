package plz.mixin.server;

import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zombie.characters.IsoPlayer;
import zombie.debug.DebugLog;
import zombie.network.BodyDamageSync;
import zombie.network.GameServer;
import zombie.plz.PLZFixes;

@Mixin(BodyDamageSync.class)
abstract class BodyDamageSyncMixin {
    // An unresolved patient NPEs below; an unresolved recipient leaks an Updater that ticks forever and sends nothing.
    @Inject(
        method = "startSendingUpdates",
        at = @At(value = "NEW", target = "zombie/network/BodyDamageSync$Updater"),
        cancellable = true
    )
    private void plz$refuseUnresolved(short localId, short remoteID, CallbackInfo ci, @Local IsoPlayer player) {
        if (PLZFixes.on(PLZFixes.BODY_DAMAGE_SYNC)) {
            if (player == null || GameServer.IDToPlayerMap.get(remoteID) == null) {
                PLZFixes.hit(PLZFixes.BODY_DAMAGE_SYNC);
                DebugLog.log("PLZFixes: refusing BodyDamageSync updater local=" + localId + " remote=" + remoteID + " (unresolved player)");
                ci.cancel();
            }
        }
    }
}
