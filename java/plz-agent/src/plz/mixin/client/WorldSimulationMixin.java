package plz.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import zombie.core.physics.Transform;
import zombie.core.physics.WorldSimulation;
import zombie.plz.PLZVehicleSnap;

@Mixin(WorldSimulation.class)
abstract class WorldSimulationMixin {
    @WrapOperation(
        method = "updateInternal",
        at = @At(value = "INVOKE", target = "Lzombie/core/physics/Transform;set(Lzombie/core/physics/Transform;)V")
    )
    private void plz$watchBulletCopy(Transform target, Transform fresh, Operation<Void> original) {
        PLZVehicleSnap.onBulletCopy(target, fresh);
        original.call(target, fresh);
    }
}
