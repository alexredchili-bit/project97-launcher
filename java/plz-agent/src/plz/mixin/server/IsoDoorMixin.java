package plz.mixin.server;

import com.llamalad7.mixinextras.expression.Definition;
import com.llamalad7.mixinextras.expression.Expression;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import com.llamalad7.mixinextras.sugar.ref.LocalIntRef;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import java.util.ArrayList;
import java.util.HashMap;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import zombie.Lua.LuaEventManager;
import zombie.characters.Capability;
import zombie.characters.IsoGameCharacter;
import zombie.characters.IsoPlayer;
import zombie.core.network.ByteBufferReader;
import zombie.core.network.ByteBufferWriter;
import zombie.core.raknet.UdpConnection;
import zombie.debug.DebugType;
import zombie.inventory.types.HandWeapon;
import zombie.iso.IsoObject;
import zombie.iso.objects.IsoDoor;
import zombie.iso.objects.IsoThumpable;
import zombie.network.GameServer;
import zombie.network.PacketTypes;
import zombie.network.fields.NetObject;
import zombie.plz.PLZDoorAccess;
import zombie.plz.PLZDoorSyncGate;
import zombie.plz.PLZGatedGrid;
import zombie.plz.PLZObjectDamage;
import zombie.plz.access.PLZIsoDoorAccess;
import zombie.util.Type;

@Mixin(IsoDoor.class)
abstract class IsoDoorMixin extends IsoObject implements PLZIsoDoorAccess {
    // One report per door, per player, per interval: an unattended client leaning on a locked door drowned the audit trail.
    @Unique
    private static final long PLZ_REFUSAL_REPORT_INTERVAL_MS = 10000L;

    @Unique
    private static final HashMap<Long, Long> plz$refusalReported = new HashMap<>();

    @Shadow
    public int health;

    @Shadow
    public boolean lockedByKey;

    @Shadow
    public boolean locked;

    @Shadow
    private short lastPlayerOnlineId;

    @Unique
    private long plz$lockFlipMs;

    @Unique
    private long plz$lockFlipBy;

    IsoDoorMixin() {
        super();
    }

    @Shadow
    public abstract boolean isOpen();

    @Shadow
    public abstract boolean isLockedByKey();

    @Shadow
    private boolean canBeOpenFromInside(IsoGameCharacter chr) {
        throw new AssertionError();
    }

    @Shadow
    private void TriggerLockedDoor(IsoGameCharacter chr) {
        throw new AssertionError();
    }

    @Shadow
    private void setWasTryingToggleLockedDoor(boolean b) {
        throw new AssertionError();
    }

    @Override
    public void plzSetLockFlip(long nowMs, long sender) {
        this.plz$lockFlipMs = nowMs;
        this.plz$lockFlipBy = sender;
    }

    // The rule is in Lua (ProtectionDoors.lua); armed right before the trigger so a refusal raised for IsoThumpable/IsoWindow cannot leak here.
    @Inject(
        method = "WeaponHit",
        at = @At(value = "INVOKE", target = "Lzombie/Lua/LuaEventManager;triggerEvent(Ljava/lang/String;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)V")
    )
    private void plz$armDamageRefusal(IsoGameCharacter owner, HandWeapon weapon, CallbackInfo ci) {
        PLZObjectDamage.arm();
    }

    @Inject(
        method = "WeaponHit",
        at = @At(
            value = "INVOKE",
            target = "Lzombie/Lua/LuaEventManager;triggerEvent(Ljava/lang/String;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)V",
            shift = At.Shift.AFTER
        ),
        cancellable = true
    )
    private void plz$honourDamageRefusal(IsoGameCharacter owner, HandWeapon weapon, CallbackInfo ci) {
        if (PLZObjectDamage.takeRefusal()) {
            ci.cancel();
        }
    }

