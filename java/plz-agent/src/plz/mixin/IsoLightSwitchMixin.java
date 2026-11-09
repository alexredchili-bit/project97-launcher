package plz.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalIntRef;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zombie.core.raknet.UdpConnection;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoWorld;
import zombie.iso.objects.IsoLightSwitch;
import zombie.network.GameServer;
import zombie.plz.PLZPerf;
import zombie.plz.PLZSyncWatch;

@Mixin(IsoLightSwitch.class)
abstract class IsoLightSwitchMixin {
    @Shadow
    public boolean activated;

    @Unique
    private boolean plz$powerKnown;

    @Unique
    private int plz$powerFrame;

    @Unique
    private boolean plz$powered;

    @Unique
    private static boolean plz$applyingRemote;

    @Unique
    private IsoGridSquare plz$square() {
        return ((IsoLightSwitch) (Object) this).getSquare();
    }

    // PLZ: vanilla walks the surrounding squares for every switch every frame; power cannot change faster than the switch reacts.
    @WrapMethod(method = "hasElectricityAround")
    private boolean plz$throttledPower(Operation<Boolean> original) {
        int interval = PLZPerf.LIGHT_SWITCH_FRAMES;
        if (interval > 0 && IsoWorld.instance != null) {
            int now = IsoWorld.instance.getFrameNo();
            if (this.plz$powerKnown && this.plz$powerFrame >= 0 && now >= this.plz$powerFrame && now - this.plz$powerFrame < interval) {
                return this.plz$powered;
            }

            this.plz$powered = original.call();
            this.plz$powerFrame = now;
            this.plz$powerKnown = true;
            return this.plz$powered;
        }

        return original.call();
    }

    @WrapWithCondition(
        method = "setActive(ZZZ)Z",
        at = @At(value = "INVOKE", target = "Lzombie/iso/objects/IsoLightSwitch;syncIsoObject(ZBLzombie/core/raknet/UdpConnection;)V")
    )
    private boolean plz$noEchoWhileApplyingRemote(IsoLightSwitch self, boolean bRemote, byte val, UdpConnection source) {
        return !plz$applyingRemote;
    }

    // PLZ: the sender syncs every tile itself; re-sending siblings here echoes through every client in range.
    @WrapOperation(
        method = "syncIsoObject(ZBLzombie/core/raknet/UdpConnection;)V",
        at = @At(value = "INVOKE", target = "Lzombie/iso/objects/IsoLightSwitch;switchLight(Z)V")
    )
    private void plz$applyRemote(IsoLightSwitch self, boolean on, Operation<Void> original) {
        boolean wasApplyingRemote = plz$applyingRemote;
        this.activated = on;
        plz$applyingRemote = true;
        try {
            original.call(self, on);
        } finally {
            plz$applyingRemote = wasApplyingRemote;
        }
    }

    // Reporting the sender's own GUID makes the vanilla "other client" test fail, which skips the relay exactly like a continue.
    @WrapOperation(
        method = "syncIsoObject(ZBLzombie/core/raknet/UdpConnection;)V",
        at = @At(value = "INVOKE", target = "Lzombie/core/raknet/UdpConnection;getConnectedGUID()J", ordinal = 0)
    )
    private long plz$relayOnlyInRange(
        UdpConnection connection,
        Operation<Long> original,
        @Local(argsOnly = true) UdpConnection source,
        @Share("plzSkipped") LocalIntRef skipped
    ) {
        long guid = original.call(connection);
        if (PLZSyncWatch.relevantOnly && guid != source.getConnectedGUID()) {
            IsoGridSquare square = this.plz$square();
            if (!connection.isRelevantTo(square.x, square.y)) {
                skipped.set(skipped.get() + 1);
                return source.getConnectedGUID();
            }
        }

        return guid;
    }

    @Inject(
        method = "syncIsoObject(ZBLzombie/core/raknet/UdpConnection;)V",
        at = @At(
            value = "INVOKE",
            target = "Lzombie/network/PacketTypes$PacketType;send(Lzombie/network/IConnection;)V",
            ordinal = 0,
            shift = At.Shift.AFTER
        )
    )
    private void plz$countRelay(boolean bRemote, byte val, UdpConnection source, CallbackInfo ci, @Share("plzSent") LocalIntRef sent) {
        sent.set(sent.get() + 1);
    }

    @Inject(
        method = "syncIsoObject(ZBLzombie/core/raknet/UdpConnection;)V",
        at = @At(
            value = "INVOKE",
            target = "Lzombie/network/PacketTypes$PacketType;send(Lzombie/network/IConnection;)V",
            ordinal = 1,
            shift = At.Shift.AFTER
        )
    )
    private void plz$countBroadcast(boolean bRemote, byte val, UdpConnection source, CallbackInfo ci, @Share("plzSent") LocalIntRef sent) {
        sent.set(sent.get() + 1);
    }

    @Inject(
        method = "syncIsoObject(ZBLzombie/core/raknet/UdpConnection;)V",
        at = @At(value = "INVOKE", target = "Lzombie/iso/objects/IsoLightSwitch;flagForHotSave()V")
    )
    private void plz$recordSync(
        boolean bRemote,
        byte val,
        UdpConnection source,
        CallbackInfo ci,
        @Share("plzSent") LocalIntRef sent,
        @Share("plzSkipped") LocalIntRef skipped
    ) {
        if (GameServer.server && PLZSyncWatch.enabled) {
            IsoGridSquare square = this.plz$square();
            PLZSyncWatch.record("IsoLightSwitch", square.getX(), square.getY(), square.getZ(), sent.get(), skipped.get(), source);
        }
    }
}
