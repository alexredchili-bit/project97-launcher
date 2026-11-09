package plz.mixin.client;

import com.llamalad7.mixinextras.sugar.Local;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import zombie.plz.PLZPerf;
import zombie.plz.access.PLZTileDepthTexturesAccess;
import zombie.tileDepth.TileDepthTextures;

@Mixin(targets = "zombie.tileDepth.TileDepthTextures$LoadTask")
abstract class TileDepthTextures_LoadTaskMixin {
    @Shadow
    @Final
    TileDepthTextures textures;

    @Inject(
        method = "call",
        at = @At(
            value = "FIELD",
            target = "Lzombie/tileDepth/TileDepthTextures$LoadTask;textures:Lzombie/tileDepth/TileDepthTextures;",
            opcode = Opcodes.GETFIELD,
            ordinal = 0
        ),
        cancellable = true
    )
    private void plz$parallelLoad(CallbackInfoReturnable<Object> cir, @Local(name = "tilesetName") String tilesetName) {
        if (PLZPerf.PARALLEL_DEPTH_MAPS) {
            ((PLZTileDepthTexturesAccess) (Object) this.textures).plzLoadTileset(tilesetName);
            cir.setReturnValue(null);
        }
    }
}
