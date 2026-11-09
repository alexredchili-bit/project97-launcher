package zombie.plz.access;

import java.util.ArrayList;
import zombie.iso.IsoChunk;

public interface PLZFBOPerPlayerDataAccess {
    ArrayList<IsoChunk> plzOnScreenChunks();

    int plzLightingUpdateCounter();

    void plzSetLightingUpdateCounter(int counter);
}
