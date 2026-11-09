package plz.mixin.server;

import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zombie.characters.animals.IsoAnimal;
import zombie.characters.animals.datas.AnimalData;
import zombie.plz.PLZAnimalOwner;

@Mixin(AnimalData.class)
abstract class AnimalDataMixin {
    @Shadow
    public IsoAnimal parent;

    // The engine treats the grown animal as the same creature but leaves ModData off, so ownership is carried here.
    @Inject(
        method = "grow",
        at = @At(value = "INVOKE", target = "Lzombie/characters/animals/IsoAnimal;setAnimalID(I)V", shift = At.Shift.AFTER)
    )
    private void plz$carryOwner(String newtype, CallbackInfo ci, @Local IsoAnimal newAnimal) {
        PLZAnimalOwner.carryOver(this.parent, newAnimal);
    }
}
