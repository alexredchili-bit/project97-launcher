package zombie.plz.access;

import java.util.ArrayList;
import java.util.HashSet;
import zombie.iso.IsoGridSquare;

public interface PLZCutawayPerPlayerDataAccess {
    ArrayList<IsoGridSquare> plzLastCutawayVisitorResults();

    HashSet<IsoGridSquare> plzCutawayVisitorResultsNorth();

    HashSet<IsoGridSquare> plzCutawayVisitorResultsWest();
}
