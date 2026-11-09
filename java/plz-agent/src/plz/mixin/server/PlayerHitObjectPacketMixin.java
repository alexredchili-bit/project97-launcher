package plz.mixin.server;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zombie.core.raknet.UdpConnection;
import zombie.network.fields.hit.Thumpable;
import zombie.network.packets.hit.PlayerHit;
import zombie.network.packets.hit.PlayerHitObjectPacket;
import zombie.plz.PLZConductWatch;

@Mixin(PlayerHitObjectPacket.class)
abstract class PlayerHitObjectPacketMixin extends PlayerHit {
    @Shadow
    @Final
    protected Thumpable thumpable;

    // map.txt is a ZLogger file emptied at 10MB; PLZConductWatch keeps the same line for good.
    @Inject(
        method = "log",
        at = @At(value = "INVOKE", target = "Lzombie/core/logger/ZLogger;write(Ljava/lang/String;)V", shift = At.Shift.AFTER)
    )
    private void plz$recordDestroyed(UdpConnection connection, CallbackInfo ci) {
        PLZConductWatch.onObjectDestroyed(
            connection, this.thumpable.getIsoObject(), this.thumpable.getName(), this.weapon.getWeapon().getName()
        );
    }
}
