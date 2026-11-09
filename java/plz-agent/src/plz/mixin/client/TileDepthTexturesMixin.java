package plz.mixin.client;

import java.util.HashMap;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zombie.core.logger.ExceptionLogger;
import zombie.plz.access.PLZTileDepthTexturesAccess;
import zombie.tileDepth.TileDepthTextures;
import zombie.tileDepth.TilesetDepthTexture;

@Mixin(TileDepthTextures.class)
abstract class TileDepthTexturesMixin implements PLZTileDepthTexturesAccess {
    @Shadow
    @Final
    private HashMap<String, Integer> tilesetRows;

    @Shadow
    @Final
    private HashMap<String, TilesetDepthTexture> tilesets;

    // Claiming the name replaces vanilla's lock: without it two file threads build the same tileset and leak the loser's texture.
    @Unique
    private Set<String> plz$claimed;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void plz$initClaimed(String modID, String mediaAbsPath, CallbackInfo ci) {
        this.plz$claimed = ConcurrentHashMap.newKeySet();
    }

    // Vanilla holds the whole-object lock across the PNG decode, so tilesets load one at a time; decode unlocked, publish locked.
    @Override
    public void plzLoadTileset(String tilesetName) {
        int numRows;
        synchronized (this) {
            if (this.tilesets.get(tilesetName) != null || !this.plz$claimed.add(tilesetName)) {
                return;
            }

            numRows = this.tilesetRows.getOrDefault(tilesetName, 0);
        }

        if (numRows == 0) {
            return;
        }

        TilesetDepthTexture tileset = new TilesetDepthTexture((TileDepthTextures) (Object) this, tilesetName, 8, numRows, true);
        if (tileset.fileExists()) {
            try {
                tileset.load();
            } catch (Exception ex) {
                ExceptionLogger.logException(ex);
            }
        }

        synchronized (this) {
            this.tilesets.put(tilesetName, tileset);
        }
    }

    @Inject(method = "Reset", at = @At("TAIL"))
    private void plz$resetClaimed(CallbackInfo ci) {
        this.plz$claimed.clear();
    }
}
