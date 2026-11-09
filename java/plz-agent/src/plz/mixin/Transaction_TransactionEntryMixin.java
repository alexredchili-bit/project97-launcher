package plz.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import zombie.network.fields.ContainerID;
import zombie.plz.access.PLZTransactionEntryAccess;

@Mixin(targets = "zombie.core.Transaction$TransactionEntry")
abstract class Transaction_TransactionEntryMixin implements PLZTransactionEntryAccess {
    @Shadow
    @Final
    public ContainerID sourceId;

    @Shadow
    @Final
    public ContainerID destinationId;

    @Override
    public ContainerID plzSourceId() {
        return this.sourceId;
    }

    @Override
    public ContainerID plzDestinationId() {
        return this.destinationId;
    }
}
