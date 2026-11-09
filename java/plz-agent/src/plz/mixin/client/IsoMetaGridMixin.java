package plz.mixin.client;

import java.util.ArrayList;
import java.util.HashSet;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zombie.debug.DebugType;
import zombie.iso.IsoMetaGrid;
import zombie.iso.zones.VehicleZone;
import zombie.iso.zones.Zone;
import zombie.plz.PLZPerf;

@Mixin(IsoMetaGrid.class)
abstract class IsoMetaGridMixin {
    @Shadow
    @Final
    public ArrayList<VehicleZone> vehiclesZones;

    @Inject(method = "checkVehiclesZones", at = @At("HEAD"), cancellable = true)
    private void plz$dedupeVehiclesZonesFast(CallbackInfo ci) {
        if (!PLZPerf.LOADER_CPU_FIXES) {
            return;
        }

        ci.cancel();
        HashSet<Long> seen = new HashSet<>(this.vehiclesZones.size() * 2);
        int n = 0;

        while (n < this.vehiclesZones.size()) {
            Zone a = this.vehiclesZones.get(n);
            long key = (a.getX() & 65535L) << 48 | (a.getY() & 65535L) << 32 | (a.w & 65535L) << 16 | a.h & 65535L;
            if (seen.add(key)) {
                n++;
            } else {
                if (DebugType.Vehicle.isEnabled()) {
                    DebugType.Vehicle.debugln("checkVehiclesZones: ERROR! Zone '" + a.name + "':'" + a.type + "' (" + a.x + ", " + a.y + ") duplicate");
                }

                this.vehiclesZones.remove(n);
            }
        }
    }
}
