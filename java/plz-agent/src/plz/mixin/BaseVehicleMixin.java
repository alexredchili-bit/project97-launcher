package plz.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalIntRef;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import java.nio.ByteBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import plz.agent.LuaPublic;
import zombie.characters.IsoGameCharacter;
import zombie.characters.IsoPlayer;
import zombie.characters.IsoZombie;
import zombie.debug.DebugLog;
import zombie.inventory.types.HandWeapon;
import zombie.iso.IsoChunk;
import zombie.iso.IsoGridSquare;
import zombie.network.GameClient;
import zombie.plz.PLZFixes;
import zombie.plz.PLZVehicleAccess;
import zombie.plz.PLZVehicleDamageWatch;
import zombie.plz.PLZVehicleWatch;
import zombie.vehicleSound.VehicleSounds;
import zombie.vehicles.BaseVehicle;
import zombie.vehicles.VehiclePart;
import zombie.vehicles.VehicleWindow;

@Mixin(BaseVehicle.class)
abstract class BaseVehicleMixin {
    @Shadow
    private VehicleSounds vehicleSounds;

    @Inject(method = "putKeyToZombie", at = @At("HEAD"), cancellable = true)
    private void plz$suppressZombieKey(IsoZombie zombie, CallbackInfo ci) {
        if (PLZVehicleAccess.isSuppressingKeySpawns()) {
            ci.cancel();
        }
    }

    @Inject(method = "addKeyToWorld(Z)V", at = @At("HEAD"), cancellable = true)
    private void plz$suppressWorldKey(boolean crashed, CallbackInfo ci) {
        if (PLZVehicleAccess.isSuppressingKeySpawns()) {
            ci.cancel();
        }
    }

    @Inject(
        method = {"canLockDoor", "canUnlockDoor"},
        at = @At(value = "INVOKE", target = "Lzombie/vehicles/BaseVehicle;getSeat(Lzombie/characters/IsoGameCharacter;)I", ordinal = 0),
        cancellable = true,
        require = 2
    )
    private void plz$ledgerLocks(VehiclePart part, IsoGameCharacter chr, CallbackInfoReturnable<Boolean> cir) {
        if (PLZVehicleAccess.isEnabled()) {
            cir.setReturnValue(PLZVehicleAccess.mayWorkLocks(chr, ((BaseVehicle)(Object)this).getKeyId()));
        }
    }

    // Lua reaches the PLZVehicleAccess ledger through this exposed class (Shared/VehicleAccess/VehicleAccessBridge.lua).
    @Unique
    @LuaPublic
    private static void plzVehicleAccessSetEnabled(boolean value) {
        PLZVehicleAccess.setEnabled(value);
    }

    @Unique
    @LuaPublic
    private static boolean plzVehicleAccessIsEnabled() {
        return PLZVehicleAccess.isEnabled();
    }

    @Unique
    @LuaPublic
    private static void plzVehicleAccessSetSuppressKeys(boolean value) {
        PLZVehicleAccess.setSuppressKeySpawns(value);
    }

    @Unique
    @LuaPublic
    private static void plzVehicleAccessSetGranted(String username, String keyIds) {
        PLZVehicleAccess.setGranted(username, keyIds);
    }

    @Unique
    @LuaPublic
    private static void plzVehicleAccessSetOverride(String username, boolean allowed) {
        PLZVehicleAccess.setOverride(username, allowed);
    }

    @Unique
    @LuaPublic
    private static void plzVehicleAccessSetClaimed(String keyIds) {
        PLZVehicleAccess.setClaimed(keyIds);
    }

    @Unique
    @LuaPublic
    private static boolean plzVehicleAccessHas(String username, double keyId) {
        return PLZVehicleAccess.has(username, (int)Math.floor(keyId));
    }

    @Unique
    @LuaPublic
    private static boolean plzVehicleAccessIsClaimed(double keyId) {
        return PLZVehicleAccess.isClaimed((int)Math.floor(keyId));
    }

