package plz.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import zombie.characters.BodyDamage.BodyPart;
import zombie.plz.PLZMuscleStrain;

@Mixin(BodyPart.class)
abstract class BodyPartMixin {
    @Shadow
    private float stiffness;

    @Shadow
    public abstract float getStiffness();

    // PLZ: the network sync assigns the raw field directly, so pain has to read through the gated getter.
    @ModifyExpressionValue(
        method = "getAdditionalPain(Z)F",
        at = @At(value = "FIELD", target = "Lzombie/characters/BodyDamage/BodyPart;stiffness:F", opcode = Opcodes.GETFIELD)
    )
    private float plz$painThroughGate(float raw) {
        return this.getStiffness();
    }

    // PLZ: masking the getter is what covers the sync path, which would otherwise hand a server's value to every reader.
    @Inject(method = "getStiffness", at = @At("HEAD"), cancellable = true)
    private void plz$hideStiffness(CallbackInfoReturnable<Float> cir) {
        if (PLZMuscleStrain.isOff()) {
            cir.setReturnValue(0.0F);
        }
    }

    // PLZ: zero rather than ignore, so stiffness earned while off cannot resurface when the option comes back.
    @Inject(method = "setStiffness", at = @At("HEAD"), cancellable = true)
    private void plz$clearOnSet(float value, CallbackInfo ci) {
        if (PLZMuscleStrain.isOff()) {
            this.stiffness = 0.0F;
            ci.cancel();
        }
    }

    @Inject(method = "addStiffness", at = @At("HEAD"), cancellable = true)
    private void plz$clearOnAdd(float value, CallbackInfo ci) {
        if (PLZMuscleStrain.isOff()) {
            this.stiffness = 0.0F;
            ci.cancel();
        }
    }
}
