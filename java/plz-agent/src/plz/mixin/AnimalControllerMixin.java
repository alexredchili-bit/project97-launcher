package plz.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zombie.network.GameClient;
import zombie.network.GameServer;
import zombie.plz.PLZAnimalSync;
import zombie.popman.animal.AnimalController;

@Mixin(AnimalController.class)
abstract class AnimalControllerMixin {
    @Inject(method = "update", at = @At("TAIL"))
    private void plz$animalSync(CallbackInfo ci) {
        if (GameServer.server) {
            PLZAnimalSync.serverReport();
        } else if (GameClient.client) {
            PLZAnimalSync.clientUpdate();
        }
    }
}