    @Unique
    @LuaPublic
    private static boolean plzVehicleAccessHasOverride(String username) {
        return PLZVehicleAccess.hasOverride(username);
    }

    @Unique
    @LuaPublic
    private static void plzVehicleAccessClear() {
        PLZVehicleAccess.clearAll();
    }

    @Unique
    @LuaPublic
    private static int plzVehicleAccessPlayerCount() {
        return PLZVehicleAccess.getGrantedPlayerCount();
    }

    @Unique
    @LuaPublic
    private static int plzVehicleAccessKeyCount(String username) {
        return PLZVehicleAccess.getGrantedKeyCount(username);
    }

    @Unique
    @LuaPublic
    private static int plzVehicleAccessOverrideCount() {
        return PLZVehicleAccess.getOverrideCount();
    }

    @Unique
    @LuaPublic
    private static int plzVehicleAccessClaimedCount() {
        return PLZVehicleAccess.getClaimedCount();
    }

    @Unique
    @LuaPublic
    private static void plzVehicleAccessGrantSeatTicket(String username, double keyId) {
        PLZVehicleAccess.grantSeatTicket(username, (int)Math.floor(keyId));
    }

    @Unique
    @LuaPublic
    private static void plzVehicleAccessSetFleetKeys(String keyIds) {
        PLZVehicleAccess.setFleetKeys(keyIds);
    }

    @Unique
    @LuaPublic
    private static void plzVehicleAccessSetStaff(String username, boolean isStaff) {
        PLZVehicleAccess.setStaff(username, isStaff);
    }

    @Unique
    @LuaPublic
    private static boolean plzVehicleAccessMayOpenContainer(String username, double keyId) {
        return PLZVehicleAccess.mayOpenContainer(username, (int)Math.floor(keyId));
    }

    @Unique
    @LuaPublic
    private static int plzVehicleAccessFleetCount() {
        return PLZVehicleAccess.getFleetCount();
    }

    @Unique
    @LuaPublic
    private static int plzVehicleAccessStaffCount() {
        return PLZVehicleAccess.getStaffCount();
    }

    public boolean plzMayTakeSeat(int seat, IsoGameCharacter chr) {
        // The seat gate stays server-only: a client's grants arrive after OnCreatePlayer enables the bridge, so it would refuse a player their own car.
        if (GameClient.client) {
            return true;
        }

        if (!PLZVehicleAccess.isEnabled()) {
            return true;
        }

        BaseVehicle self = (BaseVehicle)(Object)this;
        int keyId = self.getKeyId();
        if (seat != 0 && !(PLZVehicleAccess.isClaimed(keyId) && self.isAnyDoorLocked())) {
            return true;
        }

        // A seat ticket is scoped to one non-driver seat of one vehicle and consumed on read, so it cannot be replayed.
        if (seat != 0 && chr instanceof IsoPlayer player && PLZVehicleAccess.consumeSeatTicket(player.getUsername(), keyId)) {
            return true;
        }

        return PLZVehicleAccess.mayOperate(chr, keyId);
    }

    @Inject(method = "switchSeat", at = @At("HEAD"), cancellable = true)
    private void plz$gateSwitchSeat(IsoGameCharacter chr, int seatTo, CallbackInfo ci) {
        if (!this.plzMayTakeSeat(seatTo, chr)) {
            ci.cancel();
        }
    }

    @Inject(
        method = "enter(ILzombie/characters/IsoGameCharacter;Lorg/joml/Vector3f;)Z",
        at = @At(value = "INVOKE", target = "Lzombie/vehicles/BaseVehicle;setPassenger(ILzombie/characters/IsoGameCharacter;Lorg/joml/Vector3f;)Z", ordinal = 0),
        cancellable = true
    )
    private void plz$gateEnter(int seat, IsoGameCharacter chr, org.joml.Vector3f offset, CallbackInfoReturnable<Boolean> cir) {
        if (!this.plzMayTakeSeat(seat, chr)) {
            cir.setReturnValue(false);
        }
    }

