package plz.mixin.server;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zombie.asset.Asset;
import zombie.asset.AssetManager;
import zombie.asset.AssetPath;
import zombie.plz.PLZFixes;

@Mixin(AssetManager.class)
abstract class AssetManagerMixin {
    // PLZ: the assets THashMap is get-then-put; SPVThread and main-thread puts blew up inside rehash. One lock per manager.
    @Unique
    private Object plz$assetsLock;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void plz$initLock(CallbackInfo ci) {
        this.plz$assetsLock = new Object();
    }

    @WrapMethod(method = "destroy")
    private void plz$lockedDestroy(Operation<Void> original) {
        if (PLZFixes.on(PLZFixes.ASSET_MANAGER_SYNC)) {
            synchronized (this.plz$assetsLock) {
                PLZFixes.hit(PLZFixes.ASSET_MANAGER_SYNC);
                original.call();
                return;
            }
        }

        original.call();
    }

    @WrapMethod(method = "removeUnreferenced")
    private void plz$lockedRemoveUnreferenced(Operation<Void> original) {
        if (PLZFixes.on(PLZFixes.ASSET_MANAGER_SYNC)) {
            synchronized (this.plz$assetsLock) {
                original.call();
                return;
            }
        }

        original.call();
    }

    @WrapMethod(method = "load(Lzombie/asset/AssetPath;Lzombie/asset/AssetManager$AssetParams;)Lzombie/asset/Asset;")
    private Asset plz$lockedLoad(AssetPath path, AssetManager.AssetParams params, Operation<Asset> original) {
        if (PLZFixes.on(PLZFixes.ASSET_MANAGER_SYNC)) {
            synchronized (this.plz$assetsLock) {
                return original.call(path, params);
            }
        }

        return original.call(path, params);
    }

    @WrapMethod(method = "enableUnload")
    private void plz$lockedEnableUnload(boolean enable, Operation<Void> original) {
        if (PLZFixes.on(PLZFixes.ASSET_MANAGER_SYNC)) {
            synchronized (this.plz$assetsLock) {
                original.call(enable);
                return;
            }
        }

        original.call(enable);
    }
}
