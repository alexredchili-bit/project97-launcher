package plz.mixin.server;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import zombie.iso.IsoPhysicsObject;
import zombie.plz.PLZTickRate;

@Mixin(IsoPhysicsObject.class)
abstract class IsoPhysicsObjectMixin {
    // Vanilla hardcodes 10 TPS for the server, so fpsMod would mistune the velocity decay at any other rate.
    @ModifyConstant(method = "update", constant = @Constant(intValue = 10))
    private int plz$serverFps(int vanillaFps) {
        return PLZTickRate.serverFps();
    }
}
