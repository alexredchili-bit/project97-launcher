package plz.mixin.client;

import gnu.trove.list.array.TLongArrayList;
import java.util.ArrayList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import zombie.iso.BuildingDef;
import zombie.iso.IsoCamera;
import zombie.iso.IsoGridSquare;
import zombie.iso.fboRenderChunk.FBORenderCutaways;
import zombie.plz.access.PLZCutawayWallAccess;

@Mixin(FBORenderCutaways.CutawayWall.class)
abstract class FBORenderCutaways_CutawayWallMixin implements PLZCutawayWallAccess {
    @Shadow
    FBORenderCutaways.ChunkLevelData chunkLevelData;

    @Shadow
    @Final
    TLongArrayList occludedRoomIds;

    @Shadow
    abstract boolean isPartOfBuilding(BuildingDef buildingDef);

    @Override
    public FBORenderCutaways.ChunkLevelData plzChunkLevelData() {
        return this.chunkLevelData;
    }

    @Override
    public boolean plzCanCut(ArrayList<Long> roomIds, boolean peeking, ArrayList<BuildingDef> collapse, IsoGridSquare climbTarget) {
        if (roomIds.isEmpty()) {
            if (IsoCamera.frameState.camCharacterSquare == null) {
                return false;
            }

            for (int i = 0; i < collapse.size(); i++) {
                if (this.isPartOfBuilding(collapse.get(i))) {
                    return true;
                }
            }

            return false;
        } else {
            if (peeking) {
                return true;
            }

            if (climbTarget != null && this.occludedRoomIds.contains(climbTarget.getRoomID())) {
                return true;
            }

            for (int i = 0; i < roomIds.size(); i++) {
                if (this.occludedRoomIds.contains(roomIds.get(i))) {
                    return true;
                }
            }

            return false;
        }
    }
}
