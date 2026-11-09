package plz.mixin.server;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import zombie.core.raknet.UdpConnection;
import zombie.network.PacketTypes;
import zombie.network.fields.character.AnimalID;
import zombie.network.packets.hit.VehicleHit;
import zombie.network.packets.hit.VehicleHitAnimalPacket;
import zombie.plz.PLZAnimalProtect;

@Mixin(VehicleHitAnimalPacket.class)
abstract class VehicleHitAnimalPacketMixin extends VehicleHit {
    @Shadow
    @Final
    protected AnimalID target;

    // Dropped whole, before the relay, so no client is told about a hit that did not land.
    @Override
    public void processServer(PacketTypes.PacketType packetType, UdpConnection connection) {
        if (!PLZAnimalProtect.mayRunOver(this.getCharacter(), this.target.getAnimal())) {
            return;
        }

        super.processServer(packetType, connection);
    }
}
