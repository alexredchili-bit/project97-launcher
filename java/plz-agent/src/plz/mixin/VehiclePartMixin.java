package plz.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zombie.inventory.InventoryItem;
import zombie.inventory.ItemContainer;
import zombie.plz.PLZVehicleNet;
import zombie.plz.access.PLZItemContainerAccess;
import zombie.scripting.objects.VehicleScript;
import zombie.vehicles.VehiclePart;

@Mixin(VehiclePart.class)
abstract class VehiclePartMixin {
    @Shadow
    public abstract VehicleScript.Part getScriptPart();

    // A script-declared capacity belongs to the car and vanilla stamps it only at build, so a refit shrank KI5 boots to the shared item's 25.
    @Inject(
        method = "doInventoryItemStats",
        at = @At(value = "INVOKE", target = "Lzombie/inventory/InventoryItem;getMaxCapacity()I", ordinal = 0)
    )
    private void plz$restampScriptedCapacity(InventoryItem newItem, int mechanicSkill, CallbackInfo ci) {
        int scriptedCapacity = this.getScriptPart().container.capacity;
        if (scriptedCapacity > 0 && newItem.getMaxCapacity() != scriptedCapacity) {
            newItem.setMaxCapacity(scriptedCapacity);
        }
    }

    @WrapOperation(
        method = "save",
        at = @At(value = "INVOKE", target = "Lzombie/inventory/ItemContainer;save(Ljava/nio/ByteBuffer;)Ljava/util/ArrayList;")
    )
    private ArrayList<InventoryItem> plz$saveLeanContainer(ItemContainer container, ByteBuffer output, Operation<ArrayList<InventoryItem>> original)
        throws IOException {
        if (PLZVehicleNet.leanWriting()) {
            ((PLZItemContainerAccess) (Object) container).plzSaveLean(output);
            return null;
        }

        return original.call(container, output);
    }
}