    @Inject(
        method = "couldBeOpen",
        at = @At(value = "INVOKE", target = "Lzombie/iso/objects/IsoDoor;canBeOpenFromInside(Lzombie/characters/IsoGameCharacter;)Z"),
        cancellable = true
    )
    private void plz$couldNotOpenAbsoluteLock(IsoGameCharacter chr, CallbackInfoReturnable<Boolean> cir) {
        if (this.plz$absolutelyLocked()) {
            cir.setReturnValue(false);
        }
    }

    // Also matches ToggleDoorActual's `!= null` (bUnlock): MixinExtras hands every key check over as its `== null` value.
    @Definition(id = "haveThisKeyId", method = "Lzombie/inventory/ItemContainer;haveThisKeyId(I)Lzombie/inventory/InventoryItem;")
    @Expression("?.haveThisKeyId(?) == null")
    @ModifyExpressionValue(method = {"couldBeOpen", "ToggleDoorActual"}, at = @At("MIXINEXTRAS:EXPRESSION"), require = 3)
    private boolean plz$mayNotOpen(boolean original, @Local(argsOnly = true) IsoGameCharacter chr) {
        return !this.plz$mayOpen(chr);
    }

    @ModifyExpressionValue(method = "ToggleDoorActual", at = @At(value = "FIELD", target = "Lzombie/network/GameServer;server:Z", ordinal = 1))
    private boolean plz$noCapabilityUnlock(boolean original) {
        return original && !this.plz$absolutelyLocked();
    }

    @ModifyExpressionValue(method = "ToggleDoorActual", at = @At(value = "FIELD", target = "Lzombie/core/Core;debug:Z", ordinal = 0))
    private boolean plz$noCheatUnlock(boolean original) {
        return original && !this.plz$absolutelyLocked();
    }

    @WrapOperation(
        method = "ToggleDoorActual",
        at = @At(value = "INVOKE", target = "Lzombie/iso/objects/IsoDoor;canBeOpenFromInside(Lzombie/characters/IsoGameCharacter;)Z")
    )
    private boolean plz$noInsideUnlock(IsoDoor door, IsoGameCharacter chr, Operation<Boolean> original) {
        return !this.plz$absolutelyLocked() && original.call(door, chr);
    }

    @Inject(method = "ToggleDoorActual", at = @At(value = "INVOKE", target = "Lzombie/iso/objects/IsoDoor;isLockedByKey()Z", ordinal = 0), cancellable = true)
    private void plz$holdAbsoluteLock(IsoGameCharacter chr, CallbackInfo ci) {
        if (this.plz$absolutelyLocked() && !this.isOpen()) {
            this.TriggerLockedDoor(chr);
            this.setWasTryingToggleLockedDoor(true);
            this.sync(this.isOpen() ? 1 : 0);
            ci.cancel();
        }
    }

    // Mixin's local analysis loses slots 10+ (health, doorParts) at the gate below, so they are shared from where they are made.
    @ModifyExpressionValue(method = "syncIsoObject", at = @At(value = "INVOKE", target = "Lzombie/core/network/ByteBufferReader;getInt()I"))
    private int plz$shareHealth(int original, @Share("health") LocalIntRef health) {
        health.set(original);
        return original;
    }

    @ModifyExpressionValue(method = "syncIsoObject", at = @At(value = "NEW", target = "java/util/ArrayList"))
    private ArrayList<NetObject> plz$shareDoorParts(ArrayList<NetObject> original, @Share("doorParts") LocalRef<ArrayList<NetObject>> doorParts) {
        doorParts.set(original);
        return original;
    }

