package plz.mixin.server;

import gnu.trove.iterator.TShortShortIterator;
import gnu.trove.map.hash.TShortShortHashMap;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import zombie.core.raknet.UdpConnection;
import zombie.network.PacketTypes;
import zombie.network.packets.INetworkPacket;
import zombie.network.packets.vehicle.VehicleRequestPacket;
import zombie.plz.PLZFixes;
import zombie.plz.PLZVehicleGhost;
import zombie.plz.PLZVehicleNet;
import zombie.vehicles.BaseVehicle;
import zombie.vehicles.VehicleManager;

@Mixin(VehicleRequestPacket.class)
abstract class VehicleRequestPacketMixin {
    @Unique
    private static final int PLZ_MAX_PHANTOM_REMOVES = 64;

    @Shadow
    @Final
    private TShortShortHashMap parsedRequests;

    /**
     * @author PLZ
     * @reason Phantom removal, known-vehicle tracking and full-sync dedupe touch every branch of this loop.
     */
    @Overwrite
    public void processServer(PacketTypes.PacketType packetType, UdpConnection connection) {
        TShortShortIterator iterator = this.parsedRequests.iterator();
        boolean plzRemoveUnknown = PLZFixes.on(PLZFixes.VEHICLE_PHANTOM_REMOVE);
        int plzRemoved = 0;
        boolean plzDedupe = PLZFixes.on(PLZFixes.VEHICLE_FULL_DEDUPE);
        boolean plzTrack = plzDedupe || PLZFixes.on(PLZFixes.VEHICLE_LEAN_FULL);

        while (iterator.hasNext()) {
            iterator.advance();
            BaseVehicle vehicle = VehicleManager.instance.getVehicleByID(iterator.key());
            if (vehicle == null) {
                if (plzRemoveUnknown
                    && iterator.value() == 16384
                    && plzRemoved < PLZ_MAX_PHANTOM_REMOVES
                    && !PLZVehicleGhost.isDriving(iterator.key(), connection.getUserName())) {
                    INetworkPacket.send(connection, PacketTypes.PacketType.VehicleRemove, iterator.key());
                    PLZFixes.hit(PLZFixes.VEHICLE_PHANTOM_REMOVE);
                    plzRemoved++;
                }
            } else {
                short flag = iterator.value();
                BaseVehicle.ServerVehicleState state = VehicleManager.instance.getVehicleState(connection, vehicle);
                if (flag == 16384) {
                    if (plzTrack) {
                        PLZVehicleNet.noteKnown(connection, vehicle.vehicleId);
                    }

                    if (!connection.isRelevantTo(vehicle.getX(), vehicle.getY())) {
                        INetworkPacket.send(connection, PacketTypes.PacketType.VehicleRemove, vehicle);
                    }
                } else {
                    if ((flag & 1) != 0 && plzDedupe && !PLZVehicleNet.grantFull(connection, vehicle.vehicleId)) {
                        flag = (short)(flag & ~1);
                        PLZFixes.hit(PLZFixes.VEHICLE_FULL_DEDUPE);
                    }

                    state.flags |= flag;
                }
            }
        }
    }
}
