package plz.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import zombie.core.skinnedmodel.model.VehicleSubModelInstance;
import zombie.core.textures.Texture;
import zombie.plz.PLZVehiclePlate;
import zombie.plz.access.PLZVehicleSubModelInstanceAccess;

@Mixin(VehicleSubModelInstance.class)
abstract class VehicleSubModelInstanceMixin implements PLZVehicleSubModelInstanceAccess {
    @Override
    public Texture plzPlateTexture() {
        return PLZVehiclePlate.texture((VehicleSubModelInstance)(Object)this);
    }
}