    @Inject(
        method = "syncIsoObject",
        at = @At(value = "FIELD", target = "Lzombie/iso/objects/IsoDoor;locked:Z", opcode = Opcodes.GETFIELD, ordinal = 0),
        cancellable = true
    )
    private void plz$gateRemoteSync(
        boolean bRemote,
        byte val,
        UdpConnection source,
        ByteBufferReader bb,
        CallbackInfo ci,
        @Local(name = "bOpen") LocalBooleanRef bOpen,
        @Local(name = "bLocked") LocalBooleanRef bLocked,
        @Local(name = "bLockedByKey") LocalBooleanRef bLockedByKey,
        @Share("health") LocalIntRef sharedHealth,
        @Share("doorParts") LocalRef<ArrayList<NetObject>> sharedDoorParts
    ) {
        int health = sharedHealth.get();
        ArrayList<NetObject> doorParts = sharedDoorParts.get();
        if (this.plz$refuseUnlock(bOpen.get(), bLocked.get(), bLockedByKey.get())) {
            if (this.plz$shouldReportRefusal()) {
                DebugType.Multiplayer.debugln(
                    "PLZ: refused door unlock at %d,%d,%d keyId=%d from onlineId=%d",
                    this.square.getX(), this.square.getY(), this.square.getZ(),
                    this.getKeyId(), this.lastPlayerOnlineId
                );
                LuaEventManager.triggerEvent(
                    "PLZDoorUnlockRefused", GameServer.IDToPlayerMap.get(this.lastPlayerOnlineId), this
                );
            }
            bOpen.set(this.isOpen());
            bLocked.set(this.locked);
            bLockedByKey.set(this.lockedByKey);
            this.plz$tellSenderTheTruth();
        }

        if (GameServer.server) {
            boolean flipsLock = bLocked.get() != this.locked || bLockedByKey.get() != this.lockedByKey;
            long now = System.currentTimeMillis();
            long sender = source == null ? -1L : source.getConnectedGUID();
            if (flipsLock && !doorParts.isEmpty()
                && PLZDoorSyncGate.holdsLockFlip(this.plz$lockFlipMs, this.plz$lockFlipBy, now, sender)) {
                bOpen.set(this.isOpen());
                bLocked.set(this.locked);
                bLockedByKey.set(this.lockedByKey);
                flipsLock = false;
                this.plz$tellTheTruth(source);
                PLZDoorSyncGate.countHeld();
            }

            if (this.plz$syncChangesNothing(bOpen.get(), bLocked.get(), bLockedByKey.get(), health, doorParts)) {
                PLZDoorSyncGate.countDropped();
                ci.cancel();
                return;
            }

            if (flipsLock) {
                this.plz$stampLockFlip(doorParts, now, sender);
            }
        }
    }

    @ModifyReturnValue(method = "getDoubleDoorIndex", at = @At("RETURN"))
    private static int plz$closedLeafOnly(int original) {
        return original > 4 ? -1 : original;
    }

    @Unique
    private boolean plz$shouldReportRefusal() {
        long key = (long)(this.square.getX() & 0xFFFFF) << 40
            | (long)(this.square.getY() & 0xFFFFF) << 20
            | (long)(this.square.getZ() & 0xF) << 16
            | this.lastPlayerOnlineId & 0xFFFFL;
        long now = System.currentTimeMillis();

        synchronized (plz$refusalReported) {
            Long last = plz$refusalReported.get(key);
            if (last != null && now - last < PLZ_REFUSAL_REPORT_INTERVAL_MS) {
                return false;
            }

            if (plz$refusalReported.size() > 256) {
                plz$refusalReported.values().removeIf(seen -> now - seen >= PLZ_REFUSAL_REPORT_INTERVAL_MS);
            }

            plz$refusalReported.put(key, now);
            return true;
        }
    }

    @Unique
    private boolean plz$senderCannotUseDoors() {
        if (this.lastPlayerOnlineId == -1) {
            return false;
        }

        IsoPlayer sender = GameServer.IDToPlayerMap.get(this.lastPlayerOnlineId);
        if (sender == null) {
            return false;
        }

        return PLZGatedGrid.hasFlag(sender.getUsername(), PLZGatedGrid.NO_DOORS_FLAG);
    }

    @Unique
    private boolean plz$senderMayUnlock() {
        if (this.plz$absolutelyLocked()) {
            return false;
        }

        if (this.lastPlayerOnlineId == -1) {
            return false;
        }

        IsoPlayer sender = GameServer.IDToPlayerMap.get(this.lastPlayerOnlineId);
        if (sender == null) {
            return false;
        }

        if (sender.getRole() != null && sender.getRole().hasCapability(Capability.CanOpenLockedDoors)) {
            return true;
        }

        if (this.canBeOpenFromInside(sender)) {
            return true;
        }

        return this.plz$mayOpen(sender);
    }

