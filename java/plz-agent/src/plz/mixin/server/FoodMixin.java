package plz.mixin.server;

import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import zombie.characters.animals.IsoAnimal;
import zombie.inventory.types.Food;
import zombie.iso.objects.IsoHutch;
import zombie.plz.PLZAnimalOwner;

@Mixin(Food.class)
abstract class FoodMixin {
    // PLZ: the chick takes the farm off the egg it came out of; attachBackToMother is an animalId, which collides.
    @Inject(
        method = "checkEggHatch",
        at = @At(value = "INVOKE", target = "Lzombie/characters/animals/AnimalGene;checkGeneticDisorder(Lzombie/characters/animals/IsoAnimal;)V")
    )
    private void plz$stampHatchling(IsoHutch hutch, CallbackInfoReturnable<Boolean> cir, @Local IsoAnimal baby) {
        PLZAnimalOwner.hatch((Food) (Object) this, baby);
    }
}
