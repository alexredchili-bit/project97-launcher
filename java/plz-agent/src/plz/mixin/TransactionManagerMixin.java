package plz.mixin;

import java.util.concurrent.ConcurrentLinkedQueue;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import zombie.characters.IsoPlayer;
import zombie.core.Transaction;
import zombie.core.TransactionManager;
import zombie.inventory.InventoryItem;
import zombie.inventory.ItemContainer;
import zombie.network.packets.ItemTransactionPacket;
import zombie.plz.PLZFixes;
import zombie.plz.PLZTransactionSweep;

@Mixin(TransactionManager.class)
abstract class TransactionManagerMixin {
    @Shadow
    @Final
    private static ConcurrentLinkedQueue<Transaction> transactions;

    @Inject(
        method = "isConsistent(ILzombie/inventory/InventoryItem;Lzombie/inventory/ItemContainer;Lzombie/inventory/ItemContainer;Ljava/lang/String;Lzombie/network/packets/ItemTransactionPacket;Lzombie/characters/IsoPlayer;)B",
        at = @At(value = "INVOKE", target = "Ljava/util/concurrent/ConcurrentLinkedQueue;iterator()Ljava/util/Iterator;")
    )
    private static void plz$sweepStaleTransactions(
        int itemId, InventoryItem worldItem, ItemContainer source, ItemContainer destination, String extra,
        ItemTransactionPacket dropTransaction, IsoPlayer player, CallbackInfoReturnable<Byte> cir
    ) {
        if (PLZFixes.on(PLZFixes.TRANSACTION_STALE_SWEEP)) {
            PLZTransactionSweep.sweepStale(transactions);
        }
    }
}
