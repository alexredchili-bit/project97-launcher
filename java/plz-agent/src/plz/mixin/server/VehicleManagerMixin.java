package plz.mixin.server;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import java.util.ArrayList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zombie.core.raknet.UdpConnection;
import zombie.network.IConnection;
import zombie.network.PacketTypes;
import zombie.network.packets.INetworkPacket;
import zombie.plz.PLZFixes;
import zombie.plz.PLZVehicleNet;
import zombie.vehicles.BaseVehicle;
import zombie.vehicles.VehicleManager;

@Mixin(VehicleManager.class)
abstract class VehicleManagerMixin {
    @Unique
    private ArrayList<BaseVehicle> plz$deferred;

    @Shadow
    public abstract BaseVehicle.ServerVehicleState getVehicleState(UdpConnection connection, BaseVehicle vehicle);

    @Inject(method = "<init>", at = @At("RETURN"))
    private void plz$initDeferred(CallbackInfo ci) {
        this.plz$deferred = new ArrayList<>();
    }

    @Inject(method = "sendVehicles", at = @At(value = "INVOKE", target = "Lzombie/iso/IsoCell;getVehicles()Ljava/util/Set;"))
    private void plz$beginPass(
        UdpConnection connection, CallbackInfo ci, @Share("plzBudget") LocalBooleanRef budget, @Share("plzLean") LocalBooleanRef lean
    ) {
        budget.set(PLZFixes.on(PLZFixes.VEHICLE_FULL_BUDGET));
        lean.set(PLZFixes.on(PLZFixes.VEHICLE_LEAN_FULL));
        this.plz$deferred.clear();
    }

    // A full update is deferred to the per-tick budget: flags stay 1 and nothing after the send runs for it this pass.
    @WrapOperation(
        method = "sendVehicles",
        at = @At(value = "INVOKE", target = "Lzombie/vehicles/BaseVehicle$ServerVehicleState;shouldSend(Lzombie/vehicles/BaseVehicle;)Z")
    )
    private boolean plz$deferFull(
        BaseVehicle.ServerVehicleState state, BaseVehicle vehicle, Operation<Boolean> original, @Share("plzBudget") LocalBooleanRef budget
    ) {
        boolean send = original.call(state, vehicle);
        if (send && budget.get() && (state.flags & 1) != 0) {
            state.flags = 1;
            this.plz$deferred.add(vehicle);
            return false;
        }

        return send;
    }

    @WrapOperation(
        method = "sendVehicles",
        at = @At(
            value = "INVOKE",
            target = "Lzombie/network/packets/INetworkPacket;send(Lzombie/network/IConnection;Lzombie/network/PacketTypes$PacketType;[Ljava/lang/Object;)V",
            ordinal = 0
        )
    )
    private void plz$leanFull(
        IConnection target,
        PacketTypes.PacketType type,
        Object[] args,
        Operation<Void> original,
        @Local(argsOnly = true) UdpConnection connection,
        @Local BaseVehicle vehicle,
        @Share("plzLean") LocalBooleanRef lean
    ) {
        plz$sendFull(connection, vehicle, lean.get(), () -> original.call(target, type, args));
    }

    @Inject(method = "sendVehicles", at = @At("TAIL"))
    private void plz$sendDeferred(UdpConnection connection, CallbackInfo ci, @Share("plzLean") LocalBooleanRef lean) {
        boolean plzLean = lean.get();
        if (!this.plz$deferred.isEmpty()) {
            PLZVehicleNet.sortNearestFirst(connection, this.plz$deferred);
            int sent = Math.min(this.plz$deferred.size(), PLZVehicleNet.FULLS_PER_TICK);
            for (int i = 0; i < sent; i++) {
                BaseVehicle vehicle = this.plz$deferred.get(i);
                BaseVehicle.ServerVehicleState state = this.getVehicleState(connection, vehicle);
                plz$sendFull(connection, vehicle, plzLean, () -> INetworkPacket.send(connection, PacketTypes.PacketType.VehicleFullUpdate, vehicle));
                state.flags = (short)(state.flags | 24578);
                plz$afterSend(vehicle, state);
            }

            if (this.plz$deferred.size() > sent) {
                PLZFixes.hit(PLZFixes.VEHICLE_FULL_BUDGET);
            }

            this.plz$deferred.clear();
        }

        if (plzLean) {
            PLZVehicleNet.pushOwed(connection);
        }
    }

    @Unique
    private static void plz$sendFull(UdpConnection connection, BaseVehicle vehicle, boolean plzLean, Runnable send) {
        boolean lean = plzLean && PLZVehicleNet.shouldLean(connection, vehicle);
        if (lean) {
            PLZVehicleNet.beginLean();
        }

        try {
            send.run();
        } finally {
            if (lean) {
                PLZVehicleNet.endLean();
            }
        }

        if (plzLean) {
            PLZVehicleNet.fullSent(connection, vehicle.vehicleId, lean);
        }
    }

    @Unique
    private static void plz$afterSend(BaseVehicle vehicle, BaseVehicle.ServerVehicleState state) {
        if ((state.flags & 8192) != 0) {
            state.setAuthorization(vehicle);
        }

        if ((state.flags & 2) != 0) {
            state.x = vehicle.getX();
            state.y = vehicle.getY();
            state.z = vehicle.jniTransform.origin.y;
            state.orient.set(vehicle.savedRot);
        }

        state.flags = 0;
    }
}
