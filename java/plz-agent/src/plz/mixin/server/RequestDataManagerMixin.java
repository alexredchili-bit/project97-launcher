package plz.mixin.server;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import java.util.ArrayList;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zombie.core.raknet.UdpConnection;
import zombie.network.RequestDataManager;
import zombie.network.packets.RequestDataPacket;
import zombie.plz.PLZFixes;
import zombie.plz.PLZRequestTransfers;
import zombie.plz.access.PLZRequestDataAccess;

@Mixin(RequestDataManager.class)
abstract class RequestDataManagerMixin {
    @Shadow
    @Final
    private ArrayList<Object> requests;

    // Vanilla takes the connection's first transfer and only then checks the id, so a second in-flight request stalls.
    @WrapOperation(
        method = "ACKWasReceived",
        at = @At(value = "FIELD", target = "Lzombie/network/RequestDataManager$RequestData;connectionGuid:J", opcode = Opcodes.GETFIELD)
    )
    private long plz$matchIdToo(
        @Coerce Object request, Operation<Long> original,
        @Local(argsOnly = true) RequestDataPacket.RequestID id, @Local(argsOnly = true) UdpConnection connection
    ) {
        long guid = original.call(request);
        if (PLZFixes.on(PLZFixes.REQUEST_DATA_MANAGER) && ((PLZRequestDataAccess) request).plzId() != id) {
            return connection.getConnectedGUID() + 1L;
        }

        return guid;
    }

    @Inject(
        method = "ACKWasReceived",
        at = @At(value = "INVOKE", target = "Lzombie/network/RequestDataManager;sendData(Lzombie/network/RequestDataManager$RequestData;)V")
    )
    private void plz$countAck(RequestDataPacket.RequestID id, UdpConnection connection, int bytesTransmitted, CallbackInfo ci) {
        if (PLZFixes.on(PLZFixes.REQUEST_DATA_MANAGER)) {
            PLZFixes.hit(PLZFixes.REQUEST_DATA_MANAGER);
        }
    }

    @Inject(method = "disconnect", at = @At("HEAD"), cancellable = true)
    private void plz$sweepLeaverOnly(UdpConnection connection, CallbackInfo ci) {
        long currentTime = System.currentTimeMillis();
        if (PLZFixes.on(PLZFixes.REQUEST_DATA_MANAGER)) {
            PLZRequestTransfers.sweepOnDisconnect(this.requests, connection.getConnectedGUID(), currentTime);
            ci.cancel();
        }
    }
}
