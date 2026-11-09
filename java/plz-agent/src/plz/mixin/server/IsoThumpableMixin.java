package plz.mixin.server;

import com.llamalad7.mixinextras.sugar.Local;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import zombie.characters.IsoGameCharacter;
import zombie.characters.IsoPlayer;
import zombie.core.network.ByteBufferReader;
import zombie.core.network.ByteBufferWriter;
import zombie.core.raknet.UdpConnection;
import zombie.iso.IsoLightSource;
import zombie.iso.IsoObject;
import zombie.iso.objects.IsoThumpable;
import zombie.network.GameServer;
import zombie.network.PacketTypes;
import zombie.plz.PLZObjectUpdate;

@Mixin(IsoThumpable.class)
abstract class IsoThumpableMixin extends IsoObject {
    @Shadow
    public boolean open;

    @Shadow
    private IsoLightSource lightSource;

    @Shadow
    private String lightSourceFuel;

    @Shadow
    private boolean lockedByKey;

    @Shadow
    private boolean wasTryingToggleLockedDoor;

    @Shadow
    private short lastPlayerOnlineId;

    IsoThumpableMixin() {
        super();
    }

    @Shadow
    public abstract boolean isBarricaded();

    @Shadow
    public abstract float getLifeLeft();

    @Shadow
    private void TriggerLockedDoor(IsoGameCharacter chr) {
        throw new AssertionError();
    }

    @Inject(method = "couldBeOpen", at = @At("HEAD"), cancellable = true)
    private void plz$couldNotOpenAbsoluteLock(IsoGameCharacter chr, CallbackInfoReturnable<Boolean> cir) {
        if (chr instanceof IsoPlayer && !this.open && this.plz$absolutelyLocked()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(
        method = "ToggleDoorActual",
        at = @At(
            value = "FIELD",
            target = "Lzombie/iso/objects/IsoThumpable;wasTryingToggleLockedDoor:Z",
            opcode = Opcodes.PUTFIELD,
            ordinal = 0,
            shift = At.Shift.AFTER
        ),
        cancellable = true
    )
    private void plz$holdAbsoluteLock(IsoGameCharacter chr, CallbackInfo ci) {
        if (!this.isBarricaded() && chr instanceof IsoPlayer && !this.open && this.plz$absolutelyLocked()) {
            this.TriggerLockedDoor(chr);
            this.wasTryingToggleLockedDoor = true;
            this.sync();
            ci.cancel();
        }
    }

    @Inject(method = "update()V", at = @At("HEAD"), cancellable = true)
    private void plz$skipIdleUpdate(CallbackInfo ci) {
        if (PLZObjectUpdate.skipIdle && this.plz$updateIsIdle()) {
            PLZObjectUpdate.countSkipped();
            ci.cancel();
            return;
        }

        PLZObjectUpdate.countRan();
    }

    @Inject(
        method = "syncIsoObjectReceive",
        at = @At(value = "FIELD", target = "Lzombie/iso/objects/IsoThumpable;locked:Z", opcode = Opcodes.GETFIELD, ordinal = 0),
        cancellable = true
    )
    private void plz$refuseAbsoluteLockSync(ByteBufferReader bb, UdpConnection source, CallbackInfo ci, @Local(name = "bOpen") boolean bOpen) {
        if (!GameServer.server || !this.plz$absolutelyLocked()) {
            return;
        }

        int start = bb.position();
        boolean bLockedByKey = bb.getBoolean();
        bb.getBoolean();
        bb.getInt();
        bb.getInt();
        short onlineId = bb.getShort();
        bb.getBoolean();
        bb.getBoolean();
        if (bOpen && !this.open || this.lockedByKey && !bLockedByKey) {
            this.lastPlayerOnlineId = onlineId;
            this.plz$tellSenderTheTruth();
            ci.cancel();
            return;
        }

        bb.position(start);
    }

    @Unique
    private boolean plz$absolutelyLocked() {
        return this.hasModData()
            && this.getModData().rawget("PLZLock") instanceof Boolean plzLock
            && plzLock;
    }

    @Unique
    private void plz$tellSenderTheTruth() {
        UdpConnection connection = GameServer.getConnectionByPlayerOnlineID(this.lastPlayerOnlineId);
        if (connection == null) {
            return;
        }

        ByteBufferWriter b = connection.startPacket();
        PacketTypes.PacketType.SyncIsoObject.doPacket(b);
        this.syncIsoObjectSend(b);
        PacketTypes.PacketType.SyncIsoObject.send(connection);
    }

    // getObjectIndex() is a linear scan of the square's objects, run per object per tick.
    @Unique
    private boolean plz$updateIsIdle() {
        if (this.getLifeLeft() > -1.0F) {
            return false;
        }
        if (GameServer.server) {
            return true;
        }
        if (this.lightSource != null) {
            return false;
        }
        return this.lightSourceFuel == null || this.lightSourceFuel.isEmpty();
    }
}