    // save writes animals.size() then saves every entry unguarded; nulls are swept before the count so load reads a consistent list.
    @Inject(
        method = "save(Ljava/nio/ByteBuffer;Z)V",
        at = @At(value = "FIELD", target = "Lzombie/vehicles/BaseVehicle;animals:Ljava/util/ArrayList;", opcode = 180, ordinal = 0)
    )
    private void plz$sweepNullAnimals(ByteBuffer output, boolean net, CallbackInfo ci) {
        BaseVehicle self = (BaseVehicle)(Object)this;
        if (PLZFixes.on(PLZFixes.VEHICLE_SAVE_ANIMALS) && self.animals.remove(null)) {
            while (self.animals.remove(null)) {
            }

            PLZFixes.hit(PLZFixes.VEHICLE_SAVE_ANIMALS);
        }
    }

    // Runs ahead of update()'s removedFromWorld check; a field-read hook keeps the per-frame path free of a CallbackInfo.
    @ModifyExpressionValue(
        method = "update",
        at = @At(value = "FIELD", target = "Lzombie/vehicles/BaseVehicle;removedFromWorld:Z", opcode = 180, ordinal = 0)
    )
    private boolean plz$rehomeBeforeUpdate(boolean removedFromWorld) {
        if (GameClient.client && PLZFixes.on(PLZFixes.VEHICLE_CHUNK_REHOME)) {
            this.plz$rehomeIfStranded();
        }

        return removedFromWorld;
    }

    // An occupied car's unloaded home chunk freezes it on this client while the server keeps driving it; re-file it under its real chunk.
    @Unique
    private void plz$rehomeIfStranded() {
        BaseVehicle self = (BaseVehicle)(Object)this;
        try {
            if (self.isRemovedFromWorld()) {
                return;
            }

            IsoChunk home = self.chunk;
            if (home == null || !home.refs.isEmpty() || !this.plz$hasLocalPlayerAboard()) {
                return;
            }

            self.setCurrentSquareFromPosition();
            IsoGridSquare square = self.getCurrentSquare();
            if (square == null) {
                return;
            }

            IsoChunk fresh = square.getChunk();
            if (fresh == null || fresh == home || fresh.refs.isEmpty()) {
                return;
            }

            home.vehicles.remove(self);
            if (!fresh.vehicles.contains(self)) {
                fresh.vehicles.add(self);
            }

            self.chunk = fresh;
            IsoChunk.addFromCheckedVehicles(self);
            PLZFixes.hit(PLZFixes.VEHICLE_CHUNK_REHOME);
            DebugLog.log("PLZFixes: re-homed vehicle id=" + self.getId()
                + " with a local player aboard from unloaded chunk " + home.wx + "," + home.wy
                + " to " + fresh.wx + "," + fresh.wy);
        } catch (Throwable failure) {
            DebugLog.log("PLZFixes: vehicle chunk re-home failed: " + failure);
        }
    }

    @Unique
    private boolean plz$hasLocalPlayerAboard() {
        BaseVehicle self = (BaseVehicle)(Object)this;
        int seats = self.getMaxPassengers();
        for (int seat = 0; seat < seats; seat++) {
            BaseVehicle.Passenger passenger = self.getPassenger(seat);
            if (passenger == null || passenger.character == null) {
                continue;
            }

            for (int i = 0; i < IsoPlayer.players.length; i++) {
                if (passenger.character == IsoPlayer.players[i]) {
                    return true;
                }
            }
        }

        return false;
    }

    @Inject(
        method = "crash(FZ)V",
        at = @At(value = "FIELD", target = "Lzombie/SandboxOptions;instance:Lzombie/SandboxOptions;", opcode = 178, ordinal = 0)
    )
    private void plz$watchCrash(float delta, boolean front, CallbackInfo ci) {
        PLZVehicleDamageWatch.onCrash((BaseVehicle)(Object)this, delta);
    }

