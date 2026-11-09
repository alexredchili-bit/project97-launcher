package plz.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import zombie.core.znet.SteamWorkshop;
import zombie.plz.PLZWorkshopOverride;

@Mixin(SteamWorkshop.class)
public abstract class SteamWorkshopMixin {
    @ModifyReturnValue(method = "GetItemState(J)J", at = @At("RETURN"))
    private long plz$itemState(long original, long itemID) {
        return PLZWorkshopOverride.itemState(itemID, original);
    }

    @ModifyReturnValue(method = "GetItemInstallTimeStamp(J)J", at = @At("RETURN"))
    private long plz$installTimestamp(long original, long itemID) {
        return PLZWorkshopOverride.installTimestamp(itemID, original);
    }
}
