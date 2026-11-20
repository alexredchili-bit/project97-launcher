package plz.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import plz.agent.LuaPublic;
import zombie.core.physics.Transform;
import zombie.core.physics.WorldSimulation;
import zombie.plz.PLZVehicleSnap;
import zombie.vehicles.BaseVehicle;

@Mixin(BaseVehicle.class)
abstract class BaseVehicleMixin {
    // Client/Diagnostics/PLZVehTel.lua attaches this to every jump it reports.
    @Unique
    @LuaPublic
    private static String plzVehicleSnapRecent(double maxAgeMs) {
        return PLZVehicleSnap.describe((long)maxAgeMs);
    }

    @Inject(method = "setWorldTransform", at = @At("HEAD"))
    private void plz$watchWorldTransform(Transform in, CallbackInfo ci) {
        PLZVehicleSnap.onWrite(
            (BaseVehicle)(Object)this,
            "setWorldTransform",
            "setWorldTransform",
            in.origin.x + WorldSimulation.instance.offsetX,
            in.origin.z + WorldSimulation.instance.offsetY
        );
    }
}
