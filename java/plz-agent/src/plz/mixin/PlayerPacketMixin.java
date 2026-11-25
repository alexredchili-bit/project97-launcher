package plz.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import zombie.GameTime;
import zombie.characters.IsoPlayer;
import zombie.characters.NetworkPlayerAI;
import zombie.core.network.ByteBufferReader;
import zombie.core.network.ByteBufferWriter;
import zombie.core.raknet.UdpConnection;
import zombie.iso.Vector3;
import zombie.network.IConnection;
import zombie.network.PacketTypes;
import zombie.network.fields.character.ConnectionPlayer;
import zombie.network.fields.character.Prediction;
import zombie.network.packets.character.PlayerPacket;
import zombie.plz.PLZArena;
import zombie.plz.PLZArenaHistory;
import zombie.plz.PLZArenaInterp;

@Mixin(PlayerPacket.class)
abstract class PlayerPacketMixin {
    @Shadow
    @Final
    public ConnectionPlayer player;

    @Shadow
    @Final
    public Prediction prediction;

    @Unique
    private int plz$stamp;

    // Wire format change: client and server must both carry this mixin or every player update misparses.
    @Inject(method = "parse(Lzombie/core/network/ByteBufferReader;Lzombie/network/IConnection;)V", at = @At("TAIL"))
    private void plz$readStamp(ByteBufferReader b, IConnection connection, CallbackInfo ci) {
        this.plz$stamp = b.getInt();
    }

    @Inject(method = "write(Lzombie/core/network/ByteBufferWriter;)V", at = @At("TAIL"))
    private void plz$writeStamp(ByteBufferWriter b, CallbackInfo ci) {
        b.putInt(this.plz$stamp);
    }

    @Inject(method = "set(Lzombie/characters/IsoPlayer;)Lzombie/network/PacketTypes$PacketType;", at = @At("HEAD"))
    private void plz$stampNow(IsoPlayer chr, CallbackInfoReturnable<PacketTypes.PacketType> cir) {
        this.plz$stamp = (int)GameTime.getServerTimeMills();
    }

    @Inject(
        method = "processClient(Lzombie/core/raknet/UdpConnection;)V",
        at = @At(value = "INVOKE", target = "Lzombie/characters/IsoPlayer;getVehicle()Lzombie/vehicles/BaseVehicle;", ordinal = 0)
    )
    private void plz$arenaPush(
        UdpConnection connection,
        CallbackInfo ci,
        @Local IsoPlayer isoPlayer,
        @Local NetworkPlayerAI networkAi,
        @Share("plzArena") LocalBooleanRef arena
    ) {
        boolean owned = PLZArenaInterp.owns(isoPlayer, this.prediction.x, this.prediction.y, this.prediction.z);
        arena.set(owned);
        if (owned) {
            PLZArenaInterp.push(
                isoPlayer, this.plz$stamp, this.prediction.x, this.prediction.y, this.prediction.z, this.prediction.direction, networkAi.moving
            );
        }
    }

    @WrapOperation(
        method = "processClient(Lzombie/core/raknet/UdpConnection;)V",
        at = @At(value = "INVOKE", target = "Lzombie/characters/IsoPlayer;teleportTo(FFF)V")
    )
    private void plz$arenaNoSnap(
        IsoPlayer isoPlayer, float x, float y, float z, Operation<Void> original, @Share("plzArena") LocalBooleanRef arena
    ) {
        if (!arena.get()) {
            original.call(isoPlayer, x, y, z);
        }
    }

    @Inject(method = "processServer(Lzombie/network/PacketTypes$PacketType;Lzombie/core/raknet/UdpConnection;)V", at = @At("HEAD"))
    private void plz$arenaRecord(
        PacketTypes.PacketType packetType, UdpConnection connection, CallbackInfo ci, @Share("plzOutsideOk") LocalBooleanRef outsideOk
    ) {
        boolean senderIn = this.player.getPlayer() != null && PLZArena.contains(this.prediction.x, this.prediction.y, this.prediction.z);
        if (senderIn) {
            PLZArenaHistory.record(this.player.getId(), this.plz$stamp, this.prediction.x, this.prediction.y, this.prediction.z);
        }

        outsideOk.set(
            !senderIn || packetType != PacketTypes.PacketType.PlayerUpdateUnreliable || PLZArena.outsideTick(this.player.getId())
        );
    }

    @WrapOperation(
        method = "processServer(Lzombie/network/PacketTypes$PacketType;Lzombie/core/raknet/UdpConnection;)V",
        at = @At(value = "INVOKE", target = "Lzombie/core/raknet/UdpConnection;isFullyConnected()Z")
    )
    private boolean plz$arenaRelayGate(UdpConnection c, Operation<Boolean> original, @Share("plzOutsideOk") LocalBooleanRef outsideOk) {
        return original.call(c) && (outsideOk.get() || plz$receiverInArena(c) || plz$countSkip());
    }

    @Unique
    private static boolean plz$countSkip() {
        PLZArena.relayedSkipped++;
        return false;
    }

    @Unique
    private static boolean plz$receiverInArena(UdpConnection c) {
        for (int i = 0; i < c.players.length; i++) {
            Vector3 pos = c.releventPos[i];
            if (c.players[i] != null && pos != null && PLZArena.contains(pos.x, pos.y, pos.z)) {
                return true;
            }
        }

        return false;
    }
}
