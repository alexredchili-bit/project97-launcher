package zombie.plz;

import java.util.Collection;
import zombie.plz.access.PLZTransactionAccess;
import zombie.plz.access.PLZTransactionEntryAccess;

public final class PLZTransactionSweep {
    private PLZTransactionSweep() {
    }

    // isConsistent rejects every transfer server-wide while any in-flight entry's container is gone; such an entry can never complete.
    public static void sweepStale(Collection<?> transactions) {
        boolean removed = transactions.removeIf(transaction -> {
            for (Object entry : ((PLZTransactionAccess) transaction).plzEntries()) {
                PLZTransactionEntryAccess access = (PLZTransactionEntryAccess) entry;
                if (access.plzSourceId().getContainer() == null || access.plzDestinationId().getContainer() == null) {
                    return true;
                }
            }

            return false;
        });

        if (removed) {
            PLZFixes.hit(PLZFixes.TRANSACTION_STALE_SWEEP);
        }
    }
}
