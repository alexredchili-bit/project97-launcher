package plz.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zombie.characters.IsoPlayer;
import zombie.core.raknet.UdpConnection;
import zombie.network.fields.vehicle.VehicleID;
import zombie.network.packets.vehicle.VehicleRemovePacket;
import zombie.plz.PLZFixes;
import zombie.vehicles.BaseVehicle;

@Mixin(VehicleRemovePacket.class)
abstract class VehicleRemovePacketMixin {
    @Shadow
    @Final
    protected VehicleID vehicleId;

    @Inject(method = "setData", at = @At("HEAD"), cancellable = true)
    private void plz$setDataById(Object[] values, CallbackInfo ci) {
        if (values[0] instanceof Short id) {
            this.vehicleId.set(null);
            this.vehicleId.setID(id);
            ci.cancel();
        }
    }

    // removeFromWorld refuses an occupied car but unregisterVehicle still runs, leaving an unreachable phantom.
    @Inject(
        method = "processClient",
        at = @At(value = "INVOKE", target = "Lzombie/network/fields/vehicle/VehicleID;getVehicle()Lzombie/vehicles/BaseVehicle;", ordinal = 0),
        cancellable = true
    )
    private void plz$keepOccupied(UdpConnection connection, CallbackInfo ci) {
        if (PLZFixes.on(PLZFixes.VEHICLE_REMOVE_KEEP_OCCUPIED) && plz$hasLocalPlayerAboard(this.vehicleId.getVehicle())) {
            PLZFixes.hit(PLZFixes.VEHICLE_REMOVE_KEEP_OCCUPIED);
            ci.cancel();
        }
    }

    @Unique
    private static boolean plz$hasLocalPlayerAboard(BaseVehicle vehicle) {
        for (int seat = 0; seat < vehicle.getMaxPassengers(); seat++) {
            if (vehicle.getCharacter(seat) instanceof IsoPlayer player && player.isLocalPlayer()) {
                return true;
            }
        }

        return false;
    }
}
