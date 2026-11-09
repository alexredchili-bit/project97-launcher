package zombie.plz.access;

import zombie.plz.PLZNetProbe;

public interface PLZUdpConnectionAccess {
    PLZNetProbe.Tally plzGetNetTally();

    void plzSetNetTally(PLZNetProbe.Tally tally);
}
