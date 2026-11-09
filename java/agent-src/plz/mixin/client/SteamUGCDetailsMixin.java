package plz.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import zombie.core.znet.SteamUGCDetails;
import zombie.plz.PLZWorkshopOverride;

@Mixin(SteamUGCDetails.class)
public abstract class SteamUGCDetailsMixin {
    @Shadow
    @Final
    private long id;

    @ModifyReturnValue(method = "getTimeUpdated", at = @At("RETURN"))
    private long plz$publishedTimestamp(long original) {
        return PLZWorkshopOverride.publishedTimestamp(this.id, original);
    }
}
