package plz.mixin.server;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zombie.core.network.ByteBufferReader;
import zombie.core.raknet.UdpConnection;
import zombie.network.IConnection;
import zombie.network.PacketTypes;
import zombie.network.packets.SyncZonePacket;
import zombie.plz.PLZAnimalZoneGate;

@Mixin(SyncZonePacket.class)
abstract class SyncZonePacketMixin {
    @Unique
    private PLZAnimalZoneGate.Refusal plz$refusal;

    @Shadow
    public abstract void parse(ByteBufferReader b, IConnection connection);

    public void parseServer(ByteBufferReader b, UdpConnection connection) {
        // Decided before parse, because DesignationZone.load applies a rename while it reads.
        this.plz$refusal = PLZAnimalZoneGate.review(b, connection);
        if (this.plz$refusal == null) {
            this.parse(b, connection);
        }
    }

    @Inject(method = "processServer", at = @At("HEAD"), cancellable = true)
    private void plz$restoreRefused(PacketTypes.PacketType packetType, UdpConnection connection, CallbackInfo ci) {
        if (this.plz$refusal != null) {
            PLZAnimalZoneGate.restore(this.plz$refusal, connection);
            this.plz$refusal = null;
            ci.cancel();
        }
    }
}
