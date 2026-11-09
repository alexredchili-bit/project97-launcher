package plz.mixin.server;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zombie.core.raknet.UdpConnection;
import zombie.network.PacketTypes;
import zombie.network.fields.character.ConnectionPlayer;
import zombie.network.fields.vehicle.VehicleID;
import zombie.network.packets.vehicle.VehicleEnterPacket;
import zombie.plz.PLZFixes;
import zombie.plz.PLZVehicleSeatWatch;

@Mixin(VehicleEnterPacket.class)
abstract class VehicleEnterPacketMixin {
    @Shadow
    @Final
    protected VehicleID vehicleId;

    @Shadow
    @Final
    protected ConnectionPlayer player;

    @Shadow
    protected int seatTo;

    @Inject(
        method = "processServer",
        at = @At(value = "INVOKE", target = "Lzombie/vehicles/BaseVehicle;enter(ILzombie/characters/IsoGameCharacter;)Z", shift = At.Shift.AFTER),
        cancellable = true
    )
    private void plz$refusedEnter(PacketTypes.PacketType packetType, UdpConnection connection, CallbackInfo ci) {
        if (this.vehicleId.getVehicle().getSeat(this.player.getPlayer()) != this.seatTo
            && PLZFixes.on(PLZFixes.VEHICLE_SEAT_REFUSAL_SYNC)) {
            PLZVehicleSeatWatch.refusedEnter(connection, this.vehicleId.getVehicle(), this.player.getPlayer(), this.seatTo);
            ci.cancel();
        }
    }

    // OnEnterVehicle is client-gated, so this is the only server-side notice that anybody boarded a vehicle.
    @Inject(method = "processServer", at = @At("TAIL"))
    private void plz$seated(PacketTypes.PacketType packetType, UdpConnection connection, CallbackInfo ci) {
        PLZVehicleSeatWatch.onSeated(this.vehicleId.getVehicle(), this.player.getPlayer(), this.seatTo);
    }
}
