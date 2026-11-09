package plz.mixin.client;

import java.util.ArrayList;
import java.util.HashSet;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import zombie.iso.IsoGridSquare;
import zombie.plz.access.PLZCutawayPerPlayerDataAccess;

@Mixin(targets = "zombie.iso.fboRenderChunk.FBORenderCutaways$PerPlayerData")
abstract class FBORenderCutaways_PerPlayerDataMixin implements PLZCutawayPerPlayerDataAccess {
    @Shadow
    @Final
    HashSet<IsoGridSquare> cutawayVisitorResultsNorth;

    @Shadow
    @Final
    HashSet<IsoGridSquare> cutawayVisitorResultsWest;

    @Shadow
    @Final
    ArrayList<IsoGridSquare> lastCutawayVisitorResults;

    @Override
    public ArrayList<IsoGridSquare> plzLastCutawayVisitorResults() {
        return this.lastCutawayVisitorResults;
    }

    @Override
    public HashSet<IsoGridSquare> plzCutawayVisitorResultsNorth() {
        return this.cutawayVisitorResultsNorth;
    }

    @Override
    public HashSet<IsoGridSquare> plzCutawayVisitorResultsWest() {
        return this.cutawayVisitorResultsWest;
    }
}
