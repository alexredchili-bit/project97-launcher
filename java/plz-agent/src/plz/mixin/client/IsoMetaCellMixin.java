package plz.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import zombie.iso.IsoMetaCell;
import zombie.iso.LotHeader;
import zombie.plz.PLZLotHeaders;
import zombie.plz.PLZPerf;

@Mixin(IsoMetaCell.class)
abstract class IsoMetaCellMixin {
    @Shadow
    public LotHeader info;

    @Unique
    private PLZLotHeaders.Cache plz$lotHeaders;

    @Unique
    private PLZLotHeaders.Cache plz$lotHeaderCache() {
        if (this.plz$lotHeaders == null || this.plz$lotHeaders.header != this.info) {
            this.plz$lotHeaders = new PLZLotHeaders.Cache(this.info);
        }

        return this.plz$lotHeaders;
    }

    @WrapOperation(
        method = "getChunk(I)Lzombie/iso/IsoMetaChunk;",
        at = @At(value = "INVOKE", target = "Lzombie/iso/LotHeader;getZombieIntensityForChunk(Lzombie/iso/LotHeader;II)I")
    )
    private int plz$cachedZombieIntensity(LotHeader lotHeader, int chunkX, int chunkY, Operation<Integer> original) {
        return PLZPerf.LOADER_CPU_FIXES
            ? PLZLotHeaders.zombieIntensity(lotHeader, chunkX, chunkY, this.plz$lotHeaderCache())
            : original.call(lotHeader, chunkX, chunkY);
    }
}
