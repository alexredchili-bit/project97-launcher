package plz.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import zombie.iso.IsoChunk;
import zombie.iso.fboRenderChunk.FBORenderSnow;
import zombie.plz.PLZSnowClear;
import zombie.plz.access.PLZFBORenderSnowChunkLevelAccess;

@Mixin(FBORenderSnow.class)
abstract class FBORenderSnowMixin {
    @WrapOperation(
        method = "updateSnow",
        at = @At(
            value = "INVOKE",
            target = "Lzombie/iso/fboRenderChunk/FBORenderSnow;getChunkLevel(ILzombie/iso/IsoChunk;I)Lzombie/iso/fboRenderChunk/FBORenderSnow$ChunkLevel;"
        )
    )
    private FBORenderSnow.ChunkLevel plz$trackClearRevision(
        FBORenderSnow self, int playerIndex, IsoChunk chunk, int level, Operation<FBORenderSnow.ChunkLevel> original,
        @Share("plzClearChanged") LocalBooleanRef changed
    ) {
        FBORenderSnow.ChunkLevel chunkLevel = original.call(self, playerIndex, chunk, level);
        int revision = level == 0 ? PLZSnowClear.revision(chunk.wx, chunk.wy) : 0;
        PLZFBORenderSnowChunkLevelAccess access = (PLZFBORenderSnowChunkLevelAccess) (Object) chunkLevel;
        if (revision != access.plzGetSnowClearRevision()) {
            access.plzSetSnowClearRevision(revision);
            changed.set(true);
        }

        return chunkLevel;
    }

    // A cleared tile changed, so the existing grid must rebuild even though fracTarget did not move.
    @ModifyExpressionValue(
        method = "updateSnow",
        at = @At(value = "FIELD", target = "Lzombie/iso/fboRenderChunk/FBORenderSnow$SnowGrid;frac:I", opcode = Opcodes.GETFIELD)
    )
    private int plz$rebuildOnClear(int frac, @Share("plzClearChanged") LocalBooleanRef changed) {
        return changed.get() ? -1 : frac;
    }
}
