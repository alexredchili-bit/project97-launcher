package plz.mixin.server;

import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zombie.core.raknet.UdpConnection;
import zombie.inventory.ItemContainer;
import zombie.network.PacketTypes;
import zombie.network.fields.ContainerID;
import zombie.network.packets.RequestItemsForContainerPacket;
import zombie.plz.PLZFixes;
import zombie.plz.PLZVehicleNet;

@Mixin(RequestItemsForContainerPacket.class)
abstract class RequestItemsForContainerPacketMixin {
    @Shadow
    ContainerID containerId;

    @Inject(method = "processServer", at = @At(value = "RETURN", ordinal = 0))
    private void plz$serveExploredVehicleContainer(
        PacketTypes.PacketType packetType, UdpConnection connection, CallbackInfo ci, @Local ItemContainer container
    ) {
        if (container != null
            && this.containerId.getVehicle() != null
            && PLZFixes.on(PLZFixes.VEHICLE_LEAN_FULL)
            && PLZVehicleNet.serveRequested(connection, this.containerId.getVehicle(), container)) {
            PLZFixes.hit(PLZFixes.VEHICLE_LEAN_FULL);
        }
    }
}
