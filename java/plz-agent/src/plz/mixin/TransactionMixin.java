package plz.mixin;

import java.util.List;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import zombie.core.Transaction;
import zombie.plz.access.PLZTransactionAccess;

@Mixin(Transaction.class)
abstract class TransactionMixin implements PLZTransactionAccess {
    @Shadow
    @Final
    protected List<?> entries;

    @Override
    public List<?> plzEntries() {
        return this.entries;
    }
}
