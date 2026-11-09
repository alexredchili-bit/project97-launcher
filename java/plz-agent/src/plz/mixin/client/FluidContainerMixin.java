package plz.mixin.client;

import java.util.ArrayList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import zombie.entity.components.fluids.FluidContainer;
import zombie.entity.components.fluids.FluidInstance;
import zombie.plz.PLZDrinkName;

@Mixin(FluidContainer.class)
abstract class FluidContainerMixin {
    @Shadow
    @Final
    private ArrayList<FluidInstance> fluids;

    // A mixed drink that still matches its recipe reads as the recipe's name.
    @Inject(method = "getUiName", at = @At("HEAD"), cancellable = true)
    private void plz$drinkName(CallbackInfoReturnable<String> cir) {
        String plzDrink = PLZDrinkName.nameFor((FluidContainer) (Object) this, this.fluids);
        if (plzDrink != null) {
            cir.setReturnValue(plzDrink);
        }
    }
}
