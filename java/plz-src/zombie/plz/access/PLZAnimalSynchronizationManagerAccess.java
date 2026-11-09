package zombie.plz.access;

import java.util.Collection;
import zombie.network.IConnection;

public interface PLZAnimalSynchronizationManagerAccess {
    void plzRequest(IConnection connection, Collection<Short> onlineIDs);
}