    @Unique
    private boolean plz$absolutelyLocked() {
        return this.hasModData()
            && this.getModData().rawget("PLZLock") instanceof Boolean plzLock
            && plzLock;
    }

    @Unique
    private boolean plz$mayOpen(IsoGameCharacter chr) {
        if (chr == null) {
            return false;
        }

        IsoPlayer player = Type.tryCastTo(chr, IsoPlayer.class);
        if (player != null && PLZGatedGrid.hasFlag(player.getUsername(), PLZGatedGrid.NO_DOORS_FLAG)) {
            return false;
        }

        if (chr.getInventory() != null && chr.getInventory().haveThisKeyId(this.getKeyId()) != null) {
            return true;
        }

        return PLZDoorAccess.mayOpen(player, this.getKeyId());
    }

    @Unique
    private void plz$tellSenderTheTruth() {
        this.plz$tellTheTruth(GameServer.getConnectionByPlayerOnlineID(this.lastPlayerOnlineId));
    }

    @Unique
    private void plz$tellTheTruth(UdpConnection connection) {
        if (connection == null) {
            return;
        }

        ByteBufferWriter b = connection.startPacket();
        PacketTypes.PacketType.SyncIsoObject.doPacket(b);
        this.syncIsoObjectSend(b);
        PacketTypes.PacketType.SyncIsoObject.send(connection);
    }

    @Unique
    private boolean plz$refuseUnlock(boolean bOpen, boolean bLocked, boolean bLockedByKey) {
        if (!GameServer.server) {
            return false;
        }

        if (this.plz$senderCannotUseDoors()) {
            return bOpen != this.isOpen() || bLocked != this.locked || bLockedByKey != this.lockedByKey;
        }

        boolean keyLocked = this.isLockedByKey()
            || this.hasModData() && this.getModData().rawget("CustomLock") instanceof Boolean customLock && customLock;
        if (!keyLocked || this.isHoppable()) {
            return false;
        }

        // Not `!bLocked`: a CustomLock door carries locked=false, so only turning a lock off or swinging a shut door open asks for anything.
        boolean wantsUnlock = this.locked && !bLocked
            || this.lockedByKey && !bLockedByKey
            || bOpen && !this.isOpen();
        if (!wantsUnlock) {
            return false;
        }

        return !this.plz$senderMayUnlock();
    }

    @Unique
    private boolean plz$syncChangesNothing(boolean bOpen, boolean bLocked, boolean bLockedByKey, int health, ArrayList<NetObject> doorParts) {
        if (!PLZDoorSyncGate.changesNothing(this.isOpen(), this.locked, this.lockedByKey, this.health, bOpen, bLocked, bLockedByKey, health)) {
            return false;
        }

        for (NetObject doorPart : doorParts) {
            IsoObject obj = doorPart.getObject();
            if (obj instanceof IsoDoor door) {
                if (!PLZDoorSyncGate.partChangesNothing(door.isOpen(), door.locked, door.lockedByKey, bOpen, bLockedByKey)) {
                    return false;
                }
            } else if (obj instanceof IsoThumpable thumpable) {
                if (!PLZDoorSyncGate.partChangesNothing(thumpable.open, thumpable.isLocked(), thumpable.isLockedByKey(), bOpen, bLockedByKey)) {
                    return false;
                }
            } else {
                return false;
            }
        }

        return true;
    }

    @Unique
    private void plz$stampLockFlip(ArrayList<NetObject> doorParts, long nowMs, long sender) {
        this.plz$lockFlipMs = nowMs;
        this.plz$lockFlipBy = sender;

        for (NetObject doorPart : doorParts) {
            if (doorPart.getObject() instanceof IsoDoor door) {
                ((PLZIsoDoorAccess) door).plzSetLockFlip(nowMs, sender);
            }
        }
    }
}
