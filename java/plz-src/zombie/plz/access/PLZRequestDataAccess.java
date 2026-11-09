package zombie.plz.access;

import zombie.network.packets.RequestDataPacket;

public interface PLZRequestDataAccess {
    long plzConnectionGuid();

    RequestDataPacket.RequestID plzId();

    long plzCreationTime();
}
