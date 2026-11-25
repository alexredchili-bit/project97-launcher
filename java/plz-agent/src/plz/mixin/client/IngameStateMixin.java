package plz.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zombie.gameStates.IngameState;
import zombie.plz.PLZJoinWatch;

@Mixin(IngameState.class)
abstract class IngameStateMixin {
    @Inject(
        method = "enter",
        at = @At(
            value = "INVOKE",
            target = "Lzombie/network/GameClient;sendPlayerConnect(Lzombie/characters/IsoPlayer;)V",
            shift = At.Shift.AFTER
        )
    )
    private void plz$watchPlayerConnect(CallbackInfo ci) {
        PLZJoinWatch.arm();
    }
}
