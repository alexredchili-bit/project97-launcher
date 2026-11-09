package plz.mixin.server;

import java.nio.ByteBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zombie.network.ServerChunkLoader;
import zombie.plz.access.PLZSaveChunkThreadAccess;
import zombie.plz.access.PLZServerChunkLoaderAccess;

@Mixin(targets = "zombie.network.ServerChunkLoader$SaveChunkThread")
abstract class ServerChunkLoader_SaveChunkThreadMixin implements PLZSaveChunkThreadAccess {
    @Shadow
    public abstract ByteBuffer getPendingChunkData(int wx, int wy);

    @Inject(method = "<init>", at = @At("RETURN"))
    private void plz$registerWithLoader(ServerChunkLoader loader, CallbackInfo ci) {
        ((PLZServerChunkLoaderAccess)loader).plzSetThreadSave(this);
    }

    @Override
    public ByteBuffer plzPendingChunkData(int wx, int wy) {
        return this.getPendingChunkData(wx, wy);
    }
}
