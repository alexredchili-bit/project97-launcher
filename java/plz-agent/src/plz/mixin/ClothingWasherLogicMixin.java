package plz.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zombie.inventory.ItemContainer;
import zombie.iso.objects.ClothingWasherLogic;

@Mixin(ClothingWasherLogic.class)
abstract class ClothingWasherLogicMixin {
    @Shadow
    public abstract ItemContainer getContainer();

    @Inject(
        method = "update",
        at = @At(value = "INVOKE", target = "Lzombie/iso/objects/ClothingWasherLogic;getContainer()Lzombie/inventory/ItemContainer;", ordinal = 0),
        cancellable = true
    )
    private void plz$skipWithoutContainer(CallbackInfo ci) {
        if (this.getContainer() == null) {
            ci.cancel();
        }
    }
}
