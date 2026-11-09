package plz.mixin.client;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
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

    @Override
    public void setMinUpdatePeriod(long ms) {
        this.timerMin.setUpdatePeriod(ms);
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
