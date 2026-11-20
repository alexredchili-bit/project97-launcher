package plz.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import zombie.network.packets.vehicle.VehicleFullUpdatePacket;
import zombie.plz.PLZVehicleSnap;

@Mixin(VehicleFullUpdatePacket.class)
abstract class VehicleFullUpdatePacketMixin {
    @WrapOperation(
        method = "parse",
        at = @At(value = "INVOKE", target = "Lzombie/core/physics/Bullet;setOwnVehiclePhysics(I[FZ)I")
    )
    private int plz$watchFullUpdatePhysics(int vehicleId, float[] physics, boolean collide, Operation<Integer> original) {
        PLZVehicleSnap.onWriteById(vehicleId, "VehicleFullUpdate.setOwnVehiclePhysics", physics);
        return original.call(vehicleId, physics, collide);
    }
}