    @ModifyVariable(method = "tryStartEngine", at = @At("STORE"), ordinal = 1)
    private boolean plz$ledgerIgnition(boolean startAllowed) {
        if (PLZVehicleAccess.isEnabled()) {
            BaseVehicle self = (BaseVehicle)(Object)this;
            return PLZVehicleAccess.mayOperate(self.getDriver(), self.getKeyId());
        }

        return startAllowed;
    }

    // onEngineStateChanged's client branch dereferences getVehicleSounds() after this; a null there aborts VehicleUpdatePacket.parse mid-body.
    @ModifyExpressionValue(
        method = "checkVehicleSoundsExists",
        at = @At(value = "FIELD", target = "Lzombie/network/GameClient;client:Z", opcode = 178, ordinal = 0)
    )
    private boolean plz$createClientSounds(boolean client) {
        if (client && this.vehicleSounds == null && PLZFixes.on(PLZFixes.VEHICLE_SOUNDS_CLIENT)) {
            try {
                ((BaseVehicle)(Object)this).setVehicleSounds(new VehicleSounds());
                PLZFixes.hit(PLZFixes.VEHICLE_SOUNDS_CLIENT);
            } catch (Throwable failure) {
                DebugLog.log("PLZFixes: client VehicleSounds create failed: " + failure);
            }
        }

        return client;
    }

    // The only notification Lua gets that a vehicle is gone for good; B42 fires no event of its own. See zombie.plz.PLZVehicleWatch.
    @Inject(
        method = "permanentlyRemove",
        at = @At(value = "INVOKE", target = "Lzombie/vehicles/VehiclesDB2;removeVehicle(Lzombie/vehicles/BaseVehicle;)V")
    )
    private void plz$watchPermanentRemoval(CallbackInfo ci) {
        PLZVehicleWatch.onPermanentlyRemoved((BaseVehicle)(Object)this);
    }

    // Glass takes the hit on its own part, which may be a child of the one that was struck.
    @Inject(
        method = "applyDamageToPart",
        at = @At(value = "FIELD", target = "Lzombie/vehicles/VehiclePart;light:Lzombie/vehicles/VehicleLight;", opcode = 180, ordinal = 0)
    )
    private void plz$noteStruckPart(
        IsoGameCharacter chr,
        HandWeapon weapon,
        VehiclePart vehiclePart,
        float damage,
        CallbackInfo ci,
        @Share("plzStruck") LocalRef<VehiclePart> struck,
        @Share("plzBefore") LocalIntRef before
    ) {
        // Mixin cannot resolve the window local here, so vanilla's pure window lookup is repeated.
        VehicleWindow window = vehiclePart.getWindow();
        for (int i = 0; i < vehiclePart.getChildCount(); i++) {
            VehiclePart child = vehiclePart.getChild(i);
            if (child != null && child.getWindow() != null) {
                window = child.getWindow();
                break;
            }
        }

        VehiclePart part = vehiclePart.getLight() == null && window != null && window.isHittable() ? window.getPart() : vehiclePart;
        struck.set(part);
        before.set(part.getCondition());
    }

    @Inject(method = "applyDamageToPart", at = @At("TAIL"))
    private void plz$watchPartHit(
        IsoGameCharacter chr,
        HandWeapon weapon,
        VehiclePart vehiclePart,
        float damage,
        CallbackInfo ci,
        @Share("plzStruck") LocalRef<VehiclePart> struck,
        @Share("plzBefore") LocalIntRef before
    ) {
        VehiclePart part = struck.get();
        if (vehiclePart != null && part != null) {
            PLZVehicleDamageWatch.onPartHit((BaseVehicle)(Object)this, chr, weapon, part, before.get(), part.getCondition());
        }
    }
}
