package plz.mixin.server;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import zombie.network.ServerChunkLoader;
import zombie.plz.access.PLZSaveChunkThreadAccess;
import zombie.plz.access.PLZServerChunkLoaderAccess;

@Mixin(ServerChunkLoader.class)
abstract class ServerChunkLoaderMixin implements PLZServerChunkLoaderAccess {
    // The vanilla threadSave field's type is a private class, so the save thread registers itself here.
    @Unique
    private PLZSaveChunkThreadAccess plz$threadSave;

    @Override
    public PLZSaveChunkThreadAccess plzThreadSave() {
        return this.plz$threadSave;
    }

    @Override
    public void plzSetThreadSave(PLZSaveChunkThreadAccess threadSave) {
        this.plz$threadSave = threadSave;
    }
}
