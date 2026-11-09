package plz.mixin.server;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalIntRef;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import zombie.characters.IsoPlayer;
import zombie.core.Transaction;
import zombie.core.raknet.UdpConnection;
import zombie.inventory.InventoryItem;
import zombie.inventory.ItemContainer;
import zombie.network.packets.ItemTransactionPacket;
import zombie.plz.PLZVehicleAccess;

@Mixin(ItemTransactionPacket.class)
abstract class ItemTransactionPacketMixin extends Transaction {
    @WrapOperation(
        method = "processServer",
        at = @At(
            value = "INVOKE",
            target = "Lzombie/core/TransactionManager;isConsistent(ILzombie/inventory/InventoryItem;Lzombie/inventory/ItemContainer;Lzombie/inventory/ItemContainer;Ljava/lang/String;Lzombie/network/packets/ItemTransactionPacket;Lzombie/characters/IsoPlayer;)B"
        )
    )
    private byte plz$vehicleBootRule(
        int itemId,
        InventoryItem item,
        ItemContainer source,
        ItemContainer destination,
        String extra,
        ItemTransactionPacket packet,
        IsoPlayer player,
        Operation<Byte> original,
        @Local(argsOnly = true) UdpConnection connection,
        @Share("plzChecked") LocalIntRef checked
    ) {
        byte consistent = original.call(itemId, item, source, destination, extra, packet, player);
        checked.set(checked.get() + 1);
        if (consistent != 0 || checked.get() != this.entries.size() || this.state != Transaction.TransactionState.Request) {
            return consistent;
        }

        for (Transaction.TransactionEntry entry : this.entries) {
            // PLZ: the vehicle boot rule; this is the packet every inventory transfer moves through.
            if (!PLZVehicleAccess.mayTransferBetween(entry.sourceId.getContainer(), entry.destinationId.getContainer(), connection.getUserName())) {
                return 1;
            }
        }

        return consistent;
    }
}
