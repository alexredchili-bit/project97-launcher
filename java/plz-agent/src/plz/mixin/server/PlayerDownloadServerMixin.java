package plz.mixin.server;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import zombie.core.raknet.UdpConnection;
import zombie.network.ClientChunkRequest;
import zombie.network.PlayerDownloadServer;
import zombie.plz.PLZStalledConnections;

@Mixin(PlayerDownloadServer.class)
abstract class PlayerDownloadServerMixin {
    @Shadow
    @Final
    private UdpConnection connection;

    @Inject(method = "getClientChunkRequest", at = @At("HEAD"))
    private void plz$noteChunkActivity(boolean isLargeArea, CallbackInfoReturnable<ClientChunkRequest> cir) {
        PLZStalledConnections.noteChunkActivity(this.connection);
    }
}
