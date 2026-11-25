package plz.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import zombie.characters.IsoPlayer;
import zombie.characters.NetworkCharacterAI;
import zombie.characters.NetworkPlayerAI;
import zombie.characters.animals.IsoAnimal;
import zombie.core.utils.UpdateLimit;
import zombie.iso.IsoUtils;
import zombie.network.GameClient;
import zombie.network.GameServer;
import zombie.network.PacketTypes;
import zombie.network.packets.INetworkPacket;
import zombie.network.packets.character.AnimalPacket;
import zombie.network.packets.character.PlayerPacket;
import zombie.plz.PLZAnimalSync;
import zombie.plz.PLZArena;
import zombie.plz.PLZRemoteHeal;
import zombie.plz.access.PLZNetworkPlayerAIAccess;

@Mixin(NetworkPlayerAI.class)
abstract class NetworkPlayerAIMixin implements PLZNetworkPlayerAIAccess {
    @Shadow
    @Final
    private IsoPlayer player;

    @Shadow
    @Final
    private UpdateLimit timerMin;

    @Unique
    private long plz$minPeriod;

    @Unique
    private boolean plz$fast;

    @Unique
    private boolean plz$forced;

    @Unique
    private float plz$sentX;

    @Unique
    private float plz$sentY;

    @Unique
    private float plz$sentAngle;

    @Inject(method = "<init>(Lzombie/characters/IsoPlayer;)V", at = @At("RETURN"))
    private void plz$initMinPeriod(IsoPlayer player, CallbackInfo ci) {
        this.plz$minPeriod = 200L;
    }

    @Override
    public void setMinUpdatePeriod(long ms) {
        this.plz$minPeriod = ms;
        if (!this.plz$fast) {
            this.timerMin.setUpdatePeriod(ms);
        }
    }

    @Inject(method = "set(Lzombie/network/packets/character/PlayerPacket;)Lzombie/network/PacketTypes$PacketType;", at = @At("HEAD"))
    private void plz$arenaGate(PlayerPacket packet, CallbackInfoReturnable<PacketTypes.PacketType> cir) {
        boolean fast = PLZArena.isFast(this.player);
        if (fast != this.plz$fast) {
            this.plz$fast = fast;
            this.timerMin.setUpdatePeriod(fast ? Math.min(PLZArena.SEND_MS, this.plz$minPeriod) : this.plz$minPeriod);
        }
    }

    @ModifyExpressionValue(
        method = "set(Lzombie/network/packets/character/PlayerPacket;)Lzombie/network/PacketTypes$PacketType;",
        at = @At(value = "FIELD", target = "Lzombie/characters/NetworkPlayerAI;needUpdate:Z", opcode = Opcodes.GETFIELD, ordinal = 1)
    )
    private boolean plz$noteForced(boolean needUpdate) {
        this.plz$forced = needUpdate;
        return needUpdate;
    }

    // Inside an arena, movement goes out unreliable at 20+ Hz; only real state changes stay reliable.
    @Inject(
        method = "set(Lzombie/network/packets/character/PlayerPacket;)Lzombie/network/PacketTypes$PacketType;",
        at = @At(value = "INVOKE", target = "Lzombie/network/fields/vehicle/VehicleID;set(Lzombie/vehicles/BaseVehicle;)V")
    )
    private void plz$arenaSend(
        PlayerPacket packet,
        CallbackInfoReturnable<PacketTypes.PacketType> cir,
        @Local LocalRef<PacketTypes.PacketType> result,
        @Local(ordinal = 0) boolean squareChanged,
        @Local(ordinal = 1) boolean flagsChanged,
        @Local(ordinal = 2) boolean turningChanged,
        @Local(ordinal = 3) boolean directionChanged,
        @Local(ordinal = 4) boolean timerChanged
    ) {
        boolean fast = this.plz$fast;
        boolean forced = this.plz$forced;
        this.plz$forced = false;
        if (result.get() == PacketTypes.PacketType.PlayerUpdateReliable) {
            PLZArena.countReasons(flagsChanged, turningChanged, directionChanged, timerChanged, squareChanged, forced);
            if (fast && !flagsChanged && !timerChanged && !squareChanged && !forced) {
                result.set(PacketTypes.PacketType.PlayerUpdateUnreliable);
            }
        }

        float angle = packet.prediction.direction;
        if (result.get() == null
            && fast
            && PLZArena.moved(this.plz$sentX, this.plz$sentY, this.plz$sentAngle, this.player.getX(), this.player.getY(), angle)) {
            result.set(PacketTypes.PacketType.PlayerUpdateUnreliable);
            PLZArena.sentMoved++;
        }

        if (result.get() != null) {
            PLZArena.countSent(result.get() == PacketTypes.PacketType.PlayerUpdateUnreliable);
            this.plz$sentX = this.player.getX();
            this.plz$sentY = this.player.getY();
            this.plz$sentAngle = angle;
        }
    }

    // Staff run -debug clients: vanilla Ctrl+A/D/R/S/V/Z spawned bulls, cars and zombies, or killed them, mid-RP.
    @Inject(method = "checkDebugInput()V", at = @At("HEAD"), cancellable = true)
    private void plz$noDebugHotkeys(CallbackInfo ci) {
        ci.cancel();
    }

    @Inject(method = "parse(Lzombie/network/packets/character/AnimalPacket;)V", at = @At("HEAD"))
    private void plz$touchAnimal(AnimalPacket packet, CallbackInfo ci) {
        if (this.player instanceof IsoAnimal animal) {
            PLZAnimalSync.touch(animal, (packet.flags & AnimalPacket.Flags.init) != 0);
        }
    }

    // Reached only once isExistInTheWorld() is false: the vanilla test short-circuits before getItemID.
    @Inject(
        method = "parse(Lzombie/network/packets/character/AnimalPacket;)V",
        at = @At(value = "INVOKE", target = "Lzombie/characters/animals/IsoAnimal;getItemID()I"),
        cancellable = true
    )
    private void plz$dropContradicted(AnimalPacket packet, CallbackInfo ci) {
        IsoAnimal animal = (IsoAnimal) this.player;
        if (animal.getItemID() != 0 && PLZAnimalSync.contradicted(animal)) {
            ci.cancel();
        }
    }

    // A seated player who missed the one-shot state sync walks at the seat forever; a locked door defeats the pathfinder heal.
    @Inject(method = "parse(Lzombie/network/packets/character/PlayerPacket;)V", at = @At("TAIL"))
    private void plz$healIfStuck(PlayerPacket packet, CallbackInfo ci) {
        if (GameServer.server || !GameClient.client || this.player == null || this.player.isLocalPlayer()) {
            return;
        }

        NetworkCharacterAI self = (NetworkCharacterAI) (Object) this;
        float gap = IsoUtils.DistanceManhatten(this.player.realx, this.player.realy, this.player.getX(), this.player.getY());
        int action = PLZRemoteHeal.track(this.player.getOnlineID(), this.player.getX(), this.player.getY(), self.targetX, self.targetY, gap);
        if (action == PLZRemoteHeal.REQUEST) {
            INetworkPacket.send(PacketTypes.PacketType.PlayerDataRequest, this.player.getOnlineID());
        } else if (action == PLZRemoteHeal.SNAP) {
            this.player.teleportTo(this.player.realx, this.player.realy, this.player.realz);
        }
    }
}
