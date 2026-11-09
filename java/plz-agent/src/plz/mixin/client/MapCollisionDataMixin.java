package plz.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zombie.MapCollisionData;
import zombie.iso.IsoMetaGrid;
import zombie.iso.LotHeader;
import zombie.plz.PLZPerf;
import zombie.plz.PLZZombieIntensityCache;

@Mixin(MapCollisionData.class)
abstract class MapCollisionDataMixin {
    @Unique
    private PLZZombieIntensityCache plz$intensityCache;

    @WrapOperation(
        method = "init",
        at = @At(value = "INVOKE", target = "Lzombie/iso/LotHeader;getZombieIntensityForChunk(Lzombie/iso/LotHeader;II)I")
    )
    private int plz$cachedZombieIntensity(LotHeader lotHeader, int chunkX, int chunkY, Operation<Integer> original) {
        if (!PLZPerf.LOADER_CPU_FIXES) {
            return original.call(lotHeader, chunkX, chunkY);
        }

        if (this.plz$intensityCache == null) {
            this.plz$intensityCache = new PLZZombieIntensityCache();
        }

        return this.plz$intensityCache.get(lotHeader, chunkX, chunkY);
    }

    @Inject(method = "init", at = @At(value = "INVOKE", target = "Lzombie/MapCollisionData;n_init(IIII)V"))
    private void plz$dropIntensityCache(IsoMetaGrid metaGrid, CallbackInfo ci) {
        this.plz$intensityCache = null;
    }
}
