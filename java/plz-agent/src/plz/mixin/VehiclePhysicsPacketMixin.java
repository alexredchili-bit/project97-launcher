package plz.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import zombie.core.raknet.UdpConnection;
import zombie.iso.IsoWorld;
import zombie.network.GameServer;
import zombie.network.IConnection;
import zombie.network.PacketTypes;
import zombie.network.fields.vehicle.VehicleID;
import zombie.network.packets.vehicle.VehiclePhysicsPacket;
import zombie.plz.PLZFixes;
import zombie.plz.PLZVehicleGhost;
import zombie.plz.PLZVehicleNet;
import zombie.vehicles.BaseVehicle;
import zombie.vehicles.VehicleInterpolationData;
import zombie.vehicles.VehicleManager;

@Mixin(VehiclePhysicsPacket.class)
abstract class VehiclePhysicsPacketMixin extends VehicleInterpolationData {
    @Shadow
    @Final
    protected VehicleID vehicleId;

    @WrapOperation(
        method = "processClient",
        at = @At(value = "INVOKE", target = "Lzombie/vehicles/VehicleManager;sendVehicleRequest(SS)V")
    )
    private void plz$askOnlyWhenLoaded(VehicleManager manager, short vehicleId, short flags, Operation<Void> original) {
        if (!PLZFixes.on(PLZFixes.VEHICLE_PHYSICS_ASK_LOADED) || IsoWorld.instance.currentCell.getGridSquare(this.x, this.y, 0.0) != null) {
            original.call(manager, vehicleId, flags);
        }
    }

    // PLZ: vanilla's empty sync tells a driver nothing when the server lost the car; report it (PLZVehicleGhost says why not remove).
    public void sync(PacketTypes.PacketType packetType, IConnection connection) {
        if (GameServer.server && connection != null) {
            PLZVehicleGhost.noteDriving(this.vehicleId.getID(), connection.getUserName());
        }

        if (GameServer.server && PLZFixes.on(PLZFixes.VEHICLE_GHOST_REPORT)) {
            PLZFixes.hit(PLZFixes.VEHICLE_GHOST_REPORT);
            PLZVehicleGhost.report(this.vehicleId.getID(), connection == null ? null : connection.getUserName());
        }
    }

    @WrapOperation(
        method = "processServer",
        at = @At(
            value = "INVOKE",
            target = "Lzombie/network/packets/vehicle/VehiclePhysicsPacket;sendToRelativeClients(Lzombie/network/PacketTypes$PacketType;Lzombie/core/raknet/UdpConnection;FF)V"
        )
    )
    private void plz$relayFastLane(
        VehiclePhysicsPacket packet,
        PacketTypes.PacketType packetType,
        UdpConnection connection,
        float x,
        float y,
        Operation<Void> original,
        @Local BaseVehicle vehicle
    ) {
        if (PLZFixes.on(PLZFixes.VEHICLE_PHYSICS_FAST_LANE)) {
            PLZVehicleNet.relayPhysics(packet, packetType, connection, vehicle, x, y);
        } else {
            original.call(packet, packetType, connection, x, y);
        }
    }
}
