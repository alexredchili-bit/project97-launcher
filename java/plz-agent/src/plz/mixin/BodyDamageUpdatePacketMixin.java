package plz.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zombie.characters.IsoPlayer;
import zombie.core.network.ByteBufferReader;
import zombie.network.IConnection;
import zombie.network.fields.character.ConnectionPlayer;
import zombie.network.fields.character.OnlinePlayer;
import zombie.network.fields.character.Player;
import zombie.network.packets.BodyDamageUpdatePacket;
import zombie.plz.PLZFixes;

@Mixin(BodyDamageUpdatePacket.class)
abstract class BodyDamageUpdatePacketMixin {
    @Shadow
    @Final
    private ConnectionPlayer currentPlayer;

    @Shadow
    @Final
    private OnlinePlayer remotePlayer;

    // PLZ: connect/respawn/disconnect reset the online id to -1; repaired at parse so ownership guards see the real id.
    @Unique
    private static void plz$repairStaleId(Player playerId) {
        if (playerId == null || playerId.getId() != -1) {
            return;
        }

        IsoPlayer resolved = playerId.getPlayer();
        if (resolved != null && resolved.getOnlineID() != -1) {
            playerId.setId(resolved.getOnlineID());
            PLZFixes.hit(PLZFixes.BODY_DAMAGE_UPDATE_PACKET);
        }
    }

    @Inject(
        method = "parse",
        at = @At(
            value = "INVOKE",
            target = "Lzombie/network/fields/character/OnlinePlayer;parse(Lzombie/core/network/ByteBufferReader;Lzombie/network/IConnection;)V",
            shift = At.Shift.AFTER
        )
    )
    private void plz$repairStaleIds(ByteBufferReader b, IConnection connection, CallbackInfo ci) {
        if (PLZFixes.on(PLZFixes.BODY_DAMAGE_UPDATE_PACKET)) {
            plz$repairStaleId(this.currentPlayer);
            plz$repairStaleId(this.remotePlayer);
        }
    }
}
