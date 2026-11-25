package plz.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import zombie.inventory.types.Food;

@Mixin(Food.class)
abstract class FoodMixin {
    // PLZ: hunger scales a food's weight, so a spent spice left with positive hunger weighed less than nothing.
    @Inject(method = "getActualWeight", at = @At("RETURN"), cancellable = true)
    private void plz$neverNegativeWeight(CallbackInfoReturnable<Float> cir) {
        if (cir.getReturnValueF() < 0.0F) {
            cir.setReturnValue(0.0F);
        }
    }
}
