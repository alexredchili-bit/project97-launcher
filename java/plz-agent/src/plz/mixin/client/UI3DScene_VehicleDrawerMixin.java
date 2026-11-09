package plz.mixin.client;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import zombie.plz.access.PLZSceneVehicleAccess;

@Mixin(targets = "zombie.vehicles.UI3DScene$VehicleDrawer")
abstract class UI3DScene_VehicleDrawerMixin {
    // vehicle is typed as the private SceneVehicle, which no mixin source can name.
    @Unique
    private static final VarHandle plz$VEHICLE = plz$vehicleHandle();

    @Unique
    private static VarHandle plz$vehicleHandle() {
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            return lookup.unreflectVarHandle(lookup.lookupClass().getDeclaredField("vehicle"));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    @Unique
    private PLZSceneVehicleAccess plz$vehicle() {
        return (PLZSceneVehicleAccess)plz$VEHICLE.get((Object)this);
    }

    @ModifyArg(
        method = "render(I)V",
        at = @At(value = "INVOKE", target = "Lzombie/scripting/objects/VehicleScript;getSkin(I)Lzombie/scripting/objects/VehicleScript$Skin;"),
        require = 2
    )
    private int plz$maskSkin(int index) {
        return this.plz$vehicle().plzClampedSkinIndex();
    }

    @ModifyArg(
        method = "render(I)V",
        at = @At(value = "INVOKE", target = "Lzombie/core/skinnedmodel/shader/Shader;setTexturePainColor(Lorg/joml/Vector3f;F)V")
    )
    private Vector3f plz$vehiclePaint(Vector3f paintColor) {
        return this.plz$vehicle().plzPaint();
    }
}
