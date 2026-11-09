package plz.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import zombie.characters.Moodles.Moodles;
import zombie.core.physics.CarController;
import zombie.plz.PLZHighFX;
import zombie.scripting.objects.MoodleType;
import zombie.vehicles.BaseVehicle;

@Mixin(CarController.class)
abstract class CarControllerMixin {
    @Shadow
    @Final
    public BaseVehicle vehicleObject;

    @WrapOperation(
        method = {"update", "delayCommandWhileDrunk(Z)Z", "delayCommandWhileDrunk(F)F"},
        at = @At(value = "INVOKE", target = "Lzombie/characters/Moodles/Moodles;getMoodleLevel(Lzombie/scripting/objects/MoodleType;)I"),
        require = 3
    )
    private int plz$impairedDriverLevel(Moodles moodles, MoodleType type, Operation<Integer> original) {
        return Math.max(original.call(moodles, type), PLZHighFX.driveLevel(this.vehicleObject.getDriver()));
    }
}
