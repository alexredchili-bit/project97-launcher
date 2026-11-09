package zombie.plz.access;

import java.util.ArrayList;
import zombie.iso.BuildingDef;
import zombie.iso.IsoGridSquare;
import zombie.iso.fboRenderChunk.FBORenderCutaways;

public interface PLZCutawayWallAccess {
    FBORenderCutaways.ChunkLevelData plzChunkLevelData();

    boolean plzCanCut(ArrayList<Long> roomIds, boolean peeking, ArrayList<BuildingDef> collapse, IsoGridSquare climbTarget);
}
