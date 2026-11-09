package plz.mixin.client;

import java.util.Arrays;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zombie.iso.IsoChunk;
import zombie.iso.fboRenderChunk.FBORenderCutaways;
import zombie.plz.access.PLZOrphanStructuresAccess;

@Mixin(FBORenderCutaways.OrphanStructures.class)
abstract class FBORenderCutaways_OrphanStructuresMixin implements PLZOrphanStructuresAccess {
    @Unique
    private final int[] plz$pendingFrames = new int[4];

    @Override
    public int[] plzPendingFrames() {
        return this.plz$pendingFrames;
    }

    @Inject(method = "calculate", at = @At("HEAD"))
    private void plz$clearPendingOnCalculate(IsoChunk chunk, CallbackInfo ci) {
        Arrays.fill(this.plz$pendingFrames, 0);
    }

    @Inject(method = "resetForStore", at = @At("HEAD"))
    private void plz$clearPendingOnReset(CallbackInfo ci) {
        Arrays.fill(this.plz$pendingFrames, 0);
    }
}
