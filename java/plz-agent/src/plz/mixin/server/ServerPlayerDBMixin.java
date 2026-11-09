package plz.mixin.server;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Mixin;
import zombie.characters.IsoPlayer;
import zombie.core.raknet.UdpConnection;
import zombie.plz.PLZSaveGuard;
import zombie.savefile.ServerPlayerDB;

@Mixin(ServerPlayerDB.class)
abstract class ServerPlayerDBMixin {
    @WrapMethod(method = "serverUpdateNetworkCharacter(Lzombie/characters/IsoPlayer;ILzombie/core/raknet/UdpConnection;)V")
    private void plz$guardSave(IsoPlayer player, int playerIndex, UdpConnection connection, Operation<Void> original) {
        try {
            original.call(player, playerIndex, connection);
        } catch (Throwable error) {
            PLZSaveGuard.reportSaveFailure(player, playerIndex, connection, error);
        }
    }
}
