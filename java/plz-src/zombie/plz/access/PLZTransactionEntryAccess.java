package zombie.plz.access;

import zombie.network.fields.ContainerID;

public interface PLZTransactionEntryAccess {
    ContainerID plzSourceId();

    ContainerID plzDestinationId();
}
