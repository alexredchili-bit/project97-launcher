package plz.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import zombie.iso.IsoChunk;
import zombie.iso.IsoChunkMap;
import zombie.plz.PLZPerf;

@Mixin(IsoChunkMap.class)
abstract class IsoChunkMapMixin {
    @Shadow
    public abstract int getWorldXMin();

    @Shadow
    public abstract int getWorldYMin();

    @ModifyVariable(method = "updateInternal", at = @At(value = "STORE", ordinal = 1), index = 3)
    private int plz$capHandoff(int count) {
        int plzDiv = PLZPerf.CHUNK_HANDOFF_DIVISOR;
        if (plzDiv > 0) {
            count = Math.min(count, 1 + IsoChunk.loadGridSquare.size() / plzDiv);
        }

        return count;
    }

    @ModifyExpressionValue(method = "getGridSquareDirect", at = @At(value = "FIELD", target = "Lzombie/iso/IsoChunk;loaded:Z"))
    private boolean plz$onlyChunkAtThisSlot(
        boolean loaded,
        @Local(index = 6) IsoChunk c,
        @Local(index = 4) int chunkMapChunkX,
        @Local(index = 5) int chunkMapChunkY
    ) {
        return loaded && c.wx == this.getWorldXMin() + chunkMapChunkX && c.wy == this.getWorldYMin() + chunkMapChunkY;
    }
}
