package plz.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import zombie.characters.IsoPlayer;
import zombie.characters.component.CharacterInputComponent;
import zombie.iso.Vector2;
import zombie.plz.PLZHighFX;
import zombie.plz.access.PLZCharacterInputComponentAccess;

@Mixin(CharacterInputComponent.class)
abstract class CharacterInputComponentMixin implements PLZCharacterInputComponentAccess {
    @Unique
    private static final float PLZ_MOVE_STOP_SQ = 0.342F;

    @Unique
    private boolean plz$allowAttack;

    @Unique
    private Vector2 plz$move;

    @Unique
    private long plz$moveAt;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void plz$init(CallbackInfo ci) {
        this.plz$allowAttack = true;
        this.plz$move = new Vector2();
    }

    @Override
    public boolean isAllowAttack() {
        return this.plz$allowAttack;
    }

    @Override
    public void setAllowAttack(boolean allowAttack) {
        this.plz$allowAttack = allowAttack;
    }

    // The cannabis high's heavy legs; time-based, so repeat calls within a frame change nothing. See PLZHighFX.
    @ModifyReturnValue(method = "getInputMoveVector", at = @At("RETURN"))
    private Vector2 plz$highSmooth(Vector2 out) {
        IsoPlayer player = ((CharacterInputComponent) (Object) this).tryGetECSOwnerEntityAs(IsoPlayer.class);
        float tau = player != null && player.isLocalPlayer() ? PLZHighFX.moveTau(player.getPlayerNum()) : 0.0F;
        long now = System.nanoTime();
        float k = PLZHighFX.follow(tau, PLZHighFX.secondsSince(this.plz$moveAt, now));
        this.plz$moveAt = now;
        if (k >= 1.0F) {
            this.plz$move.set(out);
            return out;
        }

        float x = this.plz$move.x + (out.x - this.plz$move.x) * k;
        float y = this.plz$move.y + (out.y - this.plz$move.y) * k;
        if (out.x == 0.0F && out.y == 0.0F && x * x + y * y < PLZ_MOVE_STOP_SQ) {
            x = 0.0F;
            y = 0.0F;
        }

        this.plz$move.set(x, y);
        return out.set(x, y);
    }

    @Inject(method = {"isAnyAimKeyDown", "isMeleeButtonDown", "isAttackButtonDown"}, at = @At("HEAD"), cancellable = true)
    private void plz$blockAttack(CallbackInfoReturnable<Boolean> cir) {
        if (!this.plz$allowAttack) {
            cir.setReturnValue(false);
        }
    }
}
