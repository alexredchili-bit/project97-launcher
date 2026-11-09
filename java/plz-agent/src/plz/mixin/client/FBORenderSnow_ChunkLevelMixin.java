package plz.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import zombie.iso.fboRenderChunk.FBORenderSnow;
import zombie.plz.access.PLZFBORenderSnowChunkLevelAccess;

@Mixin(FBORenderSnow.ChunkLevel.class)
abstract class FBORenderSnow_ChunkLevelMixin implements PLZFBORenderSnowChunkLevelAccess {
    @Unique
    private int plz$snowClearRevision;

    @Override
    public int plzGetSnowClearRevision() {
        return this.plz$snowClearRevision;
    }

    @Override
    public void plzSetSnowClearRevision(int revision) {
        this.plz$snowClearRevision = revision;
    }
}
