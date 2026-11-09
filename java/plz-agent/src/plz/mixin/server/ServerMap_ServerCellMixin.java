package plz.mixin.server;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import zombie.debug.DebugType;
import zombie.debug.LogSeverity;
import zombie.iso.IsoChunk;
import zombie.network.ServerMap;
import zombie.plz.PLZFixes;
import zombie.plz.access.PLZServerMapServerCellAccess;

@Mixin(ServerMap.ServerCell.class)
abstract class ServerMap_ServerCellMixin implements PLZServerMapServerCellAccess {
    // PLZ: the pass this cell was last marked relevant in, replacing releventNow.contains().
    @Unique
    private long plz$relevantPass;

    @Override
    public long plzGetRelevantPass() {
        return this.plz$relevantPass;
    }

    @Override
    public void plzSetRelevantPass(long pass) {
        this.plz$relevantPass = pass;
    }

    @WrapOperation(method = "RecalcAll2", at = @At(value = "INVOKE", target = "Lzombie/iso/IsoChunk;doLoadGridsquare()V"))
    private void plz$loadRestOfCell(IsoChunk chunk, Operation<Void> original) {
        if (!PLZFixes.on(PLZFixes.SERVER_CELL_LOAD_GUARD)) {
            original.call(chunk);
            return;
        }

        try {
            original.call(chunk);
        } catch (RuntimeException e) {
            PLZFixes.hit(PLZFixes.SERVER_CELL_LOAD_GUARD);
            DebugType.General.printException(e, "PLZ: chunk " + chunk.wx + "," + chunk.wy + " failed to load, loading the rest of the cell", LogSeverity.Error);
        }
    }
}
