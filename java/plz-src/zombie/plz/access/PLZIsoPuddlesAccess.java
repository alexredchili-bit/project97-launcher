package zombie.plz.access;

import java.util.ArrayList;
import zombie.iso.IsoGridSquare;

public interface PLZIsoPuddlesAccess {
    boolean plzCanRender(int z);

    int plzNumSquares();

    float[] plzData();

    void plzPack(ArrayList<IsoGridSquare> grid, int z);

    int plzAppend(float[] packed, int count, int z);

    void plzDraw(int z, int firstSquare, int numSquares);
}
