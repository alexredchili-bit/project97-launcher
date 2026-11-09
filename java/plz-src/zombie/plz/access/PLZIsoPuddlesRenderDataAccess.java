package zombie.plz.access;

import java.util.ArrayList;
import zombie.iso.IsoGridSquare;

public interface PLZIsoPuddlesRenderDataAccess {
    int plzNumSquares();

    float[] plzData();

    void plzPack(ArrayList<IsoGridSquare> grid, int z, int playerIndex);

    int plzAppend(float[] packed, int count, int z);
}
