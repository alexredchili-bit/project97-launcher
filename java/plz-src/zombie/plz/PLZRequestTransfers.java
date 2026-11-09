package zombie.plz;

import java.util.List;
import zombie.plz.access.PLZRequestDataAccess;

public final class PLZRequestTransfers {
    // Fuse for sweeping a genuinely leaked transfer, replacing vanilla's 60s cross-purge.
    private static final long STALE_TRANSFER_MS = 600000L;

    private PLZRequestTransfers() {
    }

    // Vanilla purges EVERY transfer older than 60s when ANY player leaves, orphaning slow joiners' world downloads.
    public static void sweepOnDisconnect(List<?> requests, long guid, long currentTime) {
        boolean removed = requests.removeIf(request -> {
            PLZRequestDataAccess requestData = (PLZRequestDataAccess) request;
            return requestData.plzConnectionGuid() == guid || currentTime - requestData.plzCreationTime() > STALE_TRANSFER_MS;
        });
        if (removed) {
            PLZFixes.hit(PLZFixes.REQUEST_DATA_MANAGER);
        }
    }
}
