package plz.mixin.server;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.ArrayList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zombie.network.ServerMap;
import zombie.plz.PLZFixes;
import zombie.plz.access.PLZServerMapServerCellAccess;

@Mixin(ServerMap.class)
abstract class ServerMapMixin {
    @Shadow
    public ArrayList<ServerMap.ServerCell> releventNow;

    // PLZ: stamped instead of releventNow.contains(), and resolved once per pass so the flag never changes mid-pass.
    @Unique
    private volatile long plz$relevantPass;

    @Unique
    private volatile boolean plz$relevantStamped;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void plz$initPass(CallbackInfo ci) {
        this.plz$relevantPass = 1L;
    }

    @Unique
    private static PLZServerMapServerCellAccess plz$cell(Object cell) {
        return (PLZServerMapServerCellAccess) cell;
    }

    @Unique
    private boolean plz$isRelevant(ServerMap.ServerCell cell) {
        return plz$cell(cell).plzGetRelevantPass() == this.plz$relevantPass;
    }

    @WrapOperation(method = "loadOrKeepRelevent", at = @At(value = "INVOKE", target = "Ljava/util/ArrayList;add(Ljava/lang/Object;)Z"))
    private boolean plz$stampOnAdd(ArrayList<?> list, Object cell, Operation<Boolean> original) {
        if (list == this.releventNow) {
            plz$cell(cell).plzSetRelevantPass(this.plz$relevantPass);
        }

        return original.call(list, cell);
    }

    @WrapOperation(method = "loadOrKeepRelevent", at = @At(value = "INVOKE", target = "Ljava/util/ArrayList;contains(Ljava/lang/Object;)Z"))
    private boolean plz$stampedContains(ArrayList<?> list, Object cell, Operation<Boolean> original) {
        return this.plz$relevantStamped ? this.plz$isRelevant((ServerMap.ServerCell) cell) : original.call(list, cell);
    }

    @WrapOperation(method = "preupdate", at = @At(value = "INVOKE", target = "Ljava/util/ArrayList;remove(Ljava/lang/Object;)Z"))
    private boolean plz$unstampOnRemove(ArrayList<?> list, Object cell, Operation<Boolean> original) {
        if (list == this.releventNow) {
            plz$cell(cell).plzSetRelevantPass(0L);
        }

        return original.call(list, cell);
    }

    @WrapOperation(method = "preupdate", at = @At(value = "INVOKE", target = "Ljava/util/ArrayList;clear()V"))
    private void plz$nextPass(ArrayList<?> list, Operation<Void> original) {
        original.call(list);
        if (list == this.releventNow) {
            this.plz$relevantPass++;
            this.plz$relevantStamped = PLZFixes.on(PLZFixes.SERVER_MAP_RELEVANCE_STAMP);
            if (this.plz$relevantStamped) {
                PLZFixes.hit(PLZFixes.SERVER_MAP_RELEVANCE_STAMP);
            }
        }
    }

    @WrapOperation(method = "postupdate", at = @At(value = "INVOKE", target = "Ljava/util/ArrayList;contains(Ljava/lang/Object;)Z"))
    private boolean plz$stampedRelevant(ArrayList<?> list, Object cell, Operation<Boolean> original) {
        return this.plz$relevantStamped ? this.plz$isRelevant((ServerMap.ServerCell) cell) : original.call(list, cell);
    }
}
