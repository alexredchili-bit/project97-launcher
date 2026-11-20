package plz.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zombie.iso.IsoGridSquare;
import zombie.plz.PLZVehicleSnap;
import zombie.vehicles.BaseVehicle;
import zombie.vehicles.VehicleManager;

@Mixin(VehicleManager.class)
abstract class VehicleManagerMixin {
    @Inject(method = "clientUpdateVehiclePos", at = @At("HEAD"))
    private void plz$watchClientPos(BaseVehicle vehicle, float x, float y, float z, IsoGridSquare sq, CallbackInfo ci) {
        PLZVehicleSnap.onWrite(vehicle, sq == null ? "clientUpdateVehiclePos(unloaded)" : "clientUpdateVehiclePos", "clientUpdateVehiclePos", x, y);
    }

    @WrapOperation(
        method = "updateVehiclePos",
        at = @At(value = "INVOKE", target = "Lzombie/core/physics/Bullet;setOwnVehiclePhysics(I[FZ)I"),
        require = 2
    )
    private int plz$watchInterpolatedPhysics(int vehicleId, float[] physics, boolean collide, Operation<Integer> original) {
        PLZVehicleSnap.onWriteById(vehicleId, "updateVehiclePos.setOwnVehiclePhysics", physics);
        return original.call(vehicleId, physics, collide);
    }
}
