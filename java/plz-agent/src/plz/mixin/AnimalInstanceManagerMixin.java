package plz.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zombie.characters.animals.IsoAnimal;
import zombie.network.GameClient;
import zombie.network.GameServer;
import zombie.network.IsoObjectID;
import zombie.plz.PLZAnimalSync;
import zombie.popman.animal.AnimalInstanceManager;

@Mixin(AnimalInstanceManager.class)
abstract class AnimalInstanceManagerMixin {
    @Shadow
    @Final
    private static IsoObjectID<IsoAnimal> AnimalMap;

    @Inject(
        method = "add",
        at = @At(value = "INVOKE", target = "Lzombie/debug/DebugType;noise(Ljava/lang/String;[Ljava/lang/Object;)V", shift = At.Shift.AFTER)
    )
    private void plz$serverAdd(IsoAnimal animal, short onlineID, CallbackInfo ci) {
        if (GameServer.server) {
            PLZAnimalSync.onServerAdd(AnimalMap.get(onlineID), animal, onlineID);
        }
    }

    @Inject(method = "add", at = @At("TAIL"))
    private void plz$clientTouch(IsoAnimal animal, short onlineID, CallbackInfo ci) {
        if (GameClient.client) {
            PLZAnimalSync.touch(animal, true);
        }
    }
}
